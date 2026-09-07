package com.lumira.api.alerting;

import java.math.BigDecimal;

/** Read-only business-signal contract; each signal is queried by its owner. */
public interface AlertBusinessSignalQueryPort {
    boolean supports(String signalKey);

    BigDecimal value(String signalKey, int windowSeconds);
}
