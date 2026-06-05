import { Alert, Button, Card, Empty, InputNumber, List, Space, Spin, Switch, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { request } from '@/services/common/request';
import type { ProfileFieldSetting } from '@/types/api';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { useResponsive } from '@/hooks/useResponsive';

const ProfileFieldManagementPage = () => {
  const actionPermission = useActionPermission();
  const { isMobile } = useResponsive();
  const canUpdate = actionPermission.can('system:config:update');
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);
  const microGap = resolveResponsiveValue(APP_SPACING.microGap, isMobile);
  const [items, setItems] = useState<ProfileFieldSetting[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const enabledWeight = useMemo(
    () => items.filter((item) => item.visible).reduce((total, item) => total + (item.weight || 0), 0),
    [items],
  );

  const loadItems = async () => {
    setLoading(true);
    try {
      const result = await request<ProfileFieldSetting[]>('/v1/system/profile-field-settings', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      });
      setItems(result);
    } catch (error) {
      showErrorMessage(error, '加载字段配置失败');
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

  const handleWeightChange = (fieldKey: string, weight?: number | null) => {
    if (weight == null) {
      return;
    }
    setItems((prev) => prev.map((item) => (item.fieldKey === fieldKey ? { ...item, weight } : item)));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const result = await request<ProfileFieldSetting[]>('/v1/system/profile-field-settings', {
        method: 'PUT',
        data: {
          items: items.map((item) => ({
            fieldKey: item.fieldKey,
            visible: Boolean(item.visible),
            weight: item.weight ?? 1,
          })),
        },
        ...API_OPTS.NO_REDIRECT,
      });
      setItems(result);
    } catch (error) {
      showErrorMessage(error, '保存字段配置失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ManagementPage ghost title="字段管理" style={{ height: '100%', minHeight: 0 }} content={null}>
      <ManagementPageBody>
        <Card
          title="个人中心字段评分配置"
          extra={
            <Button onClick={() => void loadItems()} disabled={loading || saving}>
              刷新
            </Button>
          }
        >
          <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
            <Alert
              type={enabledWeight === 100 ? 'success' : 'info'}
              showIcon
              message={`当前启用字段权重总和：${enabledWeight}`}
              description="个人中心会按当前启用字段的权重比例折算为 100 分，字段开关和评分权重会一起保存。"
            />
            {loading ? (
              <div style={{ display: 'grid', placeItems: 'center', minHeight: 'var(--saas-spacing-240)' }}>
                <Spin />
              </div>
            ) : items.length ? (
              <List
                dataSource={items}
                renderItem={(item) => (
                  <List.Item>
                    <Space direction="vertical" size={tagWrapGap} style={{ width: '100%' }}>
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'flex-start',
                          gap: sectionGap,
                          width: '100%',
                        }}
                      >
                        <Space direction="vertical" size={microGap} style={{ minWidth: 0 }}>
                          <Space wrap size={tagWrapGap}>
                            <Typography.Text strong>{item.fieldLabel}</Typography.Text>
                            {item.groupLabel ? <Tag color="blue">{item.groupLabel}</Tag> : null}
                          </Space>
                          <Typography.Text type="secondary">{item.fieldDescription || '控制该字段在个人中心中的展示与填写'}</Typography.Text>
                        </Space>
                        <Space wrap align="center">
                          <Typography.Text type="secondary">权重</Typography.Text>
                          <InputNumber
                            min={1}
                            precision={0}
                            controls={false}
                            disabled={!canUpdate}
                            value={item.weight ?? 1}
                            style={{ width: 'var(--saas-spacing-100)' }}
                            onChange={(value) => handleWeightChange(item.fieldKey, value)}
                          />
                          <Switch
                            checked={Boolean(item.visible)}
                            disabled={!canUpdate}
                            checkedChildren="开启"
                            unCheckedChildren="关闭"
                            onChange={(checked) => handleToggle(item.fieldKey, checked)}
                          />
                        </Space>
                      </div>
                    </Space>
                  </List.Item>
                )}
              />
            ) : (
              <Empty description="暂无可配置字段" />
            )}

            {items.length ? (
              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <Button type="primary" loading={saving} onClick={() => void handleSave()} disabled={loading || !canUpdate}>
                  保存设置
                </Button>
              </div>
            ) : null}
          </Space>
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default ProfileFieldManagementPage;
