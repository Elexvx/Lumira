package com.lumira.saas.modules.system.audit.repository;

import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.system.audit.vo.AuditLogVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;
import java.util.List;

/** Query-only boundary for system dashboard and audit-browser history. */
public interface SystemAuditQueryRepository {
    PageResponse<SystemVO.AuditLogVO> findLoginLogs(LoginSearch search);

    List<AuditLogVO> findSuccessfulLoginLogs(Long userId, long limit);

    List<AuditLogVO> findRecentOperationLogs(String username, long limit);

    PageResponse<SystemVO.AuditLogVO> findOperationLogs(OperationSearch search);

    PageResponse<SystemVO.AuditLogVO> findVerificationLogs(VerificationSearch search);

    PageResponse<SystemVO.AuditLogVO> findAiCallLogs(AiCallSearch search);

    record LoginSearch(String username, String loginType, LocalDateTime start, LocalDateTime end, long pageNo, long pageSize) {}

    record OperationSearch(String username, LocalDateTime start, LocalDateTime end, long pageNo, long pageSize) {}

    record VerificationSearch(String channel, String scene, String resultStatus, LocalDateTime start, LocalDateTime end, long pageNo, long pageSize) {}

    record AiCallSearch(Long employeeId, String skillCode, String resultStatus, LocalDateTime start, LocalDateTime end, long pageNo, long pageSize) {}
}
