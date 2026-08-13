package com.lumira.saas.modules.competition.vo;

import java.util.List;

/** Public DTOs for the competition workspace shell. */
public final class CompetitionWorkspaceVO {
    private CompetitionWorkspaceVO() {
    }

    public static class Workspace {
        private String competitionUuid;
        private String competitionNo;
        private String code;
        private String title;
        private String status;
        private Long activeRegistrationCount;
        private List<String> capabilities = List.of();
        private List<String> allowedModules = List.of();

        public String getCompetitionUuid() { return competitionUuid; }
        public void setCompetitionUuid(String competitionUuid) { this.competitionUuid = competitionUuid; }
        public String getCompetitionNo() { return competitionNo; }
        public void setCompetitionNo(String competitionNo) { this.competitionNo = competitionNo; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getActiveRegistrationCount() { return activeRegistrationCount; }
        public void setActiveRegistrationCount(Long activeRegistrationCount) { this.activeRegistrationCount = activeRegistrationCount; }
        public List<String> getCapabilities() { return capabilities; }
        public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities); }
        public List<String> getAllowedModules() { return allowedModules; }
        public void setAllowedModules(List<String> allowedModules) { this.allowedModules = allowedModules == null ? List.of() : List.copyOf(allowedModules); }
    }
}
