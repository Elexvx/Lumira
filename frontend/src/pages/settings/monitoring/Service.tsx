import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Descriptions, Row, Space, Statistic, Table, Tag } from 'antd';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { ManagementPage } from '@/features/management';
import { useResponsive } from '@/hooks/useResponsive';
import { messageService } from '@/services/message';
import { monitorService } from '@/services/system/monitor';
import type { MessageWebSocketRuntime, ServiceMonitorSnapshot } from '@/types/api';
import { formatBytes, formatDateTime, formatDuration, formatNumber, formatPercent } from './shared';

const valueStyle = { fontSize: 24, fontWeight: 700 };
const stableNumericStyle = { fontVariantNumeric: 'tabular-nums' as const };
const REALTIME_REFRESH_INTERVAL_MS = 1000;

const BreakableValue = ({ value }: { value?: string | null }) => (
  <span className="saas-monitor-break-value">{value || '-'}</span>
);

const NumericValue = ({ value }: { value: string }) => (
  <span className="saas-monitor-numeric-value" style={stableNumericStyle}>
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

export const ServiceMonitorContent = () => {
  const { isDesktop, isMobile } = useResponsive();
  const query = useQuery({
    queryKey: ['service-monitor'],
    queryFn: async () => monitorService.service({ autoRedirectOnUnauthorized: false }),
  });
  const webSocketQuery = useQuery<MessageWebSocketRuntime>({
    queryKey: ['message-websocket-runtime'],
    queryFn: async () => messageService.webSocketRuntime({ autoRedirectOnUnauthorized: false }),
  });
  const detailDescriptionsProps = useDetailDescriptionsProps({ column: isMobile ? 1 : 2 });
  const fullRowSpan = isMobile ? 1 : 2;

  // Keep a stable ref to the latest refresh function so the interval effect
  // does not depend on query.refetch directly. query.refetch changes identity
  // on every render, which would cause the interval to be torn down and
  // recreated after every data update, producing a visible "twitching" effect.
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

  const service = query.data;

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }} className="saas-service-monitor-page">
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card
              title="CPU"
              loading={query.isLoading && !service}
              style={{ height: '100%' }}
              bodyStyle={{ minHeight: isDesktop ? 108 : 0 }}
            >
              <Row gutter={[16, 16]}>
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
              <Row gutter={[16, 16]}>
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
          <Row gutter={[16, 16]}>
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
          <Table
            rowKey="tenantId"
            size="small"
            pagination={false}
            dataSource={webSocketQuery.data?.tenants || []}
            style={{ marginTop: 16 }}
            columns={[
              { title: '租户', dataIndex: 'tenantId', width: 180 },
              { title: '连接数', dataIndex: 'connectionCount', width: 120 },
            ]}
          />
        </Card>

        <Card title="基础服务健康" loading={query.isLoading && !service}>
          <Table
            rowKey="serviceName"
            size="small"
            pagination={false}
            dataSource={service?.services || []}
            columns={[
              { title: '服务', dataIndex: 'serviceName' },
              { title: '地址', dataIndex: 'baseUrl', ellipsis: true },
              {
                title: '状态',
                dataIndex: 'status',
                width: 100,
                render: (value) => <Tag color={value === 'UP' ? 'green' : 'red'}>{value || 'DOWN'}</Tag>,
              },
              { title: '响应', dataIndex: 'responseTimeMs', width: 100, render: (value) => (value == null ? '-' : `${value} ms`) },
              { title: '检测时间', dataIndex: 'checkedAt', width: 180, render: (value) => formatDateTime(value) },
              { title: '说明', dataIndex: 'errorMessage', ellipsis: true, render: (value) => value || '-' },
            ]}
          />
        </Card>

        <Card title="接口文档入口" loading={query.isLoading && !service}>
          <Table
            rowKey="serviceName"
            size="small"
            pagination={false}
            dataSource={service?.apiDocs || []}
            columns={[
              { title: '服务', dataIndex: 'serviceName', width: 180 },
              { title: 'OpenAPI 地址', dataIndex: 'url', ellipsis: true },
              {
                title: '服务状态',
                dataIndex: 'status',
                width: 120,
                render: (value) => <Tag color={value === 'UP' ? 'green' : 'red'}>{value || 'DOWN'}</Tag>,
              },
            ]}
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
              <NumericValue value={formatDateTime(service?.jvm?.startTime)} />
            </Descriptions.Item>
            <Descriptions.Item label="运行时长">
              <NumericValue value={formatDuration(service?.jvm?.uptimeSeconds)} />
            </Descriptions.Item>
            <Descriptions.Item label="进程ID">
              <NumericValue value={formatNumber(service?.jvm?.pid)} />
            </Descriptions.Item>
            <Descriptions.Item label="线程数">
              <NumericValue value={formatNumber(service?.jvm?.threadCount)} />
            </Descriptions.Item>
            <Descriptions.Item label="守护线程">
              <NumericValue value={formatNumber(service?.jvm?.daemonThreadCount)} />
            </Descriptions.Item>
            <Descriptions.Item label="峰值线程数">
              <NumericValue value={formatNumber(service?.jvm?.peakThreadCount)} />
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

const ServiceMonitorPage = () => (
  <ManagementPage
    className="saas-service-monitor-page"
    title="服务监控"
  >
    <ServiceMonitorContent />
  </ManagementPage>
);

export default ServiceMonitorPage;
