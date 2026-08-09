package com.lumira.saas.modules.ai.app;

import com.lumira.saas.modules.ai.infrastructure.AiSecretCryptoService;
import com.lumira.saas.modules.ai.repository.AiLlmConfigRepository;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

public interface AiLlmServiceConfigProvider {
    Optional<AiLlmServiceConfig> findById(Long serviceId);
    Optional<AiLlmServiceConfig> findDefault();
    Optional<AiLlmServiceConfig> findDefaultForEmployee(Long employeeId);
    Optional<AiLlmServiceConfig> findSupervisor();
}

@Service
@Primary
class JdbcAiLlmServiceConfigProvider implements AiLlmServiceConfigProvider {
    private final AiLlmConfigRepository repository;
    private final AiSecretCryptoService cryptoService;

    JdbcAiLlmServiceConfigProvider(AiLlmConfigRepository repository, AiSecretCryptoService cryptoService) {
        this.repository = repository;
        this.cryptoService = cryptoService;
    }
    @Override public Optional<AiLlmServiceConfig> findById(Long id) {
        return id == null ? Optional.empty() : repository.findEnabledById(id).map(this::decrypt);
    }
    @Override public Optional<AiLlmServiceConfig> findDefault() { return repository.findFirstEnabled().map(this::decrypt); }
    @Override public Optional<AiLlmServiceConfig> findDefaultForEmployee(Long id) {
        if (id == null) return Optional.empty();
        return repository.findForEmployee(id)
                .filter(config -> config.getId() != null && StringUtils.hasText(config.getProvider())).map(this::decrypt);
    }
    @Override public Optional<AiLlmServiceConfig> findSupervisor() { return repository.findSupervisor().map(this::decrypt); }
    private AiLlmServiceConfig decrypt(AiLlmServiceConfig config) {
        if (config != null && StringUtils.hasText(config.getApiKey())) config.setApiKey(cryptoService.decrypt(config.getApiKey()));
        return config;
    }
}
