package com.lumira.saas.infrastructure.persistence.mybatis;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisQueryOperationsTest {

    @Test
    void queryForObjectReturnsNullWhenMapperReturnsNullRow() {
        RawSqlMapper rawSqlMapper = mock(RawSqlMapper.class);
        when(rawSqlMapper.selectList(anyString(), anyList())).thenReturn(Collections.singletonList(null));

        MyBatisQueryOperations operations = new MyBatisQueryOperations(rawSqlMapper);

        assertNull(operations.queryForObject("select nullable_value", String.class));
    }

    @Test
    void existsReturnsTrueWhenMapperReturnsScalarValue() {
        RawSqlMapper rawSqlMapper = mock(RawSqlMapper.class);
        when(rawSqlMapper.selectScalar(anyString(), anyList())).thenReturn(1L);

        MyBatisQueryOperations operations = new MyBatisQueryOperations(rawSqlMapper);

        assertTrue(operations.exists("select 1 from dual"));
    }

    @Test
    void existsReturnsFalseWhenMapperReturnsNull() {
        RawSqlMapper rawSqlMapper = mock(RawSqlMapper.class);
        when(rawSqlMapper.selectScalar(anyString(), anyList())).thenReturn(null);

        MyBatisQueryOperations operations = new MyBatisQueryOperations(rawSqlMapper);

        assertFalse(operations.exists("select 1 from dual where 1 = 0"));
    }
}
