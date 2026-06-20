package com.lumira.saas.modules.system.export;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExportTaskService {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String DOWNLOAD_CENTER_BUCKET = "download_center";

    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportTaskMapper exportTaskMapper;
    private final FileInternalApi fileInternalApi;
    private final ObjectMapper objectMapper;

    public ExportTaskService(ExportTaskMapper exportTaskMapper, FileInternalApi fileInternalApi, ObjectMapper objectMapper) {
        this.exportTaskMapper = exportTaskMapper;
        this.fileInternalApi = fileInternalApi;
        this.objectMapper = objectMapper;
    }

    public ExportTaskEntity createTask(CurrentUser currentUser, String moduleKey, Object request, List<String> selectedFields, long totalCount) {
        LocalDateTime now = LocalDateTime.now();
        ExportTaskEntity entity = new ExportTaskEntity();
        entity.setTenantId(currentTenantId(currentUser));
        entity.setModuleKey(moduleKey);
        entity.setStatus(STATUS_PENDING);
        entity.setRequestPayload(writeJson(request));
        entity.setSelectedFields(writeJson(selectedFields));
        entity.setTotalCount(totalCount);
        entity.setCreatedBy(currentUser.getUserId());
        entity.setCreatedAt(now);
        entity.setDeleted(0);
        exportTaskMapper.insert(entity);
        return entity;
    }

    public void markRunning(Long taskId) {
        ExportTaskEntity update = new ExportTaskEntity();
        update.setStatus(STATUS_RUNNING);
        update.setStartedAt(LocalDateTime.now());
        exportTaskMapper.update(update, new LambdaUpdateWrapper<ExportTaskEntity>()
                .eq(ExportTaskEntity::getId, taskId)
                .eq(ExportTaskEntity::getDeleted, 0));
    }

    public void markSuccess(Long taskId, FileObjectDTO file, String fileName) {
        ExportTaskEntity update = new ExportTaskEntity();
        update.setStatus(STATUS_SUCCESS);
        update.setFileId(file == null ? null : file.id());
        update.setFileName(fileName);
        update.setFinishedAt(LocalDateTime.now());
        exportTaskMapper.update(update, new LambdaUpdateWrapper<ExportTaskEntity>()
                .eq(ExportTaskEntity::getId, taskId)
                .eq(ExportTaskEntity::getDeleted, 0));
    }

    public void markFailed(Long taskId, Exception exception) {
        ExportTaskEntity update = new ExportTaskEntity();
        update.setStatus(STATUS_FAILED);
        update.setErrorMessage(resolveErrorMessage(exception));
        update.setFinishedAt(LocalDateTime.now());
        exportTaskMapper.update(update, new LambdaUpdateWrapper<ExportTaskEntity>()
                .eq(ExportTaskEntity::getId, taskId)
                .eq(ExportTaskEntity::getDeleted, 0));
    }

    public FileObjectDTO uploadExportFile(CurrentUser currentUser, byte[] content, String fileName, String category, String tags, String remark) {
        ByteArrayMultipartFile file = new ByteArrayMultipartFile(content, "file", fileName, XLSX_CONTENT_TYPE);
        return fileInternalApi.uploadDocumentForUser(
                file,
                category,
                tags,
                remark,
                DOWNLOAD_CENTER_BUCKET,
                currentTenantId(currentUser),
                currentUser.getUserId(),
                currentUser.getUsername()
        );
    }

    public ExportVO.ExportTaskVO getTask(CurrentUser currentUser, Long taskId) {
        ExportTaskEntity entity = exportTaskMapper.selectOne(new LambdaQueryWrapper<ExportTaskEntity>()
                .eq(ExportTaskEntity::getId, taskId)
                .eq(ExportTaskEntity::getTenantId, currentTenantId(currentUser))
                .eq(ExportTaskEntity::getCreatedBy, currentUser.getUserId())
                .eq(ExportTaskEntity::getDeleted, 0));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导出任务不存在");
        }
        ExportVO.ExportTaskVO vo = new ExportVO.ExportTaskVO();
        vo.setId(entity.getId());
        vo.setModuleKey(entity.getModuleKey());
        vo.setStatus(entity.getStatus());
        vo.setTotalCount(entity.getTotalCount());
        vo.setFileId(entity.getFileId());
        vo.setFileName(entity.getFileName());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setStartedAt(entity.getStartedAt());
        vo.setFinishedAt(entity.getFinishedAt());
        if (entity.getFileId() != null) {
            vo.setDownloadUrl("/api/v1/files/" + entity.getFileId() + "/download?scope=download-center");
        }
        return vo;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "导出任务参数序列化失败");
        }
    }

    private String resolveErrorMessage(Exception exception) {
        if (exception instanceof BizException bizException && StringUtils.hasText(bizException.getMessage())) {
            return bizException.getMessage();
        }
        return exception == null || !StringUtils.hasText(exception.getMessage()) ? "导出任务执行失败" : exception.getMessage();
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "租户上下文缺失");
        }
        return currentUser.getCurrentTenantId();
    }
}
