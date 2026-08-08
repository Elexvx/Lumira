package com.lumira.file.domain;

import org.springframework.util.StringUtils;

/** Security-gated lifecycle states for stored file content. */
public final class FileObjectSecurityStatus {

    public static final String PENDING_SCAN = "PENDING_SCAN";
    public static final String CLEAN = "CLEAN";
    public static final String REJECTED = "REJECTED";
    public static final String FAILED = "FAILED";
    public static final String LEGACY_ENABLED = "ENABLED";

    private FileObjectSecurityStatus() {
    }

    /** Existing ENABLED rows predate the scan gate and remain readable for compatibility. */
    public static boolean isContentAccessible(String status) {
        return StringUtils.hasText(status)
                && (CLEAN.equalsIgnoreCase(status) || LEGACY_ENABLED.equalsIgnoreCase(status));
    }
}
