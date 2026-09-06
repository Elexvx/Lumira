package com.lumira.deploy.pluginmigration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.Map;

public final class PluginMigrationCommand {
    private PluginMigrationCommand() {
    }

    public static void main(String[] args) {
        try {
            run(args, System.getenv());
        } catch (Exception exception) {
            System.err.println("Central plugin migrator failed: " + safeMessage(exception));
            System.exit(1);
        }
    }

    static void run(String[] args, Map<String, String> environment) throws Exception {
        String mode = args.length == 0 ? "execute" : args[0].trim().toLowerCase();
        if (!"execute".equals(mode) && !"approve".equals(mode)) {
            throw new IllegalArgumentException("mode must be execute or approve");
        }
        String databaseUrl = require(environment, "DB_URL");
        String databaseUsername = require(environment, "DB_USERNAME");
        String databasePassword = requirePresent(environment, "DB_PASSWORD");
        String expectedUsername = environment.getOrDefault("PLUGIN_MIGRATOR_EXPECTED_DB_USERNAME", "lumira_migrator").trim();
        if (!databaseUsername.equals(expectedUsername)) {
            throw new IllegalArgumentException("central plugin migrator requires the dedicated " + expectedUsername + " database identity");
        }
        String releaseId = require(environment, "PLUGIN_MIGRATION_RELEASE_ID");
        PluginMigrationExecutor executor = new PluginMigrationExecutor(
                new PluginMigrationRepository(), new PluginMigrationSafetyValidator(new ObjectMapper()),
                faultInjector(environment));
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword)) {
            if ("approve".equals(mode)) {
                PluginMigrationExecutor.Approval approval = new PluginMigrationExecutor.Approval(
                        positiveLong(environment, "PLUGIN_MIGRATION_REQUEST_ID"),
                        positiveLong(environment, "PLUGIN_MIGRATION_OPERATION_EPOCH"),
                        requireDigest(environment, "PLUGIN_MIGRATION_PACKAGE_DIGEST"),
                        requireDigest(environment, "PLUGIN_MIGRATION_DIGEST"),
                        releaseId,
                        boundedRequired(environment, "PLUGIN_MIGRATION_APPROVER", 128),
                        boundedRequired(environment, "PLUGIN_MIGRATION_APPROVAL_REASON", 512)
                );
                executor.approve(connection, approval);
                System.out.println("Plugin migration request approved with full fence: requestId=" + approval.requestId());
                return;
            }
            String executorId = boundedRequired(environment, "PLUGIN_MIGRATION_EXECUTOR_ID", 128);
            int limit = boundedInt(environment.get("PLUGIN_MIGRATION_BATCH_LIMIT"), 25, 1, 100);
            int leaseSeconds = boundedInt(environment.get("PLUGIN_MIGRATION_LEASE_SECONDS"),
                    PluginMigrationExecutor.DEFAULT_LEASE_SECONDS, 30, 86_400);
            PluginMigrationExecutor.ExecutionSummary summary = executor.executeApproved(
                    connection, releaseId, executorId, limit, leaseSeconds);
            System.out.println("Central plugin migrator outcome: approved=" + summary.approved()
                    + " claimed=" + summary.claimed() + " succeeded=" + summary.succeeded());
        }
    }

    private static long positiveLong(Map<String, String> environment, String key) {
        try {
            long value = Long.parseLong(require(environment, key));
            if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a positive integer", exception);
        }
    }

    private static int boundedInt(String value, int fallback, int min, int max) {
        return boundedInt(value, fallback, min, max, "PLUGIN_MIGRATION_BATCH_LIMIT");
    }

    private static int boundedInt(String value, int fallback, int min, int max, String key) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) throw new IllegalArgumentException(key + " is outside allowed range");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static PluginMigrationFaultInjector faultInjector(Map<String, String> environment) {
        String faultPoint = environment.getOrDefault("PLUGIN_MIGRATION_FAULT_INJECTION", "NONE")
                .trim().toUpperCase(Locale.ROOT);
        return switch (faultPoint) {
            case "NONE" -> PluginMigrationFaultInjector.noOp();
            case "AFTER_DDL_BEFORE_VERIFY" -> PluginMigrationFaultInjector.delayedAfterDdl(
                    boundedInt(environment.get("PLUGIN_MIGRATION_FAULT_INJECTION_DELAY_MS"),
                            30_000, 1, 120_000, "PLUGIN_MIGRATION_FAULT_INJECTION_DELAY_MS"));
            default -> throw new IllegalArgumentException(
                    "PLUGIN_MIGRATION_FAULT_INJECTION must be NONE or AFTER_DDL_BEFORE_VERIFY");
        };
    }

    private static String requireDigest(Map<String, String> environment, String key) {
        String value = require(environment, key).toLowerCase();
        if (!value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException(key + " must be a SHA-256 digest");
        return value;
    }

    private static String boundedRequired(Map<String, String> environment, String key, int max) {
        String value = require(environment, key);
        if (value.length() > max) throw new IllegalArgumentException(key + " exceeds " + max + " characters");
        return value;
    }

    private static String require(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value.trim();
    }

    private static String requirePresent(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
