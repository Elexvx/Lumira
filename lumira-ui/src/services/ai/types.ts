import type { AiChatRequestPayload, AiChatResponseRecord, AiToolExecuteResultRecord, AiToolPlanRecord } from '@/types/api';

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
  type: 'status' | 'thinking' | 'delta' | 'done' | 'error' | 'tool_proposal' | 'tool_result' | 'tool_blocked';
  message?: string | null;
  delta?: string | null;
  response?: AiChatResponseRecord | null;
  toolPlan?: AiToolPlanRecord | null;
  toolResult?: AiToolExecuteResultRecord | null;
}

export interface AiToolProposePayload {
  employeeId?: number | null;
  conversationId?: number | null;
  message?: string | null;
  toolCode?: string | null;
  arguments?: Record<string, unknown> | null;
}

export type { AiChatRequestPayload };
