package com.lumira.saas.modules.account.repository;

import java.time.LocalDateTime;
public interface AccountActivationRepository {
    void invalidateOpenTokens(Long userId, String userUuid, Long operatorId, String operatorUuid, LocalDateTime now);
    int insertToken(String tokenHash, Long userId, String userUuid, Long expertId, LocalDateTime expiresAt, Long operatorId, String operatorUuid);
    java.util.Optional<TokenRecord> findValidToken(String tokenHash, LocalDateTime now);
    int consumeToken(TokenRecord token, LocalDateTime now);

    record TokenRecord(Long id, String tokenHash, Long userId, String userUuid, Long expertId) {}
}
