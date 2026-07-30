package com.lumira.saas.infrastructure.persistence.mybatis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Test
    void mapsJdbcTemporalColumnsToJavaTimeProperties() {
        BeanPropertyRowMapper<TemporalRecord> mapper = new BeanPropertyRowMapper<>(TemporalRecord.class);
        LocalDate issueDate = LocalDate.of(2026, 7, 30);
        LocalDateTime issuedAt = LocalDateTime.of(2026, 7, 30, 21, 30, 15);

        TemporalRecord record = mapper.mapRow(new SqlRow(Map.of(
                "issue_date", Date.valueOf(issueDate),
                "issued_at", Timestamp.valueOf(issuedAt)
        )), 0);

        Assertions.assertEquals(issueDate, record.getIssueDate());
        Assertions.assertEquals(issuedAt, record.getIssuedAt());
    }

    @Test
    void mapsJdbcTemporalColumnsToStringPropertiesUsedByCompetitionLists() {
        BeanPropertyRowMapper<StringTemporalRecord> mapper = new BeanPropertyRowMapper<>(StringTemporalRecord.class);

        StringTemporalRecord record = mapper.mapRow(new SqlRow(Map.of(
                "registration_start", Date.valueOf("2026-08-01"),
                "updated_at_text", Timestamp.valueOf("2026-08-02 03:04:05")
        )), 0);

        Assertions.assertEquals("2026-08-01", record.getRegistrationStart());
        Assertions.assertEquals("2026-08-02T03:04:05", record.getUpdatedAtText());
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

    static class TemporalRecord {
        private LocalDate issueDate;
        private LocalDateTime issuedAt;

        public LocalDate getIssueDate() {
            return issueDate;
        }

        public void setIssueDate(LocalDate issueDate) {
            this.issueDate = issueDate;
        }

        public LocalDateTime getIssuedAt() {
            return issuedAt;
        }

        public void setIssuedAt(LocalDateTime issuedAt) {
            this.issuedAt = issuedAt;
        }
    }

    static class StringTemporalRecord {
        private String registrationStart;
        private String updatedAtText;

        public String getRegistrationStart() {
            return registrationStart;
        }

        public void setRegistrationStart(String registrationStart) {
            this.registrationStart = registrationStart;
        }

        public String getUpdatedAtText() {
            return updatedAtText;
        }

        public void setUpdatedAtText(String updatedAtText) {
            this.updatedAtText = updatedAtText;
        }
    }
}
