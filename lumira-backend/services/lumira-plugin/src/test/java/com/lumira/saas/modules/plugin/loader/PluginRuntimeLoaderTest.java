package com.lumira.saas.modules.plugin.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.runtime.PluginProperties;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginScheduledTask;
import com.lumira.saas.modules.plugin.runtime.spi.PluginScheduledTaskProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginRuntimeLoaderTest {

    @Test
    void registerTasksShouldAcceptTrustedScheduledTaskDeclaration() {
        PluginRuntimeLoader loader = loader();
        PluginScheduledTaskProvider provider = context -> List.of(
                new PluginScheduledTask("daily.sync", 1, 60, () -> {
                })
        );

        List<ScheduledExecutorService> executors = loader.registerTasks(provider, runtimeContext(), metadata());

        assertThat(executors).hasSize(1);
        executors.forEach(ScheduledExecutorService::shutdownNow);
    }

    @Test
    void registerTasksShouldRejectUntrustedTaskCodeBeforeScheduling() {
        PluginRuntimeLoader loader = loader();
        PluginScheduledTaskProvider provider = context -> List.of(
                new PluginScheduledTask("../bad", 1, 60, () -> {
                })
        );

        BizException exception = assertThrows(BizException.class, () -> loader.registerTasks(provider, runtimeContext(), metadata()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLUGIN_RUNTIME_ERROR);
    }

    @Test
    void registerTasksShouldRejectZeroDelayBeforeScheduling() {
        PluginRuntimeLoader loader = loader();
        PluginScheduledTaskProvider provider = context -> List.of(
                new PluginScheduledTask("daily.sync", 0, 60, () -> {
                })
        );

        BizException exception = assertThrows(BizException.class, () -> loader.registerTasks(provider, runtimeContext(), metadata()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLUGIN_RUNTIME_ERROR);
    }

    @Test
    void registerTasksShouldRejectTooManyTasksBeforeScheduling() {
        PluginRuntimeLoader loader = loader();
        List<PluginScheduledTask> tasks = new ArrayList<>();
        for (int index = 0; index < 17; index += 1) {
            tasks.add(new PluginScheduledTask("task-" + index, 1, 60, () -> {
            }));
        }
        PluginScheduledTaskProvider provider = context -> tasks;

        BizException exception = assertThrows(BizException.class, () -> loader.registerTasks(provider, runtimeContext(), metadata()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLUGIN_RUNTIME_ERROR);
    }

    private PluginRuntimeLoader loader() {
        return new PluginRuntimeLoader(new ObjectMapper(), new PluginProperties());
    }

    private PluginRuntimeContext runtimeContext() {
        return new PluginRuntimeContext(
                "sample-plugin",
                "1.0.0",
                "0.1.0",
                Path.of("."),
                null,
                new ObjectMapper()
        );
    }

    private PluginDTO.PluginPackageMetadata metadata() {
        PluginDTO.PluginPackageMetadata metadata = new PluginDTO.PluginPackageMetadata();
        metadata.setPluginCode("sample-plugin");
        metadata.setVersion("1.0.0");
        return metadata;
    }
}
