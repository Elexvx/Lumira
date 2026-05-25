import { ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Empty,
  List,
  Spin,
  Tabs,
  Tag,
  Space,
  Typography,
} from 'antd';
import { getLocale, useIntl } from '@umijs/max';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { normalizeLocale } from '@/i18n/locale';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { messageService } from '@/services/message';
import type { MessageNoticeRecord } from '@/types/api';
import { useMessageCenterRealtime } from '@/components/message-center/useMessageCenterRealtime';
import { notifyMessageCenterRefresh } from '@/components/message-center/messageCenterEvents';

type MessageCenterFilter = 'all' | 'unread' | 'read';

interface MessageCenterNotice extends MessageNoticeRecord {
  key: string;
  effectiveAt: string;
  relativeTimeLabel: string;
  absoluteTimeLabel: string;
}

const buildAbsoluteTimeLabel = (value?: string) => {
  if (!value) {
    return '-';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat(normalizeLocale(getLocale()), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date);
};

const buildRelativeTimeLabel = (value?: string) => {
  if (!value) {
    return '-';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  const diffSeconds = Math.round((date.getTime() - Date.now()) / 1000);
  const absoluteSeconds = Math.abs(diffSeconds);

  if (absoluteSeconds < 60) {
    return diffSeconds <= 0
      ? (normalizeLocale(getLocale()) === 'en-US' ? 'Just now' : '刚刚')
      : (normalizeLocale(getLocale()) === 'en-US' ? 'Soon' : '即将');
  }

  const formatter = new Intl.RelativeTimeFormat(normalizeLocale(getLocale()), { numeric: 'auto' });
  const units: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ['year', 60 * 60 * 24 * 365],
    ['month', 60 * 60 * 24 * 30],
    ['week', 60 * 60 * 24 * 7],
    ['day', 60 * 60 * 24],
    ['hour', 60 * 60],
    ['minute', 60],
  ];

  for (const [unit, unitSeconds] of units) {
    if (absoluteSeconds >= unitSeconds) {
      return formatter.format(Math.trunc(diffSeconds / unitSeconds), unit);
    }
  }

  return formatter.format(diffSeconds, 'second');
};

const normalizeNotice = (notice: MessageNoticeRecord): MessageCenterNotice => {
  const effectiveAt = notice.publishedAt || notice.createdAt;
  return {
    ...notice,
    key: `${notice.messageType}:${notice.id}`,
    effectiveAt,
    relativeTimeLabel: buildRelativeTimeLabel(effectiveAt),
    absoluteTimeLabel: buildAbsoluteTimeLabel(effectiveAt),
  };
};

export interface MessageCenterContentProps {
  onUnreadCountChange?: (unreadCount: number) => void;
}

export const MessageCenterContent = ({ onUnreadCountChange }: MessageCenterContentProps) => {
  const { initialState } = useInitialStateModel();
  const intl = useIntl();
  const [filter, setFilter] = useState<MessageCenterFilter>('unread');
  const [notices, setNotices] = useState<MessageCenterNotice[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionKey, setActionKey] = useState<string | null>(null);
  const loadRequestIdRef = useRef(0);

  const permissions = useMemo(() => new Set(initialState?.currentUser?.permissions || []), [initialState?.currentUser?.permissions]);
  const canOpenMessageCenter =
    permissions.has('*') ||
    permissions.has('message:message:view') ||
    permissions.has('system:notification:view');

  const requestOptions = useMemo(
    () => ({
      autoRedirectOnUnauthorized: false,
      silent: true,
    }),
    [],
  );

  const reloadCenter = useCallback(async () => {
    if (!canOpenMessageCenter) {
      setNotices([]);
      setUnreadCount(0);
      setLoadError(null);
      return;
    }

    const requestId = loadRequestIdRef.current + 1;
    loadRequestIdRef.current = requestId;
    setLoading(true);
    setLoadError(null);

    try {
      const [messageResult, unreadResult] = await Promise.allSettled([
        messageService.messages({ pageNo: 1, pageSize: 100 }, requestOptions),
        messageService.unreadCount(requestOptions),
      ]);

      if (loadRequestIdRef.current !== requestId) {
        return;
      }

      const nextNotices: MessageCenterNotice[] = [];
      let failedParts = 0;

      if (messageResult.status === 'fulfilled') {
        nextNotices.push(...messageResult.value.records.map(normalizeNotice));
      } else {
        failedParts += 1;
      }

      nextNotices.sort((left, right) => {
        const leftTime = new Date(left.effectiveAt).getTime();
        const rightTime = new Date(right.effectiveAt).getTime();
        return rightTime - leftTime;
      });

      setNotices(nextNotices);
      setUnreadCount(
        unreadResult.status === 'fulfilled' ? Number(unreadResult.value.unreadCount || 0) : 0,
      );

      if (failedParts > 0) {
        setLoadError(
          nextNotices.length > 0
            ? intl.formatMessage({ id: 'message.center.loadPartialError', defaultMessage: '部分消息加载失败，请稍后重试' })
            : intl.formatMessage({ id: 'message.center.loadError', defaultMessage: '消息加载失败，请稍后重试' }),
        );
      }
    } finally {
      if (loadRequestIdRef.current === requestId) {
        setLoading(false);
      }
    }
  }, [canOpenMessageCenter, intl, requestOptions]);

  useMessageCenterRealtime(canOpenMessageCenter, useCallback((event) => {
    if (!event.eventType) {
      return;
    }

    if (
      event.eventType === 'NOTICE_CREATED' ||
      event.eventType === 'NOTICE_RETRACTED' ||
      event.eventType === 'NOTICE_READ' ||
      event.eventType === 'UNREAD_COUNT'
    ) {
      void reloadCenter();
    }
  }, [reloadCenter]));

  useEffect(() => {
    if (!canOpenMessageCenter) {
      setNotices([]);
      setUnreadCount(0);
      return;
    }

    void reloadCenter();
  }, [canOpenMessageCenter, reloadCenter]);

  useEffect(() => {
    onUnreadCountChange?.(unreadCount);
  }, [onUnreadCountChange, unreadCount]);

  const counts = useMemo(
    () => ({
      all: notices.length,
      unread: notices.filter((item) => !item.readFlag).length,
      read: notices.filter((item) => item.readFlag).length,
    }),
    [notices],
  );

  const visibleNotices = useMemo(() => {
    if (filter === 'unread') {
      return notices.filter((item) => !item.readFlag);
    }

    if (filter === 'read') {
      return notices.filter((item) => item.readFlag);
    }

    return notices;
  }, [filter, notices]);

  const tabItems = useMemo(
    () => [
      { label: intl.formatMessage({ id: 'message.center.unread', defaultMessage: '未读' }) + ` (${counts.unread})`, key: 'unread' },
      { label: intl.formatMessage({ id: 'message.center.read', defaultMessage: '已读' }) + ` (${counts.read})`, key: 'read' },
      { label: intl.formatMessage({ id: 'message.center.all', defaultMessage: '全部' }) + ` (${counts.all})`, key: 'all' },
    ],
    [counts.all, counts.read, counts.unread, intl],
  );

  const handleMarkRead = async (notice: MessageCenterNotice) => {
    if (notice.readFlag || actionKey) {
      return;
    }

    setActionKey(notice.key);
    try {
      await messageService.readMessage(notice.id, requestOptions);
      await reloadCenter();
      notifyMessageCenterRefresh();
    } finally {
      setActionKey(null);
    }
  };

  const handleMarkAllRead = async () => {
    if (counts.unread === 0 || actionKey) {
      return;
    }

    setActionKey('all');
    try {
      await messageService.readAll(requestOptions);
      await reloadCenter();
      notifyMessageCenterRefresh();
    } finally {
      setActionKey(null);
    }
  };

  if (!canOpenMessageCenter) {
    return null;
  }

  const title = intl.formatMessage({ id: 'message.center.site', defaultMessage: '站内信' });

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ minWidth: 0 }}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {title}
          </Typography.Title>
        </div>

        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => void reloadCenter()} loading={loading && notices.length === 0}>
            {intl.formatMessage({ id: 'message.center.refresh', defaultMessage: '刷新' })}
          </Button>
          <Button type="primary" disabled={counts.unread === 0} loading={actionKey === 'all'} onClick={() => void handleMarkAllRead()}>
            {intl.formatMessage({ id: 'message.center.markAllRead', defaultMessage: '全部标为已读' })}
          </Button>
        </Space>
      </div>

      {loadError ? (
        <Alert
          type="warning"
          showIcon
          message={loadError}
          action={
            <Button size="small" icon={<ReloadOutlined />} onClick={() => void reloadCenter()}>
              {intl.formatMessage({ id: 'common.retry', defaultMessage: '重试' })}
            </Button>
          }
        />
      ) : null}

      <Tabs activeKey={filter} items={tabItems} onChange={(key) => setFilter(key as MessageCenterFilter)} />

      <Spin spinning={loading && notices.length === 0} tip={intl.formatMessage({ id: 'message.center.loading', defaultMessage: '加载消息中' })}>
        {visibleNotices.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              filter === 'all'
                ? intl.formatMessage({ id: 'message.center.none', defaultMessage: '暂无消息' })
                : intl.formatMessage({ id: 'message.center.noneFiltered', defaultMessage: '暂无符合条件的消息' })
            }
          >
            {loadError ? (
              <Button icon={<ReloadOutlined />} onClick={() => void reloadCenter()}>
                {intl.formatMessage({ id: 'common.retry', defaultMessage: '重试' })}
              </Button>
            ) : null}
          </Empty>
        ) : (
          <List
            dataSource={visibleNotices}
            split={false}
            renderItem={(notice) => {
              const isUnread = !notice.readFlag;

              return (
                <List.Item key={notice.key} style={{ paddingInline: 0 }}>
                  <div
                    style={{
                      width: '100%',
                      padding: 16,
                      border: '1px solid var(--saas-border-color)',
                      borderRadius: 12,
                      background: 'transparent',
                    }}
                  >
                    <Space direction="vertical" size={12} style={{ width: '100%' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
                        <Space size={8} wrap>
                          <Typography.Text strong>{notice.title}</Typography.Text>
                          <Tag color={isUnread ? 'red' : 'blue'} bordered={false}>
                            {isUnread
                              ? intl.formatMessage({ id: 'message.center.statusUnread', defaultMessage: '未读' })
                              : intl.formatMessage({ id: 'message.center.statusRead', defaultMessage: '已读' })}
                          </Tag>
                        </Space>

                        {isUnread ? (
                          <Button type="link" loading={actionKey === notice.key} onClick={() => void handleMarkRead(notice)}>
                            {intl.formatMessage({ id: 'message.center.markRead', defaultMessage: '标为已读' })}
                          </Button>
                        ) : null}
                      </div>

                      <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }} ellipsis={{ rows: 2, expandable: false }}>
                        {notice.content}
                      </Typography.Paragraph>

                      <Typography.Text type="secondary">
                        {intl.formatMessage({ id: 'message.center.time', defaultMessage: '时间：{time}' }, { time: notice.absoluteTimeLabel })}
                      </Typography.Text>
                    </Space>
                  </div>
                </List.Item>
              );
            }}
          />
        )}
      </Spin>
    </Space>
  );
};
