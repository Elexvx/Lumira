import { useEffect, useRef } from 'react';
import { PageContainer, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Modal, Space, Tag, Typography, message } from 'antd';
import { usePermission } from '@/hooks/usePermission';
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
  const actionRef = useRef<ActionType>();
  const reloadTimerRef = useRef<number | null>(null);
  const pollingTimerRef = useRef<number | null>(null);
  const { initialState } = useInitialStateModel();
  const { canAccess } = usePermission();
  const currentUser = initialState?.currentUser;
  const canViewOnlineUsers = canAccess('system:online-user:view');
  const canKickOnlineUser = canAccess('system:online-user:kick');
  const canBanOnlineUser = canAccess('system:online-user:ban');

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
  }, [canViewOnlineUsers, currentUser?.sessionId, currentUser?.userId, initialState?.currentTenant?.tenantId]);

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
  }, [canViewOnlineUsers, currentUser?.sessionId, initialState?.currentTenant?.tenantId]);

  const columns: ProColumns<OnlineSessionRecord>[] = [
    {
      title: '用户',
      dataIndex: 'username',
      width: 220,
      fixed: 'left',
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
      title: '租户ID',
      dataIndex: 'currentTenantId',
      hideInSearch: true,
      render: (_, record) =>
        record.currentTenantId === initialState?.currentTenant?.tenantId ? <Tag color="green">当前租户</Tag> : record.currentTenantId || '-',
    },
    {
      title: '终端',
      dataIndex: 'clientType',
      hideInSearch: true,
      render: (_, record) => record.clientType || '-',
    },
    {
      title: '登录 IP',
      dataIndex: 'loginIp',
      hideInSearch: true,
      render: (_, record) => record.loginIp || '-',
    },
    {
      title: '登录时间',
      dataIndex: 'loginTime',
      hideInSearch: true,
      render: (_, record) => formatDateTime(record.loginTime),
    },
    {
      title: '最近活跃',
      dataIndex: 'lastActivityAt',
      hideInSearch: true,
      render: (_, record) => formatDateTime(record.lastActivityAt),
    },
    {
      title: '过期时间',
      dataIndex: 'expireTime',
      hideInSearch: true,
      render: (_, record) => formatDateTime(record.expireTime),
    },
    {
      title: '会话 ID',
      dataIndex: 'sessionId',
      hideInSearch: true,
      width: 260,
      render: (_, record) => (
        <Typography.Text copyable ellipsis style={{ maxWidth: 240, display: 'inline-block' }}>
          {record.sessionId}
        </Typography.Text>
      ),
    },
    {
      title: 'User-Agent',
      dataIndex: 'userAgent',
      hideInSearch: true,
      ellipsis: true,
      render: (_, record) => record.userAgent || '-',
    },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 180,
      render: (_, record) => {
        const isSelfUser = record.userId === currentUser?.userId;
        return (
          <Space size={0}>
            {canKickOnlineUser ? (
              <Button
                type="link"
                size="small"
                danger
                disabled={isSelfUser}
                onClick={() => {
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
                }}
              >
                踢出
              </Button>
            ) : null}
            {canBanOnlineUser ? (
              <Button
                type="link"
                size="small"
                danger
                disabled={isSelfUser}
                onClick={() => {
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
                }}
              >
                封禁
              </Button>
            ) : null}
          </Space>
        );
      },
    },
  ];

  return (
    <PageContainer
      title="在线用户"
      className="saas-online-users-page"
      ghost
    >
      <ProTable<OnlineSessionRecord>
        actionRef={actionRef}
        rowKey="sessionId"
        search={false}
        options={false}
        columns={columns}
        pagination={{ showSizeChanger: true }}
        scroll={{ x: 1500 }}
        request={async (params) => {
          const { current, pageSize } = params;
          const result = await systemService.onlineUsers(
            {
              pageNo: current,
              pageSize,
            },
            { autoRedirectOnUnauthorized: false },
          );
          return {
            data: result.records,
            success: true,
            total: result.total,
          };
        }}
        toolBarRender={() => [
          <Button key="refresh" onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ]}
      />
    </PageContainer>
  );
};

export default OnlineUsersPage;
