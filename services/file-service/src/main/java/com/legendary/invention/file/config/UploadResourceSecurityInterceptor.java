package com.legendary.invention.file.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.Set;

@Component("fileUploadResourceSecurityInterceptor")
public class UploadResourceSecurityInterceptor implements HandlerInterceptor {

    private static final Set<String> INLINE_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "ico");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (!isInlineImage(request.getRequestURI())) {
            response.setHeader("Content-Disposition", "attachment");
        }
        return true;
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
}
