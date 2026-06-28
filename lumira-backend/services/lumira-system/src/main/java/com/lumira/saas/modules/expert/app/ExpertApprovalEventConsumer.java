package com.lumira.saas.modules.expert.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.event.PlatformEventConsumer;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxEntity;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.account.app.AccountActivationService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnLumiraControlPlaneEnabled
public class ExpertApprovalEventConsumer implements PlatformEventConsumer {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SystemUserManagementAppService systemUserManagementAppService;
    private final AccountActivationService accountActivationService;

    public ExpertApprovalEventConsumer(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            SystemUserManagementAppService systemUserManagementAppService,
            AccountActivationService accountActivationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.systemUserManagementAppService = systemUserManagementAppService;
        this.accountActivationService = accountActivationService;
    }

    @Override
    public boolean supports(PlatformEventOutboxEntity event) {
        return event != null && WorkflowAppService.EVENT_EXPERT_APPROVED.equals(event.getEventType());
    }

    @Override
    @Transactional
    public void consume(PlatformEventOutboxEntity event) {
        Long expertId = aggregateId(event);
        if (expertId == null) {
            throw new IllegalStateException("EXPERT_APPROVED event missing aggregateId");
        }
        ExpertAccountRow expert = loadExpert(expertId);
        if (expert == null) {
            throw new IllegalStateException("Expert not found: " + expertId);
        }
        if (!StringUtils.hasText(expert.email())) {
            throw new IllegalStateException("Expert email is required before account activation");
        }
        Long userId = expert.userId();
        String username = expert.username();
        CurrentUser operator = buildOperator(event);
        if (userId == null) {
            username = nextUsername(expert.code(), expertId);
            SystemDTO.UserUpsertRequest userRequest = new SystemDTO.UserUpsertRequest();
            userRequest.setUsername(username);
            userRequest.setPassword(randomPassword());
            userRequest.setMobile(expert.mobile());
            userRequest.setEmail(expert.email());
            userRequest.setRealName(expert.name());
            userRequest.setNickname(expert.name());
            userRequest.setStatus("ENABLED");
            Long expertRoleId = findRoleId("EXPERT");
            userRequest.setRoleIds(expertRoleId == null ? List.of() : List.of(expertRoleId));
            SystemVO.UserDetailVO createdUser = systemUserManagementAppService.createUser(operator, userRequest);
            userId = createdUser.getId();
            jdbcTemplate.update(
                    """
                            update aiadc_expert
                            set user_id = ?, account_status = 'PENDING_ACTIVATION', initial_password_reset_required = 1,
                                updated_by = ?, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    userId,
                    operator.getUserId(),
                    LocalDateTime.now(),
                    expertId
            );
        }
        String token = accountActivationService.createActivationToken(userId, expertId, operator.getUserId());
        accountActivationService.sendActivationEmail(expert.email(), username, token);
    }

    private CurrentUser buildOperator(PlatformEventOutboxEntity event) {
        CurrentUser operator = new CurrentUser();
        operator.setUserId(event.getUserId() == null ? 0L : event.getUserId());
        operator.setUserUuid(event.getUserUuid());
        operator.setUsername("workflow");
        operator.setAuthenticated(true);
        return operator;
    }

    private ExpertAccountRow loadExpert(Long expertId) {
        List<ExpertAccountRow> rows = jdbcTemplate.query(
                """
                        select e.id, e.code, e.name, e.mobile, e.email, e.user_id as userId, u.username
                        from aiadc_expert e
                        left join sys_user u on u.id = e.user_id and u.deleted = 0
                        where e.id = ? and e.deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new ExpertAccountRow(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("mobile"),
                        rs.getString("email"),
                        rs.getObject("userId", Long.class),
                        rs.getString("username")
                ),
                expertId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long findRoleId(String roleCode) {
        List<Long> rows = jdbcTemplate.query(
                "select id from sys_role where role_code = ? and deleted = 0 limit 1",
                (rs, rowNum) -> rs.getLong("id"),
                roleCode
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String nextUsername(String code, Long expertId) {
        String base = "expert_" + (StringUtils.hasText(code) ? code : "approved_" + expertId).replaceAll("[^A-Za-z0-9_-]", "_");
        if (!usernameExists(base)) {
            return base;
        }
        return base + "_" + expertId;
    }

    private boolean usernameExists(String username) {
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from sys_user where username = ? and deleted = 0",
                Long.class,
                username
        );
        return count != null && count > 0;
    }

    private String randomPassword() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return "Ex" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "Aa1!";
    }

    private Long aggregateId(PlatformEventOutboxEntity event) {
        try {
            Map<String, Object> payload = objectMapper.readValue(event.getPayloadJson(), new TypeReference<>() {});
            Object aggregateId = payload.get("aggregateId");
            if (aggregateId instanceof Number number) {
                return number.longValue();
            }
            if (aggregateId != null) {
                return Long.parseLong(String.valueOf(aggregateId));
            }
        } catch (Exception ignored) {
            // Fall back to event key parsing.
        }
        if (!StringUtils.hasText(event.getEventKey())) {
            return null;
        }
        String[] parts = event.getEventKey().split(":");
        if (parts.length == 0) {
            return null;
        }
        try {
            return Long.parseLong(parts[parts.length - 1].trim().toLowerCase(Locale.ROOT));
        } catch (Exception exception) {
            return null;
        }
    }

    private record ExpertAccountRow(Long id, String code, String name, String mobile, String email, Long userId, String username) {}
}
