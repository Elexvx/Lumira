package com.lumira.saas.modules.plugin.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;

@Service
public class PluginMigrationService {

    private static final String BUILTIN_SENSITIVE_WORDS_PLUGIN = "sensitive-words";
    private static final PathMatchingResourcePatternResolver RESOURCE_RESOLVER = new PathMatchingResourcePatternResolver();

    private final DataSource dataSource;
    private final PluginPersistenceService pluginPersistenceService;

    public PluginMigrationService(DataSource dataSource, PluginPersistenceService pluginPersistenceService) {
        this.dataSource = dataSource;
        this.pluginPersistenceService = pluginPersistenceService;
    }

    public void executeUpMigrations(String pluginCode, String pluginVersion, Path versionHome, Long operatorId, String operatorUuid) {
        execute(pluginCode, pluginVersion, versionHome, "up", false, operatorId, operatorUuid);
    }

    public void executeDownMigrations(String pluginCode, String pluginVersion, Path versionHome, Long operatorId, String operatorUuid) {
        execute(pluginCode, pluginVersion, versionHome, "down", true, operatorId, operatorUuid);
    }

    private void execute(
            String pluginCode,
            String pluginVersion,
            Path versionHome,
            String direction,
            boolean requireScripts,
            Long operatorId,
            String operatorUuid
    ) {
        try {
            List<ResourceScript> scripts = resolveScripts(pluginCode, versionHome, direction);
            if (scripts.isEmpty()) {
                if (requireScripts) {
                    throw new BizException(
                            ErrorCode.PLUGIN_RUNTIME_ERROR,
                            "Plugin data purge requires at least one verified down migration"
                    );
                }
                return;
            }
            Connection connection = DataSourceUtils.getConnection(dataSource);
            for (ResourceScript script : scripts) {
                String latestSuccessfulDirection = pluginPersistenceService.latestSuccessfulSchemaDirection(
                        pluginCode,
                        pluginVersion,
                        script.stepName()
                );
                if (direction.equalsIgnoreCase(latestSuccessfulDirection)) {
                    continue;
                }
                try {
                    ScriptUtils.executeSqlScript(connection, script.resource());
                    pluginPersistenceService.insertSchemaHistory(
                            pluginCode,
                            pluginVersion,
                            script.stepName(),
                            direction,
                            script.scriptPath(),
                            "SUCCESS",
                            null,
                            operatorId,
                            operatorUuid
                    );
                } catch (Throwable throwable) {
                    pluginPersistenceService.insertSchemaHistory(
                            pluginCode,
                            pluginVersion,
                            script.stepName(),
                            direction,
                            script.scriptPath(),
                            "FAILED",
                            rootCauseMessage(throwable),
                            operatorId,
                            operatorUuid
                    );
                    throw throwable;
                }
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件迁移执行失败: " + throwable.getMessage());
        }
    }

    private List<ResourceScript> resolveScripts(String pluginCode, Path versionHome, String direction) throws IOException {
        if (BUILTIN_SENSITIVE_WORDS_PLUGIN.equals(pluginCode)) {
            Resource[] resources = RESOURCE_RESOLVER.getResources("classpath*:builtin-plugins/sensitive-words/migrations/" + direction + "/*.sql");
            Comparator<Resource> comparator = Comparator.comparing(
                    resource -> resource.getFilename() == null ? "" : resource.getFilename()
            );
            if ("down".equals(direction)) {
                comparator = comparator.reversed();
            }
            return Arrays.stream(resources)
                    .filter(Resource::exists)
                    .sorted(comparator)
                    .map(resource -> new ResourceScript(
                            resource.getFilename() == null ? direction : resource.getFilename(),
                            "classpath:builtin-plugins/sensitive-words/migrations/" + direction + "/" + resource.getFilename(),
                            resource
                    ))
                    .toList();
        }
        if (versionHome == null) {
            return List.of();
        }
        Path migrationsDir = versionHome.resolve("migrations").resolve(direction);
        if (!Files.exists(migrationsDir)) {
            Path legacyDir = versionHome.resolve("migrations");
            if ("up".equals(direction) && Files.exists(legacyDir)) {
                return Files.list(legacyDir)
                        .filter(path -> path.getFileName().toString().endsWith(".sql"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(path -> new ResourceScript(path.getFileName().toString(), path.toString(), new FileSystemResource(path)))
                        .toList();
            }
            return List.of();
        }
        Comparator<Path> comparator = Comparator.comparing(path -> path.getFileName().toString());
        if ("down".equals(direction)) {
            comparator = comparator.reversed();
        }
        return Files.list(migrationsDir)
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .sorted(comparator)
                .map(path -> new ResourceScript(path.getFileName().toString(), path.toString(), new FileSystemResource(path)))
                .toList();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return StringUtils.hasText(cursor.getMessage()) ? cursor.getMessage() : throwable.getClass().getSimpleName();
    }

    private record ResourceScript(String stepName, String scriptPath, Resource resource) {
    }
}
