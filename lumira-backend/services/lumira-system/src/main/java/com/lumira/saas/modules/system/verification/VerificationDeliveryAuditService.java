package com.lumira.saas.modules.system.verification;

import com.lumira.common.web.TraceContext;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VerificationDeliveryAuditService {

    private final MyBatisQueryOperations jdbcTemplate;

    public VerificationDeliveryAuditService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            Long userId,
            String userUuid,
            String username,
            String channel,
            String scene,
            String resultStatus,
            String detailMessage
    ) {
        int inserted = jdbcTemplate.update(
                """
                        insert into audit_operation_log (
                            user_id, user_uuid, username, module_name, action_name, operation_type,
                            result_status, detail_message, request_id, trace_id, created_by, created_by_uuid, deleted
                        ) values (?, ?, ?, 'verification', ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                userId,
                requireAuditUserUuid(userId, userUuid),
                username,
                scene,
                channel,
                resultStatus,
                detailMessage,
                TraceContext.getRequestId(),
                TraceContext.getTraceId(),
                trustedUserIdOrNull(userId),
                trustedUserUuidOrNull(userId, userUuid)
        );
        if (inserted != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Verification delivery audit changed, please retry");
        }
    }

    private Long trustedUserIdOrNull(Long userId) {
        return userId == null || userId <= 0 ? null : userId;
    }

    private String trustedUserUuidOrNull(Long userId, String userUuid) {
        return trustedUserIdOrNull(userId) == null ? null : requireAuditUserUuid(userId, userUuid);
    }

    private String requireAuditUserUuid(Long userId, String userUuid) {
        if (userId == null) {
            return StringUtils.hasText(userUuid) ? userUuid.trim() : null;
        }
        if (userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted verification audit user is required");
        }
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted verification audit user uuid is required");
        }
        return userUuid.trim();
    }
}
