import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { history, useAccess, useLocation } from '@umijs/max';
import { Alert, Button, Card, Col, Descriptions, Progress, Result, Row, Space, Spin, Statistic, Steps, Tag, Tabs, Tooltip, Typography, theme } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { ApiOutlined, CheckCircleOutlined, CloudDownloadOutlined, CloudSyncOutlined, ExclamationCircleOutlined, ReloadOutlined, RollbackOutlined } from '@ant-design/icons';
import { tokenManager } from '@/auth/token';
import { AUTHORIZATION_HEADER } from '@/constants/http';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import type {
  PlatformUpdateStatus,
  PlatformUpdatePreflight,
  PlatformUpdateTask,
  RedisMonitorClient,
  RedisMonitorCommandStat,
  RedisMonitorKeyspace,
  RedisMonitorSnapshot,
  ServiceApiDocStatus,
  ServiceMonitorSnapshot,
} from '@/types/api';
import { useQuery } from '@tanstack/react-query';
import { message, modal } from '@/theme/antdFeedbackBridge';
import { request } from '@/services/common/request';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { canSubmitPlatformUpdate, resolvePlatformUpdateConfirmationDetails } from './platformUpdateState';
import { databaseMessage } from '@/i18n/databaseMessage';
import { resolveRuntimeLocale } from '@/i18n/locale';

const t = databaseMessage;

const valueStyle = { fontSize: 24, fontWeight: 700 };
const REALTIME_REFRESH_INTERVAL_MS = 1000;
const MAX_TREND_SAMPLES = 5;

const UNKNOWN_VALUE = 'unknown';

const formatBytes = (value?: number | null) => {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '-';
  }
  if (value === 0) {
    return '0 B';
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = value;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${size >= 100 || unitIndex === 0 ? size.toFixed(0) : size.toFixed(2)} ${units[unitIndex]}`;
};

const formatPercent = (value?: number | null, digits = 2) => {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '-';
  }
  return `${value.toFixed(digits)}%`;
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString(resolveRuntimeLocale(), { hour12: false });
};

const formatNumber = (value?: number | null) => {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '-';
  }
  return value.toLocaleString(resolveRuntimeLocale());
};

const formatLoadAverage = (value?: number | null) => {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return t('ui.settings.monitoring.monitoring.notSupportedByThisSystem');
  }
  return value.toFixed(2);
};

const shortCommit = (value?: string | null) => {
  if (!value || value.toLowerCase() === UNKNOWN_VALUE) {
    return '-';
  }
  return value.slice(0, 12);
};

const statusMeta: Record<string, { color: string; icon: ReactNode; label: string; title: string }> = {
  UP_TO_DATE: {
    color: 'green',
    icon: <CheckCircleOutlined />,
    label: t('ui.settings.monitoring.monitoring.upToDate'),
    title: t('ui.settings.monitoring.monitoring.theCurrentVersionIsUpToDate'),
  },
  UPDATE_AVAILABLE: {
    color: 'orange',
    icon: <CloudSyncOutlined />,
    label: t('ui.settings.monitoring.monitoring.updateAvailable'),
    title: t('ui.settings.monitoring.monitoring.aNewVersionWasFound'),
  },
  UNKNOWN: {
    color: 'gold',
    icon: <ExclamationCircleOutlined />,
    label: t('ui.settings.monitoring.monitoring.pendingConfirmation'),
    title: t('ui.settings.monitoring.monitoring.versionInformationIsIncomplete'),
  },
  CHECK_FAILED: {
    color: 'red',
    icon: <ExclamationCircleOutlined />,
    label: t('ui.settings.monitoring.monitoring.checkFailed'),
    title: t('ui.settings.monitoring.monitoring.theUpdateSourceIsTemporarilyUnavailable'),
  },
};

const resolveStatusKey = (status?: PlatformUpdateStatus | null) => {
  if (!status) {
    return 'UNKNOWN';
  }
  if (status.status) {
    return status.status;
  }
  if (status.errorMessage) {
    return 'CHECK_FAILED';
  }
  if (status.updateAvailable) {
    return 'UPDATE_AVAILABLE';
  }
  return status.currentKnown === false || status.latestKnown === false ? 'UNKNOWN' : 'UP_TO_DATE';
};

type ServiceMonitorRow = {
  key: string;
  serviceName: string;
  baseUrl?: string | null;
  serviceStatus?: string | null;
  responseTimeMs?: number | null;
  checkedAt?: string | null;
  errorMessage?: string | null;
  apiDocUrl?: string | null;
  apiDocStatus?: string | null;
};

const renderServiceStatusTag = (status?: string | null) => {
  if (!status) {
    return '-';
  }
  return <Tag color={status === 'UP' ? 'green' : 'red'}>{status}</Tag>;
};

const buildServiceRows = (snapshot?: ServiceMonitorSnapshot): ServiceMonitorRow[] => {
  if (!snapshot) {
    return [];
  }

  const rows = new Map<string, ServiceMonitorRow>();

  for (const service of snapshot.services || []) {
    const key = service.serviceName || service.baseUrl;
    rows.set(key, {
      key,
      serviceName: service.serviceName,
      baseUrl: service.baseUrl,
      serviceStatus: service.status,
      responseTimeMs: service.responseTimeMs,
      checkedAt: service.checkedAt,
      errorMessage: service.errorMessage,
    });
  }

  for (const apiDoc of snapshot.apiDocs || []) {
    const key = apiDoc.serviceName || apiDoc.url;
    const current = rows.get(key);
    if (current) {
      current.apiDocUrl = apiDoc.url;
      current.apiDocStatus = apiDoc.status;
      continue;
    }

    rows.set(key, {
      key,
      serviceName: apiDoc.serviceName,
      apiDocUrl: apiDoc.url,
      apiDocStatus: apiDoc.status,
    });
  }

  return Array.from(rows.values());
};

const buildServiceColumns = () => {

  const serviceColumns: ProColumns<ServiceMonitorRow>[] = [
    { title: t('ui.settings.monitoring.monitoring.service'), dataIndex: 'serviceName' },
    { title: t('ui.settings.monitoring.monitoring.address'), dataIndex: 'baseUrl', ellipsis: true },
    {
      title: t('ui.settings.monitoring.monitoring.status'),
      dataIndex: 'serviceStatus',
      width: 'var(--saas-spacing-100)',
      render: (_: unknown, record: ServiceMonitorRow) => renderServiceStatusTag(record.serviceStatus),
    },
    { title: t('ui.settings.monitoring.monitoring.response'), dataIndex: 'responseTimeMs', width: 'var(--saas-spacing-100)', render: (_: unknown, record: ServiceMonitorRow) => (record.responseTimeMs == null ? '-' : `${record.responseTimeMs} ms`) },
    { title: t('ui.settings.monitoring.monitoring.checkedAt'), dataIndex: 'checkedAt', width: 'var(--saas-spacing-180)', render: (_: unknown, record: ServiceMonitorRow) => formatDateTime(record.checkedAt) },
    { title: t('ui.settings.monitoring.monitoring.openapiUrl'), dataIndex: 'apiDocUrl', ellipsis: true, render: (_: unknown, record: ServiceMonitorRow) => record.apiDocUrl || '-' },
    {
      title: t('ui.settings.monitoring.monitoring.apiDocsStatus'),
      dataIndex: 'apiDocStatus',
      width: 'var(--saas-spacing-120)',
      render: (_: unknown, record: ServiceMonitorRow) => renderServiceStatusTag(record.apiDocStatus),
    },
    { title: t('ui.settings.monitoring.monitoring.note'), dataIndex: 'errorMessage', ellipsis: true, render: (_: unknown, record: ServiceMonitorRow) => record.errorMessage || '-' },
  ];

  const apiDocColumns = [
    { title: t('ui.settings.monitoring.monitoring.service'), dataIndex: 'serviceName', width: 'var(--saas-spacing-180)' },
    { title: t('ui.settings.monitoring.monitoring.openapiUrl'), dataIndex: 'url', ellipsis: true },
    {
      title: t('ui.settings.monitoring.monitoring.serviceStatus'),
      dataIndex: 'status',
      width: 'var(--saas-spacing-120)',
      render: (_: unknown, record: ServiceApiDocStatus) => <Tag color={record.status === 'UP' ? 'green' : 'red'}>{record.status || 'DOWN'}</Tag>,
    },
  ];

  return { serviceColumns, apiDocColumns };
};

const buildRedisColumns = ({ isDesktop }: { isDesktop: boolean }) => {
  const commandColumns: ProColumns<RedisMonitorCommandStat>[] = [
    { title: t('ui.settings.monitoring.monitoring.command'), dataIndex: 'command', width: 'var(--saas-spacing-180)', fixed: isDesktop ? ('left' as const) : undefined },
    { title: t('ui.settings.monitoring.monitoring.calls'), dataIndex: 'calls', width: 'var(--saas-spacing-140)', render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.calls) },
    { title: t('ui.settings.monitoring.monitoring.totalTimeMs'), dataIndex: 'totalUsec', width: 'var(--saas-spacing-160)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.totalUsec) },
    { title: t('ui.settings.monitoring.monitoring.averageTimeMs'), dataIndex: 'avgUsec', width: 'var(--saas-spacing-160)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => record.avgUsec.toFixed(2) },
    { title: t('ui.settings.monitoring.monitoring.rejected'), dataIndex: 'rejectedCalls', width: 'var(--saas-spacing-120)', responsive: ['lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.rejectedCalls) },
    { title: t('ui.settings.monitoring.monitoring.failed'), dataIndex: 'failedCalls', width: 'var(--saas-spacing-120)', responsive: ['lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.failedCalls) },
  ];

  const keyspaceColumns: ProColumns<RedisMonitorKeyspace>[] = [
    { title: t('ui.settings.monitoring.monitoring.database'), dataIndex: 'database', width: 'var(--saas-spacing-120)' },
    { title: t('ui.settings.monitoring.monitoring.keys'), dataIndex: 'keys', width: 'var(--saas-spacing-120)', render: (_: unknown, record: RedisMonitorKeyspace) => formatNumber(record.keys) },
    { title: t('ui.settings.monitoring.monitoring.expiredKeys'), dataIndex: 'expires', width: 'var(--saas-spacing-140)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorKeyspace) => formatNumber(record.expires) },
    { title: t('ui.settings.monitoring.monitoring.averageTtlMs'), dataIndex: 'avgTtl', width: 'var(--saas-spacing-160)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorKeyspace) => formatNumber(record.avgTtl) },
  ];

  const clientColumns: ProColumns<RedisMonitorClient>[] = [
    { title: t('ui.settings.monitoring.monitoring.address'), dataIndex: 'addressPort', width: 'var(--saas-spacing-180)' },
    { title: t('ui.settings.monitoring.monitoring.name'), dataIndex: 'name', width: 'var(--saas-spacing-160)' },
    { title: t('ui.settings.monitoring.monitoring.idleS'), dataIndex: 'idle', width: 'var(--saas-spacing-100)', responsive: ['md', 'lg', 'xl', 'xxl'] as const },
    { title: t('ui.settings.monitoring.monitoring.ageS'), dataIndex: 'age', width: 'var(--saas-spacing-100)', responsive: ['md', 'lg', 'xl', 'xxl'] as const },
    { title: t('ui.settings.monitoring.monitoring.database'), dataIndex: 'databaseId', width: 'var(--saas-spacing-100)', responsive: ['md', 'lg', 'xl', 'xxl'] as const },
    { title: t('ui.settings.monitoring.monitoring.flags'), dataIndex: 'flags', width: 'var(--saas-spacing-140)', responsive: ['lg', 'xl', 'xxl'] as const, ellipsis: true },
    { title: t('ui.settings.monitoring.monitoring.lastCommand'), dataIndex: 'lastCommand', width: 'var(--saas-spacing-140)', responsive: ['lg', 'xl', 'xxl'] as const, ellipsis: true },
  ];

  return { commandColumns, keyspaceColumns, clientColumns };
};

const usePlatformUpdateMonitor = () => {
  const query = useQuery({
    queryKey: ['platform-update-status'],
    queryFn: async () =>
      request<PlatformUpdateStatus>('/v1/system/update/status', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
    refetchInterval: 2000,
  });
  const tasksQuery = useQuery({
    queryKey: ['platform-update-tasks'],
    queryFn: async () =>
      request<PlatformUpdateTask[]>('/v1/system/update/tasks', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
  });

  const updateStatus = query.data;
  const statusKey = resolveStatusKey(updateStatus);
  const currentStatusMeta = statusMeta[statusKey] || statusMeta.UNKNOWN;
  const canCompare = Boolean(updateStatus?.currentKnown && updateStatus?.latestKnown);
  const detailDescription = updateStatus?.errorMessage || updateStatus?.actionRequired || t('ui.settings.monitoring.monitoring.noActionRequired');

  const checkSteps = useMemo(
    () => [
      {
        title: t('ui.settings.monitoring.monitoring.readCurrentVersion'),
        status: updateStatus?.currentKnown === false ? 'wait' : 'finish',
        description: updateStatus?.currentKnown === false ? t('ui.settings.monitoring.monitoring.missingCommitId') : shortCommit(updateStatus?.current?.commitId),
      },
      {
        title: t('ui.settings.monitoring.monitoring.connectUpdateSource'),
        status: statusKey === 'CHECK_FAILED' ? 'error' : updateStatus?.latestKnown === false ? 'wait' : 'finish',
        description: updateStatus?.sourceType === 'github' ? 'GitHub' : updateStatus?.sourceType || '-',
      },
      {
        title: t('ui.settings.monitoring.monitoring.compareCommits'),
        status: statusKey === 'CHECK_FAILED' ? 'wait' : canCompare ? 'finish' : 'wait',
        description: canCompare ? updateStatus?.comparisonBasis || 'commit' : t('ui.settings.monitoring.monitoring.waitingForCompleteVersionInformation'),
      },
      {
        title: t('ui.settings.monitoring.monitoring.releaseAction'),
        status: statusKey === 'UPDATE_AVAILABLE' ? 'process' : statusKey === 'UP_TO_DATE' ? 'finish' : 'wait',
        description: currentStatusMeta.label,
      },
    ] as const,
    [canCompare, currentStatusMeta.label, statusKey, updateStatus],
  );

  const handleCheck = async () => {
    try {
      const result = await request<PlatformUpdateStatus>('/v1/system/update/check', {
        method: 'POST',
        ...API_OPTS.NO_REDIRECT,
      });
      await query.refetch();
      message.success(result.updateAvailable ? t('ui.settings.monitoring.monitoring.aNewVersionWasFound') : result.status === 'UNKNOWN' ? t('ui.settings.monitoring.monitoring.versionInformationPendingConfirmation') : t('ui.settings.monitoring.monitoring.youAreAlreadyOnTheLatestVersion'));
    } catch (error) {
      showErrorMessage(error, t('ui.settings.monitoring.monitoring.failedToCheckForUpdates'));
    }
  };

  const refreshAll = async () => {
    await Promise.all([query.refetch(), tasksQuery.refetch()]);
  };

  const handleInstall = async () => {
    if (statusKey !== 'UPDATE_AVAILABLE') {
      message.info(t('ui.settings.monitoring.monitoring.youAreAlreadyOnTheLatestVersionNo'));
      return;
    }
    if (updateStatus?.updaterAvailable !== true) {
      modal.info({
        title: t('ui.settings.monitoring.monitoring.installPlatformUpdater'),
        content: (
          <Space direction="vertical">
            <Typography.Text>{t('ui.settings.monitoring.monitoring.aOneTimeIdempotentHostInstallationIsRequired')}</Typography.Text>
            <Typography.Text code copyable>node bin/install-lumira-updater.mjs --deploy-dir /opt/lumira/deploy</Typography.Text>
          </Space>
        ),
      });
      return;
    }
    const requiredProtocol = updateStatus.latest?.minUpdaterProtocol || 1;
    const currentProtocol = updateStatus.updaterCapabilities?.protocolVersion || 1;
    if (currentProtocol < requiredProtocol) {
      modal.warning({
        title: t('ui.settings.monitoring.monitoring.updaterProtocolIsTooOld'),
        content: <Space direction="vertical"><Typography.Text>{t('ui.settings.monitoring.monitoring.currentProtocolIsThisReleaseRequiresUpgradeThe', { currentProtocol: currentProtocol, requiredProtocol: requiredProtocol })}</Typography.Text><Typography.Text code copyable>sudo node bin/install-lumira-updater.mjs --deploy-dir /opt/lumira/deploy</Typography.Text></Space>,
      });
      return;
    }
    let preflight: PlatformUpdatePreflight;
    try {
      preflight = await request<PlatformUpdatePreflight>('/v1/system/update/preflight', {
        method: 'POST',
        ...API_OPTS.NO_REDIRECT,
      });
    } catch (error) {
      showErrorMessage(error, t('ui.settings.monitoring.monitoring.updatePreflightFailed'));
      return;
    }
    const confirmationDetails = resolvePlatformUpdateConfirmationDetails(updateStatus, preflight);
    modal.confirm({
      title: preflight.ready
        ? t('ui.settings.monitoring.monitoring.confirmUpdateToVersion', { targetVersion: confirmationDetails.targetVersion })
        : t('ui.settings.monitoring.monitoring.preflightBlocked'),
      content: (
        <Space direction="vertical" style={{ width: '100%' }}>
          {preflight.ready ? (
            <Alert
              type="info"
              showIcon
              message={t('ui.settings.monitoring.monitoring.reviewTheVersionAndReleaseNotesBeforeStarting')}
            />
          ) : null}
          <Descriptions size="small" column={1} bordered>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.currentVersion')}>
              {confirmationDetails.currentVersion}
              {confirmationDetails.currentCommit !== '-' ? ` (${shortCommit(confirmationDetails.currentCommit)})` : ''}
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.targetVersion')}>
              {confirmationDetails.targetVersion}
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.targetCommit')}>
              <Typography.Text code copyable={confirmationDetails.targetCommit !== '-' ? { text: confirmationDetails.targetCommit } : false}>
                {shortCommit(confirmationDetails.targetCommit)}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.releaseNotes')}>
              <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                {confirmationDetails.releaseNotes || t('ui.settings.monitoring.monitoring.noReleaseNotesWereProvidedForThisUpdate')}
              </Typography.Paragraph>
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.trafficSwitch')}>{preflight.activeSlot || '-'} → {preflight.targetSlot || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.databaseMigration')}>{preflight.migrationMode || '-'}</Descriptions.Item>
          </Descriptions>
          {(preflight.blockers || []).map((item) => <Alert key={item} type="error" showIcon message={item} />)}
          {(preflight.warnings || []).map((item) => <Alert key={item} type="warning" showIcon message={item} />)}
          {preflight.ready ? <Typography.Text type="secondary">{t('ui.settings.monitoring.monitoring.trafficHotSwitchesOnlyAfterTheNewSlot')}</Typography.Text> : null}
        </Space>
      ),
      okText: t('ui.settings.monitoring.monitoring.confirmAndStartUpdate'),
      cancelText: t('ui.settings.monitoring.monitoring.cancel'),
      okButtonProps: { disabled: !preflight.ready },
      onOk: async () => {
        try {
          await request<PlatformUpdateTask>('/v1/system/update/install', {
            method: 'POST',
            data: { preflightId: preflight.preflightId, targetCommit: preflight.targetCommit },
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('ui.settings.monitoring.monitoring.updateTaskSubmitted'));
          await refreshAll();
        } catch (error) {
          showErrorMessage(error, t('ui.settings.monitoring.monitoring.failedToSubmitUpdateTask'));
        }
      },
    });
  };

  const handleRollback = async () => {
    modal.confirm({
      title: t('ui.settings.monitoring.monitoring.rollbackPlatformVersion'),
      content: t('ui.settings.monitoring.monitoring.trafficWillHotSwitchToThePreviousStable'),
      okText: t('ui.settings.monitoring.monitoring.startRollback'),
      cancelText: t('ui.settings.monitoring.monitoring.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await request<PlatformUpdateTask>('/v1/system/update/rollback', {
            method: 'POST',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('ui.settings.monitoring.monitoring.rollbackTaskSubmitted'));
          await refreshAll();
        } catch (error) {
          showErrorMessage(error, t('ui.settings.monitoring.monitoring.failedToSubmitRollbackTask'));
        }
      },
    });
  };

  const handleCancel = async (task: PlatformUpdateTask) => {
    try {
      await request<PlatformUpdateTask>(`/v1/system/update/tasks/${task.id}/cancel`, {
        method: 'POST',
        ...API_OPTS.NO_REDIRECT,
      });
      const switched = Boolean(task.phase && ['SWITCHING_TRAFFIC', 'VERIFYING_ACTIVE', 'DRAINING_OLD', 'UPDATING_WORKERS', 'FINALIZING'].includes(task.phase));
      message.success(switched ? t('ui.settings.monitoring.monitoring.trafficRollbackRequested') : t('ui.settings.monitoring.monitoring.updateCancellationRequested'));
      await refreshAll();
    } catch (error) {
      showErrorMessage(error, t('ui.settings.monitoring.monitoring.failedToCancelUpdate'));
    }
  };

  const taskColumns = useMemo<ProColumns<PlatformUpdateTask>[]>(
    () => [
      { title: t('ui.settings.monitoring.monitoring.type'), dataIndex: 'taskType', width: 'var(--saas-spacing-120)', render: (_: unknown, record) => record.taskType || '-' },
      {
        title: t('ui.settings.monitoring.monitoring.status'),
        dataIndex: 'status',
        width: 'var(--saas-spacing-120)',
        render: (_: unknown, record) => {
          const status = record.status || '-';
          const color = status === 'SUCCEEDED' || status === 'ROLLED_BACK' ? 'green' : status === 'FAILED' ? 'red' : 'blue';
          return <Tag color={color}>{status}</Tag>;
        },
      },
      { title: t('ui.settings.monitoring.monitoring.targetVersion'), dataIndex: 'targetVersion', width: 'var(--saas-spacing-160)', render: (_: unknown, record) => record.targetVersion || '-' },
      { title: t('ui.settings.monitoring.monitoring.phase'), dataIndex: 'phase', width: 'var(--saas-spacing-160)', render: (_: unknown, record) => record.phase || '-' },
      { title: t('ui.settings.monitoring.monitoring.progress'), dataIndex: 'progressPercent', width: 'var(--saas-spacing-140)', render: (_: unknown, record) => <Progress percent={record.progressPercent || 0} size="small" /> },
      { title: t('ui.settings.monitoring.monitoring.targetCommit'), dataIndex: 'targetCommit', width: 'var(--saas-spacing-160)', render: (_: unknown, record) => shortCommit(record.targetCommit) },
      { title: t('ui.settings.monitoring.monitoring.operator'), dataIndex: 'createdByName', width: 'var(--saas-spacing-140)', render: (_: unknown, record) => record.createdByName || '-' },
      { title: t('ui.settings.monitoring.monitoring.updatedAt'), dataIndex: 'updatedAt', width: 'var(--saas-spacing-180)', render: (_: unknown, record) => formatDateTime(record.updatedAt) },
      { title: t('ui.settings.monitoring.monitoring.message'), dataIndex: 'logSummary', ellipsis: true, render: (_: unknown, record) => record.errorMessage || record.logSummary || '-' },
    ],
    [],
  );

  return {
    query,
    tasksQuery,
    updateStatus,
    statusKey,
    currentStatusMeta,
    detailDescription,
    checkSteps,
    handleCheck,
    handleInstall,
    handleRollback,
    handleCancel,
    taskColumns,
    formatDateTime,
    shortCommit,
  };
};

const BreakableValue = ({ value }: { value?: string | null }) => <span className="saas-monitor-break-value">{value || '-'}</span>;

const UpdateSourceValue = ({ value, copyable = false }: { value?: string | null; copyable?: boolean }) => {
  const displayValue = value || '-';

  return (
    <Typography.Text
      className="saas-update-source-value"
      copyable={copyable && value ? { text: value } : false}
      ellipsis={{ tooltip: displayValue }}
    >
      {displayValue}
    </Typography.Text>
  );
};

const SWAGGER_UI_VERSION = '5.17.14';
const SWAGGER_UI_CSS = `https://cdn.jsdelivr.net/npm/swagger-ui-dist@${SWAGGER_UI_VERSION}/swagger-ui.css`;
const SWAGGER_UI_BUNDLE = `https://cdn.jsdelivr.net/npm/swagger-ui-dist@${SWAGGER_UI_VERSION}/swagger-ui-bundle.js`;
const SWAGGER_UI_PRESET = `https://cdn.jsdelivr.net/npm/swagger-ui-dist@${SWAGGER_UI_VERSION}/swagger-ui-standalone-preset.js`;

const serializeForScript = (value: unknown) =>
  JSON.stringify(value)
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');

const buildSwaggerHtml = (apiSpec: unknown, schemeContainerVerticalPadding: number) => {
  const serializedSpec = serializeForScript(apiSpec);

  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <link rel="stylesheet" href="${SWAGGER_UI_CSS}" />
    <style>
      html, body, #swagger-ui { height: 100%; margin: 0; background: #fff; }
      .swagger-ui .topbar { display: none; }
      .swagger-ui .scheme-container { padding: ${schemeContainerVerticalPadding}px 0; box-shadow: none; }
    </style>
  </head>
  <body>
    <div id="swagger-ui"></div>
    <script src="${SWAGGER_UI_BUNDLE}"></script>
    <script src="${SWAGGER_UI_PRESET}"></script>
    <script>
      const apiSpec = ${serializedSpec};
      window.onload = function () {
        window.ui = SwaggerUIBundle({
          spec: apiSpec,
          dom_id: '#swagger-ui',
          deepLinking: true,
          displayRequestDuration: true,
          supportedSubmitMethods: [],
          presets: [
            SwaggerUIBundle.presets.apis,
            SwaggerUIStandalonePreset
          ],
          layout: 'StandaloneLayout'
        });
      };
    </script>
  </body>
</html>`;
};

const readApiDocsError = async (response: Response) => {
  const fallback = t('ui.settings.monitoring.monitoring.apiDocsFailedToLoad').replace('{status}', String(response.status));
  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    const text = await response.text().catch(() => '');
    return text.trim() || fallback;
  }

  const body = await response.json().catch(() => null);
  if (body && typeof body === 'object') {
    const errorLike = body as { userMessage?: unknown; message?: unknown; error?: unknown };
    if (typeof errorLike.userMessage === 'string' && errorLike.userMessage.trim()) {
      return errorLike.userMessage;
    }
    if (typeof errorLike.message === 'string' && errorLike.message.trim()) {
      return errorLike.message;
    }
    if (typeof errorLike.error === 'string' && errorLike.error.trim()) {
      return errorLike.error;
    }
  }
  return fallback;
};

const ApiDocsContent = () => {
  const { token } = theme.useToken();
  const { isMobile } = useResponsive();
  const schemeContainerVerticalPadding = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const isLoggedIn = tokenManager.hasToken();
  const [apiSpec, setApiSpec] = useState<unknown>(null);
  const [isLoading, setIsLoading] = useState(isLoggedIn);
  const [loadError, setLoadError] = useState('');

  useEffect(() => {
    if (!isLoggedIn) {
      setIsLoading(false);
      return;
    }

    const controller = new AbortController();
    const tokenState = tokenManager.getTokenState();
    const authorization = tokenState?.accessToken ? `${tokenState.tokenType || 'Bearer'} ${tokenState.accessToken}` : '';

    setIsLoading(true);
    setLoadError('');

    fetch('/api/v1/system/monitor/api-docs', {
      headers: authorization ? { [AUTHORIZATION_HEADER]: authorization } : undefined,
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(await readApiDocsError(response));
        }
        return response.json();
      })
      .then((data) => {
        setApiSpec(data);
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }
        setLoadError(error instanceof Error ? error.message : t('ui.settings.monitoring.monitoring.failedToLoadApiDocs'));
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [isLoggedIn]);

  if (!isLoggedIn) {
    return (
      <ManagementPage title={t('ui.settings.monitoring.monitoring.apiDocs')}>
        <ManagementPageBody>
          <Result status="403" title={t('ui.settings.monitoring.monitoring.pleaseLogInFirst')} subTitle={t('ui.settings.monitoring.monitoring.apiDocsAreAvailableOnlyToSignedIn')} />
        </ManagementPageBody>
      </ManagementPage>
    );
  }

  return (
    <ManagementPage
      title={t('ui.settings.monitoring.monitoring.apiDocs')}
      className="saas-monitoring-api-docs-page"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => window.location.reload()}>
            {t('ui.settings.monitoring.monitoring.refreshPage')}
          </Button>
        </Space>
      }
    >
      <ManagementPageBody className="saas-monitoring-api-docs">
        <Card className="saas-monitoring-api-docs__card" bodyStyle={{ padding: 0, overflow: 'hidden', borderRadius: 'var(--saas-card-radius)', display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
          <div className="saas-monitoring-api-docs__surface">
            {isLoading ? (
              <div className="saas-monitoring-api-docs__loading">
                <Spin tip={t('ui.settings.monitoring.monitoring.loadingApiDocs')} />
              </div>
            ) : loadError ? (
              <div style={{ padding: token.paddingLG }}>
                <Alert message={t('ui.settings.monitoring.monitoring.failedToLoadApiDocs')} description={loadError} type="error" showIcon />
              </div>
            ) : (
              <iframe
                title={t('ui.settings.monitoring.monitoring.apiDocs')}
                srcDoc={buildSwaggerHtml(apiSpec, schemeContainerVerticalPadding)}
                sandbox="allow-scripts allow-forms allow-popups"
                className="saas-monitoring-api-docs__iframe"
              />
            )}
          </div>
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

const NumericValue = ({ value }: { value: string }) => (
  <span className="saas-monitor-numeric-value" style={{ fontVariantNumeric: 'tabular-nums' as const }}>
    {value}
  </span>
);

const ExpandableClampText = ({ value, lines = 2 }: { value?: string | null; lines?: number }) => {
  const [expanded, setExpanded] = useState(false);
  const normalizedValue = value?.trim();

  if (!normalizedValue) {
    return '-';
  }

  const expandable = normalizedValue.length > 72;

  return (
    <div className="saas-monitor-expandable-text">
      <div
        className={expanded ? 'saas-monitor-expandable-text__content is-expanded' : 'saas-monitor-expandable-text__content'}
        style={expanded ? undefined : ({ WebkitLineClamp: lines } as CSSProperties)}
      >
        {normalizedValue}
      </div>
      {expandable ? (
        <Button type="link" size="small" className="saas-monitor-expandable-text__trigger" onClick={() => setExpanded((current) => !current)}>
          {expanded ? t('ui.settings.monitoring.monitoring.collapse') : t('ui.settings.monitoring.monitoring.expand')}
        </Button>
      ) : null}
    </div>
  );
};

type TrendPoint = {
  label: string;
  value: number;
};

const TrendAreaChart = ({
  points,
  valueFormatter,
}: {
  points: TrendPoint[];
  valueFormatter: (value: number) => string;
}) => {
  const { token } = theme.useToken();
  const chartRef = useRef<SVGSVGElement>(null);
  const [width, setWidth] = useState(APP_SPACING.monitoringTrendChart.width);
  const height = APP_SPACING.monitoringTrendChart.height;
  const padding = APP_SPACING.monitoringTrendChart.padding;
  const chartAxisOffsetX = APP_SPACING.monitoringTrendChart.axisOffsetX;
  const chartAxisOffsetY = APP_SPACING.monitoringTrendChart.axisOffsetY;
  const chartAxisFontSize = APP_SPACING.monitoringTrendChart.axisFontSize;
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  const values = points.map((item) => item.value);
  const maxValue = Math.max(...values, 1) * 1.08;
  const normalizedPoints = points.length ? points : [{ label: '-', value: 0 }];
  const coordinates = normalizedPoints.map((item, index) => {
    const x =
      normalizedPoints.length === 1 ? padding.left + plotWidth / 2 : padding.left + (plotWidth * index) / (normalizedPoints.length - 1);
    const y = padding.top + plotHeight - (Math.max(item.value, 0) / maxValue) * plotHeight;
    return { ...item, x, y };
  });
  const linePath = coordinates.map((item, index) => `${index === 0 ? 'M' : 'L'} ${item.x} ${item.y}`).join(' ');
  const areaPath = `${linePath} L ${coordinates.at(-1)?.x ?? padding.left} ${padding.top + plotHeight} L ${coordinates[0]?.x ?? padding.left} ${padding.top + plotHeight} Z`;
  const yTicks = [maxValue, maxValue / 2, 0];
  const xAxisLabels = coordinates.filter((_, index) => {
    if (coordinates.length <= 3) {
      return true;
    }
    return index === 0 || index === Math.floor((coordinates.length - 1) / 2) || index === coordinates.length - 1;
  });

  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) {
      return undefined;
    }

    const syncWidth = (nextWidth: number) => {
      const roundedWidth = Math.round(nextWidth);
      if (roundedWidth <= 0) {
        return;
      }
      setWidth((currentWidth) => (currentWidth === roundedWidth ? currentWidth : roundedWidth));
    };

    syncWidth(chart.getBoundingClientRect().width);
    if (typeof ResizeObserver === 'undefined') {
      return undefined;
    }

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) {
        syncWidth(entry.contentRect.width);
      }
    });
    observer.observe(chart);
    return () => observer.disconnect();
  }, []);

  return (
    <svg ref={chartRef} className="saas-redis-trend-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="trend chart">
      {yTicks.map((tick) => {
        const y = padding.top + plotHeight - (tick / maxValue) * plotHeight;
        return (
          <g key={tick}>
            <line className="saas-redis-trend-chart__grid" x1={padding.left} x2={width - padding.right} y1={y} y2={y} stroke={token.colorBorderSecondary} strokeDasharray="4 4" strokeWidth={1} />
            <text className="saas-redis-trend-chart__axis" x={padding.left - chartAxisOffsetX} y={y + chartAxisOffsetY} textAnchor="end" fill={token.colorTextTertiary} fontSize={chartAxisFontSize}>
              {valueFormatter(tick)}
            </text>
          </g>
        );
      })}
      <path className="saas-redis-trend-chart__area" d={areaPath} fill={token.colorPrimaryBg} stroke="none" />
      <path className="saas-redis-trend-chart__line" d={linePath} fill="none" stroke={token.colorPrimary} strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} />
      {coordinates.map((item) => (
        <circle key={`${item.label}-${item.x}`} className="saas-redis-trend-chart__point" cx={item.x} cy={item.y} r={3.5} fill={token.colorBgContainer} stroke={token.colorPrimary} strokeWidth={2} />
      ))}
      {xAxisLabels.map((item, index) => (
          <text key={`${item.label}-${item.x}-label`} className="saas-redis-trend-chart__axis" x={item.x} y={height - chartAxisOffsetY} textAnchor={index === 0 ? 'start' : index === xAxisLabels.length - 1 ? 'end' : 'middle'} fill={token.colorTextTertiary} fontSize={chartAxisFontSize}>
          {item.label}
        </text>
      ))}
    </svg>
  );
};

const useServiceMonitor = () => {
  const { isDesktop, isMobile } = useResponsive();
  const query = useQuery({
    queryKey: ['service-monitor'],
    queryFn: async () =>
      request<ServiceMonitorSnapshot>('/v1/system/monitor/service', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
  });
  const detailDescriptionsProps = useDetailDescriptionsProps({ column: isMobile ? 1 : 2 });
  const fullRowSpan = isMobile ? 1 : 2;

  const refreshRef = useRef(query.refetch);
  useEffect(() => {
    refreshRef.current = query.refetch;
  }, [query.refetch]);


  useEffect(() => {
    const timer = window.setInterval(() => {
      void refreshRef.current();
    }, REALTIME_REFRESH_INTERVAL_MS);
    return () => {
      window.clearInterval(timer);
    };
  }, []);

  const serviceRows = useMemo(() => buildServiceRows(query.data), [query.data]);
  const { serviceColumns } = useMemo(() => buildServiceColumns(), []);

  return {
    isDesktop,
    isMobile,
    query,
    detailDescriptionsProps,
    fullRowSpan,
    valueStyle,
    serviceRows,
    serviceColumns,
  };

};

const useRedisMonitor = () => {
  const responsive = useResponsive();
  const query = useQuery({
    queryKey: ['redis-monitor'],
    queryFn: async () =>
      request<RedisMonitorSnapshot>('/v1/system/monitor/redis', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
  });
  const [samples, setSamples] = useState<Array<{ label: string; memoryBytes: number; qps: number }>>([]);
  const refreshRef = useRef(query.refetch);

  useEffect(() => {
    refreshRef.current = query.refetch;
  }, [query.refetch]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      void refreshRef.current();
    }, REALTIME_REFRESH_INTERVAL_MS);
    return () => {
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    const snapshot = query.data;
    if (!snapshot?.overview) {
      return;
    }
    const nextPoint = {
      label: new Date().toLocaleTimeString(resolveRuntimeLocale(), { hour12: false }),
      memoryBytes: snapshot.overview.memoryUsedBytes || 0,
      qps: snapshot.overview.instantaneousOpsPerSec || 0,
    };
    setSamples((current) => {
      if (current.at(-1)?.label === nextPoint.label) {
        return [...current.slice(0, -1), nextPoint];
      }
      return [...current.slice(-(MAX_TREND_SAMPLES - 1)), nextPoint];
    });
  }, [query.data]);

  const redis = query.data;

  const memoryTrend = useMemo(
    () => samples.map((item) => ({ label: item.label, value: item.memoryBytes })),
    [samples],
  );
  const qpsTrend = useMemo(
    () => samples.map((item) => ({ label: item.label, value: item.qps })),
    [samples],
  );

  const trendCharts = useMemo(
    () => [
      {
        title: t('ui.settings.monitoring.monitoring.memoryTrendMb'),
        subtitle: t('ui.settings.monitoring.monitoring.latestSamples').replace('{count}', String(MAX_TREND_SAMPLES)),
        points: memoryTrend.map((item) => ({ ...item, value: item.value / 1024 / 1024 })),
        valueFormatter: (value: number) => `${value.toFixed(2)} MB`,
      },
      {
        title: t('ui.settings.monitoring.monitoring.throughputTrendOps'),
        subtitle: t('ui.settings.monitoring.monitoring.latestSamples').replace('{count}', String(MAX_TREND_SAMPLES)),
        points: qpsTrend,
        valueFormatter: (value: number) => value.toFixed(0),
      },
    ],
    [memoryTrend, qpsTrend],
  );

  const { commandColumns, keyspaceColumns, clientColumns } = useMemo(() => buildRedisColumns({ isDesktop: responsive.isDesktop }), [responsive.isDesktop]);

  return {
    responsive,
    query,
    redis,
    valueStyle,
    trendCharts,
    commandColumns,
    keyspaceColumns,
    clientColumns,
  };
};

const PlatformUpdateContent = () => {
  const {
    query,
    tasksQuery,
    updateStatus,
    statusKey,
    currentStatusMeta,
    detailDescription,
    checkSteps,
    handleCheck,
    handleInstall,
    handleRollback,
    handleCancel,
    taskColumns,
    formatDateTime,
    shortCommit,
  } = usePlatformUpdateMonitor();
  const { isMobile } = useResponsive();
  const rowGutter = resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile);
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const compactSectionGap = resolveResponsiveValue(APP_SPACING.compactSectionGap, isMobile);
  const activeTask = updateStatus?.activeTask;
  const isTaskRunning = activeTask?.status === 'PENDING' || activeTask?.status === 'RUNNING';
  const updaterAvailable = updateStatus?.updaterAvailable === true;
  const canInstall = canSubmitPlatformUpdate(
    updateStatus?.latest?.serverImage,
    activeTask?.status,
    statusKey === 'UPDATE_AVAILABLE',
  );
  const canRollback = updaterAvailable && !isTaskRunning;
  const installTooltip = statusKey === 'UP_TO_DATE'
    ? t('ui.settings.monitoring.monitoring.youAreAlreadyOnTheLatestVersionNo')
    : canInstall
      ? t('ui.settings.monitoring.monitoring.updateToTheLatestRelease')
      : t('ui.settings.monitoring.monitoring.theUpdateSourceDoesNotProvideAnInstallable');

  return (
    <Space
      direction="vertical"
      size={sectionGap}
      style={{ width: '100%' }}
      className="saas-update-center saas-monitoring-tab-pane"
    >
      <Card loading={query.isLoading && !updateStatus}>
        <Row gutter={rowGutter} align="middle">
          <Col xs={24} lg={10}>
            <Space direction="vertical" size={compactSectionGap} style={{ width: '100%' }}>
              <Tag color={currentStatusMeta.color} icon={currentStatusMeta.icon} className="saas-update-status-tag">
                {currentStatusMeta.label}
              </Tag>
              <Typography.Title level={4} style={{ margin: 0 }}>
                {currentStatusMeta.title}
              </Typography.Title>
              <Typography.Text type="secondary">{detailDescription}</Typography.Text>
            </Space>
          </Col>
          <Col xs={24} sm={8} lg={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.currentCommit')} value={shortCommit(updateStatus?.current?.commitId)} valueStyle={{ fontSize: 22 }} />
          </Col>
          <Col xs={24} sm={8} lg={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.latestCommit')} value={shortCommit(updateStatus?.latest?.commitId)} valueStyle={{ fontSize: 22 }} />
          </Col>
          <Col xs={24} sm={8} lg={3}>
            <Statistic title={t('ui.settings.monitoring.monitoring.checkedAt.3d9490d4')} value={formatDateTime(updateStatus?.checkedAt)} valueStyle={{ fontSize: 14 }} />
          </Col>
          <Col xs={24} lg={3}>
            <Space wrap className="saas-update-actions">
              <Tooltip title={t('ui.settings.monitoring.monitoring.reCheckUpdateSource')}>
                <Button icon={<ReloadOutlined />} loading={query.isFetching} onClick={handleCheck}>
                  {t('ui.settings.monitoring.monitoring.check')}
                </Button>
              </Tooltip>
              <Tooltip title={installTooltip}>
                <Button type="primary" icon={<CloudDownloadOutlined />} disabled={!canInstall} loading={isTaskRunning} onClick={handleInstall}>
                  {t('ui.settings.monitoring.monitoring.update')}
                </Button>
              </Tooltip>
              <Tooltip title={t('ui.settings.monitoring.monitoring.rollbackWithTheLatestUpdaterEnvBackup')}>
                <Button danger icon={<RollbackOutlined />} disabled={!canRollback} onClick={handleRollback}>
                  {t('ui.settings.monitoring.monitoring.rollback')}
                </Button>
              </Tooltip>
            </Space>
          </Col>
        </Row>
      </Card>
      {statusKey === 'UNKNOWN' ? (
        <Alert
          type="warning"
          showIcon
          message={t('ui.settings.monitoring.monitoring.theCurrentDeploymentIsMissingCommitInformation')}
          description={t('ui.settings.monitoring.monitoring.theUpdateCenterCanReachTheSourceBut')}
        />
      ) : null}
      {statusKey === 'CHECK_FAILED' ? (
      <Alert type="error" showIcon message={t('ui.settings.monitoring.monitoring.updateSourceCheckFailed')} description={updateStatus?.errorMessage || t('ui.settings.monitoring.monitoring.pleaseCheckTheSourceUrlAndServerNetwork')} />
      ) : null}
      {updateStatus && updateStatus.sourceReachable === false && statusKey === 'UP_TO_DATE' ? (
        <Alert
          type="info"
          showIcon
          message={t('ui.settings.monitoring.monitoring.usingLocalVersionInformation')}
          description={updateStatus.errorMessage || t('ui.settings.monitoring.monitoring.theRemoteUpdateSourceIsUnavailableSoThe')}
        />
      ) : null}
      {activeTask ? (
        <Card size="small" title={`${activeTask.taskType || 'UPDATE'} · ${activeTask.phase || activeTask.status || ''}`} extra={isTaskRunning ? <Button danger size="small" onClick={() => void handleCancel(activeTask)}>{t('ui.settings.monitoring.monitoring.cancelRollback')}</Button> : null}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Progress percent={activeTask.progressPercent || 0} status={activeTask.status === 'FAILED' ? 'exception' : activeTask.status === 'SUCCEEDED' || activeTask.status === 'ROLLED_BACK' ? 'success' : 'active'} />
            <Descriptions size="small" column={{ xs: 1, md: 3 }}>
              <Descriptions.Item label={t('ui.settings.monitoring.monitoring.phase')}>{activeTask.phase || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.monitoring.monitoring.activeSlot')}>{activeTask.activeSlot || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('ui.settings.monitoring.monitoring.targetSlot')}>{activeTask.targetSlot || '-'}</Descriptions.Item>
            </Descriptions>
          </Space>
        </Card>
      ) : null}
      {activeTask ? (
        <Alert
          type={activeTask.status === 'FAILED' ? 'error' : activeTask.status === 'SUCCEEDED' || activeTask.status === 'ROLLED_BACK' ? 'success' : 'info'}
          showIcon
          message={`${activeTask.taskType || 'UPDATE'} ${activeTask.status || ''}`}
          description={activeTask.errorMessage || activeTask.logSummary || t('ui.settings.monitoring.monitoring.theUpdaterAgentIsProcessingTheTaskRefresh')}
        />
      ) : null}
      <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
        <Row gutter={rowGutter}>
          <Col xs={24} lg={12}>
            <Card title={t('ui.settings.monitoring.monitoring.currentRunningVersion')} className="saas-update-version-card">
              <Descriptions size="small" column={1}>
                <Descriptions.Item label={t('ui.settings.monitoring.monitoring.version')}>{updateStatus?.current?.version || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('ui.settings.monitoring.monitoring.commit')}>
                  <Typography.Text copyable={{ text: updateStatus?.current?.commitId || '' }} className="saas-update-mono">
                    {updateStatus?.current?.commitId?.slice(0, 12) || '-'}
                  </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label={t('ui.settings.monitoring.monitoring.branch')}>{updateStatus?.current?.branch || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('ui.settings.monitoring.monitoring.buildTime')}>{formatDateTime(updateStatus?.current?.buildTime)}</Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title={t('ui.settings.monitoring.monitoring.sourceVersion')} className="saas-update-version-card">
              <Descriptions size="small" column={1}>
                <Descriptions.Item label={t('ui.settings.monitoring.monitoring.version')}>{updateStatus?.latest?.version || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('ui.settings.monitoring.monitoring.commit')}>
                  <Typography.Text copyable={{ text: updateStatus?.latest?.commitId || '' }} className="saas-update-mono">
                    {updateStatus?.latest?.commitId?.slice(0, 12) || '-'}
                  </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label={t('ui.settings.monitoring.monitoring.branch')}>{updateStatus?.latest?.branch || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('ui.settings.monitoring.monitoring.releaseTime')}>{formatDateTime(updateStatus?.latest?.releasedAt)}</Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
        </Row>
        <Card title={t('ui.settings.monitoring.monitoring.checkPath')} loading={query.isLoading && !updateStatus}>
          <Steps
            size="small"
            responsive
            items={checkSteps.map((item) => ({
              title: item.title,
              status: item.status,
              description: item.description,
            }))}
          />
        </Card>
        <Card title={t('ui.settings.monitoring.monitoring.updateSource')}>
          <Descriptions
            className="saas-update-source-descriptions"
            size="small"
            column={{ xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2 }}
          >
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.sourceType')}>
              <Tag icon={<ApiOutlined />} color={updateStatus?.sourceType === 'github' ? 'blue' : 'default'}>
                {updateStatus?.sourceType === 'github' ? 'GitHub' : updateStatus?.sourceType || '-'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.comparisonBasis')}>
              <UpdateSourceValue value={updateStatus?.comparisonBasis} />
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.latestNote')}>
              <UpdateSourceValue value={updateStatus?.latest?.title} />
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.address')}>
              <UpdateSourceValue value={updateStatus?.sourceUrl} copyable />
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.serverImage')}>
              <UpdateSourceValue value={updateStatus?.latest?.serverImage} copyable />
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.frontendImage')}>
              <UpdateSourceValue value={updateStatus?.latest?.frontendImage} copyable />
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.migration')}>
              <Tag color={updateStatus?.latest?.migrationRequired ? 'orange' : 'green'}>{updateStatus?.latest?.migrationRequired ? t('ui.settings.monitoring.monitoring.required') : t('ui.settings.monitoring.monitoring.notRequired')}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.monitoring.monitoring.rollback.3ae1ddbb')}>
              <Tag color={updateStatus?.latest?.rollbackSupported === false ? 'red' : 'green'}>{updateStatus?.latest?.rollbackSupported === false ? t('ui.settings.monitoring.monitoring.no') : t('ui.settings.monitoring.monitoring.yes')}</Tag>
            </Descriptions.Item>
          </Descriptions>
        </Card>
        <Card title={t('ui.settings.monitoring.monitoring.updateTaskHistory')} loading={tasksQuery.isLoading && !tasksQuery.data}>
          <ManagementTable<PlatformUpdateTask>
            rowKey="id"
            size="small"
            search={false}
            isMobile={isMobile}
            onRefresh={() => {
              void tasksQuery.refetch();
              void query.refetch();
            }}
            dataSource={tasksQuery.data || []}
            columns={taskColumns}
          />
        </Card>
      </Space>
    </Space>
  );
};

const RedisMonitorContent = () => {
  const { responsive, query, redis, valueStyle, trendCharts, commandColumns, keyspaceColumns, clientColumns } = useRedisMonitor();
  const overview = redis?.overview;
  const rowGutter = resolveResponsiveValue(APP_SPACING.rowGutterPanel, responsive.isMobile);
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }} className="saas-monitoring-tab-pane">
      <Card loading={query.isLoading && !redis} title={t('ui.settings.monitoring.monitoring.redisInfo')}>
        <Row gutter={rowGutter}>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.redisVersion')} value={overview?.version || '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.mode')} value={overview?.mode || '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.port')} value={overview?.port ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.clients')} value={overview?.connectedClients ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.uptimeDays')} value={overview?.uptimeDays ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.hitRate')} value={formatPercent(overview?.hitRate)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="QPS" value={overview?.instantaneousOpsPerSec ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.keys')} value={overview?.keyCount ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.memoryUsed')} value={formatBytes(overview?.memoryUsedBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.memoryPeak')} value={formatBytes(overview?.memoryPeakBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.memoryUsage')} value={formatPercent(overview?.memoryUsagePercent)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.totalConnections')} value={overview?.totalConnectionsReceived ?? '-'} valueStyle={valueStyle} />
          </Col>
        </Row>
        <Row gutter={rowGutter} style={{ marginTop: sectionGap }}>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.totalHits')} value={formatNumber(overview?.hits)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.totalMisses')} value={formatNumber(overview?.misses)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('ui.settings.monitoring.monitoring.totalCommands')} value={formatNumber(overview?.totalCommandsProcessed)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={8}>
            <Statistic title={t('ui.settings.monitoring.monitoring.sampleTime')} value={formatDateTime(redis?.sampleTime)} valueStyle={valueStyle} />
          </Col>
        </Row>
      </Card>
      <Row gutter={rowGutter}>
        {trendCharts.map((chart) => (
          <Col key={chart.title} xs={24} xl={12}>
            <Card title={chart.title} extra={<Typography.Text type="secondary">{chart.subtitle}</Typography.Text>}>
              <div style={{ height: 'var(--saas-spacing-220)' }}>
                <TrendAreaChart points={chart.points} valueFormatter={chart.valueFormatter || ((value) => value.toFixed(0))} />
              </div>
            </Card>
          </Col>
        ))}
      </Row>
      <Card title={t('ui.settings.monitoring.monitoring.commandStatistics')} loading={query.isLoading && !redis}>
        <ManagementTable<RedisMonitorCommandStat>
          rowKey="command"
          search={false}
          pagination={false}
          columns={commandColumns}
          dataSource={redis?.commandStats || []}
          isMobile={responsive.isMobile}
          toolBarRender={false}
        />
      </Card>
      <Card title={t('ui.settings.monitoring.monitoring.keyInfo')} loading={query.isLoading && !redis}>
        <ManagementTable<RedisMonitorKeyspace>
          rowKey="database"
          search={false}
          pagination={false}
          columns={keyspaceColumns}
          dataSource={redis?.keyspaces || []}
          isMobile={responsive.isMobile}
          toolBarRender={false}
        />
      </Card>
      <Card title={t('ui.settings.monitoring.monitoring.connectedClients')} loading={query.isLoading && !redis}>
        <ManagementTable<RedisMonitorClient>
          rowKey={(record) => `${record.addressPort || ''}-${record.name || ''}-${record.databaseId || ''}`}
          search={false}
          pagination={false}
          dataSource={redis?.clients || []}
          columns={clientColumns}
          isMobile={responsive.isMobile}
          toolBarRender={false}
        />
      </Card>
    </Space>
  );
};

const ServiceMonitorContent = () => {
  const {
    isDesktop,
    isMobile,
    query,
    detailDescriptionsProps,
    fullRowSpan,
    serviceRows,
    valueStyle,
    serviceColumns,
  } = useServiceMonitor();

  const service = query.data;
  const rowGutter = resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile);
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }} className="saas-service-monitor-page saas-monitoring-tab-pane">
      <Row gutter={rowGutter}>
        <Col xs={24} lg={12}>
          <Card title="CPU" loading={query.isLoading && !service} style={{ height: '100%' }} bodyStyle={{ minHeight: isDesktop ? 108 : 0 }}>
            <Row gutter={rowGutter}>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('ui.settings.monitoring.monitoring.processCpuUsage')} value={service?.cpu?.processUsagePercent ?? 0} precision={2} suffix="%" valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('ui.settings.monitoring.monitoring.systemCpuUsage')} value={service?.cpu?.systemUsagePercent ?? 0} precision={2} suffix="%" valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('ui.settings.monitoring.monitoring.idleRate')} value={service?.cpu?.idlePercent ?? 0} precision={2} suffix="%" valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('ui.settings.monitoring.monitoring.loadAverage')} value={formatLoadAverage(service?.cpu?.loadAverage)} valueStyle={valueStyle} />
              </Col>
            </Row>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={t('ui.settings.monitoring.monitoring.hostMemory')} loading={query.isLoading && !service} style={{ height: '100%' }} bodyStyle={{ minHeight: isDesktop ? 108 : 0 }}>
            <Row gutter={rowGutter}>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('ui.settings.monitoring.monitoring.totalMemory')} value={formatBytes(service?.memory?.hostTotalBytes ?? service?.memory?.totalBytes)} valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('ui.settings.monitoring.monitoring.usedMemory')} value={formatBytes(service?.memory?.hostUsedBytes ?? service?.memory?.usedBytes)} valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('ui.settings.monitoring.monitoring.availableMemory')} value={formatBytes(service?.memory?.hostFreeBytes ?? service?.memory?.freeBytes)} valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('ui.settings.monitoring.monitoring.usage')} value={formatPercent(service?.memory?.hostUsagePercent ?? service?.memory?.usagePercent)} valueStyle={valueStyle} />
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>

      <Card title={t('ui.settings.monitoring.monitoring.containerJvmMemory')} loading={query.isLoading && !service}>
        <Row gutter={rowGutter}>
          <Col xs={24} sm={12} lg={6}>
            <Statistic title={t('ui.settings.monitoring.monitoring.containerMemoryLimit')} value={formatBytes(service?.memory?.totalBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Statistic title={t('ui.settings.monitoring.monitoring.containerUsedMemory')} value={formatBytes(service?.memory?.usedBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Statistic title={t('ui.settings.monitoring.monitoring.jvmHeapMax')} value={formatBytes(service?.memory?.heapMaxBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Statistic title={t('ui.settings.monitoring.monitoring.jvmHeapUsed')} value={formatBytes(service?.memory?.heapUsedBytes)} valueStyle={valueStyle} />
          </Col>
        </Row>
      </Card>


      <Card title={t('ui.settings.monitoring.monitoring.serviceHealthApiDocs')} loading={query.isLoading && !service}>
        <ManagementTable<ServiceMonitorRow>
          rowKey="key"
          size="small"
          pagination={false}
          isMobile={isMobile}
          search={false}
          onRefresh={() => query.refetch()}
          dataSource={serviceRows}
          columns={serviceColumns}
        />
      </Card>

      <Card title={t('ui.settings.monitoring.monitoring.serverInformation')} loading={query.isLoading && !service}>
        <Descriptions {...detailDescriptionsProps}>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.serverName')}>
            <BreakableValue value={service?.server?.serverName} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.serverIp')}>
            <NumericValue value={service?.server?.serverIp || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.operatingSystem')}>
            <BreakableValue value={service?.server?.osName} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.architecture')}>
            <BreakableValue value={service?.server?.osArch} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.osVersion')}>
            <NumericValue value={service?.server?.osVersion || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.projectPath')}>
            <BreakableValue value={service?.server?.projectPath} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.installPath')}>
            <BreakableValue value={service?.server?.installPath} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.homeDirectory')}>
            <BreakableValue value={service?.server?.userHome} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.tempDirectory')} span={fullRowSpan}>
            <BreakableValue value={service?.server?.tempDir} />
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={t('ui.settings.monitoring.monitoring.jvmInformation')} loading={query.isLoading && !service}>
        <Descriptions {...detailDescriptionsProps}>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.javaName')}>
            <BreakableValue value={service?.jvm?.vmName} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.javaVersion')}>
            <NumericValue value={service?.jvm?.javaVersion || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.vmVersion')}>
            <NumericValue value={service?.jvm?.vmVersion || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.vmVendor')}>
            <BreakableValue value={service?.jvm?.vmVendor} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.startTime')}>
            <NumericValue value={service?.jvm?.startTime || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.uptime')}>
            <NumericValue value={String(service?.jvm?.uptimeSeconds ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.processId')}>
            <NumericValue value={String(service?.jvm?.pid ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.threadCount')}>
            <NumericValue value={String(service?.jvm?.threadCount ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.daemonThreads')}>
            <NumericValue value={String(service?.jvm?.daemonThreadCount ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.peakThreads')}>
            <NumericValue value={String(service?.jvm?.peakThreadCount ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.javaHome')} span={fullRowSpan}>
            <BreakableValue value={service?.jvm?.javaHome} />
          </Descriptions.Item>
          <Descriptions.Item label={t('ui.settings.monitoring.monitoring.startupArguments')} span={fullRowSpan}>
            <ExpandableClampText value={service?.jvm?.inputArguments?.join(' ')} />
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
};

const normalizeTab = (value?: string | null) => (value === 'redis' || value === 'update' ? value : 'service');

const MonitoringPage = () => {
  const access = useAccess();
  const location = useLocation();
  const searchParams = useMemo(() => new URLSearchParams(location.search), [location.search]);
  const activeTab = normalizeTab(searchParams.get('tab'));
  const tabs = useMemo(
    () =>
      [
        access.canVisitSystemMonitoringService
          ? {
              key: 'service',
              label: t('ui.settings.monitoring.monitoring.serviceMonitoring'),
              children: <ServiceMonitorContent />,
            }
          : null,
        access.canVisitSystemMonitoringRedis
          ? {
              key: 'redis',
              label: t('ui.settings.monitoring.monitoring.redisMonitoring'),
              children: <RedisMonitorContent />,
            }
          : null,
        access.canVisitPlatformUpdate
          ? {
              key: 'update',
              label: t('ui.settings.monitoring.monitoring.platformUpdates'),
              children: <PlatformUpdateContent />,
            }
          : null,
      ].filter(Boolean) as Array<{ key: string; label: string; children: ReactNode }>,
    [access.canVisitPlatformUpdate, access.canVisitSystemMonitoringRedis, access.canVisitSystemMonitoringService],
  );

  const isApiDocsRoute = location.pathname === '/settings/api-docs';
  if (isApiDocsRoute) {
    return <ApiDocsContent />;
  }

  const resolvedActiveTab = tabs.some((item) => item.key === activeTab) ? activeTab : tabs[0]?.key;

  if (!resolvedActiveTab) {
    history.replace('/403');
    return null;
  }

  return (
    <ManagementPage title={t('ui.settings.monitoring.monitoring.systemMonitoring')}>
      <ManagementPageBody>
        <Tabs
          activeKey={resolvedActiveTab}
          items={tabs}
          onChange={(key) => {
            history.replace(`/settings/monitoring?tab=${key}`);
          }}
        />
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default MonitoringPage;
