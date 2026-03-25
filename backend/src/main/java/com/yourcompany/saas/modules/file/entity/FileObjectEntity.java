package com.yourcompany.saas.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_object")
public class FileObjectEntity {
    @TableId
    private Long id;
    private String tenantId;
    private String storageType;
    private String objectKey;
    private String originalFilename;
    private Long fileSize;
    private String contentType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
