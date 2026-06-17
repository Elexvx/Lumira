package com.lumira.message.controller;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.app.PlatformEventOutboxService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/message")
public class MessageReadinessV2Controller {

    private final PlatformEventOutboxService platformEventOutboxService;
    private final MessageAppService messageAppService;

    public MessageReadinessV2Controller(
            PlatformEventOutboxService platformEventOutboxService,
            MessageAppService messageAppService
    ) {
        this.platformEventOutboxService = platformEventOutboxService;
        this.messageAppService = messageAppService;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "Message",
                "message-service",
                "READY_WITH_BLOCKERS",
                "contract-and-observability",
                List.of(
                        "msg_notice",
                        "msg_notice_read",
                        "msg_delivery_log",
                        "platform_event_outbox"
                ),
                List.of(
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
                ),
                List.of(
                        "MESSAGE_NOTICE_CREATED",
                        "MESSAGE_NOTICE_READ",
                        "MESSAGE_NOTICE_ARCHIVED",
                        "MESSAGE_NOTICE_RETRACTED",
                        "MESSAGE_SYNC_STATE"
                ),
                List.of(
                        "message.db.owner-tables",
                        "message.websocket.registry",
                        "message.outbox.dispatchable-backlog",
                        "message.unread-count.capped-query"
                ),
                List.of(
                        "message.list.p95",
                        "message.unread_count.p95",
                        "message.unread_count.cache_hit_ratio",
                        "message.unread_count.cache_hits",
                        "message.unread_count.cache_misses",
                        "message.read_all.p95",
                        "message.websocket.delivery.total",
                        "message.outbox.record.total",
                        "message.outbox.delivered.total",
                        "message.outbox.failed.total",
                        "message.outbox.replay.total",
                        "message.outbox.dispatchable_backlog",
                        "message.delivery_log.count.capped_total"
                ),
                List.of(
                        "IAM permission snapshot",
                        "Platform notification settings",
                        "Redis/WebSocket runtime",
                        "platform_event_outbox",
                        "job-executor internal relay"
                ),
                List.of(
                        "route /api/v2/message/* back to message-service monolith adapter",
                        "keep platform_event_outbox rows for replay by eventKey/id",
                        "disable message outbox relay handler and deliver directly from monolith if rollback is needed",
                        "preserve msg_notice and msg_notice_read owner writes in message-service"
                ),
                List.of(
                        "Message v2 adapter and owner observability contract are available; list/unread paths still rely on capped queries rather than a dedicated unread-counter projection",
                        "WebSocket cross-process delivery, dead-letter state and replay runbook need a runtime drill before physical split"
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Message",
                "message-service",
                "UP",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("message.db.owner-tables", "CONFIGURED", "Message owner tables are declared and guarded by architecture tests."),
                        healthCheck("message.websocket.registry", "CONFIGURED", "WebSocket registry is available for connection heartbeat and delivery."),
                        healthCheck("message.outbox.dispatchable-backlog", "CONFIGURED", "Message outbox dispatchable backlog is exposed as an owner gauge."),
                        healthCheck("message.unread-count.capped-query", "CONFIGURED", "Unread count uses a capped query and must not use an unbounded full-table count.")
                ),
                messageMetrics()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Message",
                "message-service",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                messageMetrics()
        ), TraceContext.getRequestId());
    }

    private List<OwnerObservabilityDTO.MetricDTO> messageMetrics() {
        MessageAppService.MetricsSnapshot snapshot = messageAppService.snapshotMetrics();
        return List.of(
                metric("message.list.p95", "timer", "milliseconds", "Message list p95 latency; computed by the metrics backend from the v2 list endpoint.", snapshot.listMessagesP95Millis()),
                metric("message.unread_count.p95", "timer", "milliseconds", "Unread count p95 latency; capped count path must stay on indexed owner queries.", snapshot.unreadCountP95Millis()),
                metric("message.archive.count.capped_total", "gauge", "times", "Archive count queries that reached bounded cap and should be re-run on deeper pages for full total.", snapshot.archiveCappedCountQueryTotal()),
                metric("message.delivery_log.count.capped_total", "gauge", "times", "Delivery log count queries that reached bounded cap and should be re-run on deeper pages for full total.", snapshot.deliveryLogCappedCountQueryTotal()),
                metric("message.read_all.p95", "timer", "milliseconds", "Mark-all-read p95 latency.", snapshot.readAllP95Millis()),
                metric("message.unread_count.cache_hit_ratio", "gauge", "ratio", "Unread count cache hit ratio.", snapshot.unreadCountCacheHitRatio()),
                metric("message.archive.count.cache_hit_ratio", "gauge", "ratio", "Archive total count cache hit ratio.", snapshot.archiveCountCacheHitRatio()),
                metric("message.delivery_log.count.cache_hit_ratio", "gauge", "ratio", "Delivery log total count cache hit ratio.", snapshot.deliveryLogCountCacheHitRatio()),
                metric("message.websocket.delivery.total", "counter", "events", "WebSocket delivery attempts tagged by event type/result."),
                metric("message.outbox.record.total", "counter", "events", "Message outbox records created after owner transactions commit."),
                metric("message.outbox.delivered.total", "counter", "events", "Message outbox events delivered by relay."),
                metric("message.outbox.failed.total", "counter", "events", "Message outbox delivery failures awaiting retry."),
                metric("message.outbox.replay.total", "counter", "events", "Message outbox manual replay attempts."),
                metric("message.unread_count.cache_hits", "counter", "times", "Unread count cache hits.", snapshot.unreadCountCacheHits()),
                metric("message.unread_count.cache_misses", "counter", "times", "Unread count cache misses.", snapshot.unreadCountCacheMisses()),
                metric("message.archive.count.cache_hits", "counter", "times", "Archive total count cache hits.", snapshot.archiveCountCacheHits()),
                metric("message.archive.count.cache_misses", "counter", "times", "Archive total count cache misses.", snapshot.archiveCountCacheMisses()),
                metric("message.delivery_log.count.cache_hits", "counter", "times", "Delivery log total count cache hits.", snapshot.deliveryLogCountCacheHits()),
                metric("message.delivery_log.count.cache_misses", "counter", "times", "Delivery log total count cache misses.", snapshot.deliveryLogCountCacheMisses()),
                metric("message.outbox.dispatchable_backlog", "gauge", "events", "Message outbox events ready for dispatch or retry.", platformEventOutboxService.countDispatchable())
        );
    }

    private OwnerObservabilityDTO.HealthCheckDTO healthCheck(String name, String status, String description) {
        return new OwnerObservabilityDTO.HealthCheckDTO(name, status, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description, long value) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description, (double) value);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description, double value) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description, value);
    }
}
