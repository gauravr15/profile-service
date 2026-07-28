package com.odin.profileservice.service;

import com.odin.profileservice.config.ContactDiscoveryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ContactDiscoveryRateLimiterTest {
    private RedisTemplate<String, Object> redis;
    private ValueOperations<String, Object> values;
    private ContactDiscoveryRateLimiter limiter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class, invocation ->
                "execute".equals(invocation.getMethod().getName())
                        ? 0L : RETURNS_DEFAULTS.answer(invocation));
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.hasKey(anyString())).thenReturn(false);
        ContactTokenService tokenService = mock(ContactTokenService.class);
        when(tokenService.deriveDiscoveryAccountToken(anyString()))
            .thenAnswer(invocation -> "account-" + invocation.getArgument(0).hashCode());
        when(tokenService.deriveDiscoveryPhoneToken(anyString()))
            .thenAnswer(invocation -> "phone-" + invocation.getArgument(0).hashCode());
        when(tokenService.deriveCurrentVersionedToken(any(), anyString()))
            .thenAnswer(invocation -> "protected-" + invocation.getArgument(1).hashCode());
        ContactDiscoveryProperties properties = new ContactDiscoveryProperties();
        limiter = new ContactDiscoveryRateLimiter(redis, properties, tokenService);
    }

    @Test
    void accountRateAndDailyUniqueChecksUseProtectedDistributedKeys() {
        limiter.enforce("70", List.of("919900000092", "919900000093"));

        List<List<String>> keyInvocations = mockingDetails(redis).getInvocations().stream()
                .filter(invocation -> "execute".equals(
                        invocation.getMethod().getName()))
                .map(invocation -> (List<String>) invocation.getArgument(1))
                .collect(Collectors.toList());
        assertEquals(2, keyInvocations.size());
        for (List<String> invocationKeys : keyInvocations) {
            String key = invocationKeys.get(0);
            assertFalse(key.endsWith(":70"));
            assertFalse(key.contains("919900000092"));
            assertTrue(key.startsWith("contact-discovery:default:"));
        }
    }

    @Test
    void bulkFixedWindowLimitStillReturnsSafe429() {
        rebuildLimiterWithScriptResults(12L);
        ContactDiscoveryException requestLimit = assertThrows(
                ContactDiscoveryException.class,
                () -> limiter.enforce(
                        "70",
                        List.of("919900000092", "919900000093")));
        assertEquals(429, requestLimit.getStatus().value());
        assertEquals(12, requestLimit.getRetryAfterSeconds());
    }

    @Test
    void bulkDailyUniqueLimitStillReturnsSafe429() {
        rebuildLimiterWithScriptResults(0L, 45L);
        ContactDiscoveryException uniqueLimit = assertThrows(
                ContactDiscoveryException.class,
                () -> limiter.enforce(
                        "70",
                        List.of("919900000092", "919900000093")));
        assertEquals(429, uniqueLimit.getStatus().value());
        assertEquals(45, uniqueLimit.getRetryAfterSeconds());
    }

    @Test
    void redisFailureIsRetryableUnavailableRatherThanUnlimited() {
        when(redis.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("offline"));

        ContactDiscoveryException error = assertThrows(
                ContactDiscoveryException.class,
                () -> limiter.enforce("70", List.of("919900000092")));

        assertEquals(503, error.getStatus().value());
        assertEquals("CONTACT_LOOKUP_UNAVAILABLE", error.getCode());
    }

    @Test
    void singleNumberHighUnmatchedRatioCreatesTemporaryProtectedBlock() {
        when(values.increment(contains(":requests:"))).thenReturn(10L);
        when(values.increment(contains(":unmatched:"), eq(19L))).thenReturn(95L);
        when(values.increment(contains(":inputs:"), eq(20L))).thenReturn(100L);

        limiter.recordOutcome("70", 1, 19, true);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), eq("1"), any(java.time.Duration.class));
        assertTrue(key.getValue().contains(":blocked:"));
        assertFalse(key.getValue().endsWith(":70"));
    }

    @Test
    void thirtyHighUnmatchedBulkBatchesDoNotCreateOrEnforceAbuseBlock() {
        when(redis.hasKey(contains(":blocked:"))).thenReturn(true);

        for (int batch = 0; batch < 30; batch++) {
            limiter.recordOutcome("70", 10, 190, false);
            assertDoesNotThrow(() -> limiter.enforce(
                    "70",
                    List.of("919900000092", "919900000093")));
        }

        verify(redis, never()).hasKey(contains(":blocked:"));
        verify(values, never()).increment(contains(":requests:"));
        verify(values, never()).increment(contains(":unmatched:"), anyLong());
        verify(values, never()).increment(contains(":inputs:"), anyLong());
        verify(values, never()).set(
                contains(":blocked:"),
                any(),
                any(java.time.Duration.class));
    }

    @Test
    void singleNumberRequestStillEnforcesExistingAbuseBlock() {
        when(redis.hasKey(contains(":blocked:"))).thenReturn(true);

        ContactDiscoveryException blocked = assertThrows(
                ContactDiscoveryException.class,
                () -> limiter.enforce("70", List.of("919900000092")));

        assertEquals(429, blocked.getStatus().value());
        assertEquals(900, blocked.getRetryAfterSeconds());
    }

    @SuppressWarnings("unchecked")
    private void rebuildLimiterWithScriptResults(Long... results) {
        AtomicInteger index = new AtomicInteger();
        redis = mock(RedisTemplate.class, invocation -> {
            if ("execute".equals(invocation.getMethod().getName())) {
                int current = index.getAndIncrement();
                return results[Math.min(current, results.length - 1)];
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.hasKey(anyString())).thenReturn(false);
        ContactTokenService tokenService = mock(ContactTokenService.class);
        when(tokenService.deriveDiscoveryAccountToken(anyString()))
            .thenAnswer(invocation -> "account-" + invocation.getArgument(0).hashCode());
        when(tokenService.deriveDiscoveryPhoneToken(anyString()))
            .thenAnswer(invocation -> "phone-" + invocation.getArgument(0).hashCode());
        when(tokenService.deriveCurrentVersionedToken(any(), anyString()))
            .thenAnswer(invocation -> "protected-" + invocation.getArgument(1).hashCode());
        limiter = new ContactDiscoveryRateLimiter(
            redis, new ContactDiscoveryProperties(), tokenService);
    }
}
