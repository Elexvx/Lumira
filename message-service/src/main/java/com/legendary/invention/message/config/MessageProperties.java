package com.legendary.invention.message.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas.message")
public class MessageProperties {

    private long wsTicketExpiresInSeconds = 30L;

    public long getWsTicketExpiresInSeconds() {
        return wsTicketExpiresInSeconds;
    }

    public void setWsTicketExpiresInSeconds(long wsTicketExpiresInSeconds) {
        this.wsTicketExpiresInSeconds = wsTicketExpiresInSeconds;
    }
}
