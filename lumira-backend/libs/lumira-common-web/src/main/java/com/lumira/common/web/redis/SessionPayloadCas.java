package com.lumira.common.web.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Shared optimistic-lock protocol for the cross-service Redis auth-session payload.
 *
 * <p>A missing {@code mutationRevision} in an existing payload is the legacy revision {@code 0}.
 * A {@code null} revision on a Java object means that the session has never been persisted and
 * may only create an absent Redis key.</p>
 */
public final class SessionPayloadCas {

    public static final long LEGACY_REVISION = 0L;
    private static final long NEW_SESSION_EXPECTED_REVISION = -1L;
    private static final long MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L;

    private static final DefaultRedisScript<Long> COMPARE_AND_SET_SCRIPT = new DefaultRedisScript<>(
            """
                    local expectedRevision = tonumber(ARGV[1])
                    local replacementPayload = ARGV[2]
                    local ttlMillis = tonumber(ARGV[3])
                    local nextRevision = tonumber(ARGV[4])

                    local replacementOk, replacement = pcall(cjson.decode, replacementPayload)
                    if not replacementOk
                            or type(replacement) ~= 'table'
                            or type(replacement['mutationRevision']) ~= 'number'
                            or replacement['mutationRevision'] ~= nextRevision then
                        return -2
                    end

                    local currentPayload = redis.call('GET', KEYS[1])
                    if expectedRevision == -1 then
                        if currentPayload then
                            return 0
                        end
                    else
                        if not currentPayload then
                            return 0
                        end
                        local currentOk, current = pcall(cjson.decode, currentPayload)
                        if not currentOk or type(current) ~= 'table' then
                            return -1
                        end
                        local currentRevision = current['mutationRevision']
                        if currentRevision == nil or currentRevision == cjson.null then
                            currentRevision = 0
                        end
                        if type(currentRevision) ~= 'number'
                                or currentRevision < 0
                                or currentRevision % 1 ~= 0 then
                            return -1
                        end
                        if currentRevision ~= expectedRevision then
                            return 0
                        end
                    end

                    redis.call('PSETEX', KEYS[1], ttlMillis, replacementPayload)
                    return 1
                    """,
            Long.class
    );

    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            """
                    local expectedRevision = tonumber(ARGV[1])
                    local currentPayload = redis.call('GET', KEYS[1])
                    if not currentPayload then
                        return 2
                    end

                    local currentOk, current = pcall(cjson.decode, currentPayload)
                    if not currentOk or type(current) ~= 'table' then
                        return -1
                    end
                    local currentRevision = current['mutationRevision']
                    if currentRevision == nil or currentRevision == cjson.null then
                        currentRevision = 0
                    end
                    if type(currentRevision) ~= 'number'
                            or currentRevision < 0
                            or currentRevision % 1 ~= 0 then
                        return -1
                    end
                    if currentRevision ~= expectedRevision then
                        return 0
                    end

                    redis.call('DEL', KEYS[1])
                    return 1
                    """,
            Long.class
    );

    private SessionPayloadCas() {
    }

    public static Result compareAndSet(
            StringRedisTemplate redisTemplate,
            String key,
            long expectedRevision,
            long nextRevision,
            String replacementPayload,
            Duration ttl
    ) {
        Objects.requireNonNull(redisTemplate, "redisTemplate");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(replacementPayload, "replacementPayload");
        Objects.requireNonNull(ttl, "ttl");
        validateRevision(expectedRevision, true);
        validateRevision(nextRevision, false);
        long requiredNextRevision = expectedRevision == NEW_SESSION_EXPECTED_REVISION
                ? 1L
                : Math.addExact(expectedRevision, 1L);
        if (nextRevision != requiredNextRevision) {
            throw new IllegalArgumentException("nextRevision must increment expectedRevision");
        }
        long ttlMillis = Math.max(1L, ttl.toMillis());
        Long result = redisTemplate.execute(
                COMPARE_AND_SET_SCRIPT,
                List.of(key),
                Long.toString(expectedRevision),
                replacementPayload,
                Long.toString(ttlMillis),
                Long.toString(nextRevision)
        );
        if (result == null) {
            throw new IllegalStateException("Redis session compare-and-set returned no result");
        }
        return switch (result.intValue()) {
            case 1 -> Result.SAVED;
            case 0 -> Result.CONFLICT;
            case -1 -> Result.INVALID_CURRENT_PAYLOAD;
            case -2 -> throw new IllegalArgumentException("Replacement session payload has an invalid mutation revision");
            default -> throw new IllegalStateException("Unexpected Redis session compare-and-set result: " + result);
        };
    }

    /**
     * Deletes a persisted session only when it is still the exact revision read by the caller.
     * This keeps validation-driven cleanup from deleting a newer role or activity mutation.
     */
    public static DeleteResult compareAndDelete(
            StringRedisTemplate redisTemplate,
            String key,
            long expectedRevision
    ) {
        Objects.requireNonNull(redisTemplate, "redisTemplate");
        Objects.requireNonNull(key, "key");
        validateRevision(expectedRevision, false);
        Long result = redisTemplate.execute(
                COMPARE_AND_DELETE_SCRIPT,
                List.of(key),
                Long.toString(expectedRevision)
        );
        if (result == null) {
            throw new IllegalStateException("Redis session compare-and-delete returned no result");
        }
        return switch (result.intValue()) {
            case 1 -> DeleteResult.DELETED;
            case 0 -> DeleteResult.CONFLICT;
            case 2 -> DeleteResult.ABSENT;
            case -1 -> DeleteResult.INVALID_CURRENT_PAYLOAD;
            default -> throw new IllegalStateException("Unexpected Redis session compare-and-delete result: " + result);
        };
    }

    public static long expectedRevision(Long revision) {
        if (revision == null) {
            return NEW_SESSION_EXPECTED_REVISION;
        }
        validateRevision(revision, false);
        return revision;
    }

    public static long nextRevision(Long revision) {
        long currentRevision = revision == null ? LEGACY_REVISION : revision;
        validateRevision(currentRevision, false);
        if (currentRevision == MAX_SAFE_JSON_INTEGER) {
            throw new IllegalArgumentException("Session mutation revision is exhausted");
        }
        return currentRevision + 1L;
    }

    public static long normalizeLoadedRevision(Long revision) {
        long normalizedRevision = revision == null ? LEGACY_REVISION : revision;
        validateRevision(normalizedRevision, false);
        return normalizedRevision;
    }

    private static void validateRevision(long revision, boolean allowNewSessionSentinel) {
        long minimum = allowNewSessionSentinel ? NEW_SESSION_EXPECTED_REVISION : LEGACY_REVISION;
        if (revision < minimum || revision > MAX_SAFE_JSON_INTEGER) {
            throw new IllegalArgumentException("Session mutation revision is invalid");
        }
    }

    public enum Result {
        SAVED,
        CONFLICT,
        INVALID_CURRENT_PAYLOAD
    }

    public enum DeleteResult {
        DELETED,
        CONFLICT,
        ABSENT,
        INVALID_CURRENT_PAYLOAD
    }
}
