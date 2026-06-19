package com.lumira.api.message;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class MessageArchiveQueryRequestDTO {

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
