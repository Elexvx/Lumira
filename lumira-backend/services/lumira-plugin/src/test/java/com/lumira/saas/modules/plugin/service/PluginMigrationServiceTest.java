package com.lumira.saas.modules.plugin.service;

import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginMigrationServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Mock
    private PluginPersistenceService pluginPersistenceService;

    @TempDir
    private Path tempDir;

    private PluginMigrationService migrationService;

    @BeforeEach
    void setUp() {
        migrationService = new PluginMigrationService(dataSource, pluginPersistenceService);
    }

    private void allowSqlExecution() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(false);
        when(statement.getUpdateCount()).thenReturn(-1);
    }

    @Test
    void upMigrationIsSkippedWhenTheLatestSuccessfulDirectionIsUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(pluginPersistenceService.latestSuccessfulSchemaDirection(
                "sensitive-words", "1.0.0", "V1__sys_sensitive_word.sql"
        )).thenReturn("up");

        migrationService.executeUpMigrations("sensitive-words", "1.0.0", null, 100L, "user-uuid-100");

        verify(statement, never()).execute(anyString());
        verify(pluginPersistenceService, never()).insertSchemaHistory(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong(), anyString()
        );
    }

    @Test
    void upMigrationIsReplayedAfterTheLatestSuccessfulDirectionIsDown() throws Exception {
        allowSqlExecution();
        when(pluginPersistenceService.latestSuccessfulSchemaDirection(
                "sensitive-words", "1.0.0", "V1__sys_sensitive_word.sql"
        )).thenReturn("down");

        migrationService.executeUpMigrations("sensitive-words", "1.0.0", null, 100L, "user-uuid-100");

        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS `sys_sensitive_word`"));
        verify(pluginPersistenceService).insertSchemaHistory(
                "sensitive-words", "1.0.0", "V1__sys_sensitive_word.sql", "up",
                "classpath:builtin-plugins/sensitive-words/migrations/up/V1__sys_sensitive_word.sql",
                "SUCCESS", null, 100L, "user-uuid-100"
        );
    }

    @Test
    void downMigrationDropsTheSensitiveWordsTableAndRecordsTheDirection() throws Exception {
        allowSqlExecution();
        when(pluginPersistenceService.latestSuccessfulSchemaDirection(
                "sensitive-words", "1.0.0", "V1__sys_sensitive_word.sql"
        )).thenReturn("up");

        migrationService.executeDownMigrations("sensitive-words", "1.0.0", null, 100L, "user-uuid-100");

        verify(statement).execute(org.mockito.ArgumentMatchers.contains("DROP TABLE IF EXISTS `sys_sensitive_word`"));
        verify(pluginPersistenceService).insertSchemaHistory(
                "sensitive-words", "1.0.0", "V1__sys_sensitive_word.sql", "down",
                "classpath:builtin-plugins/sensitive-words/migrations/down/V1__sys_sensitive_word.sql",
                "SUCCESS", null, 100L, "user-uuid-100"
        );
    }

    @Test
    void dataPurgeFailsClosedWhenNoDownMigrationExists() {
        assertThatThrownBy(() -> migrationService.executeDownMigrations(
                "plugin-without-down-migration", "1.0.0", tempDir, 100L, "user-uuid-100"
        ))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("requires at least one verified down migration");
    }
}
