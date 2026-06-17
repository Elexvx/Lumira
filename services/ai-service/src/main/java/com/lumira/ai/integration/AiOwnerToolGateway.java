package com.lumira.ai.integration;

import com.lumira.ai.vo.AiToolVO;
import com.lumira.common.security.CurrentUser;

import java.util.List;
import java.util.Map;

public interface AiOwnerToolGateway {

    ToolExecution execute(CurrentUser currentUser, AiToolVO tool, Map<String, Object> arguments);

    List<String> configuredOwners();

    List<String> degradedOwners();

    record ToolExecution(Map<String, Object> data, boolean remote, boolean degraded) {
    }
}
