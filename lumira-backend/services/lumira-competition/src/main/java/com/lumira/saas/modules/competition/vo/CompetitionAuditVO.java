package com.lumira.saas.modules.competition.vo;

import java.time.LocalDateTime;

/** Read model for the competition-scoped configuration audit trail. */
public final class CompetitionAuditVO {
    private CompetitionAuditVO() {
    }

    public static class Record {
        private Long id;
        private String competitionUuid;
        private Long operatorUserId;
        private String operatorUserUuid;
        private String action;
        private String module;
        private String detailMessage;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCompetitionUuid() { return competitionUuid; }
        public void setCompetitionUuid(String competitionUuid) { this.competitionUuid = competitionUuid; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getOperatorUserUuid() { return operatorUserUuid; }
        public void setOperatorUserUuid(String operatorUserUuid) { this.operatorUserUuid = operatorUserUuid; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        public String getDetailMessage() { return detailMessage; }
        public void setDetailMessage(String detailMessage) { this.detailMessage = detailMessage; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}
