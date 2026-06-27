package com.lumira.asyncruntime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@Import(LumiraAsyncRuntimeAssemblyConfiguration.class)
public class LumiraAsyncApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(LumiraAsyncApplication.class);
        if (isStartupProfilingEnabled()) {
            application.setApplicationStartup(new BufferingApplicationStartup(2048));
        }
        application.run(args);
    }

    private static boolean isStartupProfilingEnabled() {
        String systemProperty = System.getProperty("lumira.startup.profiling.enabled");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return Boolean.parseBoolean(systemProperty);
        }
        return Boolean.parseBoolean(System.getenv().getOrDefault("LUMIRA_STARTUP_PROFILING_ENABLED", "false"));
    }
}
