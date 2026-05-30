import { type ReactNode, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Row,
  Space,
  Statistic,
  Steps,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  ApiOutlined,
  CheckCircleOutlined,
  CloudSyncOutlined,
  ExclamationCircleOutlined,
  GithubOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { monitorService } from '@/services/system/monitor';
import type { PlatformUpdateStatus } from '@/types/api';
import { formatDateTime } from './shared';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';


const UNKNOWN_VALUE = 'unknown';

const shortCommit = (value?: string | null) => {
  if (!value || value.toLowerCase() === UNKNOWN_VALUE) {
    return '-';
  }
  return value.slice(0, 12);
};

const copyableCommit = (value?: string | null) => {
  const normalizedValue = shortCommit(value);
  if (normalizedValue === '-') {
    return normalizedValue;
  }
  return (
    <Typography.Text copyable={{ text: value || normalizedValue }} className="saas-update-mono">
      {normalizedValue}
    </Typography.Text>
  );
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

const VersionCard = ({
  title,
  commit,
  branch,
  version,
  timeLabel,
  time,
}: {
  title: string;
  commit?: string | null;
  branch?: string | null;
  version?: string | null;
  timeLabel: string;
  time?: string | null;
}) => (
  <Card title={title} className="saas-update-version-card">
    <Descriptions size="small" column={1}>
      <Descriptions.Item label="版本">{version || '-'}</Descriptions.Item>
      <Descriptions.Item label="提交">{copyableCommit(commit)}</Descriptions.Item>
      <Descriptions.Item label="分支">{branch || '-'}</Descriptions.Item>
      <Descriptions.Item label={timeLabel}>{formatDateTime(time)}</Descriptions.Item>
    </Descriptions>
  </Card>
);

export const PlatformUpdateContent = () => {
  const query = useQuery({
    queryKey: ['platform-update-status'],
    queryFn: async () => monitorService.updateStatus(API_OPTS.NO_REDIRECT),
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
      const result = await monitorService.checkUpdate(API_OPTS.NO_REDIRECT);
      await query.refetch();
      message.success(result.updateAvailable ? '发现新版本' : result.status === 'UNKNOWN' ? '版本信息待确认' : '当前已经是最新版本');
    } catch (error) {
      showErrorMessage(error, '检查更新失败');
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }} className="saas-update-center">
      <Card loading={query.isLoading && !updateStatus}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} lg={10}>
            <Space direction="vertical" size={10} style={{ width: '100%' }}>
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

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <VersionCard
            title="当前运行版本"
            version={updateStatus?.current?.version}
            commit={updateStatus?.current?.commitId}
            branch={updateStatus?.current?.branch}
            timeLabel="构建时间"
            time={updateStatus?.current?.buildTime}
          />
        </Col>
        <Col xs={24} lg={12}>
          <VersionCard
            title="更新源版本"
            version={updateStatus?.latest?.version}
            commit={updateStatus?.latest?.commitId}
            branch={updateStatus?.latest?.branch}
            timeLabel="提交时间"
            time={updateStatus?.latest?.releasedAt}
          />
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
        <Space direction="vertical" size={8}>
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
  );
};
