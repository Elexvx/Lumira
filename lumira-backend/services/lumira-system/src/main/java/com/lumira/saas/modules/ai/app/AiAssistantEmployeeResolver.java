package com.lumira.saas.modules.ai.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.ai.repository.AiAssistantEmployeeRepository;
import com.lumira.saas.modules.ai.vo.AiVO;

final class AiAssistantEmployeeResolver {
    static final String ASSISTANT_USERNAME = "ai-assistant";
    private final AiAssistantEmployeeRepository repository;

    AiAssistantEmployeeResolver(AiAssistantEmployeeRepository repository) { this.repository = repository; }

    AiVO.EmployeeVO getOrCreateAssistantEmployee() {
        return repository.findEnabled(ASSISTANT_USERNAME)
                .filter(employee -> employee.getId() != null)
                .orElseThrow(AiAssistantEmployeeResolver::unavailable);
    }

    AiVO.EmployeeDetailVO getOrCreateAssistantEmployeeDetail() {
        return repository.findEnabledDetail(ASSISTANT_USERNAME)
                .filter(employee -> employee.getId() != null)
                .orElseThrow(AiAssistantEmployeeResolver::unavailable);
    }

    private static BizException unavailable() {
        return new BizException(ErrorCode.NOT_FOUND, "AI assistant employee is unavailable");
    }
}
