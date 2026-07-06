package com.lumira.file.processing;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Lazy
@Service
public class FileAiParseProcessor {

    public static final String ARTIFACT_AI_PARSE_READY = "AI_PARSE_READY";
    private static final int MAX_SUMMARY_CHARS = 1_000;

    private final JdbcTemplate jdbcTemplate;

    public FileAiParseProcessor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AiParseResult prepareForAiParse(Long fileId, Long userId) {
        throw new IllegalStateException("File AI parse owner UUID is required");
    }

    public AiParseResult prepareForAiParse(Long fileId, Long userId, String userUuid) {
        Long ownerId = requireUserId(userId);
        String ownerUuid = requireUserUuid(userUuid);
        TextArtifact textArtifact = findTextArtifact(fileId, ownerId, ownerUuid);
        if (textArtifact == null || !StringUtils.hasText(textArtifact.contentText())) {
            throw new IllegalStateException("TEXT_CONTENT artifact is unavailable for AI parse: " + fileId);
        }
        String normalizedText = normalizeWhitespace(textArtifact.contentText());
        String summary = normalizedText.length() > MAX_SUMMARY_CHARS
                ? normalizedText.substring(0, MAX_SUMMARY_CHARS)
                : normalizedText;
        String payload = buildPayload(textArtifact, summary, normalizedText.length());
        upsertArtifact(fileId, payload, ownerId, userUuid);
        return new AiParseResult(fileId, normalizedText.length(), summary.length());
    }

    private TextArtifact findTextArtifact(Long fileId, Long userId, String userUuid) {
        return jdbcTemplate.queryForObject(
                """
                        select fpa.content_text as contentText, fpa.content_length as contentLength
                        from file_processing_artifact fpa
                        join file_object fo
                          on fo.id = fpa.file_id
                         and fo.deleted = 0
                         and fo.status = 'ENABLED'
                         and fo.uploaded_by = ?
                         and fo.uploaded_by_uuid = ?
                         and fpa.created_by = fo.uploaded_by
                        join sys_user u
                          on u.id = fo.uploaded_by
                         and u.uuid = fo.uploaded_by_uuid
                         and u.deleted = 0
                         and u.status = 'ENABLED'
                        where fpa.file_id = ?
                          and fpa.artifact_type = ?
                          and fpa.deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new TextArtifact(
                        rs.getString("contentText"),
                        rs.getInt("contentLength")
                ),
                userId,
                userUuid,
                fileId,
                FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT
        );
    }

    private String buildPayload(TextArtifact textArtifact, String summary, int normalizedLength) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("sourceArtifactType", FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT);
        payload.put("summary", summary);
        payload.put("normalizedLength", normalizedLength);
        payload.put("sourceLength", textArtifact.contentLength());
        return payload.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + escape(String.valueOf(entry.getValue())) + "\"")
                .reduce("{", (left, right) -> "{".equals(left) ? left + right : left + "," + right)
                + "}";
    }

    private void upsertArtifact(Long fileId, String payload, Long userId, String userUuid) {
        Long ownerId = requireUserId(userId);
        String ownerUuid = requireUserUuid(userUuid);
        int updated = jdbcTemplate.update(
                """
                        insert into file_processing_artifact (
                            file_id, task_type, artifact_type, content_text, content_length,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        )
                        select ?, ?, ?, ?, ?, ?, ?, ?, ?, 0
                        from file_object fo
                        join sys_user u
                          on u.id = fo.uploaded_by
                         and u.uuid = fo.uploaded_by_uuid
                         and u.deleted = 0
                         and u.status = 'ENABLED'
                        where fo.id = ?
                          and fo.uploaded_by = ?
                          and fo.uploaded_by_uuid = ?
                          and fo.deleted = 0
                          and fo.status = 'ENABLED'
                        on duplicate key update
                            task_type = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then values(task_type) else task_type end,
                            content_text = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then values(content_text) else content_text end,
                            content_length = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then values(content_length) else content_length end,
                            deleted = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then 0 else deleted end,
                            updated_at = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then current_timestamp else updated_at end,
                            updated_by = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then values(updated_by) else updated_by end,
                            updated_by_uuid = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then values(updated_by_uuid) else updated_by_uuid end
                        """,
                fileId,
                FileProcessingTaskService.TASK_AI_PARSE,
                ARTIFACT_AI_PARSE_READY,
                payload,
                payload == null ? 0 : payload.length(),
                ownerId,
                ownerUuid,
                ownerId,
                ownerUuid,
                fileId,
                ownerId,
                ownerUuid
        );
        if (updated <= 0) {
            throw new IllegalStateException("File AI parse artifact state changed, please retry");
        }
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalStateException("File processing artifact owner is required");
        }
        return userId;
    }

    private String requireUserUuid(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new IllegalStateException("File processing artifact owner UUID is required");
        }
        return userUuid.trim();
    }

    private String normalizeWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record TextArtifact(
            String contentText,
            int contentLength
    ) {
    }

    public record AiParseResult(
            Long fileId,
            int sourceCharacters,
            int summaryCharacters
    ) {
    }
}
