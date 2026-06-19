package com.lumira.saas.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("iam_user_security_setting")
public class IamUserSecuritySettingEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer mfaEnabled;
    private Integer passwordLoginEnabled;
    private Integer smsLoginEnabled;
    private Integer emailLoginEnabled;
    private Integer passkeyEnabled;
    private Integer loginNotifyEnabled;
    private Integer deleted;
}
