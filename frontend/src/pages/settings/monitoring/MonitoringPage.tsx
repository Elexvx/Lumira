import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { history, useAccess, useLocation } from '@umijs/max';
import { Alert, Button, Card, Col, Descriptions, Result, Row, Space, Spin, Statistic, Steps, Tag, Tabs, Tooltip, Typography, theme } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { ApiOutlined, CheckCircleOutlined, CloudSyncOutlined, ExclamationCircleOutlined, GithubOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { tokenManager } from '@/auth/token';
import { AUTHORIZATION_HEADER } from '@/constants/http';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementTable } from '@/features/management/ManagementTable';
import type {
  MessageWebSocketRuntime,
  MessageWebSocketTenantRuntime,
  PlatformUpdateStatus,
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
    label: '已同步',
    title: '当前版本已同步',
  },
  UPDATE_AVAILABLE: {
    color: 'orange',
    icon: <CloudSyncOutlined />,
    label: '有更新',
    title: '发现新版本',
  },
  UNKNOWN: {
    color: 'gold',
    icon: <ExclamationCircleOutlined />,
    label: '待确认',
    title: '版本信息不完整',
  },
  CHECK_FAILED: {
    color: 'red',
    icon: <ExclamationCircleOutlined />,
    label: '检查失败',
    title: '更新源暂时不可用',
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
    { title: '租户', dataIndex: 'tenantId', width: 'var(--saas-spacing-180)' },
    { title: '连接数', dataIndex: 'connectionCount', width: 'var(--saas-spacing-120)' },
  ];

  const serviceColumns = [
    { title: '服务', dataIndex: 'serviceName' },
    { title: '地址', dataIndex: 'baseUrl', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 'var(--saas-spacing-100)',
      render: (_: unknown, record: ServiceInstanceStatus) => <Tag color={record.status === 'UP' ? 'green' : 'red'}>{record.status || 'DOWN'}</Tag>,
    },
    { title: '响应', dataIndex: 'responseTimeMs', width: 'var(--saas-spacing-100)', render: (_: unknown, record: ServiceInstanceStatus) => (record.responseTimeMs == null ? '-' : `${record.responseTimeMs} ms`) },
    { title: '检测时间', dataIndex: 'checkedAt', width: 'var(--saas-spacing-180)', render: (_: unknown, record: ServiceInstanceStatus) => formatDateTime(record.checkedAt) },
    { title: '说明', dataIndex: 'errorMessage', ellipsis: true, render: (_: unknown, record: ServiceInstanceStatus) => record.errorMessage || '-' },
  ];

  const apiDocColumns = [
    { title: '服务', dataIndex: 'serviceName', width: 'var(--saas-spacing-180)' },
    { title: 'OpenAPI 地址', dataIndex: 'url', ellipsis: true },
    {
      title: '服务状态',
      dataIndex: 'status',
      width: 'var(--saas-spacing-120)',
      render: (_: unknown, record: ServiceApiDocStatus) => <Tag color={record.status === 'UP' ? 'green' : 'red'}>{record.status || 'DOWN'}</Tag>,
    },
  ];

  return { websocketColumns, serviceColumns, apiDocColumns };
};

const buildRedisColumns = ({ isDesktop }: { isDesktop: boolean }) => {
  const commandColumns: ProColumns<RedisMonitorCommandStat>[] = [
    { title: '命令', dataIndex: 'command', width: 'var(--saas-spacing-180)', fixed: isDesktop ? ('left' as const) : undefined },
    { title: '调用次数', dataIndex: 'calls', width: 'var(--saas-spacing-140)', render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.calls) },
    { title: '耗时(ms)', dataIndex: 'totalUsec', width: 'var(--saas-spacing-160)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.totalUsec) },
    { title: '平均耗时(ms)', dataIndex: 'avgUsec', width: 'var(--saas-spacing-160)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => record.avgUsec.toFixed(2) },
    { title: '拒绝次数', dataIndex: 'rejectedCalls', width: 'var(--saas-spacing-120)', responsive: ['lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.rejectedCalls) },
    { title: '失败次数', dataIndex: 'failedCalls', width: 'var(--saas-spacing-120)', responsive: ['lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorCommandStat) => formatNumber(record.failedCalls) },
  ];

  const keyspaceColumns: ProColumns<RedisMonitorKeyspace>[] = [
    { title: '数据库', dataIndex: 'database', width: 'var(--saas-spacing-120)' },
    { title: '键数量', dataIndex: 'keys', width: 'var(--saas-spacing-120)', render: (_: unknown, record: RedisMonitorKeyspace) => formatNumber(record.keys) },
    { title: '过期键数量', dataIndex: 'expires', width: 'var(--saas-spacing-140)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorKeyspace) => formatNumber(record.expires) },
    { title: '平均TTL(ms)', dataIndex: 'avgTtl', width: 'var(--saas-spacing-160)', responsive: ['md', 'lg', 'xl', 'xxl'] as const, render: (_: unknown, record: RedisMonitorKeyspace) => formatNumber(record.avgTtl) },
  ];

  const clientColumns: ProColumns<RedisMonitorClient>[] = [
    { title: '地址', dataIndex: 'addressPort', width: 'var(--saas-spacing-180)' },
    { title: '名称', dataIndex: 'name', width: 'var(--saas-spacing-160)' },
    { title: '空闲(s)', dataIndex: 'idle', width: 'var(--saas-spacing-100)', responsive: ['md', 'lg', 'xl', 'xxl'] as const },
    { title: '年龄(s)', dataIndex: 'age', width: 'var(--saas-spacing-100)', responsive: ['md', 'lg', 'xl', 'xxl'] as const },
    { title: '数据库', dataIndex: 'databaseId', width: 'var(--saas-spacing-100)', responsive: ['md', 'lg', 'xl', 'xxl'] as const },
    { title: '标记', dataIndex: 'flags', width: 'var(--saas-spacing-140)', responsive: ['lg', 'xl', 'xxl'] as const, ellipsis: true },
    { title: '最后命令', dataIndex: 'lastCommand', width: 'var(--saas-spacing-140)', responsive: ['lg', 'xl', 'xxl'] as const, ellipsis: true },
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

  const updateStatus = query.data;
  const statusKey = resolveStatusKey(updateStatus);
  const currentStatusMeta = statusMeta[statusKey] || statusMeta.UNKNOWN;
  const latestUrl = updateStatus?.latest?.url;
  const canCompare = Boolean(updateStatus?.currentKnown && updateStatus?.latestKnown);
  const detailDescription = updateStatus?.errorMessage || updateStatus?.actionRequired || '无需处理。';

  const checkSteps = useMemo(
    () => [
      {
        title: '读取当前版本',
        status: updateStatus?.currentKnown === false ? 'wait' : 'finish',
        description: updateStatus?.currentKnown === false ? '缺少提交号' : shortCommit(updateStatus?.current?.commitId),
      },
      {
        title: '连接更新源',
        status: statusKey === 'CHECK_FAILED' ? 'error' : updateStatus?.latestKnown === false ? 'wait' : 'finish',
        description: updateStatus?.sourceType === 'github' ? 'GitHub' : updateStatus?.sourceType || '-',
      },
      {
        title: '比较提交',
        status: statusKey === 'CHECK_FAILED' ? 'wait' : canCompare ? 'finish' : 'wait',
        description: canCompare ? updateStatus?.comparisonBasis || 'commit' : '等待完整版本信息',
      },
      {
        title: '发布动作',
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
      message.success(result.updateAvailable ? '发现新版本' : result.status === 'UNKNOWN' ? '版本信息待确认' : '当前已经是最新版本');
    } catch (error) {
      showErrorMessage(error, '检查更新失败');
    }
  };

  return {
    query,
    updateStatus,
    statusKey,
    currentStatusMeta,
    latestUrl,
    detailDescription,
    checkSteps,
    handleCheck,
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
          throw new Error(`接口文档加载失败：${response.status}`);
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
        setLoadError(error instanceof Error ? error.message : '接口文档加载失败');
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
      <ManagementPage title="接口文档">
        <Result status="403" title="请先登录" subTitle="接口文档只对已登录用户开放。" />
      </ManagementPage>
    );
  }

  return (
    <ManagementPage
      title="接口文档"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => window.location.reload()}>
            刷新页面
          </Button>
        </Space>
      }
    >
      <Card bodyStyle={{ padding: 0, overflow: 'hidden', borderRadius: 'var(--saas-card-radius)' }}>
        <div style={{ minHeight: 'calc(100vh - var(--saas-spacing-220))', background: token.colorBgContainer }}>
          {isLoading ? (
            <div style={{ display: 'grid', minHeight: 'calc(100vh - var(--saas-spacing-220))', placeItems: 'center' }}>
              <Spin tip="正在加载接口文档..." />
            </div>
          ) : loadError ? (
            <div style={{ padding: token.paddingLG }}>
              <Alert message="接口文档加载失败" description={loadError} type="error" showIcon />
            </div>
          ) : (
            <iframe
              title="接口文档"
              srcDoc={buildSwaggerHtml(apiSpec, schemeContainerVerticalPadding)}
              sandbox="allow-scripts allow-forms allow-popups"
              style={{
                width: '100%',
                minHeight: 'calc(100vh - var(--saas-spacing-220))',
                border: 0,
                display: 'block',
              }}
            />
          )}
        </div>
      </Card>
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
          {expanded ? '收起' : '展开'}
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
        title: '内存趋势 (MB)',
        subtitle: `最近 ${MAX_TREND_SAMPLES} 次采样`,
        points: memoryTrend.map((item) => ({ ...item, value: item.value / 1024 / 1024 })),
        valueFormatter: (value: number) => `${value.toFixed(2)} MB`,
      },
      {
        title: '吞吐趋势 (OPS)',
        subtitle: `最近 ${MAX_TREND_SAMPLES} 次采样`,
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
    updateStatus,
    statusKey,
    currentStatusMeta,
    latestUrl,
    detailDescription,
    checkSteps,
    handleCheck,
    formatDateTime,
    shortCommit,
  } = usePlatformUpdateMonitor();
  const { isMobile } = useResponsive();
  const rowGutter = resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile);
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const compactSectionGap = resolveResponsiveValue(APP_SPACING.compactSectionGap, isMobile);

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
            <Statistic title="当前提交" value={shortCommit(updateStatus?.current?.commitId)} valueStyle={{ fontSize: 22 }} />
          </Col>
          <Col xs={24} sm={8} lg={4}>
            <Statistic title="最新提交" value={shortCommit(updateStatus?.latest?.commitId)} valueStyle={{ fontSize: 22 }} />
          </Col>
          <Col xs={24} sm={8} lg={3}>
            <Statistic title="检查时间" value={formatDateTime(updateStatus?.checkedAt)} valueStyle={{ fontSize: 14 }} />
          </Col>
          <Col xs={24} lg={3}>
            <Space wrap className="saas-update-actions">
              <Tooltip title="重新检查更新源">
                <Button type="primary" icon={<ReloadOutlined />} loading={query.isFetching} onClick={handleCheck}>
                  检查
                </Button>
              </Tooltip>
              {latestUrl ? (
                <Tooltip title="打开更新源提交">
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
          message="当前部署缺少提交信息"
          description="更新中心已经连通更新源，但当前运行版本没有携带 GIT_COMMIT，不能可靠判断是否落后。部署时注入提交号后会自动恢复精确比较。"
        />
      ) : null}
      {statusKey === 'CHECK_FAILED' ? (
        <Alert type="error" showIcon message="更新源检查失败" description={updateStatus?.errorMessage || '请检查更新源地址和服务器网络。'} />
      ) : null}
      <Space direction="vertical" size={sectionGap} style={{ width: '100%' }} className="saas-monitoring-tab-pane">
        <Row gutter={rowGutter}>
          <Col xs={24} lg={12}>
            <Card title="当前运行版本" className="saas-update-version-card">
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="版本">{updateStatus?.current?.version || '-'}</Descriptions.Item>
                <Descriptions.Item label="提交">
                  <Typography.Text copyable={{ text: updateStatus?.current?.commitId || '' }} className="saas-update-mono">
                    {updateStatus?.current?.commitId?.slice(0, 12) || '-'}
                  </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label="分支">{updateStatus?.current?.branch || '-'}</Descriptions.Item>
                <Descriptions.Item label="构建时间">{formatDateTime(updateStatus?.current?.buildTime)}</Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="更新源版本" className="saas-update-version-card">
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="版本">{updateStatus?.latest?.version || '-'}</Descriptions.Item>
                <Descriptions.Item label="提交">
                  <Typography.Text copyable={{ text: updateStatus?.latest?.commitId || '' }} className="saas-update-mono">
                    {updateStatus?.latest?.commitId?.slice(0, 12) || '-'}
                  </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label="分支">{updateStatus?.latest?.branch || '-'}</Descriptions.Item>
                <Descriptions.Item label="提交时间">{formatDateTime(updateStatus?.latest?.releasedAt)}</Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
        </Row>
        <Card title="检查链路" loading={query.isLoading && !updateStatus}>
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
        <Card title="更新源">
          <Descriptions size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label="来源类型">
              <Tag icon={<ApiOutlined />} color={updateStatus?.sourceType === 'github' ? 'blue' : 'default'}>
                {updateStatus?.sourceType === 'github' ? 'GitHub' : updateStatus?.sourceType || '-'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="比较依据">{updateStatus?.comparisonBasis || '-'}</Descriptions.Item>
            <Descriptions.Item label="最新说明" span={2}>
              {updateStatus?.latest?.title || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="地址" span={2}>
              <Typography.Text copyable ellipsis style={{ maxWidth: '100%' }}>
                {updateStatus?.sourceUrl || '-'}
              </Typography.Text>
            </Descriptions.Item>
          </Descriptions>
        </Card>
        <Card title="安全边界">
          <Space direction="vertical" size={sectionGap}>
            <Typography.Text>
              <SafetyCertificateOutlined /> 只读检查更新源，不自动拉取代码。
            </Typography.Text>
            <Typography.Text>
              <SafetyCertificateOutlined /> 发布仍需要备份、部署、健康检查和审计记录。
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
      <Card loading={query.isLoading && !redis} title="Redis信息">
        <Row gutter={rowGutter}>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="Redis版本" value={overview?.version || '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="运行模式" value={overview?.mode || '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="端口" value={overview?.port ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="客户端数" value={overview?.connectedClients ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="运行时间(天)" value={overview?.uptimeDays ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="命中率" value={formatPercent(overview?.hitRate)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="QPS" value={overview?.instantaneousOpsPerSec ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="键数量" value={overview?.keyCount ?? '-'} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="内存使用量" value={formatBytes(overview?.memoryUsedBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="内存峰值" value={formatBytes(overview?.memoryPeakBytes)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="内存使用率" value={formatPercent(overview?.memoryUsagePercent)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="总连接数" value={overview?.totalConnectionsReceived ?? '-'} valueStyle={valueStyle} />
          </Col>
        </Row>
        <Row gutter={rowGutter} style={{ marginTop: sectionGap }}>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="总命中" value={formatNumber(overview?.hits)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="总未命中" value={formatNumber(overview?.misses)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="总命令数" value={formatNumber(overview?.totalCommandsProcessed)} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={12} xl={4}>
            <Statistic title="采样时间" value={formatDateTime(redis?.sampleTime)} valueStyle={{ ...valueStyle, fontSize: 18 }} />
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
      <Card title="命令统计" loading={query.isLoading && !redis}>
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
      <Card title="Key信息" loading={query.isLoading && !redis}>
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
      <Card title="连接客户端" loading={query.isLoading && !redis}>
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
      <Typography.Title level={3} style={{ margin: 0 }}>
        服务监控
      </Typography.Title>
      <Row gutter={rowGutter}>
        <Col xs={24} lg={12}>
          <Card title="CPU" loading={query.isLoading && !service} style={{ height: '100%' }} bodyStyle={{ minHeight: isDesktop ? 108 : 0 }}>
            <Row gutter={rowGutter}>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title="用户使用率" value={service?.cpu?.processUsagePercent ?? 0} precision={2} suffix="%" valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title="系统使用率" value={service?.cpu?.systemUsagePercent ?? 0} precision={2} suffix="%" valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title="当前空闲率" value={service?.cpu?.idlePercent ?? 0} precision={2} suffix="%" valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title="平均负载" value={service?.cpu?.loadAverage ?? '-'} valueStyle={valueStyle} />
              </Col>
            </Row>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="内存" loading={query.isLoading && !service} style={{ height: '100%' }} bodyStyle={{ minHeight: isDesktop ? 108 : 0 }}>
            <Row gutter={rowGutter}>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title="总内存" value={formatBytes(service?.memory?.totalBytes)} valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title="已用内存" value={formatBytes(service?.memory?.usedBytes)} valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title="剩余内存" value={formatBytes(service?.memory?.freeBytes)} valueStyle={valueStyle} />
              </Col>
              <Col xs={24} sm={12} xxl={6}>
                <Statistic title="使用率" value={formatPercent(service?.memory?.usagePercent)} valueStyle={valueStyle} />
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>

      <Card title="WebSocket 运行监控" loading={webSocketQuery.isLoading && !webSocketQuery.data}>
        <Row gutter={rowGutter}>
          <Col xs={24} sm={8}>
            <Statistic title="当前连接数" value={webSocketQuery.data?.activeConnections ?? 0} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={8}>
            <Statistic title="在线租户数" value={webSocketQuery.data?.tenantCount ?? 0} valueStyle={valueStyle} />
          </Col>
          <Col xs={24} sm={8}>
            <Statistic title="在线用户数" value={webSocketQuery.data?.userCount ?? 0} valueStyle={valueStyle} />
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

      <Card title="基础服务健康" loading={query.isLoading && !service}>
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

      <Card title="接口文档入口" loading={query.isLoading && !service}>
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

      <Card title="服务器信息" loading={query.isLoading && !service}>
        <Descriptions {...detailDescriptionsProps}>
          <Descriptions.Item label="服务器名称">
            <BreakableValue value={service?.server?.serverName} />
          </Descriptions.Item>
          <Descriptions.Item label="服务器IP">
            <NumericValue value={service?.server?.serverIp || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label="操作系统">
            <BreakableValue value={service?.server?.osName} />
          </Descriptions.Item>
          <Descriptions.Item label="系统架构">
            <BreakableValue value={service?.server?.osArch} />
          </Descriptions.Item>
          <Descriptions.Item label="系统版本">
            <NumericValue value={service?.server?.osVersion || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label="项目路径">
            <BreakableValue value={service?.server?.projectPath} />
          </Descriptions.Item>
          <Descriptions.Item label="安装路径">
            <BreakableValue value={service?.server?.installPath} />
          </Descriptions.Item>
          <Descriptions.Item label="用户目录">
            <BreakableValue value={service?.server?.userHome} />
          </Descriptions.Item>
          <Descriptions.Item label="临时目录" span={fullRowSpan}>
            <BreakableValue value={service?.server?.tempDir} />
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Java虚拟机信息" loading={query.isLoading && !service}>
        <Descriptions {...detailDescriptionsProps}>
          <Descriptions.Item label="Java名称">
            <BreakableValue value={service?.jvm?.vmName} />
          </Descriptions.Item>
          <Descriptions.Item label="Java版本">
            <NumericValue value={service?.jvm?.javaVersion || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label="虚拟机版本">
            <NumericValue value={service?.jvm?.vmVersion || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label="虚拟机厂商">
            <BreakableValue value={service?.jvm?.vmVendor} />
          </Descriptions.Item>
          <Descriptions.Item label="启动时间">
            <NumericValue value={service?.jvm?.startTime || '-'} />
          </Descriptions.Item>
          <Descriptions.Item label="运行时长">
            <NumericValue value={String(service?.jvm?.uptimeSeconds ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label="进程ID">
            <NumericValue value={String(service?.jvm?.pid ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label="线程数">
            <NumericValue value={String(service?.jvm?.threadCount ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label="守护线程">
            <NumericValue value={String(service?.jvm?.daemonThreadCount ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label="峰值线程数">
            <NumericValue value={String(service?.jvm?.peakThreadCount ?? '-')} />
          </Descriptions.Item>
          <Descriptions.Item label="Java Home" span={fullRowSpan}>
            <BreakableValue value={service?.jvm?.javaHome} />
          </Descriptions.Item>
          <Descriptions.Item label="启动参数" span={fullRowSpan}>
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
              label: '服务监控',
              children: <ServiceMonitorContent />,
            }
          : null,
        access.canVisitSystemMonitoringRedis
          ? {
              key: 'redis',
              label: 'Redis监控',
              children: <RedisMonitorContent />,
            }
          : null,
        access.canVisitPlatformUpdate
          ? {
              key: 'update',
              label: '平台更新',
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
    <ManagementPage title="系统监控">
      <Tabs
        activeKey={resolvedActiveTab}
        items={tabs}
        onChange={(key) => {
          history.replace(`/settings/monitoring?tab=${key}`);
        }}
      />
    </ManagementPage>
  );
};

export default MonitoringPage;
