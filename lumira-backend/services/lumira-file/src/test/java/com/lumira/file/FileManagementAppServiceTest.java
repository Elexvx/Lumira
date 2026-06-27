package com.lumira.file;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.common.vo.PageResponse;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.file.app.FileManagementAppService;
import com.lumira.file.config.UploadProperties;
import com.lumira.file.entity.FileObjectEntity;
import com.lumira.file.entity.FileStorageSpaceEntity;
import com.lumira.file.mapper.FileObjectMapper;
import com.lumira.file.mapper.FileStorageSpaceMapper;
import com.lumira.file.processing.FileProcessingTaskRequestService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileManagementAppServiceTest {

    @Mock
    private FileObjectMapper fileObjectMapper;

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
    private FieldCryptoService fieldCryptoService;

    @Mock
    private FileStorageMetrics storageMetrics;

    @Mock
    private MultipartFile multipartFile;

    @TempDir
    Path tempDir;

    private FileManagementAppService service;

    @BeforeEach
    void setUp() {
        service = new FileManagementAppService(
                fileObjectMapper,
                fileStorageSpaceMapper,
                jdbcTemplate,
                uploadProperties,
                documentUploadService,
                imageUploadService,
                domainEventPublisher,
                fileProcessingTaskRequestService,
                fieldCryptoService,
                storageMetrics,
                new SafeUrlValidator()
        );
    }

    @Test
    void listFiles_shouldReportCappedTotalForLargePageWindow() {
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
        PageResponse<?> response = service.listFiles(currentUser, null, null, null, null, null, null, 1, 100, null, null);

        assertThat(response).isInstanceOf(FileVO.FileObjectPageResponse.class);
        assertThat(response.getTotal()).isEqualTo(101L);
        assertThat(response.getPageNo()).isEqualTo(1L);
        assertThat(response.getPageSize()).isEqualTo(100L);
        assertThat(response.getRecords()).hasSize(0);
        assertThat(((FileVO.FileObjectPageResponse) response).getHasMore()).isTrue();
        assertThat(((FileVO.FileObjectPageResponse) response).getTotalCapped()).isTrue();
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
    void listFiles_downloadCenterScopeShouldIncludeRecordsSavedInLegacyLocalBucket() {
        when(fileObjectMapper.selectList(ArgumentMatchers.<QueryWrapper<FileObjectEntity>>any()))
                .thenReturn(List.of(), fileObjectEntities(1));

        PageResponse<?> response = service.listFiles(currentUser(), null, null, null, null, null, "download-center", 1, 10, null, null);

        assertThat(response.getRecords()).hasSize(1);
        ArgumentCaptor<QueryWrapper<FileObjectEntity>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(fileObjectMapper, times(2)).selectList(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(wrapper -> {
                    assertThat(wrapper.getSqlSegment()).contains("visibility_scope");
                    assertThat(wrapper.getSqlSegment()).doesNotContain("bucket");
                });
    }

    @Test
    void listStorageSpaces_shouldReportCappedTotalForLargePageWindow() {
        AtomicInteger countInvocation = new AtomicInteger();
        when(fileStorageSpaceMapper.selectList(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileStorageSpaceEntity>>any()))
                .thenAnswer(invocation -> countInvocation.getAndIncrement() == 0 ? storageSpaceEntities(1000) : List.<FileStorageSpaceEntity>of());
        when(fileStorageSpaceMapper.listWithUsage(2L, 0L)).thenReturn(storageSpaceEntities(2));

        CurrentUser currentUser = currentUser();
        PageResponse<?> response = service.listStorageSpaces(currentUser, 1, 2);

        assertThat(response).isInstanceOf(FileVO.StorageSpacePageResponse.class);
        FileVO.StorageSpacePageResponse typed = (FileVO.StorageSpacePageResponse) response;
        assertThat(typed.getTotal()).isEqualTo(3L);
        assertThat(typed.getHasMore()).isTrue();
        assertThat(typed.getTotalCapped()).isTrue();
        assertThat(typed.getRecords()).hasSize(2);
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
    void listStorageSpaces_shouldSeedPlatformDefaultStorageSpacesWhenMissing() {
        when(fileStorageSpaceMapper.countDefaultStorage()).thenReturn(0L);
        when(fileStorageSpaceMapper.selectList(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileStorageSpaceEntity>>any()))
                .thenReturn(storageSpaceEntities(3));
        when(fileStorageSpaceMapper.listWithUsage(10L, 0L)).thenReturn(storageSpaceEntities(3));

        PageResponse<?> response = service.listStorageSpaces(currentUser(), 1, 10);

        ArgumentCaptor<FileStorageSpaceEntity> captor = ArgumentCaptor.forClass(FileStorageSpaceEntity.class);
        verify(fileStorageSpaceMapper, times(5)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FileStorageSpaceEntity::getStorageKey)
                .containsExactly("local", "download_center", "ai_chat", "avatar", "support_feedback");
        assertThat(captor.getAllValues().getFirst().getDefaultFlag()).isEqualTo(1);
        assertThat(captor.getAllValues())
                .filteredOn(entity -> "local".equals(entity.getStorageKey()) || "download_center".equals(entity.getStorageKey()) || "avatar".equals(entity.getStorageKey()))
                .extracting(FileStorageSpaceEntity::getAnonymousAccessAllowed)
                .containsOnly(1);
        assertThat(response.getRecords()).hasSize(3);
    }

    @Test
    void listStorageSpaces_shouldMergeLegacySystemPublicStorageIntoLocal() {
        FileStorageSpaceEntity legacyStorage = storageSpaceEntities(1).getFirst();
        legacyStorage.setStorageKey("system_public");
        when(fileStorageSpaceMapper.findByStorageKey("system_public")).thenReturn(legacyStorage);
        when(fileStorageSpaceMapper.countDefaultStorage()).thenReturn(1L);
        when(fileStorageSpaceMapper.selectList(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileStorageSpaceEntity>>any()))
                .thenReturn(List.of());
        when(fileStorageSpaceMapper.listWithUsage(10L, 0L)).thenReturn(List.of());

        service.listStorageSpaces(currentUser(), 1, 10);

        verify(fileObjectMapper).update(
                ArgumentMatchers.isNull(),
                ArgumentMatchers.<UpdateWrapper<FileObjectEntity>>any()
        );
        verify(fileStorageSpaceMapper).update(
                ArgumentMatchers.isNull(),
                ArgumentMatchers.<UpdateWrapper<FileStorageSpaceEntity>>any()
        );
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
        when(fileObjectMapper.selectById(99L)).thenReturn(inserted);

        service.uploadDocument(currentUser(), multipartFile, "资料", null, null, "missing_bucket");

        ArgumentCaptor<FileObjectEntity> captor = ArgumentCaptor.forClass(FileObjectEntity.class);
        verify(fileObjectMapper).insert(captor.capture());
        assertThat(captor.getValue().getBucket()).isEqualTo("local");
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
        return new CurrentUser(11L, "alice", null, "sid", 1, true, Set.of("*"));
    }

    private List<FileObjectEntity> fileObjectEntities(int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> {
                    FileObjectEntity entity = new FileObjectEntity();
                    entity.setId((long) index + 1L);
                    entity.setUploadedBy(11L);
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
}
