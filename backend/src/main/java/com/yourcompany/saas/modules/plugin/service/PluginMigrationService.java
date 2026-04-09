package com.yourcompany.saas.modules.plugin.service;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Comparator;
import java.util.List;

@Service
public class PluginMigrationService {

    private final DataSource dataSource;

    public PluginMigrationService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void executeMigrations(Path versionHome) {
        Path migrationsDir = versionHome.resolve("migrations");
        if (!Files.exists(migrationsDir)) {
            return;
        }
        try {
            List<Path> scripts = Files.list(migrationsDir)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (scripts.isEmpty()) {
                return;
            }
            Connection connection = DataSourceUtils.getConnection(dataSource);
            for (Path script : scripts) {
                String sql = Files.readString(script, StandardCharsets.UTF_8);
                if (StringUtils.hasText(sql)) {
                    ScriptUtils.executeSqlScript(connection, new org.springframework.core.io.FileSystemResource(script));
                }
            }
        } catch (Throwable throwable) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件迁移执行失败: " + throwable.getMessage());
        }
    }
}
