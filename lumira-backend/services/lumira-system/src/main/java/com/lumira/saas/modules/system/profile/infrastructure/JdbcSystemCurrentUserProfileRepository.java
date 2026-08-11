package com.lumira.saas.modules.system.profile.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.system.profile.repository.SystemCurrentUserProfileRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/** JDBC/MyBatis implementation of the authenticated-user profile boundary. */
@Repository
public class JdbcSystemCurrentUserProfileRepository implements SystemCurrentUserProfileRepository {
    private final MyBatisQueryOperations database;

    public JdbcSystemCurrentUserProfileRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public int updateBasicProfile(BasicProfile command) {
        return database.update(
                """
                        update sys_user
                        set avatar_url = ?, nickname = ?, real_name = ?, mobile = ?, email = ?, birth_month = ?, gender = ?, region = ?,
                            available_time = ?, id_card_number = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and uuid = ? and deleted = 0
                        """,
                command.avatarUrl(), command.nickname(), command.realName(), command.mobile(), command.email(), command.birthMonth(),
                command.gender(), command.region(), command.availableTime(), command.idCardNumber(), command.actor().userId(),
                command.actor().userUuid(), command.updatedAt(), command.userId(), command.userUuid()
        );
    }

    @Override
    public int updateAvatar(Long userId, String userUuid, String avatarUrl, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update sys_user
                        set avatar_url = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and uuid = ? and deleted = 0
                        """,
                avatarUrl, actor.userId(), actor.userUuid(), updatedAt, userId, userUuid
        );
    }

    @Override
    public int initializeAvatarIfAbsent(Long userId, String userUuid, String avatarUrl, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update sys_user
                        set avatar_url = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and uuid = ? and deleted = 0
                          and (avatar_url is null or trim(avatar_url) = '')
                        """,
                avatarUrl, actor.userId(), actor.userUuid(), updatedAt, userId, userUuid
        );
    }

    @Override
    public boolean hasActiveWechatBinding(Long userId, String userUuid) {
        try {
            return database.queryForObject(
                    """
                            select 1
                            from sys_user_wechat_binding
                            where user_id = ?
                              and user_uuid = ?
                              and deleted = 0
                            limit 1
                            """,
                    Long.class,
                    userId, userUuid
            ) != null;
        } catch (EmptyResultDataAccessException ignored) {
            return false;
        }
    }

    @Override
    public int updateContact(Long userId, String userUuid, String contactType, String value, Actor actor, LocalDateTime updatedAt) {
        String column = "mobile".equals(contactType) ? "mobile" : "email";
        return database.update(
                "update sys_user set " + column + " = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and uuid = ? and deleted = 0",
                value, actor.userId(), actor.userUuid(), updatedAt, userId, userUuid
        );
    }

    @Override
    public void upsertLocale(LocaleProfile command) {
        database.update(
                """
                        insert into iam_user_profile (
                            user_id, user_uuid, nickname, real_name, gender, birth_month, region, locale, timezone, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 'Asia/Shanghai', 0)
                        on duplicate key update
                            locale = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(locale) else locale end,
                            deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) then 0 else deleted end,
                            updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then current_timestamp else updated_at end
                        """,
                command.userId(), command.userUuid(), command.nickname(), command.realName(), command.gender(), command.birthMonth(),
                command.region(), command.locale()
        );
    }

    @Override
    public int updatePasswordHash(Long userId, String userUuid, String passwordHash, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                "update sys_user set password_hash = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and uuid = ? and deleted = 0",
                passwordHash, actor.userId(), actor.userUuid(), updatedAt, userId, userUuid
        );
    }

    @Override
    public void mergeExtraProfileJson(Long userId, String userUuid, String extraJson) {
        database.update(
                """
                        insert into iam_user_profile (user_id, user_uuid, extra_json, deleted)
                        values (?, ?, ?, 0)
                        on duplicate key update extra_json = case when user_id = values(user_id) and user_uuid = values(user_uuid) then json_merge_patch(coalesce(extra_json, json_object()), values(extra_json)) else extra_json end,
                                                deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) then 0 else deleted end,
                                                updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then current_timestamp else updated_at end
                        """,
                userId, userUuid, extraJson
        );
    }

    @Override
    public String findExtraProfileJson(Long userId, String userUuid) {
        try {
            return database.queryForObject(
                    """
                            select extra_json
                            from iam_user_profile
                            where user_id = ? and user_uuid = ? and deleted = 0
                            limit 1
                            """,
                    String.class,
                    userId, userUuid
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    @Override
    public String findLocale(Long userId, String userUuid) {
        try {
            return database.queryForObject(
                    """
                            select locale
                            from iam_user_profile
                            where user_id = ? and user_uuid = ? and deleted = 0
                            limit 1
                            """,
                    String.class,
                    userId, userUuid
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    @Override
    public List<CurrentUserVO.RoleOptionVO> findAvailableRoles(Long userId, String userUuid) {
        return database.query(
                """
                        select r.id as id,
                               r.role_code as roleCode,
                               r.role_name as roleName,
                               r.role_type as roleType,
                               count(rp.permission_key) as permissionCount
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        left join sys_role_permission rp on rp.role_id = r.id and rp.deleted = 0
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.deleted = 0
                        group by r.id, r.role_code, r.role_name, r.role_type
                        order by r.id desc
                        """,
                (row, rowNumber) -> {
                    CurrentUserVO.RoleOptionVO role = new CurrentUserVO.RoleOptionVO();
                    role.setId(row.getLong("id"));
                    role.setRoleCode(row.getString("roleCode"));
                    role.setRoleName(row.getString("roleName"));
                    role.setRoleType(row.getString("roleType"));
                    role.setPermissionCount(row.getInt("permissionCount"));
                    return role;
                },
                userId, userUuid
        );
    }

    @Override
    public List<String> findRoleNames(Long userId, String userUuid) {
        return database.queryForList(
                """
                        select r.role_name
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.deleted = 0
                        order by r.id asc
                        """,
                String.class,
                userId, userUuid
        );
    }
}
