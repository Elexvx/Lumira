import { request } from '@/services/common/request';
import type {
  TeamInviteCreatePayload,
  TeamInviteRecord,
  TeamJoinRequestRecord,
  TeamJoinResult,
  TeamMemberRecord,
  TeamRecord,
  TeamRole,
  TeamUpsertPayload,
} from './types';

export const listMyTeams = () => request<TeamRecord[]>('/v2/teams/my');
export const listAllTeams = () => request<TeamRecord[]>('/v2/admin/teams');
export const createTeam = (data: TeamUpsertPayload) => request<TeamRecord>('/v2/teams', { method: 'POST', data });
export const getTeam = (teamId: number) => request<TeamRecord>(`/v2/teams/${teamId}`);
export const updateTeam = (teamId: number, data: TeamUpsertPayload) => request<TeamRecord>(`/v2/teams/${teamId}`, { method: 'PUT', data });
export const adminUpdateTeam = (teamId: number, data: TeamUpsertPayload) =>
  request<TeamRecord>(`/v2/admin/teams/${teamId}`, { method: 'PUT', data });
export const deleteTeam = (teamId: number) => request<boolean>(`/v2/teams/${teamId}`, { method: 'DELETE' });
export const adminDeleteTeam = (teamId: number) => request<boolean>(`/v2/admin/teams/${teamId}`, { method: 'DELETE' });

export const listTeamMembers = (teamId: number) => request<TeamMemberRecord[]>(`/v2/teams/${teamId}/members`);
export const updateTeamMemberRole = (teamId: number, memberId: number, role: TeamRole) =>
  request<TeamMemberRecord>(`/v2/teams/${teamId}/members/${memberId}/role`, { method: 'PATCH', data: { role } });
export const removeTeamMember = (teamId: number, memberId: number) =>
  request<boolean>(`/v2/teams/${teamId}/members/${memberId}`, { method: 'DELETE' });
export const leaveTeam = (teamId: number) => request<boolean>(`/v2/teams/${teamId}/leave`, { method: 'POST' });
export const transferTeamOwner = (teamId: number, memberId: number, previousOwnerRole: TeamRole = 'ADMIN') =>
  request<TeamRecord>(`/v2/teams/${teamId}/transfer-owner`, { method: 'POST', data: { memberId, previousOwnerRole } });

export const createTeamInvite = (teamId: number, data: TeamInviteCreatePayload) =>
  request<TeamInviteRecord>(`/v2/teams/${teamId}/invites`, { method: 'POST', data });
export const listTeamInvites = (teamId: number) => request<TeamInviteRecord[]>(`/v2/teams/${teamId}/invites`);
export const disableTeamInvite = (teamId: number, inviteId: number) =>
  request<boolean>(`/v2/teams/${teamId}/invites/${inviteId}/disable`, { method: 'PATCH' });
export const previewTeamInvite = (token: string) =>
  request<TeamInviteRecord>('/v2/team-invites/preview', { method: 'POST', data: { token }, autoRedirectOnUnauthorized: false });
export const joinTeamByToken = (token: string) => request<TeamJoinResult>('/v2/team-invites/join', { method: 'POST', data: { token } });
export const joinTeamByCode = (inviteCode: string) =>
  request<TeamJoinResult>('/v2/team-invites/join-by-code', { method: 'POST', data: { inviteCode } });

export const createTeamJoinRequest = (teamId: number, applyMessage?: string) =>
  request<TeamJoinResult>(`/v2/teams/${teamId}/join-requests`, { method: 'POST', data: { applyMessage } });
export const listTeamJoinRequests = (teamId: number) => request<TeamJoinRequestRecord[]>(`/v2/teams/${teamId}/join-requests`);
export const approveTeamJoinRequest = (teamId: number, requestId: number, reviewMessage?: string) =>
  request<TeamJoinRequestRecord>(`/v2/teams/${teamId}/join-requests/${requestId}/approve`, { method: 'POST', data: { reviewMessage } });
export const rejectTeamJoinRequest = (teamId: number, requestId: number, reviewMessage?: string) =>
  request<TeamJoinRequestRecord>(`/v2/teams/${teamId}/join-requests/${requestId}/reject`, { method: 'POST', data: { reviewMessage } });
