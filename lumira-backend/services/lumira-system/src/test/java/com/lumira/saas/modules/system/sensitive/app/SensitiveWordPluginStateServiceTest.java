package com.lumira.saas.modules.system.sensitive.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveWordPluginStateServiceTest {

    @Test
    void isEnabledShouldUseGlobalPluginActivationAndTableChecks() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = true;
        queryOperations.tableExists = true;
        queryOperations.requiredColumnCount = 12L;
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations);

        assertThat(service.isEnabled(currentUser())).isTrue();
        assertThat(service.isEnabled(currentUser())).isTrue();
        assertThat(queryOperations.existsCallCount).isEqualTo(3);
        assertThat(queryOperations.countQueryCalled).isTrue();
    }

    @Test
    void ensureEnabledShouldRejectWhenPluginNotEnabled() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = false;
        queryOperations.tableExists = true;
        queryOperations.requiredColumnCount = 12L;
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations);

        assertThatThrownBy(() -> service.ensureEnabled(currentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.PLUGIN_NOT_ENABLED));
    }

    @Test
    void ensureEnabledShouldRejectWhenSensitiveWordSchemaIsIncomplete() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = true;
        queryOperations.tableExists = true;
        queryOperations.requiredColumnCount = 10L;
        SensitiveWordPluginStateService service = new SensitiveWordPluginStateService(queryOperations);

        assertThatThrownBy(() -> service.ensureEnabled(currentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.PLUGIN_NOT_ENABLED));
        assertThat(queryOperations.countQueryCalled).isTrue();
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUsername("admin");
        currentUser.setAuthenticated(true);
        return currentUser;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean pluginEnabled;
        private boolean tableExists;
        private boolean countQueryCalled;
        private int existsCallCount;
        private Long requiredColumnCount;

        @Override
        public boolean exists(String sql, Object... args) {
            existsCallCount += 1;
            if (sql.contains("from sys_plugin_definition")) {
                assertThat(sql).contains("sys_plugin_version");
                assertThat(sql).contains("v.is_active = 1");
                assertThat(args).containsExactly("sensitive-words");
                return pluginEnabled;
            }
            if (sql.contains("information_schema.tables")) {
                return tableExists;
            }
            return false;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
                return requiredType.cast(requiredColumnCount);
            }
            return null;
        }
    }
}
