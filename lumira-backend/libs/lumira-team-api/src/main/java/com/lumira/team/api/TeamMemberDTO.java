package com.lumira.team.api;

import java.time.LocalDateTime;

public class TeamMemberDTO {
    private Long id;
    private Long teamId;
    private Long userId;
    private String role;
    private String status;
    private String extraValuesJson;
    private LocalDateTime joinedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExtraValuesJson() { return extraValuesJson; }
    public void setExtraValuesJson(String extraValuesJson) { this.extraValuesJson = extraValuesJson; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
