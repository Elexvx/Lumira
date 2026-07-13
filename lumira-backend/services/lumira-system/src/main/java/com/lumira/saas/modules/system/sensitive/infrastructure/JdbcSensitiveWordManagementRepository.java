package com.lumira.saas.modules.system.sensitive.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.sensitive.repository.SensitiveWordManagementRepository;
import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcSensitiveWordManagementRepository implements SensitiveWordManagementRepository {
    private static final String SELECT = """
            select id, word, normalized_word as normalizedWord,
                   category, severity, action, enabled, created_by as createdBy, created_at as createdAt,
                   updated_by as updatedBy, updated_at as updatedAt
            """;
    private static final int LOOKUP_BATCH_SIZE = 500;
    private final MyBatisQueryOperations database;

    public JdbcSensitiveWordManagementRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public List<Entry> findEnabledEntries() {
        return database.query("""
                select w.id, w.word, w.normalized_word as normalizedWord, w.category, w.severity, w.action,
                       coalesce(si.sort_no, 0) as priority
                from sys_sensitive_word w
                left join sys_dict_type st on st.dict_code = 'sys_sensitive_word_severity' and st.status = 'ENABLED' and st.deleted = 0
                left join sys_dict_item si on si.dict_type_id = st.id and si.item_value = w.severity and si.status = 'ENABLED' and si.deleted = 0
                where w.enabled = 1 and w.deleted = 0
                """, (row, index) -> new Entry(row.getObject("id", Long.class), row.getString("word"),
                row.getString("normalizedWord"), row.getString("category"), row.getString("severity"), row.getString("action"),
                row.getInt("priority")));
    }

    @Override
    public List<String> findEnabledDictValues(String dictCode) {
        return database.queryForList("""
                select i.item_value as itemValue
                from sys_dict_type t
                join sys_dict_item i on i.dict_type_id = t.id and i.deleted = 0 and i.status = 'ENABLED'
                where t.dict_code = ? and t.deleted = 0 and t.status = 'ENABLED'
                order by i.sort_no asc, i.id asc
                """, dictCode).stream().map(row -> row.get("itemValue"))
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(StringUtils::hasText).map(String::trim).toList();
    }

    @Override
    public PageData search(String keyword, Boolean enabled, long offset, long limit) {
        StringBuilder where = new StringBuilder(" from sys_sensitive_word where deleted = 0");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" and (word like ? or category like ? or severity like ? or action like ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        if (enabled != null) { where.append(" and enabled = ?"); args.add(enabled ? 1 : 0); }
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(limit); pageArgs.add(offset);
        List<SensitiveWordVO.WordRecord> records = database.query(
                SELECT + where + " order by updated_at desc, id desc limit ? offset ?",
                new BeanPropertyRowMapper<>(SensitiveWordVO.WordRecord.class), pageArgs.toArray());
        long total = offset == 0 && records.size() < limit ? records.size()
                : nullToZero(database.queryForObject("select count(1)" + where, Long.class, args.toArray()));
        return new PageData(records, total);
    }

    @Override
    public Optional<SensitiveWordVO.WordRecord> findById(Long id) {
        return Optional.ofNullable(database.queryForObject(SELECT + " from sys_sensitive_word where id = ? and deleted = 0",
                new BeanPropertyRowMapper<>(SensitiveWordVO.WordRecord.class), id));
    }

    @Override
    public Long create(WordWrite w, Long userId, String uuid) {
        int inserted = database.update("""
                insert into sys_sensitive_word (
                    word, normalized_word, category, severity, action, enabled,
                    created_by, created_by_uuid, created_at, updated_by, updated_by_uuid, updated_at, deleted
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?, now(), 0)
                """, w.word(), w.normalizedWord(), w.category(), w.severity(), w.action(), w.enabled() ? 1 : 0,
                userId, uuid, userId, uuid);
        return inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
    }

    @Override
    public int update(Long id, String expected, WordWrite w, Long userId, String uuid) {
        return database.update("""
                update sys_sensitive_word
                   set word = ?, normalized_word = ?, category = ?, severity = ?, action = ?, enabled = ?,
                       updated_by = ?, updated_by_uuid = ?, updated_at = now()
                 where id = ? and normalized_word = ? and deleted = 0
                """, w.word(), w.normalizedWord(), w.category(), w.severity(), w.action(), w.enabled() ? 1 : 0,
                userId, uuid, id, expected);
    }

    @Override public int enable(Long id, String expected, Long userId, String uuid) {
        return database.update("update sys_sensitive_word set enabled = 1, updated_by = ?, updated_by_uuid = ?, updated_at = now() where id = ? and normalized_word = ? and deleted = 0",
                userId, uuid, id, expected);
    }

    @Override public int delete(Long id, String expected, Long userId, String uuid) {
        return database.update("update sys_sensitive_word set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = now() where id = ? and normalized_word = ? and deleted = 0",
                userId, uuid, id, expected);
    }

    @Override public boolean existsByNormalizedWord(String normalizedWord, Long excludeId) {
        String sql = "select 1 from sys_sensitive_word where normalized_word = ? and deleted = 0";
        return excludeId == null ? database.exists(sql + " limit 1", normalizedWord)
                : database.exists(sql + " and id <> ? limit 1", normalizedWord, excludeId);
    }

    @Override
    public Set<String> findExistingNormalizedWords(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) return result;
        for (int start = 0; start < values.size(); start += LOOKUP_BATCH_SIZE) {
            List<String> batch = values.subList(start, Math.min(values.size(), start + LOOKUP_BATCH_SIZE));
            if (batch.isEmpty()) continue;
            result.addAll(database.queryForList("select normalized_word from sys_sensitive_word where deleted = 0 and normalized_word in ("
                    + placeholders(batch.size()) + ")", String.class, batch.toArray()));
        }
        return result;
    }

    @Override
    public int insertImported(List<ImportedWord> words, String category, String severity, String action, Long userId, String uuid) {
        if (words == null || words.isEmpty()) return 0;
        StringBuilder sql = new StringBuilder("insert into sys_sensitive_word (word, normalized_word, category, severity, action, enabled, created_by, created_by_uuid, created_at, updated_by, updated_by_uuid, updated_at, deleted) values ");
        List<Object> args = new ArrayList<>(words.size() * 9);
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("(?, ?, ?, ?, ?, 1, ?, ?, now(), ?, ?, now(), 0)");
            ImportedWord word = words.get(i);
            args.add(word.word()); args.add(word.normalizedWord()); args.add(category); args.add(severity); args.add(action);
            args.add(userId); args.add(uuid); args.add(userId); args.add(uuid);
        }
        return database.update(sql.toString(), args.toArray());
    }

    private static long nullToZero(Long value) { return value == null ? 0L : value; }
    private static String placeholders(int count) { return "?,".repeat(count).replaceFirst(",$", ""); }
}
