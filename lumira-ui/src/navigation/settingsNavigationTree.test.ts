import { describe, expect, it } from 'vitest';
import type { MenuNode } from '@/types/api';
import { buildSettingsSourceItems } from './settingsNavigationTree';

describe('settings navigation tree', () => {
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
});
