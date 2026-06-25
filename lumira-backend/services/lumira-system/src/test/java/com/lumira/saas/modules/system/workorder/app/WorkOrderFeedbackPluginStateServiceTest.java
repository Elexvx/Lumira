package com.lumira.saas.modules.system.workorder.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkOrderFeedbackPluginStateServiceTest {

    @Test
    void isEnabledShouldUseGlobalPluginActivationAndTableCheck() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = true;
        queryOperations.tableExists = true;
        WorkOrderFeedbackPluginStateService service = new WorkOrderFeedbackPluginStateService(queryOperations);

        assertThat(service.isEnabled(currentUser())).isTrue();
        assertThat(service.isEnabled(currentUser())).isTrue();

        assertThat(queryOperations.pluginActivationChecked).isTrue();
        assertThat(queryOperations.workOrderTableChecked).isTrue();
    }

    @Test
    void ensureEnabledShouldRejectWhenPluginIsDisabled() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.pluginEnabled = false;
        queryOperations.tableExists = true;
        WorkOrderFeedbackPluginStateService service = new WorkOrderFeedbackPluginStateService(queryOperations);

        assertThatThrownBy(() -> service.ensureEnabled(currentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.PLUGIN_NOT_ENABLED));
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
        private boolean pluginActivationChecked;
        private boolean workOrderTableChecked;

        @Override
        public boolean exists(String sql, Object... args) {
            if (sql.contains("from sys_plugin_definition")) {
                pluginActivationChecked = true;
                assertThat(sql).contains("sys_plugin_version");
                assertThat(sql).contains("v.is_active = 1");
                assertThat(args).containsExactly("work-order-feedback");
                return pluginEnabled;
            }
            if (sql.contains("information_schema.tables")) {
                workOrderTableChecked = true;
                return tableExists;
            }
            return false;
        }
    }
}
