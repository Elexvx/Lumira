import { request, type RequestOptions } from '@/services/common/request';
import type { MessageNoticeRecord, MessageTargetScope, MessageUnreadCount, PagedResult } from '@/types/api';

export interface MessagePayload {
  title: string;
  content: string;
  targetScope: MessageTargetScope;
  targetUserId?: number;
  targetRoleId?: number;
}

export interface MessageQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
}

export interface MessageArchiveQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  targetScope?: string;
  publishStatus?: string;
  publishedAtStart?: string;
  publishedAtEnd?: string;
  sortField?: string;
  sortOrder?: string;
}

export const messageService = {
  messages: (params: MessageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<MessageNoticeRecord>>('/v1/message/messages', {
      method: 'GET',
      params,
      ...options,
    }),
  archiveMessages: (params: MessageArchiveQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<MessageNoticeRecord>>('/v1/message/archive', {
      method: 'GET',
      params,
      ...options,
    }),
  createMessage: (payload: MessagePayload, options: RequestOptions = {}) =>
    request<MessageNoticeRecord>('/v1/message/messages', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  readMessage: (id: number, options: RequestOptions = {}) =>
    request<MessageNoticeRecord>(`/v1/message/messages/${id}/read`, {
      method: 'POST',
      ...options,
    }),
  retractMessage: (id: number, options: RequestOptions = {}) =>
    request<MessageNoticeRecord>(`/v1/message/messages/${id}/retract`, {
      method: 'POST',
      ...options,
    }),
  readAll: (options: RequestOptions = {}) =>
    request<MessageUnreadCount>('/v1/message/read-all', {
      method: 'POST',
      ...options,
    }),
  unreadCount: (options: RequestOptions = {}) =>
    request<MessageUnreadCount>('/v1/message/unread-count', {
      method: 'GET',
      ...options,
    }),
};
