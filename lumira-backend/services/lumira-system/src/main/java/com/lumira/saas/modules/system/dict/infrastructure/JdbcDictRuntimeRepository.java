package com.lumira.saas.modules.system.dict.infrastructure;

import com.lumira.saas.modules.system.dict.repository.DictRuntimeRepository;
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
                       i.sort_no as sortNo, i.status, i.remark
                from sys_dict_type t
                join sys_dict_item i on i.dict_type_id = t.id and i.deleted = 0
                where t.dict_code = ? and t.deleted = 0 and t.status = 'ENABLED' and i.status = 'ENABLED'
                order by t.is_system desc, t.id desc, i.sort_no asc, i.id asc
                """, new BeanPropertyRowMapper<>(SystemVO.DictItemVO.class), dictCode);
    }
}
