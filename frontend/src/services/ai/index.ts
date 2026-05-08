import { request, type RequestOptions } from '@/services/common/request';
import type {
  AiChatRequestPayload,
  AiChatResponseRecord,
  AiConversationExportRecord,
  AiConversationShareDetailRecord,
  AiConversationShareRecord,
  AiConversationMessageRecord,
  AiConversationRecord,
  AiEmployeeDetailRecord,
  AiEmployeeRecord,
  AiEmployeeUpsertPayload,
  AiLlmServiceRecord,
  AiLlmServiceUpsertPayload,
  AiPromptTemplateRecord,
  AiSkillRecord,
  AiEmployeeSkillRecord,
  PagedResult,
} from '@/types/api';

export interface AiPageQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
}

export const aiService = {
  employees: (params: AiPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<AiEmployeeRecord>>('/ai/employees', {
      method: 'GET',
      params,
      ...options,
    }),
  employee: (id: number, options: RequestOptions = {}) =>
    request<AiEmployeeDetailRecord>(`/ai/employees/${id}`, {
      method: 'GET',
      ...options,
    }),
  createEmployee: (payload: AiEmployeeUpsertPayload, options: RequestOptions = {}) =>
    request<AiEmployeeDetailRecord>('/ai/employees', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateEmployee: (id: number, payload: AiEmployeeUpsertPayload, options: RequestOptions = {}) =>
    request<AiEmployeeDetailRecord>(`/ai/employees/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteEmployee: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/ai/employees/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  updateEmployeeEnabled: (id: number, enabled: boolean, options: RequestOptions = {}) =>
    request<boolean>(`/ai/employees/${id}/enabled`, {
      method: 'PATCH',
      data: { enabled },
      ...options,
    }),
  employeePromptTemplate: (options: RequestOptions = {}) =>
    request<AiPromptTemplateRecord>('/ai/employees/template', {
      method: 'GET',
      ...options,
    }),
  llmServices: (params: AiPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<AiLlmServiceRecord>>('/ai/llm-services', {
      method: 'GET',
      params,
      ...options,
    }),
  llmService: (id: number, options: RequestOptions = {}) =>
    request<AiLlmServiceRecord>(`/ai/llm-services/${id}`, {
      method: 'GET',
      ...options,
    }),
  createLlmService: (payload: AiLlmServiceUpsertPayload, options: RequestOptions = {}) =>
    request<AiLlmServiceRecord>('/ai/llm-services', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateLlmService: (id: number, payload: AiLlmServiceUpsertPayload, options: RequestOptions = {}) =>
    request<AiLlmServiceRecord>(`/ai/llm-services/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteLlmService: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/ai/llm-services/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  updateLlmServiceEnabled: (id: number, enabled: boolean, options: RequestOptions = {}) =>
    request<boolean>(`/ai/llm-services/${id}/enabled`, {
      method: 'PATCH',
      data: { enabled },
      ...options,
    }),
  skills: (options: RequestOptions = {}) =>
    request<AiSkillRecord[]>('/ai/skills', {
      method: 'GET',
      ...options,
    }),
  employeeSkills: (id: number, options: RequestOptions = {}) =>
    request<AiEmployeeSkillRecord[]>(`/ai/employees/${id}/skills`, {
      method: 'GET',
      ...options,
    }),
  assistant: (options: RequestOptions = {}) =>
    request<AiEmployeeRecord>('/ai/assistant', {
      method: 'GET',
      ...options,
    }),
  updateConversation: (id: number, payload: { title?: string | null; pinned?: boolean | null }, options: RequestOptions = {}) =>
    request<boolean>(`/ai/conversations/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteConversation: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/ai/conversations/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  shareConversation: (id: number, options: RequestOptions = {}) =>
    request<AiConversationShareRecord>(`/ai/conversations/${id}/share`, {
      method: 'POST',
      ...options,
    }),
  conversationShare: (token: string, options: RequestOptions = {}) =>
    request<AiConversationShareDetailRecord>(`/ai/shares/${token}`, {
      method: 'GET',
      ...options,
    }),
  exportConversation: (id: number, params: { format?: 'markdown' | 'text' } = {}, options: RequestOptions = {}) =>
    request<AiConversationExportRecord>(`/ai/conversations/${id}/export`, {
      method: 'GET',
      params,
      ...options,
    }),
  conversations: (params: AiPageQuery & { employeeId: number }, options: RequestOptions = {}) =>
    request<PagedResult<AiConversationRecord>>('/ai/conversations', {
      method: 'GET',
      params,
      ...options,
    }),
  conversationMessages: (id: number, options: RequestOptions = {}) =>
    request<AiConversationMessageRecord[]>(`/ai/conversations/${id}/messages`, {
      method: 'GET',
      ...options,
    }),
  updateEmployeeSkills: (id: number, payload: { skills: Array<{ skillCode: string; permissionMode: AiEmployeeSkillRecord['permissionMode'] }> }, options: RequestOptions = {}) =>
    request<boolean>(`/ai/employees/${id}/skills`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  chat: (payload: AiChatRequestPayload, options: RequestOptions = {}) =>
    request<AiChatResponseRecord>('/ai/chat', {
      method: 'POST',
      data: payload,
      ...options,
    }),
};
