package com.yourcompany.saas.modules.system.app;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.common.vo.PageResponse;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.infrastructure.security.service.SecuritySettingsService;
import com.yourcompany.saas.modules.audit.app.LoginAuditService;
import com.yourcompany.saas.modules.audit.app.OperationAuditService;
import com.yourcompany.saas.modules.auth.app.AuthAppService;
import com.yourcompany.saas.modules.iam.service.PermissionSnapshotService;
import com.yourcompany.saas.modules.plugin.app.PluginManagementAppService;
import com.yourcompany.saas.modules.system.dto.SystemDTO;
import com.yourcompany.saas.modules.system.vo.SystemVO;
import com.yourcompany.saas.modules.tenant.domain.TenantDomainService;
import com.yourcompany.saas.modules.tenant.entity.TenantInfoEntity;
import com.yourcompany.saas.modules.user.domain.UserDomainService;
import com.yourcompany.saas.modules.user.entity.SysUserEntity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
public class SystemManagementAppService {

    private static final Long DEFAULT_PUBLIC_TENANT_ID = 1001L;
    private static final String BRANDING_WEBSITE_NAME_KEY = "branding.website-name";
    private static final String BRANDING_WEBSITE_FAVICON_URL_KEY = "branding.website-favicon-url";
    private static final String BRANDING_WEBSITE_LOGO_URL_KEY = "branding.website-logo-url";
    private static final String BRANDING_FOOTER_ICP_KEY = "branding.footer-icp";
    private static final String BRANDING_FOOTER_COPYRIGHT_KEY = "branding.footer-copyright";
    private static final List<String> BRANDING_CONFIG_KEYS = List.of(
            BRANDING_WEBSITE_NAME_KEY,
            BRANDING_WEBSITE_FAVICON_URL_KEY,
            BRANDING_WEBSITE_LOGO_URL_KEY,
            BRANDING_FOOTER_ICP_KEY,
            BRANDING_FOOTER_COPYRIGHT_KEY
    );

    private static final List<SystemVO.ShortcutVO> DASHBOARD_SHORTCUTS = List.of(
            shortcut("系统管理", "用户、角色、菜单、字典、配置", "/system/management", "system:view"),
            shortcut("个性化设置", "站点名称、Logo、Icon 和页脚信息", "/system/personalization", "system:config:view"),
            shortcut("安全设置", "空闲超时与 token 生命周期", "/system/security", "system:config:view"),
            shortcut("租户中心", "当前租户与可访问租户", "/tenant/overview", "tenant:view"),
            shortcut("审计中心", "登录和操作日志", "/audit/overview", "audit:view"),
            shortcut("插件管理", "插件安装、启用和运行态", "/system/plugins", "plugin:management:view")
    );

    private final JdbcTemplate jdbcTemplate;
    private final AuthAppService authAppService;
    private final TenantDomainService tenantDomainService;
    private final UserDomainService userDomainService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final PluginManagementAppService pluginManagementAppService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAuditService loginAuditService;
    private final OperationAuditService operationAuditService;
    private final SecuritySettingsService securitySettingsService;

    public SystemManagementAppService(
            JdbcTemplate jdbcTemplate,
            AuthAppService authAppService,
            TenantDomainService tenantDomainService,
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            PluginManagementAppService pluginManagementAppService,
            PasswordEncoder passwordEncoder,
            LoginAuditService loginAuditService,
            OperationAuditService operationAuditService,
            SecuritySettingsService securitySettingsService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.authAppService = authAppService;
        this.tenantDomainService = tenantDomainService;
        this.userDomainService = userDomainService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.pluginManagementAppService = pluginManagementAppService;
        this.passwordEncoder = passwordEncoder;
        this.loginAuditService = loginAuditService;
        this.operationAuditService = operationAuditService;
        this.securitySettingsService = securitySettingsService;
    }

    public SystemVO.DashboardSummaryVO dashboardSummary(CurrentUser currentUser) {
        SystemVO.DashboardSummaryVO summary = new SystemVO.DashboardSummaryVO();
        summary.setCurrentUser(authAppService.currentUser(currentUser));
        TenantInfoEntity tenantInfo = tenantDomainService.findTenantById(currentTenantId(currentUser)).orElse(null);
        summary.setCurrentTenant(tenantDomainService.toTenantSummary(tenantInfo));
        summary.setTenantPlugins(pluginManagementAppService.availablePlugins(currentTenantId(currentUser)));
        summary.setMenuCount(countMenus(currentTenantId(currentUser)));
        summary.setPermissionCount(permissionSnapshotService.loadSnapshot(currentTenantId(currentUser), currentUser.getUserId()).getPermissionList().size());
        summary.setRecentLoginLogs(listLoginLogs(currentUser, currentUser.getUsername(), currentTenantId(currentUser), null, null, null, 1, 5).getRecords());
        summary.setRecentOperationLogs(listOperationLogs(currentUser, currentUser.getUsername(), currentTenantId(currentUser), null, null, 1, 5).getRecords());
        summary.setShortcuts(DASHBOARD_SHORTCUTS);
        return summary;
    }

    public SystemVO.ProfileSummaryVO profileSummary(CurrentUser currentUser) {
        SystemVO.ProfileSummaryVO summary = new SystemVO.ProfileSummaryVO();
        summary.setCurrentUser(authAppService.currentUser(currentUser));
        TenantInfoEntity tenantInfo = tenantDomainService.findTenantById(currentTenantId(currentUser)).orElse(null);
        summary.setCurrentTenant(tenantDomainService.toTenantSummary(tenantInfo));
        summary.setRoleNames(listCurrentTenantRoleNames(currentUser.getUserId(), currentTenantId(currentUser)));
        summary.setPermissionCount(permissionSnapshotService.loadSnapshot(currentTenantId(currentUser), currentUser.getUserId()).getPermissionList().size());
        summary.setRecentLoginLogs(listLoginLogs(currentUser, currentUser.getUsername(), currentTenantId(currentUser), null, null, null, 1, 10).getRecords());
        return summary;
    }

    public PageResponse<SystemVO.UserVO> listUsers(CurrentUser currentUser, String username, String mobile, String status, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        String baseSql = """
                from sys_user u
                join sys_user_tenant ut
                  on ut.user_id = u.id
                 and ut.tenant_id = ?
                 and ut.deleted = 0
                 and ut.status = 'ENABLED'
                where u.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(username)) {
            baseSql += " and u.username like ?";
            params.add(like(username));
        }
        if (StringUtils.hasText(mobile)) {
            baseSql += " and u.mobile like ?";
            params.add(like(mobile));
        }
        if (StringUtils.hasText(status)) {
            baseSql += " and u.status = ?";
            params.add(status);
        }
        String selectSql = """
                select u.id, u.username, u.mobile, u.nickname, u.real_name as realName,
                       u.avatar_url as avatarUrl, u.email, u.status, u.created_at as createdAt, u.updated_at as updatedAt
                """ + baseSql + """
                order by u.id desc
                """;
        PageResponse<SystemVO.UserVO> page = pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.UserVO.class, pageNo, pageSize, params);
        page.setRecords(page.getRecords().stream().map(user -> {
            user.setTenantNames(listUserTenantNames(user.getId()));
            user.setRoleNames(listUserRoleNames(user.getId(), tenantId));
            return user;
        }).toList());
        return page;
    }

    public SystemVO.UserDetailVO getUser(CurrentUser currentUser, Long userId) {
        SystemVO.UserVO user = queryUser(currentTenantId(currentUser), userId);
        SystemVO.UserDetailVO detail = new SystemVO.UserDetailVO();
        copyUser(detail, user);
        Long tenantId = currentTenantId(currentUser);
        detail.setRoleIds(listUserRoleIds(userId, tenantId));
        detail.setTenantIds(listUserTenantIds(userId));
        return detail;
    }

    @Transactional
    public SystemVO.UserDetailVO createUser(CurrentUser currentUser, SystemDTO.UserUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long userId = insertOrUpdateUser(null, request, currentUser.getUserId());
        upsertUserTenantRelation(userId, tenantId, true, currentUser.getUserId());
        replaceUserRoles(userId, tenantId, request.getRoleIds(), currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "user", "create", "CREATE", "SUCCESS", "创建用户: " + request.getUsername());
        return getUser(currentUser, userId);
    }

    @Transactional
    public SystemVO.UserDetailVO updateUser(CurrentUser currentUser, Long userId, SystemDTO.UserUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        insertOrUpdateUser(userId, request, currentUser.getUserId());
        replaceUserRoles(userId, tenantId, request.getRoleIds(), currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "user", "update", "UPDATE", "SUCCESS", "更新用户: " + request.getUsername());
        return getUser(currentUser, userId);
    }

    @Transactional
    public boolean updateUserStatus(CurrentUser currentUser, Long userId, String status) {
        jdbcTemplate.update(
                "update sys_user set status = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                status,
                currentUser.getUserId(),
                LocalDateTime.now(),
                userId
        );
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "user", "status", "UPDATE", "SUCCESS", "更新用户状态: " + userId + " -> " + status);
        return true;
    }

    public List<SystemVO.RoleVO> listUserRoles(CurrentUser currentUser, Long userId) {
        Long tenantId = currentTenantId(currentUser);
        return jdbcTemplate.query(
                """
                        select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id and r.deleted = 0
                        where ur.tenant_id = ? and ur.user_id = ? and ur.deleted = 0
                        order by r.id desc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.RoleVO.class),
                tenantId,
                userId
        );
    }

    public PageResponse<SystemVO.RoleVO> listRoles(CurrentUser currentUser, String roleCode, String roleName, String roleType, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        String baseSql = """
                from sys_role r
                where r.tenant_id = ? and r.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(roleCode)) {
            baseSql += " and r.role_code like ?";
            params.add(like(roleCode));
        }
        if (StringUtils.hasText(roleName)) {
            baseSql += " and r.role_name like ?";
            params.add(like(roleName));
        }
        if (StringUtils.hasText(roleType)) {
            baseSql += " and r.role_type = ?";
            params.add(roleType);
        }
        String selectSql = """
                select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                       r.role_type as roleType, r.created_at as createdAt, r.updated_at as updatedAt
                """ + baseSql + " order by r.id desc";
        PageResponse<SystemVO.RoleVO> page = pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.RoleVO.class, pageNo, pageSize, params);
        page.setRecords(page.getRecords().stream().map(role -> {
            role.setPermissionCount(countRolePermissions(role.getId(), tenantId));
            role.setUserCount(countRoleUsers(role.getId(), tenantId));
            return role;
        }).toList());
        return page;
    }

    public SystemVO.RoleDetailVO getRole(CurrentUser currentUser, Long roleId) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.RoleVO role = queryOne(
                """
                        select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.id = ? and r.tenant_id = ? and r.deleted = 0
                        """,
                SystemVO.RoleVO.class,
                roleId,
                tenantId
        );
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        SystemVO.RoleDetailVO detail = new SystemVO.RoleDetailVO();
        copyRole(detail, role);
        detail.setPermissionCount(countRolePermissions(roleId, tenantId));
        detail.setUserCount(countRoleUsers(roleId, tenantId));
        detail.setPermissionKeys(listRolePermissionKeys(roleId, tenantId));
        return detail;
    }

    @Transactional
    public SystemVO.RoleDetailVO createRole(CurrentUser currentUser, SystemDTO.RoleUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long roleId = upsertRole(null, tenantId, request, currentUser.getUserId());
        replaceRolePermissions(tenantId, roleId, request.getPermissionKeys(), currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "create", "CREATE", "SUCCESS", "创建角色: " + request.getRoleName());
        return getRole(currentUser, roleId);
    }

    @Transactional
    public SystemVO.RoleDetailVO updateRole(CurrentUser currentUser, Long roleId, SystemDTO.RoleUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        upsertRole(roleId, tenantId, request, currentUser.getUserId());
        replaceRolePermissions(tenantId, roleId, request.getPermissionKeys(), currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "update", "UPDATE", "SUCCESS", "更新角色: " + request.getRoleName());
        return getRole(currentUser, roleId);
    }

    @Transactional
    public boolean updateRolePermissions(CurrentUser currentUser, Long roleId, List<String> permissionKeys) {
        Long tenantId = currentTenantId(currentUser);
        replaceRolePermissions(tenantId, roleId, permissionKeys, currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "role", "permissions", "UPDATE", "SUCCESS", "更新角色权限: " + roleId);
        return true;
    }

    public List<SystemVO.PermissionVO> listPermissions(CurrentUser currentUser) {
        Long tenantId = currentTenantId(currentUser);
        return jdbcTemplate.query(
                """
                        select permission_key as permissionKey, permission_name as permissionName,
                               permission_group as permissionGroup, source_type as sourceType, plugin_code as pluginCode
                        from sys_permission
                        where tenant_id = ? and deleted = 0
                        order by permission_group asc, permission_key asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.PermissionVO.class),
                tenantId
        );
    }

    public List<SystemVO.MenuVO> listMenus(CurrentUser currentUser) {
        Long tenantId = currentTenantId(currentUser);
        List<SystemVO.MenuVO> menus = jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where tenant_id = ? and deleted = 0
                        order by sort_no asc, id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.MenuVO.class),
                tenantId
        );
        return buildMenuTree(menus);
    }

    public SystemVO.MenuVO getMenu(CurrentUser currentUser, Long menuId) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.MenuVO menu = queryOne(
                """
                        select id, tenant_id as tenantId, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                SystemVO.MenuVO.class,
                menuId,
                tenantId
        );
        if (menu == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "菜单不存在");
        }
        return menu;
    }

    @Transactional
    public SystemVO.MenuVO createMenu(CurrentUser currentUser, SystemDTO.MenuUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long menuId = insertMenu(null, tenantId, request, currentUser.getUserId());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "menu", "create", "CREATE", "SUCCESS", "创建菜单: " + request.getMenuName());
        return getMenu(currentUser, menuId);
    }

    @Transactional
    public SystemVO.MenuVO updateMenu(CurrentUser currentUser, Long menuId, SystemDTO.MenuUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        insertMenu(menuId, tenantId, request, currentUser.getUserId());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "menu", "update", "UPDATE", "SUCCESS", "更新菜单: " + request.getMenuName());
        return getMenu(currentUser, menuId);
    }

    @Transactional
    public boolean updateMenuStatus(CurrentUser currentUser, Long menuId, String status) {
        jdbcTemplate.update(
                "update sys_menu set status = ?, updated_by = ?, updated_at = ? where id = ? and tenant_id = ? and deleted = 0",
                status,
                currentUser.getUserId(),
                LocalDateTime.now(),
                menuId,
                currentTenantId(currentUser)
        );
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "menu", "status", "UPDATE", "SUCCESS", "更新菜单状态: " + menuId + " -> " + status);
        return true;
    }

    public PageResponse<SystemVO.DictTypeVO> listDictTypes(CurrentUser currentUser, String dictCode, String dictName, String status, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        String baseSql = """
                from sys_dict_type t
                where t.deleted = 0 and (t.tenant_id is null or t.tenant_id = ?)
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(dictCode)) {
            baseSql += " and t.dict_code like ?";
            params.add(like(dictCode));
        }
        if (StringUtils.hasText(dictName)) {
            baseSql += " and t.dict_name like ?";
            params.add(like(dictName));
        }
        if (StringUtils.hasText(status)) {
            baseSql += " and t.status = ?";
            params.add(status);
        }
        String selectSql = """
                select t.id, t.tenant_id as tenantId, t.dict_code as dictCode, t.dict_name as dictName,
                       t.status, t.is_system as isSystem, t.remark
                """ + baseSql + " order by t.is_system desc, t.id desc";
        PageResponse<SystemVO.DictTypeVO> page = pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.DictTypeVO.class, pageNo, pageSize, params);
        return page;
    }

    public SystemVO.DictTypeVO getDictType(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.DictTypeVO type = queryOne(
                """
                        select id, tenant_id as tenantId, dict_code as dictCode, dict_name as dictName,
                               status, is_system as isSystem, remark
                        from sys_dict_type
                        where id = ? and deleted = 0 and (tenant_id is null or tenant_id = ?)
                        """,
                SystemVO.DictTypeVO.class,
                id,
                tenantId
        );
        if (type == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
        return type;
    }

    @Transactional
    public SystemVO.DictTypeVO createDictType(CurrentUser currentUser, SystemDTO.DictTypeUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long id = upsertDictType(null, tenantId, request, currentUser.getUserId());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "dict", "create", "CREATE", "SUCCESS", "创建字典类型: " + request.getDictCode());
        return getDictType(currentUser, id);
    }

    @Transactional
    public SystemVO.DictTypeVO updateDictType(CurrentUser currentUser, Long id, SystemDTO.DictTypeUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        upsertDictType(id, tenantId, request, currentUser.getUserId());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "dict", "update", "UPDATE", "SUCCESS", "更新字典类型: " + request.getDictCode());
        return getDictType(currentUser, id);
    }

    public List<SystemVO.DictItemVO> listDictItems(CurrentUser currentUser, Long dictTypeId) {
        return jdbcTemplate.query(
                """
                        select id, dict_type_id as dictTypeId, item_label as itemLabel, item_value as itemValue,
                               sort_no as sortNo, status, remark
                        from sys_dict_item
                        where dict_type_id = ? and deleted = 0
                        order by sort_no asc, id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.DictItemVO.class),
                dictTypeId
        );
    }

    public SystemVO.DictItemVO getDictItem(Long dictTypeId, Long itemId) {
        SystemVO.DictItemVO item = queryOne(
                """
                        select id, dict_type_id as dictTypeId, item_label as itemLabel, item_value as itemValue,
                               sort_no as sortNo, status, remark
                        from sys_dict_item
                        where id = ? and dict_type_id = ? and deleted = 0
                        """,
                SystemVO.DictItemVO.class,
                itemId,
                dictTypeId
        );
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典项不存在");
        }
        return item;
    }

    @Transactional
    public SystemVO.DictItemVO createDictItem(CurrentUser currentUser, Long dictTypeId, SystemDTO.DictItemUpsertRequest request) {
        Long id = upsertDictItem(null, dictTypeId, request, currentUser.getUserId());
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "dict", "item-create", "CREATE", "SUCCESS", "创建字典项: " + request.getItemLabel());
        return getDictItem(dictTypeId, id);
    }

    @Transactional
    public SystemVO.DictItemVO updateDictItem(CurrentUser currentUser, Long dictTypeId, Long itemId, SystemDTO.DictItemUpsertRequest request) {
        upsertDictItem(itemId, dictTypeId, request, currentUser.getUserId());
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "dict", "item-update", "UPDATE", "SUCCESS", "更新字典项: " + request.getItemLabel());
        return getDictItem(dictTypeId, itemId);
    }

    public PageResponse<SystemVO.ConfigVO> listConfigs(CurrentUser currentUser, String configKey, String configName, String configScope, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        String baseSql = """
                from sys_config c
                where c.deleted = 0 and (c.tenant_id is null or c.tenant_id = ?)
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(configKey)) {
            baseSql += " and c.config_key like ?";
            params.add(like(configKey));
        }
        if (StringUtils.hasText(configName)) {
            baseSql += " and c.config_name like ?";
            params.add(like(configName));
        }
        if (StringUtils.hasText(configScope)) {
            baseSql += " and c.config_scope = ?";
            params.add(configScope);
        }
        String selectSql = """
                select c.id, c.tenant_id as tenantId, c.config_key as configKey, c.config_name as configName,
                       c.config_value as configValue, c.config_scope as configScope, c.is_system as isSystem, c.remark
                """ + baseSql + " order by c.is_system desc, c.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.ConfigVO.class, pageNo, pageSize, params);
    }

    public SystemVO.ConfigVO getConfig(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        SystemVO.ConfigVO config = queryOne(
                """
                        select id, tenant_id as tenantId, config_key as configKey, config_name as configName,
                               config_value as configValue, config_scope as configScope, is_system as isSystem, remark
                        from sys_config
                        where id = ? and deleted = 0 and (tenant_id is null or tenant_id = ?)
                        """,
                SystemVO.ConfigVO.class,
                id,
                tenantId
        );
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "配置不存在");
        }
        return config;
    }

    @Transactional
    public SystemVO.ConfigVO updateConfig(CurrentUser currentUser, Long id, SystemDTO.ConfigUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        jdbcTemplate.update(
                """
                        update sys_config
                        set config_key = ?, config_name = ?, config_value = ?, config_scope = ?, remark = ?,
                            updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0 and (tenant_id is null or tenant_id = ?)
                        """,
                request.getConfigKey(),
                request.getConfigName(),
                request.getConfigValue(),
                request.getConfigScope(),
                request.getRemark(),
                currentUser.getUserId(),
                LocalDateTime.now(),
                id,
                tenantId
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "config", "update", "UPDATE", "SUCCESS", "更新配置: " + request.getConfigKey());
        return getConfig(currentUser, id);
    }

    @Transactional
    public SystemVO.ConfigVO createConfig(CurrentUser currentUser, SystemDTO.ConfigUpsertRequest request) {
        Long tenantId = "TENANT".equalsIgnoreCase(request.getConfigScope()) ? currentTenantId(currentUser) : null;
        jdbcTemplate.update(
                """
                        insert into sys_config (
                            tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, 0, ?, ?, ?, 0)
                        """,
                tenantId,
                request.getConfigKey(),
                request.getConfigName(),
                request.getConfigValue(),
                request.getConfigScope(),
                request.getRemark(),
                currentUser.getUserId(),
                currentUser.getUserId()
        );
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "config", "create", "CREATE", "SUCCESS", "创建配置: " + request.getConfigKey());
        return jdbcTemplate.queryForObject(
                """
                        select id, tenant_id as tenantId, config_key as configKey, config_name as configName,
                               config_value as configValue, config_scope as configScope, is_system as isSystem, remark
                        from sys_config
                        where config_key = ? and deleted = 0 and tenant_id <=> ?
                        order by id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(SystemVO.ConfigVO.class),
                request.getConfigKey(),
                tenantId
        );
    }

    public SystemVO.SecuritySettingsVO getSecuritySettings() {
        return toSecuritySettingsVO(securitySettingsService.loadSettings());
    }

    @Transactional
    public SystemVO.SecuritySettingsVO updateSecuritySettings(CurrentUser currentUser, SystemDTO.SecuritySettingsRequest request) {
        SecuritySettingsService.SecuritySettingsSnapshot updated = securitySettingsService.updateSettings(toSnapshot(request));
        operationAuditService.log(
                currentTenantId(currentUser),
                currentUser.getUserId(),
                currentUser.getUsername(),
                "security",
                "update",
                "UPDATE",
                "SUCCESS",
                "更新安全设置"
        );
        return toSecuritySettingsVO(updated);
    }

    public SystemVO.BrandingSettingsVO getBrandingSettings(CurrentUser currentUser) {
        return loadBrandingSettings(currentTenantId(currentUser));
    }

    public SystemVO.BrandingSettingsVO getPublicBrandingSettings(Long preferredTenantId) {
        Long tenantId = preferredTenantId == null ? DEFAULT_PUBLIC_TENANT_ID : preferredTenantId;
        return loadBrandingSettings(tenantId);
    }

    @Transactional
    public SystemVO.BrandingSettingsVO updateBrandingSettings(CurrentUser currentUser, SystemDTO.BrandingSettingsRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long operatorId = currentUser.getUserId();
        upsertBrandingConfig(
                tenantId,
                BRANDING_WEBSITE_NAME_KEY,
                "站点名称",
                sanitizeBrandingText(request.getWebsiteName(), "宏翔商道"),
                "控制台顶部与浏览器标题展示名称",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_WEBSITE_FAVICON_URL_KEY,
                "站点图标地址",
                sanitizeBrandingText(request.getWebsiteFaviconUrl(), ""),
                "浏览器标签页 icon 地址",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_WEBSITE_LOGO_URL_KEY,
                "站点 Logo 地址",
                sanitizeBrandingText(request.getWebsiteLogoUrl(), ""),
                "控制台左上角品牌 Logo 地址",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_FOOTER_ICP_KEY,
                "页脚 ICP 备案",
                sanitizeBrandingText(request.getFooterIcp(), ""),
                "页脚备案信息",
                operatorId
        );
        upsertBrandingConfig(
                tenantId,
                BRANDING_FOOTER_COPYRIGHT_KEY,
                "页脚版权声明",
                sanitizeBrandingText(request.getFooterCopyright(), ""),
                "页脚版权声明",
                operatorId
        );

        operationAuditService.log(
                tenantId,
                currentUser.getUserId(),
                currentUser.getUsername(),
                "system",
                "branding-update",
                "UPDATE",
                "SUCCESS",
                "更新个性化设置"
        );
        return loadBrandingSettings(tenantId);
    }

    public PageResponse<SystemVO.AuditLogVO> listLoginLogs(CurrentUser currentUser, String username, Long tenantId, long pageNo, long pageSize) {
        return listLoginLogs(currentUser, username, tenantId, null, null, null, pageNo, pageSize);
    }

    public PageResponse<SystemVO.AuditLogVO> listLoginLogs(
            CurrentUser currentUser,
            String username,
            Long tenantId,
            String loginType,
            String startTime,
            String endTime,
            long pageNo,
            long pageSize
    ) {
        Long effectiveTenantId = tenantId == null ? currentTenantId(currentUser) : tenantId;
        String baseSql = """
                from audit_login_log l
                where 1 = 1
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(username)) {
            baseSql += " and l.username like ?";
            params.add(like(username));
        }
        if (StringUtils.hasText(loginType)) {
            baseSql += " and l.login_type = ?";
            params.add(loginType);
        }
        if (effectiveTenantId != null) {
            baseSql += " and l.tenant_id = ?";
            params.add(effectiveTenantId);
        }
        if (StringUtils.hasText(startTime)) {
            baseSql += " and l.created_at >= ?";
            params.add(parseDateTime(startTime));
        }
        if (StringUtils.hasText(endTime)) {
            baseSql += " and l.created_at <= ?";
            params.add(parseDateTime(endTime));
        }
        String selectSql = """
                select l.id, l.tenant_id as tenantId, l.user_id as userId, l.username, l.login_type as logType,
                       l.login_result as logResult, l.fail_reason as failReason, l.login_ip as loginIp,
                       l.user_agent as userAgent, l.request_id as requestId, l.trace_id as traceId, l.created_at as createdAt
                """ + baseSql + " order by l.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.AuditLogVO.class, pageNo, pageSize, params);
    }

    public PageResponse<SystemVO.AuditLogVO> listOperationLogs(CurrentUser currentUser, String username, Long tenantId, long pageNo, long pageSize) {
        return listOperationLogs(currentUser, username, tenantId, null, null, pageNo, pageSize);
    }

    public PageResponse<SystemVO.AuditLogVO> listOperationLogs(
            CurrentUser currentUser,
            String username,
            Long tenantId,
            String startTime,
            String endTime,
            long pageNo,
            long pageSize
    ) {
        Long effectiveTenantId = tenantId == null ? currentTenantId(currentUser) : tenantId;
        String baseSql = """
                from audit_operation_log l
                where 1 = 1
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(username)) {
            baseSql += " and l.username like ?";
            params.add(like(username));
        }
        if (effectiveTenantId != null) {
            baseSql += " and l.tenant_id = ?";
            params.add(effectiveTenantId);
        }
        if (StringUtils.hasText(startTime)) {
            baseSql += " and l.created_at >= ?";
            params.add(parseDateTime(startTime));
        }
        if (StringUtils.hasText(endTime)) {
            baseSql += " and l.created_at <= ?";
            params.add(parseDateTime(endTime));
        }
        String selectSql = """
                select l.id, l.tenant_id as tenantId, l.user_id as userId, l.username, l.module_name as moduleName,
                       l.action_name as actionName, l.operation_type as operationType, l.result_status as logResult,
                       l.detail_message as detailMessage, l.request_id as requestId, l.trace_id as traceId,
                       l.created_at as createdAt
                """ + baseSql + " order by l.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.AuditLogVO.class, pageNo, pageSize, params);
    }

    public Integer countMenus(Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from sys_menu where tenant_id = ? and deleted = 0 and status = 'ENABLED'",
                Long.class,
                tenantId
        );
        return count == null ? 0 : count.intValue();
    }

    private SystemVO.BrandingSettingsVO loadBrandingSettings(Long tenantId) {
        Long effectiveTenantId = tenantId == null ? DEFAULT_PUBLIC_TENANT_ID : tenantId;
        String placeholders = BRANDING_CONFIG_KEYS.stream().map(item -> "?").collect(Collectors.joining(", "));
        String sql = """
                select tenant_id as tenantId, config_key as configKey, config_value as configValue
                from sys_config
                where deleted = 0
                  and config_scope = 'PLATFORM'
                  and config_key in (%s)
                  and (tenant_id = ? or tenant_id is null)
                order by case when tenant_id = ? then 0 else 1 end, id desc
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(BRANDING_CONFIG_KEYS);
        params.add(effectiveTenantId);
        params.add(effectiveTenantId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<String, String> valueByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String configKey = String.valueOf(row.get("configKey"));
            if (!valueByKey.containsKey(configKey)) {
                valueByKey.put(configKey, normalizeConfigText(row.get("configValue")));
            }
        }

        SystemVO.BrandingSettingsVO settings = new SystemVO.BrandingSettingsVO();
        settings.setWebsiteName(defaultIfBlank(valueByKey.get(BRANDING_WEBSITE_NAME_KEY), "宏翔商道"));
        settings.setWebsiteFaviconUrl(defaultIfBlank(valueByKey.get(BRANDING_WEBSITE_FAVICON_URL_KEY), ""));
        settings.setWebsiteLogoUrl(defaultIfBlank(valueByKey.get(BRANDING_WEBSITE_LOGO_URL_KEY), ""));
        settings.setFooterIcp(defaultIfBlank(valueByKey.get(BRANDING_FOOTER_ICP_KEY), ""));
        settings.setFooterCopyright(defaultIfBlank(valueByKey.get(BRANDING_FOOTER_COPYRIGHT_KEY), ""));
        return settings;
    }

    private void upsertBrandingConfig(
            Long tenantId,
            String configKey,
            String configName,
            String configValue,
            String remark,
            Long operatorId
    ) {
        Long existingId = queryBrandingConfigId(configKey, tenantId);
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_config (
                                tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, 'PLATFORM', 0, ?, ?, ?, 0)
                            """,
                    tenantId,
                    configKey,
                    configName,
                    configValue,
                    remark,
                    operatorId,
                    operatorId
            );
            return;
        }
        jdbcTemplate.update(
                """
                        update sys_config
                        set config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                            updated_by = ?, updated_at = ?, deleted = 0
                        where id = ?
                        """,
                configName,
                configValue,
                remark,
                operatorId,
                LocalDateTime.now(),
                existingId
        );
    }

    private Long queryBrandingConfigId(String configKey, Long tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id
                            from sys_config
                            where config_key = ? and tenant_id <=> ?
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    configKey,
                    tenantId
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private String sanitizeBrandingText(String value, String fallback) {
        String normalized = normalizeConfigText(value);
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String normalizeConfigText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private SecuritySettingsService.SecuritySettingsSnapshot toSnapshot(SystemDTO.SecuritySettingsRequest request) {
        SecuritySettingsService.SecuritySettingsSnapshot snapshot = new SecuritySettingsService.SecuritySettingsSnapshot();
        snapshot.setIdleTimeoutSeconds(request.getIdleTimeoutSeconds());
        snapshot.setAccessTokenExpireSeconds(request.getAccessTokenExpireSeconds());
        snapshot.setRefreshTokenExpireSeconds(request.getRefreshTokenExpireSeconds());
        return snapshot;
    }

    private SystemVO.SecuritySettingsVO toSecuritySettingsVO(SecuritySettingsService.SecuritySettingsSnapshot snapshot) {
        SystemVO.SecuritySettingsVO vo = new SystemVO.SecuritySettingsVO();
        vo.setIdleTimeoutSeconds(snapshot.getIdleTimeoutSeconds());
        vo.setAccessTokenExpireSeconds(snapshot.getAccessTokenExpireSeconds());
        vo.setRefreshTokenExpireSeconds(snapshot.getRefreshTokenExpireSeconds());
        return vo;
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser.getCurrentTenantId() != null) {
            return currentUser.getCurrentTenantId();
        }
        return tenantDomainService.listUserTenantAccess(currentUser.getUserId()).stream()
                .filter(access -> access.getTenant() != null)
                .findFirst()
                .map(access -> access.getTenant().getId())
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_BOUND, "当前账号没有可用租户"));
    }

    private List<String> listCurrentTenantRoleNames(Long userId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select r.role_name
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id and r.deleted = 0
                        where ur.user_id = ? and ur.tenant_id = ? and ur.deleted = 0
                        order by r.id asc
                        """,
                String.class,
                userId,
                tenantId
        );
    }

    private List<String> listUserTenantNames(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        select t.tenant_name
                        from sys_user_tenant ut
                        join tenant_info t on t.id = ut.tenant_id and t.deleted = 0
                        where ut.user_id = ? and ut.deleted = 0
                        order by t.id asc
                        """,
                String.class,
                userId
        );
    }

    private List<Long> listUserTenantIds(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        select ut.tenant_id
                        from sys_user_tenant ut
                        where ut.user_id = ? and ut.deleted = 0
                        order by ut.tenant_id asc
                        """,
                Long.class,
                userId
        );
    }

    private List<Long> listUserRoleIds(Long userId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select ur.role_id
                        from sys_user_role ur
                        where ur.user_id = ? and ur.tenant_id = ? and ur.deleted = 0
                        order by ur.role_id asc
                        """,
                Long.class,
                userId,
                tenantId
        );
    }

    private List<String> listUserRoleNames(Long userId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select r.role_name
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id and r.deleted = 0
                        where ur.user_id = ? and ur.tenant_id = ? and ur.deleted = 0
                        order by r.id asc
                        """,
                String.class,
                userId,
                tenantId
        );
    }

    private Integer countRolePermissions(Long roleId, Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_role_permission
                        where role_id = ? and tenant_id = ? and deleted = 0
                        """,
                Long.class,
                roleId,
                tenantId
        );
        return count == null ? 0 : count.intValue();
    }

    private Integer countRoleUsers(Long roleId, Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_user_role
                        where role_id = ? and tenant_id = ? and deleted = 0
                        """,
                Long.class,
                roleId,
                tenantId
        );
        return count == null ? 0 : count.intValue();
    }

    private List<String> listRolePermissionKeys(Long roleId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select permission_key
                        from sys_role_permission
                        where role_id = ? and tenant_id = ? and deleted = 0
                        order by permission_key asc
                        """,
                String.class,
                roleId,
                tenantId
        );
    }

    private SystemVO.UserVO queryUser(Long tenantId, Long userId) {
        SystemVO.UserVO user = queryOne(
                """
                        select u.id, u.username, u.mobile, u.nickname, u.real_name as realName, u.avatar_url as avatarUrl,
                               u.email, u.status, u.created_at as createdAt, u.updated_at as updatedAt
                        from sys_user u
                        join sys_user_tenant ut on ut.user_id = u.id and ut.tenant_id = ? and ut.deleted = 0
                        where u.id = ? and u.deleted = 0
                        """,
                SystemVO.UserVO.class,
                tenantId,
                userId
        );
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setTenantNames(listUserTenantNames(user.getId()));
        user.setRoleNames(listUserRoleNames(user.getId(), tenantId));
        return user;
    }

    private Long insertOrUpdateUser(Long userId, SystemDTO.UserUpsertRequest request, Long operatorId) {
        if (userId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_user (
                                username, password_hash, mobile, nickname, real_name, avatar_url, email, status,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    request.getUsername(),
                    passwordEncoder.encode(StringUtils.hasText(request.getPassword()) ? request.getPassword() : "ChangeMe123!"),
                    request.getMobile(),
                    request.getNickname(),
                    request.getRealName(),
                    request.getAvatarUrl(),
                    request.getEmail(),
                    request.getStatus(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject("select id from sys_user where username = ? and deleted = 0 order by id desc limit 1", Long.class, request.getUsername());
        }
        jdbcTemplate.update(
                """
                        update sys_user
                        set username = ?, mobile = ?, nickname = ?, real_name = ?, avatar_url = ?, email = ?, status = ?,
                            updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                request.getUsername(),
                request.getMobile(),
                request.getNickname(),
                request.getRealName(),
                request.getAvatarUrl(),
                request.getEmail(),
                request.getStatus(),
                operatorId,
                LocalDateTime.now(),
                userId
        );
        if (StringUtils.hasText(request.getPassword())) {
            jdbcTemplate.update(
                    "update sys_user set password_hash = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                    passwordEncoder.encode(request.getPassword()),
                    operatorId,
                    LocalDateTime.now(),
                    userId
            );
        }
        return userId;
    }

    private void upsertUserTenantRelation(Long userId, Long tenantId, boolean isDefault, Long operatorId) {
        jdbcTemplate.update(
                """
                        insert into sys_user_tenant (tenant_id, user_id, is_default, status, created_by, updated_by, deleted)
                        values (?, ?, ?, 'ENABLED', ?, ?, 0)
                        on duplicate key update is_default = values(is_default), status = values(status),
                                                 updated_by = values(updated_by), updated_at = current_timestamp, deleted = 0
                        """,
                tenantId,
                userId,
                isDefault ? 1 : 0,
                operatorId,
                operatorId
        );
    }

    private void replaceUserRoles(Long userId, Long tenantId, List<Long> roleIds, Long operatorId) {
        jdbcTemplate.update(
                "delete from sys_user_role where tenant_id = ? and user_id = ?",
                tenantId,
                userId
        );
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            jdbcTemplate.update(
                    """
                            insert into sys_user_role (tenant_id, user_id, role_id, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    userId,
                    roleId,
                    operatorId,
                    operatorId
            );
        }
    }

    private Long upsertRole(Long roleId, Long tenantId, SystemDTO.RoleUpsertRequest request, Long operatorId) {
        if (roleId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_role (tenant_id, role_code, role_name, role_type, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    request.getRoleCode(),
                    request.getRoleName(),
                    request.getRoleType(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject(
                    "select id from sys_role where tenant_id = ? and role_code = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    tenantId,
                    request.getRoleCode()
            );
        }
        jdbcTemplate.update(
                """
                        update sys_role
                        set role_code = ?, role_name = ?, role_type = ?, updated_by = ?, updated_at = ?
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                request.getRoleCode(),
                request.getRoleName(),
                request.getRoleType(),
                operatorId,
                LocalDateTime.now(),
                roleId,
                tenantId
        );
        return roleId;
    }

    private void replaceRolePermissions(Long tenantId, Long roleId, List<String> permissionKeys, Long operatorId) {
        jdbcTemplate.update(
                "delete from sys_role_permission where tenant_id = ? and role_id = ?",
                tenantId,
                roleId
        );
        if (CollectionUtils.isEmpty(permissionKeys)) {
            return;
        }
        for (String permissionKey : new LinkedHashSet<>(permissionKeys)) {
            jdbcTemplate.update(
                    """
                            insert into sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    roleId,
                    permissionKey,
                    operatorId,
                    operatorId
            );
        }
    }

    private Long insertMenu(Long menuId, Long tenantId, SystemDTO.MenuUpsertRequest request, Long operatorId) {
        if (menuId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_menu (
                                tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no,
                                permission_key, status, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    request.getParentId() == null ? 0L : request.getParentId(),
                    request.getMenuCode(),
                    request.getMenuName(),
                    request.getMenuType(),
                    request.getPath(),
                    request.getComponent(),
                    request.getIcon(),
                    request.getSortNo() == null ? 0 : request.getSortNo(),
                    request.getPermissionKey(),
                    request.getStatus(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject(
                    "select id from sys_menu where tenant_id = ? and menu_code = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    tenantId,
                    request.getMenuCode()
            );
        }
        jdbcTemplate.update(
                """
                        update sys_menu
                        set parent_id = ?, menu_code = ?, menu_name = ?, menu_type = ?, path = ?, component = ?,
                            icon = ?, sort_no = ?, permission_key = ?, status = ?, updated_by = ?, updated_at = ?
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                request.getParentId() == null ? 0L : request.getParentId(),
                request.getMenuCode(),
                request.getMenuName(),
                request.getMenuType(),
                request.getPath(),
                request.getComponent(),
                request.getIcon(),
                request.getSortNo() == null ? 0 : request.getSortNo(),
                request.getPermissionKey(),
                request.getStatus(),
                operatorId,
                LocalDateTime.now(),
                menuId,
                tenantId
        );
        return menuId;
    }

    private Long upsertDictType(Long id, Long tenantId, SystemDTO.DictTypeUpsertRequest request, Long operatorId) {
        if (id == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_dict_type (tenant_id, dict_code, dict_name, status, is_system, remark, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, 0, ?, ?, ?, 0)
                            """,
                    tenantId,
                    request.getDictCode(),
                    request.getDictName(),
                    request.getStatus(),
                    request.getRemark(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject(
                    "select id from sys_dict_type where tenant_id <=> ? and dict_code = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    tenantId,
                    request.getDictCode()
            );
        }
        jdbcTemplate.update(
                """
                        update sys_dict_type
                        set dict_code = ?, dict_name = ?, status = ?, remark = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                request.getDictCode(),
                request.getDictName(),
                request.getStatus(),
                request.getRemark(),
                operatorId,
                LocalDateTime.now(),
                id
        );
        return id;
    }

    private Long upsertDictItem(Long id, Long dictTypeId, SystemDTO.DictItemUpsertRequest request, Long operatorId) {
        if (id == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_dict_item (dict_type_id, item_label, item_value, sort_no, status, remark, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    dictTypeId,
                    request.getItemLabel(),
                    request.getItemValue(),
                    request.getSortNo(),
                    request.getStatus(),
                    request.getRemark(),
                    operatorId,
                    operatorId
            );
            return jdbcTemplate.queryForObject(
                    "select id from sys_dict_item where dict_type_id = ? and item_value = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    dictTypeId,
                    request.getItemValue()
            );
        }
        jdbcTemplate.update(
                """
                        update sys_dict_item
                        set item_label = ?, item_value = ?, sort_no = ?, status = ?, remark = ?, updated_by = ?, updated_at = ?
                        where id = ? and dict_type_id = ? and deleted = 0
                        """,
                request.getItemLabel(),
                request.getItemValue(),
                request.getSortNo(),
                request.getStatus(),
                request.getRemark(),
                operatorId,
                LocalDateTime.now(),
                id,
                dictTypeId
        );
        return id;
    }

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = pageSize <= 0 ? 10 : pageSize;
        long offset = (safePageNo - 1) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        String pagedSql = selectSql + " limit ? offset ?";
        List<T> records = jdbcTemplate.query(pagedSql, new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0 : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private List<SystemVO.MenuVO> buildMenuTree(List<SystemVO.MenuVO> flatMenus) {
        Map<Long, SystemVO.MenuVO> index = new LinkedHashMap<>();
        List<SystemVO.MenuVO> roots = new ArrayList<>();
        for (SystemVO.MenuVO menu : flatMenus) {
            menu.setChildren(new ArrayList<>());
            index.put(menu.getId(), menu);
        }
        for (SystemVO.MenuVO menu : flatMenus) {
            Long parentId = menu.getParentId();
            if (parentId == null || parentId == 0 || !index.containsKey(parentId)) {
                roots.add(menu);
                continue;
            }
            index.get(parentId).getChildren().add(menu);
        }
        Comparator<SystemVO.MenuVO> comparator = Comparator.comparingInt(item -> item.getSortNo() == null ? 0 : item.getSortNo());
        roots.sort(comparator);
        for (SystemVO.MenuVO root : roots) {
            sortChildren(root, comparator);
        }
        return roots;
    }

    private void sortChildren(SystemVO.MenuVO menu, Comparator<SystemVO.MenuVO> comparator) {
        if (menu.getChildren() == null || menu.getChildren().isEmpty()) {
            return;
        }
        menu.getChildren().sort(comparator);
        for (SystemVO.MenuVO child : menu.getChildren()) {
            sortChildren(child, comparator);
        }
    }

    private List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return List.of(csv.split(","));
    }

    private void copyUser(SystemVO.UserDetailVO target, SystemVO.UserVO source) {
        target.setId(source.getId());
        target.setUsername(source.getUsername());
        target.setMobile(source.getMobile());
        target.setNickname(source.getNickname());
        target.setRealName(source.getRealName());
        target.setAvatarUrl(source.getAvatarUrl());
        target.setEmail(source.getEmail());
        target.setStatus(source.getStatus());
        target.setTenantNames(source.getTenantNames());
        target.setRoleNames(source.getRoleNames());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private void copyRole(SystemVO.RoleDetailVO target, SystemVO.RoleVO source) {
        target.setId(source.getId());
        target.setTenantId(source.getTenantId());
        target.setRoleCode(source.getRoleCode());
        target.setRoleName(source.getRoleName());
        target.setRoleType(source.getRoleType());
        target.setPermissionCount(source.getPermissionCount());
        target.setUserCount(source.getUserCount());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private static SystemVO.ShortcutVO shortcut(String title, String description, String path, String permission) {
        SystemVO.ShortcutVO shortcut = new SystemVO.ShortcutVO();
        shortcut.setTitle(title);
        shortcut.setDescription(description);
        shortcut.setPath(path);
        shortcut.setPermission(permission);
        return shortcut;
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private LocalDateTime parseDateTime(String value) {
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private <T> T queryOne(String sql, Class<T> voClass, Object... params) {
        try {
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(voClass), params);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }
}
