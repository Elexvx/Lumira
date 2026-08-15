package com.lumira.file;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.CompetitionStorageSpaceRequest;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.StorageSpaceOptionDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.common.vo.PageResponse;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.file.app.FileManagementAppService;
import com.lumira.file.config.UploadProperties;
import com.lumira.file.entity.FileObjectEntity;
import com.lumira.file.entity.FileStorageSpaceEntity;
import com.lumira.file.mapper.FileObjectMapper;
import com.lumira.file.mapper.FileStorageSpaceMapper;
import com.lumira.file.infrastructure.JdbcFileProcessingArtifactRepository;
import com.lumira.file.infrastructure.MyBatisFileStorageSpaceRepository;
import com.lumira.file.infrastructure.MyBatisFileObjectRepository;
import com.lumira.file.processing.FileProcessingTaskRequestService;
import com.lumira.file.processing.FileSecurityScanProcessor;
import com.lumira.file.repository.FileProcessingArtifactRepository;
import com.lumira.file.repository.FileBusinessPolicyRepository;
import com.lumira.file.repository.FileStorageSpaceRepository;
import com.lumira.file.repository.FileObjectRepository;
import com.lumira.file.security.SafeUrlValidator;
import com.lumira.file.upload.DocumentUploadService;
import com.lumira.file.upload.FileStorageMetrics;
import com.lumira.file.upload.ImageUploadService;
import com.lumira.file.vo.FileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileManagementAppServiceTest {

    private FileProcessingArtifactRepository artifactRepository() {
        return new JdbcFileProcessingArtifactRepository(jdbcTemplate);
    }

    private FileStorageSpaceRepository storageSpaceRepository() {
        return new MyBatisFileStorageSpaceRepository(fileStorageSpaceMapper);
    }

    private FileObjectRepository fileObjectRepository() {
        return new MyBatisFileObjectRepository(fileObjectMapper);
    }

    @Mock
    private FileObjectMapper fileObjectMapper;

    @Mock
    private FileBusinessPolicyRepository businessPolicyRepository;

    @Mock
    private FileStorageSpaceMapper fileStorageSpaceMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private UploadProperties uploadProperties;

    @Mock
    private DocumentUploadService documentUploadService;

    @Mock
    private ImageUploadService imageUploadService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private FileProcessingTaskRequestService fileProcessingTaskRequestService;

    @Mock
    private FileSecurityScanProcessor fileSecurityScanProcessor;

    @Mock
    private FieldCryptoService fieldCryptoService;

    @Mock
    private FileStorageMetrics storageMetrics;

    @Mock
    private MultipartFile multipartFile;

    @Mock
    private SystemInternalApi systemInternalApi;

    @TempDir
    Path tempDir;

    private FileManagementAppService service;

    @BeforeEach
    void setUp() {
        service = new FileManagementAppService(
                fileObjectRepository(),
                businessPolicyRepository,
                storageSpaceRepository(),
                artifactRepository(),
                uploadProperties,
                documentUploadService,
                imageUploadService,
                domainEventPublisher,
                fileProcessingTaskRequestService,
                fieldCryptoService,
                storageMetrics,
                new SafeUrlValidator(),
                null,
                provider(systemInternalApi),
                scanProvider(fileSecurityScanProcessor)
        );
        org.mockito.Mockito.lenient().when(systemInternalApi.findUserIdentityById(11L)).thenReturn(userSnapshot(11L, "alice", "ENABLED"));
        org.mockito.Mockito.lenient().when(businessPolicyRepository.findEnabledItems("file_storage_provider")).thenReturn(List.of(
                new FileBusinessPolicyRepository.Item("Local storage", "LOCAL", null, 10),
                new FileBusinessPolicyRepository.Item("阿里云 OSS", "ALIYUN_OSS", null, 20),
                new FileBusinessPolicyRepository.Item("腾讯云 COS", "TENCENT_COS", null, 30)));
        org.mockito.Mockito.lenient().when(businessPolicyRepository.findEnabledItems("file_rename_strategy")).thenReturn(List.of(
                new FileBusinessPolicyRepository.Item("追加随机标识", "APPEND_RANDOM_ID", null, 10),
                new FileBusinessPolicyRepository.Item("随机字符串", "RANDOM_STRING", null, 20),
                new FileBusinessPolicyRepository.Item("保留原名", "KEEP_ORIGINAL", null, 30)));
        org.mockito.Mockito.lenient().when(businessPolicyRepository.findEnabledItems("file_storage_status")).thenReturn(List.of(
                new FileBusinessPolicyRepository.Item("启用", "ENABLED", null, 10),
                new FileBusinessPolicyRepository.Item("停用", "DISABLED", null, 20)));
        org.mockito.Mockito.lenient().when(businessPolicyRepository.findEnabledItems("file_runtime_default")).thenReturn(List.of(
                new FileBusinessPolicyRepository.Item("LOCAL", "STORAGE_PROVIDER", null, 10),
                new FileBusinessPolicyRepository.Item("local", "STORAGE_KEY", null, 15),
                new FileBusinessPolicyRepository.Item("storage/uploads/", "ROOT_PATH", null, 20),
                new FileBusinessPolicyRepository.Item("APPEND_RANDOM_ID", "RENAME_STRATEGY", null, 30),
                new FileBusinessPolicyRepository.Item("20", "MAX_FILE_SIZE_MB", null, 40),
                new FileBusinessPolicyRepository.Item("*", "ALLOWED_MIME_TYPES", null, 50),
                new FileBusinessPolicyRepository.Item("我的文件", "DOCUMENT_CATEGORY", null, 60),
                new FileBusinessPolicyRepository.Item("图片", "IMAGE_CATEGORY", null, 70),
                new FileBusinessPolicyRepository.Item("UNSUPPORTED", "UNSUPPORTED_PREVIEW_MODE", null, 80),
                new FileBusinessPolicyRepository.Item("ENABLED", "STORAGE_STATUS", null, 90)));
        org.mockito.Mockito.lenient().when(businessPolicyRepository.findEnabledItems("file_preview_extension")).thenReturn(List.of(
                new FileBusinessPolicyRepository.Item("IMAGE", "png", null, 10),
                new FileBusinessPolicyRepository.Item("PDF", "pdf", null, 20),
                new FileBusinessPolicyRepository.Item("TEXT", "txt", null, 30)));
        org.mockito.Mockito.lenient().when(businessPolicyRepository.findEnabledItems("file_preview_content_type")).thenReturn(List.of(
                new FileBusinessPolicyRepository.Item("IMAGE", "image/", "PREFIX", 10),
                new FileBusinessPolicyRepository.Item("PDF", "application/pdf", "EXACT", 20),
                new FileBusinessPolicyRepository.Item("TEXT", "text/", "PREFIX", 30)));
        org.mockito.Mockito.lenient().when(businessPolicyRepository.findEnabledItem(any(String.class), any(String.class)))
                .thenAnswer(invocation -> businessPolicyRepository.findEnabledItems(invocation.getArgument(0)).stream()
                        .filter(item -> ((String) invocation.getArgument(1)).equalsIgnoreCase(item.value()))
                        .findFirst());
        org.mockito.Mockito.lenient().when(systemInternalApi.permissionSnapshot(11L, "user-uuid-11")).thenReturn(permissionSnapshot(
                List.of("*"),
                List.of(),
                List.of(),
                List.of()
        ));
    }

    @Test
    void listFiles_shouldReportExactStableTotalBelowCap() {
        AtomicInteger listInvocation = new AtomicInteger();
        when(fileObjectMapper.selectList(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObjectEntity>>any())).thenAnswer(
                invocation -> {
                    if (listInvocation.getAndIncrement() == 0) {
                        return fileObjectEntities(101);
                    }
                    return List.<FileObjectEntity>of();
                }
        );

        CurrentUser currentUser = new CurrentUser(11L, "alice", null, "sid", 1, true, Set.of("*"));
        currentUser.setUserUuid("user-uuid-11");
        currentUser.setPermissionsVersion("permissions-1");
        PageResponse<?> response = service.listFiles(currentUser, null, null, null, null, null, null, 1, 100, null, null);

        assertThat(response).isInstanceOf(FileVO.FileObjectPageResponse.class);
        assertThat(response.getTotal()).isEqualTo(101L);
        assertThat(response.getPageNo()).isEqualTo(1L);
        assertThat(response.getPageSize()).isEqualTo(100L);
        assertThat(response.getRecords()).hasSize(0);
        assertThat(((FileVO.FileObjectPageResponse) response).getHasMore()).isFalse();
        assertThat(((FileVO.FileObjectPageResponse) response).getTotalCapped()).isFalse();
    }

    @Test
    void listFiles_shouldKeepCappedTotalStableAcrossPages() {
        AtomicInteger listInvocation = new AtomicInteger();
        when(fileObjectMapper.selectList(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObjectEntity>>any())).thenAnswer(
                invocation -> listInvocation.getAndIncrement() % 2 == 0
                        ? fileObjectEntities(1001)
                        : List.<FileObjectEntity>of()
        );

        CurrentUser currentUser = currentUser();
        PageResponse<?> firstPage = service.listFiles(currentUser, null, null, null, null, null, null, 1, 10, null, null);
        PageResponse<?> secondPage = service.listFiles(currentUser, null, null, null, null, null, null, 2, 10, null, null);

        assertThat(firstPage.getTotal()).isEqualTo(1000L);
        assertThat(secondPage.getTotal()).isEqualTo(1000L);
        assertThat(((FileVO.FileObjectPageResponse) firstPage).getTotalCapped()).isTrue();
        assertThat(((FileVO.FileObjectPageResponse) secondPage).getTotalCapped()).isTrue();
    }

    @Test
    void listFiles_shouldRejectUnauthenticatedUserBeforeMapperAccess() {
        assertThatThrownBy(() -> service.listFiles(unauthenticatedUser(), null, null, null, null, null, null, 1, 10, null, null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileObjectMapper);
    }

    @Test
    void listFiles_shouldRejectWhenTrustedResolverIsUnavailableBeforeMapperAccess() {
        FileManagementAppService serviceWithoutResolver = new FileManagementAppService(
                fileObjectRepository(),
                businessPolicyRepository,
                storageSpaceRepository(),
                artifactRepository(),
                uploadProperties,
                documentUploadService,
                imageUploadService,
                domainEventPublisher,
                fileProcessingTaskRequestService,
                fieldCryptoService,
                storageMetrics,
                new SafeUrlValidator(),
                null,
                null
        );

        assertThatThrownBy(() -> serviceWithoutResolver.listFiles(currentUser(), null, null, null, null, null, null, 1, 10, null, null))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Trusted acting user resolver is unavailable");

        verifyNoInteractions(fileObjectMapper);
    }

    @Test
    void listFiles_shouldRejectBlankUsernameBeforeMapperAccess() {
        assertThatThrownBy(() -> service.listFiles(blankUsernameUser(), null, null, null, null, null, null, 1, 10, null, null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileObjectMapper);
    }

    @Test
    void listFiles_shouldRejectMissingSessionVersionBeforeMapperAccess() {
        assertThatThrownBy(() -> service.listFiles(missingSessionVersionUser(), null, null, null, null, null, null, 1, 10, null, null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileObjectMapper);
    }

    @Test
    void listFiles_shouldRejectDisabledTrustedOperatorBeforeMapperAccess() {
        when(systemInternalApi.findUserIdentityById(11L)).thenReturn(userSnapshot(11L, "alice", "DISABLED"));

        assertThatThrownBy(() -> service.listFiles(currentUser(), null, null, null, null, null, null, 1, 10, null, null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileObjectMapper);
    }

    @Test
    void uploadDocument_shouldRejectInternalServicePrincipalBeforeStorageWrite() {
        CurrentUser internalService = new CurrentUser(0L, "internal-service", null, "internal", 0, false, Set.of());

        assertThatThrownBy(() -> service.uploadDocument(internalService, multipartFile, "docs", null, null, "local"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(documentUploadService, fileObjectMapper, fileProcessingTaskRequestService);
    }

    @Test
    void uploadFile_publicScopeShouldRequirePublishPermissionBeforeStorageWrite() {
        CurrentUser uploader = new CurrentUser(11L, "alice", null, "sid", 1, true, Set.of("system:file:upload"));
        uploader.setUserUuid("user-uuid-11");
        uploader.setPermissionsVersion("permissions-1");
        when(systemInternalApi.permissionSnapshot(11L, "user-uuid-11")).thenReturn(permissionSnapshot(
                List.of("system:file:upload"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThatThrownBy(() -> service.uploadFile(uploader, multipartFile, "docs", null, null, "local", "PUBLIC"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(documentUploadService, imageUploadService, fileObjectMapper, fileProcessingTaskRequestService);
    }

    @Test
    void uploadFile_shouldRejectWhenLivePermissionsLoseUploadPermissionBeforeStorageWrite() {
        CurrentUser uploader = currentUser("system:file:upload");
        when(systemInternalApi.permissionSnapshot(11L, "user-uuid-11")).thenReturn(permissionSnapshot(
                List.of("system:file:view"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThatThrownBy(() -> service.uploadFile(uploader, multipartFile, "docs", null, null, "local", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(documentUploadService, imageUploadService, fileObjectMapper, fileProcessingTaskRequestService);
    }

    @Test
    void uploadFile_publicScopeShouldRejectWhenLivePermissionsLosePublishPermissionBeforeRecordInsert() {
        CurrentUser uploader = currentUser("system:file:publish");
        when(systemInternalApi.permissionSnapshot(11L, "user-uuid-11")).thenReturn(
                permissionSnapshot(List.of("system:file:publish"), List.of(), List.of(), List.of()),
                permissionSnapshot(List.of("system:file:upload"), List.of(), List.of(), List.of())
        );
        FileStorageSpaceEntity localStorage = storageSpaceEntities(1).getFirst();
        localStorage.setStorageKey("local");
        localStorage.setTitle("Local storage");
        localStorage.setRootPath("storage/uploads/");
        localStorage.setBucketName("");
        localStorage.setDefaultFlag(1);
        localStorage.setAnonymousAccessAllowed(1);
        localStorage.setAllowedMimeTypes("*");
        localStorage.setMaxFileSizeMb(20);
        localStorage.setStatus("ENABLED");
        when(fileStorageSpaceMapper.findByStorageKey(ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> "local".equals(invocation.getArgument(0)) ? localStorage : null);
        when(fileStorageSpaceMapper.countDefaultStorage()).thenReturn(1L);
        when(uploadProperties.getStorageRoot()).thenReturn(tempDir.resolve("uploads").toString());
        when(uploadProperties.getPublicPath()).thenReturn("/api/uploads");
        when(documentUploadService.upload(
                any(MultipartFile.class),
                any(Path.class),
                any(String.class),
                any(Long.class),
                any(String.class),
                any(String.class)
        )).thenReturn(new DocumentUploadService.StoredDocument(
                "report.pdf",
                "report.pdf",
                "pdf",
                "application/pdf",
                128L,
                "2026/06/23/report.pdf",
                "/api/uploads/2026/06/23/report.pdf",
                "PDF",
                true
        ));
        when(multipartFile.getOriginalFilename()).thenReturn("report.pdf");
        when(multipartFile.getContentType()).thenReturn("application/pdf");

        assertThatThrownBy(() -> service.uploadFile(uploader, multipartFile, "docs", null, null, "local", "PUBLIC"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(documentUploadService).upload(
                any(MultipartFile.class),
                any(Path.class),
                any(String.class),
                any(Long.class),
                any(String.class),
                any(String.class)
        );
        verifyNoInteractions(fileObjectMapper, fileProcessingTaskRequestService);
    }

    @Test
    void listFiles_shouldNotSetHasMoreWhenCountBelowLimit() {
        AtomicInteger listInvocation = new AtomicInteger();
        when(fileObjectMapper.selectList(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObjectEntity>>any())).thenAnswer(
                invocation -> {
                    if (listInvocation.getAndIncrement() == 0) {
                        return fileObjectEntities(10);
                    }
                    return fileObjectEntities(2);
                }
        );

        CurrentUser currentUser = currentUser();
        PageResponse<?> response = service.listFiles(currentUser, "doc", null, null, null, null, "mine", 1, 100, "createdAt", "ascend");

        assertThat(response).isInstanceOf(FileVO.FileObjectPageResponse.class);
        FileVO.FileObjectPageResponse typed = (FileVO.FileObjectPageResponse) response;
        assertThat(typed.getTotal()).isEqualTo(10L);
        assertThat(typed.getHasMore()).isFalse();
        assertThat(typed.getTotalCapped()).isFalse();
        assertThat(typed.getRecords()).hasSize(2);
    }

    @Test
    void listFiles_downloadCenterScopeShouldIncludeVisibilityScopeAndLegacyDownloadBucket() {
        when(fileObjectMapper.selectList(ArgumentMatchers.<QueryWrapper<FileObjectEntity>>any()))
                .thenReturn(List.of(), fileObjectEntities(1));

        PageResponse<?> response = service.listFiles(currentUser(), null, null, null, null, null, "download-center", 1, 10, null, null);

        assertThat(response.getRecords()).hasSize(1);
        ArgumentCaptor<QueryWrapper<FileObjectEntity>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(fileObjectMapper, times(2)).selectList(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(wrapper -> {
                    assertThat(wrapper.getSqlSegment()).contains("visibility_scope");
                    assertThat(wrapper.getSqlSegment()).contains("bucket");
                    assertThat(wrapper.getSqlSegment()).contains("OR");
                });
    }

    @Test
    void listFiles_downloadCenterScopeShouldRequireDownloadCenterPermission() {
        CurrentUser currentUser = new CurrentUser(11L, "alice", null, "sid", 1, true, Set.of("system:file:view"));
        currentUser.setUserUuid("user-uuid-11");
        currentUser.setPermissionsVersion("permissions-1");
        when(systemInternalApi.permissionSnapshot(11L, "user-uuid-11")).thenReturn(permissionSnapshot(
                List.of("system:file:view"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThatThrownBy(() -> service.listFiles(currentUser, null, null, null, null, null, "download-center", 1, 10, null, null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileObjectMapper);
    }

    @Test
    void searchFilesForInternalToolShouldRejectOversizedLimitBeforeMapperAccess() {
        assertThatThrownBy(() -> service.searchFilesForInternalTool(currentUser(), null, null, null, false, 101))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileObjectMapper);
    }

    @Test
    void searchFilesForInternalToolShouldRejectNonPositiveLimitBeforeMapperAccess() {
        assertThatThrownBy(() -> service.searchFilesForInternalTool(currentUser(), null, null, null, false, 0))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileObjectMapper);
    }

    @Test
    void getFile_shouldRejectInvalidFileIdBeforeMapperAccess() {
        assertThatThrownBy(() -> service.getFile(currentUser(), 0L, false, false))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileObjectMapper);
    }

    @Test
    void readFileContent_shouldRejectOversizedFileBeforeReadingBytes() throws Exception {
        Path uploadRoot = tempDir.resolve("uploads");
        Path target = uploadRoot.resolve("large.txt");
        Files.createDirectories(uploadRoot);
        Files.write(target, new byte[(10 * 1024 * 1024) + 1]);
        when(uploadProperties.getStorageRoot()).thenReturn(uploadRoot.toString());
        when(fileObjectMapper.selectOne(ArgumentMatchers.<QueryWrapper<FileObjectEntity>>any()))
                .thenReturn(fileObjectEntity(99L, "large.txt", "large.txt"));

        assertThatThrownBy(() -> service.readFileContent(currentUser(), 99L, false, false))
                .isInstanceOf(com.lumira.common.exception.BizException.class);
    }

    @Test
    void listStorageSpaces_shouldReportExactStableTotalBelowCap() {
        AtomicInteger countInvocation = new AtomicInteger();
        when(fileStorageSpaceMapper.selectList(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileStorageSpaceEntity>>any()))
                .thenAnswer(invocation -> countInvocation.getAndIncrement() == 0 ? storageSpaceEntities(1000) : List.<FileStorageSpaceEntity>of());
        when(fileStorageSpaceMapper.listWithUsage(2L, 0L)).thenReturn(storageSpaceEntities(2));

        CurrentUser currentUser = currentUser();
        PageResponse<?> response = service.listStorageSpaces(currentUser, 1, 2);

        assertThat(response).isInstanceOf(FileVO.StorageSpacePageResponse.class);
        FileVO.StorageSpacePageResponse typed = (FileVO.StorageSpacePageResponse) response;
        assertThat(typed.getTotal()).isEqualTo(1000L);
        assertThat(typed.getHasMore()).isFalse();
        assertThat(typed.getTotalCapped()).isFalse();
        assertThat(typed.getRecords()).hasSize(2);
    }

    @Test
    void listFiles_mineScopeShouldFilterByUploaderUuid() {
        when(fileObjectMapper.selectList(ArgumentMatchers.<QueryWrapper<FileObjectEntity>>any()))
                .thenReturn(fileObjectEntities(1), List.of());

        service.listFiles(currentUser(), null, null, null, null, null, "mine", 1, 10, null, null);

        ArgumentCaptor<QueryWrapper<FileObjectEntity>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(fileObjectMapper, times(2)).selectList(captor.capture());
        assertThat(captor.getAllValues().getFirst().getSqlSegment())
                .contains("uploaded_by")
                .contains("uploaded_by_uuid");
    }

    @Test
    void listStorageSpaces_shouldRejectBlankUsernameBeforeMapperAccess() {
        assertThatThrownBy(() -> service.listStorageSpaces(blankUsernameUser(), 1, 2))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileStorageSpaceMapper);
    }

    @Test
    void listStorageSpaces_shouldRejectWhenLivePermissionsLoseManagePermissionBeforeMapperAccess() {
        CurrentUser currentUser = currentUser("system:file:manage");
        when(systemInternalApi.permissionSnapshot(11L, "user-uuid-11")).thenReturn(permissionSnapshot(
                List.of("system:file:view"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThatThrownBy(() -> service.listStorageSpaces(currentUser, 1, 2))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileStorageSpaceMapper);
    }

    @Test
    void listStorageSpaces_shouldNotSetHasMoreWhenCountBelowLimit() {
        when(fileStorageSpaceMapper.selectList(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileStorageSpaceEntity>>any()))
                .thenReturn(storageSpaceEntities(2));
        when(fileStorageSpaceMapper.listWithUsage(2L, 0L)).thenReturn(storageSpaceEntities(2));

        CurrentUser currentUser = currentUser();
        PageResponse<?> response = service.listStorageSpaces(currentUser, 1, 2);

        assertThat(response).isInstanceOf(FileVO.StorageSpacePageResponse.class);
        FileVO.StorageSpacePageResponse typed = (FileVO.StorageSpacePageResponse) response;
        assertThat(typed.getTotal()).isEqualTo(2L);
        assertThat(typed.getHasMore()).isFalse();
        assertThat(typed.getTotalCapped()).isFalse();
        assertThat(typed.getRecords()).hasSize(2);
    }

    @Test
    void resolveFilePath_shouldRejectPendingScanContent() {
        FileObjectEntity pending = fileObjectEntity(88L, "pending.pdf", "2026/08/pending.pdf");
        pending.setStatus("PENDING_SCAN");
        when(fileObjectMapper.selectOne(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObjectEntity>>any()))
                .thenReturn(pending);

        assertThatThrownBy(() -> service.resolveFilePath(currentUser(), 88L, false, false))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.readFileContent(currentUser(), 88L, false, false))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void getPreviewableFile_shouldScanPendingDownloadCenterContentBeforeReturning() {
        FileObjectEntity pending = fileObjectEntity(88L, "pending.pdf", "2026/08/pending.pdf");
        pending.setFileExtension("pdf");
        pending.setContentType("application/pdf");
        pending.setPreviewMode("PDF");
        pending.setStatus("PENDING_SCAN");
        when(fileObjectMapper.selectOne(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObjectEntity>>any()))
                .thenReturn(pending);
        when(fileSecurityScanProcessor.scan(88L, 11L, "user-uuid-11")).thenAnswer(invocation -> {
            pending.setStatus("CLEAN");
            return new FileSecurityScanProcessor.SecurityScanResult(88L, "TEST", "CLEAN", "", 128L);
        });

        FileObjectDTO preview = service.getPreviewableFile(currentUser(), 88L, true, true);

        assertThat(preview.status()).isEqualTo("CLEAN");
        assertThat(preview.previewUrl()).isEqualTo("/api/uploads/2026/08/pending.pdf");
        verify(fileSecurityScanProcessor).scan(88L, 11L, "user-uuid-11");
    }

    @Test
    void listStorageSpaceOptions_shouldAllowCompetitionEditorsAndHideSystemManagedCompetitionBuckets() {
        CurrentUser currentUser = currentUser("aiadc:competition:update");
        when(systemInternalApi.permissionSnapshot(11L, "user-uuid-11")).thenReturn(permissionSnapshot(
                List.of("aiadc:competition:update"),
                List.of(),
                List.of(),
                List.of()
        ));
        List<FileStorageSpaceEntity> storageSpaces = new ArrayList<>(storageSpaceEntities(2));
        FileStorageSpaceEntity competitionStorage = storageSpaceEntities(1).getFirst();
        competitionStorage.setStorageKey("competition_ca5e4e825be14d068aba3c9cb45acad1");
        storageSpaces.add(competitionStorage);
        when(fileStorageSpaceMapper.listWithUsage(100L, 0L)).thenReturn(storageSpaces);

        List<StorageSpaceOptionDTO> options = service.listStorageSpaceOptions(currentUser);

        assertThat(options)
                .extracting(StorageSpaceOptionDTO::storageKey)
                .containsExactly("bucket-0", "bucket-1");
    }

    @Test
    void listStorageSpaceOptions_shouldRejectUsersWithoutCompetitionOrFileManagementPermission() {
        CurrentUser currentUser = currentUser("system:file:view");
        when(systemInternalApi.permissionSnapshot(11L, "user-uuid-11")).thenReturn(permissionSnapshot(
                List.of("system:file:view"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThatThrownBy(() -> service.listStorageSpaceOptions(currentUser))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileStorageSpaceMapper);
    }

    @Test
    void ensureCompetitionStorageSpaceCreatesOnePrivateLocalBucketIdempotently() {
        String competitionUuid = "ca5e4e82-5be1-4d06-8aba-3c9cb45acad1";
        String storageKey = "competition_ca5e4e825be14d068aba3c9cb45acad1";
        Path uploadRoot = tempDir.resolve("uploads");
        when(uploadProperties.getStorageRoot()).thenReturn(uploadRoot.toString());
        AtomicReference<FileStorageSpaceEntity> stored = new AtomicReference<>();
        when(fileStorageSpaceMapper.findByStorageKey(storageKey)).thenAnswer(invocation -> stored.get());
        when(fileStorageSpaceMapper.insert(any(FileStorageSpaceEntity.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        CompetitionStorageSpaceRequest request = new CompetitionStorageSpaceRequest(
                88L,
                competitionUuid,
                "全国大学生智能应用开发大赛",
                11L,
                "user-uuid-11"
        );

        service.ensureCompetitionStorageSpace(request);
        service.ensureCompetitionStorageSpace(request);

        FileStorageSpaceEntity entity = stored.get();
        assertThat(entity).isNotNull();
        assertThat(entity.getStorageKey()).isEqualTo(storageKey);
        assertThat(entity.getRootPath()).isEqualTo(
                "storage/uploads/competitions/ca5e4e825be14d068aba3c9cb45acad1/"
        );
        assertThat(entity.getProvider()).isEqualTo("LOCAL");
        assertThat(entity.getDefaultFlag()).isZero();
        assertThat(entity.getAnonymousAccessAllowed()).isZero();
        assertThat(entity.getMaxFileSizeMb()).isEqualTo(1024);
        assertThat(entity.getCreatedBy()).isEqualTo(11L);
        assertThat(Files.isDirectory(uploadRoot.resolve("competitions/ca5e4e825be14d068aba3c9cb45acad1"))).isTrue();
        verify(fileStorageSpaceMapper, times(1)).insert(any(FileStorageSpaceEntity.class));
    }

    @Test
    void platformDefaultStorageSpacesShouldBeSeededByDatabase() throws Exception {
        String sql = Files.readString(Path.of("../../sql/saas.sql"));
        String source = Files.readString(Path.of("src/main/java/com/lumira/file/app/FileManagementAppService.java"));

        assertThat(sql).contains("INSERT INTO `file_storage_space`");
        assertThat(sql).contains("'local'", "'download_center'", "'ai_chat'", "'avatar'", "'support_feedback'");
        assertThat(source).doesNotContain("DEFAULT_STORAGE_SPACES", "new DefaultStorageSpace", "private record DefaultStorageSpace");
        assertThat(source).doesNotContain("new StorageSpaceDTO(null, \"Local storage\"");
    }

    @Test
    void legacySystemPublicStorageMigrationShouldBeDatabaseOwned() throws Exception {
        String sql = Files.readString(Path.of("../../sql/upgrade-file-storage-space-persistence-v1.sql"));
        String source = Files.readString(Path.of("src/main/java/com/lumira/file/app/FileManagementAppService.java"));

        assertThat(sql).contains("WHERE `bucket`='system_public'", "SET `bucket`='local'");
        assertThat(source).doesNotContain("LEGACY_STORAGE_KEY_SYSTEM_PUBLIC", "mergeLegacySystemPublicStorageSpace");
    }

    @Test
    void fileBusinessPoliciesShouldBeDatabaseOwned() throws Exception {
        String sql = Files.readString(Path.of("../../sql/upgrade-file-business-policy-dictionary-v1.sql"));
        String source = Files.readString(Path.of("src/main/java/com/lumira/file/app/FileManagementAppService.java"));

        assertThat(sql).contains("file_storage_provider", "file_rename_strategy", "file_storage_status",
                "file_preview_extension", "file_preview_content_type", "file_runtime_default");
        assertThat(source).doesNotContain(
                "Set.of(\"LOCAL\", \"ALIYUN_OSS\", \"TENCENT_COS\")",
                "Set.of(\"APPEND_RANDOM_ID\", \"RANDOM_STRING\", \"KEEP_ORIGINAL\")",
                "List.of(\"png\", \"jpg\"",
                "case \"ALIYUN_OSS\"");
    }

    @Test
    void uploadDocument_shouldFallbackToDefaultStorageWhenRequestedBucketIsMissing() {
        FileStorageSpaceEntity localStorage = storageSpaceEntities(1).getFirst();
        localStorage.setStorageKey("local");
        localStorage.setTitle("Local storage");
        localStorage.setRootPath("storage/uploads/");
        localStorage.setBucketName("");
        localStorage.setDefaultFlag(1);
        localStorage.setAnonymousAccessAllowed(1);
        localStorage.setAllowedMimeTypes("*");
        localStorage.setMaxFileSizeMb(20);
        localStorage.setStatus("ENABLED");
        when(fileStorageSpaceMapper.findByStorageKey(ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> "local".equals(invocation.getArgument(0)) ? localStorage : null);
        when(fileStorageSpaceMapper.countDefaultStorage()).thenReturn(1L);
        when(fileStorageSpaceMapper.findDefault()).thenReturn(localStorage);
        when(uploadProperties.getStorageRoot()).thenReturn(tempDir.resolve("uploads").toString());
        when(uploadProperties.getPublicPath()).thenReturn("/api/uploads");
        when(documentUploadService.upload(
                any(MultipartFile.class),
                any(Path.class),
                any(String.class),
                any(Long.class),
                any(String.class),
                any(String.class)
        )).thenReturn(new DocumentUploadService.StoredDocument(
                "report.pdf",
                "report.pdf",
                "pdf",
                "application/pdf",
                128L,
                "2026/06/23/report.pdf",
                "/api/uploads/2026/06/23/report.pdf",
                "PDF",
                true
        ));
        when(fileObjectMapper.insert(any(FileObjectEntity.class))).thenAnswer(invocation -> {
            FileObjectEntity entity = invocation.getArgument(0);
            entity.setId(99L);
            return 1;
        });
        FileObjectEntity inserted = fileObjectEntities(1).getFirst();
        inserted.setId(99L);
        inserted.setBucket("local");
        inserted.setStorageType("LOCAL");
        inserted.setObjectKey("2026/06/23/report.pdf");
        inserted.setOriginalFilename("report.pdf");
        inserted.setContentType("application/pdf");
        inserted.setFileExtension("pdf");
        inserted.setFileSize(128L);
        inserted.setPublicUrl("/api/uploads/2026/06/23/report.pdf");
        inserted.setPreviewMode("PDF");
        inserted.setPreviewableFlag(1);
        inserted.setStatus("PENDING_SCAN");
        when(fileObjectMapper.selectById(99L)).thenReturn(inserted);

        FileObjectDTO uploaded = service.uploadDocument(currentUser(), multipartFile, "资料", null, null, "missing_bucket");

        ArgumentCaptor<FileObjectEntity> captor = ArgumentCaptor.forClass(FileObjectEntity.class);
        verify(fileObjectMapper).insert(captor.capture());
        assertThat(captor.getValue().getBucket()).isEqualTo("local");
        assertThat(captor.getValue().getUploadedByUuid()).isEqualTo("user-uuid-11");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING_SCAN");
        assertThat(uploaded.status()).isEqualTo("PENDING_SCAN");
        assertThat(uploaded.publicUrl()).isNull();
        assertThat(uploaded.previewUrl()).isNull();
        assertThat(uploaded.downloadUrl()).isNull();
    }

    @Test
    void uploadPublicImage_shouldSynchronouslyScanCleanBeforeReturningUrl() {
        java.util.concurrent.atomic.AtomicReference<FileObjectEntity> stored = preparePublicImageUpload();
        when(fileSecurityScanProcessor.scan(99L, 11L, "user-uuid-11")).thenAnswer(invocation -> {
            stored.get().setStatus("CLEAN");
            return new FileSecurityScanProcessor.SecurityScanResult(99L, "TEST", "CLEAN", "", 64L);
        });

        FileObjectDTO uploaded = service.uploadPublicImage(currentUser(), multipartFile, "avatar", "profile", "local");

        assertThat(uploaded.status()).isEqualTo("CLEAN");
        assertThat(uploaded.publicUrl()).isEqualTo("/api/uploads/2026/08/avatar.png");
        verify(fileProcessingTaskRequestService).requestTasksForUpload(eq(uploaded), any(CurrentUser.class));
    }

    @Test
    void uploadPublicImage_shouldRejectThreatWithoutPublishingOrQueueing() {
        java.util.concurrent.atomic.AtomicReference<FileObjectEntity> stored = preparePublicImageUpload();
        when(fileSecurityScanProcessor.scan(99L, 11L, "user-uuid-11")).thenAnswer(invocation -> {
            stored.get().setStatus("REJECTED");
            return new FileSecurityScanProcessor.SecurityScanResult(99L, "TEST", "THREAT_DETECTED", "TEST", 64L);
        });

        assertThatThrownBy(() -> service.uploadPublicImage(currentUser(), multipartFile, "avatar", "profile", "local"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(domainEventPublisher, fileProcessingTaskRequestService);
    }

    @Test
    void uploadPublicImage_shouldFailClosedWhenSynchronousScanFails() {
        preparePublicImageUpload();
        when(fileSecurityScanProcessor.scan(99L, 11L, "user-uuid-11"))
                .thenThrow(new IllegalStateException("scanner unavailable"));

        assertThatThrownBy(() -> service.uploadPublicImage(currentUser(), multipartFile, "avatar", "profile", "local"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(domainEventPublisher, fileProcessingTaskRequestService);
    }

    private java.util.concurrent.atomic.AtomicReference<FileObjectEntity> preparePublicImageUpload() {
        FileStorageSpaceEntity localStorage = storageSpaceEntities(1).getFirst();
        localStorage.setStorageKey("local");
        localStorage.setProvider("LOCAL");
        localStorage.setRootPath("storage/uploads/");
        localStorage.setDefaultFlag(1);
        localStorage.setAnonymousAccessAllowed(1);
        localStorage.setAllowedMimeTypes("*");
        localStorage.setMaxFileSizeMb(20);
        localStorage.setStatus("ENABLED");
        when(fileStorageSpaceMapper.findByStorageKey("local")).thenReturn(localStorage);
        when(uploadProperties.getStorageRoot()).thenReturn(tempDir.resolve("uploads").toString());
        when(uploadProperties.getPublicPath()).thenReturn("/api/uploads");
        when(imageUploadService.upload(any(MultipartFile.class), any(Path.class), any(String.class), any(Long.class), any(String.class), any(String.class)))
                .thenReturn(new ImageUploadService.StoredImage(
                        "avatar.png", "avatar.png", ".png", "image/png", 64L,
                        "2026/08/avatar.png", "/api/uploads/2026/08/avatar.png"));
        java.util.concurrent.atomic.AtomicReference<FileObjectEntity> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(fileObjectMapper.insert(any(FileObjectEntity.class))).thenAnswer(invocation -> {
            FileObjectEntity entity = invocation.getArgument(0);
            entity.setId(99L);
            stored.set(entity);
            return 1;
        });
        when(fileObjectMapper.selectById(99L)).thenAnswer(invocation -> stored.get());
        return stored;
    }

    @Test
    void deleteStorageSpace_shouldRejectWhenFilesExist() {
        CurrentUser currentUser = currentUser();
        when(fileStorageSpaceMapper.findByIdWithUsage(5L)).thenReturn(storageSpaceEntities(1).getFirst());
        when(fileObjectMapper.selectOne(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObjectEntity>>any()))
                .thenReturn(new FileObjectEntity());

        assertThatThrownBy(() -> service.deleteStorageSpace(currentUser, 5L))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(fileObjectMapper).selectOne(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObjectEntity>>any());
    }

    @Test
    void deleteStorageSpace_shouldRejectWhenLivePermissionsLoseDeletePermissionBeforePersistence() {
        CurrentUser currentUser = currentUser("system:file:manage:delete");
        when(systemInternalApi.permissionSnapshot(11L, "user-uuid-11")).thenReturn(permissionSnapshot(
                List.of("system:file:manage"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThatThrownBy(() -> service.deleteStorageSpace(currentUser, 5L))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(fileStorageSpaceMapper, fileObjectMapper);
    }

    @Test
    void storageSpaceWritesShouldBindOriginalProviderKeyStatusAndDefaultFlag() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/file/infrastructure/MyBatisFileStorageSpaceRepository.java"));

        assertThat(source).contains(
                ".eq(FileStorageSpaceEntity::getStorageKey, expected.storageKey())",
                ".eq(FileStorageSpaceEntity::getProvider, expected.provider())",
                ".eq(FileStorageSpaceEntity::getStatus, expected.status())",
                ".eq(FileStorageSpaceEntity::getDefaultFlag, Boolean.TRUE.equals(expected.defaultStorage()) ? 1 : 0)"
        );
    }

    @Test
    void deleteFile_shouldBindPersonalOwnerUuidInFinalSoftDelete() throws Exception {
        Path uploadRoot = tempDir.resolve("uploads");
        Files.createDirectories(uploadRoot);
        Files.writeString(uploadRoot.resolve("obj-1"), "hello");
        when(uploadProperties.getStorageRoot()).thenReturn(uploadRoot.toString());
        FileObjectEntity file = fileObjectEntity(1L, "file.txt", "obj-1");
        when(fileObjectMapper.selectOne(ArgumentMatchers.<QueryWrapper<FileObjectEntity>>any())).thenReturn(file);

        service.deleteFile(currentUser(), 1L, false, false);

        ArgumentCaptor<UpdateWrapper<FileObjectEntity>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(fileObjectMapper).update(ArgumentMatchers.isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSet()).contains("updated_by_uuid");
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("id", "deleted", "uploaded_by", "uploaded_by_uuid");
    }

    @Test
    void testStorageSpace_shouldRejectLocalRootOutsideUploadRoot() {
        CurrentUser currentUser = currentUser();
        FileStorageSpaceEntity entity = storageSpaceEntities(1).getFirst();
        entity.setRootPath(tempDir.resolve("outside").toString());
        when(uploadProperties.getStorageRoot()).thenReturn(tempDir.resolve("uploads").toString());
        when(fileStorageSpaceMapper.findByIdWithUsage(5L)).thenReturn(entity);

        var result = service.testStorageSpace(currentUser, 5L);

        assertThat(result.getStatus()).isEqualTo("DOWN");
        assertThat(result.getMessage()).isEqualTo("存储空间不可访问或配置不正确");
    }

    @Test
    void testStorageSpace_shouldNotReturnLocalRootPathWhenWritable() {
        CurrentUser currentUser = currentUser();
        FileStorageSpaceEntity entity = storageSpaceEntities(1).getFirst();
        Path uploadRoot = tempDir.resolve("uploads");
        Path localRoot = uploadRoot.resolve("tenant-local");
        entity.setRootPath(localRoot.toString());
        when(uploadProperties.getStorageRoot()).thenReturn(uploadRoot.toString());
        when(fileStorageSpaceMapper.findByIdWithUsage(5L)).thenReturn(entity);

        var result = service.testStorageSpace(currentUser, 5L);

        assertThat(result.getStatus()).isEqualTo("UP");
        assertThat(result.getMessage()).isEqualTo("本地存储目录可写");
        assertThat(result.getMessage()).doesNotContain(localRoot.toString());
    }

    @Test
    void safeUrlValidatorShouldRejectLocalhostEndpoint() {
        assertThatThrownBy(() -> new SafeUrlValidator().validateHttpUrl("http://localhost:8080/internal"))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Remote storage endpoint is not allowed");
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser(11L, "alice", null, "sid", 1, true, Set.of("*"));
        currentUser.setUserUuid("user-uuid-11");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private CurrentUser currentUser(String... permissions) {
        CurrentUser currentUser = new CurrentUser(11L, "alice", null, "sid", 1, true, Set.of(permissions));
        currentUser.setUserUuid("user-uuid-11");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(11L, "alice", null, "sid", 1, false, Set.of("*"));
    }

    private CurrentUser blankUsernameUser() {
        return new CurrentUser(11L, " ", null, "sid", 1, true, Set.of("*"));
    }

    private CurrentUser missingSessionVersionUser() {
        return new CurrentUser(11L, "alice", null, "sid", null, true, Set.of("*"));
    }

    private List<FileObjectEntity> fileObjectEntities(int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> {
                    FileObjectEntity entity = new FileObjectEntity();
                    entity.setId((long) index + 1L);
                    entity.setUploadedBy(11L);
                    entity.setUploadedByUuid("user-uuid-11");
                    entity.setOriginalFilename("file-" + index);
                    entity.setObjectKey("obj-" + index);
                    entity.setStorageType("object");
                    entity.setBucket("bucket");
                    entity.setFileExtension("txt");
                    entity.setContentType("text/plain");
                    entity.setFileSize(100L);
                    entity.setPublicUrl("https://example.test/file");
                    entity.setPreviewMode("TEXT");
                    entity.setPreviewableFlag(1);
                    entity.setCategory("cat");
                    entity.setStatus("ENABLED");
                    entity.setCreatedAt(LocalDateTime.now());
                    entity.setUpdatedAt(LocalDateTime.now());
                    return entity;
                })
                .toList();
    }

    private FileObjectEntity fileObjectEntity(Long id, String originalFilename, String objectKey) {
        FileObjectEntity entity = new FileObjectEntity();
        entity.setId(id);
        entity.setUploadedBy(11L);
        entity.setUploadedByUuid("user-uuid-11");
        entity.setOriginalFilename(originalFilename);
        entity.setObjectKey(objectKey);
        entity.setStorageType("LOCAL");
        entity.setBucket("local");
        entity.setFileExtension("txt");
        entity.setContentType("text/plain");
        entity.setFileSize(100L);
        entity.setPublicUrl("/api/uploads/" + objectKey);
        entity.setPreviewMode("TEXT");
        entity.setPreviewableFlag(1);
        entity.setStatus("ENABLED");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private List<FileStorageSpaceEntity> storageSpaceEntities(int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> {
                    FileStorageSpaceEntity entity = new FileStorageSpaceEntity();
                    entity.setId((long) index + 1L);
                    entity.setTitle("space-" + index);
                    entity.setStorageKey("bucket-" + index);
                    entity.setProvider("LOCAL");
                    entity.setRootPath("/tmp");
                    entity.setBucketName("bucket");
                    entity.setRenameStrategy("NONE");
                    entity.setMaxFileSizeMb(100);
                    entity.setStatus("ENABLED");
                    entity.setDefaultFlag(0);
                    entity.setRetainFileOnRecordDelete(0);
                    entity.setAnonymousAccessAllowed(0);
                    entity.setCreatedAt(LocalDateTime.now());
                    entity.setUpdatedAt(LocalDateTime.now());
                    entity.setFileCount((long) index);
                    entity.setTotalSizeBytes((long) index * 1024);
                    return entity;
                })
                .toList();
    }

    private ObjectProvider<SystemInternalApi> provider(SystemInternalApi internalApi) {
        ObjectProvider<SystemInternalApi> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.lenient().when(provider.getIfAvailable()).thenReturn(internalApi);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<FileSecurityScanProcessor> scanProvider(FileSecurityScanProcessor processor) {
        ObjectProvider<FileSecurityScanProcessor> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.lenient().when(provider.getIfAvailable()).thenReturn(processor);
        return provider;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private PermissionSnapshotDTO permissionSnapshot(
            List<String> permissions,
            List<Long> roleIds,
            List<Long> deptIds,
            List<Long> descendantDeptIds
    ) {
        return new PermissionSnapshotDTO(
                "perm-v11",
                permissions,
                roleIds,
                21L,
                deptIds,
                descendantDeptIds,
                List.of(),
                "/files"
        );
    }
}
