package com.lumira.saas.modules.iam.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.iam.repository.IamUserRepository;
import com.lumira.saas.modules.iam.service.IamUserAccount;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** MyBatis/JDBC adapter for the IAM-user persistence boundary. */
@Repository
public class JdbcIamUserRepository implements IamUserRepository {
    private final MyBatisQueryOperations database;

    public JdbcIamUserRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public SysUserEntity findActiveSysUserById(Long userId) {
        List<SysUserEntity> users = database.query(
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
        return users.isEmpty() ? null : users.getFirst();
    }

    @Override
    public IamUserAccount findActiveAccountById(Long userId) {
        List<IamUserAccount> accounts = database.query(
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
        return accounts.isEmpty() ? null : accounts.getFirst();
    }

    @Override
    public List<IamUserAccount.IdentityView> findActiveIdentities(Long userId, String userUuid) {
        return database.query(
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

    @Override
    public IdentityBinding findIdentityBinding(String identityType, String identifierNormalized) {
        List<IdentityBinding> bindings = database.query(
                """
                        select id, user_id, user_uuid, verified, deleted
                        from iam_user_identity
                        where identity_type = ? and identifier_normalized = ?
                        limit 1
                        """,
                (rs, rowNum) -> new IdentityBinding(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("user_uuid"),
                        rs.getInt("verified") == 1,
                        rs.getInt("deleted")
                ),
                identityType,
                identifierNormalized
        );
        return bindings.isEmpty() ? null : bindings.getFirst();
    }

    @Override
    public List<IamUserAccount.CredentialView> findActiveCredentials(Long userId, String userUuid, String credentialType) {
        return database.query(
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
                userUuid,
                credentialType
        );
    }

    @Override
    public int passwordChangeRequired(Long userId, String userUuid) {
        Integer required = database.queryForObject(
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
                userUuid
        );
        return required == null ? 0 : required;
    }

    @Override
    public List<IamUserAccount.DeviceView> findRecentActiveDevices(Long userId, String userUuid, int limit) {
        return database.query(
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
                userUuid,
                limit
        );
    }

    @Override
    public List<IamUserAccount.SecuritySettingView> findActiveSecuritySettings(Long userId, String userUuid) {
        return database.query(
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
                userUuid
        );
    }

    @Override
    public SysUserEntity findLegacyActiveSysUser(String rawAccount, String normalizedMobile, String normalizedAccount) {
        List<SysUserEntity> users = database.query(
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
                rawAccount,
                normalizedMobile,
                normalizedAccount
        );
        return users.isEmpty() ? null : users.getFirst();
    }

    @Override
    public int reviveIdentityForBinding(IdentityBinding existing, IdentityCommand command) {
        return database.update(
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
                command.userId(), command.userUuid(), command.identifier(), command.verified() ? 1 : 0,
                command.primaryIdentity() ? 1 : 0, existing.id(), command.identityType(), command.identifierNormalized(),
                command.userId(), command.userUuid()
        );
    }

    @Override
    public int transferIdentity(IdentityBinding existing, IdentityCommand command) {
        return database.update(
                """
                        update iam_user_identity
                        set user_id = ?, user_uuid = ?, identifier = ?, verified = 1, status = 'ENABLED', deleted = 0, updated_at = current_timestamp
                        where id = ?
                          and identity_type = ?
                          and identifier_normalized = ?
                          and ((deleted = 1) or (user_id = ? and user_uuid = ?))
                        """,
                command.userId(), command.userUuid(), command.identifier(), existing.id(), command.identityType(),
                command.identifierNormalized(), existing.userId(), existing.userUuid()
        );
    }

    @Override
    public int synchronizeIdentity(IdentityBinding existing, IdentityCommand command) {
        return database.update(
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
                command.userId(), command.userUuid(), command.identifier(), command.verified() ? 1 : 0,
                command.primaryIdentity() ? 1 : 0, command.status(), command.deleted(), existing.id(),
                command.identityType(), command.identifierNormalized(), existing.userId(), existing.userUuid(), existing.deleted()
        );
    }

    @Override
    public int insertIdentity(IdentityCommand command) {
        return database.update(
                """
                        insert into iam_user_identity (
                            user_id, user_uuid, identity_type, identifier, identifier_normalized, verified, primary_identity, status, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                command.userId(), command.userUuid(), command.identityType(), command.identifier(), command.identifierNormalized(),
                command.verified() ? 1 : 0, command.primaryIdentity() ? 1 : 0, command.status(), command.deleted()
        );
    }

    @Override
    public int deactivateIdentity(Long userId, String userUuid, String identityType, String identifierNormalized) {
        return database.update(
                """
                        update iam_user_identity
                        set status = 'DISABLED', deleted = 1, updated_at = current_timestamp
                        where user_id = ? and user_uuid = ? and identity_type = ? and identifier_normalized = ? and deleted = 0
                        """,
                userId, userUuid, identityType, identifierNormalized
        );
    }

    @Override
    public int updateAccountStatus(Long userId, String userUuid, String status) {
        return database.update(
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
                status, userId, userUuid
        );
    }

    @Override
    public void updateIdentityStatus(Long userId, String userUuid, String status) {
        database.update(
                "update iam_user_identity set status = ?, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                status, userId, userUuid
        );
    }

    @Override
    public void updateCredentialStatus(Long userId, String userUuid, String status) {
        database.update(
                "update iam_user_credential set status = ?, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                status, userId, userUuid
        );
    }

    @Override
    public int softDeleteAccount(Long userId, String userUuid) {
        return database.update(
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
                userId, userUuid
        );
    }

    @Override
    public void softDeleteAccountRelations(Long userId, String userUuid) {
        database.update(
                "update iam_user_identity set status = 'DISABLED', deleted = 1, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                userId, userUuid
        );
        database.update(
                "update iam_user_credential set status = 'DISABLED', deleted = 1, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                userId, userUuid
        );
        database.update(
                "update iam_user_device set deleted = 1, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                userId, userUuid
        );
        database.update(
                "update iam_user_security_setting set deleted = 1, updated_at = current_timestamp where user_id = ? and user_uuid = ? and deleted = 0",
                userId, userUuid
        );
    }

    @Override
    public int upsertAccount(SysUserEntity user, String userNo, String displayName, String status, String source, int deleted) {
        return database.update(
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
                user.getId(), userNo, displayName, user.getAvatarUrl(), status, source, deleted
        );
    }

    @Override
    public int upsertPasswordCredential(PasswordCredential command) {
        return database.update(
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
                command.userId(), command.userUuid(), command.passwordHash(), command.status(), command.deleted(),
                command.resolvePasswordChange() ? 1 : 0
        );
    }

    @Override
    public int updateLastLogin(LoginSuccess command) {
        return database.update(
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
                command.occurredAt(), command.occurredAt(), command.userId(), command.userUuid()
        );
    }

    @Override
    public void markIdentityUsed(LoginSuccess command, String identityType, String identifierNormalized) {
        database.update(
                """
                        update iam_user_identity
                        set last_used_at = ?, updated_at = ?
                        where user_id = ? and user_uuid = ? and identity_type = ? and identifier_normalized = ? and deleted = 0
                        """,
                command.occurredAt(), command.occurredAt(), command.userId(), command.userUuid(), identityType, identifierNormalized
        );
    }

    @Override
    public int upsertDevice(LoginDevice command) {
        return database.update(
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
                command.userId(), command.userUuid(), command.deviceId(), command.deviceName(), command.deviceType(),
                command.os(), command.browser(), command.ip(), command.occurredAt()
        );
    }

    @Override
    public int upsertProfile(SysUserEntity user, String userUuid, int deleted) {
        return database.update(
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
                user.getId(), userUuid, user.getNickname(), user.getRealName(), user.getGender(), user.getBirthMonth(),
                user.getRegion(), user.getAvailableTime(), user.getIdCardNumber() != null && !user.getIdCardNumber().trim().isEmpty(), deleted
        );
    }

    @Override
    public int upsertSecuritySetting(Long userId, String userUuid, int deleted) {
        return database.update(
                """
                        insert into iam_user_security_setting (user_id, user_uuid, deleted)
                        values (?, ?, ?)
                        on duplicate key update updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then current_timestamp else updated_at end,
                                                deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(deleted) else deleted end
                        """,
                userId, userUuid, deleted
        );
    }

    @Override
    public int insertEvent(IamEvent event) {
        return database.update(
                """
                        insert into iam_user_event (user_id, user_uuid, event_type, event_source, operator_id, operator_uuid, ip, user_agent, detail_json)
                        values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as json))
                        """,
                event.userId(), event.userUuid(), event.eventType(), event.eventSource(), event.operatorId(),
                event.operatorUuid(), event.ip(), event.userAgent(), event.detailJson()
        );
    }
}
