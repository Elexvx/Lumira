package com.lumira.file.integration.alerting;

import com.lumira.api.alerting.AlertBusinessSignalQueryPort;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;

/** File-owned business signal adapter for Alerting. */
public class FileAlertBusinessSignalAdapter implements AlertBusinessSignalQueryPort {
    public static final String FILE_SCAN_FAILED_SIGNAL = "business.file.scan.failed";

    private final JdbcTemplate jdbc;

    public FileAlertBusinessSignalAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String signalKey) {
        return FILE_SCAN_FAILED_SIGNAL.equals(signalKey);
    }

    @Override
    public BigDecimal value(String signalKey, int windowSeconds) {
        if (!supports(signalKey)) {
            throw new IllegalArgumentException("Unsupported file business signal");
        }
        Long value = jdbc.queryForObject("""
                select count(*) from file_processing_task
                 where deleted = 0 and task_type = 'SECURITY_SCAN' and status = 'FAILED'
                   and updated_at >= date_sub(current_timestamp, interval ? second)
                """, Long.class, windowSeconds);
        return BigDecimal.valueOf(value == null ? 0 : value);
    }
}
