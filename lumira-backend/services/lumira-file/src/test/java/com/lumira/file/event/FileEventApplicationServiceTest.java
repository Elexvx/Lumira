package com.lumira.file.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventPayloadDigests;
import com.lumira.api.file.FileObjectUploadedEventCommand;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileEventApplicationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void commitsReceiptAndProjectionInOwnerOrder() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        stubFileAggregate(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), anyRowMapper(), eq(100L))).thenReturn(List.of());

        FileEventApplicationService service = new FileEventApplicationService(jdbcTemplate, objectMapper);

        assertThat(service.handleFileObjectUploaded(command("event-1", 3L))).isTrue();

        var order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).update(contains("insert ignore into file_event_receipt"), any(Object[].class));
        order.verify(jdbcTemplate).queryForObject(contains("from file_object"), eq(Long.class), eq(100L));
        order.verify(jdbcTemplate).update(contains("update file_event_projection set is_current = 0"), eq(100L));
        order.verify(jdbcTemplate).update(contains("insert into file_event_projection"), any(Object[].class));
        order.verify(jdbcTemplate).update(contains("update file_event_receipt"), any(Object[].class));
    }

    @Test
    void duplicateReceiptDoesNotRepeatProjectionSideEffect() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.queryForList(contains("from file_event_receipt"), eq("event-1")))
                .thenReturn(List.of(Map.of(
                        "event_type", "FILE_OBJECT_UPLOADED",
                        "aggregate_id", "100",
                        "aggregate_version", 3L,
                        "payload_digest", command("event-1", 3L).payloadDigest(),
                        "status", "SUCCEEDED"
                )));

        FileEventApplicationService service = new FileEventApplicationService(jdbcTemplate, objectMapper);

        assertThat(service.handleFileObjectUploaded(command("event-1", 3L))).isFalse();
        verify(jdbcTemplate, never()).update(contains("insert into file_event_projection"), any(Object[].class));
    }

    @Test
    void olderEventIsRecordedButCannotBecomeCurrent() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        stubFileAggregate(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), anyRowMapper(), eq(100L))).thenReturn(List.of(3L));

        FileEventApplicationService service = new FileEventApplicationService(jdbcTemplate, objectMapper);

        assertThat(service.handleFileObjectUploaded(command("event-2", 2L))).isTrue();
        verify(jdbcTemplate, never()).update(contains("set is_current = 0"), eq(100L));
        ArgumentCaptor<Object[]> projectionArguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("insert into file_event_projection"), projectionArguments.capture());
        assertThat(projectionArguments.getValue()[5]).isEqualTo(0);
    }

    @Test
    void projectionFailureDoesNotMarkReceiptSuccessful() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(contains("insert ignore into file_event_receipt"), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.update(contains("update file_event_projection set is_current = 0"), any(Object[].class)))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("insert into file_event_projection"), any(Object[].class)))
                .thenThrow(new IllegalStateException("projection unavailable"));
        stubFileAggregate(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), anyRowMapper(), eq(100L))).thenReturn(List.of());

        FileEventApplicationService service = new FileEventApplicationService(jdbcTemplate, objectMapper);

        assertThatThrownBy(() -> service.handleFileObjectUploaded(command("event-3", 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("projection unavailable");
        verify(jdbcTemplate, never()).update(contains("set status = 'SUCCEEDED'"), any(Object[].class));
    }

    @Test
    void rejectsPayloadDigestDriftBeforeClaimingReceipt() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileEventApplicationService service = new FileEventApplicationService(jdbcTemplate, objectMapper);
        FileObjectUploadedEventCommand command = new FileObjectUploadedEventCommand(
                "event-digest-drift",
                "FILE_OBJECT_UPLOADED",
                "file",
                "file",
                "lumira-file",
                "100",
                1L,
                1,
                Instant.parse("2026-09-07T00:00:00Z"),
                null,
                "release-test",
                EventPayloadDigests.sha256("{}"),
                Map.of("fileId", 100L)
        );

        assertThatThrownBy(() -> service.handleFileObjectUploaded(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest");
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @SuppressWarnings("unchecked")
    private RowMapper<Long> anyRowMapper() {
        return (RowMapper<Long>) any(RowMapper.class);
    }

    private void stubFileAggregate(JdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.queryForObject(contains("from file_object"), eq(Long.class), eq(100L)))
                .thenReturn(100L);
    }

    private FileObjectUploadedEventCommand command(String eventId, long version) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileId", 100L);
        payload.put("name", "report.pdf");
        return new FileObjectUploadedEventCommand(
                eventId,
                "FILE_OBJECT_UPLOADED",
                "file",
                "file",
                "lumira-file",
                "100",
                version,
                1,
                Instant.parse("2026-09-07T00:00:00Z"),
                "trace-file-1",
                "release-test",
                EventPayloadDigests.sha256(objectMapper.writeValueAsString(payload)),
                payload
        );
    }
}
