import { useEffect, useRef } from 'react';
import { useRequest } from '@umijs/max';
import { PageContainer } from '@ant-design/pro-components';
import { Card, Col, Descriptions, Row, Space, Statistic, Tag, Typography } from 'antd';
import { useResponsive } from '@/hooks/useResponsive';
import { monitorService } from '@/services/system/monitor';
import type { ServiceMonitorSnapshot } from '@/types/api';
import { formatBytes, formatDateTime, formatDuration, formatNumber, formatPercent } from './shared';

const valueStyle = { fontSize: 24, fontWeight: 700 };

const ServiceMonitorPage = () => {
  const { isDesktop } = useResponsive();
  const query = useRequest(async () => ({ data: await monitorService.service({ autoRedirectOnUnauthorized: false }) }) as { data: ServiceMonitorSnapshot });

  // Keep a stable ref to the latest refresh function so the interval effect
  // does not depend on query.refresh directly. query.refresh changes identity
  // on every render, which would cause the interval to be torn down and
  // recreated after every data update, producing a visible "twitching" effect.
  const refreshRef = useRef(query.refresh);
  useEffect(() => {
    refreshRef.current = query.refresh;
  });

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
      title="服务监控"
      ghost
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card
              title="CPU"
              loading={query.loading && !service}
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
            <Card title="内存" loading={query.loading && !service} style={{ height: '100%' }} bodyStyle={{ minHeight: isDesktop ? 108 : 0 }}>
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

        <Card title="服务器信息" loading={query.loading && !service}>
          <Descriptions bordered column={{ xs: 1, sm: 1, md: 2, xl: isDesktop ? 3 : 2, xxl: 4 }} size="small">
            <Descriptions.Item label="服务器名称">{service?.server?.serverName || '-'}</Descriptions.Item>
            <Descriptions.Item label="服务器IP">{service?.server?.serverIp || '-'}</Descriptions.Item>
            <Descriptions.Item label="操作系统">{service?.server?.osName || '-'}</Descriptions.Item>
            <Descriptions.Item label="系统架构">{service?.server?.osArch || '-'}</Descriptions.Item>
            <Descriptions.Item label="系统版本">{service?.server?.osVersion || '-'}</Descriptions.Item>
            <Descriptions.Item label="项目路径">{service?.server?.projectPath || '-'}</Descriptions.Item>
            <Descriptions.Item label="安装路径">{service?.server?.installPath || '-'}</Descriptions.Item>
            <Descriptions.Item label="用户目录">{service?.server?.userHome || '-'}</Descriptions.Item>
            <Descriptions.Item label="临时目录">{service?.server?.tempDir || '-'}</Descriptions.Item>
          </Descriptions>
        </Card>

        <Card title="Java虚拟机信息" loading={query.loading && !service}>
          <Descriptions bordered column={{ xs: 1, sm: 1, md: 2, xl: isDesktop ? 3 : 2, xxl: 4 }} size="small">
            <Descriptions.Item label="Java名称">{service?.jvm?.vmName || '-'}</Descriptions.Item>
            <Descriptions.Item label="Java版本">{service?.jvm?.javaVersion || '-'}</Descriptions.Item>
            <Descriptions.Item label="虚拟机版本">{service?.jvm?.vmVersion || '-'}</Descriptions.Item>
            <Descriptions.Item label="虚拟机厂商">{service?.jvm?.vmVendor || '-'}</Descriptions.Item>
            <Descriptions.Item label="启动时间">{formatDateTime(service?.jvm?.startTime)}</Descriptions.Item>
            <Descriptions.Item label="运行时长">{formatDuration(service?.jvm?.uptimeSeconds)}</Descriptions.Item>
            <Descriptions.Item label="进程ID">{formatNumber(service?.jvm?.pid)}</Descriptions.Item>
            <Descriptions.Item label="线程数">{formatNumber(service?.jvm?.threadCount)}</Descriptions.Item>
            <Descriptions.Item label="守护线程">{formatNumber(service?.jvm?.daemonThreadCount)}</Descriptions.Item>
            <Descriptions.Item label="峰值线程数">{formatNumber(service?.jvm?.peakThreadCount)}</Descriptions.Item>
            <Descriptions.Item label="Java Home">{service?.jvm?.javaHome || '-'}</Descriptions.Item>
            <Descriptions.Item label="启动参数">
              <Typography.Paragraph style={{ marginBottom: 0 }} ellipsis={{ rows: 2, expandable: true, symbol: '展开' }}>
                {service?.jvm?.inputArguments?.join(' ') || '-'}
              </Typography.Paragraph>
            </Descriptions.Item>
          </Descriptions>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default ServiceMonitorPage;
