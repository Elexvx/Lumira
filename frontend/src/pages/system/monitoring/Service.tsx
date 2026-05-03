import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Descriptions, Row, Space, Statistic, Tag } from 'antd';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { useResponsive } from '@/hooks/useResponsive';
import { monitorService } from '@/services/system/monitor';
import type { ServiceMonitorSnapshot } from '@/types/api';
import { formatBytes, formatDateTime, formatDuration, formatNumber, formatPercent } from './shared';

const valueStyle = { fontSize: 24, fontWeight: 700 };
const stableNumericStyle = { fontVariantNumeric: 'tabular-nums' as const };

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

const ServiceMonitorPage = () => {
  const { isDesktop } = useResponsive();
  const query = useQuery({
    queryKey: ['service-monitor'],
    queryFn: async () => monitorService.service({ autoRedirectOnUnauthorized: false }),
  });
  const detailDescriptionsProps = useDetailDescriptionsProps({ column: { xs: 1, sm: 1, md: 2, xl: 2, xxl: 2 } });

  // Keep a stable ref to the latest refresh function so the interval effect
  // does not depend on query.refetch directly. query.refetch changes identity
  // on every render, which would cause the interval to be torn down and
  // recreated after every data update, producing a visible "twitching" effect.
  const refreshRef = useRef(query.refetch);
  useEffect(() => {
    refreshRef.current = query.refetch;
  }, [query.refetch]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      void refreshRef.current();
    }, 10000);
    return () => {
      window.clearInterval(timer);
    };
  }, []);

  const service = query.data;

  return (
    <PageContainer
      className="saas-service-monitor-page"
      title="服务监控"
      ghost
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card
              title="CPU"
              loading={query.isLoading && !service}
              extra={<Tag color="geekblue">核心数 {service?.cpu?.coreCount ?? '-'}</Tag>}
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
            <Descriptions.Item label="临时目录">
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
            <Descriptions.Item label="Java Home" span={isDesktop ? 2 : 1}>
              <BreakableValue value={service?.jvm?.javaHome} />
            </Descriptions.Item>
            <Descriptions.Item label="启动参数" span={isDesktop ? 2 : 1}>
              <ExpandableClampText value={service?.jvm?.inputArguments?.join(' ')} />
            </Descriptions.Item>
          </Descriptions>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default ServiceMonitorPage;
