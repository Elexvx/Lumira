import { useEffect, useMemo, useRef, useState } from 'react';
import { type ProColumns } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Card, Col, Row, Space, Statistic, Typography, theme } from 'antd';
import { ManagementPage, ManagementTable } from '@/features/management';
import { useResponsive } from '@/hooks/useResponsive';
import { monitorService } from '@/services/system/monitor';
import type { RedisMonitorClient, RedisMonitorCommandStat, RedisMonitorKeyspace, RedisMonitorSnapshot } from '@/types/api';
import { formatBytes, formatDateTime, formatNumber, formatPercent } from './shared';

type TrendPoint = {
  label: string;
  memoryBytes: number;
  qps: number;
};

const MAX_TREND_SAMPLES = 5;
const REALTIME_REFRESH_INTERVAL_MS = 1000;
const valueStyle = { fontSize: 24, fontWeight: 700 };

const TrendAreaChart = ({
  points,
  valueFormatter,
}: {
  points: Array<{ label: string; value: number }>;
  valueFormatter: (value: number) => string;
}) => {
  const { token } = theme.useToken();
  const width = 420;
  const height = 220;
  const padding = { top: 24, right: 56, bottom: 54, left: 64 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  const values = points.map((item) => item.value);
  const maxValue = Math.max(...values, 1) * 1.08;
  const normalizedPoints = points.length ? points : [{ label: '-', value: 0 }];
  const coordinates = normalizedPoints.map((item, index) => {
    const x = normalizedPoints.length === 1
      ? padding.left + plotWidth / 2
      : padding.left + (plotWidth * index) / (normalizedPoints.length - 1);
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
            <text className="saas-redis-trend-chart__axis" x={padding.left - 8} y={y + 4} textAnchor="end" fill={token.colorTextTertiary} fontSize={11}>
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
        <text key={`${item.label}-${item.x}-label`} className="saas-redis-trend-chart__axis" x={item.x} y={height - 10} textAnchor="middle" fill={token.colorTextTertiary} fontSize={11}>
          {item.label}
        </text>
      ))}
    </svg>
  );
};

export const RedisMonitorContent = () => {
  const responsive = useResponsive();
  const query = useQuery({
    queryKey: ['redis-monitor'],
    queryFn: async () => monitorService.redis({ autoRedirectOnUnauthorized: false }),
  });
  const [samples, setSamples] = useState<TrendPoint[]>([]);
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
    const nextPoint: TrendPoint = {
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

  const commandColumns = useMemo<ProColumns<RedisMonitorCommandStat>[]>(
    () => [
      { title: '命令', dataIndex: 'command', width: 180, fixed: responsive.isDesktop ? 'left' : undefined },
      { title: '调用次数', dataIndex: 'calls', width: 140, render: (_, record) => formatNumber(record.calls) },
      { title: '耗时(ms)', dataIndex: 'totalUsec', width: 160, responsive: ['md', 'lg', 'xl', 'xxl'], render: (_, record) => formatNumber(record.totalUsec) },
      { title: '平均耗时(ms)', dataIndex: 'avgUsec', width: 160, responsive: ['md', 'lg', 'xl', 'xxl'], render: (_, record) => record.avgUsec.toFixed(2) },
      { title: '拒绝次数', dataIndex: 'rejectedCalls', width: 120, responsive: ['lg', 'xl', 'xxl'], render: (_, record) => formatNumber(record.rejectedCalls) },
      { title: '失败次数', dataIndex: 'failedCalls', width: 120, responsive: ['lg', 'xl', 'xxl'], render: (_, record) => formatNumber(record.failedCalls) },
    ],
    [responsive.isDesktop],
  );

  const keyspaceColumns = useMemo<ProColumns<RedisMonitorKeyspace>[]>(
    () => [
      { title: '数据库', dataIndex: 'database', width: 120, importance: 1 },
      { title: '键数量', dataIndex: 'keys', width: 120, importance: 1, render: (_, record) => formatNumber(record.keys) },
      { title: '过期键数量', dataIndex: 'expires', width: 140, responsive: ['md', 'lg', 'xl', 'xxl'], render: (_, record) => formatNumber(record.expires) },
      { title: '平均TTL(ms)', dataIndex: 'avgTtl', width: 160, responsive: ['md', 'lg', 'xl', 'xxl'], render: (_, record) => formatNumber(record.avgTtl) },
    ],
    [],
  );

  const clientColumns = useMemo<ProColumns<RedisMonitorClient>[]>(
    () => [
      { title: '地址', dataIndex: 'addressPort', width: 180, importance: 1 },
      { title: '名称', dataIndex: 'name', width: 160, importance: 1 },
      { title: '空闲(s)', dataIndex: 'idle', width: 100, responsive: ['md', 'lg', 'xl', 'xxl'] },
      { title: '年龄(s)', dataIndex: 'age', width: 100, responsive: ['md', 'lg', 'xl', 'xxl'] },
      { title: '数据库', dataIndex: 'databaseId', width: 100, responsive: ['md', 'lg', 'xl', 'xxl'] },
      { title: '标记', dataIndex: 'flags', width: 140, responsive: ['lg', 'xl', 'xxl'], ellipsis: true },
      { title: '最后命令', dataIndex: 'lastCommand', width: 140, responsive: ['lg', 'xl', 'xxl'], ellipsis: true },
    ],
    [],
  );

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card loading={query.isLoading && !redis} title="Redis信息">
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="Redis版本" value={redis?.overview.version || '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="运行模式" value={redis?.overview.mode || '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="端口" value={redis?.overview.port ?? '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="客户端数" value={redis?.overview.connectedClients ?? '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="运行时间(天)" value={redis?.overview.uptimeDays ?? '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="命中率" value={formatPercent(redis?.overview.hitRate)} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="QPS" value={redis?.overview.instantaneousOpsPerSec ?? '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="键数量" value={redis?.overview.keyCount ?? '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="内存使用量" value={formatBytes(redis?.overview.memoryUsedBytes)} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="内存峰值" value={formatBytes(redis?.overview.memoryPeakBytes)} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="内存使用率" value={formatPercent(redis?.overview.memoryUsagePercent)} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="总连接数" value={redis?.overview.totalConnectionsReceived ?? '-'} valueStyle={valueStyle} />
            </Col>
          </Row>

          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="总命中" value={formatNumber(redis?.overview.hits)} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="总未命中" value={formatNumber(redis?.overview.misses)} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="总命令数" value={formatNumber(redis?.overview.totalCommandsProcessed)} valueStyle={valueStyle} />
            </Col>
            <Col xs={24} sm={12} xl={4}>
              <Statistic title="采样时间" value={formatDateTime(redis?.sampleTime)} valueStyle={{ ...valueStyle, fontSize: 18 }} />
            </Col>
          </Row>
        </Card>

        <Row gutter={[16, 16]}>
          {trendCharts.map((chart) => (
            <Col key={chart.title} xs={24} lg={12}>
              <Card title={chart.title} extra={<Typography.Text type="secondary">{chart.subtitle}</Typography.Text>}>
                <div style={{ height: 220 }}>
                  <TrendAreaChart points={chart.points} valueFormatter={chart.valueFormatter} />
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

const RedisMonitorPage = () => (
  <ManagementPage title="Redis监控">
    <RedisMonitorContent />
  </ManagementPage>
);

export default RedisMonitorPage;
