package com.lumira.saas.modules.system.dict.infrastructure;

import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.dict.repository.SystemDictionaryManagementRepository;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** JDBC/MyBatis dictionary-management adapter. */
@Repository
public class JdbcSystemDictionaryManagementRepository implements SystemDictionaryManagementRepository {
    private static final long MAX_PAGE_SIZE = 100L;
    private final MyBatisQueryOperations database;

    public JdbcSystemDictionaryManagementRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public PageResponse<SystemVO.DictTypeVO> findTypes(TypeSearch search) {
        String where = " from sys_dict_type t where t.deleted = 0";
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(search.dictCode())) {
            where += " and t.dict_code like ?";
            params.add(like(search.dictCode()));
        }
        if (StringUtils.hasText(search.dictName())) {
            where += " and t.dict_name like ?";
            params.add(like(search.dictName()));
        }
        if (StringUtils.hasText(search.status())) {
            where += " and t.status = ?";
            params.add(search.status());
        }
        return page(
                "select t.id, t.dict_code as dictCode, t.dict_name as dictName, t.status, t.is_system as isSystem, t.remark, t.structure_type as structureType" + where
                        + " order by t.is_system desc, t.id desc",
                "select count(1)" + where,
                SystemVO.DictTypeVO.class,
                search.pageNo(), search.pageSize(), params
        );
    }

    @Override
    public SystemVO.DictTypeVO findActiveType(Long typeId) {
        return queryOne(
                """
                        select id, dict_code as dictCode, dict_name as dictName, status, is_system as isSystem, remark,
                               structure_type as structureType
                        from sys_dict_type
                        where id = ? and deleted = 0
                        """,
                SystemVO.DictTypeVO.class, typeId
        );
    }

    @Override
    public DictionaryWriteResult saveType(TypeWrite command) {
        if (command.existing() == null) {
            int inserted = database.update(
                    """
                            insert into sys_dict_type (dict_code, dict_name, status, is_system, remark, created_by, created_by_uuid, updated_by, updated_by_uuid, structure_type, deleted)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    command.dictCode(), command.dictName(), command.status(), command.isSystem(), command.remark(), command.actor().userId(),
                    command.actor().userUuid(), command.actor().userId(), command.actor().userUuid(), command.structureType()
            );
            Long id = inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
            return new DictionaryWriteResult(inserted, id);
        }
        int updated = database.update(
                """
                        update sys_dict_type
                        set dict_code = ?, dict_name = ?, status = ?, is_system = ?, remark = ?, structure_type = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and dict_code = ? and is_system = ? and deleted = 0
                        """,
                command.dictCode(), command.dictName(), command.status(), command.isSystem(), command.remark(), command.structureType(), command.actor().userId(),
                command.actor().userUuid(), command.updatedAt(), command.existing().id(), command.existing().dictCode(), command.existing().isSystem()
        );
        return new DictionaryWriteResult(updated, command.existing().id());
    }

    @Override
    public int softDeleteType(TypeVersion type, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update sys_dict_type
                        set deleted = 1,
                            dict_code = concat(left(dict_code, greatest(0, 64 - char_length(concat('__deleted_', id)))), '__deleted_', id),
                            updated_by = ?,
                            updated_by_uuid = ?,
                            updated_at = ?
                        where id = ? and dict_code = ? and is_system = ? and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt, type.id(), type.dictCode(), type.isSystem()
        );
    }

    @Override
    public void retireItemsForType(Long typeId, Actor actor, LocalDateTime updatedAt) {
        database.update(
                """
                        update sys_dict_item
                        set deleted = 1,
                            item_value = concat(left(item_value, greatest(0, 64 - char_length(concat('__deleted_', id)))), '__deleted_', id),
                            updated_by = ?,
                            updated_by_uuid = ?,
                            updated_at = ?
                        where dict_type_id = ? and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt, typeId
        );
    }

    @Override
    public List<SystemVO.DictItemVO> findActiveItems(Long typeId) {
        return database.query(
                """
                        select id, dict_type_id as dictTypeId, item_label as itemLabel, item_value as itemValue,
                               sort_no as sortNo, status, remark, parent_item_value as parentItemValue,
                               level_no as levelNo, leaf
                        from sys_dict_item
                        where dict_type_id = ? and deleted = 0
                        order by sort_no asc, id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.DictItemVO.class), typeId
        );
    }

    @Override
    public PageResponse<SystemVO.DictItemVO> findItems(ItemSearch search) {
        String where = " from sys_dict_item i where i.dict_type_id = ? and i.deleted = 0";
        List<Object> params = new ArrayList<>();
        params.add(search.typeId());
        if (StringUtils.hasText(search.keyword())) {
            where += " and (i.item_label like ? or i.item_value like ?)";
            String keyword = like(search.keyword());
            params.add(keyword);
            params.add(keyword);
        }
        if (StringUtils.hasText(search.parentItemValue())) {
            if ("__ROOT__".equals(search.parentItemValue())) {
                where += " and (i.parent_item_value is null or i.parent_item_value = '')";
            } else {
                where += " and i.parent_item_value = ?";
                params.add(search.parentItemValue().trim());
            }
        }
        return page(
                "select i.id, i.dict_type_id as dictTypeId, i.item_label as itemLabel, i.item_value as itemValue,"
                        + " i.sort_no as sortNo, i.status, i.remark, i.parent_item_value as parentItemValue,"
                        + " i.level_no as levelNo, i.leaf" + where + " order by i.sort_no asc, i.id asc",
                "select count(1)" + where,
                SystemVO.DictItemVO.class,
                search.pageNo(), search.pageSize(), params
        );
    }

    @Override
    public List<SystemVO.DictItemVO> findEnabledItemsByCode(String dictCode) {
        return database.query(
                """
                        select i.id, i.dict_type_id as dictTypeId, i.item_label as itemLabel, i.item_value as itemValue,
                               i.sort_no as sortNo, i.status, i.remark, i.parent_item_value as parentItemValue,
                               i.level_no as levelNo, i.leaf
                        from sys_dict_type t
                        join sys_dict_item i
                          on i.dict_type_id = t.id
                         and i.deleted = 0
                        where t.dict_code = ?
                          and t.deleted = 0
                          and t.status = 'ENABLED'
                          and i.status = 'ENABLED'
                        order by i.sort_no asc, i.id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.DictItemVO.class), dictCode
        );
    }

    @Override
    public List<SystemVO.DictTypeVO> findEnabledTypes() {
        return database.query(
                """
                        select id, dict_code as dictCode, dict_name as dictName, status, is_system as isSystem, remark,
                               structure_type as structureType
                        from sys_dict_type
                        where deleted = 0 and status = 'ENABLED'
                        order by is_system desc, dict_name asc, id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.DictTypeVO.class)
        );
    }

    @Override
    public SystemVO.DictItemVO findActiveItem(Long typeId, Long itemId) {
        return queryOne(
                """
                        select id, dict_type_id as dictTypeId, item_label as itemLabel, item_value as itemValue,
                               sort_no as sortNo, status, remark, parent_item_value as parentItemValue,
                               level_no as levelNo, leaf
                        from sys_dict_item
                        where id = ? and dict_type_id = ? and deleted = 0
                        """,
                SystemVO.DictItemVO.class, itemId, typeId
        );
    }

    @Override
    public DictionaryWriteResult saveItem(ItemWrite command) {
        if (command.existing() == null) {
            int inserted = database.update(
                    """
                            insert into sys_dict_item (dict_type_id, item_label, item_value, sort_no, status, remark, created_by, created_by_uuid, updated_by, updated_by_uuid, parent_item_value, level_no, leaf, deleted)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    command.typeId(), command.itemLabel(), command.itemValue(), command.sortNo(), command.status(), command.remark(),
                    command.actor().userId(), command.actor().userUuid(), command.actor().userId(), command.actor().userUuid(),
                    command.parentItemValue(), command.levelNo(), Boolean.TRUE.equals(command.leaf()) ? 1 : 0
            );
            Long id = inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
            return new DictionaryWriteResult(inserted, id);
        }
        int updated = database.update(
                """
                        update sys_dict_item
                        set item_label = ?, item_value = ?, sort_no = ?, status = ?, remark = ?,
                            parent_item_value = ?, level_no = ?, leaf = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and dict_type_id = ? and item_value = ? and status = ? and deleted = 0
                        """,
                command.itemLabel(), command.itemValue(), command.sortNo(), command.status(), command.remark(),
                command.parentItemValue(), command.levelNo(), Boolean.TRUE.equals(command.leaf()) ? 1 : 0, command.actor().userId(),
                command.actor().userUuid(), command.updatedAt(), command.existing().id(), command.existing().typeId(),
                command.existing().itemValue(), command.existing().status()
        );
        return new DictionaryWriteResult(updated, command.existing().id());
    }

    @Override
    public int softDeleteItem(ItemVersion item, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update sys_dict_item
                        set deleted = 1,
                            item_value = concat(left(item_value, greatest(0, 64 - char_length(concat('__deleted_', id)))), '__deleted_', id),
                            updated_by = ?,
                            updated_by_uuid = ?,
                            updated_at = ?
                        where id = ? and dict_type_id = ? and item_value = ? and status = ? and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt, item.id(), item.typeId(), item.itemValue(), item.status()
        );
    }

    private <T> PageResponse<T> page(String selectSql, String countSql, Class<T> type, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add((safePageNo - 1) * safePageSize);
        List<T> rows = database.query(selectSql + " limit ? offset ?", new BeanPropertyRowMapper<>(type), queryParams.toArray());
        Long count = safePageNo == 1 && rows.size() < safePageSize
                ? (long) rows.size()
                : database.queryForObject(countSql, Long.class, params.toArray());
        PageResponse<T> response = new PageResponse<>();
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
