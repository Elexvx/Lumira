package com.lumira.api.expert;

import java.util.List;

/** Owner-backed expert eligibility lookup; callers never read Expert persistence directly. */
public interface ExpertSnapshotPort {

    ExpertSnapshot findExpertSnapshot(Long expertId);

    List<ExpertSnapshot> listApprovedForReview();
}
