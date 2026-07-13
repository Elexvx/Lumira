package com.lumira.saas.modules.system.workorder.repository;

import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.system.workorder.vo.WorkOrderFeedbackVO;
import java.util.List;

public interface WorkOrderFeedbackRepository {
    PageResponse<WorkOrderFeedbackVO.WorkOrderRecord> findPage(Filter filter, long pageNo, long pageSize);
    WorkOrderFeedbackVO.WorkOrderRecord findById(Long id, Owner owner);
    Long insert(String title, String detailHtml, String priority, String initialStatus, Long userId, String userUuid, String username);
    int updateStatus(Long id, String expectedStatus, Long submitterId, String submitterUuid,
                     String status, boolean terminalStatus, String adminReply, Long userId, String userUuid);
    List<PolicyItem> findEnabledPolicyItems(String dictionaryCode);

    record Filter(String keyword, String status, String priority, Owner owner) {}
    record Owner(Long userId, String userUuid) {
        public static Owner all() { return new Owner(null, null); }
        public boolean restricted() { return userId != null; }
    }
    record PolicyItem(String label, String value, String remark, int sortNo) {}
}
