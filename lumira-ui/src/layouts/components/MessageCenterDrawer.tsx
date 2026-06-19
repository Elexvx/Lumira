import { NotificationOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Badge, Button, Empty, List, Space, Spin, Tag, Tabs, Typography, theme } from 'antd';
import type { NotificationArgsProps } from 'antd';
import { getLocale, useIntl } from '@umijs/max';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { STANDARD_DRAWER_WIDTH_BY_BREAKPOINT } from '@/constants/ui';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { API_ORIGIN } from '@/constants/http';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { normalizeLocale } from '@/i18n/locale';
import { resolveBuiltinMessage } from '@/i18n/messages';
import type { MessageNoticeRecord } from '@/types/api';
import { request } from '@/services/common/request';
import { requestMessageList, requestMessageRead, requestMessageMarkAllRead, requestMessageUnreadCount } from '@/services/message/api';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { notification } from '@/theme/antdFeedbackBridge';

const MESSAGE_CENTER_REFRESH_EVENT = 'saas-message-center:refresh';
const MESSAGE_NOTIFICATION_DURATION_SECONDS = 4.5;
const MAX_MESSAGE_NOTIFICATION_DESCRIPTION_LENGTH = 120;

type MessageCenterFilter = 'all' | 'unread' | 'read';

interface MessageCenterNotice extends MessageNoticeRecord {
  key: string;
  effectiveAt: string;
  relativeTimeLabel: string;
  absoluteTimeLabel: string;
}

const notifyMessageCenterRefresh = () => {
  window.dispatchEvent(new Event(MESSAGE_CENTER_REFRESH_EVENT));
};

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
      ? resolveBuiltinMessage('message.center.time.now', '刚刚')
      : resolveBuiltinMessage('message.center.time.soon', '即将');
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

const normalizeMessageCenterNotice = (notice: MessageNoticeRecord): MessageCenterNotice => {
  const effectiveAt = notice.publishedAt || notice.createdAt;
  return {
    ...notice,
    key: `${notice.messageType}:${notice.id}`,
    effectiveAt,
    relativeTimeLabel: buildRelativeTimeLabel(effectiveAt),
    absoluteTimeLabel: buildAbsoluteTimeLabel(effectiveAt),
  };
};

export interface MessageCenterRealtimeEvent {
  eventType?: string;
  tenantId?: number;
  userId?: number;
  unreadCount?: number;
  message?: string;
  notice?: MessageNoticeRecord;
  timestamp?: string;
}

interface MessageWebSocketTicket {
  ticket: string;
  expiresInSeconds?: number;
}

type MessageCenterRealtimeOptions = {
  enabled: boolean;
  key: string;
};

type MessageCenterRealtimeListener = (event: MessageCenterRealtimeEvent) => void;

const realtimeState = {
  listeners: new Set<MessageCenterRealtimeListener>(),
  socket: null as WebSocket | null,
  reconnectTimer: null as number | null,
  connectionKey: null as string | null,
  connectingKey: null as string | null,
};

const clearReconnectTimer = () => {
  if (realtimeState.reconnectTimer !== null) {
    window.clearTimeout(realtimeState.reconnectTimer);
    realtimeState.reconnectTimer = null;
  }
};

const closeSocket = () => {
  clearReconnectTimer();
  if (!realtimeState.socket) {
    realtimeState.connectionKey = null;
    return;
  }

  const currentSocket = realtimeState.socket;
  realtimeState.socket = null;
  realtimeState.connectionKey = null;
  realtimeState.connectingKey = null;

  currentSocket.onopen = null;
  currentSocket.onmessage = null;
  currentSocket.onerror = null;
  currentSocket.onclose = null;

  if (currentSocket.readyState === WebSocket.OPEN || currentSocket.readyState === WebSocket.CONNECTING) {
    currentSocket.close();
  }
};

const notify = (event: MessageCenterRealtimeEvent) => {
  realtimeState.listeners.forEach((listener) => {
    try {
      listener(event);
    } catch {
      // Keep the shared channel healthy even if one listener fails.
    }
  });
};

const requestWebSocketTicket = async () => {
  const response = await request<MessageWebSocketTicket>('/v1/message/ws-ticket', {
    method: 'POST',
    autoRedirectOnUnauthorized: false,
    silent: true,
  });
  return response.ticket;
};

const buildWebSocketUrl = (ticket: string) => {
  const apiOrigin = API_ORIGIN ? new URL(API_ORIGIN) : window.location;
  const wsScheme = apiOrigin.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${wsScheme}//${apiOrigin.host}/ws/message?ticket=${encodeURIComponent(ticket)}`;
};

const connect = async (key: string) => {
  if (
    realtimeState.connectionKey === key &&
    (realtimeState.connectingKey === key ||
      (realtimeState.socket && (realtimeState.socket.readyState === WebSocket.OPEN || realtimeState.socket.readyState === WebSocket.CONNECTING)))
  ) {
    return;
  }

  closeSocket();
  realtimeState.connectionKey = key;

  realtimeState.connectingKey = key;
  let ticket: string;
  try {
    ticket = await requestWebSocketTicket();
  } catch {
    if (realtimeState.connectionKey === key && realtimeState.listeners.size > 0) {
      clearReconnectTimer();
      realtimeState.reconnectTimer = window.setTimeout(() => void connect(key), 3000);
    }
    return;
  } finally {
    if (realtimeState.connectingKey === key) {
      realtimeState.connectingKey = null;
    }
  }

  if (realtimeState.connectionKey !== key || realtimeState.listeners.size === 0) {
    return;
  }

  const nextSocket = new WebSocket(buildWebSocketUrl(ticket));
  realtimeState.socket = nextSocket;

  nextSocket.onopen = () => {
    notify({
      eventType: 'CONNECTED',
      message: resolveBuiltinMessage('message.center.connected', '消息通道已连接'),
      timestamp: new Date().toISOString(),
    });
  };

  nextSocket.onmessage = (event) => {
    if (typeof event.data !== 'string') {
      return;
    }

    try {
      const payload = JSON.parse(event.data) as MessageCenterRealtimeEvent;
      if (payload && typeof payload === 'object') {
        notify(payload);
      }
    } catch {
      // Ignore malformed messages; the next push or reconnect will recover state.
    }
  };

  nextSocket.onerror = () => {
    if (realtimeState.socket === nextSocket) {
      nextSocket.close();
    }
  };

  nextSocket.onclose = () => {
    if (realtimeState.socket === nextSocket) {
      realtimeState.socket = null;
    }
    if (realtimeState.connectionKey === key && realtimeState.listeners.size > 0) {
      clearReconnectTimer();
      realtimeState.reconnectTimer = window.setTimeout(() => void connect(key), 3000);
    }
  };
};

const subscribeMessageCenterRealtimeClient = (listener: MessageCenterRealtimeListener, options: MessageCenterRealtimeOptions) => {
  realtimeState.listeners.add(listener);

  if (options.enabled) {
    void connect(options.key);
  }

  return () => {
    realtimeState.listeners.delete(listener);
    if (realtimeState.listeners.size === 0) {
      closeSocket();
    }
  };
};

const useMessageCenterRealtime = (enabled: boolean, onEvent: (event: MessageCenterRealtimeEvent) => void) => {
  const { initialState } = useInitialStateModel();
  const sessionId = initialState?.currentUser?.sessionId;

  useEffect(() => {
    if (!enabled || !sessionId) {
      return undefined;
    }

    const connectionKey = sessionId;
    return subscribeMessageCenterRealtimeClient(onEvent, {
      enabled: true,
      key: connectionKey,
    });
  }, [enabled, onEvent, sessionId]);
};

const useMessageCenterContentModel = (enabled: boolean) => {
  const { initialState } = useInitialStateModel();
  const intl = useIntl();
  const [filter, setFilter] = useState<MessageCenterFilter>('unread');
  const [notices, setNotices] = useState<MessageCenterNotice[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionKey, setActionKey] = useState<string | null>(null);
  const loadRequestIdRef = useRef(0);
  const requestOptions = useMemo(
    () => ({
      autoRedirectOnUnauthorized: false,
      silent: true,
    }),
    [],
  );

  const permissions = useMemo(() => new Set(initialState?.currentUser?.permissions || []), [initialState?.currentUser?.permissions]);
  const canOpenMessageCenter =
    permissions.has('*') ||
    permissions.has('message:message:view') ||
    permissions.has('system:notification:view');

  const reloadCenter = useCallback(async () => {
    if (!enabled || !canOpenMessageCenter) {
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
        const messageResult = await requestMessageList({
          method: 'GET',
          params: { pageNo: 1, pageSize: 100 },
          ...requestOptions,
        });

      if (loadRequestIdRef.current !== requestId) {
        return;
      }

      const nextNotices: MessageCenterNotice[] = [];
      let failedParts = 0;

      const messageRecords = Array.isArray(messageResult?.records) ? messageResult.records : [];
      if (!Array.isArray(messageResult?.records)) {
        failedParts += 1;
      }
      nextNotices.push(...messageRecords.map(normalizeMessageCenterNotice));

      nextNotices.sort((left, right) => {
        const leftTime = new Date(left.effectiveAt).getTime();
        const rightTime = new Date(right.effectiveAt).getTime();
        return rightTime - leftTime;
      });

      setNotices(nextNotices);
      setUnreadCount(nextNotices.filter((item) => !item.readFlag).length);

      if (failedParts > 0) {
        setLoadError(
          nextNotices.length > 0
            ? intl.formatMessage({ id: 'message.center.loadPartialError', defaultMessage: '部分消息加载失败，请稍后重试' })
            : intl.formatMessage({ id: 'message.center.loadError', defaultMessage: '消息加载失败，请稍后重试' }),
        );
      }
    } catch {
      if (loadRequestIdRef.current === requestId) {
        setNotices([]);
        setUnreadCount(0);
        setLoadError(intl.formatMessage({ id: 'message.center.loadError', defaultMessage: '消息加载失败，请稍后重试' }));
      }
    } finally {
      if (loadRequestIdRef.current === requestId) {
        setLoading(false);
      }
    }
  }, [canOpenMessageCenter, enabled, intl, requestOptions]);

  useMessageCenterRealtime(
    enabled && canOpenMessageCenter,
    useCallback(
      (event) => {
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
      },
      [reloadCenter],
    ),
  );

  const handleMarkRead = useCallback(
    async (notice: MessageCenterNotice) => {
      if (notice.readFlag || actionKey) {
        return;
      }

      setActionKey(notice.key);
      try {
        await requestMessageRead(notice.id, {
          method: 'POST',
          ...requestOptions,
        });
        await reloadCenter();
        notifyMessageCenterRefresh();
      } finally {
        setActionKey(null);
      }
    },
    [actionKey, reloadCenter, requestOptions],
  );

  const handleMarkAllRead = useCallback(
    async (unreadTotal: number) => {
      if (unreadTotal === 0 || actionKey) {
        return;
      }

      setActionKey('all');
      try {
        await requestMessageMarkAllRead({
          method: 'POST',
          ...requestOptions,
        });
        await reloadCenter();
        notifyMessageCenterRefresh();
      } finally {
        setActionKey(null);
      }
    },
    [actionKey, reloadCenter, requestOptions],
  );

  useEffect(() => {
    if (enabled) {
      void reloadCenter();
    }
  }, [enabled, reloadCenter]);

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

  return {
    canOpenMessageCenter,
    loading,
    loadError,
    filter,
    setFilter,
    notices,
    visibleNotices,
    counts,
    unreadCount,
    actionKey,
    tabItems,
    requestOptions,
    reloadCenter,
    handleMarkRead,
    handleMarkAllRead: () => void handleMarkAllRead(counts.unread),
    title: intl.formatMessage({ id: 'message.center.site', defaultMessage: '站内信' }),
  };
};

const compactNotificationText = (value?: string | null) => {
  const normalized = (value || '').replace(/\s+/g, ' ').trim();
  if (normalized.length <= MAX_MESSAGE_NOTIFICATION_DESCRIPTION_LENGTH) {
    return normalized;
  }
  return `${normalized.slice(0, MAX_MESSAGE_NOTIFICATION_DESCRIPTION_LENGTH)}...`;
};

const shouldShowMessageCenterNotification = (event: MessageCenterRealtimeEvent) => event.eventType === 'NOTICE_CREATED';

const buildMessageCenterNotificationArgs = (
  event: MessageCenterRealtimeEvent,
  fallback: { title: string; description: string },
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

const MessageCenterContentBody = ({
  filter,
  loading,
  loadError,
  tabItems,
  notices,
  visibleNotices,
  actionKey,
  onReload,
  onFilterChange,
  onMarkRead,
}: {
  filter: MessageCenterFilter;
  loading: boolean;
  loadError: string | null;
  tabItems: Array<{ label: string; key: string }>;
  notices: MessageCenterNotice[];
  visibleNotices: MessageCenterNotice[];
  actionKey: string | null;
  onReload: () => void;
  onFilterChange: (key: MessageCenterFilter) => void;
  onMarkRead: (notice: MessageCenterNotice) => void;
}) => {
  const intl = useIntl();
  const { token } = theme.useToken();
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);

  return (
    <>
      <Tabs activeKey={filter} items={tabItems} onChange={(key) => onFilterChange(key as MessageCenterFilter)} />

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
              <Button icon={<ReloadOutlined />} onClick={() => void onReload()}>
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
                      padding: sectionGap,
                      border: `1px solid ${token.colorBorderSecondary}`,
                      borderRadius: 'var(--saas-card-radius)',
                      background: 'transparent',
                    }}
                  >
                    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: tagWrapGap[0], alignItems: 'flex-start' }}>
                        <Space size={tagWrapGap[0]} wrap>
                          <Typography.Text strong>{notice.title}</Typography.Text>
                          <Tag color={isUnread ? 'red' : 'blue'} bordered={false}>
                            {isUnread
                              ? intl.formatMessage({ id: 'message.center.statusUnread', defaultMessage: '未读' })
                              : intl.formatMessage({ id: 'message.center.statusRead', defaultMessage: '已读' })}
                          </Tag>
                        </Space>

                        {isUnread ? (
                          <Button type="link" loading={actionKey === notice.key} onClick={() => void onMarkRead(notice)}>
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
    </>
  );
};

const REQUEST_OPTIONS = {
  autoRedirectOnUnauthorized: false,
  silent: true,
} as const;

export const MessageCenterDrawer = () => {
  const intl = useIntl();
  const { initialState } = useInitialStateModel();
  const [open, setOpen] = useState(false);
  const contentModel = useMessageCenterContentModel(open);
  const [unreadCount, setUnreadCount] = useState(0);
  const notifiedNoticeKeysRef = useRef(new Set<string>());
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);

  const permissions = useMemo(() => new Set(initialState?.currentUser?.permissions || []), [initialState?.currentUser?.permissions]);
  const canOpenMessageCenter =
    permissions.has('*') ||
    permissions.has('message:message:view') ||
    permissions.has('system:notification:view');

  const reloadUnreadCount = useCallback(async () => {
    if (!canOpenMessageCenter) {
      setUnreadCount(0);
      return;
    }

    try {
      const result = await requestMessageUnreadCount({
        method: 'GET',
        ...REQUEST_OPTIONS,
      });
      setUnreadCount(Number(result.unreadCount || 0));
    } catch {
      setUnreadCount(0);
    }
  }, [canOpenMessageCenter]);

  useEffect(() => {
    void reloadUnreadCount();
  }, [reloadUnreadCount]);

  useEffect(() => {
    const handleRefresh = () => {
      void reloadUnreadCount();
    };

    window.addEventListener(MESSAGE_CENTER_REFRESH_EVENT, handleRefresh);
    return () => {
      window.removeEventListener(MESSAGE_CENTER_REFRESH_EVENT, handleRefresh);
    };
  }, [reloadUnreadCount]);

  const handleRealtimeEvent = useCallback((event: MessageCenterRealtimeEvent) => {
    if (shouldShowMessageCenterNotification(event)) {
      const notificationArgs = buildMessageCenterNotificationArgs(event, {
        title: intl.formatMessage({ id: 'message.center.newNotificationTitle', defaultMessage: '收到新消息' }),
        description: intl.formatMessage({ id: 'message.center.newNotificationDescription', defaultMessage: '你有一条新的站内信，请前往消息中心查看。' }),
      });
      const notificationKey = String(notificationArgs.key);

      if (!notifiedNoticeKeysRef.current.has(notificationKey)) {
        notifiedNoticeKeysRef.current.add(notificationKey);

        if (notifiedNoticeKeysRef.current.size > 100) {
          notifiedNoticeKeysRef.current = new Set([...notifiedNoticeKeysRef.current].slice(-50));
        }

        notification.info({
          ...notificationArgs,
          onClick: () => setOpen(true),
        });
      }
    }

    if (typeof event.unreadCount === 'number') {
      setUnreadCount(Math.max(0, event.unreadCount));
      return;
    }

    if (
      event.eventType === 'NOTICE_CREATED' ||
      event.eventType === 'NOTICE_RETRACTED' ||
      event.eventType === 'NOTICE_READ' ||
      event.eventType === 'UNREAD_COUNT'
    ) {
      void reloadUnreadCount();
    }
  }, [intl, reloadUnreadCount]);

  useMessageCenterRealtime(canOpenMessageCenter, handleRealtimeEvent);

  useEffect(() => {
    if (open) {
      setUnreadCount(contentModel.unreadCount);
    }
  }, [contentModel.unreadCount, open]);

  if (!canOpenMessageCenter) {
    return null;
  }

  return (
    <>
      <Badge count={unreadCount} overflowCount={99} offset={[0, resolveResponsiveValue(APP_SPACING.microOffset, isMobile)]}>
        <Button
          type="text"
          icon={<NotificationOutlined />}
          data-testid="top-message-center-button"
          aria-label={intl.formatMessage({ id: 'message.center.ariaLabel', defaultMessage: '消息中心，当前有 {count} 条未读消息' }, { count: unreadCount })}
          onClick={() => setOpen(true)}
        />
      </Badge>

      <ManagementDrawer
        title={intl.formatMessage({ id: 'message.center.title', defaultMessage: '消息中心' })}
        open={open}
        onClose={() => setOpen(false)}
        width={resolveResponsiveValue(STANDARD_DRAWER_WIDTH_BY_BREAKPOINT, isMobile)}
        destroyOnHidden={false}
      >
        {contentModel.canOpenMessageCenter ? (
          <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: tagWrapGap[0], flexWrap: 'wrap' }}>
              <div style={{ minWidth: 0 }}>
                <Typography.Title level={4} style={{ margin: 0 }}>
                  {contentModel.title}
                </Typography.Title>
              </div>

              <Space wrap>
                <Button icon={<ReloadOutlined />} onClick={() => void contentModel.reloadCenter()} loading={contentModel.loading && contentModel.notices.length === 0}>
                  {intl.formatMessage({ id: 'message.center.refresh', defaultMessage: '刷新' })}
                </Button>
                <Button type="primary" disabled={contentModel.counts.unread === 0} loading={contentModel.actionKey === 'all'} onClick={() => void contentModel.handleMarkAllRead()}>
                  {intl.formatMessage({ id: 'message.center.markAllRead', defaultMessage: '全部标为已读' })}
                </Button>
              </Space>
            </div>

            {contentModel.loadError ? (
              <Alert
                type="warning"
                showIcon
                message={contentModel.loadError}
                action={
                  <button
                    type="button"
                    onClick={() => void contentModel.reloadCenter()}
                    style={{ border: 0, background: 'transparent', padding: 0, color: 'inherit', cursor: 'pointer' }}
                  >
                    {intl.formatMessage({ id: 'common.retry', defaultMessage: '重试' })}
                  </button>
                }
              />
            ) : null}

            <MessageCenterContentBody
              filter={contentModel.filter}
              loading={contentModel.loading}
              loadError={contentModel.loadError}
              tabItems={contentModel.tabItems}
              notices={contentModel.notices}
              visibleNotices={contentModel.visibleNotices}
              actionKey={contentModel.actionKey}
              onReload={contentModel.reloadCenter}
              onFilterChange={(key) => contentModel.setFilter(key as typeof contentModel.filter)}
              onMarkRead={(notice) => void contentModel.handleMarkRead(notice)}
            />
          </Space>
        ) : null}
      </ManagementDrawer>
    </>
  );
};
