import { useMemo, useRef, useState } from 'react';
import { ProDescriptions, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, DatePicker, Input, InputNumber, Select, Space, Tag, Typography } from 'antd';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { TableActionBar } from '@/features/table/TableActionBar';
import { buildTableRequest } from '@/features/table/proTable';
import { auditService } from '@/services/audit';
import type { AuditLogRecord } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';
import { useActionPermission } from '@/features/permissions/useActionPermission';

type AuditLogType = 'login' | 'operation' | 'ai' | 'verification';
type AuditRecord = AuditLogRecord;

const timeRangeColumn = {
  title: '时间范围',
  dataIndex: 'range',
  hideInTable: true,
  renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
  search: {
    transform: (value) => {
      if (!Array.isArray(value) || value.length !== 2) {
        return {};
      }
      const [start, end] = value as unknown as [{ format: (format: string) => string }, { format: (format: string) => string }];
      return {
        startTime: start?.format?.('YYYY-MM-DD HH:mm:ss'),
        endTime: end?.format?.('YYYY-MM-DD HH:mm:ss'),
      };
    },
  },
} as ProColumns<AuditRecord>;

const renderStatusTag = (label?: string | null) => {
  if (!label) {
    return '-';
  }
  const normalized = label.toUpperCase();
  const map: Record<string, { color: string; text: string }> = {
    SUCCESS: { color: 'green', text: '成功' },
    FAIL: { color: 'red', text: '失败' },
    FAILED: { color: 'red', text: '失败' },
    ERROR: { color: 'red', text: '异常' },
    RUNNING: { color: 'blue', text: '运行中' },
    ENABLED: { color: 'green', text: '已启用' },
    DISABLED: { color: 'default', text: '已禁用' },
  };
  const current = map[normalized];
  return <Tag color={current?.color || 'blue'}>{current?.text || label}</Tag>;
};

const AuditOverviewPage = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const [logType, setLogType] = useState<AuditLogType>('login');
  const [selectedRecord, setSelectedRecord] = useState<AuditRecord | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const detailProps = useDetailProDescriptionsProps<AuditRecord>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedRecord || undefined,
  });
  const detailExtraProps = useDetailProDescriptionsProps<AuditRecord>({
    column: 1,
    dataSource: selectedRecord || undefined,
  });
  const canViewLoginLogs = actionPermission.can('audit:login:view');
  const canViewOperationLogs = actionPermission.can('audit:operation:view');
  const tabList = [
    canViewLoginLogs ? { tab: '登录日志', key: 'login' } : null,
    canViewOperationLogs ? { tab: '操作日志', key: 'operation' } : null,
    canViewOperationLogs ? { tab: '验证码日志', key: 'verification' } : null,
    canViewOperationLogs ? { tab: 'AI 调用记录', key: 'ai' } : null,
  ].filter((item): item is { tab: string; key: AuditLogType } => Boolean(item));
  const activeLogType = tabList.some((item) => item.key === logType) ? logType : tabList[0]?.key || 'login';
  const canReadActiveLog = activeLogType === 'login' ? canViewLoginLogs : canViewOperationLogs;

  const columns = useMemo<ProColumns<AuditRecord>[]>(() => {
    if (activeLogType === 'login') {
      return [
          { title: '用户名', dataIndex: 'username', importance: 1 },
          timeRangeColumn,
          {
            title: '结果',
            dataIndex: 'logResult',
            importance: 1,
            responsive: ['xs', 'sm', 'md', 'lg', 'xl', 'xxl'],
            valueEnum: {
              SUCCESS: { text: '成功' },
              FAIL: { text: '失败' },
              FAILED: { text: '失败' },
              ERROR: { text: '异常' },
            },
            render: (_, record) => renderStatusTag(record.logResult),
          },
          {
            title: '原因',
            dataIndex: 'failReason',
            search: false,
            responsive: ['lg', 'xl', 'xxl'],
            ellipsis: true,
            render: (_, record) =>
              record.failReason ? (
                <Typography.Text copyable={{ text: record.failReason }} ellipsis={{ tooltip: record.failReason }}>
                  {record.failReason}
                </Typography.Text>
              ) : (
                '-'
              ),
          },
          { title: '时间', dataIndex: 'createdAt', search: false, importance: 1 },
          {
            title: '操作',
            valueType: 'option',
            fixed: responsive.isDesktop ? 'right' : undefined,
            width: 100,
            render: (_, record) => (
              <TableActionBar
                isMobile={responsive.isMobile}
                items={[
                  {
                    key: 'detail',
                    label: '详情',
                    onClick: () => {
                      setSelectedRecord(record);
                      setDrawerOpen(true);
                    },
                  },
                ]}
              />
            ),
          },
        ];
    }

    if (activeLogType === 'ai') {
      return [
        {
          title: '数字员工 ID',
          dataIndex: 'employeeId',
          importance: 1,
          renderFormItem: () => <InputNumber min={1} style={{ width: '100%' }} controls={false} />,
        },
        {
          title: '技能编码',
          dataIndex: 'skillCode',
          importance: 2,
          renderFormItem: () => <Input placeholder="例如：chat" />,
          ellipsis: true,
        },
        {
          title: '结果',
          dataIndex: 'resultStatus',
          importance: 1,
          valueEnum: {
            SUCCESS: { text: '成功' },
            FAIL: { text: '失败' },
            FAILED: { text: '失败' },
            ERROR: { text: '异常' },
          },
          renderFormItem: () => (
            <Select
              allowClear
              options={[
                { label: '成功', value: 'SUCCESS' },
                { label: '失败', value: 'FAIL' },
                { label: '异常', value: 'ERROR' },
              ]}
            />
          ),
          render: (_, record) => renderStatusTag(record.logResult),
        },
        timeRangeColumn,
        { title: '会话 ID', dataIndex: 'conversationId', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
        { title: '工具', dataIndex: 'toolName', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
        { title: '权限模式', dataIndex: 'permissionMode', search: false, responsive: ['lg', 'xl', 'xxl'] },
        {
          title: '详情',
          dataIndex: 'detailMessage',
          search: false,
          responsive: ['lg', 'xl', 'xxl'],
          ellipsis: true,
          render: (_, record) =>
            record.detailMessage ? (
              <Typography.Text copyable={{ text: record.detailMessage }} ellipsis={{ tooltip: record.detailMessage }}>
                {record.detailMessage}
              </Typography.Text>
            ) : (
              '-'
            ),
        },
        { title: '时间', dataIndex: 'createdAt', search: false, importance: 1 },
        {
          title: '操作',
          valueType: 'option',
          fixed: responsive.isDesktop ? 'right' : undefined,
          width: 100,
          render: (_, record) => (
            <TableActionBar
              isMobile={responsive.isMobile}
              items={[
                {
                  key: 'detail',
                  label: '详情',
                  onClick: () => {
                    setSelectedRecord(record);
                    setDrawerOpen(true);
                  },
                },
              ]}
            />
          ),
        },
      ];
    }

    if (activeLogType === 'verification') {
      return [
        {
          title: '渠道',
          dataIndex: 'channel',
          importance: 1,
          renderFormItem: () => (
            <Select
              allowClear
              options={[
                { label: '短信验证码', value: 'SMS' },
                { label: '邮箱验证码', value: 'EMAIL' },
              ]}
            />
          ),
          render: (_, record) => {
            const channel = record.operationType;
            if (channel === 'SMS') {
              return <Tag color="blue">短信验证码</Tag>;
            }
            if (channel === 'EMAIL') {
              return <Tag color="purple">邮箱验证码</Tag>;
            }
            return channel || '-';
          },
        },
        {
          title: '场景',
          dataIndex: 'scene',
          importance: 1,
          renderFormItem: () => (
            <Select
              allowClear
              options={[
                { label: '验证码登录', value: 'LOGIN_CODE' },
                { label: '二次验证', value: 'SECOND_FACTOR' },
                { label: '绑定验证', value: 'CONTACT_BIND' },
              ]}
            />
          ),
          render: (_, record) => {
            const map: Record<string, string> = {
              LOGIN_CODE: '验证码登录',
              SECOND_FACTOR: '二次验证',
              CONTACT_BIND: '绑定验证',
            };
            return map[record.actionName || ''] || record.actionName || '-';
          },
        },
        {
          title: '结果',
          dataIndex: 'resultStatus',
          importance: 1,
          renderFormItem: () => (
            <Select
              allowClear
              options={[
                { label: '成功', value: 'SUCCESS' },
                { label: '失败', value: 'FAIL' },
              ]}
            />
          ),
          render: (_, record) => renderStatusTag(record.logResult),
        },
        timeRangeColumn,
        { title: '用户', dataIndex: 'username', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
        {
          title: '详情',
          dataIndex: 'detailMessage',
          search: false,
          responsive: ['md', 'lg', 'xl', 'xxl'],
          ellipsis: true,
          render: (_, record) =>
            record.detailMessage ? (
              <Typography.Text copyable={{ text: record.detailMessage }} ellipsis={{ tooltip: record.detailMessage }}>
                {record.detailMessage}
              </Typography.Text>
            ) : (
              '-'
            ),
        },
        { title: '时间', dataIndex: 'createdAt', search: false, importance: 1 },
        {
          title: '操作',
          valueType: 'option',
          fixed: responsive.isDesktop ? 'right' : undefined,
          width: 100,
          render: (_, record) => (
            <TableActionBar
              isMobile={responsive.isMobile}
              items={[
                {
                  key: 'detail',
                  label: '详情',
                  onClick: () => {
                    setSelectedRecord(record);
                    setDrawerOpen(true);
                  },
                },
              ]}
            />
          ),
        },
      ];
    }

    return [
          { title: '用户名', dataIndex: 'username', importance: 1 },
          timeRangeColumn,
          { title: '模块', dataIndex: 'moduleName', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
          { title: '操作', dataIndex: 'actionName', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
          { title: '类型', dataIndex: 'operationType', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
          {
            title: '详情',
            dataIndex: 'detailMessage',
            search: false,
            responsive: ['lg', 'xl', 'xxl'],
            ellipsis: true,
            render: (_, record) => {
              const content = record.detailMessage || record.failReason || '';
              return content ? (
                <Typography.Text copyable={{ text: content }} ellipsis={{ tooltip: content }}>
                  {content}
                </Typography.Text>
              ) : (
                '-'
              );
            },
          },
          { title: '时间', dataIndex: 'createdAt', search: false, importance: 1 },
          {
            title: '操作',
            valueType: 'option',
            fixed: responsive.isDesktop ? 'right' : undefined,
            width: 100,
            render: (_, record) => (
              <TableActionBar
                isMobile={responsive.isMobile}
                items={[
                  {
                    key: 'detail',
                    label: '详情',
                    onClick: () => {
                      setSelectedRecord(record);
                      setDrawerOpen(true);
                    },
                  },
                ]}
              />
            ),
          },
        ];
  }, [activeLogType, responsive.isDesktop, responsive.isMobile]);

  return (
    <ManagementPage
      title="审计中心"
      tabList={tabList}
      tabActiveKey={activeLogType}
      onTabChange={(key) => {
        setLogType(key as AuditLogType);
        actionRef.current?.reload();
      }}
    >
      <ManagementTable<AuditRecord>
          key={activeLogType}
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
          pagination={{ showSizeChanger: true, pageSize: 10 }}
          request={buildTableRequest((params) =>
            canReadActiveLog
              ? activeLogType === 'login'
                ? auditService.loginLogs(params, { autoRedirectOnUnauthorized: false })
                : activeLogType === 'ai'
                  ? auditService.aiCallLogs(params, { autoRedirectOnUnauthorized: false })
                  : activeLogType === 'verification'
                    ? auditService.verificationLogs(params, { autoRedirectOnUnauthorized: false })
                    : auditService.operationLogs(params, { autoRedirectOnUnauthorized: false })
              : Promise.resolve({ records: [], total: 0 }),
          )}
          toolBarRender={() => [
            <Button key="refresh" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => actionRef.current?.reload()}>
              刷新
            </Button>,
          ]}
      />

      <ManagementDrawer
        title={selectedRecord ? `日志详情 · ${selectedRecord.username || selectedRecord.moduleName || selectedRecord.id}` : '日志详情'}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setSelectedRecord(null);
        }}
      >
        {selectedRecord ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <ProDescriptions<AuditRecord>
              {...detailProps}
              columns={[
                { title: '用户名', dataIndex: 'username', renderText: (value) => value || '-' },
                { title: '会话 ID', dataIndex: 'conversationId', renderText: (value) => value ?? '-' },
                { title: '数字员工 ID', dataIndex: 'employeeId', renderText: (value) => value ?? '-' },
                { title: '类型', dataIndex: 'logType', renderText: (value) => value || selectedRecord.operationType || '-' },
                { title: '结果', dataIndex: 'logResult', renderText: (value) => value || '-' },
                { title: '技能编码', dataIndex: 'skillCode', renderText: (value) => value || '-' },
                { title: '工具', dataIndex: 'toolName', renderText: (value) => value || '-' },
                { title: '权限模式', dataIndex: 'permissionMode', renderText: (value) => value || '-' },
                { title: 'RequestId', dataIndex: 'requestId', renderText: (value) => value || '-' },
                { title: 'TraceId', dataIndex: 'traceId', renderText: (value) => value || '-' },
                { title: '时间', dataIndex: 'createdAt' },
              ]}
            />
            <ProDescriptions<AuditRecord>
              {...detailExtraProps}
              columns={[
                {
                  title: '扩展信息',
                  dataIndex: 'detailMessage',
                  renderText: (_, entity) => entity.failReason || entity.detailMessage || entity.requestPayloadJson || '无更多详情',
                },
                {
                  title: '请求内容',
                  dataIndex: 'requestPayloadJson',
                  renderText: (value) => value || '-',
                },
                {
                  title: '响应内容',
                  dataIndex: 'responsePayloadJson',
                  renderText: (value) => value || '-',
                },
              ]}
            />
          </Space>
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default AuditOverviewPage;
