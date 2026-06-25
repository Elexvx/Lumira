package com.lumira.saas.modules.ai.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.vo.AiVO;

import java.time.LocalDateTime;

final class AiAssistantEmployeeResolver {

    static final String ASSISTANT_USERNAME = "ai-assistant";

    private static final String ASSISTANT_NICKNAME = "AI Assistant";
    private static final String ASSISTANT_POSITION = "General Chat";
    private static final String ASSISTANT_DESCRIPTION = "Default assistant for general AI conversations.";
    private static final String ASSISTANT_GREETING = "Hello, I am AI Assistant. How can I help?";
    private static final String ASSISTANT_SYSTEM_PROMPT = "You are the general AI assistant for this enterprise platform. "
            + "Help clearly and concisely, answer in the user's language, and do not claim access to a specific digital "
            + "employee's private skills or knowledge unless one is selected.";

    private final MyBatisQueryOperations jdbcTemplate;

    AiAssistantEmployeeResolver(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    AiVO.EmployeeVO getOrCreateAssistantEmployee() {
        AiVO.EmployeeVO employee = queryAssistantEmployee(AiVO.EmployeeVO.class);
        if (employee != null) {
            return employee;
        }
        upsertAssistantEmployee();
        employee = queryAssistantEmployee(AiVO.EmployeeVO.class);
        if (employee == null || employee.getId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "AI assistant employee is unavailable");
        }
        return employee;
    }

    AiVO.EmployeeDetailVO getOrCreateAssistantEmployeeDetail() {
        AiVO.EmployeeDetailVO employee = queryAssistantEmployee(AiVO.EmployeeDetailVO.class);
        if (employee != null) {
            return employee;
        }
        upsertAssistantEmployee();
        employee = queryAssistantEmployee(AiVO.EmployeeDetailVO.class);
        if (employee == null || employee.getId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "AI assistant employee is unavailable");
        }
        return employee;
    }

    private <T extends AiVO.EmployeeVO> T queryAssistantEmployee(Class<T> voClass) {
        return jdbcTemplate.query(
                """
                        select e.id, e.username, e.nickname, e.position, e.avatar_key as avatarKey,
                               e.description, e.greeting, e.system_prompt as systemPrompt,
                               e.default_llm_service_id as defaultLlmServiceId,
                               e.enabled, e.sort_order as sortOrder, e.create_time as createTime, e.update_time as updateTime,
                               s.title as defaultLlmServiceTitle
                        from ai_employee e
                        left join ai_llm_service s
                          on s.id = e.default_llm_service_id
                         and s.is_deleted = 0
                        where e.username = ?
                          and e.is_deleted = 0
                          and e.enabled = 1
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(voClass),
                ASSISTANT_USERNAME
        ).stream().findFirst().orElse(null);
    }

    private void upsertAssistantEmployee() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into ai_employee (
                            username, nickname, position, avatar_key, description, greeting,
                            system_prompt, default_llm_service_id, enabled, sort_order, is_deleted, create_time, update_time
                        ) values (?, ?, ?, null, ?, ?, ?, null, 1, 100000, 0, ?, ?)
                        on duplicate key update
                            nickname = values(nickname),
                            position = values(position),
                            description = values(description),
                            greeting = values(greeting),
                            system_prompt = values(system_prompt),
                            enabled = 1,
                            update_time = values(update_time)
                        """,
                ASSISTANT_USERNAME,
                ASSISTANT_NICKNAME,
                ASSISTANT_POSITION,
                ASSISTANT_DESCRIPTION,
                ASSISTANT_GREETING,
                ASSISTANT_SYSTEM_PROMPT,
                now,
                now
        );
    }
}
