package com.odin.profileservice.service;

import com.odin.profileservice.config.ContactDiscoveryProperties;
import com.odin.profileservice.utility.PhoneNumberHasher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfSystemProperty(named = "contact.discovery.redis.port", matches = "\\d+")
class ContactDiscoveryRedisIntegrationTest {
    private static LettuceConnectionFactory factoryA;
    private static LettuceConnectionFactory factoryB;
    private static RedisTemplate<String, Object> redisA;
    private static RedisTemplate<String, Object> redisB;

    @BeforeAll
    static void connect() {
        int port = Integer.getInteger("contact.discovery.redis.port", 6391);
        factoryA = factory(port);
        factoryB = factory(port);
        redisA = template(factoryA);
        redisB = template(factoryB);
        redisA.getConnectionFactory().getConnection().ping();
    }

    @AfterAll
    static void close() {
        if (factoryA != null) {
            factoryA.destroy();
        }
        if (factoryB != null) {
            factoryB.destroy();
        }
    }

    @BeforeEach
    void flush() {
        redisA.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void fixedWindowIsAtomicSharedAndExpires() throws Exception {
        ContactDiscoveryProperties properties = properties("fixed", 20, 100, 1);
        ContactDiscoveryRateLimiter first = limiter(redisA, properties);
        ContactDiscoveryRateLimiter second = limiter(redisB, properties);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                ContactDiscoveryRateLimiter selected = index % 2 == 0 ? first : second;
                calls.add(() -> {
                    try {
                        selected.enforce("70", List.of("919900000092"));
                        return true;
                    } catch (ContactDiscoveryException ex) {
                        assertEquals(429, ex.getStatus().value());
                        return false;
                    }
                });
            }
            int allowed = 0;
            for (Future<Boolean> result : executor.invokeAll(calls)) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertEquals(20, allowed);
            String rateKey = onlyKey("*:rate:single:*");
            assertEquals("40", String.valueOf(redisA.opsForValue().get(rateKey)));
            assertTrue(redisA.getExpire(rateKey) > 0);
            redisA.expire(rateKey, Duration.ofSeconds(1));
        } finally {
            executor.shutdownNow();
        }

        Thread.sleep(1_150);
        first.enforce("70", List.of("919900000092"));
    }

    @Test
    void dailyUniqueSetDeduplicatesAndRollsBackOnlyNewTokens() {
        ContactDiscoveryProperties properties = properties("unique", 100, 3, 60);
        ContactDiscoveryRateLimiter first = limiter(redisA, properties);
        ContactDiscoveryRateLimiter second = limiter(redisB, properties);

        first.enforce("70", List.of("919900000091", "919900000092",
                "919900000092"));
        first.enforce("70", List.of("919900000091"));
        ContactDiscoveryException rejected = assertThrows(
                ContactDiscoveryException.class,
                () -> second.enforce("70",
                        List.of("919900000093", "919900000094")));
        assertEquals(429, rejected.getStatus().value());

        String uniqueKey = onlyKey("*:unique:*");
        Set<Object> members = redisA.opsForSet().members(uniqueKey);
        assertNotNull(members);
        assertEquals(2, members.size());
        assertTrue(redisA.getExpire(uniqueKey) > 0);
        assertTrue(redisA.getExpire(uniqueKey) <= Duration.ofDays(1).getSeconds());
    }

    @Test
    void concurrentUniqueRequestsCannotCrossLimit() throws Exception {
        ContactDiscoveryProperties properties = properties("unique-race", 100, 5, 60);
        ContactDiscoveryRateLimiter first = limiter(redisA, properties);
        ContactDiscoveryRateLimiter second = limiter(redisB, properties);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Void>> calls = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                int value = index;
                calls.add(() -> {
                    try {
                        (value % 2 == 0 ? first : second).enforce(
                                "70", List.of("91990000" + String.format("%04d", value)));
                    } catch (ContactDiscoveryException ex) {
                        assertEquals(429, ex.getStatus().value());
                    }
                    return null;
                });
            }
            for (Future<Void> result : executor.invokeAll(calls)) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(5L, redisA.opsForSet().size(onlyKey("*:unique:*")));
    }

    @Test
    void cooldownIsSharedProtectedAndExpires() throws Exception {
        ContactDiscoveryProperties properties = properties("cooldown", 100, 100, 1);
        properties.setAbuseMinimumRequests(2);
        properties.setAbuseUnmatchedPercent(90);
        ContactDiscoveryRateLimiter first = limiter(redisA, properties);
        ContactDiscoveryRateLimiter second = limiter(redisB, properties);

        first.recordOutcome("70", 0, 10, true);
        first.recordOutcome("70", 0, 10, true);
        ContactDiscoveryException blocked = assertThrows(
                ContactDiscoveryException.class,
                () -> second.enforce("70", List.of("919900000092")));
        assertEquals(429, blocked.getStatus().value());

        String blockedKey = onlyKey("*:blocked:*");
        assertFalse(blockedKey.endsWith(":70"));
        assertFalse(blockedKey.contains("919900000092"));
        assertTrue(redisA.getExpire(blockedKey) > 0);

        Thread.sleep(1_150);
        second.enforce("70", List.of("919900000092"));
    }

    @Test
    void realConnectionRefusalFailsClosedAndRecoveryWorks() {
        LettuceConnectionFactory unavailableFactory = factory(1);
        try {
            RedisTemplate<String, Object> unavailable = template(unavailableFactory);
            ContactDiscoveryException error = assertThrows(
                    ContactDiscoveryException.class,
                    () -> limiter(unavailable, properties("failure", 10, 10, 60))
                            .enforce("70", List.of("919900000092")));
            assertEquals(503, error.getStatus().value());
            assertEquals("CONTACT_LOOKUP_UNAVAILABLE", error.getCode());
            assertFalse(error.getMessage().contains("Redis"));
        } finally {
            unavailableFactory.destroy();
        }

        limiter(redisA, properties("recovery", 10, 10, 60))
                .enforce("70", List.of("919900000092"));
    }

    @Test
    void accountCleanupDeletesKnownProtectedKeysWithoutScanning() {
        ContactDiscoveryProperties properties = properties("cleanup", 100, 100, 60);
        properties.setAbuseMinimumRequests(2);
        properties.setAbuseUnmatchedPercent(90);
        ContactDiscoveryRateLimiter limiter = limiter(redisA, properties);
        limiter.enforce("70", List.of("919900000092"));
        limiter.recordOutcome("70", 0, 10, true);
        limiter.recordOutcome("70", 0, 10, true);
        assertFalse(redisA.keys("contact-discovery:" + properties.getEnvironment() + ":*")
                .isEmpty());

        limiter.clearAccountState("70");

        assertTrue(redisA.keys("contact-discovery:" + properties.getEnvironment() + ":*")
                .isEmpty());
    }

    private static ContactDiscoveryProperties properties(
            String namespace, int perMinute, int dailyUnique, int cooldownSeconds) {
        ContactDiscoveryProperties properties = new ContactDiscoveryProperties();
        properties.setEnvironment("integration-" + namespace + "-" + UUID.randomUUID());
        properties.setMaxNumbers(Math.min(5, dailyUnique));
        properties.setTargetBatchSize(1);
        properties.setSingleRequestsPerMinute(perMinute);
        properties.setBulkRequestsPerMinute(perMinute);
        properties.setDailyUniqueNumbers(dailyUnique);
        properties.setTemporaryBlockSeconds(cooldownSeconds);
        return properties;
    }

    private static ContactDiscoveryRateLimiter limiter(
            RedisTemplate<String, Object> redis,
            ContactDiscoveryProperties properties) {
        PhoneNumberHasher hasher = new PhoneNumberHasher();
        ReflectionTestUtils.setField(hasher, "globalPhonePepper",
            "integration-only-contact-discovery-pepper");
        ContactTokenService tokenService = mock(ContactTokenService.class);
        when(tokenService.deriveDiscoveryAccountToken(anyString()))
            .thenAnswer(invocation -> "account-" + invocation.getArgument(0).hashCode());
        when(tokenService.deriveDiscoveryPhoneToken(anyString()))
            .thenAnswer(invocation -> "phone-" + invocation.getArgument(0).hashCode());
        when(tokenService.deriveCurrentVersionedToken(any(), anyString()))
            .thenAnswer(invocation -> "protected-" + invocation.getArgument(1).hashCode());
        return new ContactDiscoveryRateLimiter(redis, properties, tokenService);
    }

    private static String onlyKey(String pattern) {
        Set<String> keys = redisA.keys(pattern);
        assertNotNull(keys);
        assertEquals(1, keys.size(), "Unexpected keys for " + pattern + ": " + keys);
        return keys.iterator().next();
    }

    private static LettuceConnectionFactory factory(int port) {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration("127.0.0.1", port);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        return factory;
    }

    private static RedisTemplate<String, Object> template(
            LettuceConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericToStringSerializer<>(Object.class));
        template.setHashValueSerializer(new GenericToStringSerializer<>(Object.class));
        template.afterPropertiesSet();
        return template;
    }
}
