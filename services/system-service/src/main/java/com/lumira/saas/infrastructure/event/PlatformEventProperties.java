package com.lumira.saas.infrastructure.event;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "saas.event")
public class PlatformEventProperties {

    private final Outbox outbox = new Outbox();

    public Outbox getOutbox() {
        return outbox;
    }

    public static class Outbox {

        private boolean relayEnabled = false;
        private long relayFixedDelayMs = 5000;
        private int batchSize = 100;
        private String dispatcher = "logging";
        private String redisStreamKey = "saas:platform-events";

        public boolean isRelayEnabled() {
            return relayEnabled;
        }

        public void setRelayEnabled(boolean relayEnabled) {
            this.relayEnabled = relayEnabled;
        }

        public long getRelayFixedDelayMs() {
            return relayFixedDelayMs;
        }

        public void setRelayFixedDelayMs(long relayFixedDelayMs) {
            this.relayFixedDelayMs = relayFixedDelayMs;
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
