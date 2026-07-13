package com.lumira.file.infrastructure;

import com.lumira.file.repository.FileBusinessPolicyRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFileBusinessPolicyRepository implements FileBusinessPolicyRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcFileBusinessPolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Item> findEnabledItems(String dictionaryCode) {
        return jdbcTemplate.query("""
                select i.item_label, i.item_value, i.remark, i.sort_no
                from sys_dict_type t
                join sys_dict_item i on i.dict_type_id = t.id
                where t.dict_code = ? and t.status = 'ENABLED' and t.deleted = 0
                  and i.status = 'ENABLED' and i.deleted = 0
                order by i.sort_no, i.id
                """, (rs, rowNum) -> new Item(
                rs.getString("item_label"), rs.getString("item_value"),
                rs.getString("remark"), rs.getInt("sort_no")), dictionaryCode);
    }
}
