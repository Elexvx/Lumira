package com.lumira.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.file.entity.FileStorageSpaceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileStorageSpaceMapper extends BaseMapper<FileStorageSpaceEntity> {

    List<FileStorageSpaceEntity> listWithUsage(
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    FileStorageSpaceEntity findDefault();

    FileStorageSpaceEntity findByStorageKey(@Param("storageKey") String storageKey);

    FileStorageSpaceEntity findByIdWithUsage(@Param("id") Long id);

    Boolean shouldRetainStoredFile(@Param("storageKey") String storageKey);

    String findAccessKeySecret(@Param("id") Long id);

    void clearDefaultStorage();

    Long countDefaultStorage();

    void ensureFirstDefaultStorage();
}
