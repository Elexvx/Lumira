package com.lumira.team.infrastructure.audit;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.team.app.TeamAuditPort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SystemOwnerTeamAuditPort implements TeamAuditPort {

    private final SystemInternalApi systemInternalApi;

    public SystemOwnerTeamAuditPort(SystemInternalApi systemInternalApi) {
        this.systemInternalApi = systemInternalApi;
    }

    @Override
    public void log(
            Long userId,
            String userUuid,
            String username,
            String moduleName,
            String actionName,
            String operationType,
            String resultStatus,
            String detailMessage
    ) {
        String trustedUserUuid = requireAuditUserUuid(userId, userUuid);
        Boolean recorded = systemInternalApi.recordOperationAudit(new OperationAuditRecordRequestDTO(
                userId,
                trustedUserUuid,
                username,
                moduleName,
                actionName,
                operationType,
                resultStatus,
                detailMessage
        ));
        if (!Boolean.TRUE.equals(recorded)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Team operation audit changed, please retry");
        }
    }

    private String requireAuditUserUuid(Long userId, String userUuid) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted team audit user is required");
        }
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted team audit user uuid is required");
        }
        return userUuid.trim();
    }
}
