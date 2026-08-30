package com.lumira.saas.modules.iam.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.iam.infrastructure.IamUserPersistenceAdapters;
import com.lumira.saas.modules.iam.repository.IamUserRepository;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final IamUserRepository iamUserRepository;

    @Autowired
    public IamUserService(IamUserRepository iamUserRepository) {
        this.iamUserRepository = iamUserRepository;
    }

    /** Compatibility constructor for legacy unit fixtures; production injects the typed repository. */
    public IamUserService(Object persistence) {
        this(IamUserPersistenceAdapters.from(persistence));
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
        IamUserRepository.IdentityBinding binding = queryIdentityBinding(normalizedType, normalizedIdentifier);
        if (binding == null || binding.deleted() != 0 || !binding.verified() || !StringUtils.hasText(binding.userUuid())) {
            return Optional.empty();
        }
        return findAccountByUserId(binding.userId())
                .filter(account -> binding.userUuid().equals(account.getUserUuid()));
    }

    public boolean isIdentityReserved(String identityType, String identifier) {
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        return StringUtils.hasText(normalizedType)
                && StringUtils.hasText(normalizedIdentifier)
                && queryIdentityBinding(normalizedType, normalizedIdentifier) != null;
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
        for (String identityType : identityTypes) {
            String normalizedIdentifier = normalizeIdentifier(identityType, account);
            IamUserRepository.IdentityBinding binding = queryIdentityBinding(identityType, normalizedIdentifier);
            if (binding != null && binding.deleted() == 0 && !binding.verified()) {
                return Optional.empty();
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
        IamUserRepository.IdentityBinding existing = queryIdentityBinding(normalizedType, normalizedIdentifier);
        if (existing != null && (!userId.equals(existing.userId()) || !trustedUserUuid.equals(existing.userUuid())) && existing.deleted() == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已被其他用户绑定", "登录身份已被其他用户绑定");
        }
        if (existing != null) {
            int updated = iamUserRepository.reviveIdentityForBinding(existing, identityCommand(
                    userId, trustedUserUuid, normalizedType, identifier, normalizedIdentifier,
                    verified, primaryIdentity, "ENABLED", 0
            ));
            if (updated <= 0) {
                throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已变更，请重试", "登录身份已变更，请重试");
            }
            return;
        }
        int inserted = iamUserRepository.insertIdentity(identityCommand(
                userId, trustedUserUuid, normalizedType, identifier, normalizedIdentifier,
                verified, primaryIdentity, "ENABLED", 0
        ));
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
        IamUserRepository.IdentityBinding existing = queryIdentityBinding(normalizedType, normalizedIdentifier);
        Long previousUserId = existing == null ? null : existing.userId();
        if (existing == null) {
            bindIdentity(targetUserId, trustedTargetUserUuid, normalizedType, identifier, true, false);
        } else if (!targetUserId.equals(existing.userId())) {
            int updated = iamUserRepository.transferIdentity(existing, identityCommand(
                    targetUserId, trustedTargetUserUuid, normalizedType, identifier, normalizedIdentifier,
                    true, false, "ENABLED", 0
            ));
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
        int identityUpdated = iamUserRepository.deactivateIdentity(
                userId, trustedUserUuid, normalizedType, normalizedIdentifier
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
        int userUpdated = iamUserRepository.updateAccountStatus(userId, trustedUserUuid, status);
        requireIamUserWrite(userUpdated);
        iamUserRepository.updateIdentityStatus(userId, trustedUserUuid, status);
        iamUserRepository.updateCredentialStatus(userId, trustedUserUuid, status);
    }

    @Transactional
    public void softDeleteUser(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return;
        }
        String trustedUserUuid = requireMatchingUserUuid(userId, userUuid);
        int userUpdated = iamUserRepository.softDeleteAccount(userId, trustedUserUuid);
        requireIamUserWrite(userUpdated);
        iamUserRepository.softDeleteAccountRelations(userId, trustedUserUuid);
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
        int userUpserted = iamUserRepository.upsertAccount(
                user, userNo(user.getId()), displayName, status, defaultSource(source), deleted
        );
        requireIamUserWrite(userUpserted);
        syncIdentityFromSysUser(user.getId(), userUuid, IDENTITY_USERNAME, user.getUsername(), true, true, status, deleted);
        bindLegacyIdentityIfAvailable(user.getId(), userUuid, IDENTITY_MOBILE, user.getMobile(), status, deleted);
        bindLegacyIdentityIfAvailable(user.getId(), userUuid, IDENTITY_EMAIL, user.getEmail(), status, deleted);
        upsertPasswordCredentialFromSysUser(user.getId(), userUuid, user.getPasswordHash(), status, deleted);
        upsertProfile(user);
        upsertSecuritySetting(user.getId(), userUuid, deleted);
    }

    @Transactional
    public void createRegisteredUser(
            SysUserEntity user,
            boolean mobileVerified,
            boolean emailVerified,
            String source
    ) {
        if (user == null || user.getId() == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "注册用户不能为空");
        }
        String displayName = firstText(user.getNickname(), user.getRealName(), user.getUsername(), "用户" + user.getId());
        String userUuid = requireUserUuid(user);
        String status = syncStatus(user.getStatus(), 0);
        requireIamUserWrite(iamUserRepository.upsertAccount(
                user, userNo(user.getId()), displayName, status, defaultSource(source), 0
        ));
        syncIdentityFromSysUser(user.getId(), userUuid, IDENTITY_USERNAME, user.getUsername(), true, true, status, 0);
        syncIdentityFromSysUser(user.getId(), userUuid, IDENTITY_MOBILE, user.getMobile(), mobileVerified, false, status, 0);
        syncIdentityFromSysUser(user.getId(), userUuid, IDENTITY_EMAIL, user.getEmail(), emailVerified, false, status, 0);
        upsertPasswordCredentialFromSysUser(user.getId(), userUuid, user.getPasswordHash(), status, 0);
        upsertProfile(user);
        upsertSecuritySetting(user.getId(), userUuid, 0);
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
        IamUserRepository.IdentityBinding existing = queryIdentityBinding(normalizedType, normalizedIdentifier);
        if (existing != null && (!userId.equals(existing.userId()) || !userUuid.equals(existing.userUuid())) && existing.deleted() == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已被其他用户绑定", "登录身份已被其他用户绑定");
        }
        if (existing != null) {
            int updated = iamUserRepository.synchronizeIdentity(existing, identityCommand(
                    userId, userUuid, normalizedType, identifier, normalizedIdentifier,
                    verified, primaryIdentity, status, deleted
            ));
            if (updated <= 0) {
                throw new BizException(ErrorCode.BIZ_ERROR, "登录身份已变更，请重试", "登录身份已变更，请重试");
            }
            return;
        }
        int inserted = iamUserRepository.insertIdentity(identityCommand(
                userId, userUuid, normalizedType, identifier, normalizedIdentifier,
                verified, primaryIdentity, status, deleted
        ));
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
        int credentialUpserted = iamUserRepository.upsertPasswordCredential(
                new IamUserRepository.PasswordCredential(
                        userId, userUuid, passwordHash, status, deleted, resolvePasswordChange
                )
        );
        requireIamUserWrite(credentialUpserted);
    }

    public boolean requiresPasswordChange(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return false;
        }
        requireMatchingUserUuid(userId, userUuid);
        return iamUserRepository.passwordChangeRequired(userId, userUuid.trim()) == 1;
    }

    public Optional<IamUserAccount.CredentialView> findActiveCredential(Long userId, String userUuid, String credentialType) {
        if (userId == null || !StringUtils.hasText(userUuid) || !StringUtils.hasText(credentialType)) {
            return Optional.empty();
        }
        requireMatchingUserUuid(userId, userUuid);
        List<IamUserAccount.CredentialView> credentials = iamUserRepository.findActiveCredentials(
                userId, userUuid.trim(), credentialType.trim().toUpperCase(Locale.ROOT)
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
        return iamUserRepository.findRecentActiveDevices(userId, userUuid.trim(), safeLimit);
    }

    public Optional<IamUserAccount.SecuritySettingView> findSecuritySetting(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return Optional.empty();
        }
        requireMatchingUserUuid(userId, userUuid);
        List<IamUserAccount.SecuritySettingView> settings = iamUserRepository.findActiveSecuritySettings(userId, userUuid.trim());
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
        IamUserRepository.LoginSuccess loginSuccess = new IamUserRepository.LoginSuccess(userId, trustedUserUuid, now);
        int userUpdated = iamUserRepository.updateLastLogin(loginSuccess);
        requireIamUserWrite(userUpdated);
        if (StringUtils.hasText(identityType) && StringUtils.hasText(account)) {
            iamUserRepository.markIdentityUsed(loginSuccess, normalizeIdentityType(identityType), normalizeIdentifier(identityType, account));
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
        int deviceUpserted = iamUserRepository.upsertDevice(new IamUserRepository.LoginDevice(
                userId, userUuid, deviceId, deviceInfo.deviceName(), deviceInfo.deviceType(),
                deviceInfo.os(), deviceInfo.browser(), ip, now
        ));
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
        int profileUpserted = iamUserRepository.upsertProfile(user, userUuid, deletedFlag(user.getDeleted()));
        requireIamUserWrite(profileUpserted);
    }

    private void upsertSecuritySetting(Long userId, String userUuid, int deleted) {
        int securitySettingUpserted = iamUserRepository.upsertSecuritySetting(userId, userUuid, deleted);
        requireIamUserWrite(securitySettingUpserted);
    }

    private void recordEvent(Long userId, String eventType, String eventSource, Long operatorId, String operatorUuid, String ip, String userAgent, String detailJson) {
        String userUuid = userId == null ? null : requireUserUuid(userId);
        String trustedOperatorUuid = requireOperatorUuid(operatorId, operatorUuid);
        int eventInserted = iamUserRepository.insertEvent(new IamUserRepository.IamEvent(
                userId, userUuid, eventType, eventSource, operatorId, trustedOperatorUuid,
                ip, userAgent, StringUtils.hasText(detailJson) ? detailJson : "{}"
        ));
        requireIamUserWrite(eventInserted);
    }

    private IamUserAccount queryIamAccountById(Long userId) {
        return iamUserRepository.findActiveAccountById(userId);
    }

    private List<IamUserAccount.IdentityView> queryIdentities(Long userId, String userUuid) {
        return iamUserRepository.findActiveIdentities(userId, userUuid);
    }

    private IamUserRepository.IdentityBinding queryIdentityBinding(String identityType, String identifierNormalized) {
        return iamUserRepository.findIdentityBinding(identityType, identifierNormalized);
    }

    private SysUserEntity querySysUserById(Long userId) {
        return iamUserRepository.findActiveSysUserById(userId);
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

    private IamUserRepository.IdentityCommand identityCommand(
            Long userId,
            String userUuid,
            String identityType,
            String identifier,
            String identifierNormalized,
            boolean verified,
            boolean primaryIdentity,
            String status,
            int deleted
    ) {
        return new IamUserRepository.IdentityCommand(
                userId,
                userUuid,
                identityType,
                identifier == null ? null : identifier.trim(),
                identifierNormalized,
                verified,
                primaryIdentity,
                status,
                deleted
        );
    }

    private Optional<SysUserEntity> findLegacySysUser(String account) {
        String normalizedAccount = normalizeLoginAccount(account);
        return Optional.ofNullable(iamUserRepository.findLegacyActiveSysUser(
                account.trim(), normalizeIdentifier(IDENTITY_MOBILE, account), normalizedAccount
        ));
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

    private record DeviceInfo(String deviceName, String deviceType, String os, String browser) {
    }
}
