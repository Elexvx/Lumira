import { useQuery } from '@tanstack/react-query';
import { Button, Card, Descriptions, Result, Space, Tag, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { monitorService } from '@/services/system/monitor';
import { formatDateTime } from './shared';

const shortCommit = (value?: string | null) => (value && value !== 'unknown' ? value.slice(0, 12) : '-');

export const PlatformUpdateContent = () => {
  const query = useQuery({
    queryKey: ['platform-update-status'],
    queryFn: async () => monitorService.updateStatus({ autoRedirectOnUnauthorized: false }),
  });

  const updateStatus = query.data;
  const latestUrl = updateStatus?.latest?.url;

  const handleCheck = async () => {
    try {
      const result = await monitorService.checkUpdate({ autoRedirectOnUnauthorized: false });
      await query.refetch();
      message.success(result.updateAvailable ? '发现新版本' : '当前已经是最新版本');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '检查更新失败');
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card loading={query.isLoading && !updateStatus}>
        <Result
          status={updateStatus?.updateAvailable ? 'warning' : updateStatus?.errorMessage ? 'error' : 'success'}
          title={updateStatus?.updateAvailable ? '发现新版本' : updateStatus?.errorMessage ? '更新源暂时不可用' : '当前版本已同步'}
          subTitle={
            updateStatus?.errorMessage ||
            '系统只检查官方 GitHub 最新提交并显示提醒，不会自动拉代码、执行命令或重启服务。'
          }
          extra={[
            <Button key="check" type="primary" icon={<ReloadOutlined />} loading={query.isFetching} onClick={handleCheck}>
              检查更新
            </Button>,
            latestUrl ? (
              <Button key="github" href={latestUrl} target="_blank" rel="noreferrer">
                查看 GitHub
              </Button>
            ) : null,
          ]}
        />
      </Card>

      <Card title="版本对比" loading={query.isLoading && !updateStatus}>
        <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
          <Descriptions.Item label="当前版本">{updateStatus?.current?.version || '-'}</Descriptions.Item>
          <Descriptions.Item label="最新版本">{updateStatus?.latest?.version || '-'}</Descriptions.Item>
          <Descriptions.Item label="当前提交">{shortCommit(updateStatus?.current?.commitId)}</Descriptions.Item>
          <Descriptions.Item label="最新提交">{shortCommit(updateStatus?.latest?.commitId)}</Descriptions.Item>
          <Descriptions.Item label="当前分支">{updateStatus?.current?.branch || '-'}</Descriptions.Item>
          <Descriptions.Item label="最新分支">{updateStatus?.latest?.branch || '-'}</Descriptions.Item>
          <Descriptions.Item label="当前构建时间">{formatDateTime(updateStatus?.current?.buildTime)}</Descriptions.Item>
          <Descriptions.Item label="最新提交时间">{formatDateTime(updateStatus?.latest?.releasedAt)}</Descriptions.Item>
          <Descriptions.Item label="检查时间">{formatDateTime(updateStatus?.checkedAt)}</Descriptions.Item>
          <Descriptions.Item label="状态">
            {updateStatus?.updateAvailable ? <Tag color="orange">有更新</Tag> : <Tag color="green">已同步</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="更新源" span={2}>
            <Typography.Text copyable ellipsis style={{ maxWidth: '100%' }}>
              {updateStatus?.sourceUrl || '-'}
            </Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label="最新说明" span={2}>
            {updateStatus?.latest?.title || '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="安全策略">
        <Typography.Paragraph style={{ marginBottom: 0 }}>
          GitHub 推送后，系统通过官方只读接口发现新提交并提醒管理员。这个页面不会自动部署，也不会接受前端传入命令；后续一键更新会在备份、二次确认、健康检查和审计日志都具备后再开放。
        </Typography.Paragraph>
      </Card>
    </Space>
  );
};
