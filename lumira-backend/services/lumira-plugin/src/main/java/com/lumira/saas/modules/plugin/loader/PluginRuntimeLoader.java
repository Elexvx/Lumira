package com.lumira.saas.modules.plugin.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.registry.PluginRuntimeDescriptor;
import com.lumira.saas.modules.plugin.runtime.PluginProperties;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredMenu;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredPermission;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHealthReport;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginScheduledTask;
import com.lumira.saas.modules.plugin.runtime.spi.PluginBootstrap;
import com.lumira.saas.modules.plugin.runtime.spi.PluginHealthIndicator;
import com.lumira.saas.modules.plugin.runtime.spi.PluginHttpHandler;
import com.lumira.saas.modules.plugin.runtime.spi.PluginMenuProvider;
import com.lumira.saas.modules.plugin.runtime.spi.PluginPermissionProvider;
import com.lumira.saas.modules.plugin.runtime.spi.PluginSecondFactorProvider;
import com.lumira.saas.modules.plugin.runtime.spi.PluginScheduledTaskProvider;
import org.springframework.stereotype.Service;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class PluginRuntimeLoader {

    private final ObjectMapper objectMapper;
    private final PluginProperties pluginProperties;

    public PluginRuntimeLoader(ObjectMapper objectMapper, PluginProperties pluginProperties) {
        this.objectMapper = objectMapper;
        this.pluginProperties = pluginProperties;
    }

    public PluginRuntimeDescriptor load(
            PluginDTO.PluginPackageMetadata metadata,
            Path versionHome
    ) {
        try {
            Path jarPath = versionHome.resolve("lumira-backend/plugin.jar");
            URLClassLoader classLoader = new URLClassLoader(
                    new java.net.URL[]{jarPath.toUri().toURL()},
                    PluginBootstrap.class.getClassLoader()
            );
            PluginRuntimeContext runtimeContext = new PluginRuntimeContext(
                    metadata.getPluginCode(),
                    metadata.getVersion(),
                    pluginProperties.getPlatformVersion(),
                    versionHome,
                    null,
                    objectMapper
            );
            PluginBootstrap bootstrap = loadRequired(classLoader, PluginBootstrap.class);
            bootstrap.initialize(runtimeContext);
            PluginHttpHandler httpHandler = loadRequired(classLoader, PluginHttpHandler.class);
            PluginPermissionProvider permissionProvider = loadOptional(classLoader, PluginPermissionProvider.class);
            PluginMenuProvider menuProvider = loadOptional(classLoader, PluginMenuProvider.class);
            PluginHealthIndicator healthIndicator = loadOptional(classLoader, PluginHealthIndicator.class);
            PluginSecondFactorProvider secondFactorProvider = loadOptional(classLoader, PluginSecondFactorProvider.class);
            PluginScheduledTaskProvider taskProvider = loadOptional(classLoader, PluginScheduledTaskProvider.class);
            List<PluginDeclaredPermission> permissions = permissionProvider == null
                    ? mapPermissions(metadata)
                    : permissionProvider.permissions(runtimeContext);
            List<PluginDeclaredMenu> menus = menuProvider == null
                    ? mapMenus(metadata)
                    : menuProvider.menus(runtimeContext);
            List<ScheduledExecutorService> executors = registerTasks(taskProvider, runtimeContext, metadata);
            PluginHealthReport healthReport = healthIndicator == null
                    ? PluginHealthReport.healthy("插件运行时已就绪")
                    : healthIndicator.healthCheck(runtimeContext);
            if (!healthReport.healthy()) {
                for (ScheduledExecutorService executor : executors) {
                    executor.shutdownNow();
                }
                bootstrap.destroy(runtimeContext);
                classLoader.close();
                throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件健康检查失败: " + healthReport.message());
            }
            return new PluginRuntimeDescriptor(
                    metadata.getPluginCode(),
                    metadata.getVersion(),
                    classLoader,
                    runtimeContext,
                    bootstrap,
                    httpHandler,
                    healthIndicator,
                    secondFactorProvider,
                    permissions,
                    menus,
                    executors
            );
        } catch (BizException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件运行时加载失败: " + throwable.getMessage());
        }
    }

    private List<ScheduledExecutorService> registerTasks(
            PluginScheduledTaskProvider taskProvider,
            PluginRuntimeContext runtimeContext,
            PluginDTO.PluginPackageMetadata metadata
    ) {
        if (taskProvider == null) {
            return List.of();
        }
        List<PluginScheduledTask> tasks = taskProvider.scheduledTasks(runtimeContext);
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<ScheduledExecutorService> executors = new ArrayList<>(tasks.size());
        for (PluginScheduledTask task : tasks) {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                    runnable -> new Thread(runnable, "plugin-" + metadata.getPluginCode() + "-" + task.taskCode())
            );
            executor.scheduleWithFixedDelay(
                    task.task(),
                    Math.max(task.initialDelaySeconds(), 1),
                    Math.max(task.fixedDelaySeconds(), 1),
                    TimeUnit.SECONDS
            );
            executors.add(executor);
        }
        return executors;
    }

    private List<PluginDeclaredPermission> mapPermissions(PluginDTO.PluginPackageMetadata metadata) {
        if (metadata.getRequiredPermissions() == null) {
            return List.of();
        }
        return metadata.getRequiredPermissions().stream()
                .map(item -> new PluginDeclaredPermission(item.getPermissionKey(), item.getPermissionName(), item.getPermissionGroup()))
                .toList();
    }

    private List<PluginDeclaredMenu> mapMenus(PluginDTO.PluginPackageMetadata metadata) {
        if (metadata.getMenuDeclarations() == null) {
            return List.of();
        }
        return metadata.getMenuDeclarations().stream()
                .map(item -> new PluginDeclaredMenu(
                        item.getMenuCode(),
                        item.getParentMenuCode(),
                        item.getMenuName(),
                        item.getRoutePath(),
                        item.getIcon(),
                        item.getPermissionKey(),
                        item.getSortNo() == null ? 0 : item.getSortNo()
                ))
                .toList();
    }

    private <T> T loadRequired(URLClassLoader classLoader, Class<T> type) {
        return ServiceLoader.load(type, classLoader).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件缺少 SPI 实现: " + type.getSimpleName()));
    }

    private <T> T loadOptional(URLClassLoader classLoader, Class<T> type) {
        return ServiceLoader.load(type, classLoader).findFirst().orElse(null);
    }
}
