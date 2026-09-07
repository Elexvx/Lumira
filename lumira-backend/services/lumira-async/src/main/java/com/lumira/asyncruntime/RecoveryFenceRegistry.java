package com.lumira.asyncruntime;

import com.lumira.api.event.RelayExecutionContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * The single runtime authority for recovery and outbox publish fencing.
 *
 * <p>Recovery epochs and relay generations share one Redis hash per owner but
 * have separate fields. Recovery epochs reject stale job requests; relay
 * generations reject already-running async work after a takeover. Keeping
 * both decisions in this component prevents a second, competing lock scheme
 * from appearing in the async lane.</p>
 */
@Component
public class RecoveryFenceRegistry {
    static final String RELAY_GENERATION_FIELD = "relay_generation";
    static final String RELAY_DIGEST_FIELD = "relay_digest";
    static final String RELAY_HOLDER_FIELD = "relay_holder";
    static final String RELAY_LEASE_UNTIL_FIELD = "relay_lease_until";
    static final Duration RELAY_LEASE = Duration.ofSeconds(30L);

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

    /**
     * Atomically renews the current holder, acquires an empty slot, or
     * advances the relay generation after an expired lease. A live different
     * holder returns zero and is never overwritten by a normal async tick.
     */
    private static final DefaultRedisScript<Long> ACQUIRE_RELAY = new DefaultRedisScript<>("""
            local currentGeneration = redis.call('HGET', KEYS[1], 'relay_generation')
            local currentDigest = redis.call('HGET', KEYS[1], 'relay_digest')
            local currentHolder = redis.call('HGET', KEYS[1], 'relay_holder')
            local currentLeaseUntil = redis.call('HGET', KEYS[1], 'relay_lease_until')
            local holder = ARGV[1]
            local digest = ARGV[2]
            local now = tonumber(ARGV[3])
            local leaseUntil = ARGV[4]
            local requestedGeneration = tonumber(ARGV[5])
            local mode = ARGV[6]

            if mode == 'takeover' then
              if ARGV[7] and ARGV[7] ~= '' then
                local recoveryEpoch = redis.call('HGET', KEYS[1], 'epoch')
                local recoveryDigest = redis.call('HGET', KEYS[1], 'digest')
                if (not recoveryEpoch)
                  or tonumber(recoveryEpoch) ~= tonumber(ARGV[7])
                  or recoveryDigest ~= ARGV[8] then
                  return -1
                end
              end
              local nextGeneration = currentGeneration and (tonumber(currentGeneration) + 1) or 1
              redis.call('HSET', KEYS[1],
                'relay_generation', nextGeneration,
                'relay_digest', digest,
                'relay_holder', holder,
                'relay_lease_until', leaseUntil)
              return nextGeneration
            end

            if not currentGeneration then
              redis.call('HSET', KEYS[1],
                'relay_generation', 1,
                'relay_digest', digest,
                'relay_holder', holder,
                'relay_lease_until', leaseUntil)
              return 1
            end

            if currentHolder == holder
              and currentDigest == digest
              and requestedGeneration == tonumber(currentGeneration) then
              redis.call('HSET', KEYS[1], 'relay_lease_until', leaseUntil)
              return tonumber(currentGeneration)
            end

            if (not currentLeaseUntil) or (tonumber(currentLeaseUntil) <= now) then
              local nextGeneration = tonumber(currentGeneration) + 1
              redis.call('HSET', KEYS[1],
                'relay_generation', nextGeneration,
                'relay_digest', digest,
                'relay_holder', holder,
                'relay_lease_until', leaseUntil)
              return nextGeneration
            end

            return 0
            """, Long.class);

    private final ConcurrentMap<String, FenceState> fences = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RelayFenceState> relayFences = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RelayFenceToken> relayTokens = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;

    public RecoveryFenceRegistry() {
        this.redis = null;
    }

    @Autowired
    public RecoveryFenceRegistry(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    /**
     * Production recovery fencing must be backed by the durable runtime Redis.
     * The no-argument constructor remains available only for narrow unit tests
     * and assemblies that intentionally exercise the in-memory fallback.
     */
    boolean isDurable() {
        return redis != null;
    }

    public void assertCurrent(String owner, long operationEpoch, String fenceToken) {
        requireOwner(owner);
        requireEpoch(operationEpoch);
        requireToken(fenceToken);
        String tokenDigest = digest(fenceToken);
        if (redis != null) {
            Long accepted = redis.execute(
                    ADVANCE_FENCE,
                    List.of(redisKey(owner)),
                    Long.toString(operationEpoch),
                    tokenDigest
            );
            if (!Long.valueOf(1L).equals(accepted)) {
                throw new StaleRecoveryFenceException(owner, operationEpoch, redisEpoch(owner));
            }
            return;
        }
        synchronized (fences) {
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
    }

    /**
     * Acquires or renews the normal async holder's publish authority. A live
     * job-recovery holder is never displaced by this method.
     */
    public RelayFenceToken acquireOrRenew(String owner, String holder) {
        requireOwner(owner);
        String normalizedHolder = requireText(holder, "holder");
        long now = System.currentTimeMillis();
        long leaseUntil = now + RELAY_LEASE.toMillis();
        RelayFenceToken active = relayTokens.get(owner);
        String token = active != null && normalizedHolder.equals(active.holder())
                ? active.fenceToken()
                : newToken();
        long requestedGeneration = active != null && normalizedHolder.equals(active.holder())
                ? active.generation()
                : 0L;

        if (redis != null) {
            Long generation = redis.execute(
                    ACQUIRE_RELAY,
                    List.of(redisKey(owner)),
                    normalizedHolder,
                    digest(token),
                    Long.toString(now),
                    Long.toString(leaseUntil),
                    Long.toString(requestedGeneration),
                    "acquire"
            );
            if (generation == null) {
                throw new IllegalStateException("Relay fence acquisition returned no generation for owner " + owner);
            }
            if (generation <= 0L) {
                throw new FenceOwnershipException(owner, normalizedHolder);
            }
            RelayFenceToken acquired = new RelayFenceToken(
                    owner, generation, token, normalizedHolder, Instant.ofEpochMilli(leaseUntil)
            );
            relayTokens.put(owner, acquired);
            return acquired;
        }

        RelayFenceState current = relayFences.get(owner);
        RelayFenceToken acquired;
        if (current == null) {
            acquired = new RelayFenceToken(owner, 1L, token, normalizedHolder, Instant.ofEpochMilli(leaseUntil));
        } else if (current.holder().equals(normalizedHolder)
                && active != null
                && current.generation() == active.generation()
                && current.tokenDigest().equals(digest(active.fenceToken()))) {
            acquired = new RelayFenceToken(
                    owner, current.generation(), active.fenceToken(), normalizedHolder, Instant.ofEpochMilli(leaseUntil)
            );
        } else if (!current.leaseUntil().isAfter(Instant.ofEpochMilli(now))) {
            acquired = new RelayFenceToken(
                    owner, current.generation() + 1L, newToken(), normalizedHolder, Instant.ofEpochMilli(leaseUntil)
            );
        } else {
            throw new FenceOwnershipException(owner, current.holder());
        }
        relayFences.put(owner, toState(acquired));
        relayTokens.put(owner, acquired);
        return acquired;
    }

    /**
     * Forces a new relay generation for a validated recovery request. The
     * caller must first pass {@link #assertCurrent(String, long, String)}.
     */
    public RelayFenceToken takeover(String owner, String holder) {
        return takeover(owner, holder, null, null);
    }

    /**
     * Atomically binds a recovery takeover to the exact recovery epoch/token
     * that was validated immediately before it. This closes the race where an
     * older job request could validate first but take over after a newer job.
     */
    public RelayFenceToken takeover(
            String owner,
            String holder,
            long operationEpoch,
            String recoveryFenceToken
    ) {
        return takeover(owner, holder, Long.valueOf(operationEpoch), recoveryFenceToken);
    }

    private RelayFenceToken takeover(
            String owner,
            String holder,
            Long operationEpoch,
            String recoveryFenceToken
    ) {
        requireOwner(owner);
        String normalizedHolder = requireText(holder, "holder");
        if (operationEpoch != null) {
            requireEpoch(operationEpoch);
            requireToken(recoveryFenceToken);
        }
        String token = newToken();
        long now = System.currentTimeMillis();
        long leaseUntil = now + RELAY_LEASE.toMillis();
        if (redis != null) {
            Long generation = operationEpoch == null
                    ? redis.execute(
                    ACQUIRE_RELAY,
                    List.of(redisKey(owner)),
                    normalizedHolder,
                    digest(token),
                    Long.toString(now),
                    Long.toString(leaseUntil),
                    "0",
                    "takeover"
            )
                    : redis.execute(
                    ACQUIRE_RELAY,
                    List.of(redisKey(owner)),
                    normalizedHolder,
                    digest(token),
                    Long.toString(now),
                    Long.toString(leaseUntil),
                    "0",
                    "takeover",
                    Long.toString(operationEpoch),
                    digest(recoveryFenceToken)
            );
            if (Long.valueOf(-1L).equals(generation)) {
                throw new StaleRecoveryFenceException(owner, operationEpoch, redisEpoch(owner));
            }
            if (generation == null || generation <= 0L) {
                throw new IllegalStateException("Relay fence takeover failed for owner " + owner);
            }
            RelayFenceToken acquired = new RelayFenceToken(
                    owner, generation, token, normalizedHolder, Instant.ofEpochMilli(leaseUntil)
            );
            relayTokens.put(owner, acquired);
            return acquired;
        }

        synchronized (fences) {
            if (operationEpoch != null) {
                FenceState recovery = fences.get(owner);
                String recoveryDigest = digest(recoveryFenceToken);
                if (recovery == null
                        || recovery.operationEpoch() != operationEpoch
                        || !MessageDigest.isEqual(
                        recovery.tokenDigest().getBytes(StandardCharsets.US_ASCII),
                        recoveryDigest.getBytes(StandardCharsets.US_ASCII))) {
                    throw new StaleRecoveryFenceException(
                            owner,
                            operationEpoch,
                            recovery == null ? 0L : recovery.operationEpoch()
                    );
                }
            }
            RelayFenceState current = relayFences.get(owner);
            long generation = current == null ? 1L : current.generation() + 1L;
            RelayFenceToken acquired = new RelayFenceToken(
                    owner, generation, token, normalizedHolder, Instant.ofEpochMilli(leaseUntil)
            );
            relayFences.put(owner, toState(acquired));
            relayTokens.put(owner, acquired);
            return acquired;
        }
    }

    /**
     * Must be called at the async lane's final publish boundary. It is a
     * read-only check, so an old queued task cannot renew or recreate authority.
     */
    public void assertCurrentPublishAuthority(String owner, long generation, String fenceToken) {
        requireOwner(owner);
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        requireToken(fenceToken);
        RelayFenceState current = currentRelayState(owner);
        if (current == null
                || current.generation() != generation
                || !current.leaseUntil().isAfter(Instant.now())
                || !MessageDigest.isEqual(
                current.tokenDigest().getBytes(StandardCharsets.US_ASCII),
                digest(fenceToken).getBytes(StandardCharsets.US_ASCII))) {
            long currentGeneration = current == null ? 0L : current.generation();
            throw new FenceLostException(owner, generation, currentGeneration);
        }
    }

    public RelayFenceState currentRelayState(String owner) {
        requireOwner(owner);
        if (redis != null) {
            Object generation = redis.opsForHash().get(redisKey(owner), RELAY_GENERATION_FIELD);
            if (generation == null) return null;
            Object tokenDigest = redis.opsForHash().get(redisKey(owner), RELAY_DIGEST_FIELD);
            Object holder = redis.opsForHash().get(redisKey(owner), RELAY_HOLDER_FIELD);
            Object leaseUntil = redis.opsForHash().get(redisKey(owner), RELAY_LEASE_UNTIL_FIELD);
            try {
                return new RelayFenceState(
                        owner,
                        Long.parseLong(generation.toString()),
                        requireText(String.valueOf(tokenDigest), "relay token digest"),
                        requireText(String.valueOf(holder), "relay holder"),
                        Instant.ofEpochMilli(Long.parseLong(String.valueOf(leaseUntil)))
                );
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Stored relay fence state is invalid for owner " + owner, exception);
            }
        }
        return relayFences.get(owner);
    }

    long currentEpoch(String owner) {
        requireOwner(owner);
        if (redis != null) return redisEpoch(owner);
        FenceState state = fences.get(owner);
        return state == null ? 0L : state.operationEpoch();
    }

    private long redisEpoch(String owner) {
        Object value = redis.opsForHash().get(redisKey(owner), "epoch");
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Stored recovery fence epoch is invalid for owner " + owner, exception);
        }
    }

    private RelayFenceState toState(RelayFenceToken token) {
        return new RelayFenceState(
                token.owner(), token.generation(), digest(token.fenceToken()), token.holder(), token.leaseUntil()
        );
    }

    private String redisKey(String owner) {
        return "lumira:runtime:recovery-fence:" + owner;
    }

    private String newToken() {
        return UUID.randomUUID() + UUID.randomUUID().toString();
    }

    private void requireOwner(String owner) {
        requireText(owner, "owner");
    }

    private void requireEpoch(long operationEpoch) {
        if (operationEpoch <= 0L) throw new IllegalArgumentException("operationEpoch must be positive");
    }

    private void requireToken(String fenceToken) {
        if (fenceToken == null || fenceToken.length() < 24) {
            throw new IllegalArgumentException("fenceToken must contain at least 24 characters");
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
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

    public record RelayFenceToken(
            String owner,
            long generation,
            String fenceToken,
            String holder,
            Instant leaseUntil
    ) {
        public RelayExecutionContext context() {
            return new RelayExecutionContext(owner, generation, fenceToken, holder);
        }
    }

    public record RelayFenceState(
            String owner,
            long generation,
            String tokenDigest,
            String holder,
            Instant leaseUntil
    ) { }

    public static final class StaleRecoveryFenceException extends RuntimeException {
        StaleRecoveryFenceException(String owner, long attemptedEpoch, long currentEpoch) {
            super("Stale recovery fence owner=" + owner + " attemptedEpoch=" + attemptedEpoch + " currentEpoch=" + currentEpoch);
        }
    }

    public static final class FenceOwnershipException extends RuntimeException {
        FenceOwnershipException(String owner, String currentHolder) {
            super("Relay fence is held by another live holder owner=" + owner + " holder=" + currentHolder);
        }
    }

    public static final class FenceLostException extends RuntimeException {
        FenceLostException(String owner, long attemptedGeneration, long currentGeneration) {
            super("Relay publish fence lost owner=" + owner
                    + " attemptedGeneration=" + attemptedGeneration
                    + " currentGeneration=" + currentGeneration);
        }
    }
}
