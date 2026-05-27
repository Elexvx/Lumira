import { request, type RequestOptions } from '@/services/common/request';
import type { AuditLogRecord, PagedResult } from '@/types/api';

export interface AuditLogQuery extends Record<string, unknown> {
  username?: string;
  tenantId?: number;
  employeeId?: number;
  skillCode?: string;
  resultStatus?: string;
  channel?: string;
  scene?: string;
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
  aiCallLogs: (params: AuditLogQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<AuditLogRecord>>('/v1/audit/ai-call-logs', {
      method: 'GET',
      params,
      ...options,
    }),
  verificationLogs: (params: AuditLogQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<AuditLogRecord>>('/v1/audit/verification-logs', {
      method: 'GET',
      params,
      ...options,
    }),
  summary: (options: RequestOptions = {}) =>
    request<{ loginCount: number; operationCount: number; aiCallCount?: number }>('/v1/audit/summary', {
      method: 'GET',
      ...options,
    }),
};
