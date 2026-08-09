package com.lumira.saas.modules.ai.repository;

import com.lumira.saas.modules.ai.app.AiLlmServiceConfig;
import java.util.Optional;

public interface AiLlmConfigRepository {
    Optional<AiLlmServiceConfig> findEnabledById(Long serviceId);
    Optional<AiLlmServiceConfig> findFirstEnabled();
    Optional<AiLlmServiceConfig> findForEmployee(Long employeeId);
    Optional<AiLlmServiceConfig> findSupervisor();
}
