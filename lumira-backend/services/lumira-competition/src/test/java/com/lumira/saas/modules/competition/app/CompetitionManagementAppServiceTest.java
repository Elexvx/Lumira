package com.lumira.saas.modules.competition.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.event.EventCatalogEventTypes;
import com.lumira.api.event.TransactionalEventOutboxPort;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.api.dictionary.DictionaryValueNormalizer;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.infrastructure.persistence.RowMapper;
import com.lumira.saas.modules.competition.support.CompetitionSessionAuthenticationFixture;
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.infrastructure.JdbcCompetitionManagementRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcCompetitionSettingsRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcCompetitionStageRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcRegistrationDatasetRepository;
import com.lumira.saas.modules.competition.repository.CompetitionManagementRepository;
import com.lumira.saas.modules.competition.repository.CompetitionSettingsRepository;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import com.lumira.saas.modules.competition.support.CompetitionPermissionSnapshotFixture;
import com.lumira.saas.modules.competition.support.CompetitionTrustTestFixtures;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompetitionManagementAppServiceTest {

    @Test
    void applicationServiceKeepsCompetitionPersistenceBehindPorts() {
        List<Class<?>> dependencyTypes = new ArrayList<>();
        java.util.Arrays.stream(CompetitionManagementAppService.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType)
                .forEach(dependencyTypes::add);
        java.util.Arrays.stream(CompetitionManagementAppService.class.getDeclaredConstructors())
                .flatMap(constructor -> java.util.Arrays.stream(constructor.getParameterTypes()))
                .forEach(dependencyTypes::add);

        assertThat(dependencyTypes).doesNotContain(
                CompetitionSqlOperations.class,
                JdbcCompetitionManagementRepository.class,
                JdbcCompetitionSettingsRepository.class,
                JdbcCompetitionStageRepository.class
        );
    }

    @Test
    void listCompetitionsShouldRejectUnauthenticatedUserBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.listCompetitions(unauthenticatedUser(), null, null, null, null, null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listCompetitionsShouldRejectBlankUsernameBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        CurrentUser currentUser = admin();
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.listCompetitions(currentUser, null, null, null, null, null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listCompetitionsShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        CurrentUser currentUser = admin();
        currentUser.setSessionVersion(null);

        assertThatThrownBy(() -> service.listCompetitions(currentUser, null, null, null, null, null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listCompetitionsShouldDelegatePersistenceToCompetitionManagementRepository() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementRepository repository = mock(CompetitionManagementRepository.class);
        CompetitionManagementAppService service = service(jdbcTemplate, repository);
        CompetitionVO.Competition competition = competition("published");
        when(repository.findCompetitions(any())).thenReturn(
                new CompetitionManagementRepository.CompetitionPage(List.of(competition), 23L)
        );

        PageResponse<CompetitionVO.Competition> page = service.listCompetitions(
                admin(), " innovation ", " design ", "published", "zh", true, 2, 10
        );

        assertThat(page.getRecords()).containsExactly(competition);
        assertThat(page.getTotal()).isEqualTo(23L);
        verify(repository).findCompetitions(new CompetitionManagementRepository.CompetitionSearch(
                "innovation", "design", "published", "zh", true, 10L, 10L
        ));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createCompetitionShouldRejectWhenLiveSnapshotRevokesCreatePermissionBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new CompetitionPermissionSnapshotFixture.PermissionSnapshot("permissions-2", Set.of("aiadc:competition:view")));
        CompetitionManagementAppService service =
                strictService(jdbcTemplate, permissionSnapshotService, null, null);

        assertThatThrownBy(() -> service.createCompetition(user("aiadc:competition:create"), publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createCompetitionShouldRejectDisabledTrustedIdentityBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "DISABLED"));
        CompetitionManagementAppService service =
                strictService(jdbcTemplate, permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.createCompetition(user("aiadc:competition:create"), publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createCompetitionShouldRejectTrustedIdentityWhenLiveUsernameIsUnavailableBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));
        CompetitionManagementAppService service =
                strictService(jdbcTemplate, permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.createCompetition(user("aiadc:competition:create"), publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createCompetitionDraftShouldRefreshLiveUsername() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new CompetitionPermissionSnapshotFixture.PermissionSnapshot("permissions-2", Set.of("*", "aiadc:competition:create", "aiadc:competition:view")));
        CompetitionManagementAppService service =
                strictService(jdbcTemplate, permissionSnapshotService, systemInternalApi, null);
        jdbcTemplate.enqueue(List.of(competition("draft")), List.of(configSet()));
        jdbcTemplate.updateCount = 1;
        CurrentUser currentUser = admin();
        currentUser.setUsername("admin-stale");

        CompetitionDTO.CompetitionUpsertRequest request = publishRequest();
        request.setStatus("draft");
        service.createCompetitionDraft(currentUser, request);

        assertThat(currentUser.getUsername()).isEqualTo("admin-live");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
    }

    @Test
    void createCompetitionDraftShouldPersistEmptyBasicPlaceholders() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new CompetitionPermissionSnapshotFixture.PermissionSnapshot("permissions-1", Set.of("*", "aiadc:competition:create", "aiadc:competition:view")));
        CompetitionManagementAppService service =
                strictService(jdbcTemplate, permissionSnapshotService, systemInternalApi, null);
        jdbcTemplate.enqueue(List.of(competition("draft")), List.of(configSet()));
        jdbcTemplate.updateCount = 1;
        CompetitionDTO.CompetitionUpsertRequest request = new CompetitionDTO.CompetitionUpsertRequest();
        request.setCompetitionStart("TBD");
        request.setLocation("TBD");
        request.setParticipationScope("TBD");

        service.createCompetitionDraft(admin(), request);

        assertThat(jdbcTemplate.updateArguments).hasSize(3);
        assertThat(jdbcTemplate.updateArguments.get(0)[6]).isEqualTo("");
        assertThat(jdbcTemplate.updateArguments.get(0)[9]).isNull();
        assertThat(jdbcTemplate.updateArguments.get(0)[13]).isEqualTo("");
        assertThat(jdbcTemplate.updateArguments.get(0)[15]).isEqualTo("");
        assertThat(jdbcTemplate.updateArguments.get(0)[16]).isNull();
        assertThat(jdbcTemplate.updates.get(1)).contains("insert into competition_registration_dataset");
        assertThat(jdbcTemplate.updateArguments.get(1)[0]).isEqualTo(11L);
    }

    @Test
    void createCompetitionShouldRejectRevokedSessionTicketBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionSessionAuthenticationFixture sessionAuthenticationService = mock(CompetitionSessionAuthenticationFixture.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        CompetitionManagementAppService service =
                strictService(jdbcTemplate, null, null, sessionAuthenticationService);

        assertThatThrownBy(() -> service.createCompetition(user("aiadc:competition:create"), publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createCompetitionShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service =
                strictService(jdbcTemplate, null, null, null);

        assertThatThrownBy(() -> service.createCompetition(user("aiadc:competition:create"), publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createCompetitionShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        CompetitionManagementAppService service =
                strictService(jdbcTemplate, permissionSnapshotService, null, null);

        assertThatThrownBy(() -> service.createCompetition(user("aiadc:competition:create"), publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new CompetitionPermissionSnapshotFixture.PermissionSnapshot("permissions-2", Set.of("aiadc:competition:view")));
        CurrentUser currentUser = user("aiadc:competition:view");
        currentUser.setSimulatedRoleId(0L);

        CompetitionAuthenticationTrust.refresh(
                currentUser,
                CompetitionTrustTestFixtures.resolver(permissionSnapshotService, null, null),
                true
        );

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(1001L, "user-uuid-1001", 0L);
    }

    @Test
    void listCompetitionsShouldRequireViewPermissionAtServiceLayer() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.listCompetitions(user("aiadc:competition:create"), null, null, null, null, null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listPublishedCompetitionsAllowsRegistrationCreatePermission() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        jdbcTemplate.enqueue(List.of(competition("published")));

        PageResponse<CompetitionVO.Competition> page = service.listCompetitions(
                user("aiadc:registration:create"),
                null,
                null,
                "published",
                null,
                null,
                1,
                10
        );

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getStatus()).isEqualTo("published");
    }

    @Test
    void getCompetitionSettingsShouldRequireViewPermissionBeforeLookup() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.getCompetitionSettings(user("aiadc:competition:update"), "competition-uuid"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getCompetitionSettingsAllowsRegistrationCreatePermissionForPublishedCompetition() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        jdbcTemplate.enqueue(
                List.of(competition("published")),
                List.of(configSet()),
                List.of(),
                List.of(
                        configItem("TEAM_FIELD", "teamName", "团队名称", null, "{\"standardField\":true}"),
                        configItem("TEAM_FIELD", "avatarUrl", "团队头像", null, "{\"standardField\":true}"),
                        configItem("MEMBER_FIELD", "memberName", "成员姓名", null, "{\"standardField\":true}"),
                        configItem("PROJECT_FIELD", "title", "项目名称", null, "{\"standardField\":true}"),
                        configItem("PROJECT_FIELD", "imageUrl", "项目头像", null, "{\"standardField\":true}"),
                        configItem("PROJECT_FIELD", "intellectualPropertyType", "知识产权类型", null, "{\"standardField\":true}"),
                        configItem("PROJECT_FIELD", "distributionRegions", "知识产权分布区域", null, "{\"standardField\":true}")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        CompetitionVO.Settings settings = service.getCompetitionSettings(user("aiadc:registration:create"), "competition-uuid");

        assertThat(settings.getCompetition()).isNotNull();
        assertThat(settings.getCompetition().getStatus()).isEqualTo("published");
        assertThat(settings.getActiveConfigSet()).isNotNull();
        assertThat(settings.getFields())
                .extracting(CompetitionVO.ConfigItem::getItemKey)
                .contains("teamName", "avatarUrl", "memberName", "title", "imageUrl", "intellectualPropertyType", "distributionRegions")
                .doesNotContain("employeeNo", "departmentName", "role", "remark");
    }

    @Test
    void jdbcCompetitionSettingsRepositoryShouldCopyDefaultTemplatesWithoutBusinessDataInJava() {
        StubOperations jdbcTemplate = new StubOperations();
        jdbcTemplate.updateCount = 1;
        JdbcCompetitionSettingsRepository repository = new JdbcCompetitionSettingsRepository(jdbcTemplate);

        repository.seedDefaultConfigItems(
                new CompetitionSettingsRepository.ConfigTemplateSeed(
                        "competition-uuid",
                        22L,
                        new CompetitionSettingsRepository.Actor(1001L, "user-uuid-1001")
                )
        );

        assertThat(jdbcTemplate.updates).hasSize(1);
        assertThat(jdbcTemplate.updates.get(0))
                .contains("from competition_config_item_template")
                .contains("template_code = 'DEFAULT'")
                .doesNotContain("enabled = 1")
                .doesNotContain("团队名称", "项目名称", "知识产权");
    }

    @Test
    void getCompetitionSettingsHidesDraftCompetitionFromRegistrationPermission() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        when(jdbcTemplate.query(
                contains("where uuid = ? and status = 'published'"),
                org.mockito.ArgumentMatchers.<RowMapper<CompetitionVO.Competition>>any(),
                eq("competition-uuid")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getCompetitionSettings(user("aiadc:registration:create"), "competition-uuid"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void getCompetitionAllowsRegistrationCreatePermissionForPublishedCompetition() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        jdbcTemplate.enqueue(List.of(competition("published")));

        CompetitionVO.Competition competition = service.getCompetition(user("aiadc:registration:create"), 11L);

        assertThat(competition).isNotNull();
        assertThat(competition.getStatus()).isEqualTo("published");
    }

    @Test
    void getCompetitionHidesDraftCompetitionFromRegistrationPermission() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        jdbcTemplate.enqueue(List.of(competition("draft")));

        assertThatThrownBy(() -> service.getCompetition(user("aiadc:registration:create"), 11L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void deleteCompetitionRejectsCompetitionWithRegistrations() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        when(jdbcTemplate.queryForObject(contains("from competition_registration"), eq(Long.class), eq(11L))).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteCompetition(admin(), 11L))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getUserMessage()).contains("cannot be deleted");
                });
        verify(jdbcTemplate, never()).update(contains("update aiadc_competition"), any());
    }

    @Test
    void deleteCompetitionSoftDeletesWhenNoRegistrationsExist() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        when(jdbcTemplate.queryForObject(contains("from competition_registration"), eq(Long.class), eq(11L))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<CompetitionVO.Competition>>any(), eq(11L)))
                .thenReturn(List.of(competition("draft")));
        when(jdbcTemplate.update(contains("update aiadc_competition"), eq(1001L), eq("user-uuid-1001"), any(), eq(11L), eq("competition-uuid"), eq("C202606290001"), eq("draft"))).thenReturn(1);

        assertThat(service.deleteCompetition(admin(), 11L)).isTrue();
        verify(jdbcTemplate).update(contains("update aiadc_competition"), eq(1001L), eq("user-uuid-1001"), any(), eq(11L), eq("competition-uuid"), eq("C202606290001"), eq("draft"));
    }

    @Test
    void updateCompetitionDraftRejectsPublishedCompetition() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
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
    void updateCompetitionShouldRequireUpdatePermissionAtServiceLayer() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.updateCompetition(user("aiadc:competition:view"), 11L, publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(jdbcTemplate, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<CompetitionVO.Competition>>any(), any());
        verify(jdbcTemplate, never()).update(anyString(), any());
    }

    @Test
    void competitionWritesShouldRejectNullRequestOrInvalidIdBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.createCompetition(admin(), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createCompetitionDraft(admin(), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.updateCompetition(admin(), 0L, publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.updateCompetitionDraft(admin(), -1L, publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.deleteCompetition(admin(), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void competitionWritesShouldRejectUnsafeFieldsBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionDTO.CompetitionUpsertRequest unsafeImage = publishRequest();
        unsafeImage.setImageUrl("javascript:alert(1)");
        CompetitionDTO.CompetitionUpsertRequest invalidJson = publishRequest();
        invalidJson.setScheduleJson("{bad");
        CompetitionDTO.CompetitionUpsertRequest oversizedContent = publishRequest();
        oversizedContent.setHomepageContent("x".repeat(20001));

        assertThatThrownBy(() -> service.createCompetition(admin(), unsafeImage))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createCompetition(admin(), invalidJson))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createCompetition(admin(), oversizedContent))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createCompetitionShouldRejectWhenMainInsertMissesBeforeLastInsertId() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        jdbcTemplate.updateCount = 0;

        assertThatThrownBy(() -> service.createCompetition(admin(), publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Competition changed, please retry");
                });

        assertThat(jdbcTemplate.updates).hasSize(1);
        assertThat(jdbcTemplate.updates.get(0)).contains("insert into aiadc_competition");
        assertThat(jdbcTemplate.lastInsertIdQueries).isZero();
    }

    @Test
    void createCompetitionShouldRejectWhenDefaultConfigSetInsertMisses() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        jdbcTemplate.updateResults.add(1);
        jdbcTemplate.updateResults.add(1);
        jdbcTemplate.updateResults.add(0);
        jdbcTemplate.enqueue(List.of(competition("draft")), List.of());

        assertThatThrownBy(() -> service.createCompetition(admin(), publishRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Competition config set changed, please retry");
                });

        assertThat(jdbcTemplate.updates).hasSize(3);
        assertThat(jdbcTemplate.updates.get(0)).contains("insert into aiadc_competition");
        assertThat(jdbcTemplate.updates.get(1)).contains("insert into competition_registration_dataset");
        assertThat(jdbcTemplate.updates.get(2)).contains("insert into competition_config_set");
        assertThat(jdbcTemplate.lastInsertIdQueries).isEqualTo(1);
    }

    @Test
    void saveSettingsModuleShouldRejectNullRequestBeforeCompetitionLookup() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.saveSettingsModule(admin(), "competition-uuid", "documents", null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void saveSettingsModuleShouldRejectUnsafeConfigItemBeforeCompetitionLookup() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionDTO.SettingsModuleRequest request = new CompetitionDTO.SettingsModuleRequest();
        CompetitionDTO.ConfigItemRequest invalidJson = new CompetitionDTO.ConfigItemRequest();
        invalidJson.setItemType("REQUIRED_FILE");
        invalidJson.setItemKey("work-file");
        invalidJson.setTitle("Work file");
        invalidJson.setContentJson("{bad");
        CompetitionDTO.ConfigItemRequest oversizedText = new CompetitionDTO.ConfigItemRequest();
        oversizedText.setItemType("AGREEMENT");
        oversizedText.setItemKey("commitment");
        oversizedText.setTitle("Commitment");
        oversizedText.setContentText("x".repeat(20001));

        request.setItems(List.of(invalidJson));
        assertThatThrownBy(() -> service.saveSettingsModule(admin(), "competition-uuid", "files", request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        request.setItems(List.of(oversizedText));
        assertThatThrownBy(() -> service.saveSettingsModule(admin(), "competition-uuid", "documents", request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void saveSettingsModuleShouldRequireStorageLocationForMaterialItems() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionDTO.ConfigItemRequest material = new CompetitionDTO.ConfigItemRequest();
        material.setItemType("STAGE_MATERIAL");
        material.setItemKey("work-file");
        material.setTitle("作品文件");
        material.setContentJson("""
                {"stageCode":"PRELIMINARY","fileFormat":"DOCUMENT","maxSizeMb":100}
                """);
        CompetitionDTO.SettingsModuleRequest request = new CompetitionDTO.SettingsModuleRequest();
        request.setItems(List.of(material));

        assertThatThrownBy(() -> service.saveSettingsModule(admin(), "competition-uuid", "stage-materials", request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("作品文件", "必须选择保存位置");
                });

        verifyNoInteractions(jdbcTemplate);
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
                    assertThat(exception.getUserMessage()).contains("赛事暂未满足发布条件", "请设置报名开始时间");
                });
    }

    @Test
    void publishSettingsRejectsEnabledDocumentWithoutContent() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.Competition competition = competition("draft");
        CompetitionVO.ConfigSet configSet = configSet();
        CompetitionVO.ConfigItem document = configItem("AGREEMENT", "commitment", "Commitment", "", null);
        jdbcTemplate.enqueue(List.of(competition), List.of(configSet), List.of(document));

        assertThatThrownBy(() -> service.publishSettings(admin(), "competition-uuid"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getUserMessage()).contains("赛事暂未满足发布条件", "报名文书“赛事承诺书”必须填写内容");
        });
        assertThat(jdbcTemplate.updates).noneMatch(sql -> sql.contains("update competition_config_set set status = 'PUBLISHED'"));
    }

    @Test
    void publishSettingsUsesChineseLabelsForLegacyDefaultItems() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.ConfigItem contactName = configItem(
                "REGISTRATION_FIELD",
                "contact-name",
                "Contact name",
                null,
                "{}"
        );
        CompetitionVO.ConfigItem workFile = configItem(
                "REQUIRED_FILE",
                "work-file",
                "Work file",
                null,
                "{}"
        );
        jdbcTemplate.enqueue(List.of(competition("draft")), List.of(configSet()), List.of(contactName, workFile));

        assertThatThrownBy(() -> service.publishSettings(admin(), "competition-uuid"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getUserMessage()).isEqualTo(
                            "赛事暂未满足发布条件：报名字段“联系人姓名”必须设置字段类型；提交材料“作品文件”必须设置允许上传的文件格式"
                    );
                });
    }

    @Test
    void publishValidationAcceptsLegacyDefaultItemMetadata() throws Exception {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.ConfigItem contactName = configItem(
                "REGISTRATION_FIELD",
                "contact-name",
                "Contact name",
                null,
                "{\"type\":\"input\",\"target\":\"registration\"}"
        );
        CompetitionVO.ConfigItem workFile = configItem(
                "REQUIRED_FILE",
                "work-file",
                "Work file",
                null,
                "{\"accept\":\"*\",\"maxSizeMb\":100,\"maxCount\":1}"
        );
        jdbcTemplate.enqueue(List.of(contactName, workFile));
        Method method = CompetitionManagementAppService.class.getDeclaredMethod(
                "validateCompetitionReadyForPublish",
                CompetitionVO.Competition.class,
                CompetitionVO.ConfigSet.class
        );
        method.setAccessible(true);

        method.invoke(service, competition("draft"), configSet());

        assertThat(jdbcTemplate.queryResults).isEmpty();
    }

    @Test
    void updateCompetitionSkipsPublishValidationForAlreadyPublishedCompetition() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.Competition existing = competition("published");
        CompetitionVO.Competition updated = competition("published");
        updated.setRegistrationStart(null);
        jdbcTemplate.enqueue(List.of(existing), List.of(updated), List.of("user-uuid"));

        jdbcTemplate.updateCount = 1;

        CompetitionVO.Competition saved = service.updateCompetition(admin(), 11L, publishRequest());

        assertThat(saved.getStatus()).isEqualTo("published");
    }

    @Test
    void publishedCompetitionUpdateRecordsCatalogOutboxWithOwnerMutation() {
        StubOperations jdbcTemplate = new StubOperations();
        TransactionalEventOutboxPort outbox = mock(TransactionalEventOutboxPort.class);
        CompetitionManagementAppService service = service(
                jdbcTemplate,
                new JdbcCompetitionManagementRepository(jdbcTemplate),
                outbox
        );
        CompetitionVO.Competition existing = competition("published");
        CompetitionVO.Competition updated = competition("published");
        jdbcTemplate.enqueue(List.of(existing), List.of(updated), List.of("user-uuid"));
        jdbcTemplate.updateCount = 1;

        service.updateCompetition(admin(), 11L, publishRequest());

        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> attributes =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(outbox).record(
                eq(EventCatalogEventTypes.CATALOG_ITEM_UPSERTED),
                eq(1001L),
                eq("event-catalog.item"),
                eq(11L),
                attributes.capture()
        );
        assertThat(attributes.getValue())
                .containsEntry("sourceType", "COMPETITION")
                .containsEntry("sourceId", 11L);
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
    void updateCompetitionSynchronizesExplicitMaterialAndReviewWindowsToStages() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionVO.Competition existing = competition("draft");
        CompetitionVO.Competition updated = competition("draft");
        jdbcTemplate.enqueue(List.of(existing), List.of(updated));
        jdbcTemplate.updateCount = 1;
        CompetitionDTO.CompetitionUpsertRequest request = publishRequest();
        request.setStatus("draft");
        request.setScheduleJson("""
                [{
                  "timeMode":"CONFIRMED",
                  "title":"初赛",
                  "materialStart":"2026-07-01 09:00",
                  "materialEnd":"2026-07-10 18:00",
                  "start":"2026-07-11 09:00",
                  "end":"2026-07-12 18:00",
                  "reviewStart":"2026-07-13 09:00",
                  "reviewEnd":"2026-07-15 18:00"
                }]
                """);

        service.updateCompetition(admin(), 11L, request);

        assertThat(jdbcTemplate.updates)
                .anyMatch(sql -> sql.contains("material_submit_start = ?")
                        && sql.contains("review_start = ?")
                        && sql.contains("stage_code = ?"));
        assertThat(jdbcTemplate.updateArguments)
                .anySatisfy(arguments -> assertThat(arguments).contains(
                        "初赛",
                        LocalDateTime.of(2026, 7, 1, 9, 0),
                        LocalDateTime.of(2026, 7, 10, 18, 0),
                        LocalDateTime.of(2026, 7, 13, 9, 0),
                        LocalDateTime.of(2026, 7, 15, 18, 0),
                        "PRELIMINARY"
                ));
    }

    @Test
    void updateCompetitionRejectsOverlappingTimelineWindowsBeforeWriting() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        jdbcTemplate.enqueue(List.of(competition("draft")));
        jdbcTemplate.updateCount = 1;
        CompetitionDTO.CompetitionUpsertRequest request = publishRequest();
        request.setStatus("draft");
        request.setScheduleJson("""
                [{
                  "timeMode":"CONFIRMED",
                  "materialStart":"2026-07-01 09:00",
                  "materialEnd":"2026-07-12 18:00",
                  "start":"2026-07-11 09:00",
                  "end":"2026-07-13 18:00",
                  "reviewStart":"2026-07-13 09:00",
                  "reviewEnd":"2026-07-15 18:00"
                }]
                """);

        assertThatThrownBy(() -> service.updateCompetition(admin(), 11L, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(jdbcTemplate.updates).isEmpty();
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
        updatedItem.setTitle("Commitment updated");
        updatedItem.setContentText("I have read and agree");
        updatedItem.setRequiredFlag(true);
        updatedItem.setEnabled(true);
        CompetitionDTO.ConfigItemRequest newItem = new CompetitionDTO.ConfigItemRequest();
        newItem.setItemType("CONSENT");
        newItem.setItemKey("new-consent");
        newItem.setTitle("知情同意");
        newItem.setContentText("Consent content");
        newItem.setRequiredFlag(true);
        newItem.setEnabled(true);
        request.setItems(List.of(updatedItem, newItem));
        CompetitionVO.ConfigItem existingKept = configItem("AGREEMENT", "commitment", "Commitment", "Old content", "{}");
        existingKept.setId(33L);
        CompetitionVO.ConfigItem existingRemoved = configItem("CONSENT", "old-consent", "Old consent", "Old content", "{}");
        existingRemoved.setId(34L);
        jdbcTemplate.enqueue(
                List.of(competition),
                List.of(configSet),
                List.of(existingKept, existingRemoved),
                List.of(competition),
                List.of(configSet),
                List.of(
                        configItem("AGREEMENT", "commitment", "Commitment updated", "I have read and agree", "{}"),
                        configItem("CONSENT", "new-consent", "Informed consent", "Consent content", "{}")
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
                .contains("updated_by_uuid")
                .contains("where id = ? and competition_uuid = ? and config_set_id = ? and item_type = ? and item_key = ? and deleted = 0");
        assertThat(jdbcTemplate.updates.get(1))
                .contains("insert into competition_config_item")
                .contains("created_by_uuid")
                .contains("updated_by_uuid");
        assertThat(jdbcTemplate.updates.get(2))
                .contains("delete from competition_config_item")
                .contains("item_type = ?")
                .contains("item_key = ?")
                .contains("deleted = 1");
        assertThat(jdbcTemplate.updates.get(3))
                .contains("update competition_config_item")
                .contains("set deleted = 1")
                .contains("updated_by_uuid")
                .contains("competition_uuid = ?")
                .contains("config_set_id = ?")
                .contains("id in");
    }

    @Test
    void saveSettingsModuleMatchesKeysCaseInsensitivelyLikeTheDatabaseUniqueIndex() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionDTO.ConfigItemRequest requestItem = new CompetitionDTO.ConfigItemRequest();
        requestItem.setItemType("MEMBER_FIELD");
        requestItem.setItemKey("school");
        requestItem.setTitle("学校");
        requestItem.setContentJson("{\"fieldType\":\"TEXT\"}");
        requestItem.setEnabled(true);
        CompetitionDTO.SettingsModuleRequest request = new CompetitionDTO.SettingsModuleRequest();
        request.setItems(List.of(requestItem));
        CompetitionVO.ConfigItem existing = configItem(
                "MEMBER_FIELD",
                "School",
                "School",
                null,
                "{\"fieldType\":\"TEXT\"}"
        );
        jdbcTemplate.enqueue(
                List.of(competition("draft")),
                List.of(configSet()),
                List.of(existing),
                List.of(competition("draft")),
                List.of(configSet()),
                List.of(),
                List.of(existing),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        jdbcTemplate.updateCount = 1;

        service.saveSettingsModule(admin(), "competition-uuid", "fields", request);

        assertThat(jdbcTemplate.updates)
                .anyMatch(sql -> sql.contains("update competition_config_item") && sql.contains("item_key = ? and deleted = 0"));
        assertThat(jdbcTemplate.updates).noneMatch(sql -> sql.contains("insert into competition_config_item"));
    }

    @Test
    void saveSettingsModuleRejectsCaseInsensitiveDuplicateKeysBeforeDatabaseWrite() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionDTO.ConfigItemRequest first = new CompetitionDTO.ConfigItemRequest();
        first.setItemType("MEMBER_FIELD");
        first.setItemKey("School");
        first.setTitle("学校");
        first.setContentJson("{\"fieldType\":\"TEXT\"}");
        CompetitionDTO.ConfigItemRequest duplicate = new CompetitionDTO.ConfigItemRequest();
        duplicate.setItemType("MEMBER_FIELD");
        duplicate.setItemKey("school");
        duplicate.setTitle("院校");
        duplicate.setContentJson("{\"fieldType\":\"TEXT\"}");
        CompetitionDTO.SettingsModuleRequest request = new CompetitionDTO.SettingsModuleRequest();
        request.setItems(List.of(first, duplicate));
        jdbcTemplate.enqueue(
                List.of(competition("draft")),
                List.of(configSet()),
                List.of()
        );

        assertThatThrownBy(() -> service.saveSettingsModule(admin(), "competition-uuid", "fields", request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getUserMessage()).contains("不能使用重复", "school");
                });
        assertThat(jdbcTemplate.updates).isEmpty();
    }

    @Test
    void saveSettingsModuleShouldSynchronizeMaterialConfigIntoParticipantStageForm() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        String metadata = """
                {"stageCode":"PRELIMINARY","fileFormat":"DOCUMENT","maxSizeMb":100,"storageKey":"competition_materials"}
                """;
        CompetitionDTO.ConfigItemRequest materialRequest = new CompetitionDTO.ConfigItemRequest();
        materialRequest.setItemType("STAGE_MATERIAL");
        materialRequest.setItemKey("work-file");
        materialRequest.setTitle("作品文件");
        materialRequest.setContentJson(metadata);
        materialRequest.setRequiredFlag(true);
        materialRequest.setEnabled(true);
        CompetitionDTO.SettingsModuleRequest request = new CompetitionDTO.SettingsModuleRequest();
        request.setItems(List.of(materialRequest));
        CompetitionVO.ConfigItem material = configItem(
                "STAGE_MATERIAL",
                "work-file",
                "作品文件",
                null,
                metadata
        );
        material.setRequiredFlag(true);
        jdbcTemplate.enqueue(
                List.of(competition("published")),
                List.of(configSet()),
                List.of(),
                List.of(material),
                List.of(competition("published")),
                List.of(configSet()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(material),
                List.of()
        );
        jdbcTemplate.updateCount = 1;

        service.saveSettingsModule(admin(), "competition-uuid", "materials", request);

        assertThat(jdbcTemplate.updates)
                .anyMatch(sql -> sql.contains("update competition_stage set stage_name"));
        assertThat(jdbcTemplate.updates)
                .anyMatch(sql -> sql.contains("update competition_stage_form set form_name"));
        assertThat(jdbcTemplate.updateArguments)
                .anyMatch(arguments -> java.util.Arrays.stream(arguments)
                        .anyMatch(argument -> argument instanceof String value
                                && value.contains("\"storageKey\":\"competition_materials\"")
                                && value.contains("\"required\":true")));
    }

    @Test
    void saveSettingsModuleRejectsWhenConfigItemSnapshotWriteMisses() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        CompetitionDTO.SettingsModuleRequest request = new CompetitionDTO.SettingsModuleRequest();
        CompetitionDTO.ConfigItemRequest updatedItem = new CompetitionDTO.ConfigItemRequest();
        updatedItem.setItemType("AGREEMENT");
        updatedItem.setItemKey("commitment");
        updatedItem.setTitle("Commitment updated");
        updatedItem.setContentText("I have read and agree");
        updatedItem.setRequiredFlag(true);
        updatedItem.setEnabled(true);
        request.setItems(List.of(updatedItem));
        jdbcTemplate.enqueue(
                List.of(competition("draft")),
                List.of(configSet()),
                List.of(configItem("AGREEMENT", "commitment", "Commitment", "Old content", "{}"))
        );
        jdbcTemplate.updateCount = 0;

        assertThatThrownBy(() -> service.saveSettingsModule(admin(), "competition-uuid", "documents", request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Competition config item changed, please retry");
                });
    }

    @Test
    void publishSettingsRejectsWhenCurrentConfigSetSnapshotWriteMisses() {
        StubOperations jdbcTemplate = new StubOperations();
        CompetitionManagementAppService service = service(jdbcTemplate);
        jdbcTemplate.enqueue(List.of(competition("draft")), List.of(configSet()), List.of());
        jdbcTemplate.updateCount = 0;

        assertThatThrownBy(() -> service.publishSettings(admin(), "competition-uuid"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Competition config set changed, please retry");
                });
        assertThat(jdbcTemplate.updates).hasSize(1);
        assertThat(jdbcTemplate.updates.get(0))
                .contains("update competition_config_set")
                .contains("status = 'PUBLISHED'");
    }

    private CompetitionManagementAppService service(CompetitionSqlOperations jdbcTemplate) {
        return service(jdbcTemplate, new JdbcCompetitionManagementRepository(jdbcTemplate));
    }

    private CompetitionManagementAppService service(
            CompetitionSqlOperations jdbcTemplate,
            CompetitionManagementRepository competitionManagementRepository
    ) {
        return service(jdbcTemplate, competitionManagementRepository, null);
    }

    private CompetitionManagementAppService service(
            CompetitionSqlOperations jdbcTemplate,
            CompetitionManagementRepository competitionManagementRepository,
            TransactionalEventOutboxPort transactionalEventOutboxPort
    ) {
        return new CompetitionManagementAppService(
                mock(DictionaryValueNormalizer.class),
                null,
                new JdbcRegistrationDatasetRepository(jdbcTemplate),
                competitionManagementRepository,
                new JdbcCompetitionSettingsRepository(jdbcTemplate),
                new JdbcCompetitionStageRepository(jdbcTemplate),
                false,
                transactionalEventOutboxPort
        );
    }

    private CompetitionManagementAppService strictService(
            CompetitionSqlOperations jdbcTemplate,
            CompetitionPermissionSnapshotFixture permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            CompetitionSessionAuthenticationFixture sessionAuthenticationService
    ) {
        return new CompetitionManagementAppService(
                mock(DictionaryValueNormalizer.class),
                CompetitionTrustTestFixtures.resolver(
                        permissionSnapshotService,
                        systemInternalApi,
                        sessionAuthenticationService
                ),
                new JdbcRegistrationDatasetRepository(jdbcTemplate),
                new JdbcCompetitionManagementRepository(jdbcTemplate),
                new JdbcCompetitionSettingsRepository(jdbcTemplate),
                new JdbcCompetitionStageRepository(jdbcTemplate),
                true
        );
    }

    private CurrentUser admin() {
        return user("*");
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(1001L, "admin", "session", 1, false, Set.of("*", "aiadc:competition:view"));
    }

    private CurrentUser user(String permission) {
        CurrentUser currentUser = new CurrentUser(1001L, "admin", "session", 1, true, Set.of(permission));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private CompetitionDTO.CompetitionUpsertRequest publishRequest() {
        CompetitionDTO.CompetitionUpsertRequest request = new CompetitionDTO.CompetitionUpsertRequest();
        request.setCode("C202606290001");
        request.setTitle("Competition");
        request.setCategory("OTHER");
        request.setCompetitionLevel("PROVINCIAL");
        request.setParticipationScope("All teams");
        request.setRegistrationStart("2026-06-29 00:00");
        request.setRegistrationEnd("2026-07-10 23:59");
        request.setCompetitionStart("2026-07-11 00:00");
        request.setLocation("Online");
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
        competition.setTitle("Competition");
        competition.setCategory("OTHER");
        competition.setCompetitionLevel("PROVINCIAL");
        competition.setParticipationScope("All teams");
        competition.setRegistrationStart("2026-06-29 00:00");
        competition.setRegistrationEnd("2026-07-10 23:59");
        competition.setCompetitionStart("2026-07-11 00:00");
        competition.setLocation("Online");
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

    private SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
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

    private static final class StubOperations extends CompetitionSqlOperations {
        private final Queue<List<?>> queryResults = new ArrayDeque<>();
        private final List<String> updates = new ArrayList<>();
        private final List<Object[]> updateArguments = new ArrayList<>();
        private final Queue<Integer> updateResults = new ArrayDeque<>();
        private int updateCount = 0;
        private int lastInsertIdQueries;

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
            updateArguments.add(args);
            return updateResults.isEmpty() ? updateCount : updateResults.remove();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.toLowerCase().contains("last_insert_id")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(11L);
            }
            if (requiredType == Long.class) {
                return requiredType.cast(0L);
            }
            return null;
        }
    }
}
