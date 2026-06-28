package com.lumira.saas.modules.system.app;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.profile.dto.ProfileFieldSettingItem;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionGroupVO;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionItemVO;
import com.lumira.saas.modules.system.profile.vo.ProfileCompletionSummaryVO;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemProfileSettingsAppServiceTest {

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

        assertEquals("联系方式", findSetting(first, "mobile").getGroupLabel());
        assertEquals("联系方式", findSetting(second, "mobile").getGroupLabel());
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
        assertEquals("联系方式", mobile.getGroupLabel());
        assertTrue(avatar.getVisible());
        assertEquals(10, avatar.getWeight());
        assertFalse(email.getVisible());
        assertEquals(15, email.getWeight());
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

        List<ProfileFieldSettingVO> teamMemberSettings = service.getProfileFieldSettings(buildCurrentUser(), "TEAM_MEMBER");
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
        assertEquals("待开启", mobile.getActionLabel());
        assertNotNull(mobile.getActionHint());
    }

    private static SystemProfileSettingsAppService newService(JdbcTemplate jdbcTemplate) {
        return new SystemProfileSettingsAppService(new MyBatisQueryOperations(jdbcTemplate), new RecordingOperationAuditService());
    }

    private static CurrentUser buildCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUsername("admin");
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

    private static final class RecordingOperationAuditService extends OperationAuditService {
        private RecordingOperationAuditService() {
            super(null);
        }

        @Override
        public void log(Long userId, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
        }
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final Map<String, String> configValues;
        private final List<Object[]> insertedRows = new ArrayList<>();
        private final List<String> insertSqls = new ArrayList<>();
        private int queryForListCount;

        private RecordingJdbcTemplate(Map<String, String> configValues) {
            this.configValues = new LinkedHashMap<>(configValues);
        }

        @Override
        public int update(String sql, Object... args) {
            insertSqls.add(sql);
            insertedRows.add(args);
            if (args.length >= 3 && args[0] instanceof String configKey) {
                configValues.put(configKey, String.valueOf(args[2]));
            }
            return 1;
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
