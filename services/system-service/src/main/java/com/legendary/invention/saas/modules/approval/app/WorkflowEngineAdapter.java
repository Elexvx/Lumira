package com.legendary.invention.saas.modules.approval.app;

import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.approval.dto.ApprovalDTO;
import com.legendary.invention.saas.modules.approval.vo.ApprovalVO;

public interface WorkflowEngineAdapter {
    ApprovalVO.InstanceVO start(CurrentUser currentUser, ApprovalDTO.InstanceCreateRequest request);
    ApprovalVO.InstanceVO approve(CurrentUser currentUser, Long taskId, String comment);
    ApprovalVO.InstanceVO reject(CurrentUser currentUser, Long taskId, String comment);
    ApprovalVO.InstanceVO cancel(CurrentUser currentUser, Long instanceId);
}
