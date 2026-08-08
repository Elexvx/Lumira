package com.lumira.saas.modules.iam.repository;

import com.lumira.saas.modules.iam.service.IamUserAccount;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Durable state boundary for IAM users.
 *
 * <p>Identity normalization, trust checks, conflict policy and audit intent
 * remain in {@code IamUserService}. This port owns SQL, row mapping and the
 * compare-and-set predicates that protect IAM records.</p>
 */
public interface IamUserRepository {
    SysUserEntity findActiveSysUserById(Long userId);

    IamUserAccount findActiveAccountById(Long userId);

    List<IamUserAccount.IdentityView> findActiveIdentities(Long userId, String userUuid);

    IdentityBinding findIdentityBinding(String identityType, String identifierNormalized);

    List<IamUserAccount.CredentialView> findActiveCredentials(Long userId, String userUuid, String credentialType);

    int passwordChangeRequired(Long userId, String userUuid);

    List<IamUserAccount.DeviceView> findRecentActiveDevices(Long userId, String userUuid, int limit);

    List<IamUserAccount.SecuritySettingView> findActiveSecuritySettings(Long userId, String userUuid);

    SysUserEntity findLegacyActiveSysUser(String rawAccount, String normalizedMobile, String normalizedAccount);

    int reviveIdentityForBinding(IdentityBinding existing, IdentityCommand command);

    int transferIdentity(IdentityBinding existing, IdentityCommand command);

    int synchronizeIdentity(IdentityBinding existing, IdentityCommand command);

    int insertIdentity(IdentityCommand command);

    int deactivateIdentity(Long userId, String userUuid, String identityType, String identifierNormalized);

    int updateAccountStatus(Long userId, String userUuid, String status);

    void updateIdentityStatus(Long userId, String userUuid, String status);

    void updateCredentialStatus(Long userId, String userUuid, String status);

    int softDeleteAccount(Long userId, String userUuid);

    void softDeleteAccountRelations(Long userId, String userUuid);

    int upsertAccount(SysUserEntity user, String userNo, String displayName, String status, String source, int deleted);

    int upsertPasswordCredential(PasswordCredential command);

    int updateLastLogin(LoginSuccess command);

    void markIdentityUsed(LoginSuccess command, String identityType, String identifierNormalized);

    int upsertDevice(LoginDevice command);

    int upsertProfile(SysUserEntity user, String userUuid, int deleted);

    int upsertSecuritySetting(Long userId, String userUuid, int deleted);

    int insertEvent(IamEvent event);

    record IdentityBinding(Long id, Long userId, String userUuid, int deleted) {}

    record IdentityCommand(
            Long userId,
            String userUuid,
            String identityType,
            String identifier,
            String identifierNormalized,
            boolean verified,
            boolean primaryIdentity,
            String status,
            int deleted
    ) {}

    record PasswordCredential(
            Long userId,
            String userUuid,
            String passwordHash,
            String status,
            int deleted,
            boolean resolvePasswordChange
    ) {}

    record LoginSuccess(Long userId, String userUuid, LocalDateTime occurredAt) {}

    record LoginDevice(
            Long userId,
            String userUuid,
            String deviceId,
            String deviceName,
            String deviceType,
            String os,
            String browser,
            String ip,
            LocalDateTime occurredAt
    ) {}

    record IamEvent(
            Long userId,
            String userUuid,
            String eventType,
            String eventSource,
            Long operatorId,
            String operatorUuid,
            String ip,
            String userAgent,
            String detailJson
    ) {}
}
