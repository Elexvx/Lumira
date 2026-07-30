package com.lumira.saas.modules.iam.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IamUserService {

    public static final String IDENTITY_USERNAME = "USERNAME";
    public static final String IDENTITY_MOBILE = "MOBILE";
    public static final String IDENTITY_EMAIL = "EMAIL";
    public static final String IDENTITY_WECHAT_OPENID = "WECHAT_OPENID";
    public static final String IDENTITY_WECHAT_UNIONID = "WECHAT_UNIONID";
    public static final String IDENTITY_PASSKEY = "PASSKEY";

    private final MyBatisQueryOperations jdbcTemplate;

    public IamUserService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SysUserEntity> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        Optional<IamUserAccount> account = findAccountByUserId(userId);
        return account.map(IamUserAccount::getLegacyUser).or(() -> Optional.ofNullable(querySysUserById(userId)));
    }

    public Optional<IamUserAccount> findAccountByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        SysUserEntity legacyUser = querySysUserById(userId);
        if (legacyUser == null || !StringUtils.hasText(legacyUser.getUuid())) {
            return Optional.empty();
        }
        String userUuid = legacyUser.getUuid();
        IamUserAccount account = queryIamAccountById(userId);
        if (account == null) {
            return Optional.empty();
        }
        account.setUserUuid(userUuid);
        account.setIdentities(queryIdentities(userId, userUuid));
        account.setDevices(listRecentDevices(userId, userUuid, 5));
        account.setSecuritySetting(findSecuritySetting(userId, userUuid).orElse(null));
        account.setPasswordCredential(findActiveCredential(userId, userUuid, "PASSWORD").orElse(null));
        account.setLegacyUser(legacyUser);
        return Optional.of(account);
    }

    public Optional<SysUserEntity> findByIdentity(String identityType, String identifier) {
        return findAccountByIdentity(identityType, identifier).map(IamUserAccount::getLegacyUser);
    }

    public Optional<IamUserAccount> findAccountByIdentity(String identityType, String identifier) {
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        if (!StringUtils.hasText(normalizedType) || !StringUtils.hasText(normalizedIdentifier)) {
            return Optional.empty();
        }
        IdentityBinding binding = queryIdentityBinding(normalizedType, normalizedIdentifier);
        if (binding == null || binding.deleted() != 0 || !StringUtils.hasText(binding.userUuid())) {
            return Optional.empty();
        }
        return findAccountByUserId(binding.userId())
                .filter(account -> binding.userUuid().equals(account.getUserUuid()));
    }

    public Optional<SysUserEntity> findByLoginAccount(String account) {
        return findAccountByLoginAccount(account).map(IamUserAccount::getLegacyUser);
    }

    public Optional<IamUserAccount> findAccountByLoginAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return Optional.empty();
        }
        List<String> identityTypes = candidateLoginIdentityTypes(account);
        for (String identityType : identityTypes) {
            Optional<IamUserAccount> user = findAccountByIdentity(identityType, account);
            if (user.isPresent()) {
                return user;
            }
        }
        Optional<SysUserEntity> legacy = findLegacySysUser(account);
        legacy.ifPresent(user -> syncSysUser(user, "LEGACY_LOGIN_FALLBACK"));
        return legacy.flatMap(user -> findAccountByUserId(user.getId()));
    }

    @Transactional
    public void createUserWithIdentity(SysUserEntity user, String rawAccount, String source) {
        syncSysUser(user, defaultSource(source));
        String identityType = detectIdentityType(rawAccount);
        if (StringUtils.hasText(identityType)) {
            int deleted = deletedFlag(user.getDeleted());
            String status = syncStatus(user.getStatus(), deleted);
            if (deleted == 0 && "ENABLED".equals(status)) {
                bindIdentity(user.getId(), requireUserUuid(user), identityType, rawAccount, true, true);
            } else {
                syncIdentityFromSysUser(user.getId(), requireUserUuid(user), identityType, rawAccount, true, true, status, deleted);
            }
        }
    }

    @Transactional
    public void bindIdentity(Long userId, String identityType, String identifier) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user uuid is required");
    }

    @Transactional
    public void bindIdentity(Long userId, String identityType, String identifier, boolean verified, boolean primaryIdentity) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user uuid is required");
    }

    @Transactional
    public void bindIdentity(Long userId, String userUuid, String identityType, String identifier) {
        bindIdentity(userId, userUuid, identityType, identifier, false, false);
    }

    @Transactional
    public void bindIdentity(Long userId, String userUuid, String identityType, String identifier, boolean verified, boolean primaryIdentity) {
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        if (userId == null || !StringUtils.hasText(userUuid) || !StringUtils.hasText(normalizedType) || !StringUtils.hasText(normalizedIdentifier)) {
            return;
        }
        String trustedUserUuid = requireMatchingUserUuid(userId, userUuid);
        IdentityBinding existing = queryIdentityBinding(normalizedType, normalizedIdentifier);
        if (existing != null && (!userId.equals(existing.userId()) || !trustedUserUuid.equals(existing.userUuid())) && existing.deleted() == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已被其他用户绑定", "登录身份已被其他用户绑定");
        }
        if (existing != null) {
            int updated = jdbcTemplate.update(
                    """
                            update iam_user_identity
                            set user_id = ?,
                                user_uuid = ?,
                                identifier = ?,
                                verified = greatest(verified, ?),
                                primary_identity = greatest(primary_identity, ?),
                                status = 'ENABLED',
                                deleted = 0,
                                updated_at = current_timestamp
                            where id = ?
                              and identity_type = ?
                              and identifier_normalized = ?
                              and ((deleted = 1) or (user_id = ? and user_uuid = ?))
                            """,
                    userId,
                    trustedUserUuid,
                    identifier.trim(),
                    verified ? 1 : 0,
                    primaryIdentity ? 1 : 0,
                    existing.id(),
                    normalizedType,
                    normalizedIdentifier,
                    userId,
                    trustedUserUuid
            );
            if (updated <= 0) {
                throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已变更，请重试", "登录身份已变更，请重试");
            }
            return;
        }
        int inserted = jdbcTemplate.update(
                """
                        insert into iam_user_identity (
                            user_id, user_uuid, identity_type, identifier, identifier_normalized, verified, primary_identity, status, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, 'ENABLED', 0)
                        """,
                userId,
                trustedUserUuid,
                normalizedType,
                identifier.trim(),
                normalizedIdentifier,
                verified ? 1 : 0,
                primaryIdentity ? 1 : 0
        );
        requireIamUserWrite(inserted);
    }

    @Transactional
    public void transferIdentity(Long targetUserId, String identityType, String identifier, Long operatorId, String reason, String ip, String userAgent) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted target user uuid is required");
    }

    @Transactional
    public void transferIdentity(Long targetUserId, String identityType, String identifier, Long operatorId, String operatorUuid, String reason, String ip, String userAgent) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted target user uuid is required");
    }

    @Transactional
    public void transferIdentity(Long targetUserId, String targetUserUuid, String identityType, String identifier, Long operatorId, String operatorUuid, String reason, String ip, String userAgent) {
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        if (targetUserId == null || !StringUtils.hasText(targetUserUuid) || !StringUtils.hasText(normalizedType) || !StringUtils.hasText(normalizedIdentifier)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "身份转移参数不完整");
        }
        String trustedOperatorUuid = requireOperatorUuid(operatorId, operatorUuid);
        String trustedTargetUserUuid = requireMatchingUserUuid(targetUserId, targetUserUuid);
        IdentityBinding existing = queryIdentityBinding(normalizedType, normalizedIdentifier);
        Long previousUserId = existing == null ? null : existing.userId();
        if (existing == null) {
            bindIdentity(targetUserId, trustedTargetUserUuid, normalizedType, identifier, true, false);
        } else if (!targetUserId.equals(existing.userId())) {
            int updated = jdbcTemplate.update(
                    """
                            update iam_user_identity
                            set user_id = ?, user_uuid = ?, identifier = ?, verified = 1, status = 'ENABLED', deleted = 0, updated_at = current_timestamp
                            where id = ?
                              and identity_type = ?
                              and identifier_normalized = ?
                              and ((deleted = 1) or (user_id = ? and user_uuid = ?))
                            """,
                    targetUserId,
                    trustedTargetUserUuid,
                    identifier.trim(),
                    existing.id(),
                    normalizedType,
                    normalizedIdentifier,
                    existing.userId(),
                    existing.userUuid()
            );
            if (updated <= 0) {
                throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已变更，请重试", "登录身份已变更，请重试");
            }
        }
        recordEvent(
                targetUserId,
                "IDENTITY_TRANSFERRED",
                "IAM",
                operatorId,
                trustedOperatorUuid,
                ip,
                userAgent,
                "{\"identityType\":\"" + jsonEscape(normalizedType)
                        + "\",\"identifierNormalized\":\"" + jsonEscape(normalizedIdentifier)
                        + "\",\"previousUserId\":" + (previousUserId == null ? "null" : previousUserId)
                        + ",\"targetUserId\":" + targetUserId
                        + ",\"reason\":\"" + jsonEscape(reason) + "\"}"
        );
    }

    @Transactional
    public void unbindIdentity(Long userId, String identityType, String identifier) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user uuid is required");
    }

    @Transactional
    public void unbindIdentity(Long userId, String userUuid, String identityType, String identifier) {
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        if (userId == null || !StringUtils.hasText(userUuid) || !StringUtils.hasText(normalizedType) || !StringUtils.hasText(normalizedIdentifier)) {
            return;
        }
        String trustedUserUuid = requireMatchingUserUuid(userId, userUuid);
        int identityUpdated = jdbcTemplate.update(
                """
                        update iam_user_identity
                        set status = 'DISABLED', deleted = 1, updated_at = current_timestamp
                        where user_id = ? and user_uuid = ? and identity_type = ? and identifier_normalized = ? and deleted = 0
                """,
                userId,
                trustedUserUuid,
                normalizedType,
                normalizedIdentifier
        );
        if (identityUpdated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Login identity changed, please retry");
        }
    }

    @Transactional
    public void changeUserStatus(Long userId, String userUuid, String status) {
        if (userId == null || !StringUtils.hasText(userUuid) || !StringUtils.hasText(status)) {
            return;
        }
        String trustedUserUuid = requireMatchingUserUuid(userId, userUuid);
        int userUpdated = jdbcTemplate.update(
                """
                        update iam_user
                        set status = ?, updated_at = current_timestamp
                        where id = ?
                          and deleted = 0
                          and exists (
                              select 1 from sys_user u
                              where u.id = iam_user.id
                                and u.uuid = ?
                                and u.deleted = 0
                          )
                        """,
                status,
                userId,
                trustedUserUuid
        );
        requireIamUserWrite(userUpdated);
        jdbcTemplate.update(
                "update iam_user_identity set status = ?, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                status,
                userId,
                trustedUserUuid
        );
        jdbcTemplate.update(
                "update iam_user_credential set status = ?, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                status,
                userId,
                trustedUserUuid
        );
    }

    @Transactional
    public void softDeleteUser(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return;
        }
        String trustedUserUuid = requireMatchingUserUuid(userId, userUuid);
        int userUpdated = jdbcTemplate.update(
                """
                        update iam_user
                        set status = 'DISABLED', deleted = 1, updated_at = current_timestamp
                        where id = ?
                          and deleted = 0
                          and exists (
                              select 1 from sys_user u
                              where u.id = iam_user.id
                                and u.uuid = ?
                                and u.deleted = 0
                          )
                        """,
                userId,
                trustedUserUuid
        );
        requireIamUserWrite(userUpdated);
        jdbcTemplate.update(
                "update iam_user_identity set status = 'DISABLED', deleted = 1, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                userId,
                trustedUserUuid
        );
        jdbcTemplate.update(
                "update iam_user_credential set status = 'DISABLED', deleted = 1, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                userId,
                trustedUserUuid
        );
        jdbcTemplate.update(
                "update iam_user_device set deleted = 1, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                userId,
                trustedUserUuid
        );
        jdbcTemplate.update(
                "update iam_user_security_setting set deleted = 1, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                userId,
                trustedUserUuid
        );
    }

    @Transactional
    public void updateProfile(SysUserEntity user) {
        if (user == null || user.getId() == null) {
            return;
        }
        syncSysUser(user, "SYS_USER_SYNC");
    }

    @Transactional
    public void syncSysUser(SysUserEntity user, String source) {
        if (user == null || user.getId() == null) {
            return;
        }
        String displayName = firstText(user.getNickname(), user.getRealName(), user.getUsername(), "用户" + user.getId());
        String userUuid = requireUserUuid(user);
        int deleted = deletedFlag(user.getDeleted());
        String status = syncStatus(user.getStatus(), deleted);
        int userUpserted = jdbcTemplate.update(
                """
                        insert into iam_user (
                            id, user_no, display_name, avatar_url, status, user_type, source, registered_at, deleted
                        ) values (?, ?, ?, ?, ?, 'REGISTERED', ?, current_timestamp, ?)
                        on duplicate key update display_name = values(display_name),
                                                avatar_url = values(avatar_url),
                                                status = values(status),
                                                updated_at = current_timestamp,
                                                deleted = values(deleted)
                        """,
                user.getId(),
                userNo(user.getId()),
                displayName,
                user.getAvatarUrl(),
                status,
                defaultSource(source),
                deleted
        );
        requireIamUserWrite(userUpserted);
        syncIdentityFromSysUser(user.getId(), userUuid, IDENTITY_USERNAME, user.getUsername(), true, true, status, deleted);
        bindLegacyIdentityIfAvailable(user.getId(), userUuid, IDENTITY_MOBILE, user.getMobile(), status, deleted);
        bindLegacyIdentityIfAvailable(user.getId(), userUuid, IDENTITY_EMAIL, user.getEmail(), status, deleted);
        upsertPasswordCredentialFromSysUser(user.getId(), userUuid, user.getPasswordHash(), status, deleted);
        upsertProfile(user);
        upsertSecuritySetting(user.getId(), userUuid, deleted);
    }

    private void bindLegacyIdentityIfAvailable(Long userId, String userUuid, String identityType, String identifier, String status, int deleted) {
        try {
            syncIdentityFromSysUser(userId, userUuid, identityType, identifier, true, false, status, deleted);
        } catch (BizException exception) {
            recordEvent(
                    userId,
                    "IDENTITY_SYNC_CONFLICT",
                    "IAM",
                    userId,
                    userUuid,
                    null,
                    null,
                    "{\"identityType\":\"" + jsonEscape(identityType)
                            + "\",\"identifierNormalized\":\"" + jsonEscape(normalizeIdentifier(identityType, identifier))
                            + "\"}"
            );
        }
    }

    private void syncIdentityFromSysUser(Long userId, String userUuid, String identityType, String identifier, boolean verified, boolean primaryIdentity, String status, int deleted) {
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        if (userId == null || !StringUtils.hasText(userUuid) || !StringUtils.hasText(normalizedType) || !StringUtils.hasText(normalizedIdentifier)) {
            return;
        }
        IdentityBinding existing = queryIdentityBinding(normalizedType, normalizedIdentifier);
        if (existing != null && (!userId.equals(existing.userId()) || !userUuid.equals(existing.userUuid())) && existing.deleted() == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已被其他用户绑定", "登录身份已被其他用户绑定");
        }
        if (existing != null) {
            int updated = jdbcTemplate.update(
                    """
                            update iam_user_identity
                            set user_id = ?,
                                user_uuid = ?,
                                identifier = ?,
                                verified = greatest(verified, ?),
                                primary_identity = greatest(primary_identity, ?),
                                status = ?,
                                deleted = ?,
                                updated_at = current_timestamp
                            where id = ?
                              and identity_type = ?
                              and identifier_normalized = ?
                              and user_id = ?
                              and user_uuid = ?
                              and deleted = ?
                            """,
                    userId,
                    userUuid,
                    identifier.trim(),
                    verified ? 1 : 0,
                    primaryIdentity ? 1 : 0,
                    status,
                    deleted,
                    existing.id(),
                    normalizedType,
                    normalizedIdentifier,
                    existing.userId(),
                    existing.userUuid(),
                    existing.deleted()
            );
            if (updated <= 0) {
                throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已变更，请重试", "登录身份已变更，请重试");
            }
            return;
        }
        int inserted = jdbcTemplate.update(
                """
                        insert into iam_user_identity (
                            user_id, user_uuid, identity_type, identifier, identifier_normalized, verified, primary_identity, status, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                userUuid,
                normalizedType,
                identifier.trim(),
                normalizedIdentifier,
                verified ? 1 : 0,
                primaryIdentity ? 1 : 0,
                status,
                deleted
        );
        requireIamUserWrite(inserted);
    }

    @Transactional
    public void upsertPasswordCredential(Long userId, String userUuid, String passwordHash) {
        requireMatchingUserUuid(userId, userUuid);
        upsertPasswordCredential(userId, userUuid.trim(), passwordHash, "ENABLED", 0, true);
    }

    private void upsertPasswordCredentialFromSysUser(Long userId, String userUuid, String passwordHash, String status, int deleted) {
        upsertPasswordCredential(userId, userUuid, passwordHash, status, deleted, false);
    }

    private void upsertPasswordCredential(
            Long userId,
            String userUuid,
            String passwordHash,
            String status,
            int deleted,
            boolean resolvePasswordChange
    ) {
        if (userId == null || !StringUtils.hasText(userUuid) || !StringUtils.hasText(passwordHash)) {
            return;
        }
        int credentialUpserted = jdbcTemplate.update(
                """
                        insert into iam_user_credential (
                            user_id, user_uuid, credential_type, credential_secret, algorithm, version,
                            last_changed_at, password_change_required, status, deleted
                        ) values (?, ?, 'PASSWORD', ?, 'BCRYPT', 1, current_timestamp, 0, ?, ?)
                        on duplicate key update credential_secret = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(credential_secret) else credential_secret end,
                                                algorithm = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(algorithm) else algorithm end,
                                                last_changed_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then current_timestamp else last_changed_at end,
                                                password_change_required = case when user_id = values(user_id) and user_uuid = values(user_uuid) and ? = 1 then 0 else password_change_required end,
                                                status = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(status) else status end,
                                                deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(deleted) else deleted end,
                                                updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then current_timestamp else updated_at end
                        """,
                userId,
                userUuid,
                passwordHash,
                status,
                deleted,
                resolvePasswordChange ? 1 : 0
        );
        requireIamUserWrite(credentialUpserted);
    }

    public boolean requiresPasswordChange(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return false;
        }
        requireMatchingUserUuid(userId, userUuid);
        Integer required = jdbcTemplate.queryForObject(
                """
                        select coalesce(max(password_change_required), 0)
                        from iam_user_credential
                        where user_id = ?
                          and user_uuid = ?
                          and credential_type = 'PASSWORD'
                          and status = 'ENABLED'
                          and deleted = 0
                        """,
                Integer.class,
                userId,
                userUuid.trim()
        );
        return required != null && required == 1;
    }

    public Optional<IamUserAccount.CredentialView> findActiveCredential(Long userId, String userUuid, String credentialType) {
        if (userId == null || !StringUtils.hasText(userUuid) || !StringUtils.hasText(credentialType)) {
            return Optional.empty();
        }
        requireMatchingUserUuid(userId, userUuid);
        List<IamUserAccount.CredentialView> credentials = jdbcTemplate.query(
                """
                        select id, user_id as userId, user_uuid as userUuid, credential_type as credentialType, credential_secret as credentialSecret,
                               algorithm, version, expire_at as expireAt, last_changed_at as lastChangedAt, status
                        from iam_user_credential
                        where user_id = ?
                          and user_uuid = ?
                          and credential_type = ?
                          and status = 'ENABLED'
                          and deleted = 0
                          and (expire_at is null or expire_at > current_timestamp)
                        order by version desc, id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(IamUserAccount.CredentialView.class),
                userId,
                userUuid.trim(),
                credentialType.trim().toUpperCase(Locale.ROOT)
        );
        return credentials.isEmpty() ? Optional.empty() : Optional.of(credentials.get(0));
    }

    public List<IamUserAccount.IdentityView> listIdentities(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return List.of();
        }
        requireMatchingUserUuid(userId, userUuid);
        return queryIdentities(userId, userUuid.trim());
    }

    public List<IamUserAccount.DeviceView> listRecentDevices(Long userId, String userUuid, int limit) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return List.of();
        }
        requireMatchingUserUuid(userId, userUuid);
        int safeLimit = limit <= 0 ? 5 : Math.min(limit, 20);
        return jdbcTemplate.query(
                """
                        select id, user_id as userId, user_uuid as userUuid, device_id as deviceId, device_name as deviceName,
                               device_type as deviceType, os, browser, last_ip as lastIp,
                               last_active_at as lastActiveAt, trusted
                        from iam_user_device
                        where user_id = ? and user_uuid = ? and deleted = 0
                        order by last_active_at desc, id desc
                        limit ?
                        """,
                (rs, rowNum) -> {
                    IamUserAccount.DeviceView device = new IamUserAccount.DeviceView();
                    device.setId(rs.getLong("id"));
                    device.setUserId(rs.getLong("userId"));
                    device.setUserUuid(rs.getString("userUuid"));
                    device.setDeviceId(rs.getString("deviceId"));
                    device.setDeviceName(rs.getString("deviceName"));
                    device.setDeviceType(rs.getString("deviceType"));
                    device.setOs(rs.getString("os"));
                    device.setBrowser(rs.getString("browser"));
                    device.setLastIp(rs.getString("lastIp"));
                    device.setLastActiveAt(rs.getTimestamp("lastActiveAt") == null ? null : rs.getTimestamp("lastActiveAt").toLocalDateTime());
                    device.setTrusted(rs.getInt("trusted") == 1);
                    return device;
                },
                userId,
                userUuid.trim(),
                safeLimit
        );
    }

    public Optional<IamUserAccount.SecuritySettingView> findSecuritySetting(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return Optional.empty();
        }
        requireMatchingUserUuid(userId, userUuid);
        List<IamUserAccount.SecuritySettingView> settings = jdbcTemplate.query(
                """
                        select user_id as userId, user_uuid as userUuid, mfa_enabled as mfaEnabled,
                               password_login_enabled as passwordLoginEnabled,
                               sms_login_enabled as smsLoginEnabled,
                               email_login_enabled as emailLoginEnabled,
                               passkey_enabled as passkeyEnabled,
                               login_notify_enabled as loginNotifyEnabled
                        from iam_user_security_setting
                        where user_id = ? and user_uuid = ? and deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> {
                    IamUserAccount.SecuritySettingView setting = new IamUserAccount.SecuritySettingView();
                    setting.setUserId(rs.getLong("userId"));
                    setting.setUserUuid(rs.getString("userUuid"));
                    setting.setMfaEnabled(rs.getInt("mfaEnabled") == 1);
                    setting.setPasswordLoginEnabled(rs.getInt("passwordLoginEnabled") == 1);
                    setting.setSmsLoginEnabled(rs.getInt("smsLoginEnabled") == 1);
                    setting.setEmailLoginEnabled(rs.getInt("emailLoginEnabled") == 1);
                    setting.setPasskeyEnabled(rs.getInt("passkeyEnabled") == 1);
                    setting.setLoginNotifyEnabled(rs.getInt("loginNotifyEnabled") == 1);
                    return setting;
                },
                userId,
                userUuid.trim()
        );
        return settings.isEmpty() ? Optional.empty() : Optional.of(settings.get(0));
    }

    @Transactional
    public void recordLoginSuccess(Long userId, String identityType, String account, String ip, String userAgent) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user uuid is required");
    }

    @Transactional
    public void recordLoginSuccess(Long userId, String identityType, String account, String ip, String userAgent, String deviceId) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user uuid is required");
    }

    @Transactional
    public void recordLoginSuccess(Long userId, String userUuid, String identityType, String account, String ip, String userAgent, String deviceId) {
        LocalDateTime now = LocalDateTime.now();
        String trustedUserUuid = requireMatchingUserUuid(userId, userUuid);
        int userUpdated = jdbcTemplate.update(
                """
                        update iam_user
                        set last_login_at = ?, updated_at = ?
                        where id = ?
                          and deleted = 0
                          and exists (
                              select 1 from sys_user u
                              where u.id = iam_user.id
                                and u.uuid = ?
                                and u.deleted = 0
                          )
                        """,
                now,
                now,
                userId,
                trustedUserUuid
        );
        requireIamUserWrite(userUpdated);
        if (StringUtils.hasText(identityType) && StringUtils.hasText(account)) {
            jdbcTemplate.update(
                    """
                            update iam_user_identity
                            set last_used_at = ?, updated_at = ?
                            where user_id = ? and user_uuid = ? and identity_type = ? and identifier_normalized = ? and deleted = 0
                            """,
                    now,
                    now,
                    userId,
                    trustedUserUuid,
                    normalizeIdentityType(identityType),
                    normalizeIdentifier(identityType, account)
            );
        }
        upsertLoginDevice(userId, trustedUserUuid, deviceId, ip, userAgent, now);
        recordEvent(userId, "USER_LOGIN_SUCCESS", "AUTH", userId, trustedUserUuid, ip, userAgent, "{\"result\":\"SUCCESS\"}");
    }

    public void recordLoginFailure(Long userId, String eventType, String account, String ip, String userAgent) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user uuid is required");
    }

    public void recordLoginFailure(Long userId, String userUuid, String eventType, String account, String ip, String userAgent) {
        String trustedUserUuid = userId == null ? null : requireMatchingUserUuid(userId, userUuid);
        recordEvent(userId, eventType, "AUTH", userId, trustedUserUuid, ip, userAgent, "{\"account\":\"" + jsonEscape(account) + "\"}");
    }

    public void recordUserRegistered(Long userId, String userUuid, String source, String ip, String userAgent) {
        String trustedUserUuid = userId == null ? null : requireMatchingUserUuid(userId, userUuid);
        recordEvent(userId, "USER_REGISTERED", defaultSource(source), userId, trustedUserUuid, ip, userAgent, "{\"source\":\"" + jsonEscape(defaultSource(source)) + "\"}");
    }

    public String detectIdentityType(String account) {
        if (!StringUtils.hasText(account)) {
            return null;
        }
        String trimmed = account.trim();
        if (isMobile(trimmed)) {
            return IDENTITY_MOBILE;
        }
        if (isEmail(trimmed)) {
            return IDENTITY_EMAIL;
        }
        return IDENTITY_USERNAME;
    }

    public String normalizeLoginAccount(String account) {
        return normalizeIdentifier(detectIdentityType(account), account);
    }

    public String normalizeIdentifier(String identityType, String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return null;
        }
        String value = identifier.trim();
        String normalizedType = normalizeIdentityType(identityType);
        if (IDENTITY_EMAIL.equals(normalizedType) || IDENTITY_USERNAME.equals(normalizedType)) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (IDENTITY_MOBILE.equals(normalizedType)) {
            return value.replace(" ", "").replace("-", "").replace("+86", "");
        }
        return value;
    }

    private void upsertLoginDevice(Long userId, String userUuid, String rawDeviceId, String ip, String userAgent, LocalDateTime now) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return;
        }
        String deviceId = StringUtils.hasText(rawDeviceId) ? rawDeviceId.trim() : temporaryDeviceId(ip, userAgent);
        DeviceInfo deviceInfo = parseDeviceInfo(userAgent);
        int deviceUpserted = jdbcTemplate.update(
                """
                        insert into iam_user_device (
                            user_id, user_uuid, device_id, device_name, device_type, os, browser, last_ip, last_active_at, trusted, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
                        on duplicate key update device_name = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(device_name) else device_name end,
                                                device_type = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(device_type) else device_type end,
                                                os = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(os) else os end,
                                                browser = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(browser) else browser end,
                                                last_ip = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(last_ip) else last_ip end,
                                                last_active_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(last_active_at) else last_active_at end,
                                                updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then current_timestamp else updated_at end,
                                                deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) then 0 else deleted end
                        """,
                userId,
                userUuid,
                deviceId,
                deviceInfo.deviceName(),
                deviceInfo.deviceType(),
                deviceInfo.os(),
                deviceInfo.browser(),
                ip,
                now
        );
        requireIamUserWrite(deviceUpserted);
    }

    private String temporaryDeviceId(String ip, String userAgent) {
        return "tmp_" + sha256Hex(defaultString(userAgent) + "|" + defaultString(ip)).substring(0, 32);
    }

    private DeviceInfo parseDeviceInfo(String userAgent) {
        String ua = defaultString(userAgent);
        String lower = ua.toLowerCase(Locale.ROOT);
        String os;
        if (lower.contains("windows")) {
            os = "Windows";
        } else if (lower.contains("mac os") || lower.contains("macintosh")) {
            os = "macOS";
        } else if (lower.contains("android")) {
            os = "Android";
        } else if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ios")) {
            os = "iOS";
        } else if (lower.contains("linux")) {
            os = "Linux";
        } else {
            os = "Unknown";
        }
        String browser;
        if (lower.contains("edg/")) {
            browser = "Edge";
        } else if (lower.contains("chrome/") || lower.contains("crios/")) {
            browser = "Chrome";
        } else if (lower.contains("safari/")) {
            browser = "Safari";
        } else if (lower.contains("firefox/")) {
            browser = "Firefox";
        } else {
            browser = "Unknown";
        }
        String deviceType = lower.contains("mobile") || lower.contains("iphone") || lower.contains("android") ? "MOBILE" : "DESKTOP";
        return new DeviceInfo(os + " " + browser, deviceType, os, browser);
    }

    private void upsertProfile(SysUserEntity user) {
        String userUuid = requireUserUuid(user);
        int profileUpserted = jdbcTemplate.update(
                """
                        insert into iam_user_profile (
                            user_id, user_uuid, nickname, real_name, gender, birth_month, region, locale, timezone, extra_json, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, 'zh-CN', 'Asia/Shanghai', json_object('availableTime', ?, 'idCardBound', ?), ?)
                        on duplicate key update nickname = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(nickname) else nickname end,
                                                real_name = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(real_name) else real_name end,
                                                gender = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(gender) else gender end,
                                                birth_month = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(birth_month) else birth_month end,
                                                region = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(region) else region end,
                                                extra_json = case when user_id = values(user_id) and user_uuid = values(user_uuid) then json_merge_patch(coalesce(extra_json, json_object()), values(extra_json)) else extra_json end,
                                                deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(deleted) else deleted end,
                                                updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then current_timestamp else updated_at end
                        """,
                user.getId(),
                userUuid,
                user.getNickname(),
                user.getRealName(),
                user.getGender(),
                user.getBirthMonth(),
                user.getRegion(),
                user.getAvailableTime(),
                StringUtils.hasText(user.getIdCardNumber()),
                deletedFlag(user.getDeleted())
        );
        requireIamUserWrite(profileUpserted);
    }

    private void upsertSecuritySetting(Long userId, String userUuid, int deleted) {
        int securitySettingUpserted = jdbcTemplate.update(
                """
                        insert into iam_user_security_setting (user_id, user_uuid, deleted)
                        values (?, ?, ?)
                        on duplicate key update updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then current_timestamp else updated_at end,
                                                deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(deleted) else deleted end
                        """,
                userId,
                userUuid,
                deleted
        );
        requireIamUserWrite(securitySettingUpserted);
    }

    private void recordEvent(Long userId, String eventType, String eventSource, Long operatorId, String operatorUuid, String ip, String userAgent, String detailJson) {
        String userUuid = userId == null ? null : requireUserUuid(userId);
        String trustedOperatorUuid = requireOperatorUuid(operatorId, operatorUuid);
        int eventInserted = jdbcTemplate.update(
                """
                        insert into iam_user_event (user_id, user_uuid, event_type, event_source, operator_id, operator_uuid, ip, user_agent, detail_json)
                        values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as json))
                        """,
                userId,
                userUuid,
                eventType,
                eventSource,
                operatorId,
                trustedOperatorUuid,
                ip,
                userAgent,
                StringUtils.hasText(detailJson) ? detailJson : "{}"
        );
        requireIamUserWrite(eventInserted);
    }

    private IamUserAccount queryIamAccountById(Long userId) {
        List<IamUserAccount> accounts = jdbcTemplate.query(
                """
                        select id as userId, user_no as userNo, display_name as displayName, avatar_url as avatarUrl,
                               status, user_type as userType, source, registered_at as registeredAt, last_login_at as lastLoginAt
                        from iam_user
                        where id = ? and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(IamUserAccount.class),
                userId
        );
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    private List<IamUserAccount.IdentityView> queryIdentities(Long userId, String userUuid) {
        return jdbcTemplate.query(
                """
                        select id, user_id as userId, user_uuid as userUuid, identity_type as identityType, identifier, identifier_normalized as identifierNormalized,
                               verified, primary_identity as primaryIdentity, status
                        from iam_user_identity
                        where user_id = ? and user_uuid = ? and deleted = 0
                        order by primary_identity desc, id asc
                        """,
                (rs, rowNum) -> {
                    IamUserAccount.IdentityView identity = new IamUserAccount.IdentityView();
                    identity.setId(rs.getLong("id"));
                    identity.setUserId(rs.getLong("userId"));
                    identity.setUserUuid(rs.getString("userUuid"));
                    identity.setIdentityType(rs.getString("identityType"));
                    identity.setIdentifier(rs.getString("identifier"));
                    identity.setIdentifierNormalized(rs.getString("identifierNormalized"));
                    identity.setVerified(rs.getInt("verified") == 1);
                    identity.setPrimaryIdentity(rs.getInt("primaryIdentity") == 1);
                    identity.setStatus(rs.getString("status"));
                    return identity;
                },
                userId,
                userUuid
        );
    }

    private IdentityBinding queryIdentityBinding(String identityType, String identifierNormalized) {
        List<IdentityBinding> bindings = jdbcTemplate.query(
                """
                        select id, user_id, user_uuid, deleted
                        from iam_user_identity
                        where identity_type = ? and identifier_normalized = ?
                        limit 1
                        """,
                (rs, rowNum) -> new IdentityBinding(rs.getLong("id"), rs.getLong("user_id"), rs.getString("user_uuid"), rs.getInt("deleted")),
                identityType,
                identifierNormalized
        );
        return bindings.isEmpty() ? null : bindings.get(0);
    }

    private SysUserEntity querySysUserById(Long userId) {
        List<SysUserEntity> users = jdbcTemplate.query(
                """
                        select id, uuid, username, nickname, real_name as realName, avatar_url as avatarUrl, birth_month as birthMonth,
                               gender, region, available_time as availableTime, id_card_number as idCardNumber,
                               password_hash as passwordHash, mobile, email, status, deleted
                        from sys_user
                        where id = ? and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(SysUserEntity.class),
                userId
        );
        return users.isEmpty() ? null : users.get(0);
    }

    private String requireUserUuid(Long userId) {
        SysUserEntity user = querySysUserById(userId);
        return requireUserUuid(user);
    }

    private String requireUserUuid(SysUserEntity user) {
        if (user == null || user.getId() == null || !StringUtils.hasText(user.getUuid())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "用户身份缺少可信 UUID", "用户身份缺少可信 UUID");
        }
        return user.getUuid().trim();
    }

    private String requireMatchingUserUuid(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user uuid is required");
        }
        String resolvedUserUuid = requireUserUuid(userId);
        if (!resolvedUserUuid.equals(userUuid.trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity mismatch");
        }
        return resolvedUserUuid;
    }

    private String requireOperatorUuid(Long operatorId, String operatorUuid) {
        if (operatorId == null) {
            return null;
        }
        if (!StringUtils.hasText(operatorUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted operator uuid is required");
        }
        return operatorUuid.trim();
    }

    private void requireIamUserWrite(int updated) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "IAM user changed, please retry");
        }
    }

    private Optional<SysUserEntity> findLegacySysUser(String account) {
        String normalizedAccount = normalizeLoginAccount(account);
        List<SysUserEntity> users = jdbcTemplate.query(
                """
                        select id, uuid, username, nickname, real_name as realName, avatar_url as avatarUrl, birth_month as birthMonth,
                               gender, region, available_time as availableTime, id_card_number as idCardNumber,
                               password_hash as passwordHash, mobile, email, status, deleted
                        from sys_user
                        where deleted = 0
                          and (username = ? or mobile = ? or email = ?)
                        order by id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(SysUserEntity.class),
                account.trim(),
                normalizeIdentifier(IDENTITY_MOBILE, account),
                normalizedAccount
        );
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    private List<String> candidateLoginIdentityTypes(String account) {
        String detected = detectIdentityType(account);
        if (IDENTITY_MOBILE.equals(detected)) {
            return List.of(IDENTITY_MOBILE, IDENTITY_USERNAME);
        }
        if (IDENTITY_EMAIL.equals(detected)) {
            return List.of(IDENTITY_EMAIL, IDENTITY_USERNAME);
        }
        return List.of(IDENTITY_USERNAME, IDENTITY_MOBILE, IDENTITY_EMAIL);
    }

    private String normalizeIdentityType(String identityType) {
        return StringUtils.hasText(identityType) ? identityType.trim().toUpperCase(Locale.ROOT) : null;
    }

    private boolean isMobile(String account) {
        String normalized = normalizeIdentifier(IDENTITY_MOBILE, account);
        return normalized != null && normalized.matches("^1[3-9]\\d{9}$");
    }

    private boolean isEmail(String account) {
        return account != null && account.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private String userNo(Long userId) {
        return "U" + String.format("%012d", userId);
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "ENABLED";
    }

    private String syncStatus(String status, int deleted) {
        if (deleted == 1) {
            return "DISABLED";
        }
        return defaultStatus(status);
    }

    private String defaultSource(String source) {
        return StringUtils.hasText(source) ? source.trim().toUpperCase(Locale.ROOT) : "SYSTEM";
    }

    private int deletedFlag(Integer deleted) {
        return deleted == null ? 0 : deleted;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private record IdentityBinding(Long id, Long userId, String userUuid, int deleted) {
    }

    private record DeviceInfo(String deviceName, String deviceType, String os, String browser) {
    }
}
