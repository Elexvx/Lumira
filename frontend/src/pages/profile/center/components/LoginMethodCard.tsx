import { Button, Card, Empty, List, Space, Tag, Typography } from 'antd';
import { useResponsive } from '@/hooks/useResponsive';

export interface LoginMethodItem {
  key: string;
  title: string;
  statusLabel?: string;
  statusColor?: string;
  value?: string | null;
  methodLabel?: string;
  methodColor?: string;
  actionLabel: string;
  actionLoading?: boolean;
  disabled?: boolean;
  onAction: () => void;
}

interface LoginMethodCardProps {
  loading?: boolean;
  canManage: boolean;
  items: LoginMethodItem[];
}

export const LoginMethodCard = ({ loading = false, canManage, items }: LoginMethodCardProps) => {
  const responsive = useResponsive();
  const compactLayout = responsive.isMobile;

  return (
    <Card title="登录方式" loading={loading}>
      {items.length ? (
        <List
          dataSource={items}
          split={false}
          renderItem={(item) => (
            <List.Item style={{ paddingInline: 0 }}>
              <div
                style={{
                  display: 'flex',
                  flexDirection: compactLayout ? 'column' : 'row',
                  justifyContent: 'space-between',
                  gap: compactLayout ? 12 : 16,
                  width: '100%',
                  alignItems: compactLayout ? 'stretch' : 'flex-start',
                }}
              >
                <Space direction="vertical" size={4} style={{ minWidth: 0, width: '100%' }}>
                  <Space wrap>
                    <Typography.Text strong>{item.title}</Typography.Text>
                    <Tag color={item.statusColor || (item.value ? 'green' : 'default')}>{item.statusLabel || (item.value ? '已绑定' : '未绑定')}</Tag>
                    {item.methodLabel ? <Tag color={item.methodColor}>{item.methodLabel}</Tag> : null}
                  </Space>
                  <Typography.Text type="secondary">{item.value || '暂无绑定信息'}</Typography.Text>
                </Space>
                <Space
                  wrap
                  style={{
                    flexShrink: 0,
                    justifyContent: compactLayout ? 'flex-start' : 'flex-end',
                    width: compactLayout ? '100%' : 'auto',
                  }}
                >
                  <Button type="primary" block={compactLayout} onClick={item.onAction} disabled={!canManage || item.disabled} loading={item.actionLoading}>
                    {item.actionLabel}
                  </Button>
                </Space>
              </div>
            </List.Item>
          )}
        />
      ) : (
        <Empty description="当前暂无可绑定登录方式" />
      )}
    </Card>
  );
};
