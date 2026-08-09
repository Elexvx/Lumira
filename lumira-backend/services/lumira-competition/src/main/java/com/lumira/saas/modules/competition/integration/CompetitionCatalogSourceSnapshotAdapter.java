package com.lumira.saas.modules.competition.integration;

import com.lumira.api.event.EventCatalogSourceSnapshot;
import com.lumira.api.event.EventCatalogSourceSnapshotPort;
import com.lumira.saas.modules.competition.repository.CompetitionManagementRepository;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import java.util.List;

/** Competition-owned rebuild adapter exposed only through the common catalog port. */
public class CompetitionCatalogSourceSnapshotAdapter implements EventCatalogSourceSnapshotPort {

    private final CompetitionManagementRepository competitionManagementRepository;

    public CompetitionCatalogSourceSnapshotAdapter(CompetitionManagementRepository competitionManagementRepository) {
        this.competitionManagementRepository = competitionManagementRepository;
    }

    @Override
    public String sourceType() {
        return "COMPETITION";
    }

    @Override
    public List<EventCatalogSourceSnapshot> loadCatalogSnapshots(long offset, int limit) {
        return competitionManagementRepository.findCompetitions(
                        new CompetitionManagementRepository.CompetitionSearch(null, null, null, null, null, offset, limit)
                )
                .records()
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    private EventCatalogSourceSnapshot toSnapshot(CompetitionVO.Competition competition) {
        return new EventCatalogSourceSnapshot(
                sourceType(),
                competition.getId(),
                competition.getUuid(),
                competition.getLocale(),
                competition.getTitle(),
                competition.getShortName(),
                competition.getDescription(),
                competition.getStatus(),
                competition.getRegistrationStart(),
                competition.getRegistrationEnd(),
                competition.getCompetitionStart(),
                competition.getCompetitionEnd(),
                null,
                competition.getLocation(),
                competition.getImageUrl(),
                competition.getTags(),
                null,
                null,
                Boolean.TRUE.equals(competition.getFeatured()),
                competition.getSort() == null ? 100 : competition.getSort(),
                competition.getUpdatedAt()
        );
    }
}
