import type { NotificationArgsProps } from 'antd';
import type { MessageCenterRealtimeEvent } from '@/components/message-center/messageCenterRealtime';

export const MESSAGE_NOTIFICATION_DURATION_SECONDS = 4.5;
export const MAX_MESSAGE_NOTIFICATION_DESCRIPTION_LENGTH = 120;

export interface MessageCenterNotificationFallback {
  title: string;
  description: string;
}

export const compactNotificationText = (value?: string | null) => {
  const normalized = (value || '').replace(/\s+/g, ' ').trim();
  if (normalized.length <= MAX_MESSAGE_NOTIFICATION_DESCRIPTION_LENGTH) {
    return normalized;
  }
  return `${normalized.slice(0, MAX_MESSAGE_NOTIFICATION_DESCRIPTION_LENGTH)}...`;
};

export const shouldShowMessageCenterNotification = (event: MessageCenterRealtimeEvent) =>
  event.eventType === 'NOTICE_CREATED';

export const buildMessageCenterNotificationArgs = (
  event: MessageCenterRealtimeEvent,
  fallback: MessageCenterNotificationFallback,
  generatedAt = Date.now(),
): NotificationArgsProps => {
  const notice = event.notice;
  const noticeKey = notice?.id ? `notice-${notice.id}` : `notice-${event.timestamp || generatedAt}`;

  return {
    key: `message-center-${noticeKey}`,
    title: notice?.title || fallback.title,
    description: compactNotificationText(notice?.content || event.message) || fallback.description,
    placement: 'topRight',
    duration: MESSAGE_NOTIFICATION_DURATION_SECONDS,
    showProgress: true,
    pauseOnHover: true,
  };
};
