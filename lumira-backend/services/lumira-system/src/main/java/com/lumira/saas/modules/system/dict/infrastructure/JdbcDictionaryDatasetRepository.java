package com.lumira.saas.modules.system.dict.infrastructure;

import com.lumira.saas.modules.system.dict.app.DictionaryImportService;
import com.lumira.saas.modules.system.dict.repository.DictionaryDatasetRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDictionaryDatasetRepository implements DictionaryDatasetRepository {

    private final JdbcTemplate database;

    public JdbcDictionaryDatasetRepository(JdbcTemplate database) {
        this.database = database;
    }

    @Override
    public boolean acquireInitializationLock(String lockName, int timeoutSeconds) {
        Integer locked = database.queryForObject("select get_lock(?, ?)", Integer.class, lockName, timeoutSeconds);
        return locked != null && locked == 1;
    }

    @Override
    public void releaseInitializationLock(String lockName) {
        database.queryForObject("select release_lock(?)", Integer.class, lockName);
    }

    @Override
    public Installation findInstallation(String datasetCode) {
        List<Installation> installed = database.query(
                """
                        select dataset_code, dataset_version, file_sha256, row_count, status, installed_at
                        from sys_dictionary_dataset_installation
                        where dataset_code = ? and status = 'INSTALLED'
                        """,
                (rs, rowNum) -> new Installation(
                        rs.getString("dataset_code"), rs.getString("dataset_version"), rs.getString("file_sha256"),
                        rs.getInt("row_count"), rs.getString("status"), rs.getTimestamp("installed_at").toLocalDateTime()
                ),
                datasetCode
        );
        return installed.isEmpty() ? null : installed.getFirst();
    }

    @Override
    public int countActiveTypes(String dictCode) {
        Integer count = database.queryForObject(
                "select count(1) from sys_dict_type where dict_code = ? and deleted = 0",
                Integer.class,
                dictCode
        );
        return count == null ? 0 : count;
    }

    @Override
    public Long insertType(TypeInsert type) {
        database.update(
                """
                        insert into sys_dict_type
                            (dict_code, dict_name, status, is_system, remark, structure_type,
                             created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                type.dictCode(), type.dictName(), type.status(), type.system() ? 1 : 0, type.remark(),
                type.structureType(), type.actorId(), type.actorUuid(), type.actorId(), type.actorUuid()
        );
        return database.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public void insertItems(Long typeId, List<DictionaryImportService.DictionaryRow> rows, Long actorId, String actorUuid) {
        long safeActorId = actorId == null ? 0L : actorId;
        database.batchUpdate(
                """
                        insert into sys_dict_item
                            (dict_type_id, item_value, item_label, parent_item_value, level_no, leaf,
                             sort_no, status, remark, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                rows,
                500,
                (statement, row) -> {
                    statement.setLong(1, typeId);
                    statement.setString(2, row.itemValue());
                    statement.setString(3, row.itemLabel());
                    statement.setString(4, row.parentItemValue());
                    statement.setInt(5, row.levelNo());
                    statement.setInt(6, row.leaf() ? 1 : 0);
                    statement.setInt(7, row.sortNo());
                    statement.setString(8, row.status());
                    statement.setString(9, row.remark());
                    statement.setLong(10, safeActorId);
                    statement.setString(11, actorUuid); // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli -- SQL is static and this value is bound through PreparedStatement.
                    statement.setLong(12, safeActorId);
                    statement.setString(13, actorUuid); // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli -- SQL is static and this value is bound through PreparedStatement.
                }
        );
    }

    @Override
    public void recordInstallation(Installation installation) {
        database.update(
                """
                        insert into sys_dictionary_dataset_installation
                            (dataset_code, dataset_version, file_sha256, row_count, status, installed_at)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                installation.datasetCode(), installation.version(), installation.sha256(), installation.rowCount(),
                installation.status(), installation.installedAt()
        );
    }
}
