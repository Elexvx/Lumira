package com.lumira.saas.modules.expert.repository;

import com.lumira.api.workflow.WorkflowExpertApplicationPort;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import java.util.List;
import java.util.Optional;

public interface ExpertRepository {
    PageData search(String keyword, String status, String approvalStatus, long offset, long limit);
    Optional<ExpertVO.Expert> findById(Long id);
    Long create(ExpertDTO.ExpertUpsertRequest expert, String initialStatus, String initialApprovalStatus,
                Long userId, String userUuid);
    int attachWorkflow(Long id, String code, String expectedStatus, String expectedApprovalStatus,
                       Long workflowInstanceId, Long userId, String userUuid);
    int updateWorkflowDecision(WorkflowExpertApplicationPort.ExpertApplicationDecision decision);
    int update(Long id, ExpertVO.Expert expected, ExpertDTO.ExpertUpsertRequest expert, Long userId, String userUuid);
    int delete(Long id, ExpertVO.Expert expected, Long userId, String userUuid);

    record PageData(List<ExpertVO.Expert> records, long total) { }
}
