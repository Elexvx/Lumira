package com.lumira.saas.modules.system.sensitive.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.ai.app.AiKnowledgeTextExtractor;
import com.lumira.saas.modules.system.sensitive.dto.SensitiveWordDTO;
import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class SensitiveWordServiceTest {

    @Test
    void createWordShouldRejectDuplicateWordViaExistsCheck() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.wordExists = true;
        SensitiveWordService service = new SensitiveWordService(
                queryOperations,
                mock(AiKnowledgeTextExtractor.class),
                mock(SensitiveWordPluginStateService.class)
        );

        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("敏感词");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        CurrentUser currentUser = currentUser();
        assertThatThrownBy(() -> service.createWord(currentUser, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
        assertThat(queryOperations.existsCallCount).isEqualTo(1);
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void importWordsShouldRejectExistingWordViaExistsCheck() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.wordExists = true;
        SensitiveWordService service = new SensitiveWordService(
                queryOperations,
                mock(AiKnowledgeTextExtractor.class),
                mock(SensitiveWordPluginStateService.class)
        );

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("words.txt");
        when(file.getBytes()).thenReturn("敏感词".getBytes());

        SensitiveWordVO.ImportResult result = service.importWords(currentUser(), file);

        assertThat(result.getDuplicated()).isEqualTo(1);
        assertThat(queryOperations.existsCallCount).isEqualTo(1);
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void listWordsShouldSkipCountForFirstShortPage() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordService service = new SensitiveWordService(
                queryOperations,
                mock(AiKnowledgeTextExtractor.class),
                mock(SensitiveWordPluginStateService.class)
        );

        var response = service.listWords(currentUser(), null, null, 1, 10);

        assertThat(response.getRecords()).isEmpty();
        assertThat(response.getTotal()).isZero();
        assertThat(queryOperations.countQueryCalled).isFalse();
        assertThat(queryOperations.lastListSql).contains("from sys_sensitive_word");
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setCurrentTenantId(1001L);
        currentUser.setUserId(2001L);
        currentUser.setUsername("admin");
        currentUser.setAuthenticated(true);
        return currentUser;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean wordExists;
        private boolean countQueryCalled;
        private int existsCallCount;
        private String lastListSql = "";

        @Override
        public boolean exists(String sql, Object... args) {
            existsCallCount += 1;
            return wordExists && sql.contains("from sys_sensitive_word");
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
            }
            if (sql.contains("select last_insert_id()")) {
                return requiredType.cast(2001L);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            lastListSql = sql;
            return List.of();
        }
    }
}
