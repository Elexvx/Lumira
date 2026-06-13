package com.lumira.saas.modules.iam.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import org.springframework.dao.EmptyResultDataAccessException;
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
        IamUserAccount account = queryIamAccountById(userId);
        if (account == null) {
            return Optional.empty();
        }
        account.setIdentities(queryIdentities(userId));
        account.setDevices(listRecentDevices(userId, 5));
        account.setSecuritySetting(findSecuritySetting(userId).orElse(null));
        account.setPasswordCredential(findActiveCredential(userId, "PASSWORD").orElse(null));
        account.setLegacyUser(querySysUserById(userId));
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
        Long userId = queryIamUserIdByIdentity(normalizedType, normalizedIdentifier);
        return userId == null ? Optional.empty() : findAccountByUserId(userId);
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
                bindIdentity(user.getId(), identityType, rawAccount, true, true);
            } else {
                syncIdentityFromSysUser(user.getId(), identityType, rawAccount, true, true, status, deleted);
            }
        }
    }

    @Transactional
    public void bindIdentity(Long userId, String identityType, String identifier) {
        bindIdentity(userId, identityType, identifier, false, false);
    }

    @Transactional
    public void bindIdentity(Long userId, String identityType, String identifier, boolean verified, boolean primaryIdentity) {
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        if (userId == null || !StringUtils.hasText(normalizedType) || !StringUtils.hasText(normalizedIdentifier)) {
            return;
        }
        IdentityBinding existing = queryIdentityBinding(normalizedType, normalizedIdentifier);
        if (existing != null && !userId.equals(existing.userId()) && existing.deleted() == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已被其他用户绑定", "登录身份已被其他用户绑定");
        }
        if (existing != null) {
            jdbcTemplate.update(
                    """
                            update iam_user_identity
                            set user_id = ?,
                                identifier = ?,
                                verified = greatest(verified, ?),
                                primary_identity = greatest(primary_identity, ?),
                                status = 'ENABLED',
                                deleted = 0,
                                updated_at = current_timestamp
                            where id = ?
                            """,
                    userId,
                    identifier.trim(),
                    verified ? 1 : 0,
                    primaryIdentity ? 1 : 0,
                    existing.id()
            );
            return;
        }
        jdbcTemplate.update(
                """
                        insert into iam_user_identity (
                            user_id, identity_type, identifier, identifier_normalized, verified, primary_identity, status, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'ENABLED', 0)
                        """,
                userId,
                normalizedType,
                identifier.trim(),
                normalizedIdentifier,
                verified ? 1 : 0,
                primaryIdentity ? 1 : 0
        );
    }

    @Transactional
    public void transferIdentity(Long targetUserId, String identityType, String identifier, Long operatorId, String reason, String ip, String userAgent) {
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        if (targetUserId == null || !StringUtils.hasText(normalizedType) || !StringUtils.hasText(normalizedIdentifier)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "身份转移参数不完整");
        }
        IdentityBinding existing = queryIdentityBinding(normalizedType, normalizedIdentifier);
        Long previousUserId = existing == null ? null : existing.userId();
        if (existing == null) {
            bindIdentity(targetUserId, normalizedType, identifier, true, false);
        } else if (!targetUserId.equals(existing.userId())) {
            jdbcTemplate.update(
                    """
                            update iam_user_identity
                            set user_id = ?, identifier = ?, verified = 1, status = 'ENABLED', deleted = 0, updated_at = current_timestamp
                            where id = ?
                            """,
                    targetUserId,
                    identifier.trim(),
                    existing.id()
            );
        }
        recordEvent(
                targetUserId,
                "IDENTITY_TRANSFERRED",
                "IAM",
                operatorId,
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
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        if (userId == null || !StringUtils.hasText(normalizedType) || !StringUtils.hasText(normalizedIdentifier)) {
            return;
        }
        jdbcTemplate.update(
                """
                        update iam_user_identity
                        set status = 'DISABLED', deleted = 1, updated_at = current_timestamp
                        where user_id = ? and identity_type = ? and identifier_normalized = ? and deleted = 0
                        """,
                userId,
                normalizedType,
                normalizedIdentifier
        );
    }

    @Transactional
    public void changeUserStatus(Long userId, String status) {
        if (userId == null || !StringUtils.hasText(status)) {
            return;
        }
        jdbcTemplate.update(
                "update iam_user set status = ?, updated_at = current_timestamp where id = ? and deleted = 0",
                status,
                userId
        );
        jdbcTemplate.update(
                "update iam_user_identity set status = ?, updated_at = current_timestamp where user_id = ? and deleted = 0",
                status,
                userId
        );
        jdbcTemplate.update(
                "update iam_user_credential set status = ?, updated_at = current_timestamp where user_id = ? and deleted = 0",
                status,
                userId
        );
    }

    @Transactional
    public void softDeleteUser(Long userId) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update(
                "update iam_user set status = 'DISABLED', deleted = 1, updated_at = current_timestamp where id = ? and deleted = 0",
                userId
        );
        jdbcTemplate.update(
                "update iam_user_identity set status = 'DISABLED', deleted = 1, updated_at = current_timestamp where user_id = ? and deleted = 0",
                userId
        );
        jdbcTemplate.update(
                "update iam_user_credential set status = 'DISABLED', deleted = 1, updated_at = current_timestamp where user_id = ? and deleted = 0",
                userId
        );
        jdbcTemplate.update(
                "update iam_user_device set deleted = 1, updated_at = current_timestamp where user_id = ? and deleted = 0",
                userId
        );
        jdbcTemplate.update(
                "update iam_user_security_setting set deleted = 1, updated_at = current_timestamp where user_id = ? and deleted = 0",
                userId
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
        int deleted = deletedFlag(user.getDeleted());
        String status = syncStatus(user.getStatus(), deleted);
        jdbcTemplate.update(
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
        syncIdentityFromSysUser(user.getId(), IDENTITY_USERNAME, user.getUsername(), true, true, status, deleted);
        bindLegacyIdentityIfAvailable(user.getId(), IDENTITY_MOBILE, user.getMobile(), status, deleted);
        bindLegacyIdentityIfAvailable(user.getId(), IDENTITY_EMAIL, user.getEmail(), status, deleted);
        upsertPasswordCredentialFromSysUser(user.getId(), user.getPasswordHash(), status, deleted);
        upsertProfile(user);
        upsertSecuritySetting(user.getId(), deleted);
    }

    private void bindLegacyIdentityIfAvailable(Long userId, String identityType, String identifier, String status, int deleted) {
        try {
            syncIdentityFromSysUser(userId, identityType, identifier, true, false, status, deleted);
        } catch (BizException exception) {
            recordEvent(
                    userId,
                    "IDENTITY_SYNC_CONFLICT",
                    "IAM",
                    userId,
                    null,
                    null,
                    "{\"identityType\":\"" + jsonEscape(identityType)
                            + "\",\"identifierNormalized\":\"" + jsonEscape(normalizeIdentifier(identityType, identifier))
                            + "\"}"
            );
        }
    }

    private void syncIdentityFromSysUser(Long userId, String identityType, String identifier, boolean verified, boolean primaryIdentity, String status, int deleted) {
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        if (userId == null || !StringUtils.hasText(normalizedType) || !StringUtils.hasText(normalizedIdentifier)) {
            return;
        }
        IdentityBinding existing = queryIdentityBinding(normalizedType, normalizedIdentifier);
        if (existing != null && !userId.equals(existing.userId()) && existing.deleted() == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已被其他用户绑定", "登录身份已被其他用户绑定");
        }
        if (existing != null) {
            jdbcTemplate.update(
                    """
                            update iam_user_identity
                            set user_id = ?,
                                identifier = ?,
                                verified = greatest(verified, ?),
                                primary_identity = greatest(primary_identity, ?),
                                status = ?,
                                deleted = ?,
                                updated_at = current_timestamp
                            where id = ?
                            """,
                    userId,
                    identifier.trim(),
                    verified ? 1 : 0,
                    primaryIdentity ? 1 : 0,
                    status,
                    deleted,
                    existing.id()
            );
            return;
        }
        jdbcTemplate.update(
                """
                        insert into iam_user_identity (
                            user_id, identity_type, identifier, identifier_normalized, verified, primary_identity, status, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                normalizedType,
                identifier.trim(),
                normalizedIdentifier,
                verified ? 1 : 0,
                primaryIdentity ? 1 : 0,
                status,
                deleted
        );
    }

    @Transactional
    public void upsertPasswordCredential(Long userId, String passwordHash) {
        upsertPasswordCredential(userId, passwordHash, "ENABLED", 0);
    }

    private void upsertPasswordCredentialFromSysUser(Long userId, String passwordHash, String status, int deleted) {
        upsertPasswordCredential(userId, passwordHash, status, deleted);
    }

    private void upsertPasswordCredential(Long userId, String passwordHash, String status, int deleted) {
        if (userId == null || !StringUtils.hasText(passwordHash)) {
            return;
        }
        jdbcTemplate.update(
                """
                        insert into iam_user_credential (
                            user_id, credential_type, credential_secret, algorithm, version, last_changed_at, status, deleted
                        ) values (?, 'PASSWORD', ?, 'BCRYPT', 1, current_timestamp, ?, ?)
                        on duplicate key update credential_secret = values(credential_secret),
                                                algorithm = values(algorithm),
                                                last_changed_at = current_timestamp,
                                                status = values(status),
                                                deleted = values(deleted),
                                                updated_at = current_timestamp
                        """,
                userId,
                passwordHash,
                status,
                deleted
        );
    }

    public Optional<IamUserAccount.CredentialView> findActiveCredential(Long userId, String credentialType) {
        if (userId == null || !StringUtils.hasText(credentialType)) {
            return Optional.empty();
        }
        List<IamUserAccount.CredentialView> credentials = jdbcTemplate.query(
                """
                        select id, user_id as userId, credential_type as credentialType, credential_secret as credentialSecret,
                               algorithm, version, expire_at as expireAt, last_changed_at as lastChangedAt, status
                        from iam_user_credential
                        where user_id = ?
                          and credential_type = ?
                          and status = 'ENABLED'
                          and deleted = 0
                          and (expire_at is null or expire_at > current_timestamp)
                        order by version desc, id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(IamUserAccount.CredentialView.class),
                userId,
                credentialType.trim().toUpperCase(Locale.ROOT)
        );
        return credentials.isEmpty() ? Optional.empty() : Optional.of(credentials.get(0));
    }

    public List<IamUserAccount.IdentityView> listIdentities(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return queryIdentities(userId);
    }

    public List<IamUserAccount.DeviceView> listRecentDevices(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        int safeLimit = limit <= 0 ? 5 : Math.min(limit, 20);
        return jdbcTemplate.query(
                """
                        select id, user_id as userId, device_id as deviceId, device_name as deviceName,
                               device_type as deviceType, os, browser, last_ip as lastIp,
                               last_active_at as lastActiveAt, trusted
                        from iam_user_device
                        where user_id = ? and deleted = 0
                        order by last_active_at desc, id desc
                        limit ?
                        """,
                (rs, rowNum) -> {
                    IamUserAccount.DeviceView device = new IamUserAccount.DeviceView();
                    device.setId(rs.getLong("id"));
                    device.setUserId(rs.getLong("userId"));
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
                safeLimit
        );
    }

    public Optional<IamUserAccount.SecuritySettingView> findSecuritySetting(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        List<IamUserAccount.SecuritySettingView> settings = jdbcTemplate.query(
                """
                        select user_id as userId, mfa_enabled as mfaEnabled,
                               password_login_enabled as passwordLoginEnabled,
                               sms_login_enabled as smsLoginEnabled,
                               email_login_enabled as emailLoginEnabled,
                               passkey_enabled as passkeyEnabled,
                               login_notify_enabled as loginNotifyEnabled
                        from iam_user_security_setting
                        where user_id = ? and deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> {
                    IamUserAccount.SecuritySettingView setting = new IamUserAccount.SecuritySettingView();
                    setting.setUserId(rs.getLong("userId"));
                    setting.setMfaEnabled(rs.getInt("mfaEnabled") == 1);
                    setting.setPasswordLoginEnabled(rs.getInt("passwordLoginEnabled") == 1);
                    setting.setSmsLoginEnabled(rs.getInt("smsLoginEnabled") == 1);
                    setting.setEmailLoginEnabled(rs.getInt("emailLoginEnabled") == 1);
                    setting.setPasskeyEnabled(rs.getInt("passkeyEnabled") == 1);
                    setting.setLoginNotifyEnabled(rs.getInt("loginNotifyEnabled") == 1);
                    return setting;
                },
                userId
        );
        return settings.isEmpty() ? Optional.empty() : Optional.of(settings.get(0));
    }

    @Transactional
    public void recordLoginSuccess(Long userId, String identityType, String account, String ip, String userAgent) {
        recordLoginSuccess(userId, identityType, account, ip, userAgent, null);
    }

    @Transactional
    public void recordLoginSuccess(Long userId, String identityType, String account, String ip, String userAgent, String deviceId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update iam_user set last_login_at = ?, updated_at = ? where id = ? and deleted = 0", now, now, userId);
        if (StringUtils.hasText(identityType) && StringUtils.hasText(account)) {
            jdbcTemplate.update(
                    """
                            update iam_user_identity
                            set last_used_at = ?, updated_at = ?
                            where user_id = ? and identity_type = ? and identifier_normalized = ? and deleted = 0
                            """,
                    now,
                    now,
                    userId,
                    normalizeIdentityType(identityType),
                    normalizeIdentifier(identityType, account)
            );
        }
        upsertLoginDevice(userId, deviceId, ip, userAgent, now);
        recordEvent(userId, "USER_LOGIN_SUCCESS", "AUTH", userId, ip, userAgent, "{\"result\":\"SUCCESS\"}");
    }

    public void recordLoginFailure(Long userId, String eventType, String account, String ip, String userAgent) {
        recordEvent(userId, eventType, "AUTH", userId, ip, userAgent, "{\"account\":\"" + jsonEscape(account) + "\"}");
    }

    public void recordUserRegistered(Long userId, String source, String ip, String userAgent) {
        recordEvent(userId, "USER_REGISTERED", defaultSource(source), userId, ip, userAgent, "{\"source\":\"" + jsonEscape(defaultSource(source)) + "\"}");
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

    private void upsertLoginDevice(Long userId, String rawDeviceId, String ip, String userAgent, LocalDateTime now) {
        if (userId == null) {
            return;
        }
        String deviceId = StringUtils.hasText(rawDeviceId) ? rawDeviceId.trim() : temporaryDeviceId(ip, userAgent);
        DeviceInfo deviceInfo = parseDeviceInfo(userAgent);
        jdbcTemplate.update(
                """
                        insert into iam_user_device (
                            user_id, device_id, device_name, device_type, os, browser, last_ip, last_active_at, trusted, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
                        on duplicate key update device_name = values(device_name),
                                                device_type = values(device_type),
                                                os = values(os),
                                                browser = values(browser),
                                                last_ip = values(last_ip),
                                                last_active_at = values(last_active_at),
                                                updated_at = current_timestamp,
                                                deleted = 0
                        """,
                userId,
                deviceId,
                deviceInfo.deviceName(),
                deviceInfo.deviceType(),
                deviceInfo.os(),
                deviceInfo.browser(),
                ip,
                now
        );
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
        jdbcTemplate.update(
                """
                        insert into iam_user_profile (
                            user_id, nickname, real_name, gender, birth_month, region, locale, timezone, extra_json, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'zh-CN', 'Asia/Shanghai', json_object('availableTime', ?, 'idCardBound', ?), ?)
                        on duplicate key update nickname = values(nickname),
                                                real_name = values(real_name),
                                                gender = values(gender),
                                                birth_month = values(birth_month),
                                                region = values(region),
                                                extra_json = json_merge_patch(coalesce(extra_json, json_object()), values(extra_json)),
                                                deleted = values(deleted),
                                                updated_at = current_timestamp
                        """,
                user.getId(),
                user.getNickname(),
                user.getRealName(),
                user.getGender(),
                user.getBirthMonth(),
                user.getRegion(),
                user.getAvailableTime(),
                StringUtils.hasText(user.getIdCardNumber()),
                deletedFlag(user.getDeleted())
        );
    }

    private void upsertSecuritySetting(Long userId, int deleted) {
        jdbcTemplate.update(
                """
                        insert into iam_user_security_setting (user_id, deleted)
                        values (?, ?)
                        on duplicate key update updated_at = current_timestamp, deleted = values(deleted)
                        """,
                userId,
                deleted
        );
    }

    private void recordEvent(Long userId, String eventType, String eventSource, Long operatorId, String ip, String userAgent, String detailJson) {
        jdbcTemplate.update(
                """
                        insert into iam_user_event (user_id, event_type, event_source, operator_id, ip, user_agent, detail_json)
                        values (?, ?, ?, ?, ?, ?, cast(? as json))
                        """,
                userId,
                eventType,
                eventSource,
                operatorId,
                ip,
                userAgent,
                StringUtils.hasText(detailJson) ? detailJson : "{}"
        );
    }

    private Long queryIamUserIdByIdentity(String identityType, String identifierNormalized) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select user_id
                            from iam_user_identity
                            where identity_type = ? and identifier_normalized = ? and status = 'ENABLED' and deleted = 0
                            limit 1
                            """,
                    Long.class,
                    identityType,
                    identifierNormalized
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
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

    private List<IamUserAccount.IdentityView> queryIdentities(Long userId) {
        return jdbcTemplate.query(
                """
                        select id, user_id as userId, identity_type as identityType, identifier, identifier_normalized as identifierNormalized,
                               verified, primary_identity as primaryIdentity, status
                        from iam_user_identity
                        where user_id = ? and deleted = 0
                        order by primary_identity desc, id asc
                        """,
                (rs, rowNum) -> {
                    IamUserAccount.IdentityView identity = new IamUserAccount.IdentityView();
                    identity.setId(rs.getLong("id"));
                    identity.setUserId(rs.getLong("userId"));
                    identity.setIdentityType(rs.getString("identityType"));
                    identity.setIdentifier(rs.getString("identifier"));
                    identity.setIdentifierNormalized(rs.getString("identifierNormalized"));
                    identity.setVerified(rs.getInt("verified") == 1);
                    identity.setPrimaryIdentity(rs.getInt("primaryIdentity") == 1);
                    identity.setStatus(rs.getString("status"));
                    return identity;
                },
                userId
        );
    }

    private IdentityBinding queryIdentityBinding(String identityType, String identifierNormalized) {
        List<IdentityBinding> bindings = jdbcTemplate.query(
                """
                        select id, user_id
                             , deleted
                        from iam_user_identity
                        where identity_type = ? and identifier_normalized = ?
                        limit 1
                        """,
                (rs, rowNum) -> new IdentityBinding(rs.getLong("id"), rs.getLong("user_id"), rs.getInt("deleted")),
                identityType,
                identifierNormalized
        );
        return bindings.isEmpty() ? null : bindings.get(0);
    }

    private SysUserEntity querySysUserById(Long userId) {
        List<SysUserEntity> users = jdbcTemplate.query(
                """
                        select id, username, nickname, real_name as realName, avatar_url as avatarUrl, birth_month as birthMonth,
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

    private Optional<SysUserEntity> findLegacySysUser(String account) {
        String normalizedAccount = normalizeLoginAccount(account);
        List<SysUserEntity> users = jdbcTemplate.query(
                """
                        select id, username, nickname, real_name as realName, avatar_url as avatarUrl, birth_month as birthMonth,
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

    private record IdentityBinding(Long id, Long userId, int deleted) {
    }

    private record DeviceInfo(String deviceName, String deviceType, String os, String browser) {
    }
}
