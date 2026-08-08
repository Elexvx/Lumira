package com.lumira.saas.modules.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReviewBatchStatusTest {

    @Test
    void followsTheControlledReviewLifecycle() {
        assertThat(ReviewBatchStatus.DRAFT.canTransitionTo(ReviewBatchStatus.READY)).isTrue();
        assertThat(ReviewBatchStatus.READY.canTransitionTo(ReviewBatchStatus.ASSIGNING)).isTrue();
        assertThat(ReviewBatchStatus.ASSIGNING.canTransitionTo(ReviewBatchStatus.IN_REVIEW)).isTrue();
        assertThat(ReviewBatchStatus.IN_REVIEW.canTransitionTo(ReviewBatchStatus.AGGREGATING)).isTrue();
        assertThat(ReviewBatchStatus.AGGREGATING.canTransitionTo(ReviewBatchStatus.FINALIZED)).isTrue();
        assertThat(ReviewBatchStatus.FINALIZED.canTransitionTo(ReviewBatchStatus.PUBLISHED)).isTrue();
        assertThat(ReviewBatchStatus.PUBLISHED.canTransitionTo(ReviewBatchStatus.ARCHIVED)).isTrue();
    }

    @Test
    void rejectsSkippingFinalizeBeforePublication() {
        assertThatThrownBy(() ->
                ReviewBatchStatus.IN_REVIEW.requireTransitionTo(ReviewBatchStatus.PUBLISHED)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_REVIEW -> PUBLISHED");
    }

    @Test
    void permitsAggregateReworkWithoutChangingSubmittedSheets() {
        assertThat(ReviewBatchStatus.AGGREGATING.canTransitionTo(ReviewBatchStatus.IN_REVIEW)).isTrue();
    }
}
