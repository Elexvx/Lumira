import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { StandardDateTimeRangePicker } from '@/components/date/StandardDateTimeRangePicker';
import { history } from '@umijs/max';
import { useCallback, useMemo, useRef, useState } from 'react';
import { ProDescriptions, type ActionType, type ProColumns, type ProTableProps } from '@ant-design/pro-components';
import { Button, Select, Space, Tag, Typography } from 'antd';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { request } from '@/services/common/request';
import type { AuditLogRecord } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import { useResponsive } from '@/hooks/useResponsive';
import type { PagedResponse } from '@/features/table/proTableRequest';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

type AuditRecord = AuditLogRecord;
type AuditTableRequest = NonNullable<ProTableProps<AuditRecord, Record<string, unknown>>['request']>;

type AuditLogType = 'login' | 'operation' | 'verification';

const timeRangeColumn: any = {
  title: t('ui.settings.monitoring.audit.timeRange'),
  dataIndex: 'range',
  hideInTable: true,
  renderFormItem: () => <StandardDateTimeRangePicker style={{ width: '100%' }} />,
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
    SUCCESS: { color: 'green', text: t('ui.settings.monitoring.audit.success') },
    FAIL: { color: 'red', text: t('ui.settings.monitoring.audit.failed') },
    FAILED: { color: 'red', text: t('ui.settings.monitoring.audit.failed') },
    ERROR: { color: 'red', text: t('ui.settings.monitoring.audit.error') },
    RUNNING: { color: 'blue', text: t('ui.settings.monitoring.audit.running') },
    ENABLED: { color: 'green', text: t('ui.settings.monitoring.audit.enabled') },
    DISABLED: { color: 'default', text: t('ui.settings.monitoring.audit.disabled') },
  };
  const current = map[normalized];
  return <Tag color={current?.color || 'blue'}>{current?.text || label}</Tag>;
};

const buildDetailActionColumn = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): any => ({
      title: t('ui.settings.monitoring.audit.actions'),
  valueType: 'option',
  fixed: isDesktop ? 'right' : undefined,
  width: 96,
  align: 'center',
  className: 'saas-table-action-column saas-table-action-column--compact',
  render: (_: unknown, record: AuditRecord) => (
    <TableActionBar
      isMobile={isMobile}
      items={[
        {
          key: 'detail',
          label: t('ui.settings.monitoring.audit.details'),
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
  { title: t('ui.settings.monitoring.audit.username'), dataIndex: 'username', importance: 1 },
  timeRangeColumn,
  {
    title: t('ui.settings.monitoring.audit.result'),
    dataIndex: 'logResult',
    importance: 1,
    responsive: ['xs', 'sm', 'md', 'lg', 'xl', 'xxl'],
    valueEnum: {
      SUCCESS: { text: t('ui.settings.monitoring.audit.success') },
      FAIL: { text: t('ui.settings.monitoring.audit.failed') },
      FAILED: { text: t('ui.settings.monitoring.audit.failed') },
      ERROR: { text: t('ui.settings.monitoring.audit.error') },
    },
    render: (_: unknown, record: AuditRecord) => renderStatusTag(record.logResult),
  },
  {
    title: t('ui.settings.monitoring.audit.reason'),
    dataIndex: 'failReason',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.failReason),
  },
  { title: t('ui.settings.monitoring.audit.time'), dataIndex: 'createdAt', search: false, importance: 1 },
  buildDetailActionColumn(onOpenDetail, isDesktop, isMobile),
];

const buildOperationAuditColumns = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): ProColumns<AuditRecord>[] => [
  { title: t('ui.settings.monitoring.audit.username'), dataIndex: 'username', importance: 1 },
  timeRangeColumn,
  { title: t('ui.settings.monitoring.audit.module'), dataIndex: 'moduleName', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  { title: t('ui.settings.monitoring.audit.action'), dataIndex: 'actionName', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  { title: t('ui.settings.monitoring.audit.type'), dataIndex: 'operationType', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  {
    title: t('ui.settings.monitoring.audit.details'),
    dataIndex: 'detailMessage',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.detailMessage || record.failReason),
  },
  { title: t('ui.settings.monitoring.audit.time'), dataIndex: 'createdAt', search: false, importance: 1 },
  buildDetailActionColumn(onOpenDetail, isDesktop, isMobile),
];

const buildVerificationAuditColumns = (
  onOpenDetail: (record: AuditRecord) => void,
  isDesktop: boolean,
  isMobile: boolean,
): ProColumns<AuditRecord>[] => [
  {
    title: t('ui.settings.monitoring.audit.channel'),
    dataIndex: 'channel',
    importance: 1,
    renderFormItem: () => (
      <Select
        allowClear
        options={[
          { label: t('ui.settings.monitoring.audit.smsCode'), value: 'SMS' },
          { label: t('ui.settings.monitoring.audit.emailCode'), value: 'EMAIL' },
        ]}
      />
    ),
    render: (_: unknown, record: AuditRecord) => {
      const channel = record.operationType;
      if (channel === 'SMS') {
        return <Tag color="blue">{t('ui.settings.monitoring.audit.smsCode')}</Tag>;
      }
      if (channel === 'EMAIL') {
        return <Tag color="purple">{t('ui.settings.monitoring.audit.emailCode')}</Tag>;
      }
      return channel || '-';
    },
  },
  {
    title: t('ui.settings.monitoring.audit.scenario'),
    dataIndex: 'scene',
    importance: 1,
    renderFormItem: () => (
      <Select
        allowClear
        options={[
          { label: t('ui.settings.monitoring.audit.codeLogin'), value: 'LOGIN_CODE' },
          { label: t('ui.settings.monitoring.audit.secondFactor'), value: 'SECOND_FACTOR' },
          { label: t('ui.settings.monitoring.audit.bindingVerification'), value: 'CONTACT_BIND' },
        ]}
      />
    ),
    render: (_: unknown, record: AuditRecord) => {
      const map: Record<string, string> = {
        LOGIN_CODE: t('ui.settings.monitoring.audit.codeLogin'),
        SECOND_FACTOR: t('ui.settings.monitoring.audit.secondFactor'),
        CONTACT_BIND: t('ui.settings.monitoring.audit.bindingVerification'),
      };
      return map[record.actionName || ''] || record.actionName || '-';
    },
  },
  {
    title: t('ui.settings.monitoring.audit.result'),
    dataIndex: 'resultStatus',
    importance: 1,
    renderFormItem: () => (
      <Select
        allowClear
        options={[
          { label: t('ui.settings.monitoring.audit.success'), value: 'SUCCESS' },
          { label: t('ui.settings.monitoring.audit.failed'), value: 'FAIL' },
        ]}
      />
    ),
    render: (_: unknown, record: AuditRecord) => renderStatusTag(record.logResult),
  },
  timeRangeColumn,
  { title: t('ui.settings.monitoring.audit.user'), dataIndex: 'username', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  {
    title: t('ui.settings.monitoring.audit.details'),
    dataIndex: 'detailMessage',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_: unknown, record: AuditRecord) => buildDetailText(record.detailMessage),
  },
  { title: t('ui.settings.monitoring.audit.time'), dataIndex: 'createdAt', search: false, importance: 1 },
  buildDetailActionColumn(onOpenDetail, isDesktop, isMobile),
];

const buildAuditColumns = ({ activeLogType, isDesktop, isMobile, onOpenDetail }: { activeLogType: AuditLogType; isDesktop: boolean; isMobile: boolean; onOpenDetail: (record: AuditRecord) => void }): ProColumns<AuditRecord>[] => {
  if (activeLogType === 'login') {
    return buildLoginAuditColumns(onOpenDetail, isDesktop, isMobile);
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
        canViewLoginLogs ? { tab: t('ui.settings.monitoring.audit.loginLogs'), key: 'login' } : null,
        canViewOperationLogs ? { tab: t('ui.settings.monitoring.audit.operationLogs'), key: 'operation' } : null,
        canViewOperationLogs ? { tab: t('ui.settings.monitoring.audit.verificationLogs'), key: 'verification' } : null,
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
    column: 1,
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
      title={t('ui.settings.monitoring.audit.auditCenter')}
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
              {t('ui.settings.monitoring.audit.refresh')}
            </Button>,
          ]}
        />
      </ManagementPageBody>

      <ManagementDrawer
        title={selectedRecord ? `${t('ui.settings.monitoring.audit.logDetails')} · ${selectedRecord.username || selectedRecord.moduleName || selectedRecord.id}` : t('ui.settings.monitoring.audit.logDetails')}
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
                { title: t('ui.settings.monitoring.audit.username'), dataIndex: 'username', renderText: (value) => value || '-' },
                { title: t('ui.settings.monitoring.audit.type'), dataIndex: 'logType', renderText: (value) => value || selectedRecord.operationType || '-' },
                { title: t('ui.settings.monitoring.audit.result'), dataIndex: 'logResult', renderText: (value) => value || '-' },
                { title: 'RequestId', dataIndex: 'requestId', renderText: (value) => value || '-' },
                { title: 'TraceId', dataIndex: 'traceId', renderText: (value) => value || '-' },
                { title: t('ui.settings.monitoring.audit.time'), dataIndex: 'createdAt' },
              ]}
            />
            <ProDescriptions<AuditRecord>
              {...detailExtraProps}
              columns={[
                {
                  title: t('ui.settings.monitoring.audit.additionalInfo'),
                  dataIndex: 'detailMessage',
                  renderText: (_, entity) => entity.failReason || entity.detailMessage || entity.requestPayloadJson || t('ui.settings.monitoring.audit.noMoreDetails'),
                },
                {
                  title: t('ui.settings.monitoring.audit.requestPayload'),
                  dataIndex: 'requestPayloadJson',
                  renderText: (value) => value || '-',
                },
                {
                  title: t('ui.settings.monitoring.audit.responsePayload'),
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
