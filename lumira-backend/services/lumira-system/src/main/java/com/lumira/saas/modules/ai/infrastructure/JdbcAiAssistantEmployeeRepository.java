package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.repository.AiAssistantEmployeeRepository;
import com.lumira.saas.modules.ai.vo.AiVO;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiAssistantEmployeeRepository implements AiAssistantEmployeeRepository {
    private static final String SELECT = """
            select e.id, e.username, e.nickname, e.position, e.avatar_key as avatarKey,
                   e.description, e.greeting, e.system_prompt as systemPrompt,
                   e.default_llm_service_id as defaultLlmServiceId,
                   e.enabled, e.sort_order as sortOrder, e.create_time as createTime, e.update_time as updateTime,
                   s.title as defaultLlmServiceTitle
            from ai_employee e left join ai_llm_service s on s.id = e.default_llm_service_id and s.is_deleted = 0
            where e.username = ? and e.is_deleted = 0 and e.enabled = 1 limit 1
            """;
    private final MyBatisQueryOperations database;
    public JdbcAiAssistantEmployeeRepository(MyBatisQueryOperations database) { this.database = database; }
    @Override public Optional<AiVO.EmployeeVO> findEnabled(String username) { return find(username, AiVO.EmployeeVO.class); }
    @Override public Optional<AiVO.EmployeeDetailVO> findEnabledDetail(String username) { return find(username, AiVO.EmployeeDetailVO.class); }
    private <T extends AiVO.EmployeeVO> Optional<T> find(String username, Class<T> type) {
        return database.query(SELECT, new BeanPropertyRowMapper<>(type), username).stream().findFirst();
    }
}
