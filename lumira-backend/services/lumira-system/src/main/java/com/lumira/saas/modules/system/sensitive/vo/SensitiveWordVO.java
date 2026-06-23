package com.lumira.saas.modules.system.sensitive.vo;

import java.util.List;

public class SensitiveWordVO {

    public static class WordRecord {
        private Long id;
        private Long tenantId;
        private String word;
        private String normalizedWord;
        private String category;
        private String severity;
        private String action;
        private Boolean enabled;
        private Long createdBy;
        private String createdAt;
        private Long updatedBy;
        private String updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getWord() { return word; }
        public void setWord(String word) { this.word = word; }
        public String getNormalizedWord() { return normalizedWord; }
        public void setNormalizedWord(String normalizedWord) { this.normalizedWord = normalizedWord; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class MatchItem {
        private String fieldPath;
        private String word;
        private String maskedWord;
        private String action;

        public MatchItem() {
        }

        public MatchItem(String fieldPath, String word, String maskedWord, String action) {
            this.fieldPath = fieldPath;
            this.word = word;
            this.maskedWord = maskedWord;
            this.action = action;
        }

        public String getFieldPath() { return fieldPath; }
        public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
        public String getWord() { return word; }
        public void setWord(String word) { this.word = word; }
        public String getMaskedWord() { return maskedWord; }
        public void setMaskedWord(String maskedWord) { this.maskedWord = maskedWord; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }

    public static class CheckResult {
        private boolean hit;
        private boolean blocked;
        private List<MatchItem> matches = List.of();

        public CheckResult() {
        }

        public CheckResult(boolean hit, List<MatchItem> matches) {
            this(hit, hit, matches);
        }

        public CheckResult(boolean hit, boolean blocked, List<MatchItem> matches) {
            this.hit = hit;
            this.blocked = blocked;
            this.matches = matches;
        }

        public boolean isHit() { return hit; }
        public void setHit(boolean hit) { this.hit = hit; }
        public boolean isBlocked() { return blocked; }
        public void setBlocked(boolean blocked) { this.blocked = blocked; }
        public List<MatchItem> getMatches() { return matches; }
        public void setMatches(List<MatchItem> matches) { this.matches = matches; }
    }

    public static class ImportResult {
        private int total;
        private int imported;
        private int duplicated;
        private int invalid;

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getImported() { return imported; }
        public void setImported(int imported) { this.imported = imported; }
        public int getDuplicated() { return duplicated; }
        public void setDuplicated(int duplicated) { this.duplicated = duplicated; }
        public int getInvalid() { return invalid; }
        public void setInvalid(int invalid) { this.invalid = invalid; }
    }
}
