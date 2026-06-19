package com.lumira.common.security.data;

import java.util.Locale;

public enum DataScopeType {
    ALL,
    TENANT,
    DEPT_AND_CHILD,
    DEPT,
    CUSTOM,
    SELF;

    public static DataScopeType from(String value) {
        if (value == null || value.isBlank()) {
            return SELF;
        }
        try {
            return DataScopeType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return SELF;
        }
    }
}
