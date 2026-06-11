package com.lumira.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

public final class MessageDTO {

    private MessageDTO() {
    }

    public static class MessageCreateRequest {
        @NotBlank
        @Size(max = 128, message = "title长度不能超过128个字符")
        private String title;
        @NotBlank
        @Size(max = 2000, message = "content长度不能超过2000个字符")
        private String content;
        @NotBlank
        @Pattern(regexp = "^(TENANT|USER|ROLE)$", message = "targetScope只能是TENANT、USER或ROLE")
        private String targetScope;
        private List<@Pattern(regexp = "^(INBOX|EMAIL|WECHAT_OFFICIAL)$", message = "channels只能包含INBOX、EMAIL或WECHAT_OFFICIAL") String> channels = List.of("INBOX");
        @Positive(message = "targetUserId必须大于0")
        private Long targetUserId;
        @Positive(message = "targetRoleId必须大于0")
        private Long targetRoleId;

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

        public List<String> getChannels() {
            return channels;
        }

        public void setChannels(List<String> channels) {
            this.channels = channels == null
                    ? List.of("INBOX")
                    : channels.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toUpperCase())
                    .distinct()
                    .toList();
        }

        public void setTargetUserId(Long targetUserId) {
            this.targetUserId = targetUserId;
        }

        public Long getTargetRoleId() {
            return targetRoleId;
        }

        public void setTargetRoleId(Long targetRoleId) {
            this.targetRoleId = targetRoleId;
        }
    }

    public static class MessageArchiveQueryRequest {
        @Positive
        private Long pageNo = 1L;
        @Positive
        private Long pageSize = 20L;
        @Size(max = 128, message = "keyword长度不能超过128个字符")
        private String keyword;
        @Pattern(regexp = "^(MESSAGE)$", message = "messageType只能是MESSAGE")
        private String messageType;
        @Pattern(regexp = "^(TENANT|USER|ROLE)$", message = "targetScope只能是TENANT、USER或ROLE")
        private String targetScope;
        @Pattern(regexp = "^(MANUAL)$", message = "sourceType只能是MANUAL")
        private String sourceType;
        @Pattern(regexp = "^(PUBLISHED|RETRACTED)$", message = "publishStatus只能是PUBLISHED或RETRACTED")
        private String publishStatus;
        @Pattern(regexp = "^(INBOX|EMAIL|WECHAT_OFFICIAL)$", message = "channel只能是INBOX、EMAIL或WECHAT_OFFICIAL")
        private String channel;
        @Pattern(regexp = "^(SUCCESS|FAILED|SKIPPED)$", message = "sendStatus只能是SUCCESS、FAILED或SKIPPED")
        private String sendStatus;
        @Pattern(regexp = "^(publishedAt|createdAt|title|sourceType|publishStatus|targetScope|messageType|readFlag)$", message = "sortField不合法")
        private String sortField;
        @Pattern(regexp = "^(ASC|DESC)$", message = "sortOrder只能是ASC或DESC")
        private String sortOrder;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime publishedAtStart;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime publishedAtEnd;

        public Long getPageNo() {
            return pageNo;
        }

        public void setPageNo(Long pageNo) {
            this.pageNo = pageNo;
        }

        public Long getPageSize() {
            return pageSize;
        }

        public void setPageSize(Long pageSize) {
            this.pageSize = pageSize;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword == null ? null : keyword.trim();
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType == null ? null : messageType.trim().toUpperCase();
        }

        public String getTargetScope() {
            return targetScope;
        }

        public void setTargetScope(String targetScope) {
            this.targetScope = targetScope == null ? null : targetScope.trim().toUpperCase();
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType == null ? null : sourceType.trim().toUpperCase();
        }

        public String getPublishStatus() {
            return publishStatus;
        }

        public void setPublishStatus(String publishStatus) {
            this.publishStatus = publishStatus == null ? null : publishStatus.trim().toUpperCase();
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel == null ? null : channel.trim().toUpperCase();
        }

        public String getSendStatus() {
            return sendStatus;
        }

        public void setSendStatus(String sendStatus) {
            this.sendStatus = sendStatus == null ? null : sendStatus.trim().toUpperCase();
        }

        public String getSortField() {
            return sortField;
        }

        public void setSortField(String sortField) {
            this.sortField = sortField == null ? null : sortField.trim();
        }

        public String getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(String sortOrder) {
            this.sortOrder = sortOrder == null ? null : sortOrder.trim().toUpperCase();
        }

        public LocalDateTime getPublishedAtStart() {
            return publishedAtStart;
        }

        public void setPublishedAtStart(LocalDateTime publishedAtStart) {
            this.publishedAtStart = publishedAtStart;
        }

        public LocalDateTime getPublishedAtEnd() {
            return publishedAtEnd;
        }

        public void setPublishedAtEnd(LocalDateTime publishedAtEnd) {
            this.publishedAtEnd = publishedAtEnd;
        }
    }
}
