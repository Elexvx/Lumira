package com.lumira.api.expert;

import com.lumira.common.security.CurrentUser;

/** System-owned account and activation capabilities required by Expert approval. */
public interface ExpertAccountProvisioningPort {

    AccountIdentity findAccount(Long userId);

    boolean usernameExists(String username);

    AccountIdentity createExpertAccount(CurrentUser operator, CreateExpertAccount command);

    void ensureExpertRole(CurrentUser operator, AccountIdentity account);

    void sendActivation(CurrentUser operator, Long expertId, String email, AccountIdentity account);

    record AccountIdentity(Long userId, String userUuid, String username) {
    }

    record CreateExpertAccount(
            String username,
            String password,
            String mobile,
            String email,
            String realName
    ) {
    }
}
