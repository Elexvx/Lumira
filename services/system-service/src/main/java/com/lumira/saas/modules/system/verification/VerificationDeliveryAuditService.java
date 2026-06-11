package com.lumira.saas.modules.system.verification;

import com.lumira.common.web.TraceContext;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationDeliveryAuditService {

    private final MyBatisQueryOperations jdbcTemplate;

    public VerificationDeliveryAuditService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            Long tenantId,
            Long userId,
            String username,
            String channel,
            String scene,
            String resultStatus,
            String detailMessage
    ) {
        jdbcTemplate.update(
                """
                        insert into audit_operation_log (
                            tenant_id, user_id, username, module_name, action_name, operation_type,
                            result_status, detail_message, request_id, trace_id, created_by, deleted
                        ) values (?, ?, ?, 'verification', ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantId,
                userId,
                username,
                scene,
                channel,
                resultStatus,
                detailMessage,
                TraceContext.getRequestId(),
                TraceContext.getTraceId(),
                userId == null ? 0L : userId
        );
    }
}
