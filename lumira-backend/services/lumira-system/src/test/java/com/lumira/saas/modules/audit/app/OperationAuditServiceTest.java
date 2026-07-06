package com.lumira.saas.modules.audit.app;

import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.audit.entity.AuditOperationLogEntity;
import com.lumira.saas.modules.audit.mapper.AuditOperationLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationAuditServiceTest {

    @Test
    void logRecordsTrustedUserUuid() {
        AuditOperationLogMapper mapper = mock(AuditOperationLogMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AuditOperationLogEntity.class))).thenReturn(1);
        OperationAuditService service = new OperationAuditService(mapper, null);

        service.log(1001L, "user-uuid-1001", "admin", "system", "update", "UPDATE", "SUCCESS", "updated");

        ArgumentCaptor<AuditOperationLogEntity> captor = ArgumentCaptor.forClass(AuditOperationLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserUuid()).isEqualTo("user-uuid-1001");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(1001L);
        assertThat(captor.getValue().getCreatedByUuid()).isEqualTo("user-uuid-1001");
    }

    @Test
    void logShouldRejectPositiveUserIdWithoutUserUuid() {
        AuditOperationLogMapper mapper = mock(AuditOperationLogMapper.class);
        OperationAuditService service = new OperationAuditService(mapper, null);

        assertThatThrownBy(() -> service.log(1001L, null, "admin", "system", "update", "UPDATE", "SUCCESS", "updated"))
                .isInstanceOf(BizException.class);

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(AuditOperationLogEntity.class));
    }

    @Test
    void logShouldRejectWhenAuditInsertMisses() {
        AuditOperationLogMapper mapper = mock(AuditOperationLogMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AuditOperationLogEntity.class))).thenReturn(0);
        OperationAuditService service = new OperationAuditService(mapper, null);

        assertThatThrownBy(() -> service.log(1001L, "user-uuid-1001", "admin", "system", "update", "UPDATE", "SUCCESS", "updated"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Operation audit changed");
    }
}
