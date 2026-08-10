package com.lumira.file.upload;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Resolves an optional, untrusted upload directory inside a configured storage space. */
public final class FileUploadDirectory {
    private static final int MAX_DIRECTORY_LENGTH = 180;
    private static final int MAX_SEGMENTS = 8;
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private FileUploadDirectory() {
    }

    public static Scope resolve(Path storageRoot, String publicPath, String directory) {
        Path normalizedStorageRoot = storageRoot.toAbsolutePath().normalize();
        String normalizedDirectory = normalize(directory);
        if (!StringUtils.hasText(normalizedDirectory)) {
            return new Scope(normalizedStorageRoot, trimTrailingSlash(publicPath), null);
        }
        Path scopedStorageRoot = normalizedStorageRoot.resolve(normalizedDirectory).normalize();
        if (!scopedStorageRoot.startsWith(normalizedStorageRoot)) {
            throw invalidDirectory();
        }
        return new Scope(
                scopedStorageRoot,
                trimTrailingSlash(publicPath) + "/" + normalizedDirectory,
                normalizedDirectory
        );
    }

    public static String qualifyObjectKey(String directory, String relativePath) {
        if (!StringUtils.hasText(directory)) {
            return relativePath;
        }
        return directory + "/" + relativePath;
    }

    static String normalize(String directory) {
        if (!StringUtils.hasText(directory)) {
            return null;
        }
        String normalized = directory.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (normalized.length() > MAX_DIRECTORY_LENGTH || normalized.startsWith("/") || normalized.endsWith("/")) {
            throw invalidDirectory();
        }
        String[] rawSegments = normalized.split("/", -1);
        if (rawSegments.length > MAX_SEGMENTS) {
            throw invalidDirectory();
        }
        List<String> segments = new ArrayList<>(rawSegments.length);
        for (String segment : rawSegments) {
            if (!SAFE_SEGMENT.matcher(segment).matches()) {
                throw invalidDirectory();
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    private static String trimTrailingSlash(String publicPath) {
        String normalized = StringUtils.hasText(publicPath) ? publicPath.trim() : "";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static BizException invalidDirectory() {
        return new BizException(ErrorCode.BAD_REQUEST, "文件存储目录无效", "文件存储目录无效");
    }

    public record Scope(Path storageRoot, String publicPath, String directory) {
    }
}
