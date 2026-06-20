package com.lumira.saas.infrastructure.persistence;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class BatchJdbcHelper {

    private static final int DEFAULT_BATCH_SIZE = 500;

    private final MyBatisQueryOperations jdbcTemplate;
    private final MeterRegistry meterRegistry;

    public BatchJdbcHelper(MyBatisQueryOperations jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    public <T> int insertValues(String insertPrefixSql, List<T> rows, int batchSize, RowValueBinder<T> binder) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Objects.requireNonNull(insertPrefixSql, "insertPrefixSql is required");
        Objects.requireNonNull(binder, "binder is required");
        int safeBatchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        int affected = 0;
        Instant startedAt = Instant.now();
        try {
            for (int start = 0; start < rows.size(); start += safeBatchSize) {
                int end = Math.min(rows.size(), start + safeBatchSize);
                List<Object> args = new ArrayList<>();
                StringBuilder sql = new StringBuilder(insertPrefixSql).append(" values ");
                for (int index = start; index < end; index += 1) {
                    if (index > start) {
                        sql.append(", ");
                    }
                    RowValues values = binder.bind(rows.get(index));
                    sql.append("(").append("?,".repeat(values.args().size()).replaceFirst(",$", "")).append(")");
                    args.addAll(values.args());
                }
                affected += jdbcTemplate.update(sql.toString(), args.toArray());
            }
            return affected;
        } finally {
            Timer.builder("batch.write.duration")
                    .description("Batch JDBC write duration.")
                    .register(meterRegistry)
                    .record(Duration.between(startedAt, Instant.now()));
        }
    }

    public record RowValues(List<Object> args) {
    }

    @FunctionalInterface
    public interface RowValueBinder<T> {
        RowValues bind(T row);
    }
}
