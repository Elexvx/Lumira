package com.lumira.file.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumira.file.entity.FileObjectEntity;
import com.lumira.file.entity.FileStorageSpaceEntity;
import com.lumira.file.mapper.FileObjectMapper;
import com.lumira.file.mapper.FileStorageSpaceMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component("fileUploadResourceSecurityInterceptor")
public class UploadResourceSecurityInterceptor implements HandlerInterceptor {

    private static final Set<String> INLINE_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "ico");
    private static final String DOWNLOAD_CENTER_PUBLIC_PREFIX = "/api/uploads/download_center/";
    private static final String PUBLIC_UPLOAD_PREFIX = "/api/uploads/";
    private static final String VISIBILITY_SCOPE_PUBLIC = "PUBLIC";

    private final FileObjectMapper fileObjectMapper;
    private final FileStorageSpaceMapper fileStorageSpaceMapper;

    public UploadResourceSecurityInterceptor(FileObjectMapper fileObjectMapper, FileStorageSpaceMapper fileStorageSpaceMapper) {
        this.fileObjectMapper = fileObjectMapper;
        this.fileStorageSpaceMapper = fileStorageSpaceMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        response.setHeader("X-Content-Type-Options", "nosniff");
        String requestUri = request.getRequestURI();
        if (isDownloadCenterResource(requestUri) || !isPublicFileObject(requestUri)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        if (!isInlineImage(requestUri)) {
            response.setHeader("Content-Disposition", "attachment");
        }
        return true;
    }

    private boolean isDownloadCenterResource(String requestUri) {
        return requestUri != null && requestUri.startsWith(DOWNLOAD_CENTER_PUBLIC_PREFIX);
    }

    private boolean isInlineImage(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        int extensionStart = requestUri.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == requestUri.length() - 1) {
            return false;
        }
        String extension = requestUri.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
        return INLINE_IMAGE_EXTENSIONS.contains(extension);
    }

    private boolean isPublicFileObject(String requestUri) {
        if (requestUri == null || !requestUri.startsWith(PUBLIC_UPLOAD_PREFIX)) {
            return false;
        }
        String normalizedPublicUrl = normalizePublicUrl(requestUri);
        if (!StringUtils.hasText(normalizedPublicUrl)) {
            return false;
        }
        FileObjectEntity file = fileObjectMapper.selectOne(new LambdaQueryWrapper<FileObjectEntity>()
                .eq(FileObjectEntity::getPublicUrl, normalizedPublicUrl)
                .eq(FileObjectEntity::getVisibilityScope, VISIBILITY_SCOPE_PUBLIC)
                .eq(FileObjectEntity::getDeleted, 0)
                .last("limit 1"));
        if (file == null || !StringUtils.hasText(file.getBucket())) {
            return false;
        }
        FileStorageSpaceEntity storageSpace = fileStorageSpaceMapper.findByStorageKey(file.getTenantId(), file.getBucket());
        return storageSpace != null
                && storageSpace.getAnonymousAccessAllowed() != null
                && storageSpace.getAnonymousAccessAllowed() == 1
                && "ENABLED".equalsIgnoreCase(storageSpace.getStatus());
    }

    private String normalizePublicUrl(String requestUri) {
        String decoded = UriUtils.decode(requestUri, StandardCharsets.UTF_8);
        if (!decoded.startsWith(PUBLIC_UPLOAD_PREFIX) || decoded.contains("..") || decoded.contains("\\")) {
            return null;
        }
        return decoded;
    }
}
