import dayjs from 'dayjs';
import { Button, DatePicker, Form, Input, Space, Switch } from 'antd';
import type { FormProps } from 'antd';
import type { BrandingSettings } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

interface MaintenanceTabProps {
  formProps: FormProps<BrandingSettings>;
  saving: boolean;
  canUpdate: boolean;
  onSave: () => void;
}

export const MaintenanceTab = ({
  formProps,
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

      <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
        <Button type="primary" loading={saving} disabled={!canUpdate} onClick={onSave}>
          {t('common.save')}
        </Button>
      </div>
    </Space>
  );
};
