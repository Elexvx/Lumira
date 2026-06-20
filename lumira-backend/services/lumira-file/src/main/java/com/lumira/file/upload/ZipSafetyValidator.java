package com.lumira.file.upload;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.file.config.UploadProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
        int entries = 0;
        long totalUncompressed = 0L;
        boolean hasContentTypes = false;
        boolean hasExpectedDirectory = false;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries++;
                if (entries > uploadProperties.getZipMaxEntries()) {
                    throw blocked();
                }
                validateEntryName(entry.getName());
                if ("[Content_Types].xml".equals(entry.getName())) {
                    hasContentTypes = true;
                }
                if (entry.getName().startsWith(expectedDirectory)) {
                    hasExpectedDirectory = true;
                }
                long entryBytes = 0L;
                int read;
                while ((read = zipInputStream.read(buffer)) != -1) {
                    entryBytes += read;
                    totalUncompressed += read;
                    if (entryBytes > uploadProperties.getZipMaxSingleEntryBytes()
                            || totalUncompressed > uploadProperties.getZipMaxUncompressedBytes()) {
                        throw blocked();
                    }
                }
            }
        } catch (BizException exception) {
            throw exception;
        } catch (IOException exception) {
            throw blocked();
        }
        if (!hasContentTypes || !hasExpectedDirectory) {
            throw blocked();
        }
        if (bytes.length > 0 && totalUncompressed / Math.max(1L, bytes.length) > uploadProperties.getZipMaxCompressionRatio()) {
            throw blocked();
        }
    }

    private void validateEntryName(String name) {
        if (!StringUtils.hasText(name)) {
            throw blocked();
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.contains("../")
                || normalized.contains("/..")
                || Path.of(normalized).isAbsolute()) {
            throw blocked();
        }
    }

    private BizException blocked() {
        return new BizException(ErrorCode.BAD_REQUEST, "Unsafe zip document rejected", "Document file is invalid or unsafe");
    }
}
