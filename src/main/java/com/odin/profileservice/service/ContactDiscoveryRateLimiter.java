package com.odin.profileservice.service;

import com.odin.profileservice.config.ContactDiscoveryProperties;
import com.odin.profileservice.service.ContactTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
@Slf4j
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
    private static final DefaultRedisScript<String> UNIQUE_DIAGNOSTIC =
            new DefaultRedisScript<>(
                    "local before=redis.call('SCARD',KEYS[1]);"
                            + "local missing=0;"
                            + "for i=1,#ARGV do "
                            + " if redis.call('SISMEMBER',KEYS[1],ARGV[i])==0 then "
                            + "  missing=missing+1;"
                            + " end;"
                            + "end;"
                            + "return tostring(before)..':'..tostring(before+missing);",
                    String.class);

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
        String blockedKey = prefix + "blocked:" + accountToken;
        try {
            if (single && Boolean.TRUE.equals(redisTemplate.hasKey(blockedKey))) {
                String abuseRequestsKey =
                        prefix + "abuse:requests:" + accountToken;
                long count = safeNumericValue(abuseRequestsKey);
                logRejection(
                        "ABUSE_BLOCK",
                        accountToken,
                        count,
                        count,
                        properties.getAbuseMinimumRequests(),
                        safeTtl(blockedKey));
                throw ContactDiscoveryException.rateLimited(
                        properties.getTemporaryBlockSeconds());
            }
            String rateKey =
                    prefix + "rate:" + category + ":" + accountToken;
            Long requestRetry = redisTemplate.execute(
                    FIXED_WINDOW,
                    Collections.singletonList(rateKey),
                    60, limit);
            if (requestRetry != null && requestRetry > 0) {
                long countAfter = safeNumericValue(rateKey);
                logRejection(
                        "FIXED_WINDOW",
                        accountToken,
                        countAfter > 0 ? countAfter - 1 : -1,
                        countAfter,
                        limit,
                        requestRetry);
                throw ContactDiscoveryException.rateLimited(requestRetry.intValue());
            }

            List<Object> arguments = new ArrayList<>();
            arguments.add(secondsUntilUtcDayEnd());
            arguments.add(properties.getDailyUniqueNumbers());
            for (String number : canonicalNumbers) {
                arguments.add(protectedToken("phone:" + number));
            }
            String uniqueKey =
                    prefix + "unique:" + LocalDate.now(ZoneOffset.UTC)
                            + ":" + accountToken;
            Long uniqueRetry = redisTemplate.execute(
                    UNIQUE_WINDOW,
                    Collections.singletonList(uniqueKey),
                    arguments.toArray());
            if (uniqueRetry != null && uniqueRetry > 0) {
                long[] counts = uniqueCounts(
                        uniqueKey, arguments.subList(2, arguments.size()));
                logRejection(
                        "DAILY_UNIQUE",
                        accountToken,
                        counts[0],
                        counts[1],
                        properties.getDailyUniqueNumbers(),
                        uniqueRetry);
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

    public void recordOutcome(
            String customerId,
            int matched,
            int unmatched,
            boolean singleNumberRequest) {
        if (!singleNumberRequest) {
            return;
        }
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

    private long[] uniqueCounts(String uniqueKey, List<Object> protectedNumbers) {
        try {
            String diagnostic = redisTemplate.execute(
                    UNIQUE_DIAGNOSTIC,
                    Collections.singletonList(uniqueKey),
                    protectedNumbers.toArray());
            if (diagnostic == null) {
                return new long[] {-1, -1};
            }
            String[] values = diagnostic.split(":", 2);
            return new long[] {
                    Long.parseLong(values[0]),
                    Long.parseLong(values[1])
            };
        } catch (RuntimeException ignored) {
            return new long[] {-1, -1};
        }
    }

    private long safeTtl(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key);
            return ttl == null ? -1 : ttl;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private long numericValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private long safeNumericValue(String key) {
        try {
            return numericValue(redisTemplate.opsForValue().get(key));
        } catch (RuntimeException exception) {
            return -1L;
        }
    }

    private void logRejection(
            String branch,
            String protectedAccountIdentifier,
            long countBefore,
            long countAfter,
            long configuredLimit,
            long remainingTtl) {
        log.warn(
                "[CONTACT_LIMITER_DIAG] traceId={} limiterBranch={} "
                        + "protectedAccountKeyId={} countBefore={} countAfter={} "
                        + "configuredLimit={} remainingTTL={} decision=REJECT",
                traceId(),
                branch,
                protectedAccountIdentifier,
                countBefore,
                countAfter,
                configuredLimit,
                remainingTtl);
    }

    private String traceId() {
        String traceId = MDC.get("correlationId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("traceId");
        }
        return traceId == null || traceId.isBlank() ? "unknown" : traceId;
    }
}
