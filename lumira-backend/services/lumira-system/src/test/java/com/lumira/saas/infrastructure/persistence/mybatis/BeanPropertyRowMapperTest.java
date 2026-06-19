package com.lumira.saas.infrastructure.persistence.mybatis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class BeanPropertyRowMapperTest {

    @Test
    void mapsNumericColumnsToBooleanProperties() {
        BeanPropertyRowMapper<BooleanRecord> mapper = new BeanPropertyRowMapper<>(BooleanRecord.class);

        BooleanRecord enabled = mapper.mapRow(new SqlRow(Map.of("enabled", 1, "active_flag", 0)), 0);

        Assertions.assertEquals(Boolean.TRUE, enabled.getEnabled());
        Assertions.assertFalse(enabled.isActiveFlag());
    }

    @Test
    void mapsNumericColumnsToBooleanFields() {
        BeanPropertyRowMapper<BooleanFieldRecord> mapper = new BeanPropertyRowMapper<>(BooleanFieldRecord.class);

        BooleanFieldRecord record = mapper.mapRow(new SqlRow(Map.of("enabled", 0)), 0);

        Assertions.assertEquals(Boolean.FALSE, record.enabled);
    }

    static class BooleanRecord {
        private Boolean enabled;
        private boolean activeFlag;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isActiveFlag() {
            return activeFlag;
        }

        public void setActiveFlag(boolean activeFlag) {
            this.activeFlag = activeFlag;
        }
    }

    static class BooleanFieldRecord {
        private Boolean enabled;
    }
}
