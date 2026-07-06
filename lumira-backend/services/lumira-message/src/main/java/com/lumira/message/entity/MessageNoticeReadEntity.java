package com.lumira.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("msg_notice_read")
public class MessageNoticeReadEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noticeId;
    private Long userId;
    private String userUuid;
    private LocalDateTime readAt;
    private Long createdBy;
    private String createdByUuid;
    private Long updatedBy;
    private String updatedByUuid;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
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
