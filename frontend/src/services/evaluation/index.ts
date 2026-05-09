import { request, type RequestOptions } from '@/services/common/request';
import type { EvaluationInstanceRecord, EvaluationTemplateRecord, EvaluationScoreTaskRecord, PagedResult } from '@/types/api';

export interface EvaluationPageQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
  objectType?: string;
}

export type EvaluationTemplatePayload = Pick<EvaluationTemplateRecord, 'templateName' | 'objectType' | 'description'> & {
  dimensions: NonNullable<EvaluationTemplateRecord['dimensions']>;
  gradeRules: NonNullable<EvaluationTemplateRecord['gradeRules']>;
};

export interface EvaluationInstancePayload {
  templateId: number;
  objectId?: number;
  objectTitle: string;
  scorerUserIds: number[];
  reviewerUserId?: number;
}

export interface EvaluationScorePayload {
  details: Array<{ dimensionId: number; score: number; comment?: string }>;
  comment?: string;
}

export const evaluationService = {
  templates: (params: EvaluationPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<EvaluationTemplateRecord>>('/evaluations/templates', { method: 'GET', params, ...options }),
  createTemplate: (payload: EvaluationTemplatePayload, options: RequestOptions = {}) =>
    request<EvaluationTemplateRecord>('/evaluations/templates', { method: 'POST', data: payload, ...options }),
  updateTemplate: (id: number, payload: EvaluationTemplatePayload, options: RequestOptions = {}) =>
    request<EvaluationTemplateRecord>(`/evaluations/templates/${id}`, { method: 'PUT', data: payload, ...options }),
  updateTemplateEnabled: (id: number, enabled: boolean, options: RequestOptions = {}) =>
    request<boolean>(`/evaluations/templates/${id}/enabled`, { method: 'PATCH', data: { enabled }, ...options }),
  createInstance: (payload: EvaluationInstancePayload, options: RequestOptions = {}) =>
    request<EvaluationInstanceRecord>('/evaluations/instances', { method: 'POST', data: payload, ...options }),
  instances: (params: EvaluationPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<EvaluationInstanceRecord>>('/evaluations/instances', { method: 'GET', params, ...options }),
  instance: (id: number, options: RequestOptions = {}) =>
    request<EvaluationInstanceRecord>(`/evaluations/instances/${id}`, { method: 'GET', ...options }),
  myPendingTasks: (params: EvaluationPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<EvaluationScoreTaskRecord>>('/evaluations/tasks/my-pending', { method: 'GET', params, ...options }),
  submitScore: (taskId: number, payload: EvaluationScorePayload, options: RequestOptions = {}) =>
    request<EvaluationInstanceRecord>(`/evaluations/tasks/${taskId}/submit-score`, { method: 'POST', data: payload, ...options }),
  review: (id: number, payload: { finalScore: number; finalGrade: string; comment?: string }, options: RequestOptions = {}) =>
    request<EvaluationInstanceRecord>(`/evaluations/instances/${id}/review`, { method: 'POST', data: payload, ...options }),
  archive: (id: number, payload: { comment?: string } = {}, options: RequestOptions = {}) =>
    request<EvaluationInstanceRecord>(`/evaluations/instances/${id}/archive`, { method: 'POST', data: payload, ...options }),
};
