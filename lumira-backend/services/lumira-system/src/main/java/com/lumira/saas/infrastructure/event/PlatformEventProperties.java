package com.lumira.saas.infrastructure.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas.event")
public class PlatformEventProperties {

    private final Outbox outbox = new Outbox();

    public Outbox getOutbox() {
        return outbox;
    }

    public static class Outbox {

        private boolean relayEnabled = false;
        private int batchSize = 100;
        private int maxDrainRounds = 4;
        private int maxBurstRounds = 12;
        private String dispatcher = "logging";
        private String redisStreamKey = "saas:platform-events";

        public boolean isRelayEnabled() {
            return relayEnabled;
        }

        public void setRelayEnabled(boolean relayEnabled) {
            this.relayEnabled = relayEnabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public String getDispatcher() {
            return dispatcher;
        }

        public int getMaxDrainRounds() {
            return maxDrainRounds;
        }

        public void setMaxDrainRounds(int maxDrainRounds) {
            this.maxDrainRounds = maxDrainRounds;
        }

        public int getMaxBurstRounds() {
            return maxBurstRounds;
        }

        public void setMaxBurstRounds(int maxBurstRounds) {
            this.maxBurstRounds = maxBurstRounds;
        }

        public void setDispatcher(String dispatcher) {
            this.dispatcher = dispatcher;
        }

        public String getRedisStreamKey() {
            return redisStreamKey;
        }

        public void setRedisStreamKey(String redisStreamKey) {
            this.redisStreamKey = redisStreamKey;
        }
    }
}
