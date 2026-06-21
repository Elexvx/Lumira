export type TeamRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'MEMBER';
export type TeamType = 'GENERAL' | 'DEV' | 'COMPETITION' | 'CLUB' | 'OTHER';
export type TeamVisibility = 'PRIVATE' | 'PUBLIC';
export type TeamJoinMode = 'INVITE_ONLY' | 'APPLY' | 'OPEN';

export interface TeamRecord {
  id: number;
  tenantId: number;
  teamCode: string;
  teamName: string;
  teamType: TeamType;
  avatarUrl?: string | null;
  description?: string | null;
  visibility: TeamVisibility;
  joinMode: TeamJoinMode;
  ownerUserId: number;
  memberCount: number;
  status: string;
  myRole?: TeamRole | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface TeamMemberRecord {
  id: number;
  tenantId: number;
  teamId: number;
  userId: number;
  role: TeamRole;
  memberAlias?: string | null;
  status: string;
  invitedBy?: number | null;
  joinedAt?: string | null;
  createdAt?: string;
}

export interface TeamInviteRecord {
  id: number;
  tenantId: number;
  teamId: number;
  inviteCode?: string | null;
  inviteType: string;
  roleOnJoin: TeamRole;
  expiresAt?: string | null;
  maxUses?: number | null;
  usedCount: number;
  needApproval: boolean;
  status: string;
  createdAt?: string;
  rawToken?: string;
  inviteUrl?: string;
}

export interface TeamJoinRequestRecord {
  id: number;
  tenantId: number;
  teamId: number;
  userId: number;
  inviteId?: number | null;
  applyMessage?: string | null;
  status: string;
  reviewedBy?: number | null;
  reviewedAt?: string | null;
  reviewMessage?: string | null;
  createdAt?: string;
}

export interface TeamJoinResult {
  status: 'JOINED' | 'PENDING';
  team?: TeamRecord;
  joinRequest?: TeamJoinRequestRecord;
}

export interface TeamUpsertPayload {
  teamName: string;
  teamType?: TeamType;
  avatarUrl?: string;
  description?: string;
  visibility?: TeamVisibility;
  joinMode?: TeamJoinMode;
}

export interface TeamInviteCreatePayload {
  inviteCode?: string;
  roleOnJoin?: TeamRole;
  expiresAt?: string;
  maxUses?: number;
  needApproval?: boolean;
}
