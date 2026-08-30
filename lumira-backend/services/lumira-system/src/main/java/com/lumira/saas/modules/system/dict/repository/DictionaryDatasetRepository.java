package com.lumira.saas.modules.system.dict.repository;

import com.lumira.saas.modules.system.dict.app.DictionaryImportService;
import java.time.LocalDateTime;
import java.util.List;

public interface DictionaryDatasetRepository {

    boolean acquireInitializationLock(String lockName, int timeoutSeconds);

    void releaseInitializationLock(String lockName);

    Installation findInstallation(String datasetCode);

    int countActiveTypes(String dictCode);

    Long insertType(TypeInsert type);

    void insertItems(Long typeId, List<DictionaryImportService.DictionaryRow> rows, Long actorId, String actorUuid);

    void recordInstallation(Installation installation);

    record TypeInsert(
            String dictCode, String dictName, String status, boolean system, String remark,
            String structureType, Long actorId, String actorUuid
    ) {}

    record Installation(
            String datasetCode, String version, String sha256, int rowCount, String status, LocalDateTime installedAt
    ) {}
}
