import { request, type RequestOptions } from '@/services/common/request';
import type { AuditLogRecord, PagedResult } from '@/types/api';

export interface AuditLogQuery extends Record<string, unknown> {
  username?: string;
  tenantId?: number;
  loginType?: string;
  logType?: string;
  startTime?: string;
  endTime?: string;
  pageNo?: number;
  pageSize?: number;
}

export const auditService = {
  loginLogs: (params: AuditLogQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<AuditLogRecord>>('/v1/audit/login-logs', {
      method: 'GET',
      params,
      ...options,
    }),
  operationLogs: (params: AuditLogQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<AuditLogRecord>>('/v1/audit/operation-logs', {
      method: 'GET',
      params,
      ...options,
    }),
  summary: (options: RequestOptions = {}) =>
    request<{ loginCount: number; operationCount: number }>('/v1/audit/summary', {
      method: 'GET',
      ...options,
    }),
};
