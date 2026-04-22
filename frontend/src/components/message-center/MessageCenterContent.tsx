import { MessageOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  Col,
  Empty,
  List,
  Row,
  Spin,
  Tabs,
  Tag,
  Space,
  Typography,
  theme,
} from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { messageService } from '@/services/message';
import type { MessageNoticeRecord } from '@/types/api';
import { useMessageCenterRealtime } from '@/components/message-center/useMessageCenterRealtime';

type MessageCenterFilter = 'all' | 'unread' | 'read';

interface MessageCenterNotice extends MessageNoticeRecord {
  key: string;
  effectiveAt: string;
  relativeTimeLabel: string;
  absoluteTimeLabel: string;
}

interface MessageCenterChannel {
  key: string;
  label: string;
  notices: MessageCenterNotice[];
  unreadCount: number;
  totalCount: number;
  preview: string;
  relativeTimeLabel: string;
}

const MESSAGE_TYPE_LABELS: Record<string, string> = {
  MESSAGE: '站内信',
};

const buildAbsoluteTimeLabel = (value?: string) => {
  if (!value) {
    return '-';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('zh-CN', {
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
    return diffSeconds <= 0 ? '刚刚' : '即将';
  }

  const formatter = new Intl.RelativeTimeFormat('zh-CN', { numeric: 'auto' });
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

const resolveMessageTypeLabel = (messageType?: string) => {
  if (!messageType) {
    return '消息';
  }

  return MESSAGE_TYPE_LABELS[messageType] || messageType;
};

const shortenText = (value?: string, fallback = '-') => {
  if (!value) {
    return fallback;
  }

  const compact = value.replace(/\s+/g, ' ').trim();
  if (compact.length <= 24) {
    return compact;
  }

  return `${compact.slice(0, 24)}…`;
};

export interface MessageCenterContentProps {
  className?: string;
  onUnreadCountChange?: (unreadCount: number) => void;
}

export const MessageCenterContent = ({ className, onUnreadCountChange }: MessageCenterContentProps) => {
  const { initialState } = useInitialStateModel();
  const { token } = theme.useToken();
  const [filter, setFilter] = useState<MessageCenterFilter>('all');
  const [notices, setNotices] = useState<MessageCenterNotice[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [activeChannelKey, setActiveChannelKey] = useState<string | null>(null);
  const [actionKey, setActionKey] = useState<string | null>(null);
  const loadRequestIdRef = useRef(0);

  const tenantId = initialState?.currentTenant?.tenantId;
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
    if (!tenantId) {
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
        setLoadError(nextNotices.length > 0 ? '部分消息加载失败，请稍后重试' : '消息加载失败，请稍后重试');
      }
    } finally {
      if (loadRequestIdRef.current === requestId) {
        setLoading(false);
      }
    }
  }, [requestOptions, tenantId]);

  useMessageCenterRealtime(Boolean(tenantId && canOpenMessageCenter), useCallback((event) => {
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
      return;
    }

    if (!tenantId) {
      setNotices([]);
      setUnreadCount(0);
      return;
    }

    void reloadCenter();
  }, [canOpenMessageCenter, reloadCenter, tenantId]);

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

  const channelList = useMemo<MessageCenterChannel[]>(() => {
    const grouped = new Map<string, MessageCenterNotice[]>();

    for (const notice of notices) {
      const key = notice.messageType || 'MESSAGE';
      const next = grouped.get(key) || [];
      next.push(notice);
      grouped.set(key, next);
    }

    return Array.from(grouped.entries())
      .map(([key, channelNotices]) => {
        const latestNotice = channelNotices[0] || null;
        const unreadCountForChannel = channelNotices.filter((item) => !item.readFlag).length;
        return {
          key,
          label: resolveMessageTypeLabel(key),
          notices: channelNotices,
          unreadCount: unreadCountForChannel,
          totalCount: channelNotices.length,
          preview: latestNotice ? shortenText(latestNotice.title || latestNotice.content) : '暂无消息',
          relativeTimeLabel: latestNotice?.relativeTimeLabel || '-',
        };
      })
      .sort((left, right) => {
        const leftTime = left.notices[0] ? new Date(left.notices[0].effectiveAt).getTime() : 0;
        const rightTime = right.notices[0] ? new Date(right.notices[0].effectiveAt).getTime() : 0;
        return rightTime - leftTime;
      });
  }, [notices]);

  useEffect(() => {
    if (channelList.length === 0) {
      if (activeChannelKey !== null) {
        setActiveChannelKey(null);
      }
      return;
    }

    if (!activeChannelKey || !channelList.some((item) => item.key === activeChannelKey)) {
      setActiveChannelKey(channelList[0].key);
    }
  }, [activeChannelKey, channelList]);

  const activeChannel = useMemo(() => {
    if (channelList.length === 0) {
      return null;
    }

    return channelList.find((item) => item.key === activeChannelKey) || channelList[0];
  }, [activeChannelKey, channelList]);

  const visibleNotices = useMemo(() => {
    const source = activeChannel?.notices || notices;

    if (filter === 'unread') {
      return source.filter((item) => !item.readFlag);
    }

    if (filter === 'read') {
      return source.filter((item) => item.readFlag);
    }

    return source;
  }, [activeChannel?.notices, filter, notices]);

  const tabItems = useMemo(
    () => [
      { label: `全部 (${counts.all})`, key: 'all' },
      { label: `未读 (${counts.unread})`, key: 'unread' },
      { label: `已读 (${counts.read})`, key: 'read' },
    ],
    [counts.all, counts.read, counts.unread],
  );

  const handleMarkRead = async (notice: MessageCenterNotice) => {
    if (notice.readFlag || actionKey) {
      return;
    }

    setActionKey(notice.key);
    try {
      await messageService.readMessage(notice.id, requestOptions);
      await reloadCenter();
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
    } finally {
      setActionKey(null);
    }
  };

  if (!canOpenMessageCenter) {
    return null;
  }

  const activeChannelLabel = activeChannel?.label || '站内信';
  const activeChannelSubtitle =
    activeChannel && activeChannel.totalCount > 0
      ? `共 ${activeChannel.totalCount} 条消息 · ${activeChannel.unreadCount} 条未读`
      : '当前没有消息';

  return (
    <div className={className ? `saas-message-center ${className}` : 'saas-message-center'}>
      <Row gutter={[16, 16]} className="saas-message-center__layout">
        <Col xs={24} lg={8}>
          <Card className="saas-message-center__sidebar-card" bordered styles={{ body: { padding: 16 } }}>
            <Tabs
              activeKey={filter}
              items={tabItems.map((item) => ({ key: item.key, label: item.label }))}
              onChange={(key) => setFilter(key as MessageCenterFilter)}
              size="large"
            />

            <div className="saas-message-center__sidebar-section">
              <Typography.Text type="secondary">消息来源</Typography.Text>
              <List
                className="saas-message-center__channel-list"
                dataSource={channelList}
                locale={{
                  emptyText: loadError ? '消息来源加载失败' : '暂无消息来源',
                }}
                renderItem={(channel) => {
                  const isActive = channel.key === activeChannel?.key;

                  return (
                    <List.Item
                      onClick={() => setActiveChannelKey(channel.key)}
                      className="saas-message-center__channel-item"
                      style={{
                        cursor: channelList.length > 1 ? 'pointer' : 'default',
                        background: isActive ? token.colorFillSecondary : token.colorBgContainer,
                        borderColor: isActive ? token.colorPrimaryBorder : token.colorBorderSecondary,
                      }}
                    >
                      <List.Item.Meta
                        avatar={
                          <Badge count={channel.unreadCount} overflowCount={99} offset={[2, 0]}>
                            <Avatar shape="square" icon={<MessageOutlined />} />
                          </Badge>
                        }
                        title={
                          <Space size={8} wrap>
                            <Typography.Text strong>{channel.label}</Typography.Text>
                            <Tag color={channel.unreadCount > 0 ? 'red' : 'blue'} bordered={false}>
                              {channel.unreadCount > 0 ? '未读' : '已读'}
                            </Tag>
                          </Space>
                        }
                        description={
                          <Space direction="vertical" size={2} style={{ width: '100%' }}>
                            <Typography.Text type="secondary" ellipsis>
                              {channel.preview}
                            </Typography.Text>
                            <Typography.Text type="secondary">{channel.relativeTimeLabel}</Typography.Text>
                          </Space>
                        }
                      />
                    </List.Item>
                  );
                }}
              />
            </div>
          </Card>
        </Col>

        <Col xs={24} lg={16}>
          <div className="saas-message-center__main">
            <div className="saas-message-center__header">
              <div className="saas-message-center__header-copy">
                <Typography.Title level={4} style={{ margin: 0 }}>
                  {activeChannelLabel}
                </Typography.Title>
                <Typography.Text type="secondary">{activeChannelSubtitle}</Typography.Text>
              </div>

              <Space wrap>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => void reloadCenter()}
                  loading={loading && notices.length === 0}
                >
                  刷新
                </Button>
                <Button type="primary" disabled={counts.unread === 0} loading={actionKey === 'all'} onClick={() => void handleMarkAllRead()}>
                  全部标为已读
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
                    重试
                  </Button>
                }
              />
            ) : null}

            <Spin spinning={loading && notices.length === 0} tip="加载消息中">
              <div className="saas-message-center__notice-list">
                {visibleNotices.length === 0 ? (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={filter === 'all' ? '暂无消息' : '暂无符合条件的消息'}
                  >
                    {loadError ? (
                      <Button icon={<ReloadOutlined />} onClick={() => void reloadCenter()}>
                        重试
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
                        <List.Item key={notice.key} className="saas-message-center__notice-item">
                          <Card
                            hoverable
                            size="small"
                            className="saas-message-center__notice-card"
                            styles={{
                              body: { padding: 16 },
                            }}
                          >
                            <Space direction="vertical" size={12} style={{ width: '100%' }}>
                              <div className="saas-message-center__notice-head">
                                <Space align="start" size={12}>
                                  <Badge dot={isUnread} offset={[-1, 2]}>
                                    <Avatar shape="square" icon={<MessageOutlined />} />
                                  </Badge>
                                  <div className="saas-message-center__notice-head-copy">
                                    <Space size={8} wrap>
                                      <Typography.Text strong>{notice.title}</Typography.Text>
                                      <Tag color={isUnread ? 'red' : 'blue'} bordered={false}>
                                        {isUnread ? '未读' : '已读'}
                                      </Tag>
                                    </Space>
                                    <Typography.Text type="secondary">{notice.relativeTimeLabel}</Typography.Text>
                                  </div>
                                </Space>

                                {isUnread ? (
                                  <Button
                                    type="link"
                                    loading={actionKey === notice.key}
                                    onClick={() => void handleMarkRead(notice)}
                                  >
                                    标为已读
                                  </Button>
                                ) : (
                                  <Tag color="blue" bordered={false}>
                                    已读
                                  </Tag>
                                )}
                              </div>

                              <Typography.Paragraph
                                style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}
                                ellipsis={{ rows: 2, expandable: false }}
                              >
                                {notice.content}
                              </Typography.Paragraph>

                              <Typography.Text type="secondary">时间：{notice.absoluteTimeLabel}</Typography.Text>
                            </Space>
                          </Card>
                        </List.Item>
                      );
                    }}
                  />
                )}
              </div>
            </Spin>
          </div>
        </Col>
      </Row>
    </div>
  );
};
