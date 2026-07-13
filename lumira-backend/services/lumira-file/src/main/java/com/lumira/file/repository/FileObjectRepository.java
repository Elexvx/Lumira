package com.lumira.file.repository;

import com.lumira.file.entity.FileObjectEntity;
import java.util.List;
import java.util.Set;

public interface FileObjectRepository {
    void insert(FileObjectEntity entity);
    FileObjectEntity findById(Long id);
    int softDelete(Long id, Long userId, String userUuid, boolean requireOwner);
    boolean existsInBucket(String storageKey);
    List<FileObjectEntity> search(Query query, Access access, long offset, long limit);
    long countCandidates(Query query, Access access, long limit);
    FileObjectEntity findVisibleById(Long id, Access access);

    record Query(String keyword, boolean includeRemarkInKeyword, String category, String fileExtension,
                 String previewMode, String bucket, String contentTypePrefix, String status,
                 String sortField, boolean ascending) { }

    record Access(boolean downloadCenter, boolean all, Long ownerUserId, String ownerUserUuid,
                  Set<Long> departmentIds, Set<Long> userIds) { }
}
