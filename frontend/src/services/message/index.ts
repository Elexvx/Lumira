import { request, type RequestOptions } from '@/services/common/request';
import type { MessageNoticeRecord, MessageTargetScope, MessageUnreadCount, PagedResult } from '@/types/api';

export interface MessagePayload {
  title: string;
  content: string;
  targetScope: MessageTargetScope;
  targetUserId?: number;
}

export interface MessageQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
}

export const messageService = {
  messages: (params: MessageQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<MessageNoticeRecord>>('/v1/message/messages', {
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
