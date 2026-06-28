export type ExpertStatus = 'active' | 'inactive';
export type ExpertApprovalStatus = 'PENDING' | 'RUNNING' | 'APPROVED' | 'REJECTED';

export interface ExpertRecord {
  id: number;
  code: string;
  name: string;
  title?: string | null;
  organization?: string | null;
  position?: string | null;
  expertise: string;
  phone?: string | null;
  mobile?: string | null;
  idCardNumber?: string | null;
  userId?: number | null;
  accountStatus?: string | null;
  initialPasswordResetRequired?: boolean | null;
  initialPassword?: string | null;
  email?: string | null;
  avatarUrl?: string | null;
  bio?: string | null;
  tags?: string | null;
  status: ExpertStatus;
  approvalStatus?: ExpertApprovalStatus | null;
  approvalInstanceId?: number | null;
  sort: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface ExpertUpsertPayload {
  code?: string;
  name: string;
  title?: string;
  organization?: string;
  position?: string;
  expertise: string;
  phone?: string;
  mobile?: string;
  idCardNumber?: string;
  email?: string;
  avatarUrl?: string;
  bio?: string;
  tags?: string;
  status: ExpertStatus;
  sort?: number;
}

export interface ExpertQueryParams {
  keyword?: string;
  status?: ExpertStatus;
  approvalStatus?: ExpertApprovalStatus;
  pageNo?: number;
  pageSize?: number;
}

export interface PageResponse<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
  hasMore?: boolean;
}
