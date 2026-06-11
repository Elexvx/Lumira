package com.lumira.saas.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("iam_user_event")
public class IamUserEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String eventType;
    private String eventSource;
    private Long operatorId;
    private String ip;
    private String userAgent;
    private String detailJson;
    private LocalDateTime createdAt;
}
