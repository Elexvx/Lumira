import { KeyOutlined } from '@ant-design/icons';
import { Button, Card, Divider, Empty, List, Popconfirm, Space, Tag, Typography } from 'antd';
import { useResponsive } from '@/hooks/useResponsive';
import type { PasskeyCredentialRecord } from '@/types/api';

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
  passkeys: PasskeyCredentialRecord[];
  onBindPasskey: () => void;
  onRenamePasskey: (id: number, currentLabel?: string) => void;
  onDeletePasskey: (id: number) => void;
}

export const LoginMethodCard = ({
  loading = false,
  canManage,
  items,
  passkeys,
  onBindPasskey,
  onRenamePasskey,
  onDeletePasskey,
}: LoginMethodCardProps) => {
  const responsive = useResponsive();
  const compactLayout = responsive.isMobile;

  return (
    <Card
      title="登录方式绑定"
      loading={loading}
      extra={
        <Button icon={<KeyOutlined />} onClick={onBindPasskey} disabled={!canManage}>
          新增通行密钥
        </Button>
      }
    >
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
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
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前暂无可绑定登录方式" />
        )}

        <Divider orientation="left" plain style={{ margin: 0 }}>
          通行密钥
        </Divider>
        {passkeys.length ? (
          <List
            dataSource={passkeys}
            split={false}
            renderItem={(item) => (
              <List.Item
                style={{ paddingInline: 0 }}
                actions={[
                  <Button key="rename" type="link" onClick={() => onRenamePasskey(item.id, item.label)} disabled={!canManage}>
                    重命名
                  </Button>,
                  <Popconfirm key="delete" title="确认删除该通行密钥？" onConfirm={() => onDeletePasskey(item.id)}>
                    <Button type="link" danger disabled={!canManage}>
                      删除
                    </Button>
                  </Popconfirm>,
                ]}
              >
                <List.Item.Meta
                  avatar={<KeyOutlined />}
                  title={item.label || '通行密钥'}
                  description={`创建时间: ${item.createdAt || '-'} · 最后使用: ${item.lastUsedAt || '-'}`}
                />
              </List.Item>
            )}
          />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有绑定通行密钥" />
        )}
      </Space>
    </Card>
  );
};
