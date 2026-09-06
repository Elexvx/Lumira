package com.lumira.asyncruntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.iam.AuthorizationRuntimeKeys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamAuthorizationInvalidationConsumerTest {

    @Test
    void roleChangedDeletesRuntimeAuthorizationVersionsAndAcknowledges() {
        TestContext context = testContext();
        when(context.receipts.setIfAbsent(anyString(), eq("1"), eq(Duration.ofDays(30))))
                .thenReturn(true);

        context.consumer.onMessage(validMessage(RecordId.of("1-0"), IamAuthorizationInvalidationConsumer.ROLE_CHANGED, 1));

        verify(context.redis).delete(List.of(
                AuthorizationRuntimeKeys.role(42L),
                AuthorizationRuntimeKeys.roleDataPolicy(42L)
        ));
        verify(context.stream).acknowledge(
                IamAuthorizationInvalidationConsumer.STREAM,
                IamAuthorizationInvalidationConsumer.GROUP,
                RecordId.of("1-0")
        );
        assertThat(context.meters.get("lumira.iam.authz.consumer.events.consumed").counter().count())
                .isEqualTo(1.0d);
        assertThat(context.meters.get("iam_event_invalidation_success_total").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void duplicateEventUsesRuntimeReceiptWithoutRepeatingTheInvalidation() {
        TestContext context = testContext();
        when(context.redis.hasKey(anyString())).thenReturn(false, true);
        when(context.receipts.setIfAbsent(anyString(), eq("1"), eq(Duration.ofDays(30))))
                .thenReturn(true);

        context.consumer.onMessage(validMessage(RecordId.of("2-0"), IamAuthorizationInvalidationConsumer.ROLE_CHANGED, 1));
        context.consumer.onMessage(validMessage(RecordId.of("3-0"), IamAuthorizationInvalidationConsumer.ROLE_CHANGED, 1));

        verify(context.redis).delete(anyCollection());
        verify(context.stream).acknowledge(
                IamAuthorizationInvalidationConsumer.STREAM,
                IamAuthorizationInvalidationConsumer.GROUP,
                RecordId.of("3-0")
        );
        assertThat(context.meters.get("lumira.iam.authz.consumer.events.duplicate").counter().count())
                .isEqualTo(1.0d);
        assertThat(context.meters.get("iam_event_duplicate_total").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void permissionPolicyChangedAlsoInvalidatesGlobalDataPolicy() {
        TestContext context = testContext();
        when(context.receipts.setIfAbsent(anyString(), eq("1"), eq(Duration.ofDays(30))))
                .thenReturn(true);

        context.consumer.onMessage(validMessage(
                RecordId.of("4-0"),
                IamAuthorizationInvalidationConsumer.PERMISSION_POLICY_CHANGED,
                1
        ));

        verify(context.redis).delete(List.of(
                AuthorizationRuntimeKeys.role(42L),
                AuthorizationRuntimeKeys.roleDataPolicy(42L),
                AuthorizationRuntimeKeys.globalDataPolicy()
        ));
    }

    @Test
    void unsupportedSchemaIsDeadLetteredAndAcknowledged() {
        TestContext context = testContext();
        RecordId id = RecordId.of("5-0");
        MapRecord<String, String, String> message = message(
                id,
                IamAuthorizationInvalidationConsumer.ROLE_CHANGED,
                "2",
                "42"
        );

        context.consumer.onMessage(message);

        verify(context.redis, never()).delete(anyCollection());
        verify(context.stream).add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class));
        verify(context.stream).acknowledge(IamAuthorizationInvalidationConsumer.STREAM, IamAuthorizationInvalidationConsumer.GROUP, id);
        assertThat(context.meters.get("lumira.iam.authz.consumer.events.dead-letter").counter().count())
                .isEqualTo(1.0d);
        assertThat(context.meters.get("iam_event_dlq_total").counter().count())
                .isEqualTo(1.0d);
        assertThat(context.meters.get("iam_event_schema_reject_total").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void runtimeRedisFailureLeavesTheMessagePendingForRetry() {
        TestContext context = testContext();
        doThrow(new IllegalStateException("runtime redis unavailable"))
                .when(context.redis).delete(anyCollection());
        RecordId id = RecordId.of("6-0");

        context.consumer.onMessage(validMessage(id, IamAuthorizationInvalidationConsumer.ROLE_CHANGED, 1));

        verify(context.stream, never()).acknowledge(
                IamAuthorizationInvalidationConsumer.STREAM,
                IamAuthorizationInvalidationConsumer.GROUP,
                id
        );
        verify(context.stream, never()).add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class));
    }

    @SuppressWarnings("unchecked")
    private TestContext testContext() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> stream = mock(StreamOperations.class);
        ValueOperations<String, String> receipts = mock(ValueOperations.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        doAnswer(ignored -> stream).when(redis).opsForStream();
        doAnswer(ignored -> receipts).when(redis).opsForValue();
        IamAuthorizationInvalidationConsumer consumer = new IamAuthorizationInvalidationConsumer(
                mock(RedisConnectionFactory.class),
                redis,
                new ObjectMapper(),
                meters,
                IamAuthorizationInvalidationConsumer.STREAM,
                IamAuthorizationInvalidationConsumer.GROUP,
                "consumer-1",
                IamAuthorizationInvalidationConsumer.DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE,
                IamAuthorizationInvalidationConsumer.DEFAULT_PENDING_RECOVERY_INTERVAL,
                IamAuthorizationInvalidationConsumer.DEFAULT_MAX_DELIVERY_COUNT
        );
        return new TestContext(consumer, redis, stream, receipts, meters);
    }

    private MapRecord<String, String, String> validMessage(
            RecordId id,
            String eventType,
            int schemaVersion
    ) {
        return message(id, eventType, String.valueOf(schemaVersion), "42");
    }

    private MapRecord<String, String, String> message(
            RecordId id,
            String eventType,
            String schemaVersion,
            String aggregateId
    ) {
        String payload = """
                {"schemaVersion":%s,"aggregateType":"iam.role","aggregateId":"%s","attributes":{
                  "eventId":"iam-event-1","sourceModule":"iam","producer":"iam","owner":"lumira-system"
                }}
                """.formatted(schemaVersion, aggregateId);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("eventType", eventType);
        values.put("eventId", "iam-event-1");
        values.put("sourceModule", "iam");
        values.put("payloadJson", payload);
        return MapRecord.create(IamAuthorizationInvalidationConsumer.STREAM, values).withId(id);
    }

    private record TestContext(
            IamAuthorizationInvalidationConsumer consumer,
            StringRedisTemplate redis,
            StreamOperations<String, String, String> stream,
            ValueOperations<String, String> receipts,
            SimpleMeterRegistry meters
    ) {
    }
}
