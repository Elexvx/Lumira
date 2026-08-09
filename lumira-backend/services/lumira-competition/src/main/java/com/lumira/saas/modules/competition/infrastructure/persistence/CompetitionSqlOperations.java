package com.lumira.saas.modules.competition.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Competition-owned SQL gateway.  It keeps repository SQL on standard JDBC
 * and prevents the new bounded context from inheriting system's MyBatis helper.
 */
@Component
public class CompetitionSqlOperations {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CompetitionSqlOperations(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** For test doubles that override the operation methods. */
    protected CompetitionSqlOperations() {
        this.jdbcTemplate = null;
    }

    public int update(String sql, Object... args) {
        return requireJdbc().update(sql, args);
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return requireJdbc().queryForList(sql, args);
    }

    public boolean exists(String sql, Object... args) {
        return !queryForList(sql, args).isEmpty();
    }

    public <T> List<T> queryForList(String sql, Class<T> requiredType, Object... args) {
        return requireJdbc().queryForList(sql, requiredType, args);
    }

    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        List<T> rows = requireJdbc().queryForList(sql, requiredType, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
        List<T> rows = query(sql, rowMapper, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return requireJdbc().query(sql, (resultSet, rowNum) -> map(rowMapper, resultSet, rowNum), args);
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("No Competition JDBC operations are configured");
        }
        return jdbcTemplate;
    }

    private <T> T map(RowMapper<T> rowMapper, ResultSet resultSet, int rowNum) {
        try {
            return rowMapper.mapRow(new JdbcResultSetSqlRow(resultSet), rowNum);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to map Competition SQL row", exception);
        }
    }

    private static final class JdbcResultSetSqlRow extends SqlRow {
        private final ResultSet resultSet;

        private JdbcResultSetSqlRow(ResultSet resultSet) {
            super(Map.of());
            this.resultSet = resultSet;
        }

        @Override
        public Object getObject(String column) {
            try {
                return resultSet.getObject(column);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public <T> T getObject(String column, Class<T> requiredType) {
            try {
                return resultSet.getObject(column, requiredType);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public String getString(String column) {
            try {
                return resultSet.getString(column);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public long getLong(String column) {
            try {
                return resultSet.getLong(column);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public int getInt(String column) {
            try {
                return resultSet.getInt(column);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public boolean getBoolean(String column) {
            try {
                return resultSet.getBoolean(column);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public BigDecimal getBigDecimal(String column) {
            try {
                return resultSet.getBigDecimal(column);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public Timestamp getTimestamp(String column) {
            try {
                return resultSet.getTimestamp(column);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public boolean wasNull() {
            try {
                return resultSet.wasNull();
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
