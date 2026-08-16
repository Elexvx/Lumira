package com.lumira.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.file.entity.FileObjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileObjectMapper extends BaseMapper<FileObjectEntity> {

    /**
     * Reads the current row after processing services that update file_object through JdbcTemplate.
     * MyBatis' session cache must not return the pre-scan PENDING_SCAN state.
     */
    @Select("select * from file_object where id = #{id} limit 1")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    FileObjectEntity selectFreshById(@Param("id") Long id);
}
