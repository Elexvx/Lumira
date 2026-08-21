package com.lumira.saas.modules.system.sensitive.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.api.text.TextModerationPort;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.sensitive.dto.SensitiveWordDTO;
import com.lumira.saas.modules.system.sensitive.infrastructure.JdbcSensitiveWordDictionaryRepository;
import com.lumira.saas.modules.system.sensitive.infrastructure.JdbcSensitiveWordManagementRepository;
import com.lumira.saas.modules.system.sensitive.repository.SensitiveWordManagementRepository;
import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SensitiveWordServiceTest {

    private static SensitiveWordManagementRepository repository(MyBatisQueryOperations database) {
        return new JdbcSensitiveWordManagementRepository(database);
    }

    @Test
    void createWordShouldRequireManagePermissionAtServiceLayer() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService
        );

        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.createWord(currentUser(Set.of("plugin:sensitive-words:view")), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.existsCallCount).isZero();
    }

    @Test
    void createWordShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService
        );

        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.createWord(missingSessionVersionUser(), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.existsCallCount).isZero();
    }

    @Test
    void createWordShouldRejectMissingUserUuidBeforePluginCheckAndDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService
        );
        CurrentUser currentUser = currentUser();
        currentUser.setUserUuid(" ");
        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.createWord(currentUser, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.existsCallCount).isZero();
    }

    @Test
    void createWordShouldRejectWhenLiveSnapshotRevokesManagePermissionBeforePluginCheckAndDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("plugin:sensitive-words:view")));
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService,
                permissionSnapshotService
        );
        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.createWord(currentUser(Set.of("plugin:sensitive-words:manage")), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void createWordShouldRejectDisabledTrustedUserIdentityBeforePluginCheckAndDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "admin-live", "DISABLED"));
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService,
                new SensitiveWordDictionaryCache(
                        new JdbcSensitiveWordDictionaryRepository(queryOperations),
                        new SensitiveWordDictionaryVersionService(),
                        new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                ),
                new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.createWord(currentUser(Set.of("plugin:sensitive-words:manage")), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void createWordShouldRejectRevokedSessionTicketBeforePluginCheckAndDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 2001L, "user-uuid-2001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Login required"));
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService,
                new SensitiveWordDictionaryCache(
                        new JdbcSensitiveWordDictionaryRepository(queryOperations),
                        new SensitiveWordDictionaryVersionService(),
                        new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                ),
                new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                mock(PermissionSnapshotService.class),
                sessionAuthenticationService
        );
        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.createWord(currentUser(Set.of("plugin:sensitive-words:manage")), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "admin-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("*")));
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService,
                new SensitiveWordDictionaryCache(
                        new JdbcSensitiveWordDictionaryRepository(queryOperations),
                        new SensitiveWordDictionaryVersionService(),
                        new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                ),
                new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(0L);
        Method method = SensitiveWordService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(2001L, "user-uuid-2001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void createWordShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService,
                new SensitiveWordDictionaryCache(
                        new JdbcSensitiveWordDictionaryRepository(queryOperations),
                        new SensitiveWordDictionaryVersionService(),
                        new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                ),
                new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                null,
                null,
                null
        );
        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.createWord(currentUser(Set.of("plugin:sensitive-words:manage")), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void createWordShouldRejectBlankLiveUsernameBeforePluginCheckAndDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", " ", "ENABLED"));
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService,
                new SensitiveWordDictionaryCache(
                        new JdbcSensitiveWordDictionaryRepository(queryOperations),
                        new SensitiveWordDictionaryVersionService(),
                        new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                ),
                new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        BizException exception = assertThrows(BizException.class, () -> service.createWord(currentUser(Set.of("plugin:sensitive-words:manage", "plugin:sensitive-words:view")), wordRequest("hello")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void createWordShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(null);
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService,
                new SensitiveWordDictionaryCache(
                        new JdbcSensitiveWordDictionaryRepository(queryOperations),
                        new SensitiveWordDictionaryVersionService(),
                        new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                ),
                new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                permissionSnapshotService,
                null,
                null
        );
        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.createWord(currentUser(Set.of("plugin:sensitive-words:manage")), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });
        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void importWordsShouldRejectMissingPermissionsVersionBeforeFileReadAndDatabaseAccess() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService
        );
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion(" ");
        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> service.importWords(currentUser, file))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(pluginStateService, file);
        assertThat(queryOperations.batchLookupCallCount).isZero();
    }

    @Test
    void importWordsShouldRequireImportPermissionAtServiceLayer() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService
        );

        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> service.importWords(currentUser(Set.of("plugin:sensitive-words:manage")), file))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(pluginStateService, file);
        assertThat(queryOperations.batchLookupCallCount).isZero();
    }

    @Test
    void listWordsShouldRejectBlankUsernameBeforePluginCheckAndDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService
        );
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.listWords(currentUser, null, null, 1, 10))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.lastListSql).isEmpty();
    }

    @Test
    void resourceOperationsShouldRejectInvalidIdsBeforePluginCheckAndDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService
        );

        assertThatThrownBy(() -> service.getWord(currentUser(Set.of("plugin:sensitive-words:view")), 0L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateWord(currentUser(Set.of("plugin:sensitive-words:manage")), -1L, new SensitiveWordDTO.UpsertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateStatus(currentUser(Set.of("plugin:sensitive-words:manage")), null, Boolean.TRUE))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.deleteWord(currentUser(Set.of("plugin:sensitive-words:manage")), 0L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(pluginStateService);
        assertThat(queryOperations.lastListSql).isEmpty();
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void createWordShouldRejectDuplicateWordViaExistsCheck() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.wordExists = true;
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                mock(SensitiveWordPluginStateService.class)
        );

        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("敏感词");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        CurrentUser currentUser = currentUser();
        assertThatThrownBy(() -> service.createWord(currentUser, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
        assertThat(queryOperations.existsCallCount).isEqualTo(1);
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void createWordShouldRejectWhenInsertMisses() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.updateResult = 0;
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                mock(SensitiveWordPluginStateService.class)
        );

        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.createWord(currentUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Sensitive word changed, please retry");
                });
        assertThat(queryOperations.updateCallCount).isEqualTo(1);
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void createWordShouldRefreshLiveUsernameBeforeInsert() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("plugin:sensitive-words:manage", "plugin:sensitive-words:view")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "  admin-live  ", "ENABLED"));
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                pluginStateService,
                new SensitiveWordDictionaryCache(
                        new JdbcSensitiveWordDictionaryRepository(queryOperations),
                        new SensitiveWordDictionaryVersionService(),
                        new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                ),
                new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);
        CurrentUser currentUser = currentUser(Set.of("plugin:sensitive-words:manage", "plugin:sensitive-words:view"));
        currentUser.setUsername("admin-stale");

        service.createWord(currentUser, request);

        assertThat(currentUser.getUsername()).isEqualTo("admin-live");
    }

    @Test
    void importWordsShouldRejectExistingWordWithOneBatchLookup() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.wordExists = true;
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                mock(SensitiveWordPluginStateService.class)
        );

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("words.txt");
        when(file.getBytes()).thenReturn("敏感词".getBytes());

        SensitiveWordVO.ImportResult result = service.importWords(currentUser(), file);

        assertThat(result.getDuplicated()).isEqualTo(1);
        assertThat(queryOperations.existsCallCount).isZero();
        assertThat(queryOperations.batchLookupCallCount).isEqualTo(1);
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    @Test
    void importWordsShouldPersistTrustedUserUuidWithNumericAuditFields() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.updateResult = 2;
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                mock(SensitiveWordPluginStateService.class)
        );
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("words.txt");
        when(file.getBytes()).thenReturn("blocked\nreview".getBytes());

        SensitiveWordVO.ImportResult result = service.importWords(currentUser(), file);

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(queryOperations.lastUpdateSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(queryOperations.lastUpdateArgs).contains(2001L, "user-uuid-2001");
    }

    @Test
    void importWordsShouldRejectPartialBatchInsert() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.updateResult = 1;
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                mock(SensitiveWordPluginStateService.class)
        );
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("words.txt");
        when(file.getSize()).thenReturn(14L);
        when(file.getBytes()).thenReturn("blocked\nreview".getBytes());

        assertThatThrownBy(() -> service.importWords(currentUser(), file))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Sensitive word import changed, please retry");
                });
        assertThat(queryOperations.updateCallCount).isEqualTo(1);
    }

    @Test
    void sensitiveWordWritesShouldPersistTrustedUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/sensitive/infrastructure/JdbcSensitiveWordManagementRepository.java"));
        String serviceSource = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/sensitive/app/SensitiveWordService.java"));

        assertThat(source).contains(
                "created_by, created_by_uuid, created_at, updated_by, updated_by_uuid",
                "updated_by = ?, updated_by_uuid = ?",
                "where id = ? and normalized_word = ? and deleted = 0"
        );
        assertThat(serviceSource).contains("Sensitive word changed, please retry");
    }

    @Test
    void updateWordShouldBindFinalWriteToLoadedNormalizedWord() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                mock(SensitiveWordPluginStateService.class)
        );
        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord("blocked-next");
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);

        service.updateWord(currentUser(Set.of("plugin:sensitive-words:manage", "plugin:sensitive-words:view")), 88L, request);

        assertThat(queryOperations.lastUpdateSql).contains("where id = ? and normalized_word = ? and deleted = 0");
        assertThat(queryOperations.lastUpdateArgs).contains(88L, "blocked");
    }

    @Test
    void importWordsShouldRejectOversizedTextFileBeforeReadingBytes() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                mock(SensitiveWordPluginStateService.class)
        );

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("words.txt");
        when(file.getSize()).thenReturn((1L * 1024L * 1024L) + 1L);

        assertThatThrownBy(() -> service.importWords(currentUser(), file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(file, never()).getBytes();
        assertThat(queryOperations.batchLookupCallCount).isZero();
    }

    @Test
    void importWordsShouldRejectTooManyFragmentsBeforeDatabaseLookup() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                mock(SensitiveWordPluginStateService.class)
        );
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("words.txt");
        when(file.getSize()).thenReturn(40_000L);
        when(file.getBytes()).thenReturn(String.join("\n", java.util.Collections.nCopies(5001, "word")).getBytes());

        assertThatThrownBy(() -> service.importWords(currentUser(), file))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        assertThat(queryOperations.batchLookupCallCount).isZero();
        assertThat(queryOperations.updateCallCount).isZero();
    }

    @Test
    void listWordsShouldSkipCountForFirstShortPage() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SensitiveWordService service = new SensitiveWordService(
                repository(queryOperations),
                mock(TextModerationPort.class),
                mock(SensitiveWordPluginStateService.class)
        );

        var response = service.listWords(currentUser(), null, null, 1, 10);

        assertThat(response.getRecords()).isEmpty();
        assertThat(response.getTotal()).isZero();
        assertThat(queryOperations.countQueryCalled).isFalse();
        assertThat(queryOperations.lastListSql).contains("from sys_sensitive_word");
    }

    @Test
    void checkTextShouldNotBlockLogOnlyWord() {
        SensitiveWordDictionaryCache dictionaryCache = mock(SensitiveWordDictionaryCache.class);
        when(dictionaryCache.getMatcher()).thenReturn(new SensitiveWordMatcher(List.of(
                new SensitiveWordMatcher.DictionaryEntry(1L, "敏感词", "敏感词", "DEFAULT", "MEDIUM", "LOG_ONLY", 20)
        )));
        SensitiveWordService service = new SensitiveWordService(
                repository(new RecordingQueryOperations()),
                mock(TextModerationPort.class),
                mock(SensitiveWordPluginStateService.class),
                dictionaryCache,
                new SensitiveWordMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
        );

        SensitiveWordVO.CheckResult result = service.checkText(currentUser(), "这里有敏感词", "content");

        assertThat(result.isHit()).isTrue();
        assertThat(result.isBlocked()).isFalse();
        assertThat(result.getMatches()).singleElement().satisfies(match -> {
            assertThat(match.getMaskedWord()).isEqualTo("敏*词");
            assertThat(match.getAction()).isEqualTo("LOG_ONLY");
        });
    }

    private CurrentUser currentUser() {
        return currentUser(Set.of("*"));
    }

    private CurrentUser currentUser(Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(permissions);
        return currentUser;
    }

    private CurrentUser missingSessionVersionUser() {
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);
        return currentUser;
    }

    private SensitiveWordDTO.UpsertRequest wordRequest(String word) {
        SensitiveWordDTO.UpsertRequest request = new SensitiveWordDTO.UpsertRequest();
        request.setWord(word);
        request.setCategory("DEFAULT");
        request.setSeverity("MEDIUM");
        request.setEnabled(Boolean.TRUE);
        return request;
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

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean wordExists;
        private boolean countQueryCalled;
        private int existsCallCount;
        private int batchLookupCallCount;
        private int updateCallCount;
        private int updateResult = 1;
        private String lastUpdateSql = "";
        private List<Object> lastUpdateArgs = List.of();
        private String lastListSql = "";

        @Override
        public int update(String sql, Object... args) {
            updateCallCount += 1;
            lastUpdateSql = sql;
            lastUpdateArgs = Arrays.asList(args);
            return updateResult;
        }

        @Override
        public boolean exists(String sql, Object... args) {
            existsCallCount += 1;
            return wordExists && sql.contains("from sys_sensitive_word");
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
            }
            if (sql.contains("select last_insert_id()")) {
                return requiredType.cast(2001L);
            }
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from sys_sensitive_word") && sql.contains("where id = ?")) {
                SensitiveWordVO.WordRecord record = new SensitiveWordVO.WordRecord();
                record.setId(args != null && args.length > 0 && args[0] instanceof Number number ? number.longValue() : 88L);
                record.setWord("blocked");
                record.setNormalizedWord("blocked");
                record.setCategory("DEFAULT");
                record.setSeverity("MEDIUM");
                record.setAction("BLOCK");
                record.setEnabled(true);
                return (T) record;
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (args.length > 0) {
                String value = switch (String.valueOf(args[0])) {
                    case "sys_sensitive_word_blocking_action" -> "BLOCK";
                    case "sys_sensitive_word_default_category" -> "DEFAULT";
                    case "sys_sensitive_word_import_category" -> "IMPORTED";
                    case "sys_sensitive_word_default_severity" -> "MEDIUM";
                    default -> null;
                };
                if (value != null) return List.of(Map.of("itemValue", value));
                if ("sys_sensitive_word_action".equals(args[0])) {
                    return List.of(Map.of("itemValue", "BLOCK"), Map.of("itemValue", "LOG_ONLY"));
                }
            }
            return List.of();
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("normalized_word in")) {
                batchLookupCallCount += 1;
                return wordExists ? List.of(requiredType.cast(args[0])) : List.of();
            }
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            lastListSql = sql;
            if (sql.contains("from sys_sensitive_word") && sql.contains("where id = ?")) {
                SensitiveWordVO.WordRecord record = new SensitiveWordVO.WordRecord();
                record.setId(args != null && args.length > 0 && args[0] instanceof Number number ? number.longValue() : 88L);
                record.setWord("blocked");
                record.setNormalizedWord("blocked");
                record.setCategory("DEFAULT");
                record.setSeverity("MEDIUM");
                record.setAction("BLOCK");
                record.setEnabled(true);
                return List.of((T) record);
            }
            return List.of();
        }
    }
}
