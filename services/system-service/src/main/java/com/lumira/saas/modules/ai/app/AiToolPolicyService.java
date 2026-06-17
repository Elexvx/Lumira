package com.lumira.saas.modules.ai.app;

import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface AiToolPolicyService {

    PageResponse<AiVO.ToolPolicyVO> listPolicies(CurrentUser currentUser, long pageNo, long pageSize);

    AiVO.ToolPolicyVO createPolicy(CurrentUser currentUser, AiDTO.ToolPolicyUpsertRequest request);

    AiVO.ToolPolicyVO updatePolicy(CurrentUser currentUser, Long id, AiDTO.ToolPolicyUpsertRequest request);

    boolean updatePolicyEnabled(CurrentUser currentUser, Long id, boolean enabled);

    boolean deletePolicy(CurrentUser currentUser, Long id);

    PolicyDecision evaluate(Long tenantId, String toolCode, String actionType, String riskLevel, String message, Map<String, Object> arguments);

    record PolicyDecision(String verdict, String message, List<String> matches) {
        boolean denied() {
            return "DENY".equalsIgnoreCase(verdict);
        }
    }
}

@Service
@Primary
class DefaultAiToolPolicyService implements AiToolPolicyService {

    private static final long MAX_PAGE_SIZE = 100L;

    private final MyBatisQueryOperations jdbcTemplate;

    @Autowired
    DefaultAiToolPolicyService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResponse<AiVO.ToolPolicyVO> listPolicies(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        long safePageNo = Math.max(1, pageNo);
        long safePageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        long offset = (safePageNo - 1) * safePageSize;
        List<AiVO.ToolPolicyVO> records = jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, policy_name as policyName, tool_code as toolCode,
                               action_type as actionType, risk_level as riskLevel, match_type as matchType,
                               match_value as matchValue, verdict, message, enabled,
                               create_time as createTime, update_time as updateTime
                        from ai_tool_policy
                        where tenant_id = ?
                          and is_deleted = 0
                        order by id desc
                        limit ? offset ?
                        """,
                new BeanPropertyRowMapper<>(AiVO.ToolPolicyVO.class),
                tenantId,
                safePageSize,
                offset
        );
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : nullToZero(jdbcTemplate.queryForObject(
                "select count(1) from ai_tool_policy where tenant_id = ? and is_deleted = 0",
                Long.class,
                tenantId
        ));
        PageResponse<AiVO.ToolPolicyVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    @Override
    @Transactional
    public AiVO.ToolPolicyVO createPolicy(CurrentUser currentUser, AiDTO.ToolPolicyUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        jdbcTemplate.update(
                """
                        insert into ai_tool_policy (
                            tenant_id, policy_name, tool_code, action_type, risk_level, match_type,
                            match_value, verdict, message, enabled, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                tenantId,
                requiredText(request.getPolicyName(), "策略名称不能为空"),
                defaultText(request.getToolCode(), "*"),
                normalizeText(request.getActionType()),
                normalizeRisk(request.getRiskLevel()),
                defaultText(request.getMatchType(), "KEYWORD"),
                normalizeText(request.getMatchValue()),
                defaultText(request.getVerdict(), "DENY").toUpperCase(Locale.ROOT),
                normalizeText(request.getMessage()),
                Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return requirePolicy(tenantId, id);
    }

    @Override
    @Transactional
    public AiVO.ToolPolicyVO updatePolicy(CurrentUser currentUser, Long id, AiDTO.ToolPolicyUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        requirePolicy(tenantId, id);
        jdbcTemplate.update(
                """
                        update ai_tool_policy
                        set policy_name = ?, tool_code = ?, action_type = ?, risk_level = ?, match_type = ?,
                            match_value = ?, verdict = ?, message = ?, enabled = ?, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                requiredText(request.getPolicyName(), "策略名称不能为空"),
                defaultText(request.getToolCode(), "*"),
                normalizeText(request.getActionType()),
                normalizeRisk(request.getRiskLevel()),
                defaultText(request.getMatchType(), "KEYWORD"),
                normalizeText(request.getMatchValue()),
                defaultText(request.getVerdict(), "DENY").toUpperCase(Locale.ROOT),
                normalizeText(request.getMessage()),
                Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1,
                LocalDateTime.now(),
                tenantId,
                id
        );
        return requirePolicy(tenantId, id);
    }

    @Override
    @Transactional
    public boolean updatePolicyEnabled(CurrentUser currentUser, Long id, boolean enabled) {
        Long tenantId = currentTenantId(currentUser);
        requirePolicy(tenantId, id);
        jdbcTemplate.update(
                "update ai_tool_policy set enabled = ?, update_time = ? where tenant_id = ? and id = ? and is_deleted = 0",
                enabled ? 1 : 0,
                LocalDateTime.now(),
                tenantId,
                id
        );
        return true;
    }

    @Override
    @Transactional
    public boolean deletePolicy(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        requirePolicy(tenantId, id);
        jdbcTemplate.update(
                "update ai_tool_policy set is_deleted = 1, update_time = ? where tenant_id = ? and id = ? and is_deleted = 0",
                LocalDateTime.now(),
                tenantId,
                id
        );
        return true;
    }

    @Override
    public PolicyDecision evaluate(Long tenantId, String toolCode, String actionType, String riskLevel, String message, Map<String, Object> arguments) {
        List<AiVO.ToolPolicyVO> policies = jdbcTemplate.query(
                """
                        select id, policy_name as policyName, tool_code as toolCode, action_type as actionType,
                               risk_level as riskLevel, match_type as matchType, match_value as matchValue,
                               verdict, message, enabled
                        from ai_tool_policy
                        where tenant_id = ?
                          and enabled = 1
                          and is_deleted = 0
                        order by id asc
                        """,
                new BeanPropertyRowMapper<>(AiVO.ToolPolicyVO.class),
                tenantId
        );
        String haystack = (String.valueOf(message) + "\n" + String.valueOf(arguments)).toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        String decisionMessage = null;
        for (AiVO.ToolPolicyVO policy : policies) {
            if (!matchesTool(policy.getToolCode(), toolCode)
                    || !matchesNullable(policy.getActionType(), actionType)
                    || !matchesNullable(policy.getRiskLevel(), riskLevel)
                    || !matchesValue(policy.getMatchType(), policy.getMatchValue(), haystack)) {
                continue;
            }
            matches.add(policy.getPolicyName());
            if ("DENY".equalsIgnoreCase(policy.getVerdict())) {
                return new PolicyDecision("DENY", firstText(policy.getMessage(), "命中 AI 工具防护规则：" + policy.getPolicyName()), matches);
            }
            if (!StringUtils.hasText(decisionMessage)) {
                decisionMessage = policy.getMessage();
            }
        }
        return new PolicyDecision("ALLOW", firstText(decisionMessage, "平台防护规则通过"), matches);
    }

    private AiVO.ToolPolicyVO requirePolicy(Long tenantId, Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "策略 ID 不能为空");
        }
        return jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, policy_name as policyName, tool_code as toolCode,
                               action_type as actionType, risk_level as riskLevel, match_type as matchType,
                               match_value as matchValue, verdict, message, enabled,
                               create_time as createTime, update_time as updateTime
                        from ai_tool_policy
                        where tenant_id = ? and id = ? and is_deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiVO.ToolPolicyVO.class),
                tenantId,
                id
        ).stream().findFirst().orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "AI 工具策略不存在"));
    }

    private boolean matchesTool(String pattern, String toolCode) {
        String resolvedPattern = defaultText(pattern, "*");
        if ("*".equals(resolvedPattern)) {
            return true;
        }
        if (resolvedPattern.endsWith("*")) {
            return String.valueOf(toolCode).startsWith(resolvedPattern.substring(0, resolvedPattern.length() - 1));
        }
        return resolvedPattern.equals(toolCode);
    }

    private boolean matchesNullable(String expected, String actual) {
        return !StringUtils.hasText(expected) || expected.equalsIgnoreCase(String.valueOf(actual));
    }

    private boolean matchesValue(String matchType, String matchValue, String haystack) {
        if (!StringUtils.hasText(matchValue)) {
            return true;
        }
        if ("REGEX".equalsIgnoreCase(matchType)) {
            return java.util.regex.Pattern.compile(matchValue, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(haystack).find();
        }
        for (String keyword : matchValue.split(",")) {
            if (StringUtils.hasText(keyword) && haystack.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeRisk(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser != null && currentUser.getCurrentTenantId() != null) {
            return currentUser.getCurrentTenantId();
        }
        return PlatformConstants.PLATFORM_TENANT_ID;
    }
}
