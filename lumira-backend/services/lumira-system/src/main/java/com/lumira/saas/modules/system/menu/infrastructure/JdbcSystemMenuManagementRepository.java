package com.lumira.saas.modules.system.menu.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.menu.repository.SystemMenuManagementRepository;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/** MyBatis/JDBC implementation of the managed menu and permission boundary. */
@Repository
public class JdbcSystemMenuManagementRepository implements SystemMenuManagementRepository {
    private final MyBatisQueryOperations database;

    public JdbcSystemMenuManagementRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<SystemVO.PermissionVO> findPermissions() {
        return database.query(
                """
                        select permission_key as permissionKey, permission_name as permissionName,
                               permission_group as permissionGroup, source_type as sourceType, plugin_code as pluginCode
                        from sys_permission
                        where deleted = 0
                        order by permission_group asc, permission_key asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.PermissionVO.class)
        );
    }

    @Override
    public List<SystemVO.MenuVO> findMenus() {
        return database.query(
                """
                        select id, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where deleted = 0
                        order by sort_no asc, id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.MenuVO.class)
        );
    }

    @Override
    public List<PluginMenu> findActivePluginMenus() {
        return database.queryForList(
                """
                        select relation.menu_code as menuCode,
                               relation.menu_name as menuName,
                               relation.route_path as path,
                               relation.icon,
                               relation.permission_key as permissionKey,
                               relation.parent_menu_code as parentMenuCode,
                               relation.sort_no as sortNo,
                               relation.plugin_code as pluginCode
                        from sys_plugin_menu_rel relation
                        join sys_plugin_version version
                          on version.plugin_code = relation.plugin_code
                         and version.version = relation.plugin_version
                         and version.is_active = 1
                         and version.deleted = 0
                        join sys_plugin_definition definition
                          on definition.plugin_code = relation.plugin_code
                         and definition.status = 'ENABLED'
                         and definition.deleted = 0
                        where relation.deleted = 0
                        order by relation.sort_no asc, relation.id asc
                        """
        ).stream().map(this::pluginMenu).toList();
    }

    @Override
    public List<SystemVO.MenuVO> findEnabledMenus() {
        return database.query(
                """
                        select menu_code as menuCode, path, component, permission_key as permissionKey
                        from sys_menu
                        where deleted = 0 and status = 'ENABLED'
                        """,
                new BeanPropertyRowMapper<>(SystemVO.MenuVO.class)
        );
    }

    @Override
    public SystemVO.MenuVO findActiveMenu(Long menuId) {
        return queryOne(
                """
                        select id, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where id = ? and deleted = 0
                        """,
                SystemVO.MenuVO.class,
                menuId
        );
    }

    @Override
    public boolean hasActiveChild(Long menuId) {
        return database.exists("select 1 from sys_menu where parent_id = ? and deleted = 0 limit 1", menuId);
    }

    @Override
    public boolean hasActivePermissionReference(String permissionKey) {
        return database.exists("select 1 from sys_role_permission where permission_key = ? and deleted = 0 limit 1", permissionKey);
    }

    @Override
    public int reorder(MenuOrder command) {
        return database.update(
                """
                        update sys_menu
                        set parent_id = ?, sort_no = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and menu_code = ? and menu_type = ? and deleted = 0
                        """,
                command.parentId() == null ? 0L : command.parentId(), command.sortNo(), command.actor().userId(), command.actor().userUuid(),
                command.updatedAt(), command.version().id(), command.version().menuCode(), command.version().menuType()
        );
    }

    @Override
    public int updateStatus(MenuVersion version, String status, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update sys_menu
                        set status = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and menu_code = ? and menu_type = ? and deleted = 0
                        """,
                status, actor.userId(), actor.userUuid(), updatedAt, version.id(), version.menuCode(), version.menuType()
        );
    }

    @Override
    public int softDelete(MenuVersion version, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update sys_menu
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and menu_code = ? and menu_type = ? and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt, version.id(), version.menuCode(), version.menuType()
        );
    }

    @Override
    public MenuSaveResult save(MenuSave command) {
        if (command.existing() == null) {
            int inserted = database.update(
                    """
                            insert into sys_menu (
                                parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no,
                                permission_key, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    command.parentId() == null ? 0L : command.parentId(), command.menuCode(), command.menuName(), command.menuType(),
                    command.path(), command.component(), command.icon(), command.sortNo() == null ? 0 : command.sortNo(), command.permissionKey(),
                    command.status(), command.actor().userId(), command.actor().userUuid(), command.actor().userId(), command.actor().userUuid()
            );
            Long menuId = inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
            return new MenuSaveResult(inserted, menuId);
        }
        int updated = database.update(
                """
                        update sys_menu
                        set parent_id = ?, menu_code = ?, menu_name = ?, menu_type = ?, path = ?, component = ?,
                            icon = ?, sort_no = ?, permission_key = ?, status = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and menu_code = ? and menu_type = ? and deleted = 0
                        """,
                command.parentId() == null ? 0L : command.parentId(), command.menuCode(), command.menuName(), command.menuType(), command.path(),
                command.component(), command.icon(), command.sortNo() == null ? 0 : command.sortNo(), command.permissionKey(), command.status(),
                command.actor().userId(), command.actor().userUuid(), command.updatedAt(), command.existing().id(), command.existing().menuCode(),
                command.existing().menuType()
        );
        return new MenuSaveResult(updated, command.existing().id());
    }

    private PluginMenu pluginMenu(Map<String, Object> row) {
        return new PluginMenu(
                stringValue(row.get("menuCode")),
                stringValue(row.get("menuName")),
                stringValue(row.get("path")),
                stringValue(row.get("icon")),
                stringValue(row.get("permissionKey")),
                stringValue(row.get("parentMenuCode")),
                integerValue(row.get("sortNo")),
                stringValue(row.get("pluginCode"))
        );
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private <T> T queryOne(String sql, Class<T> type, Object... params) {
        try {
            return database.queryForObject(sql, new BeanPropertyRowMapper<>(type), params);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }
}
