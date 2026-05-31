import { Button, Card, Empty, List, Space, Tag, Typography } from 'antd';
import type { ReactNode } from 'react';
import type { SecondFactorProviderStatus } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';

interface BoundProviderCardProps {
  title?: ReactNode;
  emptyDescription?: ReactNode;
  canManageSecondFactor: boolean;
  loading: boolean;
  providers: SecondFactorProviderStatus[];
  bindingLoading: boolean;
  bindingSubmitting: boolean;
  onBind: (provider: SecondFactorProviderStatus) => void;
  onUnbind: (provider: SecondFactorProviderStatus) => void;
}

export const BoundProviderCard = ({
  canManageSecondFactor,
  loading,
  providers,
  bindingLoading,
  bindingSubmitting,
  onBind,
  onUnbind,
  title = '二次验证方式',
  emptyDescription = '当前暂无可绑定二次验证方式',
}: BoundProviderCardProps) => {
  const responsive = useResponsive();
  const compactLayout = responsive.isMobile;

  return (
    <Card title={title} loading={loading}>
      {providers.length ? (
        <List
          dataSource={providers}
          split={false}
          renderItem={(provider) => (
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
                    <Typography.Text strong>{provider.factorName || provider.factorCode}</Typography.Text>
                    {provider.systemEnabled === false ? <Tag color="red">系统已关闭</Tag> : null}
                    <Tag color={provider.bound ? 'green' : provider.enabled ? 'gold' : 'default'}>{provider.bound ? '已绑定' : '未绑定'}</Tag>
                    <Tag>{provider.factorCode}</Tag>
                  </Space>
                  <Typography.Text type="secondary">{provider.maskedContact || provider.statusMessage || '暂无绑定标识'}</Typography.Text>
                </Space>
                <Space
                  wrap
                  style={{
                    flexShrink: 0,
                    justifyContent: compactLayout ? 'flex-start' : 'flex-end',
                    width: compactLayout ? '100%' : 'auto',
                  }}
                >
                  <Button
                    type="primary"
                    block={compactLayout}
                    onClick={() => onBind(provider)}
                    disabled={!canManageSecondFactor || bindingLoading || bindingSubmitting || provider.systemEnabled === false}
                  >
                    {provider.systemEnabled === false ? '系统未启用' : provider.bound ? '重新绑定' : '绑定'}
                  </Button>
                  {provider.bound ? (
                    <Button danger block={compactLayout} onClick={() => onUnbind(provider)} disabled={!canManageSecondFactor || provider.systemEnabled === false}>
                      解绑
                    </Button>
                  ) : null}
                </Space>
              </div>
            </List.Item>
          )}
        />
      ) : (
        <Empty description={emptyDescription} />
      )}
    </Card>
  );
};
