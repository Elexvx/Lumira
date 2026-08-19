package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PlatformSettingDefaultsPort;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.infrastructure.persistence.RowMapper;
import com.lumira.saas.modules.competition.support.CompetitionSessionAuthenticationFixture;
import com.lumira.saas.modules.competition.dto.CertificateDTO;
import com.lumira.saas.modules.competition.infrastructure.JdbcCertificateTemplateRepository;
import com.lumira.saas.modules.competition.infrastructure.JdbcCertificateRecordRepository;
import com.lumira.saas.modules.competition.repository.CertificateRecordRepository;
import com.lumira.saas.modules.competition.repository.CertificateTemplateRepository;
import com.lumira.saas.modules.competition.repository.CompetitionSettingsRepository;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import com.lumira.saas.modules.competition.support.CompetitionPermissionSnapshotFixture;
import com.lumira.saas.modules.competition.support.CompetitionTrustTestFixtures;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CertificateAppServiceTest {

    @Test
    void certificateApplicationServiceShouldNotOwnSqlOrLowLevelPersistence() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/app/CertificateAppService.java"));
        String templateAdapter = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/infrastructure/JdbcCertificateTemplateRepository.java"));
        String recordAdapter = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/infrastructure/JdbcCertificateRecordRepository.java"));

        assertThat(source).doesNotContain("CompetitionSqlOperations", "jdbcTemplate", "insert into certificate_",
                "update certificate_", "select count(1) from certificate_");
        assertThat(templateAdapter).contains("insert into certificate_template", "update certificate_template");
        assertThat(recordAdapter).contains("insert into certificate_batch", "insert into certificate_record",
                "insert into certificate_verify_log");
    }

    @Test
    void listBatchesShouldScopeNormalUserByCreator() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<CertificateVO.Batch>>any(), any(), any())).thenReturn(List.of());
        CertificateAppService service = service(jdbcTemplate);

        service.listBatches(user(Set.of("aiadc:certificate-batch:view")), 1, 10);

        verify(jdbcTemplate).queryForObject(
                org.mockito.ArgumentMatchers.contains("certificate_batch.created_by = ? and certificate_batch.created_by_uuid = ?"),
                eq(Long.class),
                eq(1001L),
                eq("user-uuid-1001")
        );
    }

    @Test
    void getRecordShouldHideRecordsCreatedByOtherUsers() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<CertificateVO.Record>>any(), any(), any(), any())).thenReturn(List.of());
        CertificateAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.getRecord(user(Set.of("aiadc:certificate:view")), 9001L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(jdbcTemplate).query(
                org.mockito.ArgumentMatchers.contains("r.created_by = ? and r.created_by_uuid = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<CertificateVO.Record>>any(),
                eq(9001L),
                eq(1001L),
                eq("user-uuid-1001")
        );
    }

    @Test
    void listRecordsShouldAllowGlobalPermissionWithoutCreatorFilter() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<CertificateVO.Record>>any(), any(), any())).thenReturn(List.of());
        CertificateAppService service = service(jdbcTemplate);

        service.listRecords(user(Set.of("*")), null, null, null, 1, 10);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<CertificateVO.Record>>any(),
                eq(0L),
                eq(10L)
        );
        assertThat(sqlCaptor.getValue()).doesNotContain("created_by = ?");
    }

    @Test
    void listBatchesShouldRejectUnauthenticatedUserBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(Set.of("*", "aiadc:certificate-batch:view"));
        currentUser.setAuthenticated(false);

        assertThatThrownBy(() -> service.listBatches(currentUser, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listBatchesShouldRejectBlankUsernameBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(Set.of("*", "aiadc:certificate-batch:view"));
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.listBatches(currentUser, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listBatchesShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(Set.of("*", "aiadc:certificate-batch:view"));
        currentUser.setSessionVersion(null);

        assertThatThrownBy(() -> service.listBatches(currentUser, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listBatchesShouldRejectMissingUserUuidBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(Set.of("*", "aiadc:certificate-batch:view"));
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> service.listBatches(currentUser, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void generateBatchShouldRejectMissingPermissionsVersionBeforeTemplateLookup() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(Set.of("*", "aiadc:certificate-batch:create"));
        currentUser.setPermissionsVersion(" ");

        assertThatThrownBy(() -> service.generateBatch(currentUser, batchRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createTemplateShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = new CertificateAppService(
                new JdbcCertificateTemplateRepository(jdbcTemplate),
                new JdbcCertificateRecordRepository(jdbcTemplate),
                new ObjectMapper(),
                mock(FileInternalApi.class),
                mock(CertificateRenderService.class),
                null,
                true
        );
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName("Award");

        assertThatThrownBy(() -> service.createTemplate(user(Set.of("aiadc:certificate-template:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createTemplateShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        CertificateAppService service = new CertificateAppService(
                new JdbcCertificateTemplateRepository(jdbcTemplate),
                new JdbcCertificateRecordRepository(jdbcTemplate),
                new ObjectMapper(),
                mock(FileInternalApi.class),
                mock(CertificateRenderService.class),
                CompetitionTrustTestFixtures.resolver(permissionSnapshotService, null, null),
                true
        );
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName("Award");

        assertThatThrownBy(() -> service.createTemplate(user(Set.of("aiadc:certificate-template:create")), request))
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
                .thenReturn(new CompetitionPermissionSnapshotFixture.PermissionSnapshot("permissions-2", Set.of("aiadc:certificate-template:view")));
        CurrentUser currentUser = user(Set.of("aiadc:certificate-template:view"));
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
    void listTemplatesShouldRequireTemplateViewPermissionBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.listTemplates(user(Set.of("aiadc:certificate-batch:view")), null, null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listTemplatesShouldRejectUnboundedQueryBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.listTemplates(user(Set.of("aiadc:certificate-template:view")), "x".repeat(129), null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listRecordsShouldRejectInvalidStatusBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.listRecords(user(Set.of("aiadc:certificate:view")), null, null, "all", 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listBatchesShouldRejectInvalidPageBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.listBatches(user(Set.of("aiadc:certificate-batch:view")), 0, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createTemplateShouldRequireCreatePermissionBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName("Award");

        assertThatThrownBy(() -> service.createTemplate(user(Set.of("aiadc:certificate-template:view")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createTemplateShouldRejectWhenLiveSnapshotRevokesCreatePermissionBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new CompetitionPermissionSnapshotFixture.PermissionSnapshot("permissions-2", Set.of("aiadc:certificate-template:view")));
        CertificateAppService service = service(jdbcTemplate, mock(FileInternalApi.class), permissionSnapshotService);
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName("Award");

        assertThatThrownBy(() -> service.createTemplate(user(Set.of("aiadc:certificate-template:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createTemplateShouldRejectDisabledTrustedIdentityBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "DISABLED"));
        CertificateAppService service =
                service(jdbcTemplate, mock(FileInternalApi.class), permissionSnapshotService, systemInternalApi, null);
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName("Award");

        assertThatThrownBy(() -> service.createTemplate(user(Set.of("aiadc:certificate-template:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
    }

    @Test
    void createTemplateShouldRejectTrustedIdentityWhenLiveUsernameIsUnavailableBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));
        CertificateAppService service =
                service(jdbcTemplate, mock(FileInternalApi.class), permissionSnapshotService, systemInternalApi, null);
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName("Award");

        assertThatThrownBy(() -> service.createTemplate(user(Set.of("aiadc:certificate-template:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
    }

    @Test
    void createTemplateShouldRefreshLiveUsernameBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class, invocation ->
                "update".equals(invocation.getMethod().getName())
                        ? 1
                        : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));
        CompetitionPermissionSnapshotFixture permissionSnapshotService = mock(CompetitionPermissionSnapshotFixture.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "alice-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new CompetitionPermissionSnapshotFixture.PermissionSnapshot(
                        "permissions-2",
                        Set.of("aiadc:certificate-template:create", "aiadc:certificate-template:view")));
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("insert into certificate_template"), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject("select last_insert_id()", Long.class)).thenReturn(10L);
        CertificateVO.Template template = new CertificateVO.Template();
        template.setId(10L);
        template.setTemplateCode("TPL-10");
        template.setTemplateName("Award");
        template.setStatus("DRAFT");
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<CertificateVO.Template>>any(), eq(10L)))
                .thenReturn(List.of(template));
        when(jdbcTemplate.update(
                org.mockito.ArgumentMatchers.contains("insert into certificate_template_version"),
                org.mockito.ArgumentMatchers.<Object[]>any()
        ))
                .thenReturn(1);
        CertificateAppService service =
                service(jdbcTemplate, mock(FileInternalApi.class), permissionSnapshotService, systemInternalApi, null);
        CurrentUser currentUser = user(Set.of("*"));
        currentUser.setUsername("alice-stale");
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName("Award");

        service.createTemplate(currentUser, request);

        assertThat(currentUser.getUsername()).isEqualTo("alice-live");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
    }

    @Test
    void createTemplateShouldRejectRevokedSessionTicketBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CompetitionSessionAuthenticationFixture sessionAuthenticationService = mock(CompetitionSessionAuthenticationFixture.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName("Award");
        CertificateAppService service =
                service(jdbcTemplate, mock(FileInternalApi.class), null, sessionAuthenticationService);

        assertThatThrownBy(() -> service.createTemplate(user(Set.of("aiadc:certificate-template:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createTemplateShouldRejectWhenTemplateInsertMissesBeforeLastInsertId() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        when(jdbcTemplate.update(
                org.mockito.ArgumentMatchers.contains("insert into certificate_template"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(0);
        CertificateAppService service = service(jdbcTemplate);
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName("Award");

        assertThatThrownBy(() -> service.createTemplate(user(Set.of("aiadc:certificate-template:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Certificate template changed, please retry");
                });

        verify(jdbcTemplate, never()).queryForObject(org.mockito.ArgumentMatchers.contains("last_insert_id"), eq(Long.class));
    }

    @Test
    void generateBatchShouldRequireCreatePermissionBeforeTemplateLookup() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.generateBatch(user(Set.of("aiadc:certificate-batch:view")), new CertificateDTO.BatchGenerateRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void generateBatchShouldRejectWhenBatchInsertMissesBeforeCertificateRecords() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateVO.TemplateVersion version = new CertificateVO.TemplateVersion();
        version.setId(11L);
        version.setTemplateId(10L);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<CertificateVO.TemplateVersion>>any(),
                eq(11L)
        )).thenReturn(List.of(version));
        when(jdbcTemplate.update(
                org.mockito.ArgumentMatchers.contains("insert into certificate_batch"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(0);
        CertificateAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.generateBatch(user(Set.of("aiadc:certificate-template:view", "aiadc:certificate-batch:create")), batchRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Certificate batch changed, please retry");
                });

        verify(jdbcTemplate, never()).queryForObject(org.mockito.ArgumentMatchers.contains("last_insert_id"), eq(Long.class));
        verify(jdbcTemplate, never()).update(org.mockito.ArgumentMatchers.contains("insert into certificate_record"), any());
    }

    @Test
    void templateVersionOperationsShouldRejectInvalidIdsBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);
        CurrentUser currentUser = user(Set.of("aiadc:certificate-template:view", "aiadc:certificate-template:publish"));

        assertThatThrownBy(() -> service.listVersions(currentUser, -1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.getVersion(currentUser, 0L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.publishTemplate(currentUser, null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void publishValidationShouldRejectBlankOrIncompleteCanvas() {
        CertificateAppService service = service(mock(CompetitionSqlOperations.class));
        CertificateVO.TemplateVersion draft = new CertificateVO.TemplateVersion();
        draft.setCanvasJson("{\"page\":{},\"elements\":[]}");

        assertThatThrownBy(() -> service.validatePublishableTemplate(draft))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).contains("cannot be empty");
                });

        draft.setCanvasJson("""
                {"elements":[
                  {"type":"text","text":"获奖证书"},
                  {"type":"text","fieldKey":"recipientName"},
                  {"type":"text","fieldKey":"awardName"}
                ]}
                """);
        assertThatThrownBy(() -> service.validatePublishableTemplate(draft))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getMessage())
                                .contains("competitionTitle", "issueDate", "verificationUrl"));
    }

    @Test
    void publishValidationShouldAcceptCompleteAwardCertificateCanvas() {
        CertificateAppService service = service(mock(CompetitionSqlOperations.class));
        CertificateVO.TemplateVersion draft = new CertificateVO.TemplateVersion();
        draft.setCanvasJson("""
                {"elements":[
                  {"type":"text","text":"获奖证书"},
                  {"type":"text","fieldKey":"recipientName"},
                  {"type":"text","fieldKey":"awardName"},
                  {"type":"text","fieldKey":"competitionTitle"},
                  {"type":"text","fieldKey":"issueDate"},
                  {"type":"qrcode","fieldKey":"verificationUrl"}
                ]}
                """);

        assertThatCode(() -> service.validatePublishableTemplate(draft)).doesNotThrowAnyException();
    }

    @Test
    void uploadBackgroundShouldRejectInvalidFileBeforeLookupOrUpload() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        CertificateAppService service = service(jdbcTemplate, fileInternalApi);
        MockMultipartFile textFile = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.uploadBackground(user(Set.of("aiadc:certificate-template:update")), 10L, textFile))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(jdbcTemplate);
        verify(fileInternalApi, never()).uploadImageForUser(any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void generateBatchShouldRejectOversizedRecordBatchBeforeTemplateLookup() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);
        CertificateDTO.BatchGenerateRequest request = batchRequest();
        List<CertificateDTO.CertificateDataRequest> records = new ArrayList<>();
        for (int i = 0; i < 201; i += 1) {
            records.add(certificateRow("Recipient " + i));
        }
        request.setRecords(records);

        assertThatThrownBy(() -> service.generateBatch(user(Set.of("aiadc:certificate-batch:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void verifyByTokenShouldRejectOversizedTokenBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.verifyByToken("x".repeat(129), "127.0.0.1", "agent"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void revokeRecordShouldRequireRevokePermissionBeforeRecordLookup() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.revokeRecord(user(Set.of("aiadc:certificate:view")), 9001L, "wrong"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void certificateBatchAndRecordUpdatesShouldPersistTrustedUpdaterUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/infrastructure/JdbcCertificateRecordRepository.java"))
                + Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/app/CertificateAppService.java"));

        assertThat(source).contains(
                "updated_by, updated_by_uuid, deleted",
                "update certificate_batch",
                "where id = ? and created_by = ? and created_by_uuid = ? and deleted = 0",
                "set status = 'REVOKED', revoked_reason = ?, revoked_at = ?, updated_by = ?, updated_by_uuid = ?",
                "where id = ? and certificate_no = ? and batch_id = ? and status = ? and deleted = 0",
                "update certificate_record set certificate_file_url = ?, updated_by = ?, updated_by_uuid = ?",
                "where id = ? and certificate_no = ? and batch_id = ? and status = ?",
                "set certificate_file_url = ?, status = 'ISSUED', updated_by = ?, updated_by_uuid = ?",
                "where id = ? and certificate_no = ? and batch_id = ? and status = 'GENERATING'",
                "set status = 'FAILED', certificate_file_url = null",
                "status in ('GENERATING', 'ISSUED')",
                "and created_by = ? and created_by_uuid = ? and deleted = 0",
                "Certificate record changed, please retry"
        );
    }

    @Test
    void awardGrantUpsertShouldQualifyTargetStatusInJoinedInsertSelect() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lumira/saas/modules/competition/infrastructure/JdbcCertificateRecordRepository.java"));

        assertThat(source)
                .contains("competition_award_grant.status in ('GRANTED', 'REVOKED')")
                .contains("competition_award_grant.certificate_record_id is null")
                .contains("competition_award_grant.award_name")
                .contains("competition_award_grant.rank_no")
                .contains("competition_award_grant.decision")
                .contains("competition_award_grant.updated_by")
                .contains("aggregate.rank_no between ? and ?")
                .contains("json_extract(registration.team_snapshot_json, '$.teamName')")
                .contains("json_extract(registration.project_snapshot_json, '$.title')")
                .doesNotContain("aiadc_project")
                .doesNotContain("left join team")
                .doesNotContain("sys_user")
                .contains("status = if(")
                .doesNotContain("award_name = if(status = 'GRANTED'");
    }

    @Test
    void grantPublishedAwardsShouldRejectOverlappingRulesBeforeDatabaseAccess() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);
        CertificateDTO.AwardGrantRequest request = new CertificateDTO.AwardGrantRequest();
        request.setReviewBatchId(100L);
        request.setRules(List.of(
                awardRule("一等奖", 1, 3),
                awardRule("二等奖", 3, 5)
        ));

        assertThatThrownBy(() -> service.grantPublishedAwards(
                user(Set.of("aiadc:certificate-batch:create")), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).contains("cannot overlap");
                });

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void saveCompetitionAwardRulesShouldAcceptMainExcellenceAndTwentySpecialAwards() {
        CertificateRecordRepository recordRepository = mock(CertificateRecordRepository.class);
        when(recordRepository.savePublishedReviewAwardRules(
                eq(900L), anyString(), eq(1001L), eq("user-uuid-1001"), any()
        )).thenReturn(1);
        CertificateAppService service = new CertificateAppService(
                mock(CertificateTemplateRepository.class),
                recordRepository,
                new ObjectMapper(),
                mock(FileInternalApi.class),
                mock(CertificateRenderService.class),
                null,
                mock(CompetitionSettingsRepository.class),
                false
        );
        List<CertificateDTO.AwardRuleRequest> rules = new ArrayList<>();
        for (int index = 1; index <= 24; index++) {
            rules.add(awardRule("奖项" + index, index, index));
        }

        List<CertificateVO.AwardRule> saved = service.saveCompetitionAwardRules(
                user(Set.of("aiadc:certificate-batch:create")), 900L, rules);

        assertThat(saved).hasSize(24);
        verify(recordRepository).savePublishedReviewAwardRules(
                eq(900L), anyString(), eq(1001L), eq("user-uuid-1001"), any());
    }

    @Test
    void competitionAwardRulesShouldConvertPercentageQuotaUsingPublishedCandidateCount() {
        CompetitionSettingsRepository settingsRepository = mock(CompetitionSettingsRepository.class);
        CertificateRecordRepository recordRepository = mock(CertificateRecordRepository.class);
        CompetitionVO.ConfigSet configSet = new CompetitionVO.ConfigSet();
        configSet.setId(11L);
        CompetitionVO.ConfigItem settingsItem = new CompetitionVO.ConfigItem();
        settingsItem.setItemType("AWARD_SETTINGS");
        settingsItem.setItemKey("award-rules");
        settingsItem.setContentJson("""
                {"version":2,"mainQuotaType":"PERCENTAGE",
                 "mainAwards":[
                   {"awardName":"一等奖","quota":10},
                   {"awardName":"二等奖","quota":20},
                   {"awardName":"三等奖","quota":10}
                 ],
                 "excellence":{"enabled":true,"quota":20},
                 "specialAwards":[]}
                """);
        when(settingsRepository.findCurrentConfigSet("competition-1")).thenReturn(configSet);
        when(settingsRepository.findConfigItems("competition-1", 11L, Set.of("AWARD_SETTINGS")))
                .thenReturn(List.of(settingsItem));
        when(recordRepository.findPublishedReviewCandidateCount(900L)).thenReturn(25);
        CertificateAppService service = new CertificateAppService(
                mock(CertificateTemplateRepository.class),
                recordRepository,
                new ObjectMapper(),
                mock(FileInternalApi.class),
                mock(CertificateRenderService.class),
                null,
                settingsRepository,
                false
        );

        List<CertificateVO.AwardRule> rules = service.listCompetitionAwardRules(
                user(Set.of("aiadc:certificate-batch:create")), "competition-1", 900L);

        assertThat(rules).extracting(CertificateVO.AwardRule::getMinRank, CertificateVO.AwardRule::getMaxRank)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 3),
                        org.assertj.core.groups.Tuple.tuple(4, 8),
                        org.assertj.core.groups.Tuple.tuple(9, 11),
                        org.assertj.core.groups.Tuple.tuple(12, 16)
                );
    }

    @Test
    void savedReviewBatchAwardRulesShouldOverrideCompetitionSettings() {
        CompetitionSettingsRepository settingsRepository = mock(CompetitionSettingsRepository.class);
        CertificateRecordRepository recordRepository = mock(CertificateRecordRepository.class);
        when(recordRepository.findPublishedReviewAwardRulesJson(901L)).thenReturn("""
                [{"awardName":"一等奖","minRank":1,"maxRank":2},
                 {"awardName":"专项奖","minRank":4,"maxRank":4}]
                """);
        CertificateAppService service = new CertificateAppService(
                mock(CertificateTemplateRepository.class),
                recordRepository,
                new ObjectMapper(),
                mock(FileInternalApi.class),
                mock(CertificateRenderService.class),
                null,
                settingsRepository,
                false
        );

        List<CertificateVO.AwardRule> rules = service.listCompetitionAwardRules(
                user(Set.of("aiadc:certificate-batch:create")), "competition-1", 901L);

        assertThat(rules).extracting(CertificateVO.AwardRule::getAwardName,
                        CertificateVO.AwardRule::getMinRank, CertificateVO.AwardRule::getMaxRank)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("一等奖", 1, 2),
                        org.assertj.core.groups.Tuple.tuple("专项奖", 4, 4)
                );
        verifyNoInteractions(settingsRepository);
    }

    @Test
    void legacyCompetitionAwardRulesShouldRemainFixedWhenQuotaTypeIsMissing() {
        CompetitionSettingsRepository settingsRepository = mock(CompetitionSettingsRepository.class);
        CertificateRecordRepository recordRepository = mock(CertificateRecordRepository.class);
        CompetitionVO.ConfigSet configSet = new CompetitionVO.ConfigSet();
        configSet.setId(12L);
        CompetitionVO.ConfigItem settingsItem = new CompetitionVO.ConfigItem();
        settingsItem.setItemType("AWARD_SETTINGS");
        settingsItem.setItemKey("award-rules");
        settingsItem.setContentJson("""
                {"version":1,"rules":[
                  {"awardName":"一等奖","quota":1},
                  {"awardName":"二等奖","quota":2},
                  {"awardName":"三等奖","quota":3},
                  {"awardName":"优秀奖","quota":5}
                ]}
                """);
        when(settingsRepository.findCurrentConfigSet("competition-2")).thenReturn(configSet);
        when(settingsRepository.findConfigItems("competition-2", 12L, Set.of("AWARD_SETTINGS")))
                .thenReturn(List.of(settingsItem));

        CertificateAppService service = new CertificateAppService(
                mock(CertificateTemplateRepository.class),
                recordRepository,
                new ObjectMapper(),
                mock(FileInternalApi.class),
                mock(CertificateRenderService.class),
                null,
                settingsRepository,
                false
        );

        List<CertificateVO.AwardRule> rules = service.listCompetitionAwardRules(
                user(Set.of("aiadc:certificate-batch:create")), "competition-2", null);

        assertThat(rules).extracting(CertificateVO.AwardRule::getMinRank, CertificateVO.AwardRule::getMaxRank)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 1),
                        org.assertj.core.groups.Tuple.tuple(2, 3),
                        org.assertj.core.groups.Tuple.tuple(4, 6),
                        org.assertj.core.groups.Tuple.tuple(7, 11)
                );
        verifyNoInteractions(recordRepository);
    }

    @Test
    void certificateAwardSourcesShouldExposePublishedBatchNamesWithoutReviewPermissions() throws Exception {
        String repositorySource = Files.readString(Path.of(
                "src/main/java/com/lumira/saas/modules/competition/infrastructure/JdbcCertificateRecordRepository.java"));
        String serviceSource = Files.readString(Path.of(
                "src/main/java/com/lumira/saas/modules/competition/app/CertificateAppService.java"));

        assertThat(repositorySource).contains(
                "findPublishedAwardSources",
                "competition.title as competitionTitle",
                "stage.stage_name as stageName",
                "publication.publication_version as publicationVersion",
                "batch.status = 'PUBLISHED'",
                "grant_record.status = 'ISSUED'"
        );
        assertThat(serviceSource).contains(
                "listPublishedAwardSources",
                "requirePermission(currentUser, BATCH_CREATE)",
                "recordRepository.findPublishedAwardSources()"
        );
    }

    @Test
    void awardCertificateConcurrencyFeedbackShouldDistinguishIssuedAndMissingGrants() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lumira/saas/modules/competition/app/CertificateAppService.java"));

        assertThat(source).contains(
                "findAwardGrantsByAnyIds",
                "selected award grant(s) no longer exist",
                "selected award certificate(s) have already been issued",
                "Selected award grant status changed"
        );
    }

    @Test
    void certificateTemplateAndVersionWritesShouldPersistTrustedUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/infrastructure/JdbcCertificateTemplateRepository.java"));

        assertThat(source).contains(
                "created_by, created_by_uuid, updated_by, updated_by_uuid, deleted",
                "update certificate_template set status = 'ARCHIVED', updated_by = ?, updated_by_uuid = ?",
                "where id = ? and template_code = ? and status = ? and deleted = 0",
                "variable_schema_json = ?, updated_by = ?, updated_by_uuid = ?",
                "where id = ? and template_id = ? and version = ? and status = 'DRAFT' and deleted = 0",
                "set background_file_id = ?, background_url = ?, updated_by = ?, updated_by_uuid = ?",
                "update certificate_template_version set status = 'PUBLISHED', updated_by = ?, updated_by_uuid = ?",
                "where id = ? and template_id = ? and version = ? and status = 'DRAFT' and deleted = 0",
                "update certificate_template set status = 'PUBLISHED', latest_version = ?, updated_by = ?, updated_by_uuid = ?"
        );
    }

    @Test
    void certificateWritesShouldRejectLostSnapshotUpdates() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/app/CertificateAppService.java"));

        assertThat(source).contains(
                "requireCertificateWrite(updated, \"Certificate template changed, please retry\")",
                "requireCertificateWrite(versionInserted, \"Certificate template version changed, please retry\")",
                "requireCertificateWrite(draftInserted, \"Certificate template version changed, please retry\")",
                "requireCertificateWrite(updated, \"Certificate template version changed, please retry\")",
                "requireCertificateWrite(versionUpdated, \"Certificate template version changed, please retry\")",
                "requireCertificateWrite(templateUpdated, \"Certificate template changed, please retry\")",
                "requireCertificateWrite(batchUpdated, \"Certificate batch changed, please retry\")",
                "requireCertificateWrite(updated, \"Certificate record changed, please retry\")",
                "private void requireCertificateWrite(int updated, String message)"
        );
    }

    @Test
    void updateTemplateShouldRejectWhenSnapshotWriteMisses() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);
        CertificateVO.Template existing = new CertificateVO.Template();
        existing.setId(10L);
        existing.setTemplateCode("tpl-10");
        existing.setTemplateName("Award");
        existing.setSceneType("COMPETITION_AWARD");
        existing.setStatus("DRAFT");
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<CertificateVO.Template>>any(),
                eq(10L)
        )).thenReturn(List.of(existing));
        when(jdbcTemplate.update(
                org.mockito.ArgumentMatchers.contains("update certificate_template"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(0);
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName("Award 2");

        assertThatThrownBy(() -> service.updateTemplate(user(Set.of("aiadc:certificate-template:view", "aiadc:certificate-template:update")), 10L, request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Certificate template changed, please retry");
                });
    }

    @Test
    void getRecordForDownloadShouldRequireDownloadPermissionBeforeRecordLookup() {
        CompetitionSqlOperations jdbcTemplate = mock(CompetitionSqlOperations.class);
        CertificateAppService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.getRecordForDownload(user(Set.of("aiadc:certificate:view")), 9001L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    private CertificateAppService service(CompetitionSqlOperations jdbcTemplate) {
        return service(jdbcTemplate, mock(FileInternalApi.class));
    }

    private CertificateAppService service(CompetitionSqlOperations jdbcTemplate, FileInternalApi fileInternalApi) {
        return service(jdbcTemplate, fileInternalApi, null);
    }

    private CertificateAppService service(
            CompetitionSqlOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            CompetitionPermissionSnapshotFixture permissionSnapshotService
    ) {
        return service(jdbcTemplate, fileInternalApi, permissionSnapshotService, null);
    }

    private CertificateAppService service(
            CompetitionSqlOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            CompetitionPermissionSnapshotFixture permissionSnapshotService,
            CompetitionSessionAuthenticationFixture sessionAuthenticationService
    ) {
        return service(jdbcTemplate, fileInternalApi, permissionSnapshotService, null, sessionAuthenticationService);
    }

    private CertificateAppService service(
            CompetitionSqlOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            CompetitionPermissionSnapshotFixture permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            CompetitionSessionAuthenticationFixture sessionAuthenticationService
    ) {
        CertificateAppService service = new CertificateAppService(
                new JdbcCertificateTemplateRepository(jdbcTemplate),
                new JdbcCertificateRecordRepository(jdbcTemplate),
                new ObjectMapper(),
                fileInternalApi,
                mock(CertificateRenderService.class),
                CompetitionTrustTestFixtures.resolver(
                        permissionSnapshotService,
                        systemInternalApi,
                        sessionAuthenticationService
                ),
                systemInternalApi != null || sessionAuthenticationService != null
        );
        PlatformSettingDefaultsPort defaultsPort = groupCode -> certificateDefaultValues();
        @SuppressWarnings("unchecked")
        ObjectProvider<PlatformSettingDefaultsPort> defaultsProvider = mock(ObjectProvider.class);
        when(defaultsProvider.getIfAvailable()).thenReturn(defaultsPort);
        service.setPlatformSettingDefaultsPortProvider(defaultsProvider);
        return service;
    }

    @Test
    void certificateCanvasBusinessDefaultsShouldBeDatabaseOwned() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/app/CertificateAppService.java"));
        String adapter = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/infrastructure/JdbcCertificateTemplateRepository.java"));
        String port = Files.readString(Path.of("../../libs/lumira-common-api/src/main/java/com/lumira/api/system/PlatformSettingDefaultsPort.java"));
        String systemAdapter = Files.readString(Path.of("../../services/lumira-system/src/main/java/com/lumira/saas/modules/system/integration/PlatformSettingDefaultsPortAdapter.java"));
        String sql = Files.readString(Path.of("../../sql/upgrade-certificate-setting-definition-persistence-v1.sql"));

        assertThat(sql).contains("certificate.canvas.default-width", "certificate.canvas.default-json",
                "certificate.canvas.default-variable-schema-json", "certificate.public.organizer",
                "certificate.rule.template-statuses", "certificate.rule.scene-types",
                "certificate.number.template-prefix", "certificate.number.verification-code-length",
                "certificate.preview.batch-name", "recipientName", "verificationUrl");
        assertThat(port).contains("interface PlatformSettingDefaultsPort", "findEnabledDefaults");
        assertThat(systemAdapter).contains("findSettingDefaults");
        assertThat(adapter).doesNotContain("sys_platform_setting_definition");
        assertThat(source)
                .contains("PlatformSettingDefaultsPort", "certificateDefaultDefinitions")
                .doesNotContain("private static final String DEFAULT_VARIABLE_SCHEMA =", "private String defaultCanvas()",
                        "{\"page\":{\"width\":3508", "{\"key\":\"recipientName\"", "setOrganizer(\"Lumira\")",
                        "Set.of(\"DRAFT\", \"PUBLISHED\", \"ARCHIVED\")", "\"CTPL-\" +", "\"CB-\" +",
                        "\"CERT-\" +", "randomDigits(6)", "setBatchName(defaultText(request.getBatchName(), \"Preview\"))");
    }

    private List<Map<String, Object>> certificateDefaultRows() {
        return List.of(
                setting("certificate.canvas.default-width", "3508"),
                setting("certificate.canvas.default-height", "2480"),
                setting("certificate.canvas.default-orientation", "LANDSCAPE"),
                setting("certificate.canvas.default-unit", "PX"),
                setting("certificate.canvas.default-dpi", "300"),
                setting("certificate.canvas.default-json", "{}"),
                setting("certificate.canvas.default-variable-schema-json", "{}"),
                setting("certificate.public.organizer", "Lumira"),
                setting("certificate.rule.template-statuses", "DRAFT,PUBLISHED,ARCHIVED"),
                setting("certificate.rule.scene-types", "COMPETITION_AWARD,PARTICIPATION,CUSTOM"),
                setting("certificate.rule.source-types", "MANUAL,IMPORT,REGISTRATION,AWARD_RESULT"),
                setting("certificate.rule.recipient-types", "USER,TEAM,PROJECT,CUSTOM"),
                setting("certificate.rule.record-statuses", "GENERATING,ISSUED,FAILED,REVOKED"),
                setting("certificate.rule.default-scene-type", "COMPETITION_AWARD"),
                setting("certificate.rule.default-source-type", "MANUAL"),
                setting("certificate.rule.default-recipient-type", "CUSTOM"),
                setting("certificate.number.template-prefix", "CTPL-"),
                setting("certificate.number.batch-prefix", "CB-"),
                setting("certificate.number.certificate-prefix", "CERT-"),
                setting("certificate.number.timestamp-format", "yyyyMMddHHmmssSSS"),
                setting("certificate.number.verification-code-length", "6"),
                setting("certificate.preview.batch-no", "PREVIEW"),
                setting("certificate.preview.batch-name", "Preview"),
                setting("certificate.preview.status", "PREVIEW")
        );
    }

    private Map<String, String> certificateDefaultValues() {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : certificateDefaultRows()) {
            values.put(String.valueOf(row.get("configKey")), String.valueOf(row.get("configValue")));
        }
        return values;
    }

    private Map<String, Object> setting(String key, String value) {
        return Map.of("configKey", key, "configValue", value);
    }

    private CertificateDTO.BatchGenerateRequest batchRequest() {
        CertificateDTO.BatchGenerateRequest request = new CertificateDTO.BatchGenerateRequest();
        request.setTemplateId(10L);
        request.setTemplateVersionId(11L);
        request.setSourceType("MANUAL");
        request.setRecords(List.of(certificateRow("Alice")));
        return request;
    }

    private CertificateDTO.CertificateDataRequest certificateRow(String recipientName) {
        CertificateDTO.CertificateDataRequest row = new CertificateDTO.CertificateDataRequest();
        row.setRecipientName(recipientName);
        row.setAwardName("Gold");
        return row;
    }

    private CertificateDTO.AwardRuleRequest awardRule(String awardName, int minRank, int maxRank) {
        CertificateDTO.AwardRuleRequest rule = new CertificateDTO.AwardRuleRequest();
        rule.setAwardName(awardName);
        rule.setMinRank(minRank);
        rule.setMaxRank(maxRank);
        return rule;
    }

    private CurrentUser user(Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("alice");
        currentUser.setSessionId("session-1001");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(permissions);
        return currentUser;
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
}
