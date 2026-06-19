import { type ActionType, ProDescriptions, type ProColumns, type ProDescriptionsItemProps } from '@ant-design/pro-components';
import { Button, Space, Tag, Typography, type SpaceProps } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { AUTHORIZATION_HEADER, API_PREFIX } from '@/constants/http';
import { captureAuthRequestSnapshot, buildUnauthorizedRuntimeState } from '@/auth/unauthorized';
import { shouldSuppressUnauthorizedSideEffects, type AuthRequestSnapshot } from '@/auth/unauthorizedDecision';
import { performLogout } from '@/auth/sessionLifecycle';
import { request } from '@/services/common/request';
import { readEventStream } from '@/services/common/requestInternalsStream';
import type { OnlineSessionEventRecord, OnlineSessionRecord, PagedResult } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import { confirmAction } from '@/utils/confirm';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

interface OnlineSessionStreamOptions {
  onEvent: (event: OnlineSessionEventRecord) => void;
  onConnected?: () => void;
  onUnauthorized?: () => void;
  currentSessionId?: string | null;
}

const isCurrentSessionRemovalEvent = (event: OnlineSessionEventRecord, currentSessionId?: string | null) =>
  Boolean(currentSessionId) && event.action === 'REMOVED' && event.sessionId === currentSessionId;

const handleOnlineUsersStreamResponse = async (
  response: Response,
  options: OnlineSessionStreamOptions,
  requestAuthSnapshot: AuthRequestSnapshot,
  stop: () => void,
  scheduleReconnect: () => void,
) => {
  if (response.status === 401 || response.status === 403) {
    if (shouldSuppressUnauthorizedSideEffects(requestAuthSnapshot, buildUnauthorizedRuntimeState())) {
      stop();
      return;
    }
    options.onUnauthorized?.();
    await performLogout({ reason: 'forced_expired', hardReload: true }).catch(() => {
      // Ignore logout failures when the server has already revoked the session.
    });
    stop();
    return;
  }

  if (!response.ok || !response.body) {
    scheduleReconnect();
    return;
  }

  options.onConnected?.();
  await readEventStream(response.body, (event) => {
    if (event.event === 'heartbeat') {
      return;
    }

    try {
      const payload = JSON.parse(event.data) as OnlineSessionEventRecord;
      if (isCurrentSessionRemovalEvent(payload, options.currentSessionId)) {
        stop();
        void performLogout({ reason: 'forced_expired', hardReload: true }).catch(() => {
          // The browser may already be navigating away after the hard reload.
        });
        return;
      }
      options.onEvent(payload);
    } catch {
      // Ignore malformed payloads so a single bad event does not break the stream.
    }
  });
  scheduleReconnect();
};

const connectOnlineSessionStream = (options: OnlineSessionStreamOptions) => {
  const controller = new AbortController();
  let stopped = false;
  let reconnectTimer: number | null = null;

  const stop = () => {
    stopped = true;
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    controller.abort();
  };

  const scheduleReconnect = () => {
    if (stopped) {
      return;
    }
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer);
    }
    reconnectTimer = window.setTimeout(() => {
      void open();
    }, 3000);
  };

  const open = async () => {
    if (stopped) {
      return;
    }

    const requestAuthSnapshot = captureAuthRequestSnapshot();
    const accessToken = requestAuthSnapshot.accessToken;
    if (!accessToken) {
      scheduleReconnect();
      return;
    }

    try {
      const response = await fetch(`${API_PREFIX}/v1/system/online-users/events`, {
        method: 'GET',
        headers: {
          [AUTHORIZATION_HEADER]: `Bearer ${accessToken}`,
          Accept: 'text/event-stream',
          'Cache-Control': 'no-cache',
        },
        signal: controller.signal,
        credentials: 'same-origin',
      });

      await handleOnlineUsersStreamResponse(response, options, requestAuthSnapshot, stop, scheduleReconnect);
    } catch (error) {
      if (!stopped && !(error instanceof DOMException && error.name === 'AbortError')) {
        scheduleReconnect();
      }
    }
  };

  void open();
  return stop;
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', { hour12: false });
};

const buildOnlineUserColumns = ({
  currentUserId,
  currentSessionId,
  userCellGap,
  isMobile,
  buildActions,
  onShowDetail,
  onKick,
  onBan,
}: {
  currentUserId?: OnlineSessionRecord['userId'];
  currentSessionId?: OnlineSessionRecord['sessionId'];
  userCellGap: SpaceProps['size'];
  isMobile: boolean;
  buildActions: (items: any[]) => any[];
  onShowDetail: (record: OnlineSessionRecord) => void;
  onKick: (record: OnlineSessionRecord) => void;
  onBan: (record: OnlineSessionRecord) => void;
}): ProColumns<OnlineSessionRecord>[] => [
  {
    title: t('用户', 'User'),
    dataIndex: 'username',
    width: 'var(--saas-spacing-360)',
    render: (_, record) => (
      <Space className="saas-online-users-page__user-cell" size={userCellGap} wrap={false}>
        <Typography.Text className="saas-online-users-page__user-name" strong ellipsis={{ tooltip: record.realName || record.nickname || record.username }}>
          {record.realName || record.nickname || record.username}
        </Typography.Text>
        {record.userId === currentUserId ? <Tag color="orange">{t('当前账号', 'Current account')}</Tag> : null}
        {record.sessionId === currentSessionId ? <Tag color="blue">{t('当前会话', 'Current session')}</Tag> : null}
        <Typography.Text className="saas-online-users-page__username" type="secondary" ellipsis={{ tooltip: record.username }}>
          {record.username}
        </Typography.Text>
      </Space>
    ),
  },
  {
    title: t('终端', 'Client'),
    dataIndex: 'clientType',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    width: 'var(--saas-spacing-120)',
    render: (_, record) => (
      <Typography.Text className="saas-online-users-page__cell-text" ellipsis={{ tooltip: record.clientType || '-' }}>
        {record.clientType || '-'}
      </Typography.Text>
    ),
  },
  {
    title: t('登录 IP', 'Login IP'),
    dataIndex: 'loginIp',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    width: 'var(--saas-spacing-160)',
    render: (_, record) => (
      <Typography.Text className="saas-online-users-page__cell-text" ellipsis={{ tooltip: record.loginIp || '-' }}>
        {record.loginIp || '-'}
      </Typography.Text>
    ),
  },
  {
    title: t('登录时间', 'Login time'),
    dataIndex: 'loginTime',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    width: 'var(--saas-spacing-180)',
    render: (_, record) => (
      <Typography.Text className="saas-online-users-page__cell-text" ellipsis={{ tooltip: formatDateTime(record.loginTime) }}>
        {formatDateTime(record.loginTime)}
      </Typography.Text>
    ),
  },
  {
    title: t('最近活跃', 'Last active'),
    dataIndex: 'lastActivityAt',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    width: 'var(--saas-spacing-180)',
    render: (_, record) => (
      <Typography.Text className="saas-online-users-page__cell-text" ellipsis={{ tooltip: formatDateTime(record.lastActivityAt) }}>
        {formatDateTime(record.lastActivityAt)}
      </Typography.Text>
    ),
  },
  {
    title: t('过期时间', 'Expiry time'),
    dataIndex: 'expireTime',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    width: 'var(--saas-spacing-180)',
    render: (_, record) => (
      <Typography.Text className="saas-online-users-page__cell-text" ellipsis={{ tooltip: formatDateTime(record.expireTime) }}>
        {formatDateTime(record.expireTime)}
      </Typography.Text>
    ),
  },
  {
    title: t('会话 ID', 'Session ID'),
    dataIndex: 'sessionId',
    search: false,
    width: 'var(--saas-spacing-260)',
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) => (
      <Typography.Text copyable={{ text: record.sessionId }} ellipsis={{ tooltip: record.sessionId }}>
        {record.sessionId}
      </Typography.Text>
    ),
  },
  {
    title: 'User-Agent',
    dataIndex: 'userAgent',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    width: 'var(--saas-spacing-360)',
    ellipsis: true,
    render: (_, record) =>
      record.userAgent ? (
        <Typography.Text copyable={{ text: record.userAgent }} ellipsis={{ tooltip: record.userAgent }}>
          {record.userAgent}
        </Typography.Text>
      ) : (
        '-'
      ),
  },
  {
    title: t('Actions', 'Actions'),
    valueType: 'option',
    fixed: 'right',
    width: 'var(--saas-spacing-180)',
    render: (_, record) => {
      const isSelfUser = record.userId === currentUserId;
      return (
        <TableActionBar
          isMobile={isMobile}
          items={buildActions([
            {
              key: 'detail',
              label: t('详情', 'Details'),
              onClick: () => onShowDetail(record),
            },
            {
              key: 'kick',
              label: t('踢出', 'Kick'),
              permission: 'system:online-user:kick',
              danger: true,
              disabled: isSelfUser,
              onClick: () => onKick(record),
            },
            {
              key: 'ban',
              label: t('封禁', 'Ban'),
              permission: 'system:online-user:ban',
              danger: true,
              disabled: isSelfUser,
              onClick: () => onBan(record),
            },
          ])}
        />
      );
    },
  },
];

const buildOnlineUserDetailColumns = (): ProDescriptionsItemProps<OnlineSessionRecord>[] => [
  { title: t('用户名', 'Username'), dataIndex: 'username' },
  { title: t('姓名', 'Full name'), dataIndex: 'realName', renderText: (value) => value || '-' },
  { title: t('昵称', 'Nickname'), dataIndex: 'nickname', renderText: (value) => value || '-' },
  { title: t('终端', 'Client'), dataIndex: 'clientType', renderText: (value) => value || '-' },
  { title: t('登录 IP', 'Login IP'), dataIndex: 'loginIp', renderText: (value) => value || '-' },
  { title: t('登录时间', 'Login time'), dataIndex: 'loginTime', renderText: (value) => formatDateTime(value) },
  { title: t('最近活跃', 'Last active'), dataIndex: 'lastActivityAt', renderText: (value) => formatDateTime(value) },
  { title: t('过期时间', 'Expiry time'), dataIndex: 'expireTime', renderText: (value) => formatDateTime(value) },
  { title: t('会话 ID', 'Session ID'), dataIndex: 'sessionId', renderText: (value) => value || '-' },
  { title: 'User-Agent', dataIndex: 'userAgent', renderText: (value) => value || '-' },
];

const OnlineUsersPage = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const { initialState } = useInitialStateModel();
  const actionPermission = useActionPermission();
  const buildActions = actionPermission.buildTableActions;
  const currentUser = initialState?.currentUser;
  const responsive = useResponsive();
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<OnlineSessionRecord | null>(null);
  const detailProps = useDetailProDescriptionsProps<OnlineSessionRecord>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedRecord || undefined,
  });
  const closeDetail = useCallback(() => {
    setDetailOpen(false);
    setSelectedRecord(null);
  }, []);
  const onShowDetail = useCallback((record: OnlineSessionRecord) => {
    setSelectedRecord(record);
    setDetailOpen(true);
  }, []);
  const canViewOnlineUsers = actionPermission.can('system:online-user:view');
  const reloadOnlineUsers = useCallback(() => {
    actionRef.current?.reload();
  }, []);
  const onKick = useCallback(
    (record: OnlineSessionRecord) => {
      confirmAction({
        title: t('踢出在线会话', 'Kick online session'),
        content: t('确定要踢出该会话吗？踢出后该会话将立即失效。', 'Kick this session? It will become invalid immediately.'),
        okText: t('确定踢出', 'Kick'),
        cancelText: t('取消', 'Cancel'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/system/online-users/${record.sessionId}`, {
            method: 'DELETE',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('会话已踢出', 'Session kicked'));
          reloadOnlineUsers();
        },
      });
    },
    [reloadOnlineUsers],
  );
  const onBan = useCallback(
    (record: OnlineSessionRecord) => {
      confirmAction({
        title: t('封禁账户', 'Ban account'),
        content: t('确定要封禁该账号吗？封禁后将清退该账号所有在线会话，并禁止后续登录。', 'Ban this account? It will remove all online sessions and block future sign-ins.'),
        okText: t('确定封禁', 'Ban'),
        cancelText: t('取消', 'Cancel'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/system/online-users/${record.userId}/ban`, {
            method: 'PATCH',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('账号已封禁', 'Account banned'));
          reloadOnlineUsers();
        },
      });
    },
    [reloadOnlineUsers],
  );
  const activityTimerRef = useRef<number | null>(null);
  const scheduleActivity = useCallback(() => {
    if (activityTimerRef.current) {
      window.clearTimeout(activityTimerRef.current);
    }
    activityTimerRef.current = window.setTimeout(() => {
      activityTimerRef.current = null;
      actionRef.current?.reload();
    }, 300);
  }, []);
  useEffect(() => {
    if (!currentUser?.sessionId || !canViewOnlineUsers) {
      return;
    }

    const stop = connectOnlineSessionStream({
      currentSessionId: currentUser.sessionId,
      onEvent: (event) => {
        if (event.action !== 'HEARTBEAT') {
          scheduleActivity();
        }
      },
    });

    return () => {
      stop();
      if (activityTimerRef.current) {
        window.clearTimeout(activityTimerRef.current);
        activityTimerRef.current = null;
      }
    };
  }, [canViewOnlineUsers, currentUser?.sessionId, scheduleActivity]);
  useEffect(() => {
    if (!currentUser?.sessionId || !canViewOnlineUsers) {
      return;
    }

    const timer = window.setInterval(() => {
      actionRef.current?.reload();
    }, 30000);

    return () => {
      window.clearInterval(timer);
    };
  }, [canViewOnlineUsers, currentUser?.sessionId]);
  const columns = useMemo(
    () =>
      buildOnlineUserColumns({
        currentUserId: currentUser?.userId,
        currentSessionId: currentUser?.sessionId,
        userCellGap: resolveResponsiveValue(APP_SPACING.microOffset, responsive.isMobile) as SpaceProps['size'],
        isMobile: responsive.isMobile,
        buildActions,
        onShowDetail,
        onKick,
        onBan,
      }),
    [buildActions, currentUser?.sessionId, currentUser?.userId, onBan, onKick, onShowDetail, responsive.isMobile],
  );
  const detailColumns = useMemo(() => buildOnlineUserDetailColumns(), []);

  return (
    <ManagementPage title={t('在线用户', 'Online users')} className="saas-online-users-page" ghost>
      <ManagementPageBody>
        <ManagementTable<OnlineSessionRecord>
          actionRef={actionRef}
          rowKey="sessionId"
          search={false}
          columns={columns}
          isMobile={responsive.isMobile}
          scroll={{ x: 1980 }}
          request={buildTableRequest((params) =>
            request<PagedResult<OnlineSessionRecord>>('/v1/system/online-users', {
              method: 'GET',
              params,
              ...API_OPTS.NO_REDIRECT,
            }),
          )}
          toolBarRender={() => [
            <Button key="refresh" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => actionRef.current?.reload()}>
              {t('刷新', 'Refresh')}
            </Button>,
          ]}
        />
      </ManagementPageBody>

      <ManagementDrawer
        title={selectedRecord ? `${t('在线会话详情', 'Online session details')} · ${selectedRecord.realName || selectedRecord.nickname || selectedRecord.username}` : t('在线会话详情', 'Online session details')}
        open={detailOpen && canViewOnlineUsers}
        onClose={closeDetail}
      >
        {selectedRecord ? (
          <ProDescriptions<OnlineSessionRecord>
            {...detailProps}
            columns={detailColumns}
          />
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default OnlineUsersPage;
