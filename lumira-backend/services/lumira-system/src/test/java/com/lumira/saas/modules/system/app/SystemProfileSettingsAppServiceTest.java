package com.lumira.saas.modules.system.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.profile.dto.ProfileFieldSettingItem;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionGroupVO;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionItemVO;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionSummaryVO;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemProfileSettingsAppServiceTest {

    @Test
    void profileFieldConfigWritesShouldPersistTrustedUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemProfileSettingsAppService.java"));

        assertTrue(source.contains("created_by, created_by_uuid, updated_by, updated_by_uuid"));
        assertTrue(source.contains("updated_by = ?, updated_by_uuid = ?"));
        assertTrue(source.contains("operatorUuid = currentUser.getUserUuid()"));
        assertTrue(source.contains("and config_key = ?"));
        assertTrue(source.contains("and config_scope = 'PLATFORM'"));
        assertTrue(source.contains("and is_system = 0"));
        assertTrue(source.contains("and deleted = 0"));
        assertFalse(source.contains("updated_at = ?, deleted = 0"));
        assertTrue(source.contains("Profile config changed, please retry"));
    }

    @Test
    void getProfileFieldSettingsShouldReuseCachedSnapshot() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of(
                "profile.field.mobile.visible", "true",
                "profile.field.mobile.weight", "22",
                "profile.field.email.visible", "false"
        ));
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        List<ProfileFieldSettingVO> first = service.getProfileFieldSettings(buildCurrentUser());
        List<ProfileFieldSettingVO> second = service.getProfileFieldSettings(buildCurrentUser());

        assertEquals("contact", findSetting(first, "mobile").getGroupKey());
        assertEquals("contact", findSetting(second, "mobile").getGroupKey());
        assertEquals(1, jdbcTemplate.queryForListCount());
    }

    @Test
    void getProfileFieldSettingsUsesConfiguredWeightsAndDefaults() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of(
                "profile.field.mobile.visible", "true",
                "profile.field.mobile.weight", "22",
                "profile.field.mobile.required", "true",
                "profile.field.mobile.sort", "77",
                "profile.field.system.overrides", "[{\"fieldKey\":\"mobile\",\"fieldLabel\":\"Contact phone\",\"fieldDescription\":\"Editable phone label\",\"placeholder\":\"Enter contact phone\",\"custom\":false}]",
                "profile.field.email.visible", "false"
        ));
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        List<ProfileFieldSettingVO> settings = service.getProfileFieldSettings(buildCurrentUser());

        ProfileFieldSettingVO mobile = findSetting(settings, "mobile");
        ProfileFieldSettingVO avatar = findSetting(settings, "avatarUrl");
        ProfileFieldSettingVO email = findSetting(settings, "email");

        assertTrue(mobile.getVisible());
        assertEquals(22, mobile.getWeight());
        assertTrue(mobile.getRequired());
        assertEquals(77, mobile.getSortNo());
        assertEquals("Contact phone", mobile.getFieldLabel());
        assertEquals("Editable phone label", mobile.getFieldDescription());
        assertEquals("Enter contact phone", mobile.getPlaceholder());
        assertEquals("contact", mobile.getGroupKey());
        assertTrue(avatar.getVisible());
        assertEquals(10, avatar.getWeight());
        assertFalse(email.getVisible());
        assertEquals(15, email.getWeight());
    }

    @Test
    void getProfileFieldSettingsForManagementShouldRequireViewPermissionBeforeDatabaseRead() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        BizException error = assertThrows(
                BizException.class,
                () -> service.getProfileFieldSettingsForManagement(buildCurrentUser("system:config:update"), "PROFILE")
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, jdbcTemplate.queryForListCount());
    }

    @Test
    void getProfileFieldSettingsForManagementShouldRejectWhenLiveSnapshotRevokesViewPermissionBeforeDatabaseRead() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        PermissionSnapshotService permissionSnapshotService = org.mockito.Mockito.mock(PermissionSnapshotService.class);
        org.mockito.Mockito.when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        org.mockito.Mockito.when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:update")));
        SystemProfileSettingsAppService service = newService(jdbcTemplate, permissionSnapshotService);

        BizException error = assertThrows(
                BizException.class,
                () -> service.getProfileFieldSettingsForManagement(buildCurrentUser("system:config:view"), "PROFILE")
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, jdbcTemplate.queryForListCount());
    }

    @Test
    void updateProfileFieldSettingsPersistsWeightAndVisibility() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        List<ProfileFieldSettingItem> items = new ArrayList<>();
        items.add(profileFieldSetting("avatarUrl", true, 12));
        ProfileFieldSettingItem mobile = profileFieldSetting("mobile", false, 20);
        mobile.setFieldLabel("Contact phone");
        mobile.setFieldDescription("Editable phone label");
        mobile.setPlaceholder("Enter contact phone");
        mobile.setRequired(true);
        mobile.setSortNo(77);
        items.add(mobile);
        request.setItems(items);

        service.updateProfileFieldSettings(buildCurrentUser(), request);

        assertTrue(jdbcTemplate.insertedConfigKeys().contains("profile.field.avatar.visible"));
        assertTrue(jdbcTemplate.insertedConfigKeys().contains("profile.field.avatar.weight"));
        assertTrue(jdbcTemplate.insertedConfigKeys().contains("profile.field.mobile.visible"));
        assertTrue(jdbcTemplate.insertedConfigKeys().contains("profile.field.mobile.weight"));
        assertTrue(jdbcTemplate.insertedConfigKeys().contains("profile.field.mobile.required"));
        assertTrue(jdbcTemplate.insertedConfigKeys().contains("profile.field.mobile.sort"));
        assertTrue(jdbcTemplate.insertedConfigKeys().contains("profile.field.system.overrides"));
        assertTrue(jdbcTemplate.hasInsertValue("profile.field.avatar.weight", "12"));
        assertTrue(jdbcTemplate.hasInsertValue("profile.field.mobile.visible", "false"));
        assertTrue(jdbcTemplate.hasInsertValue("profile.field.mobile.required", "true"));
        assertTrue(jdbcTemplate.hasInsertValue("profile.field.mobile.sort", "77"));
        assertTrue(jdbcTemplate.hasInsertValueContaining("profile.field.system.overrides", "\"fieldLabel\":\"Contact phone\""));
        assertTrue(jdbcTemplate.hasInsertValueContaining("profile.field.system.overrides", "\"placeholder\":\"Enter contact phone\""));
    }

    @Test
    void updateProfileFieldSettingsShouldRequireUpdatePermissionBeforeDatabaseWrite() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        request.setItems(List.of(profileFieldSetting("avatarUrl", true, 12)));

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateProfileFieldSettings(buildCurrentUser("system:config:view"), request)
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount());
    }

    @Test
    void updateProfileFieldSettingsShouldRejectWhenLiveSnapshotRevokesUpdatePermissionBeforeDatabaseWrite() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        PermissionSnapshotService permissionSnapshotService = org.mockito.Mockito.mock(PermissionSnapshotService.class);
        org.mockito.Mockito.when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        org.mockito.Mockito.when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:view")));
        SystemProfileSettingsAppService service = newService(jdbcTemplate, permissionSnapshotService);

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        request.setItems(List.of(profileFieldSetting("avatarUrl", true, 12)));

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateProfileFieldSettings(buildCurrentUser(), request)
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount());
    }

    @Test
    void updateProfileFieldSettingsShouldRejectRevokedSessionTicketBeforeDatabaseWrite() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        SessionAuthenticationService sessionAuthenticationService = org.mockito.Mockito.mock(SessionAuthenticationService.class);
        org.mockito.Mockito.when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        SystemProfileSettingsAppService service = newService(jdbcTemplate, null, sessionAuthenticationService);

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        request.setItems(List.of(profileFieldSetting("avatarUrl", true, 12)));

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateProfileFieldSettings(buildCurrentUser(), request)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount());
    }

    @Test
    void updateProfileFieldSettingsShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        SystemProfileSettingsAppService service = new SystemProfileSettingsAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                new RecordingOperationAuditService(),
                null,
                null,
                null
        );

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        request.setItems(List.of(profileFieldSetting("avatarUrl", true, 12)));

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateProfileFieldSettings(buildCurrentUser(), request)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount());
    }

    @Test
    void updateProfileFieldSettingsShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        PermissionSnapshotService permissionSnapshotService = org.mockito.Mockito.mock(PermissionSnapshotService.class);
        org.mockito.Mockito.when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        org.mockito.Mockito.when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        SystemProfileSettingsAppService service = new SystemProfileSettingsAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                new RecordingOperationAuditService(),
                permissionSnapshotService,
                null,
                null
        );

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        request.setItems(List.of(profileFieldSetting("avatarUrl", true, 12)));

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateProfileFieldSettings(buildCurrentUser(), request)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertTrue(error.getMessage().contains("Trusted user permission snapshot is unavailable"));
        assertEquals(0, jdbcTemplate.updateCount());
    }

    @Test
    void updateProfileFieldSettingsShouldRejectDisabledTrustedUserIdentityBeforeDatabaseWrite() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        PermissionSnapshotService permissionSnapshotService = org.mockito.Mockito.mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = org.mockito.Mockito.mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "DISABLED"));
        SystemProfileSettingsAppService service = newService(
                jdbcTemplate,
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingOperationAuditService()
        );

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        request.setItems(List.of(profileFieldSetting("avatarUrl", true, 12)));

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateProfileFieldSettings(buildCurrentUser(), request)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount());
    }

    @Test
    void updateProfileFieldSettingsShouldRejectBlankLiveUsernameBeforeDatabaseWrite() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        PermissionSnapshotService permissionSnapshotService = org.mockito.Mockito.mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = org.mockito.Mockito.mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "   ", "ENABLED"));
        SystemProfileSettingsAppService service = newService(
                jdbcTemplate,
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingOperationAuditService()
        );

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        request.setItems(List.of(profileFieldSetting("avatarUrl", true, 12)));

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateProfileFieldSettings(buildCurrentUser(), request)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertTrue(error.getMessage().contains("Trusted user username is unavailable"));
        assertEquals(0, jdbcTemplate.updateCount());
    }

    @Test
    void updateProfileFieldSettingsShouldRejectBlankUsernameBeforeDatabaseWrite() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        request.setItems(List.of(profileFieldSetting("avatarUrl", true, 12)));
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setUsername(" ");

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateProfileFieldSettings(currentUser, request)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount());
    }

    @Test
    void updateProfileFieldSettingsShouldRejectMissingSessionVersionBeforeDatabaseWrite() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        request.setItems(List.of(profileFieldSetting("avatarUrl", true, 12)));
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setSessionVersion(null);

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateProfileFieldSettings(currentUser, request)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount());
    }

    @Test
    void updateProfileFieldSettingsShouldLogRefreshedLiveUsername() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        PermissionSnapshotService permissionSnapshotService = org.mockito.Mockito.mock(PermissionSnapshotService.class);
        org.mockito.Mockito.when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        org.mockito.Mockito.when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:update")));
        SystemInternalApi systemInternalApi = org.mockito.Mockito.mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "  admin-live  ", "ENABLED"));
        RecordingOperationAuditService auditService = new RecordingOperationAuditService();
        SystemProfileSettingsAppService service = newService(
                jdbcTemplate,
                permissionSnapshotService,
                null,
                systemInternalApi,
                auditService
        );
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setUsername("admin-stale");

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        request.setItems(List.of(profileFieldSetting("avatarUrl", true, 12)));

        service.updateProfileFieldSettings(currentUser, request);

        assertEquals("admin-live", currentUser.getUsername());
        assertEquals("admin-live", auditService.username);
    }

    @Test
    void updateProfileFieldSettingsShouldInvalidateCachedSnapshot() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of(
                "profile.field.mobile.visible", "true",
                "profile.field.mobile.weight", "22",
                "profile.field.mobile.required", "true",
                "profile.field.email.visible", "false"
        ));
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        List<ProfileFieldSettingVO> before = service.getProfileFieldSettings(buildCurrentUser());
        assertTrue(findSetting(before, "mobile").getVisible());

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        List<ProfileFieldSettingItem> items = new ArrayList<>();
        items.add(profileFieldSetting("avatarUrl", true, 12));
        ProfileFieldSettingItem updatedMobile = profileFieldSetting("mobile", false, 20);
        updatedMobile.setRequired(true);
        updatedMobile.setSortNo(77);
        items.add(updatedMobile);
        request.setItems(items);

        service.updateProfileFieldSettings(buildCurrentUser(), request);

        List<ProfileFieldSettingVO> after = service.getProfileFieldSettings(buildCurrentUser());
        ProfileFieldSettingVO mobile = findSetting(after, "mobile");
        assertFalse(mobile.getVisible());
        assertEquals(20, mobile.getWeight());
        assertTrue(mobile.getRequired());
        assertEquals(77, mobile.getSortNo());
        assertEquals(2, jdbcTemplate.queryForListCount());
    }

    @Test
    void profileFieldSettingsSupportCustomDefinitions() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of(
                "profile.field.custom.definitions",
                "[{\"fieldKey\":\"school\",\"fieldLabel\":\"学校\",\"fieldDescription\":\"就读学校\",\"fieldType\":\"TEXT\",\"visible\":true,\"required\":true,\"weight\":8,\"groupLabel\":\"教育信息\",\"sortNo\":120,\"custom\":true}]"
        ));
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        List<ProfileFieldSettingVO> settings = service.getProfileFieldSettings(buildCurrentUser());

        ProfileFieldSettingVO school = findSetting(settings, "school");
        assertTrue(school.getCustom());
        assertEquals("学校", school.getFieldLabel());
        assertEquals("TEXT", school.getFieldType());
        assertEquals("教育信息", school.getGroupLabel());
        assertTrue(school.getRequired());
        assertEquals(8, school.getWeight());
    }

    @Test
    void profileFieldSettingsSupportTeamMemberPageDefinitions() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of(
                "team_member.field.custom.definitions",
                "[{\"fieldKey\":\"shirtSize\",\"fieldLabel\":\"Shirt size\",\"fieldType\":\"TEXT\",\"visible\":true,\"required\":true,\"sortNo\":120,\"custom\":true}]"
        ));
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        List<ProfileFieldSettingVO> teamMemberSettings = service.getProfileFieldSettingsForManagement(buildCurrentUser("system:config:view"), "TEAM_MEMBER");
        List<ProfileFieldSettingVO> profileSettings = service.getProfileFieldSettings(buildCurrentUser());

        ProfileFieldSettingVO memberName = findSetting(teamMemberSettings, "memberName");
        ProfileFieldSettingVO shirtSize = findSetting(teamMemberSettings, "shirtSize");
        assertEquals("TEAM_MEMBER", memberName.getPageKey());
        assertEquals("TEAM_MEMBER", shirtSize.getPageKey());
        assertTrue(shirtSize.getCustom());
        assertTrue(shirtSize.getRequired());
        assertTrue(profileSettings.stream().noneMatch(item -> "memberName".equals(item.getFieldKey())));
    }

    @Test
    void updateProfileFieldSettingsPersistsCustomDefinitionsAsJson() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        List<ProfileFieldSettingItem> items = new ArrayList<>();
        items.add(profileFieldSetting("avatarUrl", true, 12));
        ProfileFieldSettingItem school = profileFieldSetting("school", true, 8);
        school.setCustom(true);
        school.setFieldLabel("学校");
        school.setFieldType("TEXT");
        school.setRequired(true);
        school.setGroupLabel("教育信息");
        school.setSortNo(120);
        items.add(school);
        request.setItems(items);

        service.updateProfileFieldSettings(buildCurrentUser(), request);

        assertTrue(jdbcTemplate.insertedConfigKeys().contains("profile.field.custom.definitions"));
        assertTrue(jdbcTemplate.hasInsertValueContaining("profile.field.custom.definitions", "\"fieldKey\":\"school\""));
        assertTrue(jdbcTemplate.hasInsertValueContaining("profile.field.custom.definitions", "\"fieldLabel\":\"学校\""));
    }

    @Test
    void updateProfileFieldSettingsRejectsWhenConfigInsertMisses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        jdbcTemplate.updateResult = 0;
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        SystemDTO.ProfileFieldSettingsRequest request = new SystemDTO.ProfileFieldSettingsRequest();
        ProfileFieldSettingItem school = profileFieldSetting("school", true, 8);
        school.setCustom(true);
        school.setFieldLabel("School");
        school.setFieldType("TEXT");
        school.setSortNo(120);
        request.setItems(List.of(school));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateProfileFieldSettings(buildCurrentUser(), request)
        );

        assertEquals(ErrorCode.BIZ_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Profile config changed, please retry"));
        assertEquals(1, jdbcTemplate.updateCount());
    }

    @Test
    void buildProfileCompletionSummarySkipsHiddenFieldsAndComputesActions() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        SystemProfileSettingsAppService service = newService(jdbcTemplate);

        CurrentUserVO currentUser = new CurrentUserVO();
        currentUser.setUserId(2001L);
        currentUser.setUsername("admin");
        currentUser.setAvatarUrl("https://example.com/avatar.png");
        currentUser.setRealName("管理员");
        currentUser.setEmail("admin@example.com");

        List<ProfileFieldSettingVO> settings = List.of(
                profileFieldSettingVO("avatarUrl", true, 10, "基础资料"),
                profileFieldSettingVO("realName", true, 15, "基础资料"),
                profileFieldSettingVO("mobile", true, 20, "联系方式"),
                profileFieldSettingVO("email", false, 15, "联系方式"),
                profileFieldSettingVO("idCardNumber", true, 55, "证件信息")
        );

        ProfileCompletionSummaryVO summary = service.buildProfileCompletionSummary(currentUser, settings, false, true);

        assertEquals(25, summary.getScore());
        assertEquals(25, summary.getCompletionRate());
        assertEquals(100, summary.getMaxScore());
        assertEquals(100, summary.getTotalWeight());
        assertEquals(25, summary.getEarnedWeight());
        assertEquals(3, summary.getGroups().size());
        assertEquals(2, summary.getIncompleteItems().size());

        ProfileCompletionGroupVO contactGroup = summary.getGroups().stream()
                .filter(item -> "contact".equals(item.getGroupKey()))
                .findFirst()
                .orElseThrow();
        ProfileCompletionItemVO mobile = contactGroup.getItems().get(0);
        assertFalse(mobile.getCompleted());
        assertEquals(Boolean.FALSE, mobile.getActionAvailable());
        assertEquals("Disabled", mobile.getActionLabel());
        assertNotNull(mobile.getActionHint());
    }

    private static SystemProfileSettingsAppService newService(JdbcTemplate jdbcTemplate) {
        return newService(jdbcTemplate, null);
    }

    private static SystemProfileSettingsAppService newService(JdbcTemplate jdbcTemplate, PermissionSnapshotService permissionSnapshotService) {
        return newService(jdbcTemplate, permissionSnapshotService, null);
    }

    private static SystemProfileSettingsAppService newService(
            JdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        return newService(jdbcTemplate, permissionSnapshotService, sessionAuthenticationService, null, new RecordingOperationAuditService());
    }

    private static SystemProfileSettingsAppService newService(
            JdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
            SystemInternalApi systemInternalApi,
            RecordingOperationAuditService auditService
    ) {
        if (systemInternalApi == null && sessionAuthenticationService == null) {
            if (permissionSnapshotService == null) {
                return new SystemProfileSettingsAppService(
                        new MyBatisQueryOperations(jdbcTemplate),
                        auditService
                );
            }
            return new SystemProfileSettingsAppService(
                    new MyBatisQueryOperations(jdbcTemplate),
                    auditService,
                    permissionSnapshotService
            );
        }
        if (systemInternalApi == null) {
            return new SystemProfileSettingsAppService(
                    new MyBatisQueryOperations(jdbcTemplate),
                    auditService,
                    permissionSnapshotService,
                    sessionAuthenticationService
            );
        }
        return new SystemProfileSettingsAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                auditService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService
        );
    }

    private static CurrentUser buildCurrentUser() {
        return buildCurrentUser("system:config:update");
    }

    private static CurrentUser buildCurrentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private static ProfileFieldSettingItem profileFieldSetting(String fieldKey, boolean visible, int weight) {
        ProfileFieldSettingItem item = new ProfileFieldSettingItem();
        item.setFieldKey(fieldKey);
        item.setVisible(visible);
        item.setWeight(weight);
        return item;
    }

    private static ProfileFieldSettingVO profileFieldSettingVO(String fieldKey, boolean visible, int weight, String groupLabel) {
        ProfileFieldSettingVO item = new ProfileFieldSettingVO();
        item.setFieldKey(fieldKey);
        item.setVisible(visible);
        item.setWeight(weight);
        item.setGroupLabel(groupLabel);
        return item;
    }

    private static ProfileFieldSettingVO findSetting(List<ProfileFieldSettingVO> settings, String fieldKey) {
        return settings.stream().filter(item -> fieldKey.equals(item.getFieldKey())).findFirst().orElseThrow();
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(Map.of());
        PermissionSnapshotService permissionSnapshotService = org.mockito.Mockito.mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = org.mockito.Mockito.mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "ENABLED"));
        org.mockito.Mockito.when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        org.mockito.Mockito.when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:update")));
        SystemProfileSettingsAppService service = newService(
                jdbcTemplate,
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingOperationAuditService()
        );
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setSimulatedRoleId(0L);
        Method method = SystemProfileSettingsAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertEquals(null, currentUser.getSimulatedRoleId());
        org.mockito.Mockito.verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        org.mockito.Mockito.verify(permissionSnapshotService, org.mockito.Mockito.never())
                .loadGrantedRoleSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
                username,
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static final class RecordingOperationAuditService extends OperationAuditService {
        private String username;

        private RecordingOperationAuditService() {
            super(null, objectProvider(null));
        }

        @Override
        public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
            this.username = username;
        }
    }

    private static <T> ObjectProvider<T> objectProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Iterator<T> iterator() {
                return value == null ? List.<T>of().iterator() : List.of(value).iterator();
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }

            @Override
            public Stream<T> orderedStream() {
                return stream();
            }
        };
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final Map<String, String> configValues;
        private final List<Object[]> insertedRows = new ArrayList<>();
        private final List<String> insertSqls = new ArrayList<>();
        private int queryForListCount;
        private int updateCount;
        private int updateResult = 1;

        private RecordingJdbcTemplate(Map<String, String> configValues) {
            this.configValues = new LinkedHashMap<>(configValues);
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount++;
            insertSqls.add(sql);
            insertedRows.add(args);
            if (args.length >= 3 && args[0] instanceof String configKey) {
                configValues.put(configKey, String.valueOf(args[2]));
            }
            return updateResult;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryForListCount++;
            List<Map<String, Object>> rows = new ArrayList<>();
            int keyCount = args.length;
            for (int index = 0; index < keyCount; index++) {
                String key = String.valueOf(args[index]);
                if (!configValues.containsKey(key)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("configKey", key);
                row.put("configValue", configValues.get(key));
                rows.add(row);
            }
            return rows;
        }

        private int queryForListCount() {
            return queryForListCount;
        }

        private int updateCount() {
            return updateCount;
        }

        private List<String> insertedConfigKeys() {
            return insertedRows.stream()
                    .map(args -> args.length > 0 ? String.valueOf(args[0]) : null)
                    .filter(value -> value != null)
                    .toList();
        }

        private boolean hasInsertValue(String configKey, String expectedValue) {
            for (Object[] args : insertedRows) {
                if (args.length > 2 && configKey.equals(String.valueOf(args[0])) && expectedValue.equals(String.valueOf(args[2]))) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasInsertValueContaining(String configKey, String expectedValuePart) {
            for (Object[] args : insertedRows) {
                if (args.length > 2 && configKey.equals(String.valueOf(args[0])) && String.valueOf(args[2]).contains(expectedValuePart)) {
                    return true;
                }
            }
            return false;
        }
    }
}
