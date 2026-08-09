package com.lumira.saas.modules.ai.repository;

import com.lumira.saas.modules.ai.vo.AiVO;
import java.time.LocalDateTime;
import java.util.Optional;

/** Persistence boundary for the AI employee chat runtime. */
public interface AiEmployeeRuntimeRepository {

    int appendChatAudit(ChatAuditLog auditLog, LocalDateTime now);

    Optional<AiVO.EmployeeDetailVO> findEmployeeDetail(Long employeeId);

    Optional<String> findConversationCode(Long conversationId);

    record ChatAuditLog(
            Long conversationId,
            Long employeeId,
            Long ownerUserId,
            String ownerUserUuid,
            String skillCode,
            String permissionMode,
            boolean confirmRequired,
            boolean confirmed,
            String resultStatus,
            String detailMessage,
            String requestPayloadJson,
            String responsePayloadJson
    ) {
    }
}
