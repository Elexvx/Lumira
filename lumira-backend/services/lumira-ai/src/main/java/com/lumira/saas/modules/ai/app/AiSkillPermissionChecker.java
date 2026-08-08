package com.lumira.saas.modules.ai.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.authorization.AgentToolGrantDecision;
import com.lumira.common.security.authorization.AgentToolGrantEvaluator;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.saas.modules.ai.repository.AiSkillGrantRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface AiSkillPermissionChecker {

    void verifyAllowed(Long employeeId, List<String> skillCodes, boolean confirmed);

    void verifyToolAllowed(Long employeeId, String toolCode, String permissionKey, String riskLevel, boolean readOnly, boolean confirmed);
}

@Service
@Primary
class DefaultAiSkillPermissionChecker implements AiSkillPermissionChecker {

    private final AiSkillGrantRepository skillGrantRepository;
    private final AgentToolGrantEvaluator agentToolGrantEvaluator;

    DefaultAiSkillPermissionChecker(AiSkillGrantRepository skillGrantRepository, AgentToolGrantEvaluator agentToolGrantEvaluator) {
        this.skillGrantRepository = skillGrantRepository;
        this.agentToolGrantEvaluator = agentToolGrantEvaluator;
    }

    @Override
    public void verifyAllowed(Long employeeId, List<String> skillCodes, boolean confirmed) {
        if (employeeId == null || CollectionUtils.isEmpty(skillCodes)) {
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool permission context is incomplete");
        }

        List<Map<String, Object>> rows = skillGrantRepository.findEmployeeSkills(employeeId);
        Map<String, Map<String, Object>> skillMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object skillCode = row.get("skillCode");
            if (skillCode != null) {
                skillMap.put(skillCode.toString(), row);
            }
        }

        for (String requestedSkillCode : skillCodes) {
            if (!StringUtils.hasText(requestedSkillCode)) {
                continue;
            }
            Map<String, Object> skill = skillMap.get(requestedSkillCode);
            if (skill == null) {
                throw new BizException(ErrorCode.FORBIDDEN, "未找到技能: " + requestedSkillCode);
            }
            if (toBoolean(skill.get("skillEnabled")) == false) {
                throw new BizException(ErrorCode.FORBIDDEN, "技能已禁用: " + requestedSkillCode);
            }
            String permissionMode = normalizePermissionMode(skill.get("permissionMode"));
            if ("deny".equals(permissionMode)) {
                throw new BizException(ErrorCode.FORBIDDEN, "技能已被禁用: " + requestedSkillCode);
            }
            boolean needConfirm = toBoolean(skill.get("needConfirm"));
            if (needConfirm && !confirmed) {
                throw new BizException(ErrorCode.BIZ_ERROR, "高风险技能需要二次确认: " + requestedSkillCode);
            }
        }
    }

    @Override
    public void verifyToolAllowed(Long employeeId, String toolCode, String permissionKey, String riskLevel, boolean readOnly, boolean confirmed) {
        if (employeeId == null || employeeId <= 0 || !StringUtils.hasText(toolCode)) {
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool permission context is incomplete");
        }
        AgentToolGrantDecision grant = agentToolGrantEvaluator.evaluate(AuthorizationRequest.aiToolAction(
                null,
                employeeId,
                toolCode,
                permissionKey,
                riskLevel,
                readOnly ? "view" : "execute",
                confirmed,
                false,
                Map.of()
        ));
        if (grant == null || !grant.allowed()) {
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool grant denied");
        }
        List<String> allowedModes = readOnly
                ? List.of("view", "visit", "invoke", "execute")
                : List.of("invoke", "execute");
        if (!allowedModes.contains(normalizePermissionMode(grant.permissionMode()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool grant does not allow execution: " + toolCode);
        }
        if (riskExceeds(riskLevel, grant.maxRiskLevel())) {
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool risk exceeds employee grant: " + toolCode);
        }
        if (grant.requireConfirm() && !confirmed) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI tool requires confirmation: " + toolCode);
        }
        if (grant.requireApproval()) {
            throw new BizException(ErrorCode.FORBIDDEN, "AI tool requires approval: " + toolCode);
        }
    }

    private String normalizePermissionMode(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    private boolean riskExceeds(String actualRisk, String maxRisk) {
        return riskRank(actualRisk) > riskRank(maxRisk);
    }

    private int riskRank(String risk) {
        return switch (risk == null ? "LOW" : risk.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }
}

@Service
class DefaultAgentToolGrantEvaluator implements AgentToolGrantEvaluator {

    private final AiSkillGrantRepository skillGrantRepository;

    DefaultAgentToolGrantEvaluator(AiSkillGrantRepository skillGrantRepository) {
        this.skillGrantRepository = skillGrantRepository;
    }

    @Override
    public AgentToolGrantDecision evaluate(AuthorizationRequest request) {
        if (request == null || request.employeeId() == null || request.employeeId() <= 0 || !StringUtils.hasText(request.toolCode())) {
            return AgentToolGrantDecision.deny("AGENT_CONTEXT_INCOMPLETE");
        }
        Map<String, Object> grant = skillGrantRepository.findToolGrant(
                request.employeeId(), request.toolCode().trim(), request.permissionKey()
        ).orElse(null);
        if (grant == null) {
            return AgentToolGrantDecision.deny("AGENT_TOOL_NOT_REGISTERED");
        }
        if (!toBoolean(grant.get("skillEnabled"))) {
            return AgentToolGrantDecision.deny("AGENT_TOOL_DISABLED");
        }
        String permissionMode = normalizePermissionMode(grant.get("permissionMode"));
        if (isReadAction(request)) {
            if (!List.of("view", "visit", "invoke", "execute").contains(permissionMode)) {
                return AgentToolGrantDecision.deny("AGENT_GRANT_VIEW_DENIED");
            }
        } else if (!List.of("invoke", "execute").contains(permissionMode)) {
            return AgentToolGrantDecision.deny("AGENT_GRANT_EXECUTE_DENIED");
        }
        String maxRiskLevel = String.valueOf(grant.getOrDefault("maxRiskLevel", "LOW"));
        if (riskExceeds(request.riskLevel(), maxRiskLevel)) {
            return AgentToolGrantDecision.deny("AGENT_RISK_EXCEEDS_GRANT");
        }
        return AgentToolGrantDecision.allow(permissionMode, String.valueOf(grant.getOrDefault("permissionKey", "")),
                maxRiskLevel, toBoolean(grant.get("requireConfirm")), toBoolean(grant.get("requireApproval")),
                List.of("AGENT_TOOL_GRANT_MATCH"));
    }

    private String normalizePermissionMode(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }

    private boolean isReadAction(AuthorizationRequest request) {
        String action = request == null || request.actionCode() == null ? "" : request.actionCode().trim().toLowerCase(Locale.ROOT);
        return action.startsWith("read") || action.startsWith("view") || action.startsWith("list") || action.startsWith("search");
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    private boolean riskExceeds(String actualRisk, String maxRisk) {
        return riskRank(actualRisk) > riskRank(maxRisk);
    }

    private int riskRank(String risk) {
        return switch (risk == null ? "LOW" : risk.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }
}
