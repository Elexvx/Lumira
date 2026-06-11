package com.lumira.saas.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("iam_user_device")
public class IamUserDeviceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String os;
    private String browser;
    private String lastIp;
    private LocalDateTime lastActiveAt;
    private Integer trusted;
    private Integer deleted;
}
