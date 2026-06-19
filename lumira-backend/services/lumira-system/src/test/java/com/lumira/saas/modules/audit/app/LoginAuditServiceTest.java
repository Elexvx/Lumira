package com.lumira.saas.modules.audit.app;

import com.lumira.common.constant.PlatformConstants;
import com.lumira.saas.modules.audit.entity.AuditLoginLogEntity;
import com.lumira.saas.modules.audit.mapper.AuditLoginLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoginAuditServiceTest {

    @Test
    void logUsesPlatformTenantWhenTenantIsMissing() {
        AuditLoginLogMapper mapper = mock(AuditLoginLogMapper.class);
        LoginAuditService service = new LoginAuditService(mapper);

        service.log(1001L, null, "admin", "PASSWORD", "FAIL", "密码错误", "127.0.0.1", "test");

        ArgumentCaptor<AuditLoginLogEntity> captor = ArgumentCaptor.forClass(AuditLoginLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(PlatformConstants.PLATFORM_TENANT_ID);
    }
}
