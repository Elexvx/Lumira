package com.lumira.file.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lumira.api.file.StorageSpaceDTO;
import com.lumira.file.entity.FileStorageSpaceEntity;
import com.lumira.file.mapper.FileStorageSpaceMapper;
import com.lumira.file.repository.FileStorageSpaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisFileStorageSpaceRepository implements FileStorageSpaceRepository {
    private final FileStorageSpaceMapper mapper;

    public MyBatisFileStorageSpaceRepository(FileStorageSpaceMapper mapper) { this.mapper = mapper; }

    @Override public long countCandidates(long limit) {
        if (limit <= 0) return 0L;
        List<FileStorageSpaceEntity> rows = mapper.selectList(new QueryWrapper<FileStorageSpaceEntity>()
                .select("id").eq("deleted", 0).last("limit " + limit));
        return rows == null ? 0L : rows.size();
    }
    @Override public List<FileStorageSpaceEntity> listWithUsage(long limit, long offset) { return mapper.listWithUsage(limit, offset); }
    @Override public FileStorageSpaceEntity findDefault() { return mapper.findDefault(); }
    @Override public FileStorageSpaceEntity findByStorageKey(String key) { return mapper.findByStorageKey(key); }
    @Override public FileStorageSpaceEntity findByIdWithUsage(Long id) { return mapper.findByIdWithUsage(id); }
    @Override public Boolean shouldRetainStoredFile(String key) { return mapper.shouldRetainStoredFile(key); }
    @Override public String findAccessKeySecret(Long id) { return mapper.findAccessKeySecret(id); }
    @Override public void insert(FileStorageSpaceEntity entity) { mapper.insert(entity); }

    @Override
    public int update(Long id, StorageSpaceDTO expected, FileStorageSpaceEntity e) {
        return mapper.update(null, new LambdaUpdateWrapper<FileStorageSpaceEntity>()
                .set(FileStorageSpaceEntity::getTitle, e.getTitle()).set(FileStorageSpaceEntity::getRootPath, e.getRootPath())
                .set(FileStorageSpaceEntity::getBucketName, e.getBucketName()).set(FileStorageSpaceEntity::getEndpoint, e.getEndpoint())
                .set(FileStorageSpaceEntity::getRegion, e.getRegion()).set(FileStorageSpaceEntity::getAccessKeyId, e.getAccessKeyId())
                .set(FileStorageSpaceEntity::getAccessKeySecret, e.getAccessKeySecret()).set(FileStorageSpaceEntity::getRenameStrategy, e.getRenameStrategy())
                .set(FileStorageSpaceEntity::getMaxFileSizeMb, e.getMaxFileSizeMb()).set(FileStorageSpaceEntity::getAllowedMimeTypes, e.getAllowedMimeTypes())
                .set(FileStorageSpaceEntity::getDefaultFlag, e.getDefaultFlag()).set(FileStorageSpaceEntity::getRetainFileOnRecordDelete, e.getRetainFileOnRecordDelete())
                .set(FileStorageSpaceEntity::getAnonymousAccessAllowed, e.getAnonymousAccessAllowed()).set(FileStorageSpaceEntity::getStatus, e.getStatus())
                .set(FileStorageSpaceEntity::getUpdatedBy, e.getUpdatedBy()).set(FileStorageSpaceEntity::getUpdatedByUuid, e.getUpdatedByUuid())
                .set(FileStorageSpaceEntity::getUpdatedAt, e.getUpdatedAt())
                .eq(FileStorageSpaceEntity::getId, id).eq(FileStorageSpaceEntity::getStorageKey, expected.storageKey())
                .eq(FileStorageSpaceEntity::getProvider, expected.provider()).eq(FileStorageSpaceEntity::getStatus, expected.status())
                .eq(FileStorageSpaceEntity::getDefaultFlag, Boolean.TRUE.equals(expected.defaultStorage()) ? 1 : 0)
                .eq(FileStorageSpaceEntity::getDeleted, 0));
    }

    @Override
    public int delete(Long id, StorageSpaceDTO expected, Long userId, String uuid) {
        return mapper.update(null, new LambdaUpdateWrapper<FileStorageSpaceEntity>()
                .set(FileStorageSpaceEntity::getDeleted, 1).set(FileStorageSpaceEntity::getUpdatedBy, userId)
                .set(FileStorageSpaceEntity::getUpdatedByUuid, uuid).set(FileStorageSpaceEntity::getUpdatedAt, LocalDateTime.now())
                .eq(FileStorageSpaceEntity::getId, id).eq(FileStorageSpaceEntity::getStorageKey, expected.storageKey())
                .eq(FileStorageSpaceEntity::getProvider, expected.provider()).eq(FileStorageSpaceEntity::getStatus, expected.status())
                .eq(FileStorageSpaceEntity::getDefaultFlag, Boolean.TRUE.equals(expected.defaultStorage()) ? 1 : 0)
                .eq(FileStorageSpaceEntity::getDeleted, 0));
    }

    @Override public void clearDefaultStorage() { mapper.clearDefaultStorage(); }
    @Override public Long countDefaultStorage() { return mapper.countDefaultStorage(); }
    @Override public void ensureFirstDefaultStorage() { mapper.ensureFirstDefaultStorage(); }
}
