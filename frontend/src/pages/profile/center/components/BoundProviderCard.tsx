import { Button, Card, Empty, List, Space, Tag, Typography } from 'antd';
import type { SecondFactorProviderStatus } from '@/types/api';

interface BoundProviderCardProps {
  canManageSecondFactor: boolean;
  loading: boolean;
  providers: SecondFactorProviderStatus[];
  bindingLoading: boolean;
  bindingSubmitting: boolean;
  emailBindingSubmitting: boolean;
  onBind: (provider: SecondFactorProviderStatus) => void;
  onUnbind: (provider: SecondFactorProviderStatus) => void;
}

export const BoundProviderCard = ({
  canManageSecondFactor,
  loading,
  providers,
  bindingLoading,
  bindingSubmitting,
  emailBindingSubmitting,
  onBind,
  onUnbind,
}: BoundProviderCardProps) => (
  <Card title="已绑定登录方式" loading={loading}>
    {providers.length ? (
      <List
        dataSource={providers}
        split={false}
        renderItem={(provider) => (
          <List.Item style={{ paddingInline: 0 }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                gap: 16,
                width: '100%',
                alignItems: 'flex-start',
              }}
            >
              <Space direction="vertical" size={4} style={{ minWidth: 0 }}>
                <Space wrap>
                  <Typography.Text strong>{provider.pluginName || provider.pluginCode}</Typography.Text>
                  <Tag color={provider.bound ? 'green' : provider.enabled ? 'gold' : 'default'}>{provider.bound ? '已绑定' : '未绑定'}</Tag>
                  <Tag>{provider.factorName || '登录方式'}</Tag>
                </Space>
                <Typography.Text type="secondary">{provider.maskedContact || provider.statusMessage || '暂无绑定标识'}</Typography.Text>
              </Space>
              {canManageSecondFactor ? (
                <Space wrap style={{ flexShrink: 0, justifyContent: 'flex-end' }}>
                  <Button
                    type="primary"
                    onClick={() => onBind(provider)}
                    disabled={bindingLoading || bindingSubmitting || emailBindingSubmitting}
                  >
                    {provider.bound ? '重新绑定' : '绑定'}
                  </Button>
                  {provider.bound ? (
                    <Button danger onClick={() => onUnbind(provider)}>
                      解绑
                    </Button>
                  ) : null}
                </Space>
              ) : null}
            </div>
          </List.Item>
        )}
      />
    ) : (
      <Empty description="当前租户暂无可绑定登录方式" />
    )}
  </Card>
);
