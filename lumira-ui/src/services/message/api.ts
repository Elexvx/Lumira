import type { MessageNoticeRecord, MessageUnreadCount, MessageDeliveryLogRecord, PagedResult } from '@/types/api';
import { request } from '@/services/common/request';
import type { RequestOptions } from '@/services/common/requestInternalsTypes';

const requestMessageWithFallback = async <T>(
  primaryPath: string,
  fallbackPath: string,
  options: RequestOptions,
): Promise<T> => {
  try {
    return await request<T>(primaryPath, options);
  } catch (error) {
    try {
      return await request<T>(fallbackPath, options);
    } catch {
      throw error;
    }
  }
};

export const requestMessageList = (options: RequestOptions): Promise<PagedResult<MessageNoticeRecord>> =>
  requestMessageWithFallback<PagedResult<MessageNoticeRecord>>('/v2/message/messages', '/v1/message/messages', options);

export const requestMessageCreate = (options: RequestOptions): Promise<MessageNoticeRecord> =>
  requestMessageWithFallback<MessageNoticeRecord>('/v2/message/messages', '/v1/message/messages', options);

export const requestMessageRead = (noticeId: number, options: RequestOptions): Promise<MessageNoticeRecord> =>
  requestMessageWithFallback<MessageNoticeRecord>(`/v2/message/messages/${noticeId}/read`, `/v1/message/messages/${noticeId}/read`, options);

export const requestMessageMarkAllRead = (options: RequestOptions): Promise<MessageUnreadCount> =>
  requestMessageWithFallback<MessageUnreadCount>('/v2/message/read-all', '/v1/message/read-all', options);

export const requestMessageUnreadCount = (options: RequestOptions): Promise<MessageUnreadCount> =>
  requestMessageWithFallback<MessageUnreadCount>('/v2/message/unread-count', '/v1/message/unread-count', options);

export const requestMessageArchive = (options: RequestOptions): Promise<PagedResult<MessageNoticeRecord>> =>
  requestMessageWithFallback<PagedResult<MessageNoticeRecord>>('/v2/message/archive', '/v1/message/archive', options);

export const requestMessageDeliveryLogs = (options: RequestOptions): Promise<PagedResult<MessageDeliveryLogRecord>> =>
  requestMessageWithFallback<PagedResult<MessageDeliveryLogRecord>>(
    '/v2/message/delivery-logs',
    '/v1/message/delivery-logs',
    options,
  );

export const requestMessageRetract = (noticeId: number, options: RequestOptions): Promise<MessageNoticeRecord> =>
  requestMessageWithFallback<MessageNoticeRecord>(
    `/v2/message/messages/${noticeId}/retract`,
    `/v1/message/messages/${noticeId}/retract`,
    options,
  );
