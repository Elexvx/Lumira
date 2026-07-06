package com.lumira.saas.modules.system.verification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.junit.jupiter.api.Test;

class VerificationDeliveryAuditServiceTest {

    @Test
    void logShouldNotInventCreatedByForAnonymousVerification() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        VerificationDeliveryAuditService service = new VerificationDeliveryAuditService(jdbcTemplate);

        service.log(null, null, null, "sms", "login", "SUCCESS", "sent");

        verify(jdbcTemplate).update(
                contains("user_uuid"),
                isNull(),
                isNull(),
                isNull(),
                eq("login"),
                eq("sms"),
                eq("SUCCESS"),
                eq("sent"),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        );
    }

    @Test
    void logShouldPersistTrustedUserUuid() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        VerificationDeliveryAuditService service = new VerificationDeliveryAuditService(jdbcTemplate);

        service.log(1001L, "user-uuid-1001", "alice", "email", "bind", "SUCCESS", "sent");

        verify(jdbcTemplate).update(
                contains("user_uuid"),
                eq(1001L),
                eq("user-uuid-1001"),
                eq("alice"),
                eq("bind"),
                eq("email"),
                eq("SUCCESS"),
                eq("sent"),
                isNull(),
                isNull(),
                eq(1001L),
                eq("user-uuid-1001")
        );
    }

    @Test
    void logShouldRejectPositiveUserIdWithoutUserUuid() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        VerificationDeliveryAuditService service = new VerificationDeliveryAuditService(jdbcTemplate);

        assertThatThrownBy(() -> service.log(1001L, null, "alice", "sms", "login", "SUCCESS", "sent"))
                .isInstanceOf(BizException.class);

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void logShouldRejectWhenAuditInsertMisses() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        VerificationDeliveryAuditService service = new VerificationDeliveryAuditService(jdbcTemplate);

        assertThatThrownBy(() -> service.log(1001L, "user-uuid-1001", "alice", "email", "bind", "SUCCESS", "sent"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Verification delivery audit changed");
    }
}
