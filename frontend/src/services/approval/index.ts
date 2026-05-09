import { request, type RequestOptions } from '@/services/common/request';
import type { ApprovalInstanceRecord, ApprovalTemplateRecord, ApprovalTaskRecord, PagedResult } from '@/types/api';

export interface ApprovalPageQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
  scope?: string;
}

export type ApprovalTemplatePayload = Pick<ApprovalTemplateRecord, 'templateName' | 'businessType' | 'description'> & {
  nodes: NonNullable<ApprovalTemplateRecord['nodes']>;
};

export interface ApprovalInstancePayload {
  businessType: string;
  businessId?: number;
  businessTitle: string;
  summary?: string;
  payloadJson?: string;
}

export const approvalService = {
  templates: (params: ApprovalPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<ApprovalTemplateRecord>>('/approvals/templates', { method: 'GET', params, ...options }),
  createTemplate: (payload: ApprovalTemplatePayload, options: RequestOptions = {}) =>
    request<ApprovalTemplateRecord>('/approvals/templates', { method: 'POST', data: payload, ...options }),
  updateTemplate: (id: number, payload: ApprovalTemplatePayload, options: RequestOptions = {}) =>
    request<ApprovalTemplateRecord>(`/approvals/templates/${id}`, { method: 'PUT', data: payload, ...options }),
  updateTemplateEnabled: (id: number, enabled: boolean, options: RequestOptions = {}) =>
    request<boolean>(`/approvals/templates/${id}/enabled`, { method: 'PATCH', data: { enabled }, ...options }),
  createInstance: (payload: ApprovalInstancePayload, options: RequestOptions = {}) =>
    request<ApprovalInstanceRecord>('/approvals/instances', { method: 'POST', data: payload, ...options }),
  instances: (params: ApprovalPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<ApprovalInstanceRecord>>('/approvals/instances', { method: 'GET', params, ...options }),
  instance: (id: number, options: RequestOptions = {}) =>
    request<ApprovalInstanceRecord>(`/approvals/instances/${id}`, { method: 'GET', ...options }),
  cancel: (id: number, options: RequestOptions = {}) =>
    request<ApprovalInstanceRecord>(`/approvals/instances/${id}/cancel`, { method: 'POST', ...options }),
  myPendingTasks: (params: ApprovalPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<ApprovalTaskRecord>>('/approvals/tasks/my-pending', { method: 'GET', params, ...options }),
  approve: (taskId: number, comment?: string, options: RequestOptions = {}) =>
    request<ApprovalInstanceRecord>(`/approvals/tasks/${taskId}/approve`, { method: 'POST', data: { comment }, ...options }),
  reject: (taskId: number, comment?: string, options: RequestOptions = {}) =>
    request<ApprovalInstanceRecord>(`/approvals/tasks/${taskId}/reject`, { method: 'POST', data: { comment }, ...options }),
};
