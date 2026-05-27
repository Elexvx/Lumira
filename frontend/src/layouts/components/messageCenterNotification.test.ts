import { describe, expect, it } from 'vitest';
import {
  MAX_MESSAGE_NOTIFICATION_DESCRIPTION_LENGTH,
  MESSAGE_NOTIFICATION_DURATION_SECONDS,
  buildMessageCenterNotificationArgs,
  compactNotificationText,
  shouldShowMessageCenterNotification,
} from '@/layouts/components/messageCenterNotification';

const fallback = {
  title: '收到新消息',
  description: '你有一条新的站内信，请前往消息中心查看。',
};

describe('messageCenterNotification', () => {
  it('builds an auto-closing top-right notification for newly created notices', () => {
    const args = buildMessageCenterNotificationArgs(
      {
        eventType: 'NOTICE_CREATED',
        notice: {
          id: 42,
          tenantId: 1,
          messageType: 'MESSAGE',
          targetScope: 'USER',
          title: '审批提醒',
          content: '你有一个新的审批任务需要处理。',
          sourceType: 'MANUAL',
          publishStatus: 'PUBLISHED',
          createdAt: '2026-05-27T12:00:00Z',
        },
      },
      fallback,
    );

    expect(args).toMatchObject({
      key: 'message-center-notice-42',
      title: '审批提醒',
      description: '你有一个新的审批任务需要处理。',
      placement: 'topRight',
      duration: MESSAGE_NOTIFICATION_DURATION_SECONDS,
      showProgress: true,
      pauseOnHover: true,
    });
  });

  it('falls back when the push payload has no notice body', () => {
    const args = buildMessageCenterNotificationArgs(
      {
        eventType: 'NOTICE_CREATED',
        message: '新消息',
        timestamp: '2026-05-27T12:00:00Z',
      },
      fallback,
    );

    expect(args.key).toBe('message-center-notice-2026-05-27T12:00:00Z');
    expect(args.title).toBe(fallback.title);
    expect(args.description).toBe('新消息');
  });

  it('compacts long notification descriptions', () => {
    const text = `${'消息内容'.repeat(60)}\n\n请及时处理`;
    const compacted = compactNotificationText(text);

    expect(compacted.length).toBe(MAX_MESSAGE_NOTIFICATION_DESCRIPTION_LENGTH + 3);
    expect(compacted.endsWith('...')).toBe(true);
    expect(compacted).not.toContain('\n');
  });

  it('only shows popup notifications for newly created notices', () => {
    expect(shouldShowMessageCenterNotification({ eventType: 'NOTICE_CREATED' })).toBe(true);
    expect(shouldShowMessageCenterNotification({ eventType: 'NOTICE_READ' })).toBe(false);
    expect(shouldShowMessageCenterNotification({ eventType: 'UNREAD_COUNT', unreadCount: 3 })).toBe(false);
  });
});
