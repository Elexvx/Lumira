package com.lumira.file;

import com.lumira.file.app.FileManagementAppService;
import com.lumira.file.config.FileOcrProperties;
import com.lumira.file.config.FileSecurityScanProperties;
import com.lumira.file.config.UploadProperties;
import com.lumira.file.config.UploadResourceSecurityInterceptor;
import com.lumira.file.event.FileOutboxMetricsService;
import com.lumira.file.event.FileOutboxRelay;
import com.lumira.file.event.FilePlatformEventPublisher;
import com.lumira.file.event.LoggingFileOutboxDispatcher;
import com.lumira.file.event.PlatformEventOutboxService;
import com.lumira.file.event.domain.FileDomainEventPublisher;
import com.lumira.file.infrastructure.security.FileJwtAuthFilter;
import com.lumira.file.mapper.FileObjectMapper;
import com.lumira.file.processing.ClamAvFileSecurityScanEngine;
import com.lumira.file.processing.DisabledFileOcrEngine;
import com.lumira.file.processing.FileAiParseProcessor;
import com.lumira.file.processing.FileOcrEngineSelector;
import com.lumira.file.processing.FileOcrProcessor;
import com.lumira.file.processing.FileProcessingMetrics;
import com.lumira.file.processing.FileProcessingTaskRequestService;
import com.lumira.file.processing.FileProcessingTaskService;
import com.lumira.file.processing.FileSecurityScanEngineSelector;
import com.lumira.file.processing.FileSecurityScanMetrics;
import com.lumira.file.processing.FileSecurityScanProcessor;
import com.lumira.file.processing.FileTextExtractionProcessor;
import com.lumira.file.processing.FileThumbnailProcessor;
import com.lumira.file.processing.InlineFileSecurityScanEngine;
import com.lumira.file.processing.TesseractFileOcrEngine;
import com.lumira.file.security.SafeUrlValidator;
import com.lumira.file.upload.DocumentUploadService;
import com.lumira.file.upload.FileStorageMetrics;
import com.lumira.file.upload.ImageUploadService;
import com.lumira.file.upload.ZipSafetyValidator;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        UploadProperties.class,
        FileSecurityScanProperties.class,
        FileOcrProperties.class,
        com.lumira.file.infrastructure.security.SecurityProperties.class
})
@MapperScan(
        basePackageClasses = FileObjectMapper.class,
        annotationClass = Mapper.class
)
@Import({
        FileManagementAppService.class,
        UploadResourceSecurityInterceptor.class,
        FileOutboxMetricsService.class,
        FileOutboxRelay.class,
        FilePlatformEventPublisher.class,
        LoggingFileOutboxDispatcher.class,
        PlatformEventOutboxService.class,
        com.lumira.file.infrastructure.security.JwtTokenService.class,
        FileJwtAuthFilter.class,
        ClamAvFileSecurityScanEngine.class,
        DisabledFileOcrEngine.class,
        FileAiParseProcessor.class,
        FileOcrEngineSelector.class,
        FileOcrProcessor.class,
        FileProcessingMetrics.class,
        FileProcessingTaskRequestService.class,
        FileProcessingTaskService.class,
        FileSecurityScanEngineSelector.class,
        FileSecurityScanMetrics.class,
        FileSecurityScanProcessor.class,
        FileTextExtractionProcessor.class,
        FileThumbnailProcessor.class,
        InlineFileSecurityScanEngine.class,
        TesseractFileOcrEngine.class,
        SafeUrlValidator.class,
        DocumentUploadService.class,
        FileStorageMetrics.class,
        ImageUploadService.class,
        ZipSafetyValidator.class
})
public class FileRuntimeAssemblyConfiguration {

    @Bean(name = "fileDomainEventPublisher")
    FileDomainEventPublisher fileDomainEventPublisher(PlatformEventOutboxService platformEventOutboxService) {
        return new FileDomainEventPublisher(platformEventOutboxService);
    }
}
