package com.legendary.invention.saas.modules.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class MessageDTO {

    private MessageDTO() {
    }

    public static class AnnouncementCreateRequest {
        @NotBlank
        @Size(max = 128, message = "title长度不能超过128个字符")
        private String title;
        @NotBlank
        @Size(max = 2000, message = "content长度不能超过2000个字符")
        private String content;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title == null ? null : title.trim();
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content == null ? null : content.trim();
        }
    }

    public static class MessageCreateRequest {
        @NotBlank
        @Size(max = 128, message = "title长度不能超过128个字符")
        private String title;
        @NotBlank
        @Size(max = 2000, message = "content长度不能超过2000个字符")
        private String content;
        @NotBlank
        @Pattern(regexp = "^(TENANT|USER)$", message = "targetScope只能是TENANT或USER")
        private String targetScope;
        @Positive(message = "targetUserId必须大于0")
        private Long targetUserId;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title == null ? null : title.trim();
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content == null ? null : content.trim();
        }

        public String getTargetScope() {
            return targetScope;
        }

        public void setTargetScope(String targetScope) {
            this.targetScope = targetScope == null ? null : targetScope.trim().toUpperCase();
        }

        public Long getTargetUserId() {
            return targetUserId;
        }

        public void setTargetUserId(Long targetUserId) {
            this.targetUserId = targetUserId;
        }
    }
}
