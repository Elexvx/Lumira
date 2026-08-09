package com.lumira.api.text;

import org.springframework.web.multipart.MultipartFile;

/**
 * Narrow cross-owner boundary for extracting user-supplied text before a
 * platform moderation policy is applied.
 *
 * <p>The platform owns the moderation policy. AI owns document parsing, so
 * callers must depend on this contract rather than an AI implementation.</p>
 */
public interface TextModerationPort {

    String extractText(MultipartFile file);
}
