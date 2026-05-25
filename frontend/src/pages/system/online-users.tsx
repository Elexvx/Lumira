import { useEffect, useMemo, useRef, useState } from 'react';
import { ProDescriptions, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Modal, Space, Tag, Typography, message } from 'antd';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { usePermissionActions } from '@/features/permissions/usePermissionActions';
import { TableActionBar } from '@/features/table/TableActionBar';
import { buildTableRequest } from '@/features/table/proTable';
import { useResponsive } from '@/hooks/useResponsive';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { systemService } from '@/services/system';
import { connectOnlineSessionStream } from '@/services/system/onlineUsers';
import type { OnlineSessionRecord } from '@/types/api';

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

const OnlineUsersPage = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const reloadTimerRef = useRef<number | null>(null);
  const pollingTimerRef = useRef<number | null>(null);
  const { initialState } = useInitialStateModel();
  const actionPermission = useActionPermission();
  const { buildActions } = usePermissionActions();
  const responsive = useResponsive();
  const currentUser = initialState?.currentUser;
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<OnlineSessionRecord | null>(null);
  const canViewOnlineUsers = actionPermission.can('system:online-user:view');
  const detailProps = useDetailProDescriptionsProps<OnlineSessionRecord>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedRecord || undefined,
  });

  useEffect(() => {
    if (!currentUser?.sessionId || !canViewOnlineUsers) {
      return;
    }

    const scheduleReload = () => {
      if (reloadTimerRef.current) {
        window.clearTimeout(reloadTimerRef.current);
      }
      reloadTimerRef.current = window.setTimeout(() => {
        reloadTimerRef.current = null;
        actionRef.current?.reload();
      }, 300);
    };

    const stop = connectOnlineSessionStream({
      currentSessionId: currentUser.sessionId,
      onEvent: (event) => {
        if (event.action !== 'HEARTBEAT') {
          scheduleReload();
        }
      },
    });

    return () => {
      stop();
      if (reloadTimerRef.current) {
        window.clearTimeout(reloadTimerRef.current);
        reloadTimerRef.current = null;
      }
    };
  }, [canViewOnlineUsers, currentUser?.sessionId, currentUser?.userId]);

  useEffect(() => {
    if (!currentUser?.sessionId || !canViewOnlineUsers) {
      return;
    }

    if (pollingTimerRef.current) {
      window.clearInterval(pollingTimerRef.current);
    }

    pollingTimerRef.current = window.setInterval(() => {
      actionRef.current?.reload();
    }, 30000);

    return () => {
      if (pollingTimerRef.current) {
        window.clearInterval(pollingTimerRef.current);
        pollingTimerRef.current = null;
      }
    };
  }, [canViewOnlineUsers, currentUser?.sessionId]);

  const columns: ProColumns<OnlineSessionRecord>[] = useMemo(
    () => [
    {
      title: '用户',
      dataIndex: 'username',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Space size={6} wrap>
            <Typography.Text strong>{record.realName || record.nickname || record.username}</Typography.Text>
            {record.userId === currentUser?.userId ? <Tag color="orange">当前账号</Tag> : null}
            {record.sessionId === currentUser?.sessionId ? <Tag color="blue">当前会话</Tag> : null}
          </Space>
          <Typography.Text type="secondary">{record.username}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '终端',
      dataIndex: 'clientType',
      search: false,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      width: 120,
      render: (_, record) => (
        <Typography.Text className="saas-online-users-page__cell-text" ellipsis={{ tooltip: record.clientType || '-' }}>
          {record.clientType || '-'}
        </Typography.Text>
      ),
    },
    {
      title: '登录 IP',
      dataIndex: 'loginIp',
      search: false,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      width: 160,
      render: (_, record) => (
        <Typography.Text className="saas-online-users-page__cell-text" ellipsis={{ tooltip: record.loginIp || '-' }}>
          {record.loginIp || '-'}
        </Typography.Text>
      ),
    },
    {
      title: '登录时间',
      dataIndex: 'loginTime',
      search: false,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      width: 180,
      render: (_, record) => (
        <Typography.Text className="saas-online-users-page__cell-text" ellipsis={{ tooltip: formatDateTime(record.loginTime) }}>
          {formatDateTime(record.loginTime)}
        </Typography.Text>
      ),
    },
    {
      title: '最近活跃',
      dataIndex: 'lastActivityAt',
      search: false,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      width: 180,
      render: (_, record) => (
        <Typography.Text className="saas-online-users-page__cell-text" ellipsis={{ tooltip: formatDateTime(record.lastActivityAt) }}>
          {formatDateTime(record.lastActivityAt)}
        </Typography.Text>
      ),
    },
    {
      title: '过期时间',
      dataIndex: 'expireTime',
      search: false,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      width: 180,
      render: (_, record) => (
        <Typography.Text className="saas-online-users-page__cell-text" ellipsis={{ tooltip: formatDateTime(record.expireTime) }}>
          {formatDateTime(record.expireTime)}
        </Typography.Text>
      ),
    },
    {
      title: '会话 ID',
      dataIndex: 'sessionId',
      search: false,
      width: 260,
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
      width: 360,
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
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 180,
      render: (_, record) => {
        const isSelfUser = record.userId === currentUser?.userId;
        return (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={buildActions([
              {
                key: 'detail',
                label: '详情',
                onClick: () => {
                  setSelectedRecord(record);
                  setDetailOpen(true);
                },
              },
              {
                key: 'kick',
                label: '踢出',
                permission: 'system:online-user:kick',
                danger: true,
                disabled: isSelfUser,
                onClick: () => {
                  Modal.confirm({
                    title: '踢出在线会话',
                    content: '确定要踢出该会话吗？踢出后该会话将立即失效。',
                    okText: '确定踢出',
                    cancelText: '取消',
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await systemService.kickOnlineUser(record.sessionId, { autoRedirectOnUnauthorized: false });
                      message.success('会话已踢出');
                      actionRef.current?.reload();
                    },
                  });
                },
              },
              {
                key: 'ban',
                label: '封禁',
                permission: 'system:online-user:ban',
                danger: true,
                disabled: isSelfUser,
                onClick: () => {
                  Modal.confirm({
                    title: '封禁账户',
                    content: '确定要封禁该账号吗？封禁后将清退该账号所有在线会话，并禁止后续登录。',
                    okText: '确定封禁',
                    cancelText: '取消',
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await systemService.banOnlineUser(record.userId, { autoRedirectOnUnauthorized: false });
                      message.success('账号已封禁');
                      actionRef.current?.reload();
                    },
                  });
                },
              },
            ])}
          />
        );
      },
    },
    ],
    [buildActions, currentUser?.sessionId, currentUser?.userId, responsive.isMobile],
  );

  return (
    <ManagementPage
      title="在线用户"
      className="saas-online-users-page"
      ghost
    >
      <ManagementTable<OnlineSessionRecord>
          actionRef={actionRef}
          rowKey="sessionId"
          search={false}
          columns={columns}
          isMobile={responsive.isMobile}
          scroll={{ x: 1840 }}
          request={buildTableRequest((params) => systemService.onlineUsers(params, { autoRedirectOnUnauthorized: false }))}
          toolBarRender={() => [
            <Button key="refresh" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => actionRef.current?.reload()}>
              刷新
            </Button>,
          ]}
      />

      <ManagementDrawer
        title={selectedRecord ? `在线会话详情 · ${selectedRecord.realName || selectedRecord.nickname || selectedRecord.username}` : '在线会话详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setSelectedRecord(null);
        }}
      >
        {selectedRecord ? (
          <ProDescriptions<OnlineSessionRecord>
            {...detailProps}
            columns={[
              { title: '用户名', dataIndex: 'username' },
              { title: '姓名', dataIndex: 'realName', renderText: (value) => value || '-' },
              { title: '昵称', dataIndex: 'nickname', renderText: (value) => value || '-' },
              { title: '终端', dataIndex: 'clientType', renderText: (value) => value || '-' },
              { title: '登录 IP', dataIndex: 'loginIp', renderText: (value) => value || '-' },
              { title: '登录时间', dataIndex: 'loginTime', renderText: (value) => formatDateTime(value) },
              { title: '最近活跃', dataIndex: 'lastActivityAt', renderText: (value) => formatDateTime(value) },
              { title: '过期时间', dataIndex: 'expireTime', renderText: (value) => formatDateTime(value) },
              { title: '会话 ID', dataIndex: 'sessionId', renderText: (value) => value || '-' },
              { title: 'User-Agent', dataIndex: 'userAgent', renderText: (value) => value || '-' },
            ]}
          />
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default OnlineUsersPage;
