package com.lumira.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumira.message.app.MessageAppService;
import com.lumira.message.app.PlatformEventOutboxService;
import org.junit.jupiter.api.Test;

class MessageReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposeMessageSplitGateContract() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        when(outboxService.countDispatchable()).thenReturn(9L);
        MessageAppService messageAppService = mock(MessageAppService.class);
        when(messageAppService.snapshotMetrics()).thenReturn(new MessageAppService.MetricsSnapshot(4.8, 1.2, 2.6, 7L, 5L, 11L, 3L, 0.79, 4L, 1L, 0.8, 6L, 2L, 0.75));
        MessageReadinessV2Controller controller = new MessageReadinessV2Controller(outboxService, messageAppService);

        var response = controller.readiness();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        var readiness = response.getData();
        assertThat(readiness.context()).isEqualTo("Message");
        assertThat(readiness.ownerModule()).isEqualTo("message-service");
        assertThat(readiness.status()).isEqualTo("READY_WITH_BLOCKERS");
        assertThat(readiness.ownerTablePatterns())
                .contains("msg_notice", "msg_notice_read", "msg_delivery_log", "platform_event_outbox");
        assertThat(readiness.apiContracts())
                .contains(
                        "/api/v2/message/readiness",
                        "/api/v2/message/health",
                        "/api/v2/message/metrics",
                        "/api/v2/message/messages",
                        "/api/v2/message/archive",
                        "/api/v2/message/delivery-logs",
                        "/api/v2/message/unread-count",
                        "/api/v2/message/read-all",
                        "/message/internal/jobs/outbox/relay",
                        "/message/internal/jobs/outbox/{id}/replay"
                );
        assertThat(readiness.eventContracts())
                .contains("MESSAGE_NOTICE_CREATED", "MESSAGE_SYNC_STATE");
        assertThat(readiness.healthChecks())
                .contains("message.outbox.dispatchable-backlog", "message.unread-count.capped-query");
        assertThat(readiness.metrics())
                .contains("message.outbox.dispatchable_backlog", "message.list.p95", "message.unread_count.p95");
        assertThat(readiness.blockers())
                .anySatisfy(blocker -> assertThat(blocker).contains("dead-letter"));

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("message.db.owner-tables", "message.websocket.registry", "message.outbox.dispatchable-backlog");

        var metrics = controller.metrics().getData();
        assertThat(metrics.status()).isEqualTo("METRICS_DECLARED");
        assertThat(metrics.metrics())
                .extracting(metric -> metric.name())
                .contains(
                        "message.outbox.dispatchable_backlog",
                        "message.outbox.record.total",
                        "message.unread_count.cache_hit_ratio",
                        "message.archive.count.cache_hit_ratio",
                        "message.delivery_log.count.cache_hit_ratio",
                        "message.archive.count.capped_total",
                        "message.delivery_log.count.capped_total"
                );
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("message.outbox.dispatchable_backlog");
                    assertThat(metric.value()).isEqualTo(9.0);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("message.list.p95");
                    assertThat(metric.value()).isEqualTo(4.8);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("message.unread_count.p95");
                    assertThat(metric.value()).isEqualTo(1.2);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("message.delivery_log.count.capped_total");
                    assertThat(metric.value()).isEqualTo(5.0);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("message.read_all.p95");
                    assertThat(metric.value()).isEqualTo(2.6);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("message.unread_count.cache_hits");
                    assertThat(metric.value()).isEqualTo(11.0);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("message.unread_count.cache_hit_ratio");
                    assertThat(metric.value()).isEqualTo(0.79);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("message.archive.count.cache_hit_ratio");
                    assertThat(metric.value()).isEqualTo(0.8);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("message.delivery_log.count.cache_hit_ratio");
                    assertThat(metric.value()).isEqualTo(0.75);
                });
    }
}
