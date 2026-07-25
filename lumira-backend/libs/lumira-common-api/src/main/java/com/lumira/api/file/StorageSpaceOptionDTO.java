package com.lumira.api.file;

public record StorageSpaceOptionDTO(
        String title,
        String storageKey,
        Boolean defaultStorage
) {
}
