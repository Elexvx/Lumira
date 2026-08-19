import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

import { MaintenanceTab } from './MaintenanceTab';

vi.mock('@/hooks/useResponsive', () => ({
  useResponsive: () => ({ isMobile: false }),
}));

vi.mock('@/i18n/databaseMessage', () => ({
  databaseMessage: (key: string) => (key === 'common.save' ? '保存' : key),
}));

describe('MaintenanceTab', () => {
  it('renders a concise blue save action without the maintenance preview', () => {
    const markup = renderToStaticMarkup(
      <MaintenanceTab
        formProps={{
          initialValues: {
            maintenanceModeEnabled: true,
            maintenanceTitle: '系统维护中',
            maintenanceMessage: '服务正在升级优化，请稍后再试。',
            maintenanceEndAt: '',
          },
        }}
        saving={false}
        canUpdate
        onSave={() => undefined}
      />,
    );

    expect(markup).toContain('ant-btn-primary');
    expect(markup.replace(/\s/g, '')).toContain('>保存</span>');
    expect(markup).not.toContain('ant-btn-dangerous');
    expect(markup).not.toContain('ant-typography');
    expect(markup).not.toContain('anticon-clock-circle');
  });

  it('renders the role allowlist and keeps the administrator selected by the form value', () => {
    const markup = renderToStaticMarkup(
      <MaintenanceTab
        formProps={{
          initialValues: {
            maintenanceModeEnabled: true,
            maintenanceAllowedRoleIds: [1001],
            maintenanceTitle: '系统维护中',
            maintenanceMessage: '服务正在升级优化，请稍后再试。',
            maintenanceEndAt: '',
          },
        }}
        saving={false}
        canUpdate
        roleOptions={[
          { id: 1001, roleCode: 'ADMIN', roleName: '管理员', roleType: 'BUILT_IN' },
          { id: 3002, roleCode: 'REVIEWER', roleName: '评审员', roleType: 'CUSTOM' },
        ]}
        onSave={() => undefined}
      />,
    );

    expect(markup).toContain('ui.settings.personalization.maintenance.allowedLoginRoles');
    expect(markup).toContain('管理员');
    expect(markup).toContain('评审员');
    expect(markup).toContain('value="1001"');
  });

  it('disables saving when the role list cannot be loaded', () => {
    const markup = renderToStaticMarkup(
      <MaintenanceTab
        formProps={{ initialValues: { maintenanceModeEnabled: true, maintenanceAllowedRoleIds: [1001] } }}
        saving={false}
        canUpdate
        rolesLoadError
        onSave={() => undefined}
      />,
    );

    expect(markup).toContain('failedToLoadAllowedLoginRoles');
    expect(markup).toContain('disabled=""');
  });
});
