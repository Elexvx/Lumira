package com.lumira.saas.modules.account.app.port;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Account's narrow identity boundary.  IAM owns the user row and credential
 * write; Account only coordinates activation and never reaches IAM tables.
 */
public interface AccountIdentityActivationPort {
    Optional<Identity> findIdentity(Long userId);

    int activateIdentity(Long userId, String userUuid, String passwordHash, LocalDateTime now);

    record Identity(Long userId, String userUuid, String username, String email, String status) { }
}
