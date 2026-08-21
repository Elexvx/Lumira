package com.lumira.saas.modules.system.dict.app;

import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.lumira.saas.modules.system.dict.infrastructure.JdbcDictRuntimeRepository;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictRuntimeServiceTest {

    @Test
    void listEnabledItemsShouldUseDictionaryCodePredicate() {
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate(List.of(item("MALE", "Male", 10)));
        DictRuntimeService service = new DictRuntimeService(new JdbcDictRuntimeRepository(jdbcTemplate));

        List<SystemVO.DictItemVO> items = service.listEnabledItems(" sys_user_gender ");

        assertEquals(1, items.size());
        assertEquals("sys_user_gender", jdbcTemplate.lastArgs.get(0));
        assertTrue(jdbcTemplate.lastSql.contains("where t.dict_code = ?"));
    }

    @Test
    void normalizeValueShouldTrimUppercaseAndRejectIllegalValue() {
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate(List.of(item("ENABLED", "Enabled", 10)));
        DictRuntimeService service = new DictRuntimeService(new JdbcDictRuntimeRepository(jdbcTemplate));

        assertEquals("ENABLED", service.normalizeValue("sys_common_status", " enabled ", null, false, "bad status"));
        assertThrows(BizException.class, () -> service.normalizeValue("sys_common_status", "disabled", null, false, "bad status"));
    }

    @Test
    void normalizeValueShouldAllowFallbackWhenDictionaryMissing() {
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate(List.of());
        DictRuntimeService service = new DictRuntimeService(new JdbcDictRuntimeRepository(jdbcTemplate));

        assertEquals("CUSTOM", service.normalizeValue("missing_dict", " custom ", null, true, "bad value"));
    }

    @Test
    void labelOfShouldReturnEnabledItemLabel() {
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate(List.of(item("OPEN", "Open", 10)));
        DictRuntimeService service = new DictRuntimeService(new JdbcDictRuntimeRepository(jdbcTemplate));

        assertEquals("Open", service.labelOf("team_join_mode", " open "));
    }

    private static SystemVO.DictItemVO item(String value, String label, int sortNo) {
        SystemVO.DictItemVO item = new SystemVO.DictItemVO();
        item.setItemValue(value);
        item.setItemLabel(label);
        item.setSortNo(sortNo);
        item.setStatus("ENABLED");
        return item;
    }

    private static class FakeJdbcTemplate extends JdbcTemplate {
        private final List<SystemVO.DictItemVO> rows;
        private String lastSql;
        private final List<Object> lastArgs = new ArrayList<>();

        private FakeJdbcTemplate(List<SystemVO.DictItemVO> rows) {
            this.rows = rows;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.lastSql = sql;
            this.lastArgs.clear();
            this.lastArgs.addAll(List.of(args));
            @SuppressWarnings("unchecked")
            List<T> result = (List<T>) rows;
            return result;
        }
    }
}
