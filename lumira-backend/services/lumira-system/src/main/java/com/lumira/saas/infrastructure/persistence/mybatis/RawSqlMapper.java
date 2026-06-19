package com.lumira.saas.infrastructure.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

import java.util.List;
import java.util.Map;

@Mapper
public interface RawSqlMapper {

    @SelectProvider(type = RawSqlProvider.class, method = "sql")
    List<Map<String, Object>> selectList(@Param("sql") String sql, @Param("params") List<Object> params);

    @SelectProvider(type = RawSqlProvider.class, method = "sql")
    Long selectScalar(@Param("sql") String sql, @Param("params") List<Object> params);

    @UpdateProvider(type = RawSqlProvider.class, method = "sql")
    int update(@Param("sql") String sql, @Param("params") List<Object> params);
}
