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
                wrapper.setPropertyValue(propertyName, entry.getValue());
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
            field.set(instance, value);
        } catch (IllegalAccessException ignored) {
            // Ignore non-writable fields to keep mapper behavior lenient like BeanPropertyRowMapper.
        }
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
