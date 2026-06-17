package com.lumira.saas.infrastructure.persistence.mybatis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MyBatisQueryOperations {

    private final RawSqlMapper rawSqlMapper;
    private final Object legacyOperations;

    @Autowired
    public MyBatisQueryOperations(RawSqlMapper rawSqlMapper) {
        this.rawSqlMapper = rawSqlMapper;
        this.legacyOperations = null;
    }

    protected MyBatisQueryOperations() {
        this.rawSqlMapper = null;
        this.legacyOperations = null;
    }

    public MyBatisQueryOperations(Object legacyOperations) {
        this.rawSqlMapper = null;
        this.legacyOperations = legacyOperations;
    }

    public int update(String sql, Object... args) {
        if (rawSqlMapper == null) {
            return invokeLegacy("update", new Class<?>[]{String.class, Object[].class}, sql, args);
        }
        BoundSql boundSql = bind(sql, args);
        return rawSqlMapper.update(boundSql.sql(), boundSql.params());
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        if (rawSqlMapper == null) {
            return invokeLegacy("queryForList", new Class<?>[]{String.class, Object[].class}, sql, args);
        }
        BoundSql boundSql = bind(sql, args);
        return rawSqlMapper.selectList(boundSql.sql(), boundSql.params());
    }

    public boolean exists(String sql, Object... args) {
        if (rawSqlMapper == null) {
            return !queryForList(sql, args).isEmpty();
        }
        BoundSql boundSql = bind(sql, args);
        return rawSqlMapper.selectScalar(boundSql.sql(), boundSql.params()) != null;
    }

    public <T> List<T> queryForList(String sql, Class<T> requiredType, Object... args) {
        if (rawSqlMapper == null) {
            return invokeLegacy("queryForList", new Class<?>[]{String.class, Class.class, Object[].class}, sql, requiredType, args);
        }
        return queryForList(sql, args).stream()
                .map(row -> convertScalar(firstValue(row), requiredType))
                .toList();
    }

    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        if (rawSqlMapper == null) {
            return invokeLegacy("queryForObject", new Class<?>[]{String.class, Class.class, Object[].class}, sql, requiredType, args);
        }
        List<Map<String, Object>> rows = queryForList(sql, args);
        if (rows.isEmpty()) {
            return null;
        }
        return convertScalar(firstValue(rows.get(0)), requiredType);
    }

    public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
        if (rawSqlMapper == null) {
            Object springMapper = toLegacyRowMapper(rowMapper);
            return invokeLegacy("queryForObject", new Class<?>[]{String.class, legacyType("RowMapper"), Object[].class}, sql, springMapper, args);
        }
        List<T> rows = query(sql, rowMapper, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        if (rawSqlMapper == null) {
            Object springMapper = toLegacyRowMapper(rowMapper);
            return invokeLegacy("query", new Class<?>[]{String.class, legacyType("RowMapper"), Object[].class}, sql, springMapper, args);
        }
        List<Map<String, Object>> rows = queryForList(sql, args);
        List<T> mapped = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i += 1) {
            try {
                mapped.add(rowMapper.mapRow(new SqlRow(rows.get(i)), i));
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to map SQL row", exception);
            }
        }
        return mapped;
    }

    public <T> T query(String sql, ResultSetExtractor<T> extractor, Object... args) {
        if (rawSqlMapper == null) {
            Object springExtractor = toLegacyResultSetExtractor(extractor);
            return invokeLegacy("query", new Class<?>[]{String.class, legacyType("ResultSetExtractor"), Object[].class}, sql, springExtractor, args);
        }
        return extractor.extractData(new SqlRowCursor(queryForList(sql, args)));
    }

    private <T> Object toLegacyResultSetExtractor(ResultSetExtractor<T> extractor) {
        Class<?> extractorType = legacyType("ResultSetExtractor");
        return Proxy.newProxyInstance(
                extractorType.getClassLoader(),
                new Class<?>[]{extractorType},
                (proxy, method, args) -> {
                    if (!"extractData".equals(method.getName())) {
                        return method.invoke(this, args);
                    }
                    ResultSet resultSet = (ResultSet) args[0];
                    return extractResultSet(extractor, resultSet);
                }
        );
    }

    private <T> T extractResultSet(ResultSetExtractor<T> extractor, ResultSet resultSet) throws Exception {
                List<Map<String, Object>> rows = new ArrayList<>();
                var metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                while (resultSet.next()) {
                    java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<>();
                    for (int columnIndex = 1; columnIndex <= columnCount; columnIndex += 1) {
                        String label = metaData.getColumnLabel(columnIndex);
                        row.put(label, resultSet.getObject(columnIndex));
                    }
                    rows.add(row);
                }
                return extractor.extractData(new SqlRowCursor(rows));
    }

    private BoundSql bind(String sql, Object... args) {
        List<Object> params = new ArrayList<>();
        StringBuilder bound = new StringBuilder();
        int argIndex = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < sql.length(); i += 1) {
            char ch = sql.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                bound.append(ch);
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                bound.append(ch);
            } else if (ch == '?' && !inSingleQuote && !inDoubleQuote) {
                bound.append("#{params[").append(argIndex).append("]}");
                params.add(args[argIndex]);
                argIndex += 1;
            } else {
                bound.append(ch);
            }
        }
        return new BoundSql(bound.toString(), params);
    }

    @SuppressWarnings("unchecked")
    private <T> T convertScalar(Object value, Class<T> requiredType) {
        if (value == null) {
            return null;
        }
        if (requiredType.isInstance(value)) {
            return (T) value;
        }
        if (requiredType == Long.class || requiredType == long.class) {
            return (T) Long.valueOf(((Number) toNumber(value)).longValue());
        }
        if (requiredType == Integer.class || requiredType == int.class) {
            return (T) Integer.valueOf(((Number) toNumber(value)).intValue());
        }
        if (requiredType == Boolean.class || requiredType == boolean.class) {
            if (value instanceof Number number) {
                return (T) Boolean.valueOf(number.intValue() != 0);
            }
            return (T) Boolean.valueOf(Boolean.parseBoolean(String.valueOf(value)));
        }
        if (requiredType == String.class) {
            return (T) String.valueOf(value);
        }
        if (requiredType == BigDecimal.class) {
            return (T) (value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value)));
        }
        return (T) value;
    }

    private Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private Object firstValue(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return row.values().stream().findFirst().orElse(null);
    }

    private <T> Object toLegacyRowMapper(RowMapper<T> rowMapper) {
        Class<?> rowMapperType = legacyType("RowMapper");
        return Proxy.newProxyInstance(
                rowMapperType.getClassLoader(),
                new Class<?>[]{rowMapperType},
                (proxy, method, args) -> {
                    if (!"mapRow".equals(method.getName())) {
                        return method.invoke(this, args);
                    }
                    try {
                        return rowMapper.mapRow(new JdbcResultSetSqlRow((ResultSet) args[0]), (Integer) args[1]);
                    } catch (Exception exception) {
                        throw new IllegalStateException("Failed to map SQL row", exception);
                    }
                }
        );
    }

    private Class<?> legacyType(String simpleName) {
        try {
            return Class.forName("org.springframework.jdbc.core." + simpleName);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Spring JDBC compatibility type is unavailable", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeLegacy(String method, Class<?>[] parameterTypes, Object... args) {
        if (legacyOperations == null) {
            throw new IllegalStateException("No SQL executor is configured");
        }
        try {
            Method targetMethod = findMethod(legacyOperations.getClass(), method, parameterTypes);
            targetMethod.setAccessible(true);
            return (T) targetMethod.invoke(legacyOperations, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke legacy SQL executor", exception);
        }
    }

    private Method findMethod(Class<?> type, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private record BoundSql(String sql, List<Object> params) {
    }
}
