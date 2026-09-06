package com.lumira.api.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable SHA-256 representation used by integration-event payload contracts. */
public final class EventPayloadDigests {

    private EventPayloadDigests() {
    }

    public static String sha256(String canonicalPayload) {
        if (canonicalPayload == null) {
            throw new IllegalArgumentException("canonicalPayload is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
