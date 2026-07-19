package com.lumira.file.upload;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.file.config.UploadProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ZipSafetyValidator {

    private final UploadProperties uploadProperties;

    public ZipSafetyValidator(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public void validateOpenXmlPackage(byte[] bytes, String expectedDirectory) {
        validateZipPackage(bytes, expectedDirectory, "Document file is invalid or unsafe");
    }

    public void validateArchive(byte[] bytes) {
        validateZipPackage(bytes, null, "压缩包无效或不安全");
    }

    private void validateZipPackage(byte[] bytes, String expectedDirectory, String userMessage) {
        int entries = 0;
        long totalUncompressed = 0L;
        boolean hasContentTypes = false;
        boolean hasExpectedDirectory = false;
        long maxSingleEntryBytes = StringUtils.hasText(expectedDirectory)
                ? uploadProperties.getZipMaxSingleEntryBytes()
                : uploadProperties.getZipMaxUncompressedBytes();
        byte[] buffer = new byte[8192];
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries++;
                if (entries > uploadProperties.getZipMaxEntries()) {
                    throw blocked(userMessage);
                }
                validateEntryName(entry.getName(), userMessage);
                if ("[Content_Types].xml".equals(entry.getName())) {
                    hasContentTypes = true;
                }
                if (StringUtils.hasText(expectedDirectory) && entry.getName().startsWith(expectedDirectory)) {
                    hasExpectedDirectory = true;
                }
                long entryBytes = 0L;
                int read;
                while ((read = zipInputStream.read(buffer)) != -1) {
                    entryBytes += read;
                    totalUncompressed += read;
                    if (entryBytes > maxSingleEntryBytes
                            || totalUncompressed > uploadProperties.getZipMaxUncompressedBytes()) {
                        throw blocked(userMessage);
                    }
                }
            }
        } catch (BizException exception) {
            throw exception;
        } catch (IOException exception) {
            throw blocked(userMessage);
        }
        if (entries == 0 || (StringUtils.hasText(expectedDirectory) && (!hasContentTypes || !hasExpectedDirectory))) {
            throw blocked(userMessage);
        }
        if (bytes.length > 0 && totalUncompressed / Math.max(1L, bytes.length) > uploadProperties.getZipMaxCompressionRatio()) {
            throw blocked(userMessage);
        }
    }

    private void validateEntryName(String name, String userMessage) {
        if (!StringUtils.hasText(name)) {
            throw blocked(userMessage);
        }
        String normalized = name.replace('\\', '/');
        boolean absolute;
        try {
            absolute = Path.of(normalized).isAbsolute();
        } catch (InvalidPathException exception) {
            throw blocked(userMessage);
        }
        if (normalized.startsWith("/")
                || normalized.contains("../")
                || normalized.contains("/..")
                || absolute) {
            throw blocked(userMessage);
        }
    }

    private BizException blocked(String userMessage) {
        return new BizException(ErrorCode.BAD_REQUEST, "Unsafe zip file rejected", userMessage);
    }
}
