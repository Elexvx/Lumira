package com.lumira.saas.modules.competition.integration.alerting;

import com.lumira.api.alerting.AlertBusinessSignalQueryPort;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Competition-owned business signal adapter for Alerting. */
public class CompetitionAlertBusinessSignalAdapter implements AlertBusinessSignalQueryPort {
    public static final String REGISTRATION_SUBMITTED_SIGNAL = "business.registration.submitted";
    public static final String REVIEW_COMPLETED_SIGNAL = "business.review.completed";

    private final CompetitionSqlOperations database;

    public CompetitionAlertBusinessSignalAdapter(CompetitionSqlOperations database) {
        this.database = database;
    }

    @Override
    public boolean supports(String signalKey) {
        return REGISTRATION_SUBMITTED_SIGNAL.equals(signalKey) || REVIEW_COMPLETED_SIGNAL.equals(signalKey);
    }

    @Override
    public BigDecimal value(String signalKey, int windowSeconds) {
        String sql = switch (signalKey) {
            case REGISTRATION_SUBMITTED_SIGNAL -> """
                    select count(*) from competition_registration
                     where deleted = 0 and created_at >= date_sub(current_timestamp, interval ? second)
                    """;
            case REVIEW_COMPLETED_SIGNAL -> """
                    select count(*) from competition_review_publication
                     where deleted = 0 and status = 'PUBLISHED'
                       and published_at >= date_sub(current_timestamp, interval ? second)
                    """;
            default -> throw new IllegalArgumentException("Unsupported competition business signal");
        };
        Long value = database.queryForObject(sql, Long.class, windowSeconds);
        return BigDecimal.valueOf(value == null ? 0 : value);
    }
}
