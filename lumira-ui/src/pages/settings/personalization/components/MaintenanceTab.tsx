import { ClockCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { Button, DatePicker, Form, Input, Space, Switch, Typography } from 'antd';
import type { FormProps } from 'antd';
import type { BrandingSettings } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';
import { formatMaintenanceCountdown, useMaintenanceCountdown } from '@/maintenance/maintenanceCountdown';
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
  const previewCountdown = useMaintenanceCountdown(preview.maintenanceEndAt);

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
          extra={t('ui.settings.personalization.maintenance.pageTitleHint')}
          rules={[{ required: true, whitespace: true }]}
        >
          <Input maxLength={80} />
        </Form.Item>
        <Form.Item
          name="maintenanceMessage"
          label={t('ui.settings.personalization.maintenance.pageMessage')}
          extra={t('ui.settings.personalization.maintenance.pageMessageHint')}
          rules={[{ required: true, whitespace: true }]}
        >
          <Input.TextArea rows={4} maxLength={300} showCount />
        </Form.Item>
        <Form.Item
          name="maintenanceEndAt"
          label={t('ui.settings.personalization.maintenance.maintenanceEndAt')}
          extra={t('ui.settings.personalization.maintenance.maintenanceEndAtHint')}
          getValueProps={(value) => ({ value: value ? dayjs(value) : null })}
          getValueFromEvent={(value) => value?.toISOString() ?? ''}
        >
          <DatePicker
            showTime
            format="YYYY-MM-DD HH:mm"
            style={{ width: '100%' }}
            disabledDate={(current) => Boolean(current && current.isBefore(dayjs(), 'minute'))}
          />
        </Form.Item>
      </Form>

      <div
        style={{
          padding: isMobile ? 24 : 36,
          color: 'var(--ant-color-text)',
          textAlign: 'center',
        }}
      >
        <Typography.Title level={3} style={{ margin: 0, color: 'var(--ant-color-text)' }}>
          {preview.maintenanceTitle}
        </Typography.Title>
        <Typography.Paragraph style={{ maxWidth: 520, margin: '14px auto 0', color: 'var(--ant-color-text-secondary)' }}>
          {preview.maintenanceMessage}
        </Typography.Paragraph>
        {previewCountdown !== null ? (
          <Typography.Text
            type="secondary"
            style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontVariantNumeric: 'tabular-nums' }}
          >
            <ClockCircleOutlined />
            {formatMaintenanceCountdown(previewCountdown)}
          </Typography.Text>
        ) : null}
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
        <Button type="primary" danger={Boolean(preview.maintenanceModeEnabled)} loading={saving} disabled={!canUpdate} onClick={onSave}>
          {t('ui.settings.personalization.maintenance.saveMaintenanceSettings')}
        </Button>
      </div>
    </Space>
  );
};
