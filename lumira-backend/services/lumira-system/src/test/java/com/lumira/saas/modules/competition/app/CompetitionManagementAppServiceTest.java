package com.lumira.saas.modules.competition.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionManagementAppServiceTest {

    @Test
    void deleteCompetitionRejectsCompetitionWithRegistrations() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        when(jdbcTemplate.queryForObject(contains("from competition_registration"), eq(Long.class), eq(11L))).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteCompetition(admin(), 11L))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getUserMessage()).contains("不能删除");
                });
        verify(jdbcTemplate, never()).update(contains("update aiadc_competition"), any());
    }

    @Test
    void deleteCompetitionSoftDeletesWhenNoRegistrationsExist() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        when(jdbcTemplate.queryForObject(contains("from competition_registration"), eq(Long.class), eq(11L))).thenReturn(0L);
        when(jdbcTemplate.update(contains("update aiadc_competition"), eq(1001L), any(), eq(11L))).thenReturn(1);

        assertThat(service.deleteCompetition(admin(), 11L)).isTrue();
        verify(jdbcTemplate).update(contains("update aiadc_competition"), eq(1001L), any(), eq(11L));
    }

    @Test
    void updateCompetitionDraftRejectsPublishedCompetition() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.Competition published = new CompetitionVO.Competition();
        published.setId(11L);
        published.setUuid("competition-uuid");
        published.setCompetitionNo("C202606290001");
        published.setStatus("published");
        when(jdbcTemplate.query(
                contains("from aiadc_competition where id = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<CompetitionVO.Competition>>any(),
                eq(11L)
        )).thenReturn(List.of(published));

        assertThatThrownBy(() -> service.updateCompetitionDraft(admin(), 11L, new CompetitionDTO.CompetitionUpsertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(jdbcTemplate, never()).update(contains("update aiadc_competition"), any());
    }

    @Test
    void updateCompetitionRejectsPublishingWhenRequiredScheduleIsMissing() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.Competition existing = competition("draft");
        CompetitionVO.Competition published = competition("published");
        published.setRegistrationStart(null);
        CompetitionVO.ConfigSet configSet = configSet();
        jdbcTemplate.enqueue(List.of(existing), List.of(published), List.of(configSet), List.of());

        jdbcTemplate.updateCount = 1;

        assertThatThrownBy(() -> service.updateCompetition(admin(), 11L, publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getUserMessage()).contains("发布前请完善配置", "报名开始时间");
                });
    }

    @Test
    void publishSettingsRejectsEnabledDocumentWithoutContent() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.Competition competition = competition("draft");
        CompetitionVO.ConfigSet configSet = configSet();
        CompetitionVO.ConfigItem document = configItem("AGREEMENT", "commitment", "承诺书", "", null);
        jdbcTemplate.enqueue(List.of(competition), List.of(configSet), List.of(document));

        assertThatThrownBy(() -> service.publishSettings(admin(), "competition-uuid"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getUserMessage()).contains("发布前请完善配置", "文书配置", "内容未填写");
        });
        assertThat(jdbcTemplate.updates).noneMatch(sql -> sql.contains("update competition_config_set set status = 'PUBLISHED'"));
    }

    @Test
    void updateCompetitionSkipsPublishValidationForAlreadyPublishedCompetition() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.Competition existing = competition("published");
        CompetitionVO.Competition updated = competition("published");
        updated.setRegistrationStart(null);
        CompetitionVO.ConfigSet configSet = configSet();
        jdbcTemplate.enqueue(List.of(existing), List.of(updated), List.of(configSet), List.of());

        jdbcTemplate.updateCount = 1;

        CompetitionVO.Competition saved = service.updateCompetition(admin(), 11L, publishRequest());

        assertThat(saved.getStatus()).isEqualTo("published");
    }

    @Test
    void updateCompetitionAllowsPageLevelDraftSaveWithoutPublishValidation() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.Competition existing = competition("draft");
        CompetitionVO.Competition updated = competition("draft");
        updated.setRegistrationStart("2026-07-01 09:00");
        updated.setRegistrationEnd("2026-07-31 18:00");
        jdbcTemplate.enqueue(List.of(existing), List.of(updated));
        jdbcTemplate.updateCount = 1;
        CompetitionDTO.CompetitionUpsertRequest request = new CompetitionDTO.CompetitionUpsertRequest();
        request.setRegistrationStart("2026-07-01 09:00");
        request.setRegistrationEnd("2026-07-31 18:00");
        request.setCompetitionStart("TBD");
        request.setLocation("TBD");
        request.setStatus("draft");

        CompetitionVO.Competition saved = service.updateCompetition(admin(), 11L, request);

        assertThat(saved.getRegistrationStart()).isEqualTo("2026-07-01 09:00");
        assertThat(jdbcTemplate.queryResults).isEmpty();
    }

    @Test
    void saveSettingsModuleSynchronizesItemsByTypeAndKey() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.Competition competition = competition("draft");
        CompetitionVO.ConfigSet configSet = configSet();
        CompetitionDTO.SettingsModuleRequest request = new CompetitionDTO.SettingsModuleRequest();
        CompetitionDTO.ConfigItemRequest updatedItem = new CompetitionDTO.ConfigItemRequest();
        updatedItem.setItemType("AGREEMENT");
        updatedItem.setItemKey("commitment");
        updatedItem.setTitle("承诺书新版");
        updatedItem.setContentText("我已阅读并同意");
        updatedItem.setRequiredFlag(true);
        updatedItem.setEnabled(true);
        CompetitionDTO.ConfigItemRequest newItem = new CompetitionDTO.ConfigItemRequest();
        newItem.setItemType("CONSENT");
        newItem.setItemKey("new-consent");
        newItem.setTitle("知情同意");
        newItem.setContentText("知情同意内容");
        newItem.setRequiredFlag(true);
        newItem.setEnabled(true);
        request.setItems(List.of(updatedItem, newItem));
        CompetitionVO.ConfigItem existingKept = configItem("AGREEMENT", "commitment", "承诺书", "旧内容", "{}");
        existingKept.setId(33L);
        CompetitionVO.ConfigItem existingRemoved = configItem("CONSENT", "old-consent", "旧知情同意", "旧内容", "{}");
        existingRemoved.setId(34L);
        jdbcTemplate.enqueue(
                List.of(competition),
                List.of(configSet),
                List.of(existingKept, existingRemoved),
                List.of("user-uuid"),
                List.of(competition),
                List.of(configSet),
                List.of(
                        configItem("AGREEMENT", "commitment", "承诺书新版", "我已阅读并同意", "{}"),
                        configItem("CONSENT", "new-consent", "知情同意", "知情同意内容", "{}")
                ),
                List.of(),
                List.of(),
                List.of()
        );
        jdbcTemplate.updateCount = 1;

        CompetitionVO.Settings settings = service.saveSettingsModule(admin(), "competition-uuid", "documents", request);

        assertThat(settings.getDocuments()).hasSize(2);
        assertThat(jdbcTemplate.updates.get(0))
                .contains("update competition_config_item")
                .contains("where id = ? and deleted = 0");
        assertThat(jdbcTemplate.updates.get(1))
                .contains("insert into competition_config_item");
        assertThat(jdbcTemplate.updates.get(2))
                .contains("delete from competition_config_item where id in");
    }

    private CompetitionManagementAppService service(MyBatisQueryOperations jdbcTemplate) {
        return new CompetitionManagementAppService(jdbcTemplate, mock(DictRuntimeService.class));
    }

    private CurrentUser admin() {
        return new CurrentUser(1001L, "admin", "session", 1, true, Set.of("aiadc:competition:delete"));
    }

    private CompetitionDTO.CompetitionUpsertRequest publishRequest() {
        CompetitionDTO.CompetitionUpsertRequest request = new CompetitionDTO.CompetitionUpsertRequest();
        request.setCode("C202606290001");
        request.setTitle("测试赛事");
        request.setCategory("OTHER");
        request.setCompetitionLevel("PROVINCIAL");
        request.setParticipationScope("全国大学生");
        request.setRegistrationStart("2026-06-29 00:00");
        request.setRegistrationEnd("2026-07-10 23:59");
        request.setCompetitionStart("2026-07-11 00:00");
        request.setLocation("线上");
        request.setFeeMode("TEAM");
        request.setCurrency("CNY");
        request.setStatus("published");
        return request;
    }

    private CompetitionVO.Competition competition(String status) {
        CompetitionVO.Competition competition = new CompetitionVO.Competition();
        competition.setId(11L);
        competition.setUuid("competition-uuid");
        competition.setCompetitionNo("C202606290001");
        competition.setCode("C202606290001");
        competition.setTitle("测试赛事");
        competition.setCategory("OTHER");
        competition.setCompetitionLevel("PROVINCIAL");
        competition.setParticipationScope("全国大学生");
        competition.setRegistrationStart("2026-06-29 00:00");
        competition.setRegistrationEnd("2026-07-10 23:59");
        competition.setCompetitionStart("2026-07-11 00:00");
        competition.setLocation("线上");
        competition.setFeeMode("TEAM");
        competition.setCurrency("CNY");
        competition.setStatus(status);
        return competition;
    }

    private CompetitionVO.ConfigSet configSet() {
        CompetitionVO.ConfigSet configSet = new CompetitionVO.ConfigSet();
        configSet.setId(22L);
        configSet.setCompetitionUuid("competition-uuid");
        configSet.setStatus("DRAFT");
        return configSet;
    }

    private CompetitionVO.ConfigItem configItem(String itemType, String itemKey, String title, String contentText, String contentJson) {
        CompetitionVO.ConfigItem item = new CompetitionVO.ConfigItem();
        item.setId(33L);
        item.setCompetitionUuid("competition-uuid");
        item.setConfigSetId(22L);
        item.setItemType(itemType);
        item.setItemKey(itemKey);
        item.setTitle(title);
        item.setContentText(contentText);
        item.setContentJson(contentJson);
        item.setEnabled(true);
        return item;
    }

    private static final class StubOperations extends MyBatisQueryOperations {
        private final Queue<List<?>> queryResults = new ArrayDeque<>();
        private final List<String> updates = new ArrayList<>();
        private int updateCount = 0;

        private void enqueue(List<?>... results) {
            queryResults.addAll(List.of(results));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (queryResults.isEmpty()) {
                return List.of();
            }
            return (List<T>) queryResults.remove();
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(sql);
            return updateCount;
        }
    }
}
