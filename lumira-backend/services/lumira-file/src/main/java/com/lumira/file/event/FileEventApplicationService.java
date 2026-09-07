package com.lumira.file.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventPayloadDigests;
import com.lumira.api.file.FileObjectUploadedEventCommand;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * File-owner side effect boundary for lifecycle integration events.
 *
 * <p>The async runtime can retry the HTTP command freely. This service makes
 * the durable File receipt and the File projection one MySQL transaction, so a
 * committed receipt always describes a committed projection.</p>
 */
@Service
public class FileEventApplicationService {

    public static final String CONSUMER_NAME = "file-lifecycle-projection";
    public static final String FILE_OBJECT_UPLOADED = "FILE_OBJECT_UPLOADED";
    public static final String SOURCE_MODULE = "file";
    public static final String PRODUCER = "file";
    public static final String OWNER = "lumira-file";
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FileEventApplicationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional
    public boolean handleFileObjectUploaded(FileObjectUploadedEventCommand command) {
        validate(command);
        String metadataJson = serializePayload(command.payload());
        String computedDigest = EventPayloadDigests.sha256(metadataJson);
        if (!computedDigest.equalsIgnoreCase(command.payloadDigest())) {
            throw new IllegalArgumentException("File event payload digest does not match");
        }

        int claimed = jdbcTemplate.update(
                """
                        insert ignore into file_event_receipt (
                            event_id, event_type, aggregate_id, aggregate_version,
                            payload_digest, status, processed_at
                        ) values (?, ?, ?, ?, ?, 'PROCESSING', null)
                        """,
                command.eventId(),
                command.eventType(),
                command.aggregateId(),
                command.aggregateVersion(),
                command.payloadDigest()
        );
        if (claimed == 0) {
            return handleExistingReceipt(command);
        }

        lockFileAggregate(command.fileId());
        Long currentVersion = currentProjectionVersion(command.fileId());
        boolean current = currentVersion == null || command.aggregateVersion() > currentVersion;
        if (current) {
            jdbcTemplate.update(
                    "update file_event_projection set is_current = 0 where file_id = ? and is_current = 1",
                    command.fileId()
            );
        }
        jdbcTemplate.update(
                """
                        insert into file_event_projection (
                            event_id, file_id, aggregate_version, event_type,
                            metadata, projection_status, is_current, last_event_at
                        ) values (?, ?, ?, ?, ?, 'PROJECTED', ?, ?)
                        """,
                command.eventId(),
                command.fileId(),
                command.aggregateVersion(),
                command.eventType(),
                metadataJson,
                current ? 1 : 0,
                localDateTime(command)
        );
        markReceiptSucceeded(command.eventId());
        return true;
    }

    private boolean handleExistingReceipt(FileObjectUploadedEventCommand command) {
        List<Map<String, Object>> receipts = jdbcTemplate.queryForList(
                """
                        select event_type, aggregate_id, aggregate_version, payload_digest, status
                        from file_event_receipt
                        where event_id = ?
                        limit 1
                        """,
                command.eventId()
        );
        if (receipts.isEmpty()) {
            throw new IllegalStateException("File event receipt disappeared after duplicate claim");
        }
        Map<String, Object> receipt = receipts.getFirst();
        if (!command.eventType().equals(String.valueOf(receipt.get("event_type")))
                || !command.aggregateId().equals(String.valueOf(receipt.get("aggregate_id")))
                || command.aggregateVersion() != numberValue(receipt.get("aggregate_version"))
                || !command.payloadDigest().equalsIgnoreCase(String.valueOf(receipt.get("payload_digest")))) {
            throw new IllegalStateException("File event id was reused with a different payload");
        }
        String status = String.valueOf(receipt.get("status"));
        if (!"SUCCEEDED".equalsIgnoreCase(status)) {
            throw new IllegalStateException("File event receipt is still processing");
        }
        return false;
    }

    private Long currentProjectionVersion(long fileId) {
        List<Long> versions = jdbcTemplate.query(
                """
                        select aggregate_version
                        from file_event_projection
                        where file_id = ? and is_current = 1
                        order by aggregate_version desc
                        limit 1
                        """,
                (resultSet, rowNum) -> {
                    long value = resultSet.getLong(1);
                    return resultSet.wasNull() ? null : value;
                },
                fileId
        );
        return versions.isEmpty() ? null : versions.getFirst();
    }

    private void lockFileAggregate(long fileId) {
        Long lockedId = jdbcTemplate.queryForObject(
                "select id from file_object where id = ? for update",
                Long.class,
                fileId
        );
        if (lockedId == null) {
            throw new IllegalArgumentException("File aggregate does not exist: " + fileId);
        }
    }

    private void markReceiptSucceeded(String eventId) {
        int updated = jdbcTemplate.update(
                """
                        update file_event_receipt
                        set status = 'SUCCEEDED', processed_at = current_timestamp(6)
                        where event_id = ? and status = 'PROCESSING'
                        """,
                eventId
        );
        if (updated != 1) {
            throw new IllegalStateException("File event receipt could not be completed");
        }
    }

    private void validate(FileObjectUploadedEventCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("File event command is required");
        }
        if (!FILE_OBJECT_UPLOADED.equals(command.eventType())) {
            throw new IllegalArgumentException("Unsupported File event type");
        }
        if (!SOURCE_MODULE.equals(command.sourceModule())) {
            throw new IllegalArgumentException("File event sourceModule must be file");
        }
        if (!PRODUCER.equals(command.producer())) {
            throw new IllegalArgumentException("File event producer must be file");
        }
        if (!OWNER.equals(command.owner())) {
            throw new IllegalArgumentException("File event owner must be lumira-file");
        }
        if (command.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported File event schema version " + command.schemaVersion());
        }
        command.fileId();
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("File event payload is not serializable", exception);
        }
    }

    private LocalDateTime localDateTime(FileObjectUploadedEventCommand command) {
        return LocalDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC);
    }

    private long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("File event receipt aggregate version is invalid", exception);
        }
    }
}
