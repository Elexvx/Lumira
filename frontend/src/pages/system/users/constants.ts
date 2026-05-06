import type { UserRecord } from '@/types/api';
import { PROTECTED_ADMIN_ID, PROTECTED_ADMIN_USERNAME, isProtectedAdminAccount as isProtectedAdminUserAccount } from '@/auth/admin';

export const GENDER_OPTIONS = [
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '其他', value: 'OTHER' },
];

export const USER_STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
];

export { PROTECTED_ADMIN_ID, PROTECTED_ADMIN_USERNAME };

export const isProtectedAdminAccount = (record?: Pick<UserRecord, 'id' | 'username'> | null) =>
  isProtectedAdminUserAccount(record ? { userId: record.id, username: record.username } : null);
