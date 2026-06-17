package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FileAiParseProcessorTest {

    @Test
    void prepareForAiParse_shouldCreateAiReadyArtifactFromTextContent() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(1001L), eq(3001L), eq(FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("contentText")).thenReturn("hello\n\nLumira DDD");
                    when(resultSet.getInt("contentLength")).thenReturn(17);
                    return mapper.mapRow(resultSet, 0);
                });
        var processor = new FileAiParseProcessor(jdbcTemplate);

        FileAiParseProcessor.AiParseResult result = processor.prepareForAiParse(1001L, 3001L, 2001L);

        assertThat(result.fileId()).isEqualTo(3001L);
        assertThat(result.sourceCharacters()).isEqualTo("hello Lumira DDD".length());
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                anyString(),
                eq(1001L),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_AI_PARSE),
                eq(FileAiParseProcessor.ARTIFACT_AI_PARSE_READY),
                payloadCaptor.capture(),
                anyInt(),
                eq(2001L),
                eq(2001L)
        );
        assertThat(String.valueOf(payloadCaptor.getValue()))
                .contains("\"sourceArtifactType\":\"TEXT_CONTENT\"")
                .contains("\"summary\":\"hello Lumira DDD\"");
    }
}
