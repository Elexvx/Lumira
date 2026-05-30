package com.legendary.invention.saas.modules.ai.app;

import com.legendary.invention.saas.infrastructure.event.PlatformEventPublisher;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.RowMapper;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AiKnowledgeBaseAppServiceTest {

    @Test
    void ownedKnowledgeBaseListKeepsWhereClauseSeparatedFromGroupBy() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(
                queryOperations,
                mock(com.legendary.invention.api.client.FileInternalApi.class),
                mock(AiKnowledgeTextExtractor.class),
                mock(OperationAuditService.class),
                mock(PlatformEventPublisher.class)
        );
        CurrentUser currentUser = new CurrentUser(7L, "admin", 1L, "session", 1, true, Set.of("ai:knowledge:view"));

        service.listKnowledgeBases(currentUser, null, null, "OWNED", 1, 10);

        assertFalse(queryOperations.lastListSql.contains("?group by"));
        assertTrue(queryOperations.lastListSql.contains("kb.owner_user_id = ?\ngroup by"));
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private String lastListSql = "";

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Long.class) {
                return (T) Long.valueOf(0L);
            }
            return null;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.lastListSql = sql;
            return List.of();
        }
    }
}
