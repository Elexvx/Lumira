package com.lumira.asyncruntime;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "lumira.event.relay-loop")
public class OwnerRelayLaneProperties {
    private int queueCapacity = 1;
    private int maxConcurrency = 1;
    private int retryBudget = 1;
    private Duration retryBackoff = Duration.ofMillis(100);
    private int circuitFailureThreshold = 3;
    private Duration circuitOpenDuration = Duration.ofSeconds(30);
    private Duration completionTimeout = Duration.ofSeconds(8);
    private Map<String, LaneOverride> owners = new LinkedHashMap<>();

    LaneSettings settingsFor(String owner) {
        LaneOverride override = owners.get(owner);
        int ownerQueueCapacity = override == null || override.queueCapacity == null ? queueCapacity : override.queueCapacity;
        int ownerMaxConcurrency = override == null || override.maxConcurrency == null ? maxConcurrency : override.maxConcurrency;
        int ownerRetryBudget = override == null || override.retryBudget == null ? retryBudget : override.retryBudget;
        Duration ownerRetryBackoff = override == null || override.retryBackoff == null ? retryBackoff : override.retryBackoff;
        int ownerCircuitThreshold = override == null || override.circuitFailureThreshold == null
                ? circuitFailureThreshold : override.circuitFailureThreshold;
        Duration ownerCircuitOpen = override == null || override.circuitOpenDuration == null
                ? circuitOpenDuration : override.circuitOpenDuration;
        return new LaneSettings(
                positive(ownerQueueCapacity, "queueCapacity"),
                positive(ownerMaxConcurrency, "maxConcurrency"),
                nonNegative(ownerRetryBudget, "retryBudget"),
                positive(ownerRetryBackoff, "retryBackoff"),
                positive(ownerCircuitThreshold, "circuitFailureThreshold"),
                positive(ownerCircuitOpen, "circuitOpenDuration")
        );
    }

    Duration completionTimeout() {
        return positive(completionTimeout, "completionTimeout");
    }

    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
    public int getRetryBudget() { return retryBudget; }
    public void setRetryBudget(int retryBudget) { this.retryBudget = retryBudget; }
    public Duration getRetryBackoff() { return retryBackoff; }
    public void setRetryBackoff(Duration retryBackoff) { this.retryBackoff = retryBackoff; }
    public int getCircuitFailureThreshold() { return circuitFailureThreshold; }
    public void setCircuitFailureThreshold(int circuitFailureThreshold) { this.circuitFailureThreshold = circuitFailureThreshold; }
    public Duration getCircuitOpenDuration() { return circuitOpenDuration; }
    public void setCircuitOpenDuration(Duration circuitOpenDuration) { this.circuitOpenDuration = circuitOpenDuration; }
    public Duration getCompletionTimeout() { return completionTimeout; }
    public void setCompletionTimeout(Duration completionTimeout) { this.completionTimeout = completionTimeout; }
    public Map<String, LaneOverride> getOwners() { return owners; }
    public void setOwners(Map<String, LaneOverride> owners) { this.owners = owners == null ? new LinkedHashMap<>() : owners; }

    record LaneSettings(
            int queueCapacity,
            int maxConcurrency,
            int retryBudget,
            Duration retryBackoff,
            int circuitFailureThreshold,
            Duration circuitOpenDuration
    ) { }

    public static class LaneOverride {
        private Integer queueCapacity;
        private Integer maxConcurrency;
        private Integer retryBudget;
        private Duration retryBackoff;
        private Integer circuitFailureThreshold;
        private Duration circuitOpenDuration;

        public Integer getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(Integer queueCapacity) { this.queueCapacity = queueCapacity; }
        public Integer getMaxConcurrency() { return maxConcurrency; }
        public void setMaxConcurrency(Integer maxConcurrency) { this.maxConcurrency = maxConcurrency; }
        public Integer getRetryBudget() { return retryBudget; }
        public void setRetryBudget(Integer retryBudget) { this.retryBudget = retryBudget; }
        public Duration getRetryBackoff() { return retryBackoff; }
        public void setRetryBackoff(Duration retryBackoff) { this.retryBackoff = retryBackoff; }
        public Integer getCircuitFailureThreshold() { return circuitFailureThreshold; }
        public void setCircuitFailureThreshold(Integer circuitFailureThreshold) { this.circuitFailureThreshold = circuitFailureThreshold; }
        public Duration getCircuitOpenDuration() { return circuitOpenDuration; }
        public void setCircuitOpenDuration(Duration circuitOpenDuration) { this.circuitOpenDuration = circuitOpenDuration; }
    }

    private static int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static int nonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
