package com.lumira.saas.modules.audit.app;

import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.audit.entity.AuditLoginLogEntity;
import com.lumira.saas.modules.audit.mapper.AuditLoginLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginAuditServiceTest {

    @Test
    void logRecordsTrustedUserWithoutTenant() {
        AuditLoginLogMapper mapper = mock(AuditLoginLogMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AuditLoginLogEntity.class))).thenReturn(1);
        LoginAuditService service = new LoginAuditService(mapper);

        service.log(1001L, "user-uuid-1001", "admin", "PASSWORD", "FAIL", "bad password", "127.0.0.1", "test");

        ArgumentCaptor<AuditLoginLogEntity> captor = ArgumentCaptor.forClass(AuditLoginLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getUserUuid()).isEqualTo("user-uuid-1001");
    }

    @Test
    void logShouldRejectPositiveUserIdWithoutUserUuid() {
        AuditLoginLogMapper mapper = mock(AuditLoginLogMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AuditLoginLogEntity.class))).thenReturn(1);
        LoginAuditService service = new LoginAuditService(mapper);

        assertThatThrownBy(() -> service.log(1001L, null, "admin", "PASSWORD", "FAIL", "bad password", "127.0.0.1", "test"))
                .isInstanceOf(BizException.class);

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(AuditLoginLogEntity.class));
    }

    @Test
    void logShouldAllowAnonymousFailureWithoutUserUuid() {
        AuditLoginLogMapper mapper = mock(AuditLoginLogMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AuditLoginLogEntity.class))).thenReturn(1);
        LoginAuditService service = new LoginAuditService(mapper);

        service.log(null, null, "unknown", "PASSWORD", "FAIL", "not found", "127.0.0.1", "test");

        ArgumentCaptor<AuditLoginLogEntity> captor = ArgumentCaptor.forClass(AuditLoginLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getUserUuid()).isNull();
    }

    @Test
    void logShouldRejectWhenAuditInsertMisses() {
        AuditLoginLogMapper mapper = mock(AuditLoginLogMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AuditLoginLogEntity.class))).thenReturn(0);
        LoginAuditService service = new LoginAuditService(mapper);

        assertThatThrownBy(() -> service.log(1001L, "user-uuid-1001", "admin", "PASSWORD", "SUCCESS", null, "127.0.0.1", "test"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Login audit changed");
    }
}
