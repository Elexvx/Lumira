package com.lumira.asyncruntime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventPayloadDigests;
import com.lumira.api.file.FileEventCommandPort;
import com.lumira.api.file.FileObjectUploadedEventCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileLifecycleConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validUploadIsDelegatedToTheFileOwnerAndAcknowledged() throws Exception {
        TestContext context = testContext();
        when(context.commandPort.handleUploaded(any())).thenReturn(true);
        RecordId id = RecordId.of("1-0");

        context.consumer.onMessage(validMessage(id, 3L));

        verify(context.commandPort).handleUploaded(any(FileObjectUploadedEventCommand.class));
        verify(context.stream).acknowledge(FileLifecycleConsumer.STREAM, FileLifecycleConsumer.GROUP, id);
        assertThat(context.meters.get("file_event_projection_success_total").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void repeatedDeliveryIsDelegatedAgainButTransportStillAcknowledgesIt() throws Exception {
        TestContext context = testContext();
        when(context.commandPort.handleUploaded(any())).thenReturn(true, false);
        RecordId first = RecordId.of("2-0");
        RecordId second = RecordId.of("3-0");

        context.consumer.onMessage(validMessage(first, 3L));
        context.consumer.onMessage(validMessage(second, 3L));

        verify(context.commandPort, org.mockito.Mockito.times(2)).handleUploaded(any(FileObjectUploadedEventCommand.class));
        verify(context.stream).acknowledge(FileLifecycleConsumer.STREAM, FileLifecycleConsumer.GROUP, first);
        verify(context.stream).acknowledge(FileLifecycleConsumer.STREAM, FileLifecycleConsumer.GROUP, second);
        assertThat(context.meters.get("file_event_duplicate_total").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void unsupportedSchemaIsDeadLetteredAndAcknowledged() throws Exception {
        TestContext context = testContext();
        RecordId id = RecordId.of("4-0");

        context.consumer.onMessage(message(id, 2L, 3L));

        verify(context.commandPort, never()).handleUploaded(any());
        verify(context.stream).add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class));
        verify(context.stream).acknowledge(FileLifecycleConsumer.STREAM, FileLifecycleConsumer.GROUP, id);
        assertThat(context.meters.get("file_event_schema_reject_total").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void ownerCommandFailureLeavesTheStreamEntryPending() throws Exception {
        TestContext context = testContext();
        doThrow(new IllegalStateException("file owner unavailable"))
                .when(context.commandPort).handleUploaded(any());
        RecordId id = RecordId.of("5-0");

        context.consumer.onMessage(validMessage(id, 1L));

        verify(context.stream, never()).acknowledge(FileLifecycleConsumer.STREAM, FileLifecycleConsumer.GROUP, id);
        verify(context.stream, never()).add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class));
        assertThat(context.meters.get("lumira.file.consumer.events.failed").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void parserPreservesAggregateVersionForOutOfOrderOwnerDecisions() throws Exception {
        TestContext context = testContext();

        FileObjectUploadedEventCommand newer = context.consumer.parse(
                validMessage(RecordId.of("6-0"), 3L).getValue(),
                RecordId.of("6-0")
        );
        FileObjectUploadedEventCommand older = context.consumer.parse(
                validMessage(RecordId.of("7-0"), 2L).getValue(),
                RecordId.of("7-0")
        );

        assertThat(newer.aggregateVersion()).isEqualTo(3L);
        assertThat(older.aggregateVersion()).isEqualTo(2L);
        assertThat(newer.fileId()).isEqualTo(100L);
    }

    private TestContext testContext() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> stream = mock(StreamOperations.class);
        FileEventCommandPort commandPort = mock(FileEventCommandPort.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        doAnswer(ignored -> stream).when(redis).opsForStream();
        FileLifecycleConsumer consumer = new FileLifecycleConsumer(
                mock(RedisConnectionFactory.class),
                redis,
                objectMapper,
                commandPort,
                meters,
                FileLifecycleConsumer.STREAM,
                FileLifecycleConsumer.GROUP,
                "consumer-1",
                FileLifecycleConsumer.DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE,
                FileLifecycleConsumer.DEFAULT_PENDING_RECOVERY_INTERVAL,
                FileLifecycleConsumer.DEFAULT_MAX_DELIVERY_COUNT
        );
        return new TestContext(consumer, commandPort, stream, meters);
    }

    private MapRecord<String, String, String> validMessage(RecordId id, long aggregateVersion)
            throws JsonProcessingException {
        return message(id, 1L, aggregateVersion);
    }

    private MapRecord<String, String, String> message(
            RecordId id,
            long schemaVersion,
            long aggregateVersion
    ) throws JsonProcessingException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileId", 100L);
        body.put("name", "report.pdf");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", "file-event-1");
        envelope.put("eventType", FileLifecycleConsumer.EVENT_TYPE);
        envelope.put("sourceModule", FileLifecycleConsumer.SOURCE_MODULE);
        envelope.put("producer", FileLifecycleConsumer.PRODUCER);
        envelope.put("owner", FileLifecycleConsumer.OWNER);
        envelope.put("aggregateId", "100");
        envelope.put("aggregateVersion", aggregateVersion);
        envelope.put("schemaVersion", schemaVersion);
        envelope.put("occurredAt", Instant.parse("2026-09-07T00:00:00Z").toString());
        envelope.put("traceId", "trace-file-1");
        envelope.put("releaseId", "release-test");
        envelope.put("payloadDigest", EventPayloadDigests.sha256(objectMapper.writeValueAsString(body)));
        envelope.put("payload", body);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("eventType", FileLifecycleConsumer.EVENT_TYPE);
        values.put("eventId", "file-event-1");
        values.put("sourceModule", FileLifecycleConsumer.SOURCE_MODULE);
        values.put("aggregateId", "100");
        values.put("payload", objectMapper.writeValueAsString(envelope));
        return MapRecord.create(FileLifecycleConsumer.STREAM, values).withId(id);
    }

    private record TestContext(
            FileLifecycleConsumer consumer,
            FileEventCommandPort commandPort,
            StreamOperations<String, String, String> stream,
            SimpleMeterRegistry meters
    ) {
    }
}
