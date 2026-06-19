package com.lumira.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.file.entity.FileStorageSpaceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileStorageSpaceMapper extends BaseMapper<FileStorageSpaceEntity> {

    List<FileStorageSpaceEntity> listWithUsage(
            @Param("tenantId") Long tenantId,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    FileStorageSpaceEntity findDefault(@Param("tenantId") Long tenantId);

    FileStorageSpaceEntity findByStorageKey(@Param("tenantId") Long tenantId, @Param("storageKey") String storageKey);

    FileStorageSpaceEntity findByIdWithUsage(@Param("tenantId") Long tenantId, @Param("id") Long id);

    Boolean shouldRetainStoredFile(@Param("tenantId") Long tenantId, @Param("storageKey") String storageKey);

    String findAccessKeySecret(@Param("tenantId") Long tenantId, @Param("id") Long id);

    void clearDefaultStorage(@Param("tenantId") Long tenantId);

    Long countDefaultStorage(@Param("tenantId") Long tenantId);

    void ensureFirstDefaultStorage(@Param("tenantId") Long tenantId);
}
