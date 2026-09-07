package com.lumira.saas.modules.iam.integration.account;

import com.lumira.saas.modules.account.app.port.AccountIdentityActivationPort;
import com.lumira.saas.modules.iam.repository.IamUserRepository;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import java.time.LocalDateTime;
import java.util.Optional;

/** IAM-owned adapter for the Account activation use case. */
public class IamAccountIdentityActivationAdapter implements AccountIdentityActivationPort {
    private final IamUserRepository iamUserRepository;
    private final IamUserService iamUserService;

    public IamAccountIdentityActivationAdapter(
            IamUserRepository iamUserRepository,
            IamUserService iamUserService
    ) {
        this.iamUserRepository = iamUserRepository;
        this.iamUserService = iamUserService;
    }

    @Override
    public Optional<Identity> findIdentity(Long userId) {
        SysUserEntity user = iamUserRepository.findActiveSysUserById(userId);
        if (user == null || user.getId() == null || user.getUuid() == null || user.getUuid().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Identity(
                user.getId(),
                user.getUuid(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus()
        ));
    }

    @Override
    public int activateIdentity(Long userId, String userUuid, String passwordHash, LocalDateTime now) {
        int updated = iamUserRepository.activateAccount(userId, userUuid, passwordHash, now);
        if (updated > 0) {
            iamUserService.upsertPasswordCredential(userId, userUuid, passwordHash);
        }
        return updated;
    }
}
