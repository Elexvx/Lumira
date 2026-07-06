package com.lumira.common.web;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false")
public class FileInternalClientConfiguration {

    private static final String INTERNAL_TOKEN_HEADER = "X-Job-Token";

    @Bean
    @Lazy
    @ConditionalOnMissingBean(FileInternalApi.class)
    public FileInternalApi remoteFileInternalApi(
            @Value("${saas.file.service-base-url:${FILE_SERVICE_BASE_URL:${saas.job.file-service-base-url:${SAAS_JOB_FILE_SERVICE_BASE_URL:http://localhost:8080}}}}") String fileServiceBaseUrl,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileToken,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider
    ) {
        if (!StringUtils.hasText(fileToken)) {
            throw new IllegalStateException("saas.internal.file-token is required");
        }
        RestClient.Builder builder = restClientBuilderProvider.getIfAvailable(RestClient::builder).clone()
                .baseUrl(TrustedServiceBaseUrlValidator.requireHttpBaseUrl(fileServiceBaseUrl, "saas.file.service-base-url"))
                .defaultHeader(INTERNAL_TOKEN_HEADER, fileToken.trim())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json");
        return new RemoteFileInternalApi(builder.build());
    }

    private static final class RemoteFileInternalApi implements FileInternalApi {

        private final RestClient restClient;

        private RemoteFileInternalApi(RestClient restClient) {
            this.restClient = restClient;
        }

        @Override
        public FileObjectDTO uploadImage(MultipartFile file, String category, String remark, String bucket) {
            return postMultipart("/internal/files/images", file, category, null, remark, bucket, null, null, null);
        }

        @Override
        public FileObjectDTO uploadImageForUser(
                MultipartFile file,
                String category,
                String remark,
                String bucket,
                Long userId,
                String userUuid,
                String username
        ) {
            return postMultipart("/internal/files/images/as-user", file, category, null, remark, bucket, userId, userUuid, username);
        }

        @Override
        public FileObjectDTO uploadDocument(MultipartFile file, String category, String tags, String remark, String bucket) {
            return postMultipart("/internal/files/documents", file, category, tags, remark, bucket, null, null, null);
        }

        @Override
        public FileObjectDTO uploadDocumentForUser(
                MultipartFile file,
                String category,
                String tags,
                String remark,
                String bucket,
                Long userId,
                String userUuid,
                String username
        ) {
            return postMultipart("/internal/files/documents/as-user", file, category, tags, remark, bucket, userId, userUuid, username);
        }

        @Override
        public FileContentDTO readFileContentForUser(Long fileId, Long userId, String userUuid, String username, boolean sharedScope) {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/files/content")
                            .queryParam("fileId", fileId)
                            .queryParam("userId", userId)
                            .queryParam("userUuid", userUuid)
                            .queryParam("username", username)
                            .queryParam("sharedScope", sharedScope)
                            .build())
                    .retrieve()
                    .body(FileContentDTO.class);
        }

        @Override
        public FileObjectDTO getFileForUser(
                Long fileId,
                Long userId,
                String userUuid,
                String username,
                boolean sharedScope,
                boolean downloadCenterScope
        ) {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/files/metadata")
                            .queryParam("fileId", fileId)
                            .queryParam("userId", userId)
                            .queryParam("userUuid", userUuid)
                            .queryParam("username", username)
                            .queryParam("sharedScope", sharedScope)
                            .queryParam("downloadCenterScope", downloadCenterScope)
                            .build())
                    .retrieve()
                    .body(FileObjectDTO.class);
        }

        @Override
        public List<FileObjectDTO> searchFilesForUser(
                Long userId,
                String userUuid,
                String username,
                String keyword,
                String contentType,
                String status,
                boolean sharedScope,
                int limit
        ) {
            FileObjectDTO[] files = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/internal/files/search")
                                .queryParam("userId", userId)
                                .queryParam("userUuid", userUuid)
                                .queryParam("username", username)
                                .queryParam("sharedScope", sharedScope)
                                .queryParam("limit", limit);
                        if (StringUtils.hasText(keyword)) {
                            uriBuilder.queryParam("keyword", keyword);
                        }
                        if (StringUtils.hasText(contentType)) {
                            uriBuilder.queryParam("contentType", contentType);
                        }
                        if (StringUtils.hasText(status)) {
                            uriBuilder.queryParam("status", status);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(FileObjectDTO[].class);
            if (files == null || files.length == 0) {
                return Collections.emptyList();
            }
            return List.of(files);
        }

        @Override
        public FileProcessingArtifactDTO readProcessingArtifactForUser(
                Long fileId,
                Long userId,
                String userUuid,
                String username,
                String artifactType,
                boolean sharedScope
        ) {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/files/artifacts")
                            .queryParam("fileId", fileId)
                            .queryParam("userId", userId)
                            .queryParam("userUuid", userUuid)
                            .queryParam("username", username)
                            .queryParam("artifactType", artifactType)
                            .queryParam("sharedScope", sharedScope)
                            .build())
                    .retrieve()
                    .body(FileProcessingArtifactDTO.class);
        }

        private FileObjectDTO postMultipart(
                String path,
                MultipartFile file,
                String category,
                String tags,
                String remark,
                String bucket,
                Long userId,
                String userUuid,
                String username
        ) {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody(file, category, tags, remark, bucket, userId, userUuid, username))
                    .retrieve()
                    .body(FileObjectDTO.class);
        }

        private MultiValueMap<String, Object> multipartBody(
                MultipartFile file,
                String category,
                String tags,
                String remark,
                String bucket,
                Long userId,
                String userUuid,
                String username
        ) {
            LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource(file));
            addTextPart(body, "category", category);
            addTextPart(body, "tags", tags);
            addTextPart(body, "remark", remark);
            addTextPart(body, "bucket", bucket);
            if (userId != null) {
                body.add("userId", String.valueOf(userId));
            }
            addTextPart(body, "userUuid", userUuid);
            addTextPart(body, "username", username);
            return body;
        }

        private void addTextPart(MultiValueMap<String, Object> body, String name, String value) {
            if (StringUtils.hasText(value)) {
                body.add(name, value);
            }
        }

        private HttpEntity<ByteArrayResource> fileResource(MultipartFile file) {
            try {
                ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return file.getOriginalFilename();
                    }
                };
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(resolveFileContentType(file));
                return new HttpEntity<>(resource, headers);
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to read multipart file content", ex);
            }
        }

        private MediaType resolveFileContentType(MultipartFile file) {
            if (!StringUtils.hasText(file.getContentType())) {
                return MediaType.APPLICATION_OCTET_STREAM;
            }
            MediaType mediaType = MediaType.parseMediaType(file.getContentType());
            return mediaType == null ? MediaType.APPLICATION_OCTET_STREAM : mediaType;
        }
    }
}
