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
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
    void materialPackageUsesBusinessAuthorizedFilesAndWritesManifest() throws Exception {
        CompetitionRegistrationAppService registrationAppService = mock(CompetitionRegistrationAppService.class);
        RegistrationDatasetRepository datasetRepository = mock(RegistrationDatasetRepository.class);
        ExportTaskPort exportTaskPort = mock(ExportTaskPort.class);
        TrustedUserSnapshotResolver userSnapshotResolver = mock(TrustedUserSnapshotResolver.class);
        FileInternalApi fileInternalApi = mock(FileInternalApi.class);
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
                mock(CompetitionExcelExportService.class),
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

        LinkedHashMap<String, String> entries = zipEntries(archive);
        assertThat(entries.keySet()).contains("manifest.json");
        assertThat(entries.keySet()).anyMatch(path -> path.contains("REG_001-__Alpha/stage-7/project_file-501-__proposal.pdf"));
        assertThat(entries.get("manifest.json")).contains("\"fileId\" : 501");
        verify(fileInternalApi).readFileContentForAuthorizedBusinessReference(
                501L, 1001L, "user-uuid-1001", "operator", "competition.registration.material", 101L, null
        );
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

    private LinkedHashMap<String, String> zipEntries(byte[] archive) throws Exception {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
