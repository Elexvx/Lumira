package com.lumira.saas.modules.system.user.app;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.system.export.ExcelExportService;
import com.lumira.saas.modules.system.export.ExportColumn;
import com.lumira.saas.modules.system.export.ExportDTO;
import com.lumira.saas.modules.system.export.ExportFieldVO;
import com.lumira.saas.modules.system.export.ExportTaskEntity;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.export.ExportVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class UserExportAppService {
    private static final String MODULE_KEY = "system:user";
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final long SYNC_THRESHOLD = 5000L;
    private static final long EXPORT_PAGE_SIZE = 100L;
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SystemUserManagementAppService systemUserManagementAppService;
    private final ExcelExportService excelExportService;
    private final ExportTaskService exportTaskService;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public UserExportAppService(
            SystemUserManagementAppService systemUserManagementAppService,
            ExcelExportService excelExportService,
            ExportTaskService exportTaskService
    ) {
        this.systemUserManagementAppService = systemUserManagementAppService;
        this.excelExportService = excelExportService;
        this.exportTaskService = exportTaskService;
    }

    public List<ExportFieldVO> listUserExportFields() {
        List<ExportColumn<SystemVO.UserVO>> columns = userColumns();
        List<ExportFieldVO> fields = new ArrayList<>(columns.size());
        for (int index = 0; index < columns.size(); index += 1) {
            ExportColumn<SystemVO.UserVO> column = columns.get(index);
            fields.add(new ExportFieldVO(column.key(), column.label(), column.defaultSelected(), index + 1));
        }
        return fields;
    }

    public ExportVO.ExportStartVO exportUsers(CurrentUser currentUser, ExportDTO.UserExportRequest request) {
        List<ExportColumn<SystemVO.UserVO>> selectedColumns = selectedColumns(request.getFields());
        long total = countUsers(currentUser, request);
        String fileName = buildFileName();
        if (total <= SYNC_THRESHOLD) {
            byte[] content = excelExportService.export("用户管理", selectedColumns, loadAllUsers(currentUser, request));
            ExportVO.ExportStartVO response = new ExportVO.ExportStartVO();
            response.setMode("SYNC");
            response.setFileName(fileName);
            response.setContentType(XLSX_CONTENT_TYPE);
            response.setContentBase64(Base64.getEncoder().encodeToString(content));
            response.setTotalCount(total);
            return response;
        }

        ExportTaskEntity task = exportTaskService.createTask(currentUser, MODULE_KEY, request, selectedColumns.stream().map(ExportColumn::key).toList(), total);
        executorService.submit(() -> runAsyncExport(currentUser, request, selectedColumns, task.getId(), fileName));
        ExportVO.ExportStartVO response = new ExportVO.ExportStartVO();
        response.setMode("ASYNC");
        response.setTaskId(task.getId());
        response.setFileName(fileName);
        response.setTotalCount(total);
        return response;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    private void runAsyncExport(
            CurrentUser currentUser,
            ExportDTO.UserExportRequest request,
            List<ExportColumn<SystemVO.UserVO>> selectedColumns,
            Long taskId,
            String fileName
    ) {
        try {
            exportTaskService.markRunning(taskId);
            byte[] content = excelExportService.export("用户管理", selectedColumns, loadAllUsers(currentUser, request));
            FileObjectDTO uploaded = exportTaskService.uploadExportFile(currentUser, content, fileName, "用户导出", "export,user", "用户管理异步导出");
            exportTaskService.markSuccess(taskId, uploaded, fileName);
        } catch (Exception exception) {
            exportTaskService.markFailed(taskId, exception);
        }
    }

    private long countUsers(CurrentUser currentUser, ExportDTO.UserExportRequest request) {
        PageResponse<SystemVO.UserVO> page = systemUserManagementAppService.listUsers(
                currentUser,
                request.getUserId(),
                request.getUsername(),
                request.getMobile(),
                request.getEmail(),
                request.getDeptId(),
                request.getStatus(),
                request.getSource(),
                request.getRegisteredStart(),
                request.getRegisteredEnd(),
                request.getLastLoginStart(),
                request.getLastLoginEnd(),
                null,
                null,
                1,
                1
        );
        return Math.max(page.getTotal(), 0L);
    }

    private List<SystemVO.UserVO> loadAllUsers(CurrentUser currentUser, ExportDTO.UserExportRequest request) {
        List<SystemVO.UserVO> users = new ArrayList<>();
        Long cursorId = null;
        String cursorCreatedAt = null;
        while (true) {
            PageResponse<SystemVO.UserVO> page = systemUserManagementAppService.listUsers(
                    currentUser,
                    request.getUserId(),
                    request.getUsername(),
                    request.getMobile(),
                    request.getEmail(),
                    request.getDeptId(),
                    request.getStatus(),
                    request.getSource(),
                    request.getRegisteredStart(),
                    request.getRegisteredEnd(),
                    request.getLastLoginStart(),
                    request.getLastLoginEnd(),
                    cursorId,
                    cursorCreatedAt,
                    1,
                    EXPORT_PAGE_SIZE
            );
            users.addAll(page.getRecords());
            if (!Boolean.TRUE.equals(page.getHasMore()) || CollectionUtils.isEmpty(page.getRecords())) {
                return users;
            }
            cursorId = page.getNextCursorId();
            cursorCreatedAt = page.getNextCursorCreatedAt();
            if (cursorId == null && cursorCreatedAt == null) {
                return users;
            }
        }
    }

    private List<ExportColumn<SystemVO.UserVO>> selectedColumns(List<String> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请至少选择一个导出字段");
        }
        Map<String, ExportColumn<SystemVO.UserVO>> columns = new LinkedHashMap<>();
        for (ExportColumn<SystemVO.UserVO> column : userColumns()) {
            columns.put(column.key(), column);
        }
        Set<String> dedupedFields = new LinkedHashSet<>(fields);
        List<ExportColumn<SystemVO.UserVO>> selected = new ArrayList<>();
        for (String field : dedupedFields) {
            ExportColumn<SystemVO.UserVO> column = columns.get(field);
            if (column == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "不支持的导出字段: " + field);
            }
            selected.add(column);
        }
        return selected;
    }

    private List<ExportColumn<SystemVO.UserVO>> userColumns() {
        return List.of(
                column("id", "用户ID", true, SystemVO.UserVO::getId),
                column("userNo", "用户编号", true, SystemVO.UserVO::getUserNo),
                column("username", "用户名", true, SystemVO.UserVO::getUsername),
                column("mobile", "手机号", true, SystemVO.UserVO::getMobile),
                column("email", "邮箱", true, SystemVO.UserVO::getEmail),
                column("nickname", "昵称", true, SystemVO.UserVO::getNickname),
                column("realName", "姓名", true, SystemVO.UserVO::getRealName),
                column("status", "状态", true, SystemVO.UserVO::getStatus),
                column("source", "来源", true, SystemVO.UserVO::getSource),
                column("registeredAt", "注册时间", true, SystemVO.UserVO::getRegisteredAt),
                column("lastLoginAt", "最近登录", true, SystemVO.UserVO::getLastLoginAt),
                column("roleNames", "角色", true, SystemVO.UserVO::getRoleNames),
                column("deptNames", "部门", true, SystemVO.UserVO::getDeptNames),
                column("idCardNumber", "身份证号码", false, SystemVO.UserVO::getIdCardNumber),
                column("avatarUrl", "头像地址", false, SystemVO.UserVO::getAvatarUrl),
                column("birthMonth", "出生年月", false, SystemVO.UserVO::getBirthMonth),
                column("gender", "性别", false, SystemVO.UserVO::getGender),
                column("region", "所在地区", false, SystemVO.UserVO::getRegion),
                column("availableTime", "可工作时间", false, SystemVO.UserVO::getAvailableTime),
                column("createdAt", "创建时间", false, SystemVO.UserVO::getCreatedAt),
                column("updatedAt", "更新时间", false, SystemVO.UserVO::getUpdatedAt)
        );
    }

    private ExportColumn<SystemVO.UserVO> column(String key, String label, boolean defaultSelected, java.util.function.Function<SystemVO.UserVO, Object> valueExtractor) {
        return new ExportColumn<>(key, label, defaultSelected, valueExtractor);
    }

    private String buildFileName() {
        return "用户管理导出-" + FILE_TIME_FORMATTER.format(LocalDateTime.now()) + ".xlsx";
    }
}
