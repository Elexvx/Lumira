import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Empty, List, Space, Spin, Switch, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { systemService } from '@/services/system';
import type { ProfileFieldSetting } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';

const ProfileFieldManagementPage = () => {
  const { canAccess } = usePermission();
  const canUpdate = canAccess('system:config:update');
  const [items, setItems] = useState<ProfileFieldSetting[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const loadItems = async () => {
    setLoading(true);
    try {
      const result = await systemService.profileFieldSettings({ autoRedirectOnUnauthorized: false });
      setItems(result);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadItems();
  }, []);

  const handleToggle = (fieldKey: string, visible: boolean) => {
    setItems((prev) => prev.map((item) => (item.fieldKey === fieldKey ? { ...item, visible } : item)));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const result = await systemService.updateProfileFieldSettings(
        {
          items: items.map((item) => ({
            fieldKey: item.fieldKey,
            visible: Boolean(item.visible),
          })),
        },
        { autoRedirectOnUnauthorized: false },
      );
      setItems(result);
      message.success('字段展示设置已保存');
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageContainer className="saas-management-page" ghost title="字段管理" style={{ height: '100%', minHeight: 0 }} content={null}>
      <div className="saas-management-page-body">
        <Card
          title="个人中心字段开关"
          extra={
            <Space>
              <Button onClick={() => void loadItems()} disabled={loading || saving}>
                刷新
              </Button>
              {canUpdate ? (
                <Button type="primary" loading={saving} onClick={() => void handleSave()} disabled={loading}>
                  保存设置
                </Button>
              ) : null}
            </Space>
          }
        >
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {loading ? (
              <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
                <Spin />
              </div>
            ) : items.length ? (
              <List
                dataSource={items}
                renderItem={(item) => (
                  <List.Item
                    actions={[
                      <Switch
                        key={item.fieldKey}
                        checked={Boolean(item.visible)}
                        disabled={!canUpdate}
                        checkedChildren="开启"
                        unCheckedChildren="关闭"
                        onChange={(checked) => handleToggle(item.fieldKey, checked)}
                      />,
                    ]}
                  >
                    <List.Item.Meta
                      title={<Typography.Text strong>{item.fieldLabel}</Typography.Text>}
                      description={item.fieldDescription || '控制该字段在个人中心中的展示与填写'}
                    />
                  </List.Item>
                )}
              />
            ) : (
              <Empty description="暂无可配置字段" />
            )}
          </Space>
        </Card>
      </div>
    </PageContainer>
  );
};

export default ProfileFieldManagementPage;
