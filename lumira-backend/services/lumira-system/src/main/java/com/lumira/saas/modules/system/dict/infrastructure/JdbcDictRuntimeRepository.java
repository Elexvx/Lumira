package com.lumira.saas.modules.system.dict.infrastructure;

import com.lumira.saas.modules.system.dict.repository.DictRuntimeRepository;
import com.lumira.saas.common.vo.PageResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.stereotype.Repository;
import com.lumira.saas.modules.system.vo.SystemVO;

@Repository
public class JdbcDictRuntimeRepository implements DictRuntimeRepository {
    private final JdbcTemplate database;

    public JdbcDictRuntimeRepository(JdbcTemplate database) { this.database = database; }

    @Override
    public List<SystemVO.DictItemVO> findEnabledItems(String dictCode) {
        return database.query("""
                select i.id, i.dict_type_id as dictTypeId, i.item_label as itemLabel, i.item_value as itemValue,
                       i.sort_no as sortNo, i.status, i.remark, i.parent_item_value as parentItemValue,
                       i.level_no as levelNo, i.leaf
                from sys_dict_type t
                join sys_dict_item i on i.dict_type_id = t.id and i.deleted = 0
                where t.dict_code = ? and t.deleted = 0 and t.status = 'ENABLED' and i.status = 'ENABLED'
                order by t.is_system desc, t.id desc, i.sort_no asc, i.id asc
                """, new BeanPropertyRowMapper<>(SystemVO.DictItemVO.class), dictCode);
    }

    @Override
    public PageResponse<SystemVO.DictItemVO> searchEnabledItems(ItemSearch search) {
        StringBuilder where = new StringBuilder("""
                from sys_dict_type t
                join sys_dict_item i on i.dict_type_id = t.id and i.deleted = 0
                where t.dict_code = ? and t.deleted = 0 and t.status = 'ENABLED' and i.status = 'ENABLED'
                """);
        List<Object> params = new ArrayList<>();
        params.add(search.dictCode());
        if (search.rootOnly()) {
            where.append(" and (i.parent_item_value is null or i.parent_item_value = '')");
        } else if (search.parentItemValue() != null && !search.parentItemValue().isBlank()) {
            where.append(" and i.parent_item_value = ?");
            params.add(search.parentItemValue().trim());
        }
        if (search.keyword() != null && !search.keyword().isBlank()) {
            where.append(" and (i.item_label like ? or i.item_value like ?)");
            String keyword = "%" + search.keyword().trim() + "%";
            params.add(keyword);
            params.add(keyword);
        }
        if (search.values() != null && !search.values().isEmpty()) {
            where.append(" and i.item_value in (");
            where.append(String.join(",", java.util.Collections.nCopies(search.values().size(), "?")));
            where.append(")");
            params.addAll(search.values());
        }
        long safePageNo = Math.max(1L, search.pageNo());
        long safePageSize = Math.max(1L, Math.min(search.pageSize(), 100L));
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safePageSize);
        pageParams.add((safePageNo - 1L) * safePageSize);
        List<SystemVO.DictItemVO> records = database.query("""
                select i.id, i.dict_type_id as dictTypeId, i.item_label as itemLabel, i.item_value as itemValue,
                       i.sort_no as sortNo, i.status, i.remark, i.parent_item_value as parentItemValue,
                       i.level_no as levelNo, i.leaf
                """ + where + " order by i.sort_no asc, i.id asc limit ? offset ?",
                new BeanPropertyRowMapper<>(SystemVO.DictItemVO.class), pageParams.toArray());
        Long total = safePageNo == 1L && records.size() < safePageSize
                ? (long) records.size()
                : database.queryForObject("select count(1) " + where, Long.class, params.toArray());
        PageResponse<SystemVO.DictItemVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }
}
