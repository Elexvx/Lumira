package com.lumira.saas.modules.iam.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/** Stable, opaque session authorization version with independently fenced dimensions. */
final class AuthorizationVersionVector {

    static final String PREFIX = "authz-v1";

    private final String subject;
    private final long subjectVersion;
    private final long bindingVersion;
    private final Map<Long, Long> roleVersions;
    private final Map<Long, Long> dataPolicyVersions;

    AuthorizationVersionVector(
            String subject,
            long subjectVersion,
            long bindingVersion,
            Map<Long, Long> roleVersions,
            Map<Long, Long> dataPolicyVersions
    ) {
        this.subject = requireSubject(subject);
        this.subjectVersion = requireVersion(subjectVersion);
        this.bindingVersion = requireVersion(bindingVersion);
        this.roleVersions = normalize(roleVersions, false);
        this.dataPolicyVersions = normalize(dataPolicyVersions, true);
    }

    String subject() {
        return subject;
    }

    long subjectVersion() {
        return subjectVersion;
    }

    long bindingVersion() {
        return bindingVersion;
    }

    Map<Long, Long> roleVersions() {
        return roleVersions;
    }

    Map<Long, Long> dataPolicyVersions() {
        return dataPolicyVersions;
    }

    String encode() {
        return PREFIX
                + ";s=" + Base64.getUrlEncoder().withoutPadding().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + ":" + subjectVersion
                + ";b=" + bindingVersion
                + ";r=" + encodeVersions(roleVersions)
                + ";d=" + encodeVersions(dataPolicyVersions);
    }

    static AuthorizationVersionVector parse(String encoded) {
        if (encoded == null || !encoded.startsWith(PREFIX + ";")) {
            throw new IllegalArgumentException("unsupported authorization version");
        }
        String[] parts = encoded.split(";", -1);
        if (parts.length != 5) {
            throw new IllegalArgumentException("invalid authorization version");
        }
        String[] subjectParts = requirePrefix(parts[1], "s=").split(":", -1);
        if (subjectParts.length != 2) {
            throw new IllegalArgumentException("invalid subject version");
        }
        String subject = new String(Base64.getUrlDecoder().decode(subjectParts[0]), StandardCharsets.UTF_8);
        long subjectVersion = parseVersion(subjectParts[1]);
        long bindingVersion = parseVersion(requirePrefix(parts[2], "b="));
        Map<Long, Long> roleVersions = parseVersions(requirePrefix(parts[3], "r="), false);
        Map<Long, Long> dataPolicyVersions = parseVersions(requirePrefix(parts[4], "d="), true);
        return new AuthorizationVersionVector(subject, subjectVersion, bindingVersion, roleVersions, dataPolicyVersions);
    }

    private static String requirePrefix(String value, String prefix) {
        if (!value.startsWith(prefix)) {
            throw new IllegalArgumentException("invalid authorization version component");
        }
        return value.substring(prefix.length());
    }

    private static String encodeVersions(Map<Long, Long> versions) {
        StringJoiner joiner = new StringJoiner(",");
        versions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> joiner.add(entry.getKey() + ":" + entry.getValue()));
        return joiner.toString();
    }

    private static Map<Long, Long> parseVersions(String encoded, boolean allowZeroKey) {
        if (encoded.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new LinkedHashMap<>();
        for (String item : encoded.split(",", -1)) {
            String[] pair = item.split(":", -1);
            if (pair.length != 2) {
                throw new IllegalArgumentException("invalid authorization version map");
            }
            long id = Long.parseLong(pair[0]);
            if (id < 0 || (!allowZeroKey && id == 0) || result.put(id, parseVersion(pair[1])) != null) {
                throw new IllegalArgumentException("invalid authorization version key");
            }
        }
        return Map.copyOf(result);
    }

    private static Map<Long, Long> normalize(Map<Long, Long> versions, boolean allowZeroKey) {
        if (versions == null || versions.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new LinkedHashMap<>();
        versions.forEach((key, value) -> {
            if (key == null || key < 0 || (!allowZeroKey && key == 0) || value == null || value < 0) {
                throw new IllegalArgumentException("invalid authorization dimension version");
            }
            result.put(key, value);
        });
        return Map.copyOf(result);
    }

    private static long parseVersion(String value) {
        return requireVersion(Long.parseLong(value));
    }

    private static long requireVersion(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("authorization version must not be negative");
        }
        return value;
    }

    private static String requireSubject(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("authorization subject is invalid");
        }
        return value.trim();
    }
}
