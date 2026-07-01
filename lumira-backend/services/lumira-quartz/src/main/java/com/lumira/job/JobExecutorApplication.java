package com.lumira.job;

import java.util.concurrent.CountDownLatch;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.lumira.job",
        "com.lumira.common"
})
@ConfigurationPropertiesScan(basePackages = {
        "com.lumira.job",
        "com.lumira.common"
})
public class JobExecutorApplication {

    public static void main(String[] args) {
        CountDownLatch keepAliveSignal = installKeepAliveThread("lumira-job-executor-keepalive");
        SpringApplication application = new SpringApplication(JobExecutorApplication.class);
        application.setKeepAlive(true);
        try {
            application.run(args);
        } catch (RuntimeException exception) {
            keepAliveSignal.countDown();
            throw exception;
        }
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
