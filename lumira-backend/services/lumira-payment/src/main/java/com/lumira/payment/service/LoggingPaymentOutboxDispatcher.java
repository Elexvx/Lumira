package com.lumira.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "saas.event.outbox", name = "dispatcher", havingValue = "logging", matchIfMissing = true)
public class LoggingPaymentOutboxDispatcher implements PaymentOutboxDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPaymentOutboxDispatcher.class);

    @Override
    public void dispatch(PaymentOutboxRow row) {
        if (row == null) {
            return;
        }
        String payload = StringUtils.hasText(row.getPayloadJson()) ? row.getPayloadJson() : "{}";
        logger.info("payment outbox dispatch: id={}, eventType={}, eventKey={}, payload={}",
                row.getId(), row.getEventType(), row.getEventKey(), payload);
    }
}
