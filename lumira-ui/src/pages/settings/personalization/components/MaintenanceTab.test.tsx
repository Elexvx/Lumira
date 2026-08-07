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
});
