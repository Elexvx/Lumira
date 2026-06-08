import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { history } from '@umijs/max';
import { getLocale } from '@umijs/max';
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
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type AuditRecord = AuditLogRecord;
type AuditTableRequest = NonNullable<ProTableProps<AuditRecord, Record<string, unknown>>['request']>;

type AuditLogType = 'login' | 'operation' | 'ai' | 'verification';

const timeRangeColumn: any = {
  title: t('时间范围', 'Time range'),
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
    SUCCESS: { color: 'green', text: t('成功', 'Success') },
    FAIL: { color: 'red', text: t('失败', 'Failed') },
    FAILED: { color: 'red', text: t('失败', 'Failed') },
    ERROR: { color: 'red', text: t('异常', 'Error') },
    RUNNING: { color: 'blue', text: t('运行中', 'Running') },
    ENABLED: { color: 'green', text: t('已启用', 'Enabled') },
    DISABLED: { color: 'default', text: t('已禁用', 'Disabled') },
  };
  const current = map[normalized];
  return <Tag color={current?.color || 'blue'}>{current?.text || label}</Tag>;
};

const buildDetailActionColumn = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): any => ({
      title: t('操作', 'Actions'),
  valueType: 'option',
  fixed: isDesktop ? 'right' : undefined,
  width: 'var(--saas-spacing-100)',
  render: (_: unknown, record: AuditRecord) => (
    <TableActionBar
      isMobile={isMobile}
      items={[
        {
          key: 'detail',
          label: t('详情', 'Details'),
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
  { title: t('用户名', 'Username'), dataIndex: 'username', importance: 1 },
  timeRangeColumn,
  {
    title: t('结果', 'Result'),
    dataIndex: 'logResult',
    importance: 1,
    responsive: ['xs', 'sm', 'md', 'lg', 'xl', 'xxl'],
    valueEnum: {
      SUCCESS: { text: t('成功', 'Success') },
      FAIL: { text: t('失败', 'Failed') },
      FAILED: { text: t('失败', 'Failed') },
      ERROR: { text: t('异常', 'Error') },
    },
    render: (_: unknown, record: AuditRecord) => renderStatusTag(record.logResult),
  },
  {
    title: t('原因', 'Reason'),
    dataIndex: 'failReason',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.failReason),
  },
  { title: t('时间', 'Time'), dataIndex: 'createdAt', search: false, importance: 1 },
  buildDetailActionColumn(onOpenDetail, isDesktop, isMobile),
];

const buildOperationAuditColumns = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): ProColumns<AuditRecord>[] => [
  { title: t('用户名', 'Username'), dataIndex: 'username', importance: 1 },
  timeRangeColumn,
  { title: t('模块', 'Module'), dataIndex: 'moduleName', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  { title: t('操作', 'Action'), dataIndex: 'actionName', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  { title: t('类型', 'Type'), dataIndex: 'operationType', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  {
    title: t('详情', 'Details'),
    dataIndex: 'detailMessage',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.detailMessage || record.failReason),
  },
  { title: t('时间', 'Time'), dataIndex: 'createdAt', search: false, importance: 1 },
  buildDetailActionColumn(onOpenDetail, isDesktop, isMobile),
];

const buildAiAuditColumns = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): ProColumns<AuditRecord>[] => [
  {
    title: t('数字员工 ID', 'Digital employee ID'),
    dataIndex: 'employeeId',
    importance: 1,
    renderFormItem: () => <InputNumber min={1} style={{ width: '100%' }} controls={false} />,
  },
  {
    title: t('技能编码', 'Skill code'),
    dataIndex: 'skillCode',
    importance: 2,
    renderFormItem: () => <Input placeholder={t('例如：chat', 'e.g. chat')} />,
    ellipsis: true,
  },
  {
    title: t('结果', 'Result'),
    dataIndex: 'resultStatus',
    importance: 1,
    valueEnum: {
      SUCCESS: { text: t('成功', 'Success') },
      FAIL: { text: t('失败', 'Failed') },
      FAILED: { text: t('失败', 'Failed') },
      ERROR: { text: t('异常', 'Error') },
    },
    renderFormItem: () => (
      <Select
        allowClear
        options={[
          { label: t('成功', 'Success'), value: 'SUCCESS' },
          { label: t('失败', 'Failed'), value: 'FAIL' },
          { label: t('异常', 'Error'), value: 'ERROR' },
        ]}
      />
    ),
    render: (_: unknown, record: AuditRecord) => renderStatusTag(record.logResult),
  },
  timeRangeColumn,
  { title: t('会话 ID', 'Conversation ID'), dataIndex: 'conversationId', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  { title: t('工具', 'Tool'), dataIndex: 'toolName', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  { title: t('权限模式', 'Permission mode'), dataIndex: 'permissionMode', search: false, responsive: ['lg', 'xl', 'xxl'] },
  {
    title: t('详情', 'Details'),
    dataIndex: 'detailMessage',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.detailMessage),
  },
  { title: t('时间', 'Time'), dataIndex: 'createdAt', search: false, importance: 1 },
  buildDetailActionColumn(onOpenDetail, isDesktop, isMobile),
];

const buildVerificationAuditColumns = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): ProColumns<AuditRecord>[] => [
  {
    title: t('渠道', 'Channel'),
    dataIndex: 'channel',
    importance: 1,
    renderFormItem: () => (
      <Select
        allowClear
        options={[
          { label: t('短信验证码', 'SMS code'), value: 'SMS' },
          { label: t('邮箱验证码', 'Email code'), value: 'EMAIL' },
        ]}
      />
    ),
    render: (_: unknown, record: AuditRecord) => {
      const channel = record.operationType;
      if (channel === 'SMS') {
        return <Tag color="blue">{t('短信验证码', 'SMS code')}</Tag>;
      }
      if (channel === 'EMAIL') {
        return <Tag color="purple">{t('邮箱验证码', 'Email code')}</Tag>;
      }
      return channel || '-';
    },
  },
  {
    title: t('场景', 'Scenario'),
    dataIndex: 'scene',
    importance: 1,
    renderFormItem: () => (
      <Select
        allowClear
        options={[
          { label: t('验证码登录', 'Code login'), value: 'LOGIN_CODE' },
          { label: t('二次验证', 'Second factor'), value: 'SECOND_FACTOR' },
          { label: t('绑定验证', 'Binding verification'), value: 'CONTACT_BIND' },
        ]}
      />
    ),
    render: (_: unknown, record: AuditRecord) => {
      const map: Record<string, string> = {
        LOGIN_CODE: t('验证码登录', 'Code login'),
        SECOND_FACTOR: t('二次验证', 'Second factor'),
        CONTACT_BIND: t('绑定验证', 'Binding verification'),
      };
      return map[record.actionName || ''] || record.actionName || '-';
    },
  },
  {
    title: t('结果', 'Result'),
    dataIndex: 'resultStatus',
    importance: 1,
    renderFormItem: () => (
      <Select
        allowClear
        options={[
          { label: t('成功', 'Success'), value: 'SUCCESS' },
          { label: t('失败', 'Failed'), value: 'FAIL' },
        ]}
      />
    ),
    render: (_: unknown, record: AuditRecord) => renderStatusTag(record.logResult),
  },
  timeRangeColumn,
  { title: t('用户', 'User'), dataIndex: 'username', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  {
    title: t('详情', 'Details'),
    dataIndex: 'detailMessage',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.detailMessage),
  },
  { title: t('时间', 'Time'), dataIndex: 'createdAt', search: false, importance: 1 },
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
        canViewLoginLogs ? { tab: t('登录日志', 'Login logs'), key: 'login' } : null,
        canViewOperationLogs ? { tab: t('操作日志', 'Operation logs'), key: 'operation' } : null,
        canViewOperationLogs ? { tab: t('验证码日志', 'Verification logs'), key: 'verification' } : null,
        canViewOperationLogs ? { tab: t('AI 调用记录', 'AI call logs'), key: 'ai' } : null,
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
      title={t('审计中心', 'Audit center')}
      tabList={tabList}
      tabActiveKey={activeLogType}
      onTabChange={handleTabChange}
    >
      <ManagementPageBody>
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
              {t('刷新', 'Refresh')}
            </Button>,
          ]}
        />
      </ManagementPageBody>

      <ManagementDrawer
        title={selectedRecord ? `${t('日志详情', 'Log details')} · ${selectedRecord.username || selectedRecord.moduleName || selectedRecord.id}` : t('日志详情', 'Log details')}
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
                { title: t('用户名', 'Username'), dataIndex: 'username', renderText: (value) => value || '-' },
                { title: t('会话 ID', 'Conversation ID'), dataIndex: 'conversationId', renderText: (value) => value ?? '-' },
                { title: t('数字员工 ID', 'Digital employee ID'), dataIndex: 'employeeId', renderText: (value) => value ?? '-' },
                { title: t('类型', 'Type'), dataIndex: 'logType', renderText: (value) => value || selectedRecord.operationType || '-' },
                { title: t('结果', 'Result'), dataIndex: 'logResult', renderText: (value) => value || '-' },
                { title: t('技能编码', 'Skill code'), dataIndex: 'skillCode', renderText: (value) => value || '-' },
                { title: t('工具', 'Tool'), dataIndex: 'toolName', renderText: (value) => value || '-' },
                { title: t('权限模式', 'Permission mode'), dataIndex: 'permissionMode', renderText: (value) => value || '-' },
                { title: 'RequestId', dataIndex: 'requestId', renderText: (value) => value || '-' },
                { title: 'TraceId', dataIndex: 'traceId', renderText: (value) => value || '-' },
                { title: t('时间', 'Time'), dataIndex: 'createdAt' },
              ]}
            />
            <ProDescriptions<AuditRecord>
              {...detailExtraProps}
              columns={[
                {
                  title: t('扩展信息', 'Additional info'),
                  dataIndex: 'detailMessage',
                  renderText: (_, entity) => entity.failReason || entity.detailMessage || entity.requestPayloadJson || t('无更多详情', 'No more details'),
                },
                {
                  title: t('请求内容', 'Request payload'),
                  dataIndex: 'requestPayloadJson',
                  renderText: (value) => value || '-',
                },
                {
                  title: t('响应内容', 'Response payload'),
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
