package com.lumira.saas.infrastructure.pagination;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KeysetCursorCodec {

    private static final String SEPARATOR = "|";

    public String encode(Instant sortTime, Long id) {
        if (sortTime == null || id == null) {
            return null;
        }
        String payload = sortTime.toEpochMilli() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", 2);
            if (parts.length != 2) {
                return null;
            }
            return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public record Cursor(Instant sortTime, Long id) {
    }
}
