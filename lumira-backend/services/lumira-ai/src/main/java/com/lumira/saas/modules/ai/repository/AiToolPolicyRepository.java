package com.lumira.saas.modules.ai.repository;

import com.lumira.saas.modules.ai.vo.AiVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for AI tool-policy state.  Application services keep
 * policy validation and authorization; this port owns optimistic state writes
 * and policy projections.
 */
public interface AiToolPolicyRepository {

    List<AiVO.ToolPolicyVO> findPage(long limit, long offset);

    long countActive();

    Long create(PolicyMutation mutation, LocalDateTime now);

    Optional<AiVO.ToolPolicyVO> findActiveById(Long id);

    int update(Long id, AiVO.ToolPolicyVO expected, PolicyMutation mutation, LocalDateTime now);

    int updateEnabled(Long id, AiVO.ToolPolicyVO expected, boolean enabled, LocalDateTime now);

    int softDelete(Long id, AiVO.ToolPolicyVO expected, LocalDateTime now);

    List<AiVO.ToolPolicyVO> findEnabled();

    record PolicyMutation(
            String policyName,
            String toolCode,
            String actionType,
            String riskLevel,
            String matchType,
            String matchValue,
            String verdict,
            String message,
            boolean enabled
    ) {
    }
}
