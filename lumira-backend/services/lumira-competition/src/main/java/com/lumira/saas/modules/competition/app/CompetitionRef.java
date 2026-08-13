package com.lumira.saas.modules.competition.app;

import com.lumira.saas.modules.competition.vo.CompetitionVO;

/** Internal request-scoped reference to a competition. */
public record CompetitionRef(
        Long id,
        String uuid,
        String competitionNo,
        String code,
        String title,
        String status
) {
    public static CompetitionRef from(CompetitionVO.Competition competition) {
        if (competition == null) {
            return null;
        }
        return new CompetitionRef(
                competition.getId(),
                competition.getUuid(),
                competition.getCompetitionNo(),
                competition.getCode(),
                competition.getTitle(),
                competition.getStatus()
        );
    }

    public boolean archived() {
        return "archived".equalsIgnoreCase(status);
    }
}
