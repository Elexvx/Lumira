import { MessageOutlined, ReloadOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  Avatar,
  Badge,
  Button,
  Card,
  Descriptions,
  Empty,
  List,
  Segmented,
  Spin,
  Space,
  Tag,
  Typography,
  theme,
} from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
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
  icon: ReactNode;
}

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
    icon: <MessageOutlined />,
  };
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
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
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

  const visibleNotices = useMemo(() => {
    if (filter === 'unread') {
      return notices.filter((item) => !item.readFlag);
    }

    if (filter === 'read') {
      return notices.filter((item) => item.readFlag);
    }

    return notices;
  }, [filter, notices]);

  useEffect(() => {
    if (visibleNotices.length === 0) {
      if (selectedKey !== null) {
        setSelectedKey(null);
      }
      return;
    }

    if (!selectedKey || !visibleNotices.some((item) => item.key === selectedKey)) {
      setSelectedKey(visibleNotices[0].key);
    }
  }, [selectedKey, visibleNotices]);

  const selectedNotice = useMemo(
    () => visibleNotices.find((item) => item.key === selectedKey) || null,
    [selectedKey, visibleNotices],
  );

  const counts = useMemo(
    () => ({
      all: notices.length,
      unread: notices.filter((item) => !item.readFlag).length,
      read: notices.filter((item) => item.readFlag).length,
    }),
    [notices],
  );

  const segmentedOptions = useMemo(
    () => [
      { label: `全部 (${counts.all})`, value: 'all' },
      { label: `未读 (${counts.unread})`, value: 'unread' },
      { label: `已读 (${counts.read})`, value: 'read' },
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

  const openArchivePage = () => {
    history.push('/system/notifications');
  };

  return (
    <div className={className ? `saas-message-center ${className}` : 'saas-message-center'}>
      <Space className="saas-message-center__toolbar" align="center" size={12} wrap>
        <Segmented
          value={filter}
          options={segmentedOptions}
          onChange={(value) => setFilter(value as MessageCenterFilter)}
        />
        <Space>
          <Button onClick={openArchivePage}>站内信归档</Button>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => void reloadCenter()}
            loading={loading && notices.length === 0}
          >
            刷新
          </Button>
          <Button type="primary" disabled={counts.unread === 0} loading={actionKey === 'all'} onClick={() => void handleMarkAllRead()}>
            全部已读
          </Button>
        </Space>
      </Space>

      <Spin spinning={loading && notices.length === 0} tip="加载消息中">
        <div className="saas-message-center__grid">
          <Card title={`消息列表 ${counts.all > 0 ? `(${counts.all})` : ''}`} className="saas-message-center__list-card">
            {loadError && visibleNotices.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={loadError || '消息中心暂时不可用'}>
                <Space wrap>
                  <Button icon={<ReloadOutlined />} onClick={() => void reloadCenter()}>
                    重试
                  </Button>
                  <Button type="primary" onClick={openArchivePage}>
                    打开站内信归档
                  </Button>
                </Space>
              </Empty>
            ) : visibleNotices.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={filter === 'all' ? '暂无消息' : '暂无符合条件的消息'}
              />
            ) : (
              <List
                itemLayout="vertical"
                rowKey="key"
                dataSource={visibleNotices}
                renderItem={(notice) => {
                  const isActive = notice.key === selectedKey;
                  const isUnread = !notice.readFlag;

                  return (
                    <List.Item
                      onClick={() => setSelectedKey(notice.key)}
                      style={{
                        cursor: 'pointer',
                        borderRadius: token.borderRadiusLG,
                        marginBlockEnd: 12,
                        padding: 16,
                        background: isActive ? token.colorFillSecondary : token.colorBgContainer,
                        border: `1px solid ${isActive ? token.colorPrimaryBorder : token.colorBorderSecondary}`,
                      }}
                    >
                      <List.Item.Meta
                        avatar={
                          <Badge dot={isUnread} offset={[-2, 2]}>
                            <Avatar shape="square" icon={<MessageOutlined />} style={{ backgroundColor: token.colorPrimary }} />
                          </Badge>
                        }
                        title={
                          <Space wrap size={8}>
                            <Typography.Text strong>{notice.title}</Typography.Text>
                            <Tag color={isUnread ? 'red' : 'blue'} bordered={false}>
                              {notice.readFlag ? '已读' : '未读'}
                            </Tag>
                          </Space>
                        }
                        description={
                          <Space direction="vertical" size={4} style={{ width: '100%' }}>
                            <Typography.Text type="secondary">{notice.relativeTimeLabel}</Typography.Text>
                            <Typography.Paragraph ellipsis={{ rows: 2, expandable: false }} style={{ marginBottom: 0 }}>
                              {notice.content}
                            </Typography.Paragraph>
                          </Space>
                        }
                      />
                    </List.Item>
                  );
                }}
              />
            )}
          </Card>

          <Card
            title="详情"
            extra={
              selectedNotice ? (
                selectedNotice.readFlag ? (
                  <Tag color="blue" bordered={false}>
                    已读
                  </Tag>
                ) : (
                  <Button
                    type="primary"
                    size="small"
                    loading={actionKey === selectedNotice.key}
                    onClick={() => void handleMarkRead(selectedNotice)}
                  >
                    标为已读
                  </Button>
                )
              ) : null
            }
            className="saas-message-center__detail-card"
          >
            {selectedNotice ? (
              <>
                <Descriptions bordered column={1} size="small">
                  <Descriptions.Item label="标题">{selectedNotice.title}</Descriptions.Item>
                  <Descriptions.Item label="时间">{selectedNotice.absoluteTimeLabel}</Descriptions.Item>
                  <Descriptions.Item label="状态">
                    {selectedNotice.readFlag ? <Tag color="blue">已读</Tag> : <Tag color="red">未读</Tag>}
                  </Descriptions.Item>
                </Descriptions>

                <Typography.Title level={5} style={{ marginTop: 16 }}>
                  内容
                </Typography.Title>
                <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                  {selectedNotice.content}
                </Typography.Paragraph>
              </>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择一条消息查看详情" />
            )}
          </Card>
        </div>
      </Spin>
    </div>
  );
};
