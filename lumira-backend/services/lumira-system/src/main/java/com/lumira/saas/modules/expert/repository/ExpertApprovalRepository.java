package com.lumira.saas.modules.expert.repository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ExpertApprovalRepository {
    Optional<OperatorRecord> findOperator(Long userId);
    Optional<ExpertAccountRecord> findApprovedExpert(Long expertId, String businessUuid, Long workflowInstanceId);
    Optional<Long> findRoleId(String roleCode);
    boolean usernameExists(String username);
    int bindAccount(Long expertId, String businessUuid, Long workflowInstanceId, Long userId, String userUuid,
                    Long updatedBy, String updatedByUuid, LocalDateTime updatedAt);

    record OperatorRecord(Long userId, String userUuid, String username, String status) { }
    record ExpertAccountRecord(Long id, String code, String name, String mobile, String email,
                               Long approvalInstanceId, Long userId, String userUuid, String username) { }
}
