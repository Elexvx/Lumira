package com.lumira.saas.modules.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class TeamDTO {
    private TeamDTO() {
    }

    public static class TeamCreateRequest {
        @NotBlank
        @Size(max = 128)
        private String teamName;
        @Size(max = 32)
        private String teamType;
        @Size(max = 512)
        private String avatarUrl;
        @Size(max = 1000)
        private String description;
        @Size(max = 32)
        private String visibility;
        @Size(max = 32)
        private String joinMode;

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
    }

    public static class TeamUpdateRequest extends TeamCreateRequest {
    }

    public static class MemberRoleRequest {
        @NotBlank
        private String role;
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public static class TransferOwnerRequest {
        private Long memberId;
        private String previousOwnerRole;
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public String getPreviousOwnerRole() { return previousOwnerRole; }
        public void setPreviousOwnerRole(String previousOwnerRole) { this.previousOwnerRole = previousOwnerRole; }
    }

    public static class InviteCreateRequest {
        @Size(max = 64)
        private String inviteCode;
        @Size(max = 32)
        private String inviteType;
        @Size(max = 32)
        private String roleOnJoin;
        private LocalDateTime expiresAt;
        private Integer maxUses;
        private Boolean needApproval;
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
        public Boolean getNeedApproval() { return needApproval; }
        public void setNeedApproval(Boolean needApproval) { this.needApproval = needApproval; }
    }

    public static class InviteTokenRequest {
        private String token;
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    public static class InviteCodeJoinRequest {
        private String inviteCode;
        public String getInviteCode() { return inviteCode; }
        public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    }

    public static class JoinRequestCreateRequest {
        @Size(max = 1000)
        private String applyMessage;
        public String getApplyMessage() { return applyMessage; }
        public void setApplyMessage(String applyMessage) { this.applyMessage = applyMessage; }
    }

    public static class JoinReviewRequest {
        @Size(max = 1000)
        private String reviewMessage;
        public String getReviewMessage() { return reviewMessage; }
        public void setReviewMessage(String reviewMessage) { this.reviewMessage = reviewMessage; }
    }
}
