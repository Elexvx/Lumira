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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class PluginRuntimeLoader {

    private static final Logger log = LoggerFactory.getLogger(PluginRuntimeLoader.class);

    private static final int MAX_SCHEDULED_TASKS = 16;
    private static final int MAX_TASK_CODE_LENGTH = 64;
    private static final long MAX_DELAY_SECONDS = 24 * 60 * 60;
    private static final Pattern TASK_CODE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9._:-]{0,63}$");

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
        if (!pluginProperties.isAllowInProcessBackendPlugins()) {
            throw new BizException(
                    ErrorCode.PLUGIN_RUNTIME_ERROR,
                    "in-process backend plugin execution is disabled; use an isolated plugin runtime"
            );
        }
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

    List<ScheduledExecutorService> registerTasks(
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
        validateScheduledTasks(tasks);
        List<ScheduledExecutorService> executors = new ArrayList<>(tasks.size());
        for (PluginScheduledTask task : tasks) {
            String taskCode = task.taskCode().trim();
            ScheduledExecutorService executor = createScheduledExecutor(metadata.getPluginCode(), taskCode);
            executor.scheduleWithFixedDelay(
                    guardedTask(metadata.getPluginCode(), taskCode, task.task()),
                    task.initialDelaySeconds(),
                    task.fixedDelaySeconds(),
                    TimeUnit.SECONDS
            );
            executors.add(executor);
        }
        return executors;
    }

    ScheduledExecutorService createScheduledExecutor(String pluginCode, String taskCode) {
        return Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, "plugin-" + pluginCode + "-" + taskCode)
        );
    }

    private Runnable guardedTask(String pluginCode, String taskCode, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (VirtualMachineError error) {
                throw error;
            } catch (Throwable throwable) {
                log.warn(
                        "plugin scheduled task failed, pluginCode={}, taskCode={}, message={}",
                        pluginCode,
                        taskCode,
                        throwable.getMessage(),
                        throwable
                );
            }
        };
    }

    private void validateScheduledTasks(List<PluginScheduledTask> tasks) {
        if (tasks.size() > MAX_SCHEDULED_TASKS) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件定时任务数量超过限制");
        }
        for (PluginScheduledTask task : tasks) {
            validateScheduledTask(task);
        }
    }

    private void validateScheduledTask(PluginScheduledTask task) {
        if (task == null) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件定时任务不能为空");
        }
        String taskCode = task.taskCode();
        if (taskCode == null || taskCode.isBlank()) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件定时任务编码不能为空");
        }
        String normalizedTaskCode = taskCode.trim();
        if (normalizedTaskCode.length() > MAX_TASK_CODE_LENGTH || !TASK_CODE_PATTERN.matcher(normalizedTaskCode).matches()) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件定时任务编码不可信");
        }
        if (task.task() == null) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件定时任务 Runnable 不能为空");
        }
        validateDelay(task.initialDelaySeconds(), "initialDelaySeconds");
        validateDelay(task.fixedDelaySeconds(), "fixedDelaySeconds");
    }

    private void validateDelay(long delaySeconds, String fieldName) {
        if (delaySeconds < 1 || delaySeconds > MAX_DELAY_SECONDS) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件定时任务 " + fieldName + " 不可信");
        }
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
