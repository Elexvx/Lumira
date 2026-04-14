import { useEffect, useMemo, useRef, useState } from 'react';
import { PageContainer, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Drawer, Modal, Space, Tag, Typography, message } from 'antd';
import { PageDetailProDescriptions } from '@/components/PageDetailDescriptions';
import { usePermission } from '@/hooks/usePermission';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { systemService } from '@/services/system';
import { connectOnlineSessionStream } from '@/services/system/onlineUsers';
import type { OnlineSessionRecord } from '@/types/api';
import { buildResponsivePagination, buildResponsiveScroll, normalizeResponsiveColumns, ResponsiveActions, ResponsiveText, useResponsiveTable } from '@/components/ResponsiveTable';

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
  const responsive = useResponsiveTable();
  const currentUser = initialState?.currentUser;
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<OnlineSessionRecord | null>(null);
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

  const columns: ProColumns<OnlineSessionRecord>[] = useMemo(
    () => [
    {
      title: '用户',
      dataIndex: 'username',
      width: 220,
      importance: 1,
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
      importance: 2,
      responsiveLevel: ['tablet', 'desktop'],
      render: (_, record) =>
        record.currentTenantId === initialState?.currentTenant?.tenantId ? <Tag color="green">当前租户</Tag> : record.currentTenantId || '-',
    },
    {
      title: '终端',
      dataIndex: 'clientType',
      hideInSearch: true,
      importance: 2,
      responsiveLevel: ['tablet', 'desktop'],
      render: (_, record) => record.clientType || '-',
    },
    {
      title: '登录 IP',
      dataIndex: 'loginIp',
      hideInSearch: true,
      importance: 2,
      responsiveLevel: ['tablet', 'desktop'],
      render: (_, record) => record.loginIp || '-',
    },
    {
      title: '登录时间',
      dataIndex: 'loginTime',
      hideInSearch: true,
      importance: 2,
      responsiveLevel: ['tablet', 'desktop'],
      render: (_, record) => formatDateTime(record.loginTime),
    },
    {
      title: '最近活跃',
      dataIndex: 'lastActivityAt',
      hideInSearch: true,
      importance: 2,
      responsiveLevel: ['tablet', 'desktop'],
      render: (_, record) => formatDateTime(record.lastActivityAt),
    },
    {
      title: '过期时间',
      dataIndex: 'expireTime',
      hideInSearch: true,
      importance: 2,
      responsiveLevel: ['tablet', 'desktop'],
      render: (_, record) => formatDateTime(record.expireTime),
    },
    {
      title: '会话 ID',
      dataIndex: 'sessionId',
      hideInSearch: true,
      width: 260,
      importance: 3,
      responsiveLevel: 'desktop',
      ellipsisText: true,
      render: (_, record) => <ResponsiveText value={record.sessionId} copyable />,
    },
    {
      title: 'User-Agent',
      dataIndex: 'userAgent',
      hideInSearch: true,
      importance: 3,
      responsiveLevel: 'desktop',
      ellipsisText: true,
      render: (_, record) => <ResponsiveText value={record.userAgent || '-'} copyable={Boolean(record.userAgent)} />,
    },
    {
      title: '操作',
      valueType: 'option',
      importance: 0,
      desktopFixed: 'right',
      width: 180,
      render: (_, record) => {
        const isSelfUser = record.userId === currentUser?.userId;
        return (
          <ResponsiveActions
            level={responsive.level}
            items={[
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
                hidden: !canKickOnlineUser,
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
                hidden: !canBanOnlineUser,
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
            ]}
          />
        );
      },
    },
    ],
    [canBanOnlineUser, canKickOnlineUser, currentUser?.sessionId, currentUser?.userId, initialState?.currentTenant?.tenantId, responsive.level],
  );

  const responsiveColumns = useMemo(() => normalizeResponsiveColumns(columns, responsive.level), [columns, responsive.level]);

  return (
    <PageContainer
      title="在线用户"
      className="saas-online-users-page saas-management-page"
      ghost
    >
      <div className="saas-table-wrap">
        <ProTable<OnlineSessionRecord>
          actionRef={actionRef}
          rowKey="sessionId"
          search={false}
          options={false}
          columns={responsiveColumns}
          pagination={buildResponsivePagination({ showSizeChanger: true }, responsive)}
          scroll={buildResponsiveScroll(responsiveColumns, responsive)}
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
            <Button key="refresh" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => actionRef.current?.reload()}>
              刷新
            </Button>,
          ]}
        />
      </div>

      <Drawer
        title={selectedRecord ? `在线会话详情 · ${selectedRecord.realName || selectedRecord.nickname || selectedRecord.username}` : '在线会话详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setSelectedRecord(null);
        }}
        width={720}
        destroyOnClose
      >
        {selectedRecord ? (
          <PageDetailProDescriptions<OnlineSessionRecord>
            column={responsive.isMobile ? 1 : 2}
            dataSource={selectedRecord}
            columns={[
              { title: '用户名', dataIndex: 'username' },
              { title: '姓名', dataIndex: 'realName', renderText: (value) => value || '-' },
              { title: '昵称', dataIndex: 'nickname', renderText: (value) => value || '-' },
              { title: '租户ID', dataIndex: 'currentTenantId', renderText: (value) => value ?? '-' },
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
      </Drawer>
    </PageContainer>
  );
};

export default OnlineUsersPage;
