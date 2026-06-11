package com.lumira.saas.infrastructure.persistence.mybatis;

import java.util.Map;

public final class RawSqlProvider {

    private RawSqlProvider() {
    }

    public static String sql(Map<String, Object> params) {
        return String.valueOf(params.get("sql"));
    }
}
