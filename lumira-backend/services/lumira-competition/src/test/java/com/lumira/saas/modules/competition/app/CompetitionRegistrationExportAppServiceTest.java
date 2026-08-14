package com.lumira.saas.modules.competition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.export.ExportTaskPort;
import com.lumira.api.file.FileContentDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedUserSnapshotResolver;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.export.CompetitionExcelExportService;
import com.lumira.saas.modules.competition.repository.RegistrationDatasetRepository;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class CompetitionRegistrationExportAppServiceTest {

    @Test
    void startExportCreatesDurableCompetitionScopedTask() {
        CompetitionRegistrationAppService registrationAppService = mock(CompetitionRegistrationAppService.class);
        ExportTaskPort exportTaskPort = mock(ExportTaskPort.class);
        PageResponse<CompetitionRegistrationVO.Registration> page = new PageResponse<>();
        page.setTotal(12);
        when(registrationAppService.listRegistrations(any(), eq(1L), eq(1L), eq(88L), eq("CONFIRMED"), eq("alpha"), eq(true)))
                .thenReturn(page);
        when(exportTaskPort.createTask(
                any(),
                eq(CompetitionRegistrationExportAppService.MODULE_KEY),
                any(),
                anyList(),
                eq(12L),
                eq(CompetitionRegistrationExportAppService.EXPORT_PERMISSION)
        )).thenReturn(new ExportTaskPort.ExportTask(9001L));
        CompetitionRegistrationExportAppService service = service(
                registrationAppService,
                mock(RegistrationDatasetRepository.class),
                exportTaskPort
        );
        CompetitionRegistrationDTO.RegistrationExportRequest request = new CompetitionRegistrationDTO.RegistrationExportRequest();
        request.setCompetitionId(88L);
        request.setStatus(" confirmed ");
        request.setKeyword(" alpha ");

        var result = service.startExport(trustedUser(), request);

        assertThat(result.getMode()).isEqualTo("ASYNC");
        assertThat(result.getTaskId()).isEqualTo(9001L);
        assertThat(result.getTotalCount()).isEqualTo(12L);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(exportTaskPort).createTask(
                any(),
                eq(CompetitionRegistrationExportAppService.MODULE_KEY),
                payload.capture(),
                anyList(),
                eq(12L),
                eq(CompetitionRegistrationExportAppService.EXPORT_PERMISSION)
        );
        var captured = (CompetitionRegistrationExportAppService.AsyncTaskPayload) payload.getValue();
        assertThat(captured.getRequest().getCompetitionId()).isEqualTo(88L);
        assertThat(captured.getRequest().getStatus()).isEqualTo("CONFIRMED");
        assertThat(captured.getRequest().getKeyword()).isEqualTo("alpha");
    }

    @Test
    void selectedExportRejectsRegistrationOutsideLogicalDataset() {
        CompetitionRegistrationAppService registrationAppService = mock(CompetitionRegistrationAppService.class);
        RegistrationDatasetRepository datasetRepository = mock(RegistrationDatasetRepository.class);
        ExportTaskPort exportTaskPort = mock(ExportTaskPort.class);
        CompetitionRegistrationVO.Registration registration = new CompetitionRegistrationVO.Registration();
        registration.setId(101L);
        registration.setCompetitionId(88L);
        when(registrationAppService.getRegistration(any(), eq(101L))).thenReturn(registration);
        when(datasetRepository.isLinked(88L, 101L)).thenReturn(false);
        CompetitionRegistrationExportAppService service = service(registrationAppService, datasetRepository, exportTaskPort);
        CompetitionRegistrationDTO.RegistrationExportRequest request = new CompetitionRegistrationDTO.RegistrationExportRequest();
        request.setCompetitionId(88L);
        request.setRegistrationIds(List.of(101L));

        BizException exception = assertThrows(BizException.class, () -> service.startExport(trustedUser(), request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        verify(exportTaskPort, never()).createTask(any(), any(), any(), anyList(), anyLong(), any());
    }

    @Test
    void dataExportUsesConfiguredCollectionFieldsAndHumanReadableValues() throws Exception {
        CompetitionRegistrationAppService registrationAppService = mock(CompetitionRegistrationAppService.class);
        RegistrationDatasetRepository datasetRepository = mock(RegistrationDatasetRepository.class);
        TrustedUserSnapshotResolver userSnapshotResolver = mock(TrustedUserSnapshotResolver.class);
        CompetitionRegistrationVO.Registration registration = new CompetitionRegistrationVO.Registration();
        registration.setId(101L);
        registration.setCompetitionId(88L);
        registration.setRegistrationNo("REG-20260813175803842-BX4");
        registration.setParticipantNo("202608122342108411-0001");
        registration.setStatus("CONFIRMED");
        registration.setMemberCount(1);
        registration.setTeamName("Codex 流程测试团队");
        registration.setProjectTitle("Codex 全流程点击测试项目");
        registration.setMaterialSubmissionCount(0);
        registration.setMaterialFileCount(0);
        registration.setCreatedAt(LocalDateTime.of(2026, 8, 13, 17, 58, 3));
        registration.setRegistrationSnapshotJson("{}");
        registration.setTeamSnapshotJson("{\"teamId\":0,\"teamName\":\"Codex 流程测试团队\"}");
        registration.setMemberSnapshotJson("[{\"memberName\":\"Codex测试成员\"}]");
        registration.setProjectSnapshotJson("""
                {
                  "title":"Codex 全流程点击测试项目",
                  "extraValues":{
                    "intellectualProperties":[{
                      "intellectualPropertyType":"软件著作权",
                      "intellectualPropertyName":"Codex 测试软件著作权",
                      "rightsHolder":"Codex 测试团队",
                      "distributionRegions":["中国大陆"]
                    }]
                  }
                }
                """);
        registration.setCollectionSchemaSnapshotJson("""
                [
                  {"scope":"TEAM_FIELD","itemKey":"teamName","title":"团队名称","fieldType":"TEXT","groupLabel":""},
                  {"scope":"TEAM_FIELD","itemKey":"avatarUrl","title":"团队头像","fieldType":"IMAGE","groupLabel":""},
                  {"scope":"TEAM_FIELD","itemKey":"description","title":"团队简介","fieldType":"TEXTAREA","groupLabel":""},
                  {"scope":"MEMBER_FIELD","itemKey":"memberName","title":"成员姓名","fieldType":"TEXT","groupLabel":""},
                  {"scope":"PROJECT_FIELD","itemKey":"title","title":"项目名称","fieldType":"TEXT","groupLabel":""},
                  {"scope":"PROJECT_FIELD","itemKey":"imageUrl","title":"项目头像","fieldType":"IMAGE","groupLabel":""},
                  {"scope":"PROJECT_FIELD","itemKey":"description","title":"项目简介","fieldType":"TEXTAREA","groupLabel":""},
                  {"scope":"PROJECT_FIELD","itemKey":"intellectualPropertyType","title":"知识产权类型","fieldType":"SELECT","groupLabel":"知识产权信息"},
                  {"scope":"PROJECT_FIELD","itemKey":"intellectualPropertyName","title":"知识产权名称","fieldType":"TEXT","groupLabel":"知识产权信息"},
                  {"scope":"PROJECT_FIELD","itemKey":"registrationNumber","title":"申请号/登记号","fieldType":"TEXT","groupLabel":"知识产权信息"},
                  {"scope":"PROJECT_FIELD","itemKey":"rightsHolder","title":"权利人","fieldType":"TEXT","groupLabel":"知识产权信息"},
                  {"scope":"PROJECT_FIELD","itemKey":"legalStatus","title":"法律状态","fieldType":"SELECT","groupLabel":"知识产权信息"},
                  {"scope":"PROJECT_FIELD","itemKey":"grantDate","title":"授权/登记日期","fieldType":"DATE","groupLabel":"知识产权信息"},
                  {"scope":"PROJECT_FIELD","itemKey":"distributionRegions","title":"知识产权分布区域","fieldType":"MULTI_SELECT","groupLabel":"知识产权信息"}
                ]
                """);
        when(registrationAppService.getRegistration(any(), eq(101L))).thenReturn(registration);
        when(datasetRepository.isLinked(88L, 101L)).thenReturn(true);
        CurrentUser user = trustedUser();
        user.setPermissions(Set.of(
                CompetitionRegistrationExportAppService.EXPORT_PERMISSION,
                CompetitionRegistrationExportAppService.SENSITIVE_EXPORT_PERMISSION
        ));
        when(userSnapshotResolver.resolve(any(), any(), any(), any(), any())).thenReturn(user);
        CompetitionRegistrationExportAppService service = new CompetitionRegistrationExportAppService(
                registrationAppService,
                datasetRepository,
                new CompetitionExcelExportService(),
                mock(ExportTaskPort.class),
                userSnapshotResolver,
                mock(FileInternalApi.class),
                new ObjectMapper(),
                provider(null),
                provider(mock(ExecutorService.class))
        );
        CompetitionRegistrationDTO.RegistrationExportRequest request = new CompetitionRegistrationDTO.RegistrationExportRequest();
        request.setCompetitionId(88L);
        request.setRegistrationIds(List.of(101L));

        byte[] exported = service.exportFromTrustedSnapshot(user, request, 9001L);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            var sheet = workbook.getSheet("报名与材料");
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new java.util.ArrayList<>();
            Map<String, Integer> columnByHeader = new LinkedHashMap<>();
            for (int index = 0; index < sheet.getRow(0).getLastCellNum(); index += 1) {
                String header = formatter.formatCellValue(sheet.getRow(0).getCell(index));
                headers.add(header);
                columnByHeader.put(header, index);
            }
            assertThat(headers).containsExactly(
                    "报名编号", "参赛编号", "报名状态", "报名时间", "团队人数", "成员序号",
                    "团队名称", "团队头像", "团队简介", "成员姓名", "项目名称", "项目头像", "项目简介",
                    "知识产权类型", "知识产权名称", "申请号/登记号", "权利人", "法律状态", "授权/登记日期",
                    "知识产权分布区域", "材料提交次数", "材料文件数"
            );
            assertThat(headers).doesNotContain(
                    "成员角色", "学号/工号", "院系/部门", "成员备注",
                    "报名完整快照(JSON)", "团队完整快照(JSON)", "项目完整快照(JSON)",
                    "成员完整快照(JSON)", "采集结构快照(JSON)", "阶段材料清单(JSON)"
            );
            var row = sheet.getRow(1);
            assertThat(formatter.formatCellValue(row.getCell(columnByHeader.get("报名状态")))).isEqualTo("已确认");
            assertThat(formatter.formatCellValue(row.getCell(columnByHeader.get("团队名称"))))
                    .isEqualTo("Codex 流程测试团队");
            assertThat(formatter.formatCellValue(row.getCell(columnByHeader.get("成员姓名"))))
                    .isEqualTo("Codex测试成员");
            assertThat(formatter.formatCellValue(row.getCell(columnByHeader.get("知识产权类型"))))
                    .isEqualTo("软件著作权");
            assertThat(formatter.formatCellValue(row.getCell(columnByHeader.get("知识产权名称"))))
                    .isEqualTo("Codex 测试软件著作权");
            assertThat(formatter.formatCellValue(row.getCell(columnByHeader.get("知识产权分布区域"))))
                    .isEqualTo("中国大陆");
            var teamAvatarCell = row.getCell(columnByHeader.get("团队头像"));
            var registrationNumberCell = row.getCell(columnByHeader.get("申请号/登记号"));
            assertThat(formatter.formatCellValue(teamAvatarCell)).isEmpty();
            assertThat(formatter.formatCellValue(registrationNumberCell)).isEmpty();
            assertThat(teamAvatarCell.getCellType()).isEqualTo(CellType.BLANK);
            assertThat(registrationNumberCell.getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    @Test
    void materialPackageUsesBusinessAuthorizedFilesAndWritesManifest() throws Exception {
        CompetitionRegistrationAppService registrationAppService = mock(CompetitionRegistrationAppService.class);
        RegistrationDatasetRepository datasetRepository = mock(RegistrationDatasetRepository.class);
        ExportTaskPort exportTaskPort = mock(ExportTaskPort.class);
        TrustedUserSnapshotResolver userSnapshotResolver = mock(TrustedUserSnapshotResolver.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
        CompetitionExcelExportService excelExportService = new CompetitionExcelExportService();
        CompetitionRegistrationVO.Registration registration = new CompetitionRegistrationVO.Registration();
        registration.setId(101L);
        registration.setCompetitionId(88L);
        registration.setRegistrationNo("REG/001");
        registration.setTeamName("../Alpha");
        when(registrationAppService.getRegistration(any(), eq(101L))).thenReturn(registration);
        when(datasetRepository.isLinked(88L, 101L)).thenReturn(true);

        CompetitionRegistrationVO.MaterialValue value = new CompetitionRegistrationVO.MaterialValue();
        value.setId(301L);
        value.setFieldKey("project/file");
        value.setFileId(501L);
        CompetitionRegistrationVO.MaterialSubmission submission = new CompetitionRegistrationVO.MaterialSubmission();
        submission.setId(201L);
        submission.setStageId(7L);
        submission.setValues(List.of(value));
        when(registrationAppService.listMaterials(any(), eq(101L))).thenReturn(List.of(submission));
        when(fileInternalApi.readFileContentForAuthorizedBusinessReference(
                eq(501L), eq(1001L), eq("user-uuid-1001"), eq("operator"),
                eq("competition.registration.material"), eq(101L), eq(null)
        )).thenReturn(new FileContentDTO(
                501L, "../proposal.pdf", "application/pdf", "pdf", "proposal".getBytes(StandardCharsets.UTF_8)
        ));
        CurrentUser user = trustedUser();
        user.setPermissions(Set.of(
                CompetitionRegistrationExportAppService.EXPORT_PERMISSION,
                CompetitionRegistrationExportAppService.MATERIAL_DOWNLOAD_PERMISSION
        ));
        when(userSnapshotResolver.resolve(any(), any(), any(), any(), any())).thenReturn(user);
        CompetitionRegistrationExportAppService service = new CompetitionRegistrationExportAppService(
                registrationAppService,
                datasetRepository,
                excelExportService,
                exportTaskPort,
                userSnapshotResolver,
                fileInternalApi,
                new ObjectMapper(),
                provider(null),
                provider(mock(ExecutorService.class))
        );
        CompetitionRegistrationDTO.RegistrationExportRequest request = new CompetitionRegistrationDTO.RegistrationExportRequest();
        request.setCompetitionId(88L);
        request.setRegistrationIds(List.of(101L));

        byte[] archive = service.exportMaterialPackageFromTrustedSnapshot(user, request, 9001L);

        LinkedHashMap<String, byte[]> entries = zipEntries(archive);
        assertThat(entries.keySet()).contains("报名记录.xlsx", "manifest.json", "001-REG_001-__Alpha/");
        assertThat(entries.keySet()).anyMatch(path -> path.contains("001-REG_001-__Alpha/stage-7/project_file-501-__proposal.pdf"));
        String manifest = new String(entries.get("manifest.json"), StandardCharsets.UTF_8);
        assertThat(manifest).contains("\"fileId\" : 501");
        assertThat(manifest).contains("\"registrationFolder\" : \"001-REG_001-__Alpha\"");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(entries.get("报名记录.xlsx")))) {
            assertThat(workbook.getSheet("报名记录").getPhysicalNumberOfRows()).isEqualTo(2);
        }
        verify(fileInternalApi).readFileContentForAuthorizedBusinessReference(
                501L, 1001L, "user-uuid-1001", "operator", "competition.registration.material", 101L, null
        );
    }

    @Test
    void materialPackageSeparatesSelectedRegistrationsIntoNumberedFolders() throws Exception {
        CompetitionRegistrationAppService registrationAppService = mock(CompetitionRegistrationAppService.class);
        RegistrationDatasetRepository datasetRepository = mock(RegistrationDatasetRepository.class);
        TrustedUserSnapshotResolver userSnapshotResolver = mock(TrustedUserSnapshotResolver.class);
        CompetitionExcelExportService excelExportService = new CompetitionExcelExportService();
        CompetitionRegistrationVO.Registration first = new CompetitionRegistrationVO.Registration();
        first.setId(101L);
        first.setCompetitionId(88L);
        first.setRegistrationNo("REG-001");
        first.setTeamName("Alpha");
        CompetitionRegistrationVO.Registration second = new CompetitionRegistrationVO.Registration();
        second.setId(102L);
        second.setCompetitionId(88L);
        second.setRegistrationNo("REG-002");
        second.setTeamName("Beta");
        when(registrationAppService.getRegistration(any(), eq(101L))).thenReturn(first);
        when(registrationAppService.getRegistration(any(), eq(102L))).thenReturn(second);
        when(datasetRepository.isLinked(88L, 101L)).thenReturn(true);
        when(datasetRepository.isLinked(88L, 102L)).thenReturn(true);
        when(registrationAppService.listMaterials(any(), anyLong())).thenReturn(List.of());
        CurrentUser user = trustedUser();
        user.setPermissions(Set.of(
                CompetitionRegistrationExportAppService.EXPORT_PERMISSION,
                CompetitionRegistrationExportAppService.MATERIAL_DOWNLOAD_PERMISSION
        ));
        when(userSnapshotResolver.resolve(any(), any(), any(), any(), any())).thenReturn(user);
        CompetitionRegistrationExportAppService service = new CompetitionRegistrationExportAppService(
                registrationAppService,
                datasetRepository,
                excelExportService,
                mock(ExportTaskPort.class),
                userSnapshotResolver,
                mock(FileInternalApi.class),
                new ObjectMapper(),
                provider(null),
                provider(mock(ExecutorService.class))
        );
        CompetitionRegistrationDTO.RegistrationExportRequest request = new CompetitionRegistrationDTO.RegistrationExportRequest();
        request.setCompetitionId(88L);
        request.setRegistrationIds(List.of(101L, 102L));

        byte[] archive = service.exportMaterialPackageFromTrustedSnapshot(user, request, 9001L);

        LinkedHashMap<String, byte[]> entries = zipEntries(archive);
        assertThat(entries.keySet()).contains(
                "报名记录.xlsx",
                "001-REG-001-Alpha/",
                "002-REG-002-Beta/",
                "manifest.json"
        );
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(entries.get("报名记录.xlsx")))) {
            assertThat(workbook.getSheet("报名记录").getPhysicalNumberOfRows()).isEqualTo(3);
        }
    }

    private CompetitionRegistrationExportAppService service(
            CompetitionRegistrationAppService registrationAppService,
            RegistrationDatasetRepository datasetRepository,
            ExportTaskPort exportTaskPort
    ) {
        return new CompetitionRegistrationExportAppService(
                registrationAppService,
                datasetRepository,
                mock(CompetitionExcelExportService.class),
                exportTaskPort,
                mock(TrustedUserSnapshotResolver.class),
                mock(FileInternalApi.class),
                new ObjectMapper(),
                provider(null),
                provider(mock(ExecutorService.class))
        );
    }

    private CurrentUser trustedUser() {
        CurrentUser user = new CurrentUser();
        user.setUserId(1001L);
        user.setUserUuid("user-uuid-1001");
        user.setUsername("operator");
        user.setSessionId("session-1");
        user.setSessionVersion(1);
        user.setPermissionsVersion("permissions-1");
        user.setAuthenticated(true);
        user.setPermissions(Set.of(CompetitionRegistrationExportAppService.EXPORT_PERMISSION));
        return user;
    }

    private <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public Iterator<T> iterator() {
                return value == null ? List.<T>of().iterator() : Stream.of(value).iterator();
            }
        };
    }

    private LinkedHashMap<String, byte[]> zipEntries(byte[] archive) throws Exception {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries.put(entry.getName(), input.readAllBytes());
            }
        }
        return entries;
    }
}
