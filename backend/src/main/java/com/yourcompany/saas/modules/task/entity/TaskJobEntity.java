package com.yourcompany.saas.modules.task.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_job")
public class TaskJobEntity {
    @TableId
    private Long id;
    private String tenantId;
    private String jobCode;
    private String jobName;
    private String cronExpr;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
