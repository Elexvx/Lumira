package com.lumira.file;

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
import com.lumira.file.processing.FileProcessingTaskService;
import com.lumira.file.security.SafeUrlValidator;
import com.lumira.file.upload.DocumentUploadService;
import com.lumira.file.upload.FileStorageMetrics;
import com.lumira.file.upload.ImageUploadService;
import com.lumira.file.vo.FileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private FileProcessingTaskService fileProcessingTaskService;

    @Mock
    private FieldCryptoService fieldCryptoService;

    @Mock
    private FileStorageMetrics storageMetrics;

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
                fileProcessingTaskService,
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

        CurrentUser currentUser = new CurrentUser(11L, "alice", 1001L, "sid", 1, true, Set.of("*"));
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
                    return fileObjectEntities(2, 200L);
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
    void listStorageSpaces_shouldReportCappedTotalForLargePageWindow() {
        AtomicInteger countInvocation = new AtomicInteger();
        when(fileStorageSpaceMapper.selectList(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileStorageSpaceEntity>>any()))
                .thenAnswer(invocation -> countInvocation.getAndIncrement() == 0 ? storageSpaceEntities(1000) : List.<FileStorageSpaceEntity>of());
        when(fileStorageSpaceMapper.listWithUsage(1001L, 2L, 0L)).thenReturn(storageSpaceEntities(2, 1001L));

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
                .thenReturn(storageSpaceEntities(2, 1001L));
        when(fileStorageSpaceMapper.listWithUsage(1001L, 2L, 0L)).thenReturn(storageSpaceEntities(2, 1001L));

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
    void deleteStorageSpace_shouldRejectWhenFilesExist() {
        CurrentUser currentUser = currentUser();
        when(fileStorageSpaceMapper.findByIdWithUsage(1001L, 5L)).thenReturn(storageSpaceEntities(1, 1001L).getFirst());
        when(fileObjectMapper.selectOne(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObjectEntity>>any()))
                .thenReturn(new FileObjectEntity());

        assertThatThrownBy(() -> service.deleteStorageSpace(currentUser, 5L))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(fileObjectMapper).selectOne(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObjectEntity>>any());
    }

    @Test
    void testStorageSpace_shouldRejectLocalRootOutsideUploadRoot() {
        CurrentUser currentUser = currentUser();
        FileStorageSpaceEntity entity = storageSpaceEntities(1, 1001L).getFirst();
        entity.setRootPath(tempDir.resolve("outside").toString());
        when(uploadProperties.getStorageRoot()).thenReturn(tempDir.resolve("uploads").toString());
        when(fileStorageSpaceMapper.findByIdWithUsage(1001L, 5L)).thenReturn(entity);

        var result = service.testStorageSpace(currentUser, 5L);

        assertThat(result.getStatus()).isEqualTo("DOWN");
        assertThat(result.getMessage()).isEqualTo("存储空间不可访问或配置不正确");
    }

    @Test
    void testStorageSpace_shouldNotReturnLocalRootPathWhenWritable() {
        CurrentUser currentUser = currentUser();
        FileStorageSpaceEntity entity = storageSpaceEntities(1, 1001L).getFirst();
        Path uploadRoot = tempDir.resolve("uploads");
        Path localRoot = uploadRoot.resolve("tenant-local");
        entity.setRootPath(localRoot.toString());
        when(uploadProperties.getStorageRoot()).thenReturn(uploadRoot.toString());
        when(fileStorageSpaceMapper.findByIdWithUsage(1001L, 5L)).thenReturn(entity);

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
        return new CurrentUser(11L, "alice", 1001L, "sid", 1, true, Set.of("*"));
    }

    private List<FileObjectEntity> fileObjectEntities(int size) {
        return fileObjectEntities(size, 1001L);
    }

    private List<FileObjectEntity> fileObjectEntities(int size, Long tenantId) {
        return IntStream.range(0, size)
                .mapToObj(index -> {
                    FileObjectEntity entity = new FileObjectEntity();
                    entity.setId((long) index + 1L);
                    entity.setTenantId(tenantId);
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
        return storageSpaceEntities(size, 1001L);
    }

    private List<FileStorageSpaceEntity> storageSpaceEntities(int size, Long tenantId) {
        return IntStream.range(0, size)
                .mapToObj(index -> {
                    FileStorageSpaceEntity entity = new FileStorageSpaceEntity();
                    entity.setId((long) index + 1L);
                    entity.setTenantId(tenantId);
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
