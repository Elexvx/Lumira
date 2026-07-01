package com.lumira.asyncruntime;

import java.util.concurrent.CountDownLatch;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@Import(LumiraAsyncRuntimeAssemblyConfiguration.class)
public class LumiraAsyncApplication {

    public static void main(String[] args) {
        CountDownLatch keepAliveSignal = installKeepAliveThread("lumira-async-keepalive");
        SpringApplication application = new SpringApplication(LumiraAsyncApplication.class);
        application.setKeepAlive(true);
        if (isStartupProfilingEnabled()) {
            application.setApplicationStartup(new BufferingApplicationStartup(2048));
        }
        try {
            application.run(args);
        } catch (RuntimeException exception) {
            keepAliveSignal.countDown();
            throw exception;
        }
    }

    private static boolean isStartupProfilingEnabled() {
        String systemProperty = System.getProperty("lumira.startup.profiling.enabled");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return Boolean.parseBoolean(systemProperty);
        }
        return Boolean.parseBoolean(System.getenv().getOrDefault("LUMIRA_STARTUP_PROFILING_ENABLED", "false"));
    }

    private static CountDownLatch installKeepAliveThread(String threadName) {
        CountDownLatch keepAliveSignal = new CountDownLatch(1);
        Thread keepAliveThread = new Thread(() -> awaitKeepAliveRelease(keepAliveSignal), threadName);
        keepAliveThread.setDaemon(false);
        keepAliveThread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(keepAliveSignal::countDown, threadName + "-shutdown"));
        return keepAliveSignal;
    }

    private static void awaitKeepAliveRelease(CountDownLatch keepAliveSignal) {
        try {
            keepAliveSignal.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
