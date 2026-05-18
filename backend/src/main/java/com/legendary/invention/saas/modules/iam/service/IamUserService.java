package com.legendary.invention.saas.modules.iam.service;

import com.legendary.invention.saas.modules.user.entity.SysUserEntity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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

    private final JdbcTemplate jdbcTemplate;

    public IamUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SysUserEntity> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(querySysUserById(userId));
    }

    public Optional<SysUserEntity> findByIdentity(String identityType, String identifier) {
        String normalizedType = normalizeIdentityType(identityType);
        String normalizedIdentifier = normalizeIdentifier(normalizedType, identifier);
        if (!StringUtils.hasText(normalizedType) || !StringUtils.hasText(normalizedIdentifier)) {
            return Optional.empty();
        }
        Long userId = queryIamUserIdByIdentity(normalizedType, normalizedIdentifier);
        return userId == null ? Optional.empty() : findByUserId(userId);
    }

    public Optional<SysUserEntity> findByLoginAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return Optional.empty();
        }
        List<String> identityTypes = candidateLoginIdentityTypes(account);
        for (String identityType : identityTypes) {
            Optional<SysUserEntity> user = findByIdentity(identityType, account);
            if (user.isPresent()) {
                return user;
            }
        }
        Optional<SysUserEntity> legacy = findLegacySysUser(account);
        legacy.ifPresent(user -> syncSysUser(user, "LEGACY_LOGIN_FALLBACK"));
        return legacy;
    }

    @Transactional
    public void createUserWithIdentity(SysUserEntity user, String rawAccount, String source) {
        syncSysUser(user, defaultSource(source));
        String identityType = detectIdentityType(rawAccount);
        if (StringUtils.hasText(identityType)) {
            bindIdentity(user.getId(), identityType, rawAccount, true, true);
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
        jdbcTemplate.update(
                """
                        insert into iam_user_identity (
                            user_id, identity_type, identifier, identifier_normalized, verified, primary_identity, status, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'ENABLED', 0)
                        on duplicate key update user_id = values(user_id),
                                                identifier = values(identifier),
                                                verified = greatest(verified, values(verified)),
                                                primary_identity = greatest(primary_identity, values(primary_identity)),
                                                status = 'ENABLED',
                                                deleted = 0,
                                                updated_at = current_timestamp
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
                defaultStatus(user.getStatus()),
                defaultSource(source),
                deletedFlag(user.getDeleted())
        );
        bindIdentity(user.getId(), IDENTITY_USERNAME, user.getUsername(), true, true);
        bindIdentity(user.getId(), IDENTITY_MOBILE, user.getMobile(), true, false);
        bindIdentity(user.getId(), IDENTITY_EMAIL, user.getEmail(), true, false);
        upsertPasswordCredential(user.getId(), user.getPasswordHash());
        upsertProfile(user);
        upsertSecuritySetting(user.getId());
    }

    @Transactional
    public void upsertPasswordCredential(Long userId, String passwordHash) {
        if (userId == null || !StringUtils.hasText(passwordHash)) {
            return;
        }
        jdbcTemplate.update(
                """
                        insert into iam_user_credential (
                            user_id, credential_type, credential_secret, algorithm, version, last_changed_at, status, deleted
                        ) values (?, 'PASSWORD', ?, 'BCRYPT', 1, current_timestamp, 'ENABLED', 0)
                        on duplicate key update credential_secret = values(credential_secret),
                                                algorithm = values(algorithm),
                                                last_changed_at = current_timestamp,
                                                status = 'ENABLED',
                                                deleted = 0,
                                                updated_at = current_timestamp
                        """,
                userId,
                passwordHash
        );
    }

    @Transactional
    public void recordLoginSuccess(Long userId, String identityType, String account, String ip, String userAgent) {
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
                                                extra_json = values(extra_json),
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

    private void upsertSecuritySetting(Long userId) {
        jdbcTemplate.update(
                """
                        insert into iam_user_security_setting (user_id)
                        values (?)
                        on duplicate key update updated_at = current_timestamp, deleted = 0
                        """,
                userId
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
}
