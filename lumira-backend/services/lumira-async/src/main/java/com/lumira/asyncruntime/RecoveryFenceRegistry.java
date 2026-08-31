package com.lumira.asyncruntime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Rejects stale recovery writers before they can invoke an owner relay. */
@Component
public class RecoveryFenceRegistry {
    private static final DefaultRedisScript<Long> ADVANCE_FENCE = new DefaultRedisScript<>("""
            local currentEpoch = redis.call('HGET', KEYS[1], 'epoch')
            local currentDigest = redis.call('HGET', KEYS[1], 'digest')
            if (not currentEpoch) or (tonumber(ARGV[1]) > tonumber(currentEpoch)) then
              redis.call('HSET', KEYS[1], 'epoch', ARGV[1], 'digest', ARGV[2])
              return 1
            end
            if (tonumber(ARGV[1]) == tonumber(currentEpoch)) and (ARGV[2] == currentDigest) then
              return 1
            end
            return 0
            """, Long.class);

    private final ConcurrentMap<String, FenceState> fences = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;

    public RecoveryFenceRegistry() {
        this.redis = null;
    }

    @Autowired
    public RecoveryFenceRegistry(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public void assertCurrent(String owner, long operationEpoch, String fenceToken) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner is required");
        if (operationEpoch <= 0L) throw new IllegalArgumentException("operationEpoch must be positive");
        if (fenceToken == null || fenceToken.length() < 24) {
            throw new IllegalArgumentException("fenceToken must contain at least 24 characters");
        }
        String tokenDigest = digest(fenceToken);
        if (redis != null) {
            Long accepted = redis.execute(
                    ADVANCE_FENCE,
                    java.util.List.of("lumira:runtime:recovery-fence:" + owner),
                    Long.toString(operationEpoch),
                    tokenDigest
            );
            if (!Long.valueOf(1L).equals(accepted)) {
                throw new StaleRecoveryFenceException(owner, operationEpoch, redisEpoch(owner));
            }
            return;
        }
        fences.compute(owner, (ignored, current) -> {
            if (current == null || operationEpoch > current.operationEpoch()) {
                return new FenceState(operationEpoch, tokenDigest);
            }
            if (operationEpoch == current.operationEpoch()
                    && MessageDigest.isEqual(
                    tokenDigest.getBytes(StandardCharsets.US_ASCII),
                    current.tokenDigest().getBytes(StandardCharsets.US_ASCII))) {
                return current;
            }
            throw new StaleRecoveryFenceException(owner, operationEpoch, current.operationEpoch());
        });
    }

    long currentEpoch(String owner) {
        if (redis != null) return redisEpoch(owner);
        FenceState state = fences.get(owner);
        return state == null ? 0L : state.operationEpoch();
    }

    private long redisEpoch(String owner) {
        Object value = redis.opsForHash().get("lumira:runtime:recovery-fence:" + owner, "epoch");
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Stored recovery fence epoch is invalid for owner " + owner, exception);
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record FenceState(long operationEpoch, String tokenDigest) { }

    public static final class StaleRecoveryFenceException extends RuntimeException {
        StaleRecoveryFenceException(String owner, long attemptedEpoch, long currentEpoch) {
            super("Stale recovery fence owner=" + owner + " attemptedEpoch=" + attemptedEpoch + " currentEpoch=" + currentEpoch);
        }
    }
}
