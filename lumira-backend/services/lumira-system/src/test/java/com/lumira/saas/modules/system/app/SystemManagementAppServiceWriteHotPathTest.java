package com.lumira.saas.modules.system.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import com.lumira.saas.modules.system.app.OnlineSessionManagementAppService;
import com.lumira.saas.modules.system.app.SystemPlatformSettingsAppService;
import com.lumira.saas.modules.system.app.SystemProfileSettingsAppService;
import com.lumira.saas.modules.system.plugin.SystemPluginViewService;
import com.lumira.saas.modules.system.role.app.SystemRoleManagementAppService;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.user.domain.UserDomainService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemManagementAppServiceWriteHotPathTest {

    @Test
    void createMenuShouldUseLastInsertId() {
        TestEnvironment env = new TestEnvironment();

        SystemVO.MenuVO menu = env.service.createMenu(buildCurrentUser(), menuRequest());

        assertEquals(901L, menu.getId());
        assertEquals("settings.menu", menu.getMenuCode());
        assertEquals(1, env.jdbcTemplate.lastInsertIdQueries);
    }

    @Test
    void createMenuShouldInvalidateCachedMenuCount() {
        TestEnvironment env = new TestEnvironment();

        assertEquals(12, env.service.countMenus());
        assertEquals(12, env.service.countMenus());
        assertEquals(1, env.jdbcTemplate.menuCountQueries);

        env.service.createMenu(buildCurrentUser(), menuRequest());
        assertEquals(13, env.service.countMenus());

        assertEquals(2, env.jdbcTemplate.menuCountQueries);
    }

    @Test
    void listMenusShouldReuseVersionedCacheUntilReadModelVersionCacheExpires() throws InterruptedException {
        TestEnvironment env = new TestEnvironment();

        assertEquals(1, env.service.listMenus(buildCurrentUser()).size());
        assertEquals(1, env.service.listMenus(buildCurrentUser()).size());
        assertEquals(1, env.jdbcTemplate.menuTreeQueries);

        env.jdbcTemplate.menuTreeVersion = 2L;
        Thread.sleep(2100L);

        assertEquals(1, env.service.listMenus(buildCurrentUser()).size());
        assertEquals(2, env.jdbcTemplate.menuTreeQueries);
    }

    @Test
    void listPermissionTreeShouldReuseVersionedPermissionCatalogUntilVersionChanges() {
        TestEnvironment env = new TestEnvironment();
        when(env.permissionSnapshotService.currentPermissionSnapshotVersion()).thenReturn("v1", "v1", "v2");

        assertEquals(1, env.service.listPermissionTree(buildCurrentUser()).size());
        assertEquals(1, env.service.listPermissionTree(buildCurrentUser()).size());
        assertEquals(1, env.jdbcTemplate.menuTreeQueries);
        assertEquals(1, env.jdbcTemplate.permissionCatalogQueries);

        assertEquals(1, env.service.listPermissionTree(buildCurrentUser()).size());
        assertEquals(1, env.jdbcTemplate.menuTreeQueries);
        assertEquals(2, env.jdbcTemplate.permissionCatalogQueries);
    }

    @Test
    void createMenuShouldBumpMenuReadModelVersionAndInvalidateVersionedMenuTreeCache() {
        TestEnvironment env = new TestEnvironment();

        assertEquals(1, env.service.listMenus(buildCurrentUser()).size());
        assertEquals(1, env.jdbcTemplate.menuTreeQueries);

        env.service.createMenu(buildCurrentUser(), menuRequest());
        assertEquals(1, env.jdbcTemplate.readModelVersionBumps);

        assertEquals(1, env.service.listMenus(buildCurrentUser()).size());
        assertEquals(2, env.jdbcTemplate.menuTreeQueries);
    }

    @Test
    void createDictTypeShouldUseLastInsertId() {
        TestEnvironment env = new TestEnvironment();

        SystemVO.DictTypeVO dictType = env.service.createDictType(buildCurrentUser(), dictTypeRequest());

        assertEquals(901L, dictType.getId());
        assertEquals("platform.theme", dictType.getDictCode());
        assertEquals(1, env.jdbcTemplate.lastInsertIdQueries);
    }

    @Test
    void createDictItemShouldUseLastInsertId() {
        TestEnvironment env = new TestEnvironment();

        SystemVO.DictItemVO dictItem = env.service.createDictItem(buildCurrentUser(), 77L, dictItemRequest());

        assertEquals(901L, dictItem.getId());
        assertEquals("暗色", dictItem.getItemLabel());
        assertEquals(1, env.jdbcTemplate.lastInsertIdQueries);
    }

    private static CurrentUser buildCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUsername("admin");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of("*"));
        return currentUser;
    }

    private static SystemDTO.MenuUpsertRequest menuRequest() {
        SystemDTO.MenuUpsertRequest request = new SystemDTO.MenuUpsertRequest();
        request.setMenuCode("settings.menu");
        request.setMenuName("菜单管理");
        request.setMenuType("PAGE");
        request.setPath("/custom/menus");
        request.setComponent("CustomMenuPage");
        request.setIcon("menu");
        request.setSortNo(10);
        request.setPermissionKey("system:menu:view");
        request.setStatus("ENABLED");
        return request;
    }

    private static SystemDTO.DictTypeUpsertRequest dictTypeRequest() {
        SystemDTO.DictTypeUpsertRequest request = new SystemDTO.DictTypeUpsertRequest();
        request.setDictCode("platform.theme");
        request.setDictName("主题");
        request.setStatus("ENABLED");
        request.setRemark("站点主题");
        return request;
    }

    private static SystemDTO.DictItemUpsertRequest dictItemRequest() {
        SystemDTO.DictItemUpsertRequest request = new SystemDTO.DictItemUpsertRequest();
        request.setItemLabel("暗色");
        request.setItemValue("dark");
        request.setSortNo(1);
        request.setStatus("ENABLED");
        request.setRemark("暗色主题");
        return request;
    }

    private static final class TestEnvironment {
        private final RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        private final PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        private final SystemManagementAppService service = new SystemManagementAppService(
                jdbcTemplate,
                mock(UserDomainService.class),
                permissionSnapshotService,
                mock(SystemPluginViewService.class),
                mock(OnlineSessionManagementAppService.class),
                mock(SystemVerificationAppService.class),
                mock(SystemPlatformSettingsAppService.class),
                mock(SystemProfileSettingsAppService.class),
                mock(PasswordEncoder.class),
                mock(AuthSessionStore.class),
                mock(LoginAuditService.class),
                mock(OperationAuditService.class),
                mock(SecuritySettingsService.class),
                mock(PasswordPolicyService.class),
                mock(IamUserService.class),
                mock(SystemUserManagementAppService.class),
                mock(SystemRoleManagementAppService.class),
                mock(FieldCryptoService.class)
        );

        private TestEnvironment() {
            when(permissionSnapshotService.currentPermissionSnapshotVersion()).thenReturn("v1");
        }
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private int lastInsertIdQueries;
        private Long menuId;
        private String menuCode;
        private String menuName;
        private String menuType;
        private String menuPath;
        private String menuComponent;
        private String menuIcon;
        private Integer menuSortNo;
        private String menuPermissionKey;
        private String menuStatus;
        private int menuCount = 12;
        private int menuCountQueries;
        private long menuTreeVersion = 1L;
        private int readModelVersionBumps;
        private int menuTreeQueries;
        private int permissionCatalogQueries;
        private Long dictTypeId;
        private String dictTypeCode;
        private String dictTypeName;
        private String dictTypeStatus;
        private String dictTypeRemark;
        private Long dictItemId;
        private Long dictItemDictTypeId;
        private String dictItemLabel;
        private String dictItemValue;
        private Integer dictItemSortNo;
        private String dictItemStatus;
        private String dictItemRemark;

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("insert into sys_menu")) {
                menuCode = (String) args[1];
                menuName = (String) args[2];
                menuType = (String) args[3];
                menuPath = (String) args[4];
                menuComponent = (String) args[5];
                menuIcon = (String) args[6];
                menuSortNo = (Integer) args[7];
                menuPermissionKey = (String) args[8];
                menuStatus = (String) args[9];
                menuId = 901L;
                if ("ENABLED".equals(menuStatus)) {
                    menuCount += 1;
                }
            }
            if (sql.contains("insert into ddd_read_model_version")) {
                readModelVersionBumps += 1;
                menuTreeVersion += 1;
            }
            if (sql.contains("insert into sys_dict_type")) {
                dictTypeCode = (String) args[0];
                dictTypeName = (String) args[1];
                dictTypeStatus = (String) args[2];
                dictTypeRemark = (String) args[3];
                dictTypeId = 901L;
            }
            if (sql.contains("insert into sys_dict_item")) {
                dictItemDictTypeId = (Long) args[0];
                dictItemLabel = (String) args[1];
                dictItemValue = (String) args[2];
                dictItemSortNo = (Integer) args[3];
                dictItemStatus = (String) args[4];
                dictItemRemark = (String) args[5];
                dictItemId = 901L;
            }
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("select last_insert_id()")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(901L);
            }
            if (sql.contains("from ddd_read_model_version")) {
                return requiredType.cast(Long.valueOf(menuTreeVersion));
            }
            if (sql.contains("select count(1) from sys_menu")) {
                menuCountQueries += 1;
                return requiredType.cast(Long.valueOf(menuCount));
            }
            throw new EmptyResultDataAccessException(1);
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from sys_menu")) {
                SystemVO.MenuVO menu = new SystemVO.MenuVO();
                menu.setId(menuId == null ? 901L : menuId);
                menu.setMenuCode(menuCode);
                menu.setMenuName(menuName);
                menu.setMenuType(menuType);
                menu.setPath(menuPath);
                menu.setComponent(menuComponent);
                menu.setIcon(menuIcon);
                menu.setSortNo(menuSortNo);
                menu.setPermissionKey(menuPermissionKey);
                menu.setStatus(menuStatus);
                return (T) menu;
            }
            if (sql.contains("from sys_dict_type")) {
                Long requestedId = args.length > 0 && args[0] instanceof Long ? (Long) args[0] : null;
                SystemVO.DictTypeVO dictType = new SystemVO.DictTypeVO();
                if (requestedId != null && requestedId.equals(77L)) {
                    dictType.setId(77L);
                    dictType.setDictCode("platform.theme");
                    dictType.setDictName("主题");
                } else {
                    dictType.setId(dictTypeId == null ? 901L : dictTypeId);
                    dictType.setDictCode(dictTypeCode);
                    dictType.setDictName(dictTypeName);
                    dictType.setStatus(dictTypeStatus);
                    dictType.setRemark(dictTypeRemark);
                }
                return (T) dictType;
            }
            if (sql.contains("from sys_dict_item")) {
                SystemVO.DictItemVO dictItem = new SystemVO.DictItemVO();
                dictItem.setId(dictItemId == null ? 901L : dictItemId);
                dictItem.setDictTypeId(dictItemDictTypeId);
                dictItem.setItemLabel(dictItemLabel);
                dictItem.setItemValue(dictItemValue);
                dictItem.setSortNo(dictItemSortNo);
                dictItem.setStatus(dictItemStatus);
                dictItem.setRemark(dictItemRemark);
                return (T) dictItem;
            }
            throw new EmptyResultDataAccessException(1);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            String normalized = sql.toLowerCase();
            if (normalized.contains("from sys_menu") && normalized.contains("order by sort_no asc, id asc")) {
                menuTreeQueries += 1;
                SystemVO.MenuVO menu = new SystemVO.MenuVO();
                menu.setId(menuId == null ? 901L : menuId);
                menu.setParentId(0L);
                menu.setMenuCode(menuCode == null ? "settings.menu" : menuCode);
                menu.setMenuName(menuName == null ? "菜单管理" : menuName);
                menu.setMenuType(menuType == null ? "PAGE" : menuType);
                menu.setPath(menuPath == null ? "/custom/menus" : menuPath);
                menu.setComponent(menuComponent == null ? "CustomMenuPage" : menuComponent);
                menu.setIcon(menuIcon == null ? "menu" : menuIcon);
                menu.setSortNo(menuSortNo == null ? 10 : menuSortNo);
                menu.setPermissionKey(menuPermissionKey == null ? "system:menu:view" : menuPermissionKey);
                menu.setStatus(menuStatus == null ? "ENABLED" : menuStatus);
                return List.of((T) menu);
            }
            if (normalized.contains("from sys_permission")) {
                permissionCatalogQueries += 1;
                SystemVO.PermissionVO pagePermission = new SystemVO.PermissionVO();
                pagePermission.setPermissionKey("system:menu:view");
                pagePermission.setPermissionName("菜单查看");
                pagePermission.setPermissionGroup("system");
                pagePermission.setSourceType("SYSTEM");
                SystemVO.PermissionVO actionPermission = new SystemVO.PermissionVO();
                actionPermission.setPermissionKey("system:menu:update");
                actionPermission.setPermissionName("菜单更新");
                actionPermission.setPermissionGroup("system");
                actionPermission.setSourceType("SYSTEM");
                return List.of((T) pagePermission, (T) actionPermission);
            }
            return List.of();
        }
    }
}
