package com.lumira.saas.modules.system.config.infrastructure;

import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.config.repository.SystemConfigurationManagementRepository;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** JDBC/MyBatis adapter for mutable PLATFORM configuration administration. */
@Repository
public class JdbcSystemConfigurationManagementRepository implements SystemConfigurationManagementRepository {
    private static final long MAX_PAGE_SIZE = 100L;
    private final MyBatisQueryOperations database;

    public JdbcSystemConfigurationManagementRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public PageResponse<SystemVO.ConfigVO> findConfigs(ConfigSearch search) {
        String where = " from sys_config c where c.deleted = 0";
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(search.configKey())) {
            where += " and c.config_key like ?";
            params.add(like(search.configKey()));
        }
        if (StringUtils.hasText(search.configName())) {
            where += " and c.config_name like ?";
            params.add(like(search.configName()));
        }
        return page(
                "select c.id, c.config_key as configKey, c.config_name as configName, c.config_value as configValue, c.is_system as isSystem, c.remark"
                        + where + " order by c.is_system desc, c.id desc",
                "select count(1)" + where,
                search.pageNo(), search.pageSize(), params
        );
    }

    @Override
    public SystemVO.ConfigVO findActiveConfig(Long configId) {
        return queryOne(
                """
                        select id, config_key as configKey, config_name as configName,
                               config_value as configValue, is_system as isSystem, remark
                        from sys_config
                        where id = ? and deleted = 0
                        """,
                SystemVO.ConfigVO.class, configId
        );
    }

    @Override
    public String findEditablePlatformValue(Long configId, String configKey) {
        return database.queryForObject(
                """
                        select config_value
                        from sys_config
                        where id = ?
                          and config_key = ?
                          and config_scope = 'PLATFORM'
                          and is_system = 0
                          and deleted = 0
                        """,
                String.class, configId, configKey
        );
    }

    @Override
    public int updateEditablePlatformConfig(ConfigWrite command) {
        return database.update(
                """
                        update sys_config
                        set config_key = ?, config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and config_key = ?
                          and config_scope = 'PLATFORM'
                          and is_system = 0
                          and deleted = 0
                        """,
                command.configKey(), command.configName(), command.configValue(), command.remark(), command.actor().userId(),
                command.actor().userUuid(), command.updatedAt(), command.existing().id(), command.existing().configKey()
        );
    }

    @Override
    public ConfigWriteResult createPlatformConfig(ConfigCreate command) {
        int inserted = database.update(
                """
                        insert into sys_config (
                            config_key, config_name, config_value, config_scope, is_system, remark,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, 'PLATFORM', 0, ?, ?, ?, ?, ?, 0)
                        """,
                command.configKey(), command.configName(), command.configValue(), command.remark(), command.actor().userId(),
                command.actor().userUuid(), command.actor().userId(), command.actor().userUuid()
        );
        Long id = inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
        return new ConfigWriteResult(inserted, id);
    }

    @Override
    public SystemVO.ConfigVO findLatestActiveConfigByKey(String configKey) {
        return database.queryForObject(
                """
                        select id, config_key as configKey, config_name as configName,
                               config_value as configValue, is_system as isSystem, remark
                        from sys_config
                        where config_key = ? and deleted = 0
                        order by id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(SystemVO.ConfigVO.class), configKey
        );
    }

    private PageResponse<SystemVO.ConfigVO> page(String selectSql, String countSql, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add((safePageNo - 1) * safePageSize);
        List<SystemVO.ConfigVO> rows = database.query(
                selectSql + " limit ? offset ?", new BeanPropertyRowMapper<>(SystemVO.ConfigVO.class), queryParams.toArray()
        );
        Long count = safePageNo == 1 && rows.size() < safePageSize
                ? (long) rows.size()
                : database.queryForObject(countSql, Long.class, params.toArray());
        PageResponse<SystemVO.ConfigVO> response = new PageResponse<>();
        response.setRecords(rows);
        response.setTotal(count == null ? 0L : count);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private <T> T queryOne(String sql, Class<T> type, Object... params) {
        try {
            return database.queryForObject(sql, new BeanPropertyRowMapper<>(type), params);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }
}
