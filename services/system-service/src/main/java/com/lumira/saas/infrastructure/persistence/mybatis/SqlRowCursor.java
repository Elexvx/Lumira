package com.lumira.saas.infrastructure.persistence.mybatis;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public class SqlRowCursor {

    private final List<Map<String, Object>> rows;
    private int index = -1;
    private SqlRow current;

    public SqlRowCursor(List<Map<String, Object>> rows) {
        this.rows = rows;
    }

    public boolean next() {
        index += 1;
        if (index >= rows.size()) {
            current = null;
            return false;
        }
        current = new SqlRow(rows.get(index));
        return true;
    }

    public Object getObject(String column) {
        return row().getObject(column);
    }

    public String getString(String column) {
        return row().getString(column);
    }

    public String getString(int columnIndex) {
        Object value = row().asMap().values().stream().skip(Math.max(0, columnIndex - 1L)).findFirst().orElse(null);
        return value == null ? null : String.valueOf(value);
    }

    public long getLong(String column) {
        return row().getLong(column);
    }

    public int getInt(String column) {
        return row().getInt(column);
    }

    public boolean getBoolean(String column) {
        return row().getBoolean(column);
    }

    public BigDecimal getBigDecimal(String column) {
        return row().getBigDecimal(column);
    }

    public Timestamp getTimestamp(String column) {
        return row().getTimestamp(column);
    }

    public boolean wasNull() {
        return row().wasNull();
    }

    private SqlRow row() {
        if (current == null) {
            throw new IllegalStateException("No current row. Call next() before reading values.");
        }
        return current;
    }
}
