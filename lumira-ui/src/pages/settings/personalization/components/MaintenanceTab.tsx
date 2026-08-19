import dayjs from 'dayjs';
import { Alert, Button, Checkbox, DatePicker, Form, Input, Space, Spin, Switch } from 'antd';
import type { FormProps } from 'antd';
import type { BrandingSettings, MaintenanceLoginRoleOption } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

interface MaintenanceTabProps {
  formProps: FormProps<BrandingSettings>;
  saving: boolean;
  canUpdate: boolean;
  onSave: () => void;
  roleOptions?: MaintenanceLoginRoleOption[];
  rolesLoading?: boolean;
  rolesLoadError?: boolean;
}

export const MaintenanceTab = ({
  formProps,
  saving,
  canUpdate,
  onSave,
  roleOptions = [],
  rolesLoading = false,
  rolesLoadError = false,
}: MaintenanceTabProps) => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const rolesUnavailable = rolesLoadError || (!rolesLoading && roleOptions.length === 0);
  const roleCheckboxOptions = roleOptions.map((role) => ({
    label: role.roleName || role.roleCode,
    value: role.id,
  }));

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
          name="maintenanceAllowedRoleIds"
          label={t('ui.settings.personalization.maintenance.allowedLoginRoles')}
          extra={t('ui.settings.personalization.maintenance.allowedLoginRolesHint')}
          rules={[
            ({ getFieldValue }) => ({
              validator: async (_, value: number[]) => {
                if (!getFieldValue('maintenanceModeEnabled')) {
                  return;
                }
                if (!Array.isArray(value) || value.length === 0) {
                  throw new Error(t('ui.settings.personalization.maintenance.allowedLoginRolesRequired'));
                }
              },
            }),
          ]}
        >
          {rolesLoading ? (
            <Spin size="small" />
          ) : (
            <Checkbox.Group options={roleCheckboxOptions} disabled={rolesUnavailable} />
          )}
        </Form.Item>
        {rolesLoadError ? (
          <Alert
            type="error"
            showIcon
            message={t('ui.settings.personalization.maintenance.failedToLoadAllowedLoginRoles')}
          />
        ) : null}
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
        <Button type="primary" loading={saving} disabled={!canUpdate || rolesLoading || rolesUnavailable} onClick={onSave}>
          {t('common.save')}
        </Button>
      </div>
    </Space>
  );
};
