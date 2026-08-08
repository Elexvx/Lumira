package com.lumira.api.ai;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Narrow read contract for the System audit browser to render AI-owned tool
 * execution history without reading {@code ai_*} tables itself.
 */
public interface AiAuditReadPort {

    AiToolAuditPage findToolAudits(AiToolAuditSearch search);

    record AiToolAuditSearch(
            Long employeeId,
            String skillCode,
            String resultStatus,
            LocalDateTime start,
            LocalDateTime end,
            long pageNo,
            long pageSize
    ) {
    }

    record AiToolAuditPage(List<AiToolAuditRecord> records, long total, long pageNo, long pageSize) {
        public AiToolAuditPage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }

    record AiToolAuditRecord(
            Long id,
            Long conversationId,
            Long employeeId,
            String skillCode,
            String toolName,
            String permissionMode,
            Integer confirmRequired,
            Integer confirmResult,
            String logResult,
            String detailMessage,
            String requestPayloadJson,
            String responsePayloadJson,
            LocalDateTime createdAt
    ) {
    }
}
