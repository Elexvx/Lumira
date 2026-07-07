package com.lumira.job;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AdaptiveRelaySchedulerTest {

    @Test
    void runOnceShouldRescheduleAfterUnexpectedThrowable() {
        BackendJobClient backendJobClient = mock(BackendJobClient.class);
        doThrow(new AssertionError("boom")).when(backendJobClient).relayOutbox();
        JobExecutorProperties.AdaptiveRelay properties = new JobExecutorProperties.AdaptiveRelay();
        properties.setEnabled(true);
        properties.setPlatformEnabled(true);
        properties.setMessageEnabled(false);
        properties.setFileEnabled(false);
        properties.setPaymentEnabled(false);
        properties.setPluginEnabled(false);
        properties.setFailureDelayMs(1234L);
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        AdaptiveRelayScheduler scheduler = new AdaptiveRelayScheduler(backendJobClient, properties, executor);

        scheduler.start();

        assertThat(executor.scheduleCount()).isEqualTo(1);
        assertThatCode(executor::runScheduledTask).doesNotThrowAnyException();
        assertThat(executor.scheduleCount()).isEqualTo(2);
        assertThat(executor.lastDelayMs()).isEqualTo(1234L);
    }

    private static final class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private Runnable scheduledTask;
        private long lastDelayMs;
        private int scheduleCount;
        private boolean shutdown;

        int scheduleCount() {
            return scheduleCount;
        }

        long lastDelayMs() {
            return lastDelayMs;
        }

        void runScheduledTask() {
            if (scheduledTask != null) {
                scheduledTask.run();
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            this.scheduledTask = command;
            this.lastDelayMs = unit.toMillis(delay);
            this.scheduleCount += 1;
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
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
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
