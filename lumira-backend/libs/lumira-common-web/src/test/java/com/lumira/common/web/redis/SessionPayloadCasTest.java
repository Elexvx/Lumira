package com.lumira.common.web.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionPayloadCasTest {

    @Test
    void compareAndSetShouldPassAtomicScriptArguments() {
        CapturingRedisTemplate redisTemplate = new CapturingRedisTemplate(1L);
        String payload = "{\"sessionId\":\"s-1\",\"mutationRevision\":8}";

        SessionPayloadCas.Result result = SessionPayloadCas.compareAndSet(
                redisTemplate,
                "saas:session:s-1",
                7L,
                8L,
                payload,
                Duration.ofMillis(1250)
        );

        assertThat(result).isEqualTo(SessionPayloadCas.Result.SAVED);
        assertThat(redisTemplate.keys).containsExactly("saas:session:s-1");
        assertThat(redisTemplate.arguments).containsExactly("7", payload, "1250", "8");
        assertThat(redisTemplate.script.getScriptAsString())
                .contains("redis.call('GET', KEYS[1])")
                .contains("currentRevision == nil or currentRevision == cjson.null")
                .contains("redis.call('PSETEX', KEYS[1], ttlMillis, replacementPayload)");
    }

    @Test
    void compareAndSetShouldMapConflictAndInvalidCurrentPayload() {
        CapturingRedisTemplate conflictTemplate = new CapturingRedisTemplate(0L);
        CapturingRedisTemplate invalidPayloadTemplate = new CapturingRedisTemplate(-1L);
        String payload = "{\"mutationRevision\":1}";

        assertThat(SessionPayloadCas.compareAndSet(
                conflictTemplate,
                "saas:session:s-1",
                0L,
                1L,
                payload,
                Duration.ofSeconds(1)
        )).isEqualTo(SessionPayloadCas.Result.CONFLICT);
        assertThat(SessionPayloadCas.compareAndSet(
                invalidPayloadTemplate,
                "saas:session:s-1",
                0L,
                1L,
                payload,
                Duration.ofSeconds(1)
        )).isEqualTo(SessionPayloadCas.Result.INVALID_CURRENT_PAYLOAD);
    }

    @Test
    void revisionLifecycleShouldDistinguishNewAndLegacySessions() {
        assertThat(SessionPayloadCas.expectedRevision(null)).isEqualTo(-1L);
        assertThat(SessionPayloadCas.nextRevision(null)).isEqualTo(1L);
        assertThat(SessionPayloadCas.normalizeLoadedRevision(null)).isZero();
        assertThat(SessionPayloadCas.expectedRevision(0L)).isZero();
        assertThat(SessionPayloadCas.nextRevision(0L)).isEqualTo(1L);
    }

    @Test
    void compareAndSetShouldRejectNonIncrementingRevision() {
        CapturingRedisTemplate redisTemplate = new CapturingRedisTemplate(1L);

        assertThatThrownBy(() -> SessionPayloadCas.compareAndSet(
                redisTemplate,
                "saas:session:s-1",
                7L,
                9L,
                "{\"mutationRevision\":9}",
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(redisTemplate.script).isNull();
    }

    @Test
    void compareAndDeleteShouldPassExpectedRevisionToAtomicScript() {
        CapturingRedisTemplate redisTemplate = new CapturingRedisTemplate(1L);

        SessionPayloadCas.DeleteResult result = SessionPayloadCas.compareAndDelete(
                redisTemplate,
                "saas:session:s-1",
                8L
        );

        assertThat(result).isEqualTo(SessionPayloadCas.DeleteResult.DELETED);
        assertThat(redisTemplate.keys).containsExactly("saas:session:s-1");
        assertThat(redisTemplate.arguments).containsExactly("8");
        assertThat(redisTemplate.script.getScriptAsString())
                .contains("redis.call('GET', KEYS[1])")
                .contains("currentRevision ~= expectedRevision")
                .contains("redis.call('DEL', KEYS[1])");
    }

    @Test
    void compareAndDeleteShouldMapNonDeletingResults() {
        assertThat(SessionPayloadCas.compareAndDelete(
                new CapturingRedisTemplate(0L),
                "saas:session:s-1",
                8L
        )).isEqualTo(SessionPayloadCas.DeleteResult.CONFLICT);
        assertThat(SessionPayloadCas.compareAndDelete(
                new CapturingRedisTemplate(2L),
                "saas:session:s-1",
                8L
        )).isEqualTo(SessionPayloadCas.DeleteResult.ABSENT);
        assertThat(SessionPayloadCas.compareAndDelete(
                new CapturingRedisTemplate(-1L),
                "saas:session:s-1",
                8L
        )).isEqualTo(SessionPayloadCas.DeleteResult.INVALID_CURRENT_PAYLOAD);
    }

    private static final class CapturingRedisTemplate extends StringRedisTemplate {

        private final Long result;
        private RedisScript<?> script;
        private List<String> keys;
        private Object[] arguments;

        private CapturingRedisTemplate(Long result) {
            this.result = result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            this.script = script;
            this.keys = keys;
            this.arguments = args;
            return (T) result;
        }
    }
}
