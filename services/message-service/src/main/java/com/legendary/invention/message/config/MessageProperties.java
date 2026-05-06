package com.legendary.invention.message.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas.message")
public class MessageProperties {

    private long wsTicketExpiresInSeconds = 30L;
    private int outboxRelayBatchSize = 100;

    public long getWsTicketExpiresInSeconds() {
        return wsTicketExpiresInSeconds;
    }

    public void setWsTicketExpiresInSeconds(long wsTicketExpiresInSeconds) {
        this.wsTicketExpiresInSeconds = wsTicketExpiresInSeconds;
    }

    public int getOutboxRelayBatchSize() {
        return outboxRelayBatchSize;
    }

    public void setOutboxRelayBatchSize(int outboxRelayBatchSize) {
        this.outboxRelayBatchSize = outboxRelayBatchSize;
    }
}
