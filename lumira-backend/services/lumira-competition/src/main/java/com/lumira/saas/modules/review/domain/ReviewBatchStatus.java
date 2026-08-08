package com.lumira.saas.modules.review.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ReviewBatchStatus {
    DRAFT,
    READY,
    ASSIGNING,
    IN_REVIEW,
    AGGREGATING,
    FINALIZED,
    PUBLISHED,
    ARCHIVED;

    private static final Map<ReviewBatchStatus, Set<ReviewBatchStatus>> TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(READY),
            READY, EnumSet.of(ASSIGNING),
            ASSIGNING, EnumSet.of(IN_REVIEW),
            IN_REVIEW, EnumSet.of(AGGREGATING),
            AGGREGATING, EnumSet.of(IN_REVIEW, FINALIZED),
            FINALIZED, EnumSet.of(PUBLISHED),
            PUBLISHED, EnumSet.of(ARCHIVED),
            ARCHIVED, EnumSet.noneOf(ReviewBatchStatus.class)
    );

    public boolean canTransitionTo(ReviewBatchStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }

    public void requireTransitionTo(ReviewBatchStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Invalid review batch transition: " + this + " -> " + target);
        }
    }
}
