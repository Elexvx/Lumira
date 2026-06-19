package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.common.security.CurrentUser;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AiKnowledgeBaseAppServiceTest {

    @Test
    void ownedKnowledgeBaseListKeepsWhereClauseSeparatedFromGroupBy() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:view"));

        service.listKnowledgeBases(currentUser, null, null, "OWNED", 1, 10);

        assertFalse(queryOperations.lastListSql.contains("?group by"));
        assertTrue(queryOperations.lastListSql.contains("kb.owner_user_id = ?\ngroup by"));
        assertThat(queryOperations.countQueryCount).isZero();
    }

    @Test
    void pendingIndexJobReadsFileAndBuildsChunks() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                new InMemoryFileInternalApi("hello knowledge world".getBytes(StandardCharsets.UTF_8)),
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        assertThat(queryOperations.lastListSql).contains("status = 'FAILED'");
        assertThat(queryOperations.lastListSql).contains("index_next_retry_at");
        assertThat(queryOperations.updateSql).anySatisfy(sql -> assertThat(sql).contains("set extracted_text"));
        assertThat(queryOperations.updateSql).anySatisfy(sql -> assertThat(sql).contains("insert into ai_knowledge_chunk"));
        assertThat(queryOperations.updateSql).anySatisfy(sql -> assertThat(sql).contains("status = 'READY'"));
    }

    @Test
    void pendingIndexJobPrefersFileTextArtifactWhenAvailable() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        InMemoryFileInternalApi fileInternalApi = new InMemoryFileInternalApi("raw file should not be parsed".getBytes(StandardCharsets.UTF_8));
        fileInternalApi.textArtifact = "artifact text from file owner";
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                fileInternalApi,
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        assertThat(fileInternalApi.contentReadCount).isZero();
        RecordingQueryOperations.UpdateCall extractedUpdate = queryOperations.updateCalls.stream()
                .filter(call -> call.sql().contains("set extracted_text"))
                .findFirst()
                .orElseThrow();
        assertThat(extractedUpdate.args()[0]).isEqualTo("artifact text from file owner");
    }

    @Test
    void failedIndexTaskSchedulesRetryBeforeDeadLetterThreshold() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.retryCount = 2;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                new FailingFileInternalApi(),
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        RecordingQueryOperations.UpdateCall failure = queryOperations.updateCalls.stream()
                .filter(call -> call.sql().contains("index_retry_count"))
                .findFirst()
                .orElseThrow();
        assertThat(failure.args()[0]).isEqualTo("FAILED");
        assertThat(failure.args()[2]).isEqualTo(3);
        assertThat(failure.args()[3]).isNotNull();
    }

    @Test
    void failedIndexTaskMovesToDeadLetterAtRetryThreshold() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.retryCount = 4;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                new FailingFileInternalApi(),
                new AiKnowledgeTextExtractor(),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );

        int processed = service.processPendingIndexTasks(10);

        assertThat(processed).isEqualTo(1);
        RecordingQueryOperations.UpdateCall failure = queryOperations.updateCalls.stream()
                .filter(call -> call.sql().contains("index_retry_count"))
                .findFirst()
                .orElseThrow();
        assertThat(failure.args()[0]).isEqualTo("DEAD_LETTER");
        assertThat(failure.args()[2]).isEqualTo(5);
        assertThat(failure.args()[3]).isNull();
    }

    @Test
    void retrieve_shouldUseVectorProjectionToRankBoundedCandidates() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.vectorSearchRows = true;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:query"));

        var references = service.retrieve(currentUser, "合同审批", List.of(20L), 2);

        assertThat(queryOperations.lastListSql).contains("embedding_vector_json");
        assertThat(references).hasSize(2);
        assertThat(references.get(0).getContent()).contains("合同审批");
    }

    @Test
    void createKnowledgeBaseShouldUseLastInsertId() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.lastInsertId = 88L;
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.lumira.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class),
                mock(DomainEventPublisher.class),
                vectorService()
        );
        CurrentUser currentUser = new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:create"));
        AiDTO.KnowledgeBaseUpsertRequest request = new AiDTO.KnowledgeBaseUpsertRequest();
        request.setName("研发知识库");
        request.setDescription("研发资料");
        request.setStatus("ENABLED");
        request.setVisibilityScope("PERSONAL");

        var result = service.createKnowledgeBase(currentUser, request);

        assertThat(queryOperations.lastInsertIdQueried).isTrue();
        assertThat(result.getId()).isEqualTo(88L);
        assertThat(result.getName()).isEqualTo("研发知识库");
    }

    private static AiKnowledgeVectorService vectorService() {
        return new AiKnowledgeVectorService(new LocalHashingAiEmbeddingModel());
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private String lastListSql = "";
        private int retryCount = 0;
        private boolean vectorSearchRows;
        private boolean lastInsertIdQueried;
        private Long lastInsertId = 0L;
        private int countQueryCount;

        @Override
        public boolean exists(String sql, Object... args) {
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("select last_insert_id()")) {
                lastInsertIdQueried = true;
                return (T) lastInsertId;
            }
            if (sql.contains("count(1)")) {
                countQueryCount += 1;
            }
            if (requiredType == Long.class) {
                return (T) Long.valueOf(0L);
            }
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            List<T> rows = query(sql, rowMapper, args);
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.lastListSql = sql;
            if (sql.contains("from ai_knowledge_document") && sql.contains("status = 'INDEXING'")) {
                try {
                    return List.of(rowMapper.mapRow(new SqlRow(Map.of(
                            "id", 30L,
                            "tenant_id", 1L,
                            "knowledge_base_id", 20L,
                            "file_id", 40L,
                            "title", "doc.txt",
                            "created_by", 7L,
                            "index_retry_count", retryCount
                    )), 0));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            if (vectorSearchRows && sql.contains("from ai_knowledge_chunk")) {
                AiKnowledgeVectorService vectorService = vectorService();
                try {
                    return List.of(
                            rowMapper.mapRow(new SqlRow(Map.of(
                                    "chunk_id", 301L,
                                    "knowledge_base_id", 20L,
                                    "knowledge_base_name", "合同知识库",
                                    "document_id", 30L,
                                    "document_title", "合同审批制度",
                                    "file_id", 40L,
                                    "original_file_name", "contract.txt",
                                    "chunk_index", 0,
                                    "content", "合同审批需要法务和财务共同确认",
                                    "embedding_vector_json", vectorService.project("合同审批需要法务和财务共同确认").vectorJson()
                            )), 0),
                            rowMapper.mapRow(new SqlRow(Map.of(
                                    "chunk_id", 302L,
                                    "knowledge_base_id", 20L,
                                    "knowledge_base_name", "通用知识库",
                                    "document_id", 31L,
                                    "document_title", "假期制度",
                                    "file_id", 41L,
                                    "original_file_name", "holiday.txt",
                                    "chunk_index", 0,
                                    "content", "员工假期申请需要提前提交",
                                    "embedding_vector_json", vectorService.project("员工假期申请需要提前提交").vectorJson()
                            )), 1)
                    );
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            if (sql.contains("from ai_knowledge_base")) {
                try {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", 88L);
                    row.put("tenant_id", 1L);
                    row.put("kb_code", "kb_88");
                    row.put("name", "研发知识库");
                    row.put("description", "研发资料");
                    row.put("status", "ENABLED");
                    row.put("visibility_scope", "PERSONAL");
                    row.put("owner_user_id", 7L);
                    row.put("created_by", 7L);
                    row.put("document_count", 0L);
                    row.put("chunk_count", 0L);
                    row.put("create_time", LocalDateTime.now());
                    row.put("update_time", LocalDateTime.now());
                    return List.of(rowMapper.mapRow(new SqlRow(row), 0));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            return List.of();
        }

        private final List<String> updateSql = new ArrayList<>();
        private final List<UpdateCall> updateCalls = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updateSql.add(sql);
            updateCalls.add(new UpdateCall(sql, args));
            return 1;
        }

        private record UpdateCall(String sql, Object[] args) {
        }
    }

    private static final class InMemoryFileInternalApi implements FileInternalApi {
        private final byte[] content;
        private String textArtifact;
        private int contentReadCount;

        private InMemoryFileInternalApi(byte[] content) {
            this.content = content;
        }

        @Override
        public FileObjectDTO uploadImage(MultipartFile file, String category, String remark) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObjectDTO uploadDocument(MultipartFile file, String category, String tags, String remark, String bucket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileContentDTO readFileContentForUser(Long fileId, Long tenantId, Long userId, String username) {
            contentReadCount++;
            return new FileContentDTO(fileId, tenantId, "doc.txt", "text/plain", "txt", content);
        }

        @Override
        public FileProcessingArtifactDTO readProcessingArtifactForUser(Long fileId, Long tenantId, Long userId, String username, String artifactType) {
            if (textArtifact == null) {
                throw new RuntimeException("artifact unavailable");
            }
            return new FileProcessingArtifactDTO(
                    9001L,
                    tenantId,
                    fileId,
                    "TEXT_EXTRACT",
                    artifactType,
                    null,
                    textArtifact,
                    textArtifact.length(),
                    LocalDateTime.now()
            );
        }
    }

    private static final class FailingFileInternalApi implements FileInternalApi {

        @Override
        public FileObjectDTO uploadImage(MultipartFile file, String category, String remark) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObjectDTO uploadDocument(MultipartFile file, String category, String tags, String remark, String bucket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileContentDTO readFileContentForUser(Long fileId, Long tenantId, Long userId, String username) {
            throw new RuntimeException("temporary parser failure");
        }
    }
}
