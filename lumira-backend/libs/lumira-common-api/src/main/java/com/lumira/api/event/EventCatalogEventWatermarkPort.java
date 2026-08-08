package com.lumira.api.event;

/** Supplies a durable event high-water mark so source rebuilds can reject stale deliveries. */
public interface EventCatalogEventWatermarkPort {

    long currentWatermark();
}
