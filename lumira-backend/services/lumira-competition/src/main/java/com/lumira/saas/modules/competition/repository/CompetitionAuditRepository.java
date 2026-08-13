package com.lumira.saas.modules.competition.repository;

import com.lumira.saas.modules.competition.vo.CompetitionAuditVO;
import java.util.List;

/** Persistence boundary for competition-scoped audit reads. */
public interface CompetitionAuditRepository {
    AuditPage findRecords(String competitionUuid, String module, long offset, long limit);

    record AuditPage(List<CompetitionAuditVO.Record> records, long total) {}
}
