import { Button, Form, Input, Space, Switch, Typography } from 'antd';
import type { FormProps } from 'antd';
import type { BrandingSettings } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

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
      <Form {...formProps} disabled={!canUpdate}>
        <Form.Item
          name="maintenanceModeEnabled"
          label={t('ui.settings.personalization.maintenance.maintenanceMode')}
          valuePropName="checked"
        >
          <Switch
            checkedChildren={t('ui.settings.personalization.maintenance.on')}
            unCheckedChildren={t('ui.settings.personalization.maintenance.off')}
          />
        </Form.Item>
        <Form.Item
          name="maintenanceTitle"
          label={t('ui.settings.personalization.maintenance.pageTitle')}
          rules={[{ required: true, whitespace: true }]}
        >
          <Input maxLength={80} />
        </Form.Item>
        <Form.Item
          name="maintenanceMessage"
          label={t('ui.settings.personalization.maintenance.pageMessage')}
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
          {t('ui.settings.personalization.maintenance.saveMaintenanceSettings')}
        </Button>
      </div>
    </Space>
  );
};
