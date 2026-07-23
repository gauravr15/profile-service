package com.odin.profileservice.service;

import com.odin.profileservice.config.ContactDiscoveryProperties;
import com.odin.profileservice.service.ContactTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactDiscoveryRateLimiter {
    private static final DefaultRedisScript<Long> FIXED_WINDOW = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]);"
                    + "if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end;"
                    + "if n>tonumber(ARGV[2]) then return redis.call('TTL',KEYS[1]); end;"
                    + "return 0;", Long.class);
    private static final DefaultRedisScript<Long> UNIQUE_WINDOW = new DefaultRedisScript<>(
            "local added={};"
                    + "for i=3,#ARGV do "
                    + " if redis.call('SADD',KEYS[1],ARGV[i])==1 then table.insert(added,ARGV[i]); end;"
                    + "end;"
                    + "if redis.call('TTL',KEYS[1])<0 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end;"
                    + "if redis.call('SCARD',KEYS[1])>tonumber(ARGV[2]) then "
                    + " for _,v in ipairs(added) do redis.call('SREM',KEYS[1],v); end;"
                    + " return redis.call('TTL',KEYS[1]);"
                    + "end; return 0;", Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ContactDiscoveryProperties properties;
    private final ContactTokenService contactTokenService;

    public void enforce(String customerId, List<String> canonicalNumbers) {
        String accountToken = protectedToken("account:" + customerId);
        boolean single = canonicalNumbers.size() == 1;
        String category = single ? "single" : "bulk";
        int limit = single
                ? properties.getSingleRequestsPerMinute()
                : properties.getBulkRequestsPerMinute();
        String prefix = "contact-discovery:" + properties.getEnvironment() + ":";
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(
                    prefix + "blocked:" + accountToken))) {
                throw ContactDiscoveryException.rateLimited(
                        properties.getTemporaryBlockSeconds());
            }
            Long requestRetry = redisTemplate.execute(
                    FIXED_WINDOW,
                    Collections.singletonList(prefix + "rate:" + category + ":" + accountToken),
                    60, limit);
            if (requestRetry != null && requestRetry > 0) {
                throw ContactDiscoveryException.rateLimited(requestRetry.intValue());
            }

            List<Object> arguments = new ArrayList<>();
            arguments.add(secondsUntilUtcDayEnd());
            arguments.add(properties.getDailyUniqueNumbers());
            for (String number : canonicalNumbers) {
                arguments.add(protectedToken("phone:" + number));
            }
            Long uniqueRetry = redisTemplate.execute(
                    UNIQUE_WINDOW,
                    Collections.singletonList(prefix + "unique:" + LocalDate.now(ZoneOffset.UTC)
                            + ":" + accountToken),
                    arguments.toArray());
            if (uniqueRetry != null && uniqueRetry > 0) {
                throw ContactDiscoveryException.rateLimited(uniqueRetry.intValue());
            }
        } catch (ContactDiscoveryException ex) {
            throw ex;
        } catch (RedisConnectionFailureException ex) {
            throw ContactDiscoveryException.unavailable();
        } catch (RuntimeException ex) {
            throw ContactDiscoveryException.unavailable();
        }
    }

    public void recordOutcome(String customerId, int matched, int unmatched) {
        String accountToken = protectedToken("account:" + customerId);
        String prefix = "contact-discovery:" + properties.getEnvironment() + ":abuse:";
        String requestsKey = prefix + "requests:" + accountToken;
        String unmatchedKey = prefix + "unmatched:" + accountToken;
        String inputsKey = prefix + "inputs:" + accountToken;
        try {
            Long requests = redisTemplate.opsForValue().increment(requestsKey);
            Long totalUnmatched = redisTemplate.opsForValue().increment(unmatchedKey, unmatched);
            Long totalInputs = redisTemplate.opsForValue().increment(inputsKey, matched + unmatched);
            redisTemplate.expire(requestsKey, java.time.Duration.ofMinutes(15));
            redisTemplate.expire(unmatchedKey, java.time.Duration.ofMinutes(15));
            redisTemplate.expire(inputsKey, java.time.Duration.ofMinutes(15));
            if (requests != null && totalUnmatched != null && totalInputs != null
                    && totalInputs > 0
                    && requests >= properties.getAbuseMinimumRequests()
                    && totalUnmatched * 100L
                    >= totalInputs * properties.getAbuseUnmatchedPercent()) {
                redisTemplate.opsForValue().set(
                        "contact-discovery:" + properties.getEnvironment()
                                + ":blocked:" + accountToken,
                        "1",
                        java.time.Duration.ofSeconds(properties.getTemporaryBlockSeconds()));
            }
        } catch (RuntimeException ignored) {
            // Outcome analytics must not convert a completed lookup into a failure.
        }
    }

    public void clearAccountState(String customerId) {
        String accountToken = protectedToken("account:" + customerId);
        String prefix = "contact-discovery:" + properties.getEnvironment() + ":";
        List<String> keys = new ArrayList<>();
        keys.add(prefix + "blocked:" + accountToken);
        keys.add(prefix + "rate:single:" + accountToken);
        keys.add(prefix + "rate:bulk:" + accountToken);
        keys.add(prefix + "unique:" + LocalDate.now(ZoneOffset.UTC) + ":" + accountToken);
        keys.add(prefix + "abuse:requests:" + accountToken);
        keys.add(prefix + "abuse:unmatched:" + accountToken);
        keys.add(prefix + "abuse:inputs:" + accountToken);
        try {
            redisTemplate.delete(keys);
        } catch (RuntimeException ignored) {
            // These protected, TTL-bounded enforcement keys expire independently.
        }
    }

    private String protectedToken(String value) {
        try {
            if (value.startsWith("account:")) {
                return contactTokenService.deriveDiscoveryAccountToken(value.substring("account:".length()));
            }
            if (value.startsWith("phone:")) {
                return contactTokenService.deriveDiscoveryPhoneToken(value.substring("phone:".length()));
            }
            return contactTokenService.deriveCurrentVersionedToken(
                    ContactTokenService.Purpose.DISCOVERY_RATE_LIMIT, value);
        } catch (RuntimeException ex) {
            throw ContactDiscoveryException.unavailable();
        }
    }

    private long secondsUntilUtcDayEnd() {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant end = LocalDate.now(ZoneOffset.UTC)
                .plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return Math.max(1, end.getEpochSecond() - now.getEpochSecond());
    }
}
