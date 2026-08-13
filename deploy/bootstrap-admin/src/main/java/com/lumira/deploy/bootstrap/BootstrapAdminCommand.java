package com.lumira.deploy.bootstrap;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

public final class BootstrapAdminCommand {

    private static final int MAX_SECRET_BYTES = 4096;
    private static final Set<String> LOOPBACK_DATABASE_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1"
    );

    private BootstrapAdminCommand() {
    }

    public static void main(String[] args) {
        try {
            run(System.getenv());
        } catch (Exception exception) {
            System.err.println("Administrator credential bootstrap failed: " + safeMessage(exception));
            System.exit(1);
        }
    }

    static void run(java.util.Map<String, String> environment) throws Exception {
        String databaseUrl = require(environment, "DB_URL");
        String databaseUsername = require(environment, "DB_USERNAME");
        String databasePassword = requirePresent(environment, "DB_PASSWORD");
        String initializationSource = environment.getOrDefault(
                "LUMIRA_BOOTSTRAP_ADMIN_INITIALIZATION_SOURCE",
                AdminCredentialBootstrap.DEFAULT_INITIALIZATION_SOURCE
        );
        validateInitializationBoundary(databaseUrl, initializationSource);
        char[] bootstrapPassword = readSecret(environment.get("LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE"));
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword)) {
                AdminCredentialBootstrap.Outcome outcome =
                        new AdminCredentialBootstrap().execute(connection, bootstrapPassword, initializationSource);
                System.out.println("Administrator credential bootstrap outcome: " + outcome);
            }
        } finally {
            if (bootstrapPassword != null) {
                Arrays.fill(bootstrapPassword, '\0');
            }
        }
    }

    static char[] readSecret(String secretPath) throws Exception {
        if (secretPath == null || secretPath.isBlank()) {
            return null;
        }
        Path path = Path.of(secretPath);
        long size = Files.size(path);
        if (size <= 0 || size > MAX_SECRET_BYTES) {
            throw new IllegalArgumentException("Bootstrap administrator secret file must contain 1 to 4096 bytes");
        }
        byte[] encoded = Files.readAllBytes(path);
        char[] decoded = null;
        try {
            var characters = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(encoded));
            decoded = new char[characters.remaining()];
            characters.get(decoded);
            if (characters.hasArray()) {
                Arrays.fill(characters.array(), '\0');
            }

            int length = decoded.length;
            if (length >= 2 && decoded[length - 2] == '\r' && decoded[length - 1] == '\n') {
                length -= 2;
            } else if (length >= 1 && decoded[length - 1] == '\n') {
                length -= 1;
            }
            if (length == 0) {
                throw new IllegalArgumentException("Bootstrap administrator secret file is empty");
            }
            char[] secret = Arrays.copyOf(decoded, length);
            Arrays.fill(decoded, '\0');
            decoded = null;
            return secret;
        } finally {
            Arrays.fill(encoded, (byte) 0);
            if (decoded != null) {
                Arrays.fill(decoded, '\0');
            }
        }
    }

    static void validateInitializationBoundary(String databaseUrl, String initializationSource) {
        if (!"LOCAL_DEFAULT".equals(initializationSource)) {
            return;
        }
        try {
            URI endpoint = URI.create(databaseUrl.substring("jdbc:".length()));
            String host = endpoint.getHost();
            String normalizedHost = host == null
                    ? null
                    : host.replaceAll("^\\[|\\]$", "").toLowerCase(Locale.ROOT);
            if (!"mysql".equalsIgnoreCase(endpoint.getScheme())
                    || normalizedHost == null
                    || !LOOPBACK_DATABASE_HOSTS.contains(normalizedHost)) {
                throw new IllegalArgumentException(
                        "LOCAL_DEFAULT administrator initialization requires loopback MySQL"
                );
            }
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException
                    && "LOCAL_DEFAULT administrator initialization requires loopback MySQL".equals(exception.getMessage())) {
                throw exception;
            }
            throw new IllegalArgumentException(
                    "LOCAL_DEFAULT administrator initialization requires loopback MySQL",
                    exception
            );
        }
    }

    private static String require(java.util.Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String requirePresent(java.util.Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
