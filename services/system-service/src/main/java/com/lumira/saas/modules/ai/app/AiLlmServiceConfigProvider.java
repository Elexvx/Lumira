package com.lumira.saas.modules.ai.app;

import com.lumira.saas.modules.ai.infrastructure.AiSecretCryptoService;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

public interface AiLlmServiceConfigProvider {

    Optional<AiLlmServiceConfig> findById(Long tenantId, Long serviceId);

    Optional<AiLlmServiceConfig> findDefault(Long tenantId);

    Optional<AiLlmServiceConfig> findDefaultForEmployee(Long tenantId, Long employeeId);

    Optional<AiLlmServiceConfig> findSupervisor(Long tenantId);
}


@Service
@Primary
class JdbcAiLlmServiceConfigProvider implements AiLlmServiceConfigProvider {

    private final MyBatisQueryOperations jdbcTemplate;
    private final AiSecretCryptoService aiSecretCryptoService;

    JdbcAiLlmServiceConfigProvider(MyBatisQueryOperations jdbcTemplate, AiSecretCryptoService aiSecretCryptoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiSecretCryptoService = aiSecretCryptoService;
    }

    @Override
    public Optional<AiLlmServiceConfig> findById(Long tenantId, Long serviceId) {
        if (tenantId == null || serviceId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                """
                        select id, provider, code, title, base_url as baseUrl, api_key_encrypted as apiKey, default_model as defaultModel,
                               timeout_ms as timeoutMs, temperature, max_tokens as maxTokens
                        from ai_llm_service
                        where tenant_id = ?
                          and id = ?
                          and is_deleted = 0
                          and enabled = 1
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiLlmServiceConfig.class),
                tenantId,
                serviceId
        ).stream()
                .findFirst()
                .map(this::decryptApiKey);
    }

    @Override
    public Optional<AiLlmServiceConfig> findDefault(Long tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                """
                        select id, provider, code, title, base_url as baseUrl, api_key_encrypted as apiKey, default_model as defaultModel,
                               timeout_ms as timeoutMs, temperature, max_tokens as maxTokens
                        from ai_llm_service
                        where tenant_id = ?
                          and is_deleted = 0
                          and enabled = 1
                        order by id asc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiLlmServiceConfig.class),
                tenantId
        ).stream()
                .findFirst()
                .map(this::decryptApiKey);
    }

    @Override
    public Optional<AiLlmServiceConfig> findDefaultForEmployee(Long tenantId, Long employeeId) {
        if (tenantId == null || employeeId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                """
                        select s.id, s.provider, s.code, s.title, s.base_url as baseUrl, s.api_key_encrypted as apiKey,
                               s.default_model as defaultModel, s.timeout_ms as timeoutMs, s.temperature, s.max_tokens as maxTokens
                        from ai_employee e
                        left join ai_llm_service s
                          on s.id = e.default_llm_service_id
                         and s.tenant_id = e.tenant_id
                         and s.is_deleted = 0
                        where e.tenant_id = ?
                          and e.id = ?
                          and e.is_deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiLlmServiceConfig.class),
                tenantId,
                employeeId
        ).stream()
                .findFirst()
                .filter(config -> config.getId() != null && StringUtils.hasText(config.getProvider()))
                .map(this::decryptApiKey);
    }

    @Override
    public Optional<AiLlmServiceConfig> findSupervisor(Long tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                """
                        select id, provider, code, title, base_url as baseUrl, api_key_encrypted as apiKey, default_model as defaultModel,
                               timeout_ms as timeoutMs, temperature, max_tokens as maxTokens
                        from ai_llm_service
                        where tenant_id = ?
                          and is_deleted = 0
                          and enabled = 1
                          and (
                            lower(code) in ('supervisor', 'ai-supervisor', 'guardrail', 'ai-guardrail')
                            or lower(title) like '%supervisor%'
                            or title like '%监督%'
                            or title like '%防护%'
                          )
                        order by id asc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(AiLlmServiceConfig.class),
                tenantId
        ).stream()
                .findFirst()
                .map(this::decryptApiKey);
    }

    private AiLlmServiceConfig decryptApiKey(AiLlmServiceConfig config) {
        if (config != null && StringUtils.hasText(config.getApiKey())) {
            config.setApiKey(aiSecretCryptoService.decrypt(config.getApiKey()));
        }
        return config;
    }
}
