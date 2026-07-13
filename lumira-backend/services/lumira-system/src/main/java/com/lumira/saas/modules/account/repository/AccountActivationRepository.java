package com.lumira.saas.modules.account.repository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AccountActivationRepository {
    Optional<UserIdentity> findUser(Long userId);
    void invalidateOpenTokens(Long userId, String userUuid, Long operatorId, String operatorUuid, LocalDateTime now);
    int insertToken(String tokenHash, Long userId, String userUuid, Long expertId, LocalDateTime expiresAt, Long operatorId, String operatorUuid);
    Optional<TokenRecord> findValidToken(String tokenHash, LocalDateTime now);
    int consumeToken(TokenRecord token, LocalDateTime now);
    int activateUser(TokenRecord token, String passwordHash, LocalDateTime now);
    int activateExpert(TokenRecord token, LocalDateTime now);
    Optional<String> findPlatformConfig(String configKey);

    record UserIdentity(String uuid, String status) {}
    record TokenRecord(Long id, String tokenHash, Long userId, String userUuid, Long expertId, String username, String email) {}
}
