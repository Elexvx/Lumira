import { describe, expect, it } from 'vitest';
import type { MenuNode } from '@/types/api';
import { buildSettingsSourceItems } from './settingsNavigationTree';

describe('settings navigation tree', () => {
  it('places workflow configuration under system settings', () => {
    const menuTree = [
      {
        menuCode: 'settings.root',
        name: '系统设置',
        path: '/settings',
        children: [
          {
            menuCode: 'workflow.config',
            name: '工作流配置',
            path: '/settings/workflows',
            icon: 'BranchesOutlined',
            permissionKey: 'workflow:config',
            sortNo: 9,
          },
        ],
      },
    ] as MenuNode[];

    const items = buildSettingsSourceItems(menuTree);

    expect(items).toContainEqual(expect.objectContaining({
      path: '/settings/workflows',
      name: 'nav.settings.workflows',
      access: 'canVisitWorkflowConfig',
    }));
  });

  it('injects enabled plugin settings pages into the settings sidebar', () => {
    const menuTree = [
      {
        menuCode: 'settings.root',
        name: '系统设置',
        path: '/settings',
        children: [
          {
            menuCode: 'plugin.sensitive-words',
            name: '敏感词管理',
            path: '/settings/sensitive-words',
            icon: 'SafetyOutlined',
            permissionKey: 'plugin:sensitive-words:view',
            sortNo: 6,
          },
        ],
      },
    ] as MenuNode[];

    const items = buildSettingsSourceItems(menuTree);

    expect(items).toContainEqual(expect.objectContaining({
      path: '/settings/sensitive-words',
      name: 'nav.plugins.sensitiveWords',
      access: 'canVisitSensitiveWordsPlugin',
    }));
  });

  it('injects the enabled alerting plugin with its guarded route metadata', () => {
    const menuTree = [{
      menuCode: 'settings.root',
      name: '系统设置',
      path: '/settings',
      children: [{
        menuCode: 'plugin.alerting',
        name: '告警中心',
        path: '/settings/alerting',
        icon: 'AlertOutlined',
        permissionKey: 'plugin:alerting:view',
        sortNo: 7,
      }],
    }] as MenuNode[];

    expect(buildSettingsSourceItems(menuTree)).toContainEqual(expect.objectContaining({
      path: '/settings/alerting',
      name: 'nav.plugins.alerting',
      access: 'canVisitAlertingPlugin',
    }));
  });
});
