package com.lumira.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaymentServiceSqlHotPathTest {

    @Test
    void hotPathSqlInOutboxServiceShouldUseDeleteAwareDispatchFiltersAndBoundedLimit() throws Exception {
        String source = serviceSource("src/main/java/com/lumira/payment/service/PaymentOutboxService.java");

        assertThat(source).contains(normalizeSql("select id, tenant_id as tenantId"));
        assertThat(source).contains(normalizeSql("from payment_event_outbox"));
        assertThat(source).contains(normalizeSql("where deleted = 0"));
        assertThat(source).contains(normalizeSql("source_type = ?"));
        assertThat(source).contains(normalizeSql("status = ? or (status = ? and (next_retry_at is null or next_retry_at <= ?)"));
        assertThat(source).contains(normalizeSql("order by created_at asc, id asc"));
        assertThat(source).contains(normalizeSql("limit ?"));
        assertThat(source).contains(normalizeSql("sum(case when status = 'PENDING' then 1 else 0 end"));
        assertThat(source).contains(normalizeSql("sum(case when status = 'FAILED' then 1 else 0 end"));
        assertThat(source).contains(normalizeSql("sum(case when status = 'DEAD_LETTER' then 1 else 0 end"));
    }

    @Test
    void hotPathSqlInWebhookServiceShouldUseReplaySafeLookupAndOrderLimits() throws Exception {
        String source = serviceSource("src/main/java/com/lumira/payment/service/PaymentWebhookService.java");

        assertThat(source).contains(normalizeSql("where tenant_id = ? and provider_code = ? and nonce = ? and deleted = 0 and received_at >= ?"));
        assertThat(source).contains(normalizeSql("where tenant_id = ? and provider_code = ? and event_id = ? and deleted = 0"));
        assertThat(source).contains(normalizeSql("order by id desc"));
        assertThat(source).contains(normalizeSql("limit 1"));
    }

    @Test
    void hotPathSqlInManagementServiceShouldHonorSoftDeleteReadWithStableOrdering() throws Exception {
        String source = serviceSource("src/main/java/com/lumira/payment/service/PaymentManagementAppService.java");

        assertThat(source).contains(normalizeSql("where tenant_id = ? and provider_code = ? and deleted = 0"));
        assertThat(source).contains(normalizeSql("order by id desc"));
        assertThat(source).contains(normalizeSql("update payment_provider_config"));
        assertThat(source).contains(normalizeSql("insert into payment_provider_config"));
    }

    @Test
    void paymentHotPathMigrationShouldIntroduceOutboxAndWebhookIndexes() throws Exception {
        String migrationSql = migrationSql();

        assertThat(migrationSql).contains("idx_payment_outbox_deleted_status_retry_created");
        assertThat(migrationSql).contains("idx_payment_outbox_deleted_status");
        assertThat(migrationSql).contains("idx_payment_webhook_event_tenant_provider_nonce_deleted_received");
        assertThat(migrationSql).contains("idx_payment_webhook_event_tenant_provider_event_deleted_id");
        assertThat(migrationSql).contains("idx_payment_provider_config_tenant_provider_deleted_id");
    }

    @Test
    void paymentOutboxOwnerQueueMigrationShouldIntroduceSourceBoundedQueueIndex() throws Exception {
        Path path = resolvePath("src/main/resources/db/migration/payment/V30__payment_outbox_owner_queue_index.sql");
        if (!Files.exists(path)) {
            path = Path.of("services/lumira-payment/src/main/resources/db/migration/payment/V30__payment_outbox_owner_queue_index.sql");
        }
        assertThat(Files.exists(path)).as("payment outbox owner-queue migration exists").isTrue();

        String sql = Files.readString(path, StandardCharsets.UTF_8);
        assertThat(sql).contains("idx_payment_outbox_owner_queue");
        assertThat(sql).contains("deleted");
        assertThat(sql).contains("source_type");
        assertThat(sql).contains("status");
        assertThat(sql).contains("next_retry_at");
        assertThat(sql).contains("created_at");
        assertThat(sql).contains("id");
    }

    private static String serviceSource(String relativePath) throws IOException {
        Path path = resolvePath(relativePath);
        assertThat(Files.exists(path)).as("service source exists").isTrue();
        return normalizeSql(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static String migrationSql() throws IOException {
        Path path = resolvePath("src/main/resources/db/migration/payment/V29__payment_hot_path_indexes.sql");
        if (!Files.exists(path)) {
            path = Path.of("services/lumira-payment/src/main/resources/db/migration/payment/V29__payment_hot_path_indexes.sql");
        }
        assertThat(Files.exists(path)).as("payment hot-path migration exists").isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path resolvePath(String relativePath) {
        Path pathFromRepoRoot = Path.of("services/lumira-payment", relativePath);
        if (Files.exists(pathFromRepoRoot)) {
            return pathFromRepoRoot;
        }
        return Path.of(relativePath);
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
