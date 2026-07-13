package com.lumira.saas.modules.iam.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.iam.repository.DelegationGrantRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDelegationGrantRepository implements DelegationGrantRepository {
    private final MyBatisQueryOperations database;

    public JdbcDelegationGrantRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public Long findEnabledSubjectId(String subjectType, Long refId) {
        return database.queryForObject("""
                select id from iam_subject
                where subject_type = ? and ref_id = ? and status = 'ENABLED' and deleted = 0 limit 1
                """, Long.class, subjectType, refId);
    }

    @Override
    public List<Map<String, Object>> findMatchingGrants(Long delegatorSubjectId, Long delegateSubjectId,
                                                        String toolCode, String permissionKey, String resourceCode, String actionCode) {
        return database.queryForList("""
                select id, resource_code as resourceCode, action_code as actionCode,
                       permission_key as permissionKey, tool_code as toolCode, scope_type as scopeType,
                       max_risk_level as maxRiskLevel, require_confirm as requireConfirm, require_approval as requireApproval
                from iam_delegation_grant
                where delegator_subject_id = ? and delegate_subject_id = ? and status = 'ENABLED' and deleted = 0
                  and (valid_from is null or valid_from <= current_timestamp)
                  and (expires_at is null or expires_at > current_timestamp)
                  and (tool_code is null or tool_code = ?)
                  and (permission_key is null or permission_key = ? or permission_key = '*')
                  and (resource_code is null or resource_code = ?)
                  and (action_code is null or action_code = ?)
                """, delegatorSubjectId, delegateSubjectId, toolCode, permissionKey, resourceCode, actionCode);
    }
}
