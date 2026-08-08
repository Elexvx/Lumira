package com.lumira.saas.modules.eventcatalog.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventCatalogEventTypes;
import com.lumira.api.event.EventCatalogEventWatermarkPort;
import com.lumira.api.event.EventCatalogItem;
import com.lumira.api.event.EventCatalogPage;
import com.lumira.api.event.EventCatalogProjectionEvent;
import com.lumira.api.event.EventCatalogProjectionHandler;
import com.lumira.api.event.EventCatalogQueryPort;
import com.lumira.api.event.EventCatalogSourceSnapshot;
import com.lumira.api.event.EventCatalogSourceSnapshotPort;
import com.lumira.api.event.EventConsumptionPort;
import com.lumira.saas.modules.eventcatalog.repository.EventCatalogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class EventCatalogAppService implements EventCatalogProjectionHandler, EventCatalogQueryPort {

    public static final String CONSUMER_NAME = "event-catalog-projection";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int REBUILD_BATCH_SIZE = 500;
    private static final List<String> SUPPORTED_SOURCES = List.of("ACTIVITY", "COMPETITION");

    private final ObjectMapper objectMapper;
    private final EventCatalogRepository repository;
    private final EventConsumptionPort eventConsumptionPort;
    private final EventCatalogEventWatermarkPort watermarkPort;
    private final Map<String, EventCatalogSourceSnapshotPort> sourcePorts;
    private final Counter appliedCounter;
    private final Counter failureCounter;
    private final Counter rebuildCounter;
    private final Timer projectionDelay;

    public EventCatalogAppService(
            ObjectMapper objectMapper,
            EventCatalogRepository repository,
            EventConsumptionPort eventConsumptionPort,
            EventCatalogEventWatermarkPort watermarkPort,
            List<EventCatalogSourceSnapshotPort> sourcePorts,
            MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.eventConsumptionPort = eventConsumptionPort;
        this.watermarkPort = watermarkPort;
        this.sourcePorts = indexSourcePorts(sourcePorts);
        this.appliedCounter = Counter.builder("event.catalog.projection.applied").register(meterRegistry);
        this.failureCounter = Counter.builder("event.catalog.projection.failed").register(meterRegistry);
        this.rebuildCounter = Counter.builder("event.catalog.projection.rebuild").register(meterRegistry);
        this.projectionDelay = Timer.builder("event.catalog.projection.delay").register(meterRegistry);
    }

    @Override
    public boolean handle(EventCatalogProjectionEvent event) {
        try {
            CatalogPayload payload = parse(event);
            boolean applied = eventConsumptionPort.executeOnce(
                    new EventConsumptionPort.EventIdentity(
                            CONSUMER_NAME,
                            Long.toString(event.outboxSequence()),
                            event.eventType(),
                            payload.sourceType(),
                            String.valueOf(payload.sourceId())
                    ),
                    () -> repository.apply(payload.toWrite(event.outboxSequence()))
            );
            if (applied) {
                appliedCounter.increment();
                recordDelay(payload.sourceUpdatedAt());
            }
            return applied;
        } catch (RuntimeException exception) {
            failureCounter.increment();
            throw exception;
        }
    }

    @Override
    public EventCatalogPage listPublished(
            String keyword,
            String sourceType,
            String locale,
            Boolean featured,
            long pageNo,
            long pageSize
    ) {
        String normalizedSource = normalizeOptionalSource(sourceType);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(MAX_PAGE_SIZE, pageSize));
        EventCatalogRepository.PageData page = repository.findPublished(new EventCatalogRepository.CatalogSearch(
                trimToNull(keyword),
                normalizedSource,
                trimToNull(locale),
                featured,
                (normalizedPageNo - 1L) * normalizedPageSize,
                normalizedPageSize
        ));
        return new EventCatalogPage(
                page.records(),
                page.total(),
                normalizedPageNo,
                normalizedPageSize,
                normalizedPageNo * normalizedPageSize < page.total()
        );
    }

    @Transactional
    public int rebuildSource(String sourceType) {
        String normalizedSource = requireSource(sourceType);
        EventCatalogSourceSnapshotPort sourcePort = sourcePorts.get(normalizedSource);
        if (sourcePort == null) {
            throw new IllegalArgumentException("No catalog rebuild source is registered for " + normalizedSource);
        }

        // Read the watermark first: any owner write that commits after it has a
        // greater outbox id and will be accepted after this snapshot replacement.
        long watermark = Math.max(0L, watermarkPort.currentWatermark());
        List<EventCatalogSourceSnapshot> snapshots = new ArrayList<>();
        long offset = 0L;
        while (true) {
            List<EventCatalogSourceSnapshot> batch = sourcePort.loadCatalogSnapshots(offset, REBUILD_BATCH_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            snapshots.addAll(batch);
            offset += batch.size();
            if (batch.size() < REBUILD_BATCH_SIZE) {
                break;
            }
        }
        repository.replaceSource(normalizedSource, snapshots, watermark);
        rebuildCounter.increment();
        return snapshots.size();
    }

    private CatalogPayload parse(EventCatalogProjectionEvent event) {
        if (!EventCatalogEventTypes.CATALOG_ITEM_UPSERTED.equals(event.eventType())
                && !EventCatalogEventTypes.CATALOG_ITEM_WITHDRAWN.equals(event.eventType())
                && !EventCatalogEventTypes.CATALOG_ITEM_ARCHIVED.equals(event.eventType())) {
            throw new IllegalArgumentException("Unsupported catalog event type: " + event.eventType());
        }
        try {
            JsonNode attributes = objectMapper.readTree(event.payloadJson()).path("attributes");
            if (!attributes.isObject()) {
                throw new IllegalArgumentException("Catalog event attributes are required");
            }
            String sourceType = requireSource(text(attributes, "sourceType"));
            Long sourceId = positiveLong(attributes, "sourceId");
            String status = switch (event.eventType()) {
                case EventCatalogEventTypes.CATALOG_ITEM_WITHDRAWN -> "withdrawn";
                case EventCatalogEventTypes.CATALOG_ITEM_ARCHIVED -> "archived";
                default -> requireText(text(attributes, "status"), "status");
            };
            return new CatalogPayload(
                    sourceType,
                    sourceId,
                    text(attributes, "sourceUuid"),
                    text(attributes, "locale"),
                    requireText(text(attributes, "title"), "title"),
                    text(attributes, "subtitle"),
                    text(attributes, "summary"),
                    status,
                    text(attributes, "registrationStart"),
                    text(attributes, "registrationEnd"),
                    text(attributes, "eventStart"),
                    text(attributes, "eventEnd"),
                    text(attributes, "eventTime"),
                    text(attributes, "location"),
                    text(attributes, "imageUrl"),
                    text(attributes, "tags"),
                    text(attributes, "ctaLabel"),
                    text(attributes, "ctaHref"),
                    attributes.path("featured").asBoolean(false),
                    attributes.path("sort").canConvertToInt() ? attributes.path("sort").asInt() : 100,
                    localDateTime(attributes, "sourceUpdatedAt")
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Catalog event payload is invalid", exception);
        }
    }

    private Map<String, EventCatalogSourceSnapshotPort> indexSourcePorts(List<EventCatalogSourceSnapshotPort> ports) {
        Map<String, EventCatalogSourceSnapshotPort> indexed = new LinkedHashMap<>();
        if (ports == null) {
            return indexed;
        }
        for (EventCatalogSourceSnapshotPort port : ports) {
            String sourceType = requireSource(port.sourceType());
            if (indexed.putIfAbsent(sourceType, port) != null) {
                throw new IllegalStateException("Duplicate catalog rebuild source: " + sourceType);
            }
        }
        return indexed;
    }

    private void recordDelay(LocalDateTime sourceUpdatedAt) {
        if (sourceUpdatedAt == null) {
            return;
        }
        long millis = Math.max(0L, Duration.between(sourceUpdatedAt.toInstant(ZoneOffset.UTC), java.time.Instant.now()).toMillis());
        projectionDelay.record(Duration.ofMillis(millis));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : trimToNull(value.asText(null));
    }

    private static Long positiveLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        long parsed = value.canConvertToLong() ? value.asLong() : 0L;
        if (parsed <= 0L) {
            throw new IllegalArgumentException(field + " is required");
        }
        return parsed;
    }

    private static LocalDateTime localDateTime(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDateTime.parse(value);
    }

    private static String requireSource(String sourceType) {
        String normalized = requireText(sourceType, "sourceType").toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SOURCES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported catalog source: " + sourceType);
        }
        return normalized;
    }

    private static String normalizeOptionalSource(String sourceType) {
        return StringUtils.hasText(sourceType) ? requireSource(sourceType) : null;
    }

    private static String requireText(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record CatalogPayload(
            String sourceType,
            Long sourceId,
            String sourceUuid,
            String locale,
            String title,
            String subtitle,
            String summary,
            String status,
            String registrationStart,
            String registrationEnd,
            String eventStart,
            String eventEnd,
            String eventTime,
            String location,
            String imageUrl,
            String tags,
            String ctaLabel,
            String ctaHref,
            boolean featured,
            int sort,
            LocalDateTime sourceUpdatedAt
    ) {
        EventCatalogRepository.CatalogWrite toWrite(long outboxSequence) {
            return new EventCatalogRepository.CatalogWrite(
                    sourceType,
                    sourceId,
                    sourceUuid,
                    locale,
                    title,
                    subtitle,
                    summary,
                    status,
                    registrationStart,
                    registrationEnd,
                    eventStart,
                    eventEnd,
                    eventTime,
                    location,
                    imageUrl,
                    tags,
                    ctaLabel,
                    ctaHref,
                    featured,
                    sort,
                    outboxSequence,
                    sourceUpdatedAt
            );
        }
    }
}
