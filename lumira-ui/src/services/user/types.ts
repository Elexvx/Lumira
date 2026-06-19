import type { PagedResult, RoleRecord, UserDetail, UserRecord } from '@/types/api';

export interface UserListQuery extends Record<string, unknown> {
  keyword?: string;
  userId?: number;
  username?: string;
  mobile?: string;
  email?: string;
  deptId?: number;
  status?: string;
  source?: string;
  registeredStart?: string;
  registeredEnd?: string;
  lastLoginStart?: string;
  lastLoginEnd?: string;
  cursorId?: number;
  cursorCreatedAt?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface UserMutationPayload {
  username: string;
  mobile?: string;
  idCardNumber?: string;
  nickname?: string;
  realName?: string;
  birthMonth?: string;
  gender?: string;
  region?: string;
  availableTime?: string;
  email?: string;
  avatarUrl?: string;
  status: string;
  password?: string;
  roleIds?: number[];
  deptIds?: number[];
  primaryDeptId?: number | null;
}

export interface UserStatusPayload {
  status: string;
}

export type { PagedResult, RoleRecord, UserDetail, UserRecord };
