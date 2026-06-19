package com.lumira.file.processing;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FileAiParseProcessor {

    public static final String ARTIFACT_AI_PARSE_READY = "AI_PARSE_READY";
    private static final int MAX_SUMMARY_CHARS = 1_000;

    private final JdbcTemplate jdbcTemplate;

    public FileAiParseProcessor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AiParseResult prepareForAiParse(Long tenantId, Long fileId, Long userId) {
        TextArtifact textArtifact = findTextArtifact(tenantId, fileId);
        if (textArtifact == null || !StringUtils.hasText(textArtifact.contentText())) {
            throw new IllegalStateException("TEXT_CONTENT artifact is unavailable for AI parse: " + fileId);
        }
        String normalizedText = normalizeWhitespace(textArtifact.contentText());
        String summary = normalizedText.length() > MAX_SUMMARY_CHARS
                ? normalizedText.substring(0, MAX_SUMMARY_CHARS)
                : normalizedText;
        String payload = buildPayload(textArtifact, summary, normalizedText.length());
        upsertArtifact(tenantId, fileId, payload, userId);
        return new AiParseResult(fileId, normalizedText.length(), summary.length());
    }

    private TextArtifact findTextArtifact(Long tenantId, Long fileId) {
        return jdbcTemplate.queryForObject(
                """
                        select content_text as contentText, content_length as contentLength
                        from file_processing_artifact
                        where tenant_id = ? and file_id = ? and artifact_type = ? and deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new TextArtifact(
                        rs.getString("contentText"),
                        rs.getInt("contentLength")
                ),
                tenantId,
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

    private void upsertArtifact(Long tenantId, Long fileId, String payload, Long userId) {
        jdbcTemplate.update(
                """
                        insert into file_processing_artifact (
                            tenant_id, file_id, task_type, artifact_type, content_text, content_length,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            task_type = values(task_type),
                            content_text = values(content_text),
                            content_length = values(content_length),
                            deleted = 0,
                            updated_at = current_timestamp,
                            updated_by = values(updated_by)
                        """,
                tenantId,
                fileId,
                FileProcessingTaskService.TASK_AI_PARSE,
                ARTIFACT_AI_PARSE_READY,
                payload,
                payload == null ? 0 : payload.length(),
                userId == null ? 0L : userId,
                userId == null ? 0L : userId
        );
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
