package com.lumira.deploy.bootstrap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;

public final class BootstrapAdminCommand {

    private static final int MAX_SECRET_BYTES = 4096;

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
        char[] bootstrapPassword = readSecret(environment.get("LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE"));
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword)) {
                AdminCredentialBootstrap.Outcome outcome =
                        new AdminCredentialBootstrap().execute(connection, bootstrapPassword);
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
