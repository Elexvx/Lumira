package com.lumira.file.repository;

import com.lumira.api.file.StorageSpaceDTO;
import com.lumira.file.entity.FileStorageSpaceEntity;
import java.util.List;

public interface FileStorageSpaceRepository {
    long countCandidates(long limit);
    List<FileStorageSpaceEntity> listWithUsage(long limit, long offset);
    FileStorageSpaceEntity findDefault();
    FileStorageSpaceEntity findByStorageKey(String storageKey);
    FileStorageSpaceEntity findByIdWithUsage(Long id);
    Boolean shouldRetainStoredFile(String storageKey);
    String findAccessKeySecret(Long id);
    void insert(FileStorageSpaceEntity entity);
    int update(Long id, StorageSpaceDTO expected, FileStorageSpaceEntity replacement);
    int delete(Long id, StorageSpaceDTO expected, Long userId, String userUuid);
    void clearDefaultStorage();
    Long countDefaultStorage();
    void ensureFirstDefaultStorage();
}
