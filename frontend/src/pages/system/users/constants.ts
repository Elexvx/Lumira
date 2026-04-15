import type { UserRecord } from '@/types/api';

export const GENDER_OPTIONS = [
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '其他', value: 'OTHER' },
];

export const USER_STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
];

export const PROTECTED_ADMIN_ID = 1001;
export const PROTECTED_ADMIN_USERNAME = 'admin';

export const isProtectedAdminAccount = (record?: Pick<UserRecord, 'id' | 'username'> | null) =>
  Boolean(record && (record.id === PROTECTED_ADMIN_ID || record.username?.toLowerCase() === PROTECTED_ADMIN_USERNAME));
