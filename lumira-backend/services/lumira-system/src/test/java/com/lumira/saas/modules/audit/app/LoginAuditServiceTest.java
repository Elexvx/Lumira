package com.lumira.saas.modules.audit.app;

import com.lumira.saas.modules.audit.entity.AuditLoginLogEntity;
import com.lumira.saas.modules.audit.mapper.AuditLoginLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoginAuditServiceTest {

    @Test
    void logRecordsUserWithoutTenant() {
        AuditLoginLogMapper mapper = mock(AuditLoginLogMapper.class);
        LoginAuditService service = new LoginAuditService(mapper);

        service.log(1001L, "admin", "PASSWORD", "FAIL", "密码错误", "127.0.0.1", "test");

        ArgumentCaptor<AuditLoginLogEntity> captor = ArgumentCaptor.forClass(AuditLoginLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1001L);
    }
}
