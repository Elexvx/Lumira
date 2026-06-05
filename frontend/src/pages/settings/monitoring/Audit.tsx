import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementTable } from '@/features/management/ManagementTable';
import { history } from '@umijs/max';
import { useCallback, useMemo, useRef, useState } from 'react';
import { ProDescriptions, type ActionType, type ProColumns, type ProTableProps } from '@ant-design/pro-components';
import { Button, DatePicker, Input, InputNumber, Select, Space, Tag, Typography } from 'antd';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { request } from '@/services/common/request';
import type { AuditLogRecord } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import { useResponsive } from '@/hooks/useResponsive';
import type { PagedResponse } from '@/features/table/proTableRequest';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

type AuditRecord = AuditLogRecord;
type AuditTableRequest = NonNullable<ProTableProps<AuditRecord, Record<string, unknown>>['request']>;

type AuditLogType = 'login' | 'operation' | 'ai' | 'verification';

const timeRangeColumn: any = {
  title: '时间范围',
  dataIndex: 'range',
  hideInTable: true,
  renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
  search: {
    transform: (value: unknown) => {
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
};

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

const buildDetailActionColumn = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): any => ({
  title: '操作',
  valueType: 'option',
  fixed: isDesktop ? 'right' : undefined,
  width: 100,
  render: (_: unknown, record: AuditRecord) => (
    <TableActionBar
      isMobile={isMobile}
      items={[
        {
          key: 'detail',
          label: '详情',
          onClick: () => onOpenDetail(record),
        },
      ]}
    />
  ),
});

const buildDetailText = (content?: string | null) => {
  if (!content) {
    return '-';
  }

  return (
    <Typography.Text copyable={{ text: content }} ellipsis={{ tooltip: content }}>
      {content}
    </Typography.Text>
  );
};

const buildLoginAuditColumns = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): ProColumns<AuditRecord>[] => [
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
    render: (_: unknown, record: AuditRecord) => renderStatusTag(record.logResult),
  },
  {
    title: '原因',
    dataIndex: 'failReason',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.failReason),
  },
  { title: '时间', dataIndex: 'createdAt', search: false, importance: 1 },
  buildDetailActionColumn(onOpenDetail, isDesktop, isMobile),
];

const buildOperationAuditColumns = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): ProColumns<AuditRecord>[] => [
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
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.detailMessage || record.failReason),
  },
  { title: '时间', dataIndex: 'createdAt', search: false, importance: 1 },
  buildDetailActionColumn(onOpenDetail, isDesktop, isMobile),
];

const buildAiAuditColumns = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): ProColumns<AuditRecord>[] => [
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
    render: (_: unknown, record: AuditRecord) => renderStatusTag(record.logResult),
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
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.detailMessage),
  },
  { title: '时间', dataIndex: 'createdAt', search: false, importance: 1 },
  buildDetailActionColumn(onOpenDetail, isDesktop, isMobile),
];

const buildVerificationAuditColumns = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): ProColumns<AuditRecord>[] => [
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
    render: (_: unknown, record: AuditRecord) => {
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
    render: (_: unknown, record: AuditRecord) => {
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
    render: (_: unknown, record: AuditRecord) => renderStatusTag(record.logResult),
  },
  timeRangeColumn,
  { title: '用户', dataIndex: 'username', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  {
    title: '详情',
    dataIndex: 'detailMessage',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.detailMessage),
  },
  { title: '时间', dataIndex: 'createdAt', search: false, importance: 1 },
  buildDetailActionColumn(onOpenDetail, isDesktop, isMobile),
];

const buildAuditColumns = ({ activeLogType, isDesktop, isMobile, onOpenDetail }: { activeLogType: AuditLogType; isDesktop: boolean; isMobile: boolean; onOpenDetail: (record: AuditRecord) => void }): ProColumns<AuditRecord>[] => {
  if (activeLogType === 'login') {
    return buildLoginAuditColumns(onOpenDetail, isDesktop, isMobile);
  }

  if (activeLogType === 'ai') {
    return buildAiAuditColumns(onOpenDetail, isDesktop, isMobile);
  }

  if (activeLogType === 'verification') {
    return buildVerificationAuditColumns(onOpenDetail, isDesktop, isMobile);
  }

  return buildOperationAuditColumns(onOpenDetail, isDesktop, isMobile);
};

const AuditOverviewPage = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const [logType, setLogType] = useState<AuditLogType>('login');
  const [selectedRecord, setSelectedRecord] = useState<AuditRecord | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const canViewLoginLogs = actionPermission.can('audit:login:view');
  const canViewOperationLogs = actionPermission.can('audit:operation:view');
  const tabList = useMemo(
    () =>
      [
        canViewLoginLogs ? { tab: '登录日志', key: 'login' } : null,
        canViewOperationLogs ? { tab: '操作日志', key: 'operation' } : null,
        canViewOperationLogs ? { tab: '验证码日志', key: 'verification' } : null,
        canViewOperationLogs ? { tab: 'AI 调用记录', key: 'ai' } : null,
      ].filter((item): item is { tab: string; key: AuditLogType } => Boolean(item)),
    [canViewLoginLogs, canViewOperationLogs],
  );

  const resolvedActiveTab = tabList.some((item) => item.key === logType) ? logType : tabList[0]?.key || null;
  const activeLogType = resolvedActiveTab || 'login';
  const canReadActiveLog = activeLogType === 'login' ? canViewLoginLogs : canViewOperationLogs;

  const handleOpenDetail = useCallback((record: AuditRecord) => {
    setSelectedRecord(record);
    setDrawerOpen(true);
  }, []);

  const columns = useMemo<ProColumns<AuditRecord>[]>(
    () =>
      buildAuditColumns({
        activeLogType,
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        onOpenDetail: handleOpenDetail,
      }),
    [activeLogType, handleOpenDetail, responsive.isDesktop, responsive.isMobile],
  );

  const tableRequest: AuditTableRequest = useMemo(
    () =>
      buildTableRequest((params) =>
        canReadActiveLog
          ? activeLogType === 'login'
            ? request<PagedResponse<AuditLogRecord>>('/v1/audit/login-logs', {
                method: 'GET',
                params,
                ...API_OPTS.NO_REDIRECT,
              })
            : activeLogType === 'ai'
              ? request<PagedResponse<AuditLogRecord>>('/v1/audit/ai-call-logs', {
                  method: 'GET',
                  params,
                  ...API_OPTS.NO_REDIRECT,
                })
              : activeLogType === 'verification'
                ? request<PagedResponse<AuditLogRecord>>('/v1/audit/verification-logs', {
                    method: 'GET',
                    params,
                    ...API_OPTS.NO_REDIRECT,
                  })
                : request<PagedResponse<AuditLogRecord>>('/v1/audit/operation-logs', {
                    method: 'GET',
                    params,
                    ...API_OPTS.NO_REDIRECT,
                  })
          : Promise.resolve({ records: [], total: 0 }),
      ),
    [activeLogType, canReadActiveLog],
  );

  const handleTabChange = useCallback((key: string) => {
    setLogType(key as AuditLogType);
    actionRef.current?.reload();
  }, []);

  const handleCloseDetail = useCallback(() => {
    setDrawerOpen(false);
    setSelectedRecord(null);
  }, []);

  const detailProps = useDetailProDescriptionsProps<AuditRecord>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedRecord || undefined,
  });
  const detailExtraProps = useDetailProDescriptionsProps<AuditRecord>({
    column: 1,
    dataSource: selectedRecord || undefined,
  });

  if (!resolvedActiveTab) {
    history.replace('/403');
    return null;
  }

  return (
    <ManagementPage
      title="审计中心"
      tabList={tabList}
      tabActiveKey={activeLogType}
      onTabChange={handleTabChange}
    >
      <ManagementTable<AuditRecord>
        key={activeLogType}
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        isMobile={responsive.isMobile}
        search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
        pagination={{ showSizeChanger: true, pageSize: 10 }}
        request={tableRequest}
        toolBarRender={() => [
          <Button key="refresh" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ]}
      />

      <ManagementDrawer
        title={selectedRecord ? `日志详情 · ${selectedRecord.username || selectedRecord.moduleName || selectedRecord.id}` : '日志详情'}
        open={drawerOpen}
        onClose={handleCloseDetail}
      >
        {selectedRecord ? (
          <Space
            direction="vertical"
            size={resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile)}
            style={{ width: '100%' }}
          >
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
