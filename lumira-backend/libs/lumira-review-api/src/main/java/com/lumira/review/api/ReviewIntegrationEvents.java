package com.lumira.review.api;

/**
 * Stable integration-event names shared by Review publishers and downstream
 * consumers. Constants in this API module are safe to reuse after Review is
 * deployed as a physical service.
 */
public final class ReviewIntegrationEvents {
    public static final String RESULTS_PUBLISHED = "COMPETITION_REVIEW_RESULTS_PUBLISHED";
    public static final String RESULT_PUBLISHED = "COMPETITION_REVIEW_RESULT_PUBLISHED";
    public static final String INVITATIONS_DISPATCHED = "COMPETITION_REVIEW_INVITATIONS_DISPATCHED";
    private ReviewIntegrationEvents() {
    }
}
