import { Card, Descriptions, Typography } from 'antd';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';

export default () => {
  const { initialState } = useInitialStateModel();
  return (
    <Card>
      <Typography.Title level={4}>首页占位</Typography.Title>
      <Typography.Paragraph>当前阶段已打通认证与租户切换主链路。</Typography.Paragraph>
      <Descriptions column={1} size="small">
        <Descriptions.Item label="当前用户">{initialState?.currentUser?.username || '-'}</Descriptions.Item>
        <Descriptions.Item label="当前租户">{initialState?.currentTenant?.tenantName || '未选择'}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
};
