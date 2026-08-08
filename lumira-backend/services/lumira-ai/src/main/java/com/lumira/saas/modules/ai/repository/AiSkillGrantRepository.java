package com.lumira.saas.modules.ai.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AiSkillGrantRepository {

    List<Map<String, Object>> findEmployeeSkills(Long employeeId);

    Optional<Map<String, Object>> findToolGrant(
            Long employeeId,
            String toolCode,
            String fallbackPermissionKey
    );
}
