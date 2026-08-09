package com.lumira.api.project;

/** Project-owner read snapshot consumed by other bounded contexts. */
public record ProjectSnapshot(
        Long id,
        String code,
        String locale,
        String title,
        String category,
        String description,
        String imageUrl,
        String ownerName,
        String status,
        String tags
) {
}
