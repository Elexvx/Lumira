package com.lumira.saas.modules.system.sensitive.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.sensitive.repository.SensitiveWordDictionaryRepository;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class JdbcSensitiveWordDictionaryRepository implements SensitiveWordDictionaryRepository {
    private final MyBatisQueryOperations database;

    public JdbcSensitiveWordDictionaryRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<Entry> findEnabledEntries() {
        return database.query("""
                select w.id, w.word, w.normalized_word as normalizedWord, w.category, w.severity, w.action,
                       coalesce(si.sort_no, 0) as priority
                from sys_sensitive_word w
                left join sys_dict_type st on st.dict_code = 'sys_sensitive_word_severity' and st.status = 'ENABLED' and st.deleted = 0
                left join sys_dict_item si on si.dict_type_id = st.id and si.item_value = w.severity and si.status = 'ENABLED' and si.deleted = 0
                where w.enabled = 1 and w.deleted = 0
                """, (row, index) -> new Entry(
                row.getObject("id", Long.class),
                row.getString("word"),
                row.getString("normalizedWord"),
                row.getString("category"),
                row.getString("severity"),
                row.getString("action"), row.getInt("priority")
        ));
    }
}
