import { useEffect, useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Row, Space, Statistic, Typography } from 'antd';
import { Area } from '@ant-design/charts';
import { buildTableScroll } from '@/features/table/proTable';
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
const valueStyle = { fontSize: 24, fontWeight: 700 };

const RedisMonitorPage = () => {
  const responsive = useResponsive();
  const query = useQuery({
    queryKey: ['redis-monitor'],
    queryFn: async () => monitorService.redis({ autoRedirectOnUnauthorized: false }),
  });
  const [samples, setSamples] = useState<TrendPoint[]>([]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      void query.refetch();
    }, 5000);
    return () => {
      window.clearInterval(timer);
    };
  }, [query.refetch]);

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
    setSamples((current) => [...current.slice(-(MAX_TREND_SAMPLES - 1)), nextPoint]);
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
    <PageContainer
      title="Redis监控"
      className="saas-management-page"
      ghost
      extra={
        <Space wrap>
          <Button onClick={async () => await query.refetch()}>立即刷新</Button>
        </Space>
      }
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card loading={query.isLoading && !redis} title="Redis信息">
          <Row gutter={[16, 16]}>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="Redis版本" value={redis?.overview.version || '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="运行模式" value={redis?.overview.mode || '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="端口" value={redis?.overview.port ?? '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="客户端数" value={redis?.overview.connectedClients ?? '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="运行时间(天)" value={redis?.overview.uptimeDays ?? '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="命中率" value={formatPercent(redis?.overview.hitRate)} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="QPS" value={redis?.overview.instantaneousOpsPerSec ?? '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="键数量" value={redis?.overview.keyCount ?? '-'} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="内存使用量" value={formatBytes(redis?.overview.memoryUsedBytes)} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="内存峰值" value={formatBytes(redis?.overview.memoryPeakBytes)} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="内存使用率" value={formatPercent(redis?.overview.memoryUsagePercent)} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="总连接数" value={redis?.overview.totalConnectionsReceived ?? '-'} valueStyle={valueStyle} />
            </Col>
          </Row>

          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="总命中" value={formatNumber(redis?.overview.hits)} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="总未命中" value={formatNumber(redis?.overview.misses)} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="总命令数" value={formatNumber(redis?.overview.totalCommandsProcessed)} valueStyle={valueStyle} />
            </Col>
            <Col xs={12} sm={6} xl={4}>
              <Statistic title="采样时间" value={formatDateTime(redis?.sampleTime)} valueStyle={{ ...valueStyle, fontSize: 18 }} />
            </Col>
          </Row>
        </Card>

        <Row gutter={[16, 16]}>
          {trendCharts.map((chart) => (
            <Col key={chart.title} xs={24} lg={12}>
              <Card title={chart.title} extra={<Typography.Text type="secondary">{chart.subtitle}</Typography.Text>}>
                <div style={{ height: 220 }}>
                  <Area
                    data={chart.points.length ? chart.points : [{ label: '-', value: 0 }]}
                    xField="label"
                    yField="value"
                    autoFit
                    height={220}
                    tooltip={{ showMarkers: true, shared: true }}
                    axis={{
                      x: {
                        label: {
                          autoHide: true,
                          autoRotate: true,
                        },
                      },
                      y: {
                        label: {
                          formatter: (value: string | number) => chart.valueFormatter(Number(value)),
                        },
                      },
                    }}
                    style={{
                      shape: 'smooth',
                      fill: '#4f7cff',
                      fillOpacity: 0.28,
                      stroke: '#4f7cff',
                      strokeWidth: 2.5,
                    }}
                    point={{
                      size: 3.5,
                      style: {
                        fill: '#fff',
                        stroke: '#4f7cff',
                        lineWidth: 2,
                      },
                    }}
                    legend={false}
                    padding={[8, 0, 20, 24]}
                  />
                </div>
              </Card>
            </Col>
          ))}
        </Row>

        <Card title="命令统计" loading={query.isLoading && !redis}>
          <div className="saas-table-wrap">
            <ProTable<RedisMonitorCommandStat>
              rowKey="command"
              search={false}
              options={false}
              pagination={false}
              columns={commandColumns}
              dataSource={redis?.commandStats || []}
              scroll={buildTableScroll(commandColumns, responsive.isMobile)}
              toolBarRender={false}
            />
          </div>
        </Card>

        <Card title="Key信息" loading={query.isLoading && !redis}>
          <div className="saas-table-wrap">
            <ProTable<RedisMonitorKeyspace>
              rowKey="database"
              search={false}
              options={false}
              pagination={false}
              columns={keyspaceColumns}
              dataSource={redis?.keyspaces || []}
              scroll={buildTableScroll(keyspaceColumns, responsive.isMobile)}
              toolBarRender={false}
            />
          </div>
        </Card>

        <Card title="连接客户端" loading={query.isLoading && !redis}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
            当前实例已连接的客户端会话列表，取自 Redis `CLIENT LIST`。
          </Typography.Paragraph>
          <div className="saas-table-wrap">
            <ProTable
              rowKey={(record: RedisMonitorClient) => `${record.addressPort || ''}-${record.name || ''}-${record.databaseId || ''}`}
              search={false}
              options={false}
              pagination={false}
              dataSource={redis?.clients || []}
              columns={clientColumns}
              scroll={buildTableScroll(clientColumns, responsive.isMobile)}
              toolBarRender={false}
            />
          </div>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default RedisMonitorPage;
