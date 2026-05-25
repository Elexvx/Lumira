import { request, requestEventStream, type RequestOptions } from '@/services/common/request';
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
  AiGovernanceOverviewRecord,
  AiKnowledgeBaseRecord,
  AiKnowledgeDocumentRecord,
  AiKnowledgeReferenceRecord,
  AiLlmServiceRecord,
  AiLlmServiceTestPayload,
  AiLlmServiceTestResult,
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

export interface AiKnowledgeBasePayload {
  name: string;
  description?: string | null;
  status?: string | null;
  visibilityScope?: string | null;
}

export interface AiKnowledgeBaseQuery extends AiPageQuery {
  keyword?: string;
  status?: string;
  scope?: string;
}

export interface AiChatStreamEvent {
  type: 'status' | 'thinking' | 'delta' | 'done' | 'error';
  message?: string | null;
  delta?: string | null;
  response?: AiChatResponseRecord | null;
}

export const aiService = {
  governanceOverview: (options: RequestOptions = {}) =>
    request<AiGovernanceOverviewRecord>('/ai/governance/overview', {
      method: 'GET',
      ...options,
    }),
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
  testLlmService: (payload: AiLlmServiceTestPayload, options: RequestOptions = {}) =>
    request<AiLlmServiceTestResult>('/ai/llm-services/test', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  skills: (options: RequestOptions = {}) =>
    request<AiSkillRecord[]>('/ai/skills', {
      method: 'GET',
      ...options,
    }),
  knowledgeBases: (params: AiKnowledgeBaseQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<AiKnowledgeBaseRecord>>('/ai/knowledge-bases', {
      method: 'GET',
      params,
      ...options,
    }),
  createKnowledgeBase: (payload: AiKnowledgeBasePayload, options: RequestOptions = {}) =>
    request<AiKnowledgeBaseRecord>('/ai/knowledge-bases', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateKnowledgeBase: (id: number, payload: AiKnowledgeBasePayload, options: RequestOptions = {}) =>
    request<AiKnowledgeBaseRecord>(`/ai/knowledge-bases/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteKnowledgeBase: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/ai/knowledge-bases/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  knowledgeDocuments: (id: number, params: AiPageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<AiKnowledgeDocumentRecord>>(`/ai/knowledge-bases/${id}/documents`, {
      method: 'GET',
      params,
      ...options,
    }),
  uploadKnowledgeDocument: (id: number, file: File, options: RequestOptions = {}) => {
    const formData = new FormData();
    formData.append('file', file);
    return request<AiKnowledgeDocumentRecord>(`/ai/knowledge-bases/${id}/documents/upload`, {
      method: 'POST',
      headers: {},
      data: formData,
      ...options,
    });
  },
  reindexKnowledgeDocument: (id: number, documentId: number, options: RequestOptions = {}) =>
    request<AiKnowledgeDocumentRecord>(`/ai/knowledge-bases/${id}/documents/${documentId}/reindex`, {
      method: 'POST',
      ...options,
    }),
  deleteKnowledgeDocument: (id: number, documentId: number, options: RequestOptions = {}) =>
    request<boolean>(`/ai/knowledge-bases/${id}/documents/${documentId}`, {
      method: 'DELETE',
      ...options,
    }),
  searchKnowledge: (payload: { query: string; knowledgeBaseIds?: number[]; limit?: number }, options: RequestOptions = {}) =>
    request<AiKnowledgeReferenceRecord[]>('/ai/knowledge-bases/search', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  employeeKnowledgeBases: (id: number, options: RequestOptions = {}) =>
    request<AiKnowledgeBaseRecord[]>(`/ai/employees/${id}/knowledge-bases`, {
      method: 'GET',
      ...options,
    }),
  updateEmployeeKnowledgeBases: (id: number, knowledgeBaseIds: number[], options: RequestOptions = {}) =>
    request<boolean>(`/ai/employees/${id}/knowledge-bases`, {
      method: 'PUT',
      data: { knowledgeBaseIds },
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
  conversations: (params: AiPageQuery & { employeeId?: number | null }, options: RequestOptions = {}) =>
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
  streamChat: (
    payload: AiChatRequestPayload,
    onEvent: (event: AiChatStreamEvent) => void,
    options: RequestOptions = {},
  ) =>
    requestEventStream('/ai/chat/stream', {
      method: 'POST',
      data: payload,
      ...options,
      onEvent: ({ data }) => {
        onEvent(JSON.parse(data) as AiChatStreamEvent);
      },
    }),
};
