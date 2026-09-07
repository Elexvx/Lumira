package com.lumira.payment.integration.alerting;

import com.lumira.api.alerting.AlertBusinessSignalQueryPort;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Payment-owned business signal adapter for Alerting. */
public class PaymentAlertBusinessSignalAdapter implements AlertBusinessSignalQueryPort {
    public static final String PAYMENT_PAID_SIGNAL = "business.payment.paid";

    private final JdbcTemplate jdbc;

    public PaymentAlertBusinessSignalAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String signalKey) {
        return PAYMENT_PAID_SIGNAL.equals(signalKey);
    }

    @Override
    public BigDecimal value(String signalKey, int windowSeconds) {
        if (!supports(signalKey)) {
            throw new IllegalArgumentException("Unsupported payment business signal");
        }
        Long value = jdbc.queryForObject("""
                select count(*) from payment_event_outbox
                 where deleted = 0 and event_type = 'PAYMENT_ORDER_PAID'
                   and created_at >= date_sub(current_timestamp, interval ? second)
                """, Long.class, windowSeconds);
        return BigDecimal.valueOf(value == null ? 0 : value);
    }
}
