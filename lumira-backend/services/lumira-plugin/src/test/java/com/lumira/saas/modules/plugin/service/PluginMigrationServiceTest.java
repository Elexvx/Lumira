package com.lumira.saas.modules.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginMigrationRequestEntity;
import com.lumira.saas.modules.plugin.runtime.PluginProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginMigrationServiceTest {
    private static final String PACKAGE_DIGEST = "a".repeat(64);

    @Mock private PluginMigrationRequestService requestService;
    @Mock private PluginProperties properties;
    @TempDir private Path tempDir;
    private PluginMigrationService migrationService;

    @BeforeEach
    void setUp() {
        migrationService = new PluginMigrationService(requestService, properties, new ObjectMapper());
    }

    @Test
    void applicationRuntimePersistsValidatedExpandRequestWithoutExecutingDdl() throws Exception {
        Path migrations = Files.createDirectories(tempDir.resolve("migrations/up"));
        Files.writeString(migrations.resolve("V1__create_message.sql"),
                "CREATE TABLE IF NOT EXISTS plugin_sms_message (id bigint primary key);");
        when(properties.getReleaseId()).thenReturn("v-test");
        when(requestService.find(any(), any(), any())).thenReturn(Optional.empty());
        when(requestService.enqueue(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PluginMigrationService.MigrationDisposition result = migrationService.requestExpandMigration(
                "sms", "1.0.0", tempDir, PACKAGE_DIGEST, metadata(), 100L, "user-uuid-100");

        assertThat(result).isEqualTo(PluginMigrationService.MigrationDisposition.MIGRATION_PENDING);
        ArgumentCaptor<PluginMigrationRequestEntity> captor = ArgumentCaptor.forClass(PluginMigrationRequestEntity.class);
        verify(requestService).enqueue(captor.capture());
        PluginMigrationRequestEntity request = captor.getValue();
        assertThat(request.getPhase()).isEqualTo("EXPAND");
        assertThat(request.getTableNamespace()).isEqualTo("plugin_sms_");
        assertThat(request.getPackageDigest()).isEqualTo(PACKAGE_DIGEST);
        assertThat(request.getMigrationDigest()).matches("[a-f0-9]{64}");
        assertThat(request.getRequestStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(request.getScriptPayload()).contains("plugin_sms_message");
    }

    @Test
    void destructiveSqlIsRejectedBeforeRequestIsPersisted() throws Exception {
        Path migrations = Files.createDirectories(tempDir.resolve("migrations/up"));
        Files.writeString(migrations.resolve("V2__bad.sql"), "DROP TABLE plugin_sms_message;");

        assertThatThrownBy(() -> migrationService.requestExpandMigration(
                "sms", "1.0.0", tempDir, PACKAGE_DIGEST, metadata(), 100L, "user-uuid-100"))
                .isInstanceOf(BizException.class).hasMessageContaining("destructive SQL");
        verify(requestService, never()).enqueue(any());
    }

    @Test
    void tableOutsidePluginNamespaceIsRejected() throws Exception {
        Path migrations = Files.createDirectories(tempDir.resolve("migrations/up"));
        Files.writeString(migrations.resolve("V2__bad.sql"), "ALTER TABLE sys_user ADD COLUMN plugin_note varchar(32);");

        assertThatThrownBy(() -> migrationService.requestExpandMigration(
                "sms", "1.0.0", tempDir, PACKAGE_DIGEST, metadata(), 100L, "user-uuid-100"))
                .isInstanceOf(BizException.class).hasMessageContaining("plugin_sms_*");
    }

    @Test
    void applicationDownMigrationAlwaysFailsClosed() {
        assertThatThrownBy(() -> migrationService.executeDownMigrations(
                "sms", "1.0.0", tempDir, 100L, "user-uuid-100"))
                .isInstanceOf(BizException.class).hasMessageContaining("down migrations are forbidden");
    }

    @Test
    void noScriptsRequireNoMigrationRequest() {
        assertThat(migrationService.requestExpandMigration(
                "sms", "1.0.0", tempDir, PACKAGE_DIGEST, metadata(), 100L, "user-uuid-100"))
                .isEqualTo(PluginMigrationService.MigrationDisposition.NO_MIGRATION);
        verify(requestService, never()).enqueue(any());
    }

    private PluginDTO.PluginPackageMetadata metadata() {
        PluginDTO.PluginPackageMetadata metadata = new PluginDTO.PluginPackageMetadata();
        metadata.setMigrationSchemaVersion("1");
        metadata.setMigrationPhase("expand");
        metadata.setRollbackMode("application_only");
        metadata.setCompatibleReaders(List.of("1.x", "2.x"));
        return metadata;
    }
}
