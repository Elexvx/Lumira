package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.app.AiLlmServiceConfig;
import com.lumira.saas.modules.ai.repository.AiLlmConfigRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiLlmConfigRepository implements AiLlmConfigRepository {
    private static final String COLUMNS = "id, provider, code, title, base_url as baseUrl, api_key_encrypted as apiKey, default_model as defaultModel, timeout_ms as timeoutMs, temperature, max_tokens as maxTokens";
    private final MyBatisQueryOperations database;
    public JdbcAiLlmConfigRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override public Optional<AiLlmServiceConfig> findEnabledById(Long id) {
        return first("select " + COLUMNS + " from ai_llm_service where id = ? and is_deleted = 0 and enabled = 1 limit 1", id);
    }
    @Override public Optional<AiLlmServiceConfig> findFirstEnabled() {
        return first("select " + COLUMNS + " from ai_llm_service where is_deleted = 0 and enabled = 1 order by id asc limit 1");
    }
    @Override public Optional<AiLlmServiceConfig> findForEmployee(Long employeeId) {
        return first("""
                select s.id, s.provider, s.code, s.title, s.base_url as baseUrl, s.api_key_encrypted as apiKey,
                       s.default_model as defaultModel, s.timeout_ms as timeoutMs, s.temperature, s.max_tokens as maxTokens
                from ai_employee e left join ai_llm_service s on s.id = e.default_llm_service_id and s.is_deleted = 0
                where e.id = ? and e.is_deleted = 0 limit 1
                """, employeeId);
    }
    @Override public Optional<AiLlmServiceConfig> findSupervisor() {
        return first("""
                select id, provider, code, title, base_url as baseUrl, api_key_encrypted as apiKey,
                       default_model as defaultModel, timeout_ms as timeoutMs, temperature, max_tokens as maxTokens
                from ai_llm_service where is_deleted = 0 and enabled = 1 and (
                  lower(code) in ('supervisor','ai-supervisor','guardrail','ai-guardrail')
                  or lower(title) like '%supervisor%' or title like '%监督%' or title like '%防护%')
                order by id asc limit 1
                """);
    }
    private Optional<AiLlmServiceConfig> first(String sql, Object... args) {
        return database.query(sql, new BeanPropertyRowMapper<>(AiLlmServiceConfig.class), args).stream().findFirst();
    }
}
