package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.expert.ExpertAccountProvisioningPort;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.account.app.AccountActivationService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.role.repository.SystemRoleManagementRepository;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.user.repository.SystemUserManagementRepository;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.util.List;
import org.springframework.util.StringUtils;

/** System-owned implementation of account provisioning requested by Expert approval. */
public class SystemExpertAccountProvisioningPort implements ExpertAccountProvisioningPort {
    private static final String EXPERT_ROLE_CODE = "EXPERT";

    private final SystemInternalApi systemInternalApi;
    private final SystemUserManagementAppService userManagementAppService;
    private final SystemRoleManagementRepository roleRepository;
    private final SystemUserManagementRepository userRepository;
    private final AccountActivationService accountActivationService;

    public SystemExpertAccountProvisioningPort(
            SystemInternalApi systemInternalApi,
            SystemUserManagementAppService userManagementAppService,
            SystemRoleManagementRepository roleRepository,
            SystemUserManagementRepository userRepository,
            AccountActivationService accountActivationService
    ) {
        this.systemInternalApi = systemInternalApi;
        this.userManagementAppService = userManagementAppService;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.accountActivationService = accountActivationService;
    }

    @Override
    public AccountIdentity findAccount(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        SystemUserSnapshotDTO user = systemInternalApi.findUserById(userId);
        if (user == null || user.userId() == null || !userId.equals(user.userId())
                || !StringUtils.hasText(user.userUuid()) || !StringUtils.hasText(user.username())) {
            return null;
        }
        return new AccountIdentity(user.userId(), user.userUuid().trim(), user.username().trim());
    }

    @Override
    public boolean usernameExists(String username) {
        return StringUtils.hasText(username)
                && userRepository.findActiveUserIdByUsername(username.trim()) != null;
    }

    @Override
    public AccountIdentity createExpertAccount(CurrentUser operator, CreateExpertAccount command) {
        Long expertRoleId = expertRoleId();
        SystemDTO.UserUpsertRequest request = new SystemDTO.UserUpsertRequest();
        request.setUsername(command.username());
        request.setPassword(command.password());
        request.setMobile(command.mobile());
        request.setEmail(command.email());
        request.setRealName(command.realName());
        request.setNickname(command.realName());
        request.setStatus("ENABLED");
        request.setRoleIds(List.of(expertRoleId));
        SystemVO.UserDetailVO created = userManagementAppService.createUserFromTrustedSnapshot(operator, request);
        if (created == null || created.getId() == null || !StringUtils.hasText(created.getUserUuid())
                || !StringUtils.hasText(created.getUsername())) {
            throw new IllegalStateException("Created expert account missing user uuid");
        }
        return new AccountIdentity(created.getId(), created.getUserUuid().trim(), created.getUsername().trim());
    }

    @Override
    public void ensureExpertRole(CurrentUser operator, AccountIdentity account) {
        AccountIdentity trustedAccount = requireAccount(account);
        int updated = userRepository.upsertUserRole(
                trustedAccount.userId(),
                trustedAccount.userUuid(),
                expertRoleId(),
                new SystemUserManagementRepository.Actor(operator.getUserId(), operator.getUserUuid())
        );
        if (updated <= 0) {
            throw new IllegalStateException("Expert role assignment changed before completion");
        }
    }

    @Override
    public void sendActivation(CurrentUser operator, Long expertId, String email, AccountIdentity account) {
        AccountIdentity trustedAccount = requireAccount(account);
        String token = accountActivationService.createActivationToken(
                trustedAccount.userId(), expertId, operator.getUserId(), operator.getUserUuid()
        );
        accountActivationService.sendActivationEmail(email, trustedAccount.username(), token);
    }

    private Long expertRoleId() {
        SystemVO.RoleVO role = roleRepository.findLatestActiveRoleByCode(EXPERT_ROLE_CODE);
        if (role == null || role.getId() == null || role.getId() <= 0) {
            throw new IllegalStateException("Expert role is not configured");
        }
        return role.getId();
    }

    private AccountIdentity requireAccount(AccountIdentity account) {
        if (account == null || account.userId() == null || account.userId() <= 0
                || !StringUtils.hasText(account.userUuid()) || !StringUtils.hasText(account.username())) {
            throw new IllegalStateException("Expert account is invalid");
        }
        return new AccountIdentity(account.userId(), account.userUuid().trim(), account.username().trim());
    }
}
