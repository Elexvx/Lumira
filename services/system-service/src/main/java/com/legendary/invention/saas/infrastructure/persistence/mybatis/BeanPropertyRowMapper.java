package com.legendary.invention.saas.infrastructure.persistence.mybatis;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import java.lang.reflect.Field;
import java.util.Map;

public class BeanPropertyRowMapper<T> implements RowMapper<T> {

    private final Class<T> mappedClass;

    public BeanPropertyRowMapper(Class<T> mappedClass) {
        this.mappedClass = mappedClass;
    }

    public Class<T> getMappedClass() {
        return mappedClass;
    }

    @Override
    public T mapRow(SqlRow row, int rowNum) {
        T instance = BeanUtils.instantiateClass(mappedClass);
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(instance);
        wrapper.setAutoGrowNestedPaths(false);
        for (Map.Entry<String, Object> entry : row.asMap().entrySet()) {
            String propertyName = normalizePropertyName(entry.getKey());
            if (wrapper.isWritableProperty(propertyName)) {
                wrapper.setPropertyValue(propertyName, convertValue(entry.getValue(), wrapper.getPropertyType(propertyName)));
            } else {
                setField(instance, propertyName, entry.getValue());
            }
        }
        return instance;
    }

    private void setField(T instance, String propertyName, Object value) {
        Field field = findField(mappedClass, propertyName);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(instance, convertValue(value, field.getType()));
        } catch (IllegalAccessException ignored) {
            // Ignore non-writable fields to keep mapper behavior lenient like BeanPropertyRowMapper.
        }
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null || targetType == null) {
            return value;
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return toBoolean(value);
        }
        return value;
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text) || "off".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return Boolean.parseBoolean(text);
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private String normalizePropertyName(String columnName) {
        String value = columnName == null ? "" : columnName;
        if (!value.contains("_")) {
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
