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
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    void loadShouldRejectInProcessBackendExecutionByDefault() {
        PluginRuntimeLoader loader = loader();

        BizException exception = assertThrows(
                BizException.class,
                () -> loader.load(metadata(), Path.of("."))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLUGIN_RUNTIME_ERROR);
        assertThat(exception.getMessage()).contains("in-process backend plugin execution is disabled");
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

    @Test
    void registerTasksShouldKeepTaskScheduledAfterRuntimeException() {
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        PluginRuntimeLoader loader = loader(executor);
        AtomicInteger runs = new AtomicInteger();
        PluginScheduledTaskProvider provider = context -> List.of(
                new PluginScheduledTask("daily.sync", 1, 60, () -> {
                    if (runs.getAndIncrement() == 0) {
                        throw new IllegalStateException("boom");
                    }
                })
        );

        List<ScheduledExecutorService> executors = loader.registerTasks(provider, runtimeContext(), metadata());

        assertThat(executors).containsExactly(executor);
        assertThatCode(executor::runScheduledTask).doesNotThrowAnyException();
        assertThatCode(executor::runScheduledTask).doesNotThrowAnyException();
        assertThat(runs.get()).isEqualTo(2);
    }

    private PluginRuntimeLoader loader() {
        return new PluginRuntimeLoader(new ObjectMapper(), new PluginProperties());
    }

    private PluginRuntimeLoader loader(RecordingScheduledExecutor executor) {
        return new PluginRuntimeLoader(new ObjectMapper(), new PluginProperties()) {
            @Override
            ScheduledExecutorService createScheduledExecutor(String pluginCode, String taskCode) {
                return executor;
            }
        };
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

    private static final class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private Runnable scheduledTask;
        private boolean shutdown;

        void runScheduledTask() {
            if (scheduledTask != null) {
                scheduledTask.run();
            }
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            this.scheduledTask = command;
            return new CompletedScheduledFuture();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CompletedScheduledFuture implements ScheduledFuture<Object> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
