package com.lumira.saas.modules.ai.repository;

import com.lumira.saas.modules.ai.vo.AiVO;
import java.util.Optional;

public interface AiAssistantEmployeeRepository {
    Optional<AiVO.EmployeeVO> findEnabled(String username);
    Optional<AiVO.EmployeeDetailVO> findEnabledDetail(String username);
}
