package com.lumira.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("msg_delivery_log")
public class MessageDeliveryLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noticeId;
    private String channel;
    private String targetScope;
    private Long targetUserId;
    private String targetUserUuid;
    private String targetUserName;
    private String targetEmail;
    private String title;
    private String content;
    private String sendStatus;
    private String errorMessage;
    private LocalDateTime sentAt;
    private Long createdBy;
    private String createdByUuid;
    private Long updatedBy;
    private String updatedByUuid;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getTargetScope() { return targetScope; }
    public void setTargetScope(String targetScope) { this.targetScope = targetScope; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public String getTargetUserUuid() { return targetUserUuid; }
    public void setTargetUserUuid(String targetUserUuid) { this.targetUserUuid = targetUserUuid; }
    public String getTargetUserName() { return targetUserName; }
    public void setTargetUserName(String targetUserName) { this.targetUserName = targetUserName; }
    public String getTargetEmail() { return targetEmail; }
    public void setTargetEmail(String targetEmail) { this.targetEmail = targetEmail; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSendStatus() { return sendStatus; }
    public void setSendStatus(String sendStatus) { this.sendStatus = sendStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedByUuid() { return createdByUuid; }
    public void setCreatedByUuid(String createdByUuid) { this.createdByUuid = createdByUuid; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public String getUpdatedByUuid() { return updatedByUuid; }
    public void setUpdatedByUuid(String updatedByUuid) { this.updatedByUuid = updatedByUuid; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
