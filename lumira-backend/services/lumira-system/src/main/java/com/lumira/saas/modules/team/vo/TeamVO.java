package com.lumira.saas.modules.team.vo;

import java.time.LocalDateTime;

public final class TeamVO {
    private TeamVO() {
    }

    public static class Team {
        private Long id;
        private Long tenantId;
        private String teamCode;
        private String teamName;
        private String teamType;
        private String avatarUrl;
        private String description;
        private String visibility;
        private String joinMode;
        private Long ownerUserId;
        private Integer memberCount;
        private String status;
        private String myRole;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getTeamCode() { return teamCode; }
        public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public String getTeamType() { return teamType; }
        public void setTeamType(String teamType) { this.teamType = teamType; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
        public String getJoinMode() { return joinMode; }
        public void setJoinMode(String joinMode) { this.joinMode = joinMode; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Integer getMemberCount() { return memberCount; }
        public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMyRole() { return myRole; }
        public void setMyRole(String myRole) { this.myRole = myRole; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class Member {
        private Long id;
        private Long tenantId;
        private Long teamId;
        private Long userId;
        private String role;
        private String memberAlias;
        private String status;
        private Long invitedBy;
        private LocalDateTime joinedAt;
        private LocalDateTime createdAt;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getMemberAlias() { return memberAlias; }
        public void setMemberAlias(String memberAlias) { this.memberAlias = memberAlias; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getInvitedBy() { return invitedBy; }
        public void setInvitedBy(Long invitedBy) { this.invitedBy = invitedBy; }
        public LocalDateTime getJoinedAt() { return joinedAt; }
        public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class Invite {
        private Long id;
        private Long tenantId;
        private Long teamId;
        private String inviteCode;
        private String inviteType;
        private String roleOnJoin;
        private LocalDateTime expiresAt;
        private Integer maxUses;
        private Integer usedCount;
        private Boolean needApproval;
        private String status;
        private LocalDateTime createdAt;
        private String rawToken;
        private String inviteUrl;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public String getInviteCode() { return inviteCode; }
        public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
        public String getInviteType() { return inviteType; }
        public void setInviteType(String inviteType) { this.inviteType = inviteType; }
        public String getRoleOnJoin() { return roleOnJoin; }
        public void setRoleOnJoin(String roleOnJoin) { this.roleOnJoin = roleOnJoin; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        public Integer getMaxUses() { return maxUses; }
        public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
        public Integer getUsedCount() { return usedCount; }
        public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }
        public Boolean getNeedApproval() { return needApproval; }
        public void setNeedApproval(Boolean needApproval) { this.needApproval = needApproval; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public String getRawToken() { return rawToken; }
        public void setRawToken(String rawToken) { this.rawToken = rawToken; }
        public String getInviteUrl() { return inviteUrl; }
        public void setInviteUrl(String inviteUrl) { this.inviteUrl = inviteUrl; }
    }

    public static class JoinRequest {
        private Long id;
        private Long tenantId;
        private Long teamId;
        private Long userId;
        private Long inviteId;
        private String applyMessage;
        private String status;
        private Long reviewedBy;
        private LocalDateTime reviewedAt;
        private String reviewMessage;
        private LocalDateTime createdAt;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getInviteId() { return inviteId; }
        public void setInviteId(Long inviteId) { this.inviteId = inviteId; }
        public String getApplyMessage() { return applyMessage; }
        public void setApplyMessage(String applyMessage) { this.applyMessage = applyMessage; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getReviewedBy() { return reviewedBy; }
        public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
        public LocalDateTime getReviewedAt() { return reviewedAt; }
        public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
        public String getReviewMessage() { return reviewMessage; }
        public void setReviewMessage(String reviewMessage) { this.reviewMessage = reviewMessage; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class JoinResult {
        private String status;
        private Team team;
        private JoinRequest joinRequest;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Team getTeam() { return team; }
        public void setTeam(Team team) { this.team = team; }
        public JoinRequest getJoinRequest() { return joinRequest; }
        public void setJoinRequest(JoinRequest joinRequest) { this.joinRequest = joinRequest; }
    }
}
