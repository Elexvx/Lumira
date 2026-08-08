package com.lumira.saas.modules.expert.repository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ExpertApprovalRepository {
    Optional<ExpertAccountRecord> findApprovedExpert(Long expertId, String businessUuid, Long workflowInstanceId);
    int bindAccount(Long expertId, String businessUuid, Long workflowInstanceId, Long userId, String userUuid,
                    Long updatedBy, String updatedByUuid, LocalDateTime updatedAt);
    int bindExistingAccount(Long expertId, String businessUuid, Long workflowInstanceId, Long userId, String userUuid,
                            Long updatedBy, String updatedByUuid, LocalDateTime updatedAt);
    int activateAccount(Long expertId, Long userId, String userUuid, LocalDateTime activatedAt);

    record ExpertAccountRecord(Long id, String code, String name, String mobile, String email,
                               Long approvalInstanceId, Long userId, String userUuid,
                               Long createdBy, String createdByUuid) { }
}
