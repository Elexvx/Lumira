package com.lumira.saas.modules.expert.integration;

import com.lumira.api.expert.ExpertSnapshot;
import com.lumira.api.expert.ExpertSnapshotPort;
import com.lumira.saas.modules.expert.repository.ExpertRepository;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import java.util.List;

/** Expert-owned implementation of Competition's narrow assignment eligibility boundary. */
public class ExpertSnapshotPortAdapter implements ExpertSnapshotPort {
    private static final long REVIEW_SNAPSHOT_LIMIT = 10_000L;

    private final ExpertRepository expertRepository;

    public ExpertSnapshotPortAdapter(ExpertRepository expertRepository) {
        this.expertRepository = expertRepository;
    }

    @Override
    public ExpertSnapshot findExpertSnapshot(Long expertId) {
        if (expertId == null || expertId <= 0) {
            return null;
        }
        return expertRepository.findById(expertId).map(this::toSnapshot).orElse(null);
    }

    @Override
    public List<ExpertSnapshot> listApprovedForReview() {
        return expertRepository.search(null, "active", "APPROVED", 0L, REVIEW_SNAPSHOT_LIMIT).records().stream()
                .map(this::toSnapshot)
                .toList();
    }

    private ExpertSnapshot toSnapshot(ExpertVO.Expert expert) {
        return new ExpertSnapshot(
                expert.getId(), expert.getUserId(), expert.getUserUuid(), expert.getName(), expert.getEmail(),
                expert.getStatus(), expert.getApprovalStatus(), expert.getAccountStatus()
        );
    }
}
