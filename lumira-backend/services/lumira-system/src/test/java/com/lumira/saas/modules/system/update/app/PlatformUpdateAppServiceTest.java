package com.lumira.saas.modules.system.update.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.update.entity.PlatformUpdateTaskEntity;
import com.lumira.saas.modules.system.update.mapper.PlatformUpdateTaskMapper;
import com.lumira.saas.modules.system.update.vo.PlatformUpdateVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformUpdateAppServiceTest {

    @Test
    void scheduledReconcilerRunsWithoutAnOpenMonitoringPage() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        when(taskMapper.selectOne(any())).thenReturn(null);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );

        service.scheduledReconcileActiveTask();

        verify(taskMapper).selectOne(any());
    }

    @Test
    void allUpdaterTerminalStatesReleaseTheGlobalTaskLock() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "finishTerminalTask",
                PlatformUpdateTaskEntity.class
        );
        method.setAccessible(true);

        for (String status : Set.of("SUCCEEDED", "FAILED", "ROLLED_BACK", "CANCELLED")) {
            PlatformUpdateTaskEntity task = new PlatformUpdateTaskEntity();
            task.setStatus(status);
            task.setActiveKey("GLOBAL");

            assertThat(method.invoke(service, task)).isEqualTo(true);
            assertThat(task.getActiveKey()).isNull();
            assertThat(task.getFinishedAt()).isNotNull();
        }
    }

    @Test
    void unlinkedTaskExpiresAndReleasesTheUpdateLock() throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("platform.update.unlinked-task-timeout-ms", Long.class)).thenReturn(30_000L);
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        when(taskMapper.update(any(PlatformUpdateTaskEntity.class), any())).thenReturn(1);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                environment,
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        PlatformUpdateTaskEntity task = new PlatformUpdateTaskEntity();
        task.setId(42L);
        task.setTaskType("INSTALL");
        task.setStatus("PENDING");
        task.setActiveKey("GLOBAL");
        task.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        task.setCreatedBy(1001L);
        task.setCreatedByUuid("user-uuid-1001");
        PlatformUpdateTaskEntity expected = new PlatformUpdateTaskEntity();
        expected.setId(task.getId());
        expected.setTaskType(task.getTaskType());
        expected.setStatus(task.getStatus());
        expected.setActiveKey(task.getActiveKey());
        expected.setCreatedBy(task.getCreatedBy());
        expected.setCreatedByUuid(task.getCreatedByUuid());
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "expireUnlinkedTaskIfNecessary",
                PlatformUpdateTaskEntity.class,
                PlatformUpdateTaskEntity.class
        );
        method.setAccessible(true);

        method.invoke(service, task, expected);

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getActiveKey()).isNull();
        assertThat(task.getFinishedAt()).isNotNull();
        verify(taskMapper).update(any(PlatformUpdateTaskEntity.class), any());
    }

    @Test
    void requireUpdateAvailableShouldRejectRedeployingTheCurrentRelease() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "requireUpdateAvailable",
                PlatformUpdateVO.StatusVO.class
        );
        method.setAccessible(true);
        PlatformUpdateVO.StatusVO status = new PlatformUpdateVO.StatusVO();
        status.setStatus("UP_TO_DATE");
        status.setUpdateAvailable(false);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(service, status)
        );

        assertThat(exception.getCause())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The current deployment is already on the latest release.");
    }

    @Test
    void fromManifestShouldRejectUnpinnedImageReferences() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "fromManifest",
                com.fasterxml.jackson.databind.JsonNode.class
        );
        method.setAccessible(true);
        var manifest = new ObjectMapper().readTree("""
                {
                  "version": "1.2.3",
                  "commit": "abc123",
                  "serverImage": "ghcr.io/example/lumira-server:latest"
                }
                """);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, manifest));
    }

    @Test
    void fromManifestShouldReadDigestPinnedManifestFromGithubReleaseBody() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "fromManifest",
                com.fasterxml.jackson.databind.JsonNode.class
        );
        method.setAccessible(true);
        String digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        var release = new ObjectMapper().readTree("""
                {
                  "html_url": "https://github.com/Elexvx/Lumira/releases/tag/continuous",
                  "body": "{\\\"version\\\":\\\"main\\\",\\\"commit\\\":\\\"036d591f3bc3d8468fb10771ae33377f5bd64f63\\\",\\\"serverImage\\\":\\\"ghcr.io/elexvx/lumira/lumira-server@sha256:%s\\\",\\\"frontendImage\\\":\\\"ghcr.io/elexvx/lumira/lumira-ui@sha256:%s\\\"}"
                }
                """.formatted(digest, digest));

        PlatformUpdateVO.LatestVersionVO latest = (PlatformUpdateVO.LatestVersionVO) method.invoke(service, release);

        assertThat(latest.getCommitId()).isEqualTo("036d591f3bc3d8468fb10771ae33377f5bd64f63");
        assertThat(latest.getServerImage()).contains("@sha256:" + digest);
        assertThat(latest.getFrontendImage()).contains("@sha256:" + digest);
        assertThat(latest.getUrl()).isEqualTo("https://github.com/Elexvx/Lumira/releases/tag/continuous");
    }

    @Test
    void fromManifestShouldReadBlueGreenV2Contract() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class), mockBuildPropertiesProvider(), new ObjectMapper(), mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("fromManifest", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        String image = "ghcr.io/elexvx/lumira/runtime@sha256:" + "a".repeat(64);
        var manifest = new ObjectMapper().readTree("""
                {
                  "schemaVersion": 2,
                  "version": "2.0.0",
                  "commit": "036d591f3bc3d8468fb10771ae33377f5bd64f63",
                  "images": {
                    "server": "%s", "frontend": "%s", "async": "%s",
                    "jobExecutor": "%s", "migrator": "%s"
                  },
                  "update": {
                    "strategy": "single-host-blue-green",
                    "minUpdaterProtocol": 2,
                    "database": { "mode": "expand-only", "targetVersion": "202607140001", "required": true }
                  }
                }
                """.formatted(image, image, image, image, image));

        PlatformUpdateVO.LatestVersionVO latest = (PlatformUpdateVO.LatestVersionVO) method.invoke(service, manifest);

        assertThat(latest.getSchemaVersion()).isEqualTo(2);
        assertThat(latest.getStrategy()).isEqualTo("single-host-blue-green");
        assertThat(latest.getAsyncImage()).isEqualTo(image);
        assertThat(latest.getJobExecutorImage()).isEqualTo(image);
        assertThat(latest.getMigratorImage()).isEqualTo(image);
        assertThat(latest.getMigrationMode()).isEqualTo("expand-only");
        assertThat(latest.getDatabaseVersion()).isEqualTo("202607140001");
        assertThat(latest.getMigrationRequired()).isTrue();
    }

    @Test
    void validateManifestSourceUrlShouldRequireHttps() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("validateManifestSourceUrl", String.class);
        method.setAccessible(true);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, "http://updates.example.com/manifest.json"));
    }

    @Test
    void validateManifestSourceUrlShouldRejectHostsOutsideAllowlist() throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("PLATFORM_UPDATE_ALLOWED_HOSTS")).thenReturn("updates.example.com");
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                environment,
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("validateManifestSourceUrl", String.class);
        method.setAccessible(true);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, "https://evil.example.com/manifest.json"));
    }

    @Test
    void validateManifestSourceUrlShouldRejectUserInfo() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("validateManifestSourceUrl", String.class);
        method.setAccessible(true);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, "https://token@api.github.com/repos/Elexvx/lumira/commits/main"));
    }

    @Test
    void validateUpdateSourceUrlShouldRequireAllowlistForCustomSource() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("validateUpdateSourceUrl", String.class, boolean.class);
        method.setAccessible(true);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, "https://updates.example.com/latest.json", false));
    }

    @Test
    void fromManifestShouldRejectInvalidCommitHash() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "fromManifest",
                com.fasterxml.jackson.databind.JsonNode.class
        );
        method.setAccessible(true);
        var manifest = new ObjectMapper().readTree("""
                {
                  "version": "1.2.3",
                  "commit": "refs/heads/main",
                  "serverImage": "ghcr.io/example/lumira-server@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                }
                """);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, manifest));
    }

    @Test
    void normalizeUpdaterStatusShouldRejectUnknownStatus() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("normalizeUpdaterStatus", String.class, String.class);
        method.setAccessible(true);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, "COMPLETE_AND_DELETE", "RUNNING"));
    }

    @Test
    void updaterTaskStateWritesShouldBindOriginalTaskSnapshot() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/lumira/saas/modules/system/update/app/PlatformUpdateAppService.java"));

        assertThat(source)
                .contains("updateTaskIfUnchanged(task, expected)")
                .contains(".eq(PlatformUpdateTaskEntity::getTaskType, expected.getTaskType())")
                .contains(".eq(PlatformUpdateTaskEntity::getStatus, expected.getStatus())")
                .contains(".eq(PlatformUpdateTaskEntity::getPhase, expected.getPhase())")
                .contains(".eq(PlatformUpdateTaskEntity::getProgressPercent, expected.getProgressPercent())")
                .contains(".eq(PlatformUpdateTaskEntity::getActiveKey, expected.getActiveKey())")
                .contains("wrapper.setSql(\"active_key = NULL\")")
                .contains("wrapper.setSql(\"error_message = NULL\")")
                .contains(".eq(PlatformUpdateTaskEntity::getCreatedByUuid, expected.getCreatedByUuid())")
                .contains("wrapper.eq(PlatformUpdateTaskEntity::getUpdaterTaskId, expected.getUpdaterTaskId())")
                .doesNotContain("taskMapper.updateById(task);");
    }

    @Test
    void ambiguousUpdaterSubmissionUsesCapabilityGatedIdempotentReconciliation() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/lumira/saas/modules/system/update/app/PlatformUpdateAppService.java"
        ));

        assertThat(source)
                .contains("if (supportsPlatformTaskLookup())")
                .contains("getUpdaterIfFound(platformTaskLookupPath(task))")
                .contains("return persistAcceptedUpdaterTask(task, expected, postUpdater(path, request))")
                .contains("Do not renew updatedAt")
                .contains("expireUnlinkedTaskIfNecessary(task, expected)");
    }

    @Test
    void productionUpdateRequestsBindReleaseIdInsteadOfCallerSuppliedManifest() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/lumira/saas/modules/system/update/app/PlatformUpdateAppService.java"
        ));

        assertThat(source)
                .contains("Map.of(\"releaseId\", requireReleaseId(latest))")
                .contains("new UpdaterRequest(\n                task.getReleaseId()")
                .doesNotContain("manifestPayload(");
    }

    @Test
    void platformTaskIdentityUsesDatabaseStableTimestampPrecision() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/lumira/saas/modules/system/update/app/PlatformUpdateAppService.java"
        ));

        assertThat(source)
                .contains("LocalDateTime now = LocalDateTime.now().withNano(0)")
                .contains("task.getCreatedAt().toString()")
                .contains("platformTaskCreatedAt");
    }

    @Test
    void sourceTokenShouldOnlyBeSentToTrustedHosts() throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("PLATFORM_UPDATE_SOURCE_TOKEN")).thenReturn("secret-token");
        when(environment.getProperty("PLATFORM_UPDATE_ALLOWED_HOSTS")).thenReturn("updates.example.com");
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                environment,
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("addSourceToken", HttpRequest.Builder.class, String.class);
        method.setAccessible(true);
        HttpRequest.Builder trusted = HttpRequest.newBuilder(URI.create("https://updates.example.com/latest.json"));
        HttpRequest.Builder untrusted = HttpRequest.newBuilder(URI.create("https://evil.example.com/latest.json"));

        method.invoke(service, trusted, "https://updates.example.com/latest.json");
        method.invoke(service, untrusted, "https://evil.example.com/latest.json");

        assertThat(trusted.build().headers().firstValue("Authorization")).contains("Bearer secret-token");
        assertThat(untrusted.build().headers().firstValue("Authorization")).isEmpty();
    }

    @Test
    void sourceTokenShouldNotFallbackToGithubTokenForCustomTrustedHost() throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("GITHUB_TOKEN")).thenReturn("github-token");
        when(environment.getProperty("PLATFORM_UPDATE_ALLOWED_HOSTS")).thenReturn("updates.example.com");
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                environment,
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("addSourceToken", HttpRequest.Builder.class, String.class);
        method.setAccessible(true);
        HttpRequest.Builder trusted = HttpRequest.newBuilder(URI.create("https://updates.example.com/latest.json"));

        method.invoke(service, trusted, "https://updates.example.com/latest.json");

        assertThat(trusted.build().headers().firstValue("Authorization")).isEmpty();
    }

    @Test
    void sourceTokenShouldAllowGithubTokenForGithubHost() throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("GITHUB_TOKEN")).thenReturn("github-token");
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                environment,
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("addSourceToken", HttpRequest.Builder.class, String.class);
        method.setAccessible(true);
        HttpRequest.Builder github = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/openai/lumira/releases/latest"));

        method.invoke(service, github, "https://api.github.com/repos/openai/lumira/releases/latest");

        assertThat(github.build().headers().firstValue("Authorization")).contains("Bearer github-token");
    }

    @Test
    void updaterUriShouldDefaultToLoopbackOnly() throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("PLATFORM_UPDATE_AGENT_URL")).thenReturn("http://updates.example.com:9788");
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                environment,
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("updaterUri", String.class);
        method.setAccessible(true);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, "/v1/health"));
    }

    @Test
    void updaterUriShouldAllowExplicitAgentHostAllowlist() throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("PLATFORM_UPDATE_AGENT_URL")).thenReturn("https://updater.internal:9788");
        when(environment.getProperty("PLATFORM_UPDATE_AGENT_ALLOWED_HOSTS")).thenReturn("updater.internal");
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                environment,
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("updaterUri", String.class);
        method.setAccessible(true);

        URI uri = (URI) method.invoke(service, "/v1/health");

        assertThat(uri).isEqualTo(URI.create("https://updater.internal:9788/v1/health"));
    }

    @Test
    void updaterUriShouldRejectUserInfo() throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("PLATFORM_UPDATE_AGENT_URL")).thenReturn("http://token@127.0.0.1:9788");
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                environment,
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("updaterUri", String.class);
        method.setAccessible(true);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, "/v1/health"));
    }

    @Test
    void installShouldRequireInstallPermissionBeforeCheckingUpdater() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("operator");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of("system:update:view"));

        BizException exception = assertThrows(BizException.class, () -> service.install(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(taskMapper, never()).insert(org.mockito.ArgumentMatchers.<PlatformUpdateTaskEntity>any());
    }

    @Test
    void installShouldRejectWhenLiveSnapshotRevokesInstallPermissionBeforeCheckingUpdater() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:update:view")));
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper,
                permissionSnapshotService
        );

        BizException exception = assertThrows(BizException.class, () -> service.install(updateUser(Set.of("*", "system:update:install"))));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(taskMapper, never()).insert(org.mockito.ArgumentMatchers.<PlatformUpdateTaskEntity>any());
    }

    @Test
    void installShouldRejectWhenTrustedPermissionSnapshotIsUnavailableInStrictMode() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper,
                permissionSnapshotService,
                null,
                null
        );

        BizException exception = assertThrows(BizException.class, () -> service.install(updateUser(Set.of("*", "system:update:install"))));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(taskMapper, never()).insert(org.mockito.ArgumentMatchers.<PlatformUpdateTaskEntity>any());
    }

    @Test
    void installShouldRejectTrustedIdentityWhenLiveUsernameIsUnavailableBeforeCheckingUpdater() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper,
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        BizException exception = assertThrows(BizException.class, () -> service.install(updateUser(Set.of("*", "system:update:install"))));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(taskMapper, never()).insert(org.mockito.ArgumentMatchers.<PlatformUpdateTaskEntity>any());
        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
    }

    @Test
    void installShouldRejectUnauthenticatedUserBeforeCheckingUpdater() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("operator");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(false);
        currentUser.setPermissions(Set.of("*", "system:update:install"));

        BizException exception = assertThrows(BizException.class, () -> service.install(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(taskMapper, never()).insert(org.mockito.ArgumentMatchers.<PlatformUpdateTaskEntity>any());
    }

    @Test
    void installShouldRejectBlankUsernameBeforeCheckingUpdater() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername(" ");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of("*", "system:update:install"));

        BizException exception = assertThrows(BizException.class, () -> service.install(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(taskMapper, never()).insert(org.mockito.ArgumentMatchers.<PlatformUpdateTaskEntity>any());
    }

    @Test
    void installShouldRejectMissingSessionVersionBeforeCheckingUpdater() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("operator");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(null);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of("*", "system:update:install"));

        BizException exception = assertThrows(BizException.class, () -> service.install(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(taskMapper, never()).insert(org.mockito.ArgumentMatchers.<PlatformUpdateTaskEntity>any());
    }

    @Test
    void installShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper,
                null,
                null,
                null
        );

        BizException exception = assertThrows(BizException.class, () -> service.install(updateUser(Set.of("*", "system:update:install"))));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(taskMapper, never()).insert(org.mockito.ArgumentMatchers.<PlatformUpdateTaskEntity>any());
    }

    @Test
    void installShouldRejectMissingUserUuidBeforeCheckingUpdater() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        CurrentUser currentUser = updateUser(Set.of("*", "system:update:install"));
        currentUser.setUserUuid(" ");

        BizException exception = assertThrows(BizException.class, () -> service.install(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(taskMapper, never()).insert(org.mockito.ArgumentMatchers.<PlatformUpdateTaskEntity>any());
    }

    @Test
    void checkLatestShouldRejectMissingPermissionsVersionBeforeExternalLookup() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        CurrentUser currentUser = updateUser(Set.of("*", "system:update:check"));
        currentUser.setPermissionsVersion(" ");

        BizException exception = assertThrows(BizException.class, () -> service.checkLatest(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(taskMapper, never()).selectOne(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getTaskShouldRejectInvalidIdBeforeMapperAccess() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );

        BizException exception = assertThrows(BizException.class, () -> service.getTask(updateViewer(), 0L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(taskMapper, never()).selectById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listTasksShouldRequireViewPermissionBeforeMapperAccess() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        CurrentUser currentUser = updateUser(Set.of("system:update:check"));

        BizException exception = assertThrows(BizException.class, () -> service.listTasks(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(taskMapper, never()).selectList(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listTasksShouldRejectRevokedSessionTicketBeforeMapperAccess() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Login required"));
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper,
                mock(PermissionSnapshotService.class),
                sessionAuthenticationService
        );

        BizException exception = assertThrows(BizException.class, () -> service.listTasks(updateViewer()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(taskMapper, never()).selectList(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getStatusShouldRequireAuthenticatedViewUserBeforeCheckingLatest() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        CurrentUser currentUser = updateUser(Set.of("system:update:view"));
        currentUser.setAuthenticated(false);

        BizException exception = assertThrows(BizException.class, () -> service.getStatus(currentUser));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(taskMapper, never()).selectOne(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void checkLatestShouldRequireCheckPermissionBeforeExternalLookup() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );

        BizException exception = assertThrows(BizException.class, () -> service.checkLatest(updateViewer()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(taskMapper, never()).selectOne(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createTaskShouldPersistTrustedOperatorUuidForAudit() throws Exception {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("*", "system:update:install")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "operator-live", "ENABLED"));
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "createTask",
                String.class,
                PlatformUpdateVO.LatestVersionVO.class,
                CurrentUser.class
        );
        method.setAccessible(true);
        PlatformUpdateVO.LatestVersionVO latest = new PlatformUpdateVO.LatestVersionVO();
        latest.setVersion("1.2.3");
        latest.setCommitId("abcdef1");
        latest.setServerImage("ghcr.io/example/lumira-server@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        when(taskMapper.insert(any(PlatformUpdateTaskEntity.class))).thenReturn(1);
        CurrentUser currentUser = updateUser(Set.of("*", "system:update:install"));
        currentUser.setUsername("operator-stale");

        method.invoke(service, "INSTALL", latest, currentUser);

        org.mockito.ArgumentCaptor<PlatformUpdateTaskEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PlatformUpdateTaskEntity.class);
        verify(taskMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(1001L);
        assertThat(captor.getValue().getCreatedByUuid()).isEqualTo("user-uuid-1001");
        assertThat(captor.getValue().getCreatedByName()).isEqualTo("operator-live");
        assertThat(currentUser.getUsername()).isEqualTo("operator-live");
    }

    @Test
    void createTaskShouldReleaseTerminalGlobalLockBeforeInsert() throws Exception {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        when(taskMapper.update(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(taskMapper.insert(any(PlatformUpdateTaskEntity.class))).thenReturn(1);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "createTask",
                String.class,
                PlatformUpdateVO.LatestVersionVO.class,
                CurrentUser.class
        );
        method.setAccessible(true);
        PlatformUpdateVO.LatestVersionVO latest = new PlatformUpdateVO.LatestVersionVO();
        latest.setVersion("1.2.3");
        latest.setCommitId("abcdef1");
        latest.setServerImage("ghcr.io/example/lumira-server@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        method.invoke(service, "INSTALL", latest, updateUser(Set.of("*", "system:update:install")));

        var ordered = inOrder(taskMapper);
        ordered.verify(taskMapper).update(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
        ordered.verify(taskMapper).insert(any(PlatformUpdateTaskEntity.class));
    }

    @Test
    void createTaskShouldRejectDisabledTrustedUserIdentityBeforeInsert() throws Exception {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "operator-live", "DISABLED"));
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "createTask",
                String.class,
                PlatformUpdateVO.LatestVersionVO.class,
                CurrentUser.class
        );
        method.setAccessible(true);
        PlatformUpdateVO.LatestVersionVO latest = new PlatformUpdateVO.LatestVersionVO();
        latest.setVersion("1.2.3");
        latest.setCommitId("abcdef1");
        latest.setServerImage("ghcr.io/example/lumira-server@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(service, "INSTALL", latest, updateUser(Set.of("*", "system:update:install")))
        );

        assertThat(error.getCause()).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(taskMapper, never()).insert(any(PlatformUpdateTaskEntity.class));
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "operator-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:update:view")));
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper,
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = updateViewer();
        currentUser.setSimulatedRoleId(0L);
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), any(), any());
    }

    @Test
    void createTaskShouldRejectWhenInsertMisses() throws Exception {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                taskMapper
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "createTask",
                String.class,
                PlatformUpdateVO.LatestVersionVO.class,
                CurrentUser.class
        );
        method.setAccessible(true);
        PlatformUpdateVO.LatestVersionVO latest = new PlatformUpdateVO.LatestVersionVO();
        latest.setVersion("1.2.3");
        latest.setCommitId("abcdef1");
        latest.setServerImage("ghcr.io/example/lumira-server@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        when(taskMapper.insert(any(PlatformUpdateTaskEntity.class))).thenReturn(0);

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> method.invoke(service, "INSTALL", latest, updateUser(Set.of("*", "system:update:install"))));

        assertThat(error.getCause()).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void toTaskVoShouldExposeTrustedOperatorUuid() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("toTaskVO", PlatformUpdateTaskEntity.class);
        method.setAccessible(true);
        PlatformUpdateTaskEntity entity = new PlatformUpdateTaskEntity();
        entity.setId(9L);
        entity.setTaskType("INSTALL");
        entity.setStatus("PENDING");
        entity.setCreatedBy(1001L);
        entity.setCreatedByUuid("user-uuid-1001");
        entity.setCreatedByName("operator");

        PlatformUpdateVO.TaskVO vo = (PlatformUpdateVO.TaskVO) method.invoke(service, entity);

        assertThat(vo.getCreatedBy()).isEqualTo(1001L);
        assertThat(vo.getCreatedByUuid()).isEqualTo("user-uuid-1001");
        assertThat(vo.getCreatedByName()).isEqualTo("operator");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<BuildProperties> mockBuildPropertiesProvider() {
        return mock(ObjectProvider.class);
    }

    private CurrentUser updateViewer() {
        return updateUser(Set.of("system:update:view"));
    }

    private CurrentUser updateUser(Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("operator");
        currentUser.setSessionId("session-1");
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
