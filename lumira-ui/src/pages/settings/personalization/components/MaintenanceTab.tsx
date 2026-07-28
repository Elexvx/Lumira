import { Alert, Button, Form, Input, Space, Switch, Typography } from 'antd';
import type { FormProps } from 'antd';
import type { BrandingSettings } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

interface MaintenanceTabProps {
  formProps: FormProps<BrandingSettings>;
  preview: BrandingSettings;
  saving: boolean;
  canUpdate: boolean;
  onSave: () => void;
}

export const MaintenanceTab = ({
  formProps,
  preview,
  saving,
  canUpdate,
  onSave,
}: MaintenanceTabProps) => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Alert
        type="warning"
        showIcon
        message={t('开启后，全站将显示维护页面', 'When enabled, the maintenance page replaces the entire site')}
        description={t(
          '保存后立即生效。维护页面右下角保留一个低可见度管理员入口；只有具备系统配置权限的账号登录后才能返回此处关闭维护模式。',
          'The change takes effect immediately. A subtle admin entry remains in the lower-right corner; only an account with system configuration permission can return here and disable maintenance mode.',
        )}
      />

      <Form {...formProps} disabled={!canUpdate}>
        <Form.Item
          name="maintenanceModeEnabled"
          label={t('维护模式', 'Maintenance mode')}
          valuePropName="checked"
        >
          <Switch
            checkedChildren={t('开启', 'On')}
            unCheckedChildren={t('关闭', 'Off')}
          />
        </Form.Item>
        <Form.Item
          name="maintenanceTitle"
          label={t('页面标题', 'Page title')}
          rules={[{ required: true, whitespace: true }]}
        >
          <Input maxLength={80} />
        </Form.Item>
        <Form.Item
          name="maintenanceMessage"
          label={t('页面说明', 'Page message')}
          rules={[{ required: true, whitespace: true }]}
        >
          <Input.TextArea rows={4} maxLength={300} showCount />
        </Form.Item>
      </Form>

      <div
        style={{
          padding: isMobile ? 24 : 36,
          borderRadius: 'var(--saas-card-radius)',
          color: '#edf3ff',
          textAlign: 'center',
          background: 'linear-gradient(145deg, #0c1424 0%, #101c32 55%, #0b1322 100%)',
        }}
      >
        <Typography.Title level={3} style={{ margin: 0, color: '#f6f8ff' }}>
          {preview.maintenanceTitle}
        </Typography.Title>
        <Typography.Paragraph style={{ maxWidth: 520, margin: '14px auto 0', color: 'rgb(213 225 247 / 68%)' }}>
          {preview.maintenanceMessage}
        </Typography.Paragraph>
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
        <Button type="primary" danger={Boolean(preview.maintenanceModeEnabled)} loading={saving} disabled={!canUpdate} onClick={onSave}>
          {t('保存维护模式设置', 'Save maintenance settings')}
        </Button>
      </div>
    </Space>
  );
};
