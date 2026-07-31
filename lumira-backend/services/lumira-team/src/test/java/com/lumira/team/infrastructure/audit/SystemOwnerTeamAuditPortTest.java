package com.lumira.team.infrastructure.audit;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SystemOwnerTeamAuditPortTest {

    @Test
    void logShouldDelegateToSystemOwnerWithTrustedIdentity() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.recordOperationAudit(any())).thenReturn(Boolean.TRUE);
        SystemOwnerTeamAuditPort auditPort = new SystemOwnerTeamAuditPort(systemInternalApi);

        auditPort.log(
                42L,
                " user-uuid-42 ",
                "alice",
                "team",
                "create",
                "CREATE",
                "SUCCESS",
                "created team 7"
        );

        ArgumentCaptor<OperationAuditRecordRequestDTO> request =
                ArgumentCaptor.forClass(OperationAuditRecordRequestDTO.class);
        verify(systemInternalApi).recordOperationAudit(request.capture());
        assertThat(request.getValue()).isEqualTo(new OperationAuditRecordRequestDTO(
                42L,
                "user-uuid-42",
                "alice",
                "team",
                "create",
                "CREATE",
                "SUCCESS",
                "created team 7"
        ));
    }

    @Test
    void logShouldRejectMissingTrustedUserUuidBeforeOwnerCall() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SystemOwnerTeamAuditPort auditPort = new SystemOwnerTeamAuditPort(systemInternalApi);

        assertThatThrownBy(() -> auditPort.log(
                42L,
                " ",
                "alice",
                "team",
                "create",
                "CREATE",
                "SUCCESS",
                "created team 7"
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(systemInternalApi);
    }

    @Test
    void logShouldFailWhenSystemOwnerDoesNotConfirmPersistence() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.recordOperationAudit(any())).thenReturn(Boolean.FALSE);
        SystemOwnerTeamAuditPort auditPort = new SystemOwnerTeamAuditPort(systemInternalApi);

        assertThatThrownBy(() -> auditPort.log(
                42L,
                "user-uuid-42",
                "alice",
                "team",
                "create",
                "CREATE",
                "SUCCESS",
                "created team 7"
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }
}
