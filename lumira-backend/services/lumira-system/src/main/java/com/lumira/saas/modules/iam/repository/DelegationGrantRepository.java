package com.lumira.saas.modules.iam.repository;

import java.util.List;
import java.util.Map;

public interface DelegationGrantRepository {
    Long findEnabledSubjectId(String subjectType, Long refId);
    List<Map<String, Object>> findMatchingGrants(Long delegatorSubjectId, Long delegateSubjectId,
                                                 String toolCode, String permissionKey, String resourceCode, String actionCode);
}
