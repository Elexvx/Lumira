package com.lumira.saas.modules.system.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemManagementAppServiceWriteHotPathTest {

    @Test
    void dictWritesShouldPersistTrustedUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemManagementAppService.java"));

        assertTrue(source.contains("insert into sys_dict_type (dict_code, dict_name, status, is_system, remark, created_by, created_by_uuid, updated_by, updated_by_uuid"));
        assertTrue(source.contains("insert into sys_dict_item (dict_type_id, item_label, item_value, sort_no, status, remark, created_by, created_by_uuid, updated_by, updated_by_uuid"));
        assertTrue(source.contains("update sys_dict_type"));
        assertTrue(source.contains("updated_by_uuid = ?"));
        assertTrue(source.contains("requireSystemWrite(updated, \"Dict type changed, please retry\")"));
        assertTrue(source.contains("requireSystemWrite(updated, \"Dict item changed, please retry\")"));
        assertTrue(source.contains("requireSystemWrite(inserted, \"Dict type changed, please retry\")"));
        assertTrue(source.contains("requireSystemWrite(inserted, \"Dict item changed, please retry\")"));
        assertTrue(source.contains("requireSystemWrite(typeDeleted, \"Dict type changed, please retry\")"));
    }

    @Test
    void configUpdateShouldBindOriginalConfigKeyAndScope() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemManagementAppService.java"));

        assertTrue(source.contains("SystemVO.ConfigVO currentConfig = loadConfig(id)"));
        assertTrue(source.contains("and config_key = ?"));
        assertTrue(source.contains("and config_scope = 'PLATFORM'"));
        assertTrue(source.contains("and is_system = 0"));
        assertTrue(source.contains("and deleted = 0"));
        assertTrue(source.contains("Config changed, please retry"));
        assertTrue(source.contains("requireSystemWrite(inserted, \"Config changed, please retry\")"));
        assertTrue(source.contains("resolveStoredConfigValue(id, currentConfig.getConfigKey(), request.getConfigKey(), request.getConfigValue())"));
    }

    @Test
    void menuWritesShouldPersistTrustedUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemManagementAppService.java"));

        assertTrue(source.contains("permission_key, status, created_by, created_by_uuid, updated_by, updated_by_uuid"));
        assertTrue(source.contains("set parent_id = ?, sort_no = ?, updated_by = ?, updated_by_uuid = ?"));
        assertTrue(source.contains("where id = ? and menu_code = ? and menu_type = ? and deleted = 0"));
        assertTrue(source.contains("menu.getMenuCode()"));
        assertTrue(source.contains("editableMenu.getMenuCode()"));
        assertTrue(source.contains("requireSystemWrite(updated, \"Menu changed, please retry\")"));
        assertTrue(source.contains("requireSystemWrite(inserted, \"Menu changed, please retry\")"));
    }

    @Test
    void dictTypeWritesShouldBindOriginalCodeAndSystemFlag() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemManagementAppService.java"));

        assertTrue(source.contains("SystemVO.DictTypeVO existingType = loadDictType(id)"));
        assertTrue(source.contains("int typeDeleted = jdbcTemplate.update("));
        assertTrue(source.contains("requireSystemWrite(typeDeleted, \"Dict type changed, please retry\")"));
        assertTrue(source.contains("where dict_type_id = ? and deleted = 0"));
        assertTrue(source.contains("where id = ? and dict_code = ? and is_system = ? and deleted = 0"));
        assertTrue(!source.contains("where t.id = sys_dict_item.dict_type_id"));
    }

    @Test
    void dictItemWritesShouldBindOriginalValueAndStatus() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemManagementAppService.java"));

        assertTrue(source.contains("SystemVO.DictItemVO existingItem = loadDictItem(dictTypeId, itemId)"));
        assertTrue(source.contains("int deleted = jdbcTemplate.update("));
        assertTrue(source.contains("requireSystemWrite(deleted, \"Dict item changed, please retry\")"));
        assertTrue(source.contains("where id = ? and dict_type_id = ? and item_value = ? and status = ? and deleted = 0"));
        assertTrue(source.contains("existingItem == null ? null : existingItem.getItemValue()"));
        assertTrue(source.contains("item.getItemValue()"));
    }

    @Test
    void currentUserProfileUpsertsShouldNotRewriteUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemManagementAppService.java"));

        assertTrue(source.contains("locale = case when user_id = values(user_id) and user_uuid = values(user_uuid)"));
        assertTrue(source.contains("extra_json = case when user_id = values(user_id) and user_uuid = values(user_uuid)"));
        assertTrue(source.contains("requireSystemWrite(updated, \"User profile changed, please retry\")"));
        assertTrue(!source.contains("user_uuid = values(user_uuid),"));
    }

    @Test
    void userRoleReplacementShouldSoftDeleteAndBindTargetRoleContext() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemManagementAppService.java"));

        assertTrue(!source.contains("delete from sys_user_role where user_id = ? and user_uuid = ?"));
        assertTrue(source.contains("update sys_user_role"));
        assertTrue(source.contains("updated_by_uuid = ?"));
        assertTrue(source.contains("from sys_role r"));
        assertTrue(source.contains("where r.id = ? and r.deleted = 0"));
        assertTrue(source.contains("requireSystemWrite(inserted, \"Role changed, please retry\")"));
        assertTrue(source.contains("requireSystemWrite(inserted, \"User changed, please retry\")"));
        assertTrue(source.contains("requireSystemWrite(updated, \"User changed, please retry\")"));
        assertTrue(source.contains("requireSystemWrite(passwordUpdated, \"User changed, please retry\")"));
    }

    @Test
    void createMenuShouldUseLastInsertId() {
        TestEnvironment env = new TestEnvironment();

        SystemVO.MenuVO menu = env.service.createMenu(buildCurrentUser(), menuRequest());

        assertEquals(901L, menu.getId());
        assertEquals("settings.menu", menu.getMenuCode());
        assertEquals(1, env.jdbcTemplate.lastInsertIdQueries);
    }

    @Test
    void createMenuShouldRejectWhenInsertMissesBeforeLastInsertId() {
        TestEnvironment env = new TestEnvironment();
        env.jdbcTemplate.updateResult = 0;

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.createMenu(buildCurrentUser(), menuRequest())
        );

        assertEquals(ErrorCode.BIZ_ERROR, error.getErrorCode());
        assertTrue(error.getMessage().contains("Menu changed, please retry"));
        assertEquals(0, env.jdbcTemplate.lastInsertIdQueries);
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
    void createMenuShouldRequireCreatePermissionBeforeDatabaseWrite() {
        TestEnvironment env = new TestEnvironment();

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.createMenu(buildCurrentUser("system:menu:view"), menuRequest())
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, env.jdbcTemplate.updateCalls);
    }

    @Test
    void createMenuShouldAcceptCreatePermissionWithoutSeparateViewPermission() {
        TestEnvironment env = new TestEnvironment();

        SystemVO.MenuVO menu = env.service.createMenu(buildCurrentUser("system:menu:create"), menuRequest());

        assertEquals(901L, menu.getId());
        assertEquals(1, env.jdbcTemplate.lastInsertIdQueries);
    }

    @Test
    void createMenuShouldRejectMissingSessionVersionBeforeDatabaseWrite() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setSessionVersion(null);

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.createMenu(currentUser, menuRequest())
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals(0, env.jdbcTemplate.updateCalls);
    }

    @Test
    void menuWritesShouldRejectInvalidInputBeforeDatabaseWrite() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();

        BizException createError = assertThrows(
                BizException.class,
                () -> env.service.createMenu(currentUser, null)
        );
        BizException updateRequestError = assertThrows(
                BizException.class,
                () -> env.service.updateMenu(currentUser, 901L, null)
        );
        BizException updateIdError = assertThrows(
                BizException.class,
                () -> env.service.updateMenu(currentUser, 0L, menuRequest())
        );
        BizException statusIdError = assertThrows(
                BizException.class,
                () -> env.service.updateMenuStatus(currentUser, 0L, "DISABLED")
        );
        BizException deleteIdError = assertThrows(
                BizException.class,
                () -> env.service.deleteMenu(currentUser, 0L)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, createError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, updateRequestError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, updateIdError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, statusIdError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, deleteIdError.getErrorCode());
        assertEquals(0, env.jdbcTemplate.updateCalls);
        assertEquals(0, env.jdbcTemplate.readModelVersionBumps);
    }

    @Test
    void updateMenuStatusShouldRequireStatusPermissionBeforeDatabaseWrite() {
        TestEnvironment env = new TestEnvironment();

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.updateMenuStatus(buildCurrentUser("system:menu:update"), 901L, "DISABLED")
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, env.jdbcTemplate.updateCalls);
    }

    @Test
    void updateMenuStatusShouldAcceptStatusPermissionWithoutUpdatePermission() {
        TestEnvironment env = new TestEnvironment();

        assertDoesNotThrow(() -> env.service.updateMenuStatus(buildCurrentUser("system:menu:status"), 901L, "DISABLED"));
        assertTrue(env.jdbcTemplate.updateCalls > 0);
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
        assertEquals(1, env.jdbcTemplate.pluginMenuQueries);
        assertEquals(1, env.jdbcTemplate.permissionCatalogQueries);

        assertEquals(1, env.service.listPermissionTree(buildCurrentUser()).size());
        assertEquals(1, env.jdbcTemplate.menuTreeQueries);
        assertEquals(2, env.jdbcTemplate.pluginMenuQueries);
        assertEquals(2, env.jdbcTemplate.permissionCatalogQueries);
    }

    @Test
    void listPermissionsShouldRequireRoleViewBeforePermissionCatalogRead() {
        TestEnvironment env = new TestEnvironment();

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.listPermissions(buildCurrentUser("system:menu:view"))
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, env.jdbcTemplate.permissionCatalogQueries);
    }

    @Test
    void listMenusShouldRequireMenuViewBeforeDatabaseRead() {
        TestEnvironment env = new TestEnvironment();

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.listMenus(buildCurrentUser("system:role:view"))
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, env.jdbcTemplate.menuTreeQueries);
    }

    @Test
    void getDictTypeShouldRequireViewPermissionBeforeDatabaseRead() {
        TestEnvironment env = new TestEnvironment();

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.getDictType(buildCurrentUser("system:dict:update"), 77L)
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, env.jdbcTemplate.rowMapperQueryForObjectCalls);
    }

    @Test
    void getConfigShouldRequireViewPermissionBeforeDatabaseRead() {
        TestEnvironment env = new TestEnvironment();

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.getConfig(buildCurrentUser("system:config:update"), 901L)
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, env.jdbcTemplate.rowMapperQueryForObjectCalls);
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
    void createDictTypeShouldRejectWhenInsertMissesBeforeLastInsertId() {
        TestEnvironment env = new TestEnvironment();
        env.jdbcTemplate.updateResult = 0;

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.createDictType(buildCurrentUser(), dictTypeRequest())
        );

        assertEquals(ErrorCode.BIZ_ERROR, error.getErrorCode());
        assertTrue(error.getMessage().contains("Dict type changed, please retry"));
        assertEquals(0, env.jdbcTemplate.lastInsertIdQueries);
    }

    @Test
    void createDictTypeShouldRequireCreatePermissionBeforeDatabaseWrite() {
        TestEnvironment env = new TestEnvironment();

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.createDictType(buildCurrentUser("system:dict:view"), dictTypeRequest())
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, env.jdbcTemplate.updateCalls);
    }

    @Test
    void createDictItemShouldUseLastInsertId() {
        TestEnvironment env = new TestEnvironment();

        SystemVO.DictItemVO dictItem = env.service.createDictItem(buildCurrentUser(), 77L, dictItemRequest());

        assertEquals(901L, dictItem.getId());
        assertEquals("暗色", dictItem.getItemLabel());
        assertEquals(1, env.jdbcTemplate.lastInsertIdQueries);
    }

    @Test
    void createDictItemShouldRejectWhenInsertMissesBeforeLastInsertId() {
        TestEnvironment env = new TestEnvironment();
        env.jdbcTemplate.updateResult = 0;

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.createDictItem(buildCurrentUser(), 77L, dictItemRequest())
        );

        assertEquals(ErrorCode.BIZ_ERROR, error.getErrorCode());
        assertTrue(error.getMessage().contains("Dict item changed, please retry"));
        assertEquals(0, env.jdbcTemplate.lastInsertIdQueries);
    }

    @Test
    void createDictItemShouldAcceptCreatePermissionWithoutSeparateViewPermission() {
        TestEnvironment env = new TestEnvironment();

        SystemVO.DictItemVO dictItem = env.service.createDictItem(buildCurrentUser("system:dict:create"), 77L, dictItemRequest());

        assertEquals(901L, dictItem.getId());
        assertEquals(1, env.jdbcTemplate.lastInsertIdQueries);
    }

    @Test
    void deleteDictTypeShouldRejectWhenPrimaryDeleteMissesBeforeCleaningItems() {
        TestEnvironment env = new TestEnvironment();
        env.jdbcTemplate.updateResults.add(0);

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.deleteDictType(buildCurrentUser(), 77L)
        );

        assertEquals(ErrorCode.BIZ_ERROR, error.getErrorCode());
        assertTrue(error.getMessage().contains("Dict type changed, please retry"));
        assertTrue(env.jdbcTemplate.deletedDictType);
        assertTrue(!env.jdbcTemplate.deletedDictItems);
    }

    @Test
    void deleteDictItemShouldRejectWhenFinalWriteMisses() {
        TestEnvironment env = new TestEnvironment();
        env.jdbcTemplate.updateResult = 0;

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.deleteDictItem(buildCurrentUser(), 77L, 901L)
        );

        assertEquals(ErrorCode.BIZ_ERROR, error.getErrorCode());
        assertTrue(error.getMessage().contains("Dict item changed, please retry"));
    }

    @Test
    void createConfigShouldRequireUpdatePermissionBeforeDatabaseWrite() {
        TestEnvironment env = new TestEnvironment();

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.createConfig(buildCurrentUser("system:config:view"), configRequest())
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, env.jdbcTemplate.updateCalls);
    }

    @Test
    void createConfigShouldRejectWhenInsertMissesBeforeLookup() {
        TestEnvironment env = new TestEnvironment();
        env.jdbcTemplate.updateResult = 0;

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.createConfig(buildCurrentUser(), configRequest())
        );

        assertEquals(ErrorCode.BIZ_ERROR, error.getErrorCode());
        assertTrue(error.getMessage().contains("Config changed, please retry"));
    }

    @Test
    void dictAndConfigWritesShouldRejectInvalidInputBeforeDatabaseWrite() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();

        BizException dictTypeCreateError = assertThrows(
                BizException.class,
                () -> env.service.createDictType(currentUser, null)
        );
        BizException dictTypeUpdateIdError = assertThrows(
                BizException.class,
                () -> env.service.updateDictType(currentUser, 0L, dictTypeRequest())
        );
        BizException dictTypeUpdateRequestError = assertThrows(
                BizException.class,
                () -> env.service.updateDictType(currentUser, 77L, null)
        );
        BizException dictTypeDeleteError = assertThrows(
                BizException.class,
                () -> env.service.deleteDictType(currentUser, 0L)
        );
        BizException dictItemCreateTypeError = assertThrows(
                BizException.class,
                () -> env.service.createDictItem(currentUser, 0L, dictItemRequest())
        );
        BizException dictItemCreateRequestError = assertThrows(
                BizException.class,
                () -> env.service.createDictItem(currentUser, 77L, null)
        );
        BizException dictItemUpdateItemError = assertThrows(
                BizException.class,
                () -> env.service.updateDictItem(currentUser, 77L, 0L, dictItemRequest())
        );
        BizException dictItemUpdateRequestError = assertThrows(
                BizException.class,
                () -> env.service.updateDictItem(currentUser, 77L, 901L, null)
        );
        BizException dictItemDeleteError = assertThrows(
                BizException.class,
                () -> env.service.deleteDictItem(currentUser, 77L, 0L)
        );
        BizException configCreateError = assertThrows(
                BizException.class,
                () -> env.service.createConfig(currentUser, null)
        );
        BizException configUpdateIdError = assertThrows(
                BizException.class,
                () -> env.service.updateConfig(currentUser, 0L, configRequest())
        );
        BizException configUpdateRequestError = assertThrows(
                BizException.class,
                () -> env.service.updateConfig(currentUser, 901L, null)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, dictTypeCreateError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, dictTypeUpdateIdError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, dictTypeUpdateRequestError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, dictTypeDeleteError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, dictItemCreateTypeError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, dictItemCreateRequestError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, dictItemUpdateItemError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, dictItemUpdateRequestError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, dictItemDeleteError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, configCreateError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, configUpdateIdError.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, configUpdateRequestError.getErrorCode());
        assertEquals(0, env.jdbcTemplate.updateCalls);
    }

    @Test
    void updateMenuStatusShouldRejectWhenLiveSnapshotRevokesStatusPermissionBeforeDatabaseWrite() {
        TestEnvironment env = new TestEnvironment();
        when(env.permissionSnapshotService.loadSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot(
                        "permissions-3",
                        Set.of("system:menu:update"),
                        Set.of(1L),
                        null,
                        Set.of(),
                        Set.of(),
                        List.of(),
                        "/dashboard/home"
                ));

        BizException error = assertThrows(
                BizException.class,
                () -> env.service.updateMenuStatus(buildCurrentUser("system:menu:status"), 901L, "DISABLED")
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, env.jdbcTemplate.updateCalls);
    }

    private static CurrentUser buildCurrentUser() {
        return buildCurrentUser("*");
    }

    private static CurrentUser buildCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid(userUuidForPermission(permission));
        currentUser.setUsername("admin");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private static String userUuidForPermission(String permission) {
        String normalized = permission == null ? "none" : permission.replace('*', 'A').replace(':', '_');
        return "user-uuid-1001-" + normalized;
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

    private static SystemDTO.ConfigUpsertRequest configRequest() {
        SystemDTO.ConfigUpsertRequest request = new SystemDTO.ConfigUpsertRequest();
        request.setConfigKey("site.name");
        request.setConfigName("Site name");
        request.setConfigValue("Lumira");
        request.setRemark("display name");
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
            when(permissionSnapshotService.isTrustedActiveUser(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
            when(permissionSnapshotService.loadSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> snapshotForPermission((String) invocation.getArgument(1)));
        }

        private PermissionSnapshotService.PermissionSnapshot snapshotForPermission(String userUuid) {
            String permission = "*";
            if (userUuid != null && userUuid.startsWith("user-uuid-1001-")) {
                permission = userUuid.substring("user-uuid-1001-".length()).replace('A', '*').replace('_', ':');
            }
            return new PermissionSnapshotService.PermissionSnapshot(
                    "permissions-2",
                    Set.of(permission),
                    Set.of(1L),
                    null,
                    Set.of(),
                    Set.of(),
                    List.of(),
                    "/dashboard/home"
            );
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
        private int pluginMenuQueries;
        private int permissionCatalogQueries;
        private int rowMapperQueryForObjectCalls;
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
        private int updateCalls;
        private int updateResult = 1;
        private final java.util.Deque<Integer> updateResults = new java.util.ArrayDeque<>();
        private boolean deletedDictItems;
        private boolean deletedDictType;

        @Override
        public int update(String sql, Object... args) {
            updateCalls += 1;
            String normalized = sql.toLowerCase();
            if (normalized.contains("update sys_dict_item") && normalized.contains("set deleted = 1")) {
                deletedDictItems = true;
            }
            if (normalized.contains("update sys_dict_type") && normalized.contains("set deleted = 1")) {
                deletedDictType = true;
            }
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
            return updateResults.isEmpty() ? updateResult : updateResults.removeFirst();
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
            rowMapperQueryForObjectCalls += 1;
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
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.toLowerCase().contains("from sys_plugin_menu_rel")) {
                pluginMenuQueries += 1;
                return List.of();
            }
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            String normalized = sql.toLowerCase();
            if (normalized.contains("from sys_menu") && normalized.contains("status = 'enabled'")) {
                menuCountQueries += 1;
                List<T> menus = new java.util.ArrayList<>();
                for (int i = 0; i < menuCount; i++) {
                    SystemVO.MenuVO menu = new SystemVO.MenuVO();
                    menu.setMenuCode("custom.menu." + i);
                    menu.setPath("/custom/menu-" + i);
                    menu.setComponent("CustomMenuPage");
                    menu.setPermissionKey("system:menu:view");
                    menus.add((T) menu);
                }
                return menus;
            }
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
