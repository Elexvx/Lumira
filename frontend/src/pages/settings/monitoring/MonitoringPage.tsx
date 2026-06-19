import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { getLocale, history, useAccess, useLocation } from '@umijs/max';
import { Alert, Button, Card, Col, Descriptions, Modal, Result, Row, Space, Spin, Statistic, Steps, Tag, Tabs, Tooltip, Typography, theme } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { ApiOutlined, CheckCircleOutlined, CloudDownloadOutlined, CloudSyncOutlined, ExclamationCircleOutlined, GithubOutlined, ReloadOutlined, RollbackOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { tokenManager } from '@/auth/token';
import { AUTHORIZATION_HEADER } from '@/constants/http';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import type {
  MessageWebSocketRuntime,
  MessageWebSocketTenantRuntime,
  PlatformUpdateStatus,
  PlatformUpdateTask,
  RedisMonitorClient,
  RedisMonitorCommandStat,
  RedisMonitorKeyspace,
  RedisMonitorSnapshot,
  ServiceApiDocStatus,
  ServiceInstanceStatus,
  ServiceMonitorSnapshot,
} from '@/types/api';
import { useQuery } from '@tanstack/react-query';
import { message } from '@/theme/antdFeedbackBridge';
import { request } from '@/services/common/request';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

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
  return date.toLocaleString('zh-CN', { hour12: false });
};

const formatNumber = (value?: number | null) => {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '-';
  }
  return value.toLocaleString('zh-CN');
};

const formatLoadAverage = (value?: number | null) => {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return t('当前系统不支持', 'Not supported by this system');
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
    label: t('已同步', 'Up to date'),
    title: t('当前版本已同步', 'The current version is up to date'),
  },
  UPDATE_AVAILABLE: {
    color: 'orange',
    icon: <CloudSyncOutlined />,
    label: t('有更新', 'Update available'),
    title: t('发现新版本', 'A new version was found'),
  },
  UNKNOWN: {
    color: 'gold',
    icon: <ExclamationCircleOutlined />,
    label: t('待确认', 'Pending confirmation'),
    title: t('版本信息不完整', 'Version information is incomplete'),
  },
  CHECK_FAILED: {
    color: 'red',
    icon: <ExclamationCircleOutlined />,
    label: t('检查失败', 'Check failed'),
    title: t('更新源暂时不可用', 'The update source is temporarily unavailable'),
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

const buildServiceColumns = () => {
  const websocketColumns = [
    { title: t('租户', 'Tenant'), dataIndex: 'tenantId', width: 'var(--saas-spacing-180)' },
    { title: t('连接数', 'Connections'), dataIndex: 'connectionCount', width: 'var(--saas-spacing-120)' },
  ];

  const serviceColumns = [
    { title: t('服务', 'Service'), dataIndex: 'serviceName' },
    { title: t('地址', 'Address'), dataIndex: 'baseUrl', ellipsis: true },
    {
      title: t('状态', 'Status'),
      dataIndex: 'status',
      width: 'var(--saas-spacing-100)',
      render: (_: unknown, record: ServiceInstanceStatus) => <Tag color={record.status === 'UP' ? 'green' : 'red'}>{record.status || 'DOWN'}</Tag>,
    },
    { title: t('响应', 'Response'), dataIndex: 'responseTimeMs', width: 'var(--saas-spacing-100)', render: (_: unknown, record: ServiceInstanceStatus) => (record.responseTimeMs == null ? '-' : `${record.responseTimeMs} ms`) },
    { title: t('检测时间', 'Checked at'), dataIndex: 'checkedAt', width: 'var(--saas-spacing-180)', render: (_: unknown, record: ServiceInstanceStatus) => formatDateTime(record.checkedAt) },
    { title: t('说明', 'Note'), dataIndex: 'errorMessage', ellipsis: true, render: (_: unknown, record: ServiceInstanceStatus) => record.errorMessage || '-' },
  ];

  const apiDocColumns = [
    { title: t('服务', 'Service'), dataIndex: 'serviceName', width: 'var(--saas-spacing-180)' },
    { title: t('OpenAPI 地址', 'OpenAPI URL'), dataIndex: 'url', ellipsis: true },
    {
      title: t('服务状态', 'Service status'),
      dataIndex: 'status',
      width: 'var(--saas-spacing-120)',
      render: (_: unknown, record: ServiceApiDocStatus) => <Tag color={record.status === 'UP' ? 'green' : 'red'}>{record.status || 'DOWN'}</Tag>,
    },
  ];

  return { websocketColumns, serviceColumns, apiDocColumns };
};

const buildRedisColumns = ({ isDesktop }: { isDesktop: boolean }) => {
  const commandColumns: ProColumns<RedisMonitorCommandStat>[] = [
    { title: t('命令', 'Command'), dataIndex: 'command', width: 'var(--saas-spacing-180)', fixed: isDesktop ? ('left' as const) : undefined },
    { title: t('调用次数', 'Calls'), dataIndex: 'calls', width: 'var(--saas-spacing-140)', render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.calls) },
    { title: t('耗时(ms)', 'Total time (ms)'), dataIndex: 'totalUsec', width: 'var(--saas-spacing-160)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.totalUsec) },
    { title: t('平均耗时(ms)', 'Average time (ms)'), dataIndex: 'avgUsec', width: 'var(--saas-spacing-160)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => record.avgUsec.toFixed(2) },
    { title: t('拒绝次数', 'Rejected'), dataIndex: 'rejectedCalls', width: 'var(--saas-spacing-120)', responsive: ['lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.rejectedCalls) },
    { title: t('失败次数', 'Failed'), dataIndex: 'failedCalls', width: 'var(--saas-spacing-120)', responsive: ['lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.failedCalls) },
  ];

  const keyspaceColumns: ProColumns<RedisMonitorKeyspace>[] = [
    { title: t('数据库', 'Database'), dataIndex: 'database', width: 'var(--saas-spacing-120)' },
    { title: t('键数量', 'Keys'), dataIndex: 'keys', width: 'var(--saas-spacing-120)', render: (_: unknown, record: RedisMonitorKeyspace) => formatNumber(record.keys) },
    { title: t('过期键数量', 'Expired keys'), dataIndex: 'expires', width: 'var(--saas-spacing-140)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorKeyspace) => formatNumber(record.expires) },
    { title: t('平均TTL(ms)', 'Average TTL (ms)'), dataIndex: 'avgTtl', width: 'var(--saas-spacing-160)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorKeyspace) => formatNumber(record.avgTtl) },
  ];

  const clientColumns: ProColumns<RedisMonitorClient>[] = [
    { title: t('地址', 'Address'), dataIndex: 'addressPort', width: 'var(--saas-spacing-180)' },
    { title: t('名称', 'Name'), dataIndex: 'name', width: 'var(--saas-spacing-160)' },
    { title: t('空闲(s)', 'Idle (s)'), dataIndex: 'idle', width: 'var(--saas-spacing-100)', responsive: ['md', 'lg', 'xl', 'xxl'] as const },
    { title: t('年龄(s)', 'Age (s)'), dataIndex: 'age', width: 'var(--saas-spacing-100)', responsive: ['md', 'lg', 'xl', 'xxl'] as const },
    { title: t('数据库', 'Database'), dataIndex: 'databaseId', width: 'var(--saas-spacing-100)', responsive: ['md', 'lg', 'xl', 'xxl'] as const },
    { title: t('标记', 'Flags'), dataIndex: 'flags', width: 'var(--saas-spacing-140)', responsive: ['lg', 'xl', 'xxl'] as const, ellipsis: true },
    { title: t('最后命令', 'Last command'), dataIndex: 'lastCommand', width: 'var(--saas-spacing-140)', responsive: ['lg', 'xl', 'xxl'] as const, ellipsis: true },
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
  const latestUrl = updateStatus?.latest?.url;
  const canCompare = Boolean(updateStatus?.currentKnown && updateStatus?.latestKnown);
  const detailDescription = updateStatus?.errorMessage || updateStatus?.actionRequired || t('无需处理。', 'No action required.');

  const checkSteps = useMemo(
    () => [
      {
        title: t('读取当前版本', 'Read current version'),
        status: updateStatus?.currentKnown === false ? 'wait' : 'finish',
        description: updateStatus?.currentKnown === false ? t('缺少提交号', 'Missing commit ID') : shortCommit(updateStatus?.current?.commitId),
      },
      {
        title: t('连接更新源', 'Connect update source'),
        status: statusKey === 'CHECK_FAILED' ? 'error' : updateStatus?.latestKnown === false ? 'wait' : 'finish',
        description: updateStatus?.sourceType === 'github' ? 'GitHub' : updateStatus?.sourceType || '-',
      },
      {
        title: t('比较提交', 'Compare commits'),
        status: statusKey === 'CHECK_FAILED' ? 'wait' : canCompare ? 'finish' : 'wait',
        description: canCompare ? updateStatus?.comparisonBasis || 'commit' : t('等待完整版本信息', 'Waiting for complete version information'),
      },
      {
        title: t('发布动作', 'Release action'),
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
      message.success(result.updateAvailable ? t('发现新版本', 'A new version was found') : result.status === 'UNKNOWN' ? t('版本信息待确认', 'Version information pending confirmation') : t('当前已经是最新版本', 'You are already on the latest version'));
    } catch (error) {
      showErrorMessage(error, t('检查更新失败', 'Failed to check for updates'));
    }
  };

  const refreshAll = async () => {
    await Promise.all([query.refetch(), tasksQuery.refetch()]);
  };

  const handleInstall = async () => {
    Modal.confirm({
      title: t('确认手动安装平台更新？', 'Install platform update?'),
      content: t('系统将通过宿主机 lumira-updater 执行备份、拉取镜像、重启和健康检查。请确认当前处于维护窗口。', 'Lumira will ask the host updater to back up, pull images, restart, and run health checks. Confirm you are in a maintenance window.'),
      okText: t('开始更新', 'Start update'),
      cancelText: t('取消', 'Cancel'),
      onOk: async () => {
        try {
          await request<PlatformUpdateTask>('/v1/system/update/install', {
            method: 'POST',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('更新任务已提交', 'Update task submitted'));
          await refreshAll();
        } catch (error) {
          showErrorMessage(error, t('提交更新任务失败', 'Failed to submit update task'));
        }
      },
    });
  };

  const handleRollback = async () => {
    Modal.confirm({
      title: t('确认回滚平台版本？', 'Rollback platform version?'),
      content: t('系统将使用 updater 最近一次保存的 deploy/.env 备份回滚镜像配置，并重新部署。', 'The updater will restore the latest saved deploy/.env backup and redeploy.'),
      okText: t('开始回滚', 'Start rollback'),
      cancelText: t('取消', 'Cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await request<PlatformUpdateTask>('/v1/system/update/rollback', {
            method: 'POST',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('回滚任务已提交', 'Rollback task submitted'));
          await refreshAll();
        } catch (error) {
          showErrorMessage(error, t('提交回滚任务失败', 'Failed to submit rollback task'));
        }
      },
    });
  };

  const taskColumns = useMemo<ProColumns<PlatformUpdateTask>[]>(
    () => [
      { title: t('类型', 'Type'), dataIndex: 'taskType', width: 'var(--saas-spacing-120)', render: (_: unknown, record) => record.taskType || '-' },
      {
        title: t('状态', 'Status'),
        dataIndex: 'status',
        width: 'var(--saas-spacing-120)',
        render: (_: unknown, record) => {
          const status = record.status || '-';
          const color = status === 'SUCCEEDED' || status === 'ROLLED_BACK' ? 'green' : status === 'FAILED' ? 'red' : 'blue';
          return <Tag color={color}>{status}</Tag>;
        },
      },
      { title: t('目标版本', 'Target version'), dataIndex: 'targetVersion', width: 'var(--saas-spacing-160)', render: (_: unknown, record) => record.targetVersion || '-' },
      { title: t('目标提交', 'Target commit'), dataIndex: 'targetCommit', width: 'var(--saas-spacing-160)', render: (_: unknown, record) => shortCommit(record.targetCommit) },
      { title: t('操作人', 'Operator'), dataIndex: 'createdByName', width: 'var(--saas-spacing-140)', render: (_: unknown, record) => record.createdByName || '-' },
      { title: t('更新时间', 'Updated at'), dataIndex: 'updatedAt', width: 'var(--saas-spacing-180)', render: (_: unknown, record) => formatDateTime(record.updatedAt) },
      { title: t('说明', 'Message'), dataIndex: 'logSummary', ellipsis: true, render: (_: unknown, record) => record.errorMessage || record.logSummary || '-' },
    ],
    [],
  );

  return {
    query,
    tasksQuery,
    updateStatus,
    statusKey,
    currentStatusMeta,
    latestUrl,
    detailDescription,
    checkSteps,
    handleCheck,
    handleInstall,
    handleRollback,
    taskColumns,
    formatDateTime,
    shortCommit,
  };
};

const BreakableValue = ({ value }: { value?: string | null }) => <span className="saas-monitor-break-value">{value || '-'}</span>;

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
  const fallback = t('接口文档加载失败：{status}', 'API docs failed to load: {status}').replace('{status}', String(response.status));
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
        setLoadError(error instanceof Error ? error.message : t('接口文档加载失败', 'Failed to load API docs'));
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
      <ManagementPage title={t('接口文档', 'API docs')}>
        <ManagementPageBody>
          <Result status="403" title={t('请先登录', 'Please log in first')} subTitle={t('接口文档只对已登录用户开放。', 'API docs are available only to signed-in users.')} />
        </ManagementPageBody>
      </ManagementPage>
    );
  }

  return (
    <ManagementPage
      title={t('接口文档', 'API docs')}
      className="saas-monitoring-api-docs-page"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => window.location.reload()}>
            {t('刷新页面', 'Refresh page')}
          </Button>
        </Space>
      }
    >
      <ManagementPageBody className="saas-monitoring-api-docs">
        <Card className="saas-monitoring-api-docs__card" bodyStyle={{ padding: 0, overflow: 'hidden', borderRadius: 'var(--saas-card-radius)', display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
          <div className="saas-monitoring-api-docs__surface">
            {isLoading ? (
              <div className="saas-monitoring-api-docs__loading">
                <Spin tip={t('正在加载接口文档...', 'Loading API docs...')} />
              </div>
            ) : loadError ? (
              <div style={{ padding: token.paddingLG }}>
                <Alert message={t('接口文档加载失败', 'Failed to load API docs')} description={loadError} type="error" showIcon />
              </div>
            ) : (
              <iframe
                title={t('接口文档', 'API docs')}
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
          {expanded ? t('收起', 'Collapse') : t('展开', 'Expand')}
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
  const width = APP_SPACING.monitoringTrendChart.width;
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

  return (
    <svg className="saas-redis-trend-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="trend chart" style={{ display: 'block', width: '100%', height: '100%' }}>
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
      {xAxisLabels.map((item) => (
          <text key={`${item.label}-${item.x}-label`} className="saas-redis-trend-chart__axis" x={item.x} y={height - chartAxisOffsetY} textAnchor="middle" fill={token.colorTextTertiary} fontSize={chartAxisFontSize}>
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
  const webSocketQuery = useQuery({
    queryKey: ['message-websocket-runtime'],
    queryFn: async () =>
      request<MessageWebSocketRuntime>('/v1/message/ws-runtime', {
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

  const webSocketRefreshRef = useRef(webSocketQuery.refetch);
  useEffect(() => {
    webSocketRefreshRef.current = webSocketQuery.refetch;
  }, [webSocketQuery.refetch]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      void refreshRef.current();
      void webSocketRefreshRef.current();
    }, REALTIME_REFRESH_INTERVAL_MS);
    return () => {
      window.clearInterval(timer);
    };
  }, []);

  const { websocketColumns, serviceColumns, apiDocColumns } = useMemo(() => buildServiceColumns(), []);

  return {
    isDesktop,
    isMobile,
    query,
    webSocketQuery,
    detailDescriptionsProps,
    fullRowSpan,
    valueStyle,
    websocketColumns,
    serviceColumns,
    apiDocColumns,
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
      label: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
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
        title: t('内存趋势 (MB)', 'Memory trend (MB)'),
        subtitle: t('最近 {count} 次采样', 'Latest {count} samples').replace('{count}', String(MAX_TREND_SAMPLES)),
        points: memoryTrend.map((item) => ({ ...item, value: item.value / 1024 / 1024 })),
        valueFormatter: (value: number) => `${value.toFixed(2)} MB`,
      },
      {
        title: t('吞吐趋势 (OPS)', 'Throughput trend (OPS)'),
        subtitle: t('最近 {count} 次采样', 'Latest {count} samples').replace('{count}', String(MAX_TREND_SAMPLES)),
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
    latestUrl,
    detailDescription,
    checkSteps,
    handleCheck,
    handleInstall,
    handleRollback,
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
  const canInstall = statusKey === 'UPDATE_AVAILABLE' && Boolean(updateStatus?.latest?.serverImage) && updaterAvailable && !isTaskRunning;
  const canRollback = updaterAvailable && !isTaskRunning;

  return (
    <div className="saas-update-center saas-monitoring-tab-pane">
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
            <Statistic title={t('当前提交', 'Current commit')} value={shortCommit(updateStatus?.current?.commitId)} valueStyle={{ fontSize: 22 }} />
          </Col>
          <Col xs={24} sm={8} lg={4}>
            <Statistic title={t('最新提交', 'Latest commit')} value={shortCommit(updateStatus?.latest?.commitId)} valueStyle={{ fontSize: 22 }} />
          </Col>
          <Col xs={24} sm={8} lg={3}>
            <Statistic title={t('检查时间', 'Checked at')} value={formatDateTime(updateStatus?.checkedAt)} valueStyle={{ fontSize: 14 }} />
          </Col>
          <Col xs={24} lg={3}>
            <Space wrap className="saas-update-actions">
              <Tooltip title={t('重新检查更新源', 'Re-check update source')}>
                <Button type="primary" icon={<ReloadOutlined />} loading={query.isFetching} onClick={handleCheck}>
                  {t('检查', 'Check')}
                </Button>
              </Tooltip>
              <Tooltip title={canInstall ? t('通过宿主机 updater 手动安装更新', 'Install through the host updater') : t('需要 manifest 镜像且没有运行中的任务', 'Requires manifest images and no running task')}>
                <Button icon={<CloudDownloadOutlined />} disabled={!canInstall} loading={isTaskRunning} onClick={handleInstall}>
                  {t('手动更新', 'Install')}
                </Button>
              </Tooltip>
              <Tooltip title={t('使用最近一次 updater 环境备份回滚', 'Rollback with the latest updater env backup')}>
                <Button danger icon={<RollbackOutlined />} disabled={!canRollback} onClick={handleRollback}>
                  {t('回滚', 'Rollback')}
                </Button>
              </Tooltip>
              {latestUrl ? (
              <Tooltip title={t('打开更新源提交', 'Open source commit')}>
                  <Button icon={<GithubOutlined />} href={latestUrl} target="_blank" rel="noreferrer" />
                </Tooltip>
              ) : null}
            </Space>
          </Col>
        </Row>
      </Card>
      {statusKey === 'UNKNOWN' ? (
        <Alert
          type="warning"
          showIcon
          message={t('当前部署缺少提交信息', 'The current deployment is missing commit information')}
          description={t('更新中心已经连通更新源，但当前运行版本没有携带 GIT_COMMIT，不能可靠判断是否落后。部署时注入提交号后会自动恢复精确比较。', 'The update center can reach the source, but the running build does not include GIT_COMMIT, so it cannot reliably determine whether it is behind. Injecting the commit ID during deployment restores accurate comparison.')}
        />
      ) : null}
      {statusKey === 'CHECK_FAILED' ? (
      <Alert type="error" showIcon message={t('更新源检查失败', 'Update source check failed')} description={updateStatus?.errorMessage || t('请检查更新源地址和服务器网络。', 'Please check the source URL and server network.')} />
      ) : null}
      {updateStatus && updateStatus.sourceReachable === false && statusKey === 'UP_TO_DATE' ? (
        <Alert
          type="info"
          showIcon
          message={t('已使用本地版本信息', 'Using local version information')}
          description={updateStatus.errorMessage || t('远程更新源暂不可用，当前已回退到本地 Git 提交作为版本基准。', 'The remote update source is unavailable, so the local Git commit is used as the version baseline.')}
        />
      ) : null}
      {updateStatus && !updaterAvailable ? (
        <Alert
          type="warning"
          showIcon
          message={t('平台更新代理未连接', 'Platform updater is not connected')}
          description={t('检查版本仍可使用；手动更新和回滚需要先启动并配置 lumira-updater。', 'Version checks still work; install and rollback require lumira-updater to be running and configured.')}
        />
      ) : null}
      {activeTask ? (
        <Alert
          type={activeTask.status === 'FAILED' ? 'error' : activeTask.status === 'SUCCEEDED' || activeTask.status === 'ROLLED_BACK' ? 'success' : 'info'}
          showIcon
          message={`${activeTask.taskType || 'UPDATE'} ${activeTask.status || ''}`}
          description={activeTask.errorMessage || activeTask.logSummary || t('更新代理正在处理任务，请稍后刷新状态。', 'The updater agent is processing the task. Refresh status later.')}
        />
      ) : null}
      <Space direction="vertical" size={sectionGap} style={{ width: '100%' }} className="saas-monitoring-tab-pane">
        <Row gutter={rowGutter}>
          <Col xs={24} lg={12}>
            <Card title={t('当前运行版本', 'Current running version')} className="saas-update-version-card">
              <Descriptions size="small" column={1}>
                <Descriptions.Item label={t('版本', 'Version')}>{updateStatus?.current?.version || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('提交', 'Commit')}>
                  <Typography.Text copyable={{ text: updateStatus?.current?.commitId || '' }} className="saas-update-mono">
                    {updateStatus?.current?.commitId?.slice(0, 12) || '-'}
                  </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label={t('分支', 'Branch')}>{updateStatus?.current?.branch || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('构建时间', 'Build time')}>{formatDateTime(updateStatus?.current?.buildTime)}</Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title={t('更新源版本', 'Source version')} className="saas-update-version-card">
              <Descriptions size="small" column={1}>
                <Descriptions.Item label={t('版本', 'Version')}>{updateStatus?.latest?.version || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('提交', 'Commit')}>
                  <Typography.Text copyable={{ text: updateStatus?.latest?.commitId || '' }} className="saas-update-mono">
                    {updateStatus?.latest?.commitId?.slice(0, 12) || '-'}
                  </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label={t('分支', 'Branch')}>{updateStatus?.latest?.branch || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('提交时间', 'Release time')}>{formatDateTime(updateStatus?.latest?.releasedAt)}</Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
        </Row>
        <Card title={t('检查链路', 'Check path')} loading={query.isLoading && !updateStatus}>
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
        <Card title={t('更新源', 'Update source')}>
          <Descriptions size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label={t('来源类型', 'Source type')}>
              <Tag icon={<ApiOutlined />} color={updateStatus?.sourceType === 'github' ? 'blue' : 'default'}>
                {updateStatus?.sourceType === 'github' ? 'GitHub' : updateStatus?.sourceType || '-'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('比较依据', 'Comparison basis')}>{updateStatus?.comparisonBasis || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('最新说明', 'Latest note')} span={2}>
              {updateStatus?.latest?.title || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('后端镜像', 'Server image')} span={2}>
              <Typography.Text copyable ellipsis style={{ maxWidth: '100%' }}>
                {updateStatus?.latest?.serverImage || '-'}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('前端镜像', 'Frontend image')} span={2}>
              <Typography.Text copyable ellipsis style={{ maxWidth: '100%' }}>
                {updateStatus?.latest?.frontendImage || '-'}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('迁移', 'Migration')}>
              <Tag color={updateStatus?.latest?.migrationRequired ? 'orange' : 'green'}>{updateStatus?.latest?.migrationRequired ? t('需要', 'Required') : t('不需要', 'Not required')}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('可回滚', 'Rollback')}>
              <Tag color={updateStatus?.latest?.rollbackSupported === false ? 'red' : 'green'}>{updateStatus?.latest?.rollbackSupported === false ? t('否', 'No') : t('是', 'Yes')}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('地址', 'Address')} span={2}>
              <Typography.Text copyable ellipsis style={{ maxWidth: '100%' }}>
                {updateStatus?.sourceUrl || '-'}
              </Typography.Text>
            </Descriptions.Item>
          </Descriptions>
        </Card>
        <Card title={t('更新任务历史', 'Update task history')} loading={tasksQuery.isLoading && !tasksQuery.data}>
          <ManagementTable<PlatformUpdateTask>
            rowKey="id"
            size="small"
            pagination={false}
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
        <Card title={t('安全边界', 'Safety boundary')}>
          <Space direction="vertical" size={sectionGap}>
            <Typography.Text>
              <SafetyCertificateOutlined /> {t('只读检查更新源，不自动拉取代码。', 'Read-only source check; no automatic code pull.')}
            </Typography.Text>
            <Typography.Text>
              <SafetyCertificateOutlined /> {t('发布仍需要备份、部署、健康检查和审计记录。', 'Releasing still requires backup, deployment, health checks, and audit records.')}
            </Typography.Text>
            {(updateStatus?.notes || []).map((note) => (
              <Typography.Text type="secondary" key={note}>
                {note}
              </Typography.Text>
            ))}
          </Space>
        </Card>
      </Space>
    </div>
  );
};

const RedisMonitorContent = () => {
  const { responsive, query, redis, valueStyle, trendCharts, commandColumns, keyspaceColumns, clientColumns } = useRedisMonitor();
  const overview = redis?.overview;
  const rowGutter = resolveResponsiveValue(APP_SPACING.rowGutterPanel, responsive.isMobile);
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }} className="saas-monitoring-tab-pane">
      <Card loading={query.isLoading && !redis} title={t('Redis信息', 'Redis info')}>
        <Row gutter={rowGutter}>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('Redis版本', 'Redis version')} value={overview?.version || '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('运行模式', 'Mode')} value={overview?.mode || '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('端口', 'Port')} value={overview?.port ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('客户端数', 'Clients')} value={overview?.connectedClients ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('运行时间(天)', 'Uptime (days)')} value={overview?.uptimeDays ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('命中率', 'Hit rate')} value={formatPercent(overview?.hitRate)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="QPS" value={overview?.instantaneousOpsPerSec ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('键数量', 'Keys')} value={overview?.keyCount ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('内存使用量', 'Memory used')} value={formatBytes(overview?.memoryUsedBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('内存峰值', 'Memory peak')} value={formatBytes(overview?.memoryPeakBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('内存使用率', 'Memory usage')} value={formatPercent(overview?.memoryUsagePercent)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('总连接数', 'Total connections')} value={overview?.totalConnectionsReceived ?? '-'} valueStyle={valueStyle} />
          </Col>
        </Row>
        <Row gutter={rowGutter} style={{ marginTop: sectionGap }}>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('总命中', 'Total hits')} value={formatNumber(overview?.hits)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('总未命中', 'Total misses')} value={formatNumber(overview?.misses)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('总命令数', 'Total commands')} value={formatNumber(overview?.totalCommandsProcessed)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title={t('采样时间', 'Sample time')} value={formatDateTime(redis?.sampleTime)} valueStyle={{ ...valueStyle, fontSize: 18 }} />
          </Col>
        </Row>
      </Card>
      <Row gutter={rowGutter}>
        {trendCharts.map((chart) => (
          <Col key={chart.title} xs={24} lg={12}>
            <Card title={chart.title} extra={<Typography.Text type="secondary">{chart.subtitle}</Typography.Text>}>
              <div style={{ height: 'var(--saas-spacing-220)' }}>
                <TrendAreaChart points={chart.points} valueFormatter={chart.valueFormatter || ((value) => value.toFixed(0))} />
              </div>
            </Card>
          </Col>
        ))}
      </Row>
      <Card title={t('命令统计', 'Command statistics')} loading={query.isLoading && !redis}>
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
      <Card title={t('Key信息', 'Key info')} loading={query.isLoading && !redis}>
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
      <Card title={t('连接客户端', 'Connected clients')} loading={query.isLoading && !redis}>
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
    webSocketQuery,
    detailDescriptionsProps,
    fullRowSpan,
    valueStyle,
    websocketColumns,
    serviceColumns,
    apiDocColumns,
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
                <Statistic title={t('用户使用率', 'Process CPU usage')} value={service?.cpu?.processUsagePercent ?? 0} precision={2} suffix="%" valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('系统使用率', 'System CPU usage')} value={service?.cpu?.systemUsagePercent ?? 0} precision={2} suffix="%" valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('当前空闲率', 'Idle rate')} value={service?.cpu?.idlePercent ?? 0} precision={2} suffix="%" valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('平均负载', 'Load average')} value={formatLoadAverage(service?.cpu?.loadAverage)} valueStyle={valueStyle} />
              </Col>
            </Row>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={t('宿主机内存', 'Host memory')} loading={query.isLoading && !service} style={{ height: '100%' }} bodyStyle={{ minHeight: isDesktop ? 108 : 0 }}>
            <Row gutter={rowGutter}>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('总内存', 'Total memory')} value={formatBytes(service?.memory?.hostTotalBytes ?? service?.memory?.totalBytes)} valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('已用内存', 'Used memory')} value={formatBytes(service?.memory?.hostUsedBytes ?? service?.memory?.usedBytes)} valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('可用内存', 'Available memory')} value={formatBytes(service?.memory?.hostFreeBytes ?? service?.memory?.freeBytes)} valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title={t('使用率', 'Usage')} value={formatPercent(service?.memory?.hostUsagePercent ?? service?.memory?.usagePercent)} valueStyle={valueStyle} />
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>

      <Card title={t('容器 / JVM 内存', 'Container / JVM memory')} loading={query.isLoading && !service}>
        <Row gutter={rowGutter}>
          <Col xs={24} sm={12} lg={6}>
            <Statistic title={t('容器内存限制', 'Container memory limit')} value={formatBytes(service?.memory?.totalBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Statistic title={t('容器已用内存', 'Container used memory')} value={formatBytes(service?.memory?.usedBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Statistic title={t('JVM 堆上限', 'JVM heap max')} value={formatBytes(service?.memory?.heapMaxBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Statistic title={t('JVM 已用堆', 'JVM heap used')} value={formatBytes(service?.memory?.heapUsedBytes)} valueStyle={valueStyle} />
          </Col>
        </Row>
      </Card>

      <Card title={t('WebSocket 运行监控', 'WebSocket monitoring')} loading={webSocketQuery.isLoading && !webSocketQuery.data}>
        <Row gutter={rowGutter}>
          <Col xs={24} sm={8}>
            <Statistic title={t('当前连接数', 'Active connections')} value={webSocketQuery.data?.activeConnections ?? 0} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={8}>
            <Statistic title={t('在线租户数', 'Online tenants')} value={webSocketQuery.data?.tenantCount ?? 0} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={8}>
            <Statistic title={t('在线用户数', 'Online users')} value={webSocketQuery.data?.userCount ?? 0} valueStyle={valueStyle} />
          </Col>
        </Row>
        <ManagementTable<MessageWebSocketTenantRuntime>
          rowKey="tenantId"
          size="small"
          pagination={false}
          isMobile={isMobile}
          search={false}
          onRefresh={() => webSocketQuery.refetch()}
          dataSource={webSocketQuery.data?.tenants || []}
          style={{ marginTop: sectionGap }}
          columns={websocketColumns}
        />
      </Card>

      <Card title={t('基础服务健康', 'Service health')} loading={query.isLoading && !service}>
        <ManagementTable<ServiceInstanceStatus>
          rowKey="serviceName"
          size="small"
          pagination={false}
          isMobile={isMobile}
          search={false}
          onRefresh={() => query.refetch()}
          dataSource={service?.services || []}
          columns={serviceColumns}
        />
      </Card>

      <Card title={t('接口文档入口', 'API docs entry')} loading={query.isLoading && !service}>
        <ManagementTable<ServiceApiDocStatus>
          rowKey="serviceName"
          size="small"
          pagination={false}
          isMobile={isMobile}
          search={false}
          onRefresh={() => query.refetch()}
          dataSource={service?.apiDocs || []}
          columns={apiDocColumns}
        />
      </Card>

      <Card title={t('服务器信息', 'Server information')} loading={query.isLoading && !service}>
        <Descriptions {...detailDescriptionsProps}>
          <Descriptions.Item label={t('服务器名称', 'Server name')}>
            <BreakableValue value={service?.server?.serverName} />
          </Descriptions.Item>
          <Descriptions.Item label={t('服务器IP', 'Server IP')}>
            <NumericValue value={service?.server?.serverIp || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label={t('操作系统', 'Operating system')}>
            <BreakableValue value={service?.server?.osName} />
          </Descriptions.Item>
          <Descriptions.Item label={t('系统架构', 'Architecture')}>
            <BreakableValue value={service?.server?.osArch} />
          </Descriptions.Item>
          <Descriptions.Item label={t('系统版本', 'OS version')}>
            <NumericValue value={service?.server?.osVersion || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label={t('项目路径', 'Project path')}>
            <BreakableValue value={service?.server?.projectPath} />
          </Descriptions.Item>
          <Descriptions.Item label={t('安装路径', 'Install path')}>
            <BreakableValue value={service?.server?.installPath} />
          </Descriptions.Item>
          <Descriptions.Item label={t('用户目录', 'Home directory')}>
            <BreakableValue value={service?.server?.userHome} />
          </Descriptions.Item>
          <Descriptions.Item label={t('临时目录', 'Temp directory')} span={fullRowSpan}>
            <BreakableValue value={service?.server?.tempDir} />
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={t('Java虚拟机信息', 'JVM information')} loading={query.isLoading && !service}>
        <Descriptions {...detailDescriptionsProps}>
          <Descriptions.Item label={t('Java名称', 'Java name')}>
            <BreakableValue value={service?.jvm?.vmName} />
          </Descriptions.Item>
          <Descriptions.Item label={t('Java版本', 'Java version')}>
            <NumericValue value={service?.jvm?.javaVersion || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label={t('虚拟机版本', 'VM version')}>
            <NumericValue value={service?.jvm?.vmVersion || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label={t('虚拟机厂商', 'VM vendor')}>
            <BreakableValue value={service?.jvm?.vmVendor} />
          </Descriptions.Item>
          <Descriptions.Item label={t('启动时间', 'Start time')}>
            <NumericValue value={service?.jvm?.startTime || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label={t('运行时长', 'Uptime')}>
            <NumericValue value={String(service?.jvm?.uptimeSeconds ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label={t('进程ID', 'Process ID')}>
            <NumericValue value={String(service?.jvm?.pid ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label={t('线程数', 'Thread count')}>
            <NumericValue value={String(service?.jvm?.threadCount ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label={t('守护线程', 'Daemon threads')}>
            <NumericValue value={String(service?.jvm?.daemonThreadCount ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label={t('峰值线程数', 'Peak threads')}>
            <NumericValue value={String(service?.jvm?.peakThreadCount ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label={t('Java Home', 'Java Home')} span={fullRowSpan}>
            <BreakableValue value={service?.jvm?.javaHome} />
          </Descriptions.Item>
          <Descriptions.Item label={t('启动参数', 'Startup arguments')} span={fullRowSpan}>
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
              label: t('服务监控', 'Service monitoring'),
              children: <ServiceMonitorContent />,
            }
          : null,
        access.canVisitSystemMonitoringRedis
          ? {
              key: 'redis',
              label: t('Redis监控', 'Redis monitoring'),
              children: <RedisMonitorContent />,
            }
          : null,
        access.canVisitPlatformUpdate
          ? {
              key: 'update',
              label: t('平台更新', 'Platform updates'),
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
    <ManagementPage title={t('系统监控', 'System monitoring')}>
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
