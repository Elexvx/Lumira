package com.lumira.saas.infrastructure.persistence.mybatis;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class SqlRow {

    private final Map<String, Object> values;
    private boolean lastValueWasNull;

    public SqlRow(Map<String, Object> values) {
        this.values = normalize(values);
    }

    public Object getObject(String column) {
        Object value = values.get(normalizeKey(column));
        lastValueWasNull = value == null;
        return value;
    }

    @SuppressWarnings("unchecked")
    public <T> T getObject(String column, Class<T> requiredType) {
        Object value = getObject(column);
        if (value == null || requiredType.isInstance(value)) {
            return (T) value;
        }
        if (requiredType == Long.class || requiredType == long.class) {
            return (T) Long.valueOf(value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value)));
        }
        if (requiredType == Integer.class || requiredType == int.class) {
            return (T) Integer.valueOf(value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value)));
        }
        if (requiredType == String.class) {
            return (T) String.valueOf(value);
        }
        return (T) value;
    }

    public String getString(String column) {
        Object value = getObject(column);
        return value == null ? null : String.valueOf(value);
    }

    public long getLong(String column) {
        Object value = getObject(column);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    public int getInt(String column) {
        Object value = getObject(column);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    public boolean getBoolean(String column) {
        Object value = getObject(column);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    public BigDecimal getBigDecimal(String column) {
        Object value = getObject(column);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    public Timestamp getTimestamp(String column) {
        Object value = getObject(column);
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Timestamp.valueOf(localDateTime);
        }
        return value == null ? null : Timestamp.valueOf(String.valueOf(value));
    }

    public boolean wasNull() {
        return lastValueWasNull;
    }

    public Map<String, Object> asMap() {
        return values;
    }

    private static Map<String, Object> normalize(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            normalized.put(key, value);
            normalized.put(normalizeKey(key), value);
            normalized.putIfAbsent(toCamelCase(key), value);
            normalized.putIfAbsent(normalizeKey(toCamelCase(key)), value);
        });
        return normalized;
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }

    private static String toCamelCase(String value) {
        if (value == null || !value.contains("_")) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }
}
