package com.lumira.common.web.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Final owner-side check for an async relay request.
 *
 * <p>The async lane check protects the normal scheduler process. This check
 * runs after authentication and immediately before the owner invokes its
 * outbox claim/dispatch code, so a delayed HTTP request cannot publish with a
 * generation that a recovery takeover has already replaced.</p>
 */
public final class RelayFenceValidator {
    public static final String OWNER_HEADER = "X-Lumira-Relay-Owner";
    public static final String GENERATION_HEADER = "X-Lumira-Relay-Generation";
    public static final String FENCE_HEADER = "X-Lumira-Relay-Fence";

    private static final String KEY_PREFIX = "lumira:runtime:recovery-fence:";
    private static final String GENERATION_FIELD = "relay_generation";
    private static final String DIGEST_FIELD = "relay_digest";
    private static final String LEASE_UNTIL_FIELD = "relay_lease_until";

    private RelayFenceValidator() {
    }

    public static void assertCurrent(
            StringRedisTemplate redis,
            String expectedOwner,
            String relayOwner,
            Long generation,
            String fenceToken
    ) {
        if (redis == null) {
            throw new StaleRelayFenceException(expectedOwner, generation, "Runtime Redis is unavailable");
        }
        if (expectedOwner == null || expectedOwner.isBlank()
                || relayOwner == null || !expectedOwner.equals(relayOwner.trim())) {
            throw new StaleRelayFenceException(expectedOwner, generation, "Relay owner header is invalid");
        }
        if (generation == null || generation <= 0L) {
            throw new StaleRelayFenceException(expectedOwner, generation, "Relay generation header is invalid");
        }
        if (fenceToken == null || fenceToken.length() < 24) {
            throw new StaleRelayFenceException(expectedOwner, generation, "Relay fence header is invalid");
        }

        String key = KEY_PREFIX + expectedOwner;
        Object storedGeneration = redis.opsForHash().get(key, GENERATION_FIELD);
        Object storedDigest = redis.opsForHash().get(key, DIGEST_FIELD);
        Object storedLeaseUntil = redis.opsForHash().get(key, LEASE_UNTIL_FIELD);
        try {
            long currentGeneration = storedGeneration == null
                    ? 0L
                    : Long.parseLong(storedGeneration.toString());
            long leaseUntil = storedLeaseUntil == null
                    ? 0L
                    : Long.parseLong(storedLeaseUntil.toString());
            String currentDigest = storedDigest == null ? "" : storedDigest.toString();
            boolean current = currentGeneration == generation
                    && leaseUntil > System.currentTimeMillis()
                    && MessageDigest.isEqual(
                    currentDigest.getBytes(StandardCharsets.US_ASCII),
                    digest(fenceToken).getBytes(StandardCharsets.US_ASCII));
            if (!current) {
                throw new StaleRelayFenceException(expectedOwner, generation,
                        "Relay generation or lease is no longer current");
            }
        } catch (NumberFormatException exception) {
            throw new StaleRelayFenceException(expectedOwner, generation, "Stored relay fence state is invalid");
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static final class StaleRelayFenceException extends RuntimeException {
        public StaleRelayFenceException(String owner, Long attemptedGeneration, String reason) {
            super("Stale relay fence owner=" + owner
                    + " attemptedGeneration=" + attemptedGeneration + ": " + reason);
        }
    }
}
