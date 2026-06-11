package com.lumira.saas.modules.system.sensitive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SensitiveWordDTO {

    public static class UpsertRequest {
        @NotBlank(message = "敏感词不能为空")
        @Size(max = 128, message = "敏感词长度不能超过 128 个字符")
        private String word;

        @Size(max = 64, message = "分类长度不能超过 64 个字符")
        private String category;

        @Size(max = 32, message = "等级长度不能超过 32 个字符")
        private String severity;

        private Boolean enabled;

        public String getWord() {
            return word;
        }

        public void setWord(String word) {
            this.word = word;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class StatusRequest {
        private Boolean enabled;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class CheckRequest {
        private String text;
        private String fieldPath;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getFieldPath() {
            return fieldPath;
        }

        public void setFieldPath(String fieldPath) {
            this.fieldPath = fieldPath;
        }
    }
}
