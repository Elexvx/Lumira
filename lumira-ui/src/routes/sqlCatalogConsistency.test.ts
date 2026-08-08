import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { backendRoutes, type BackendRouteRecord } from './meta';

const collectRouteImplementations = (
  routes: BackendRouteRecord[],
  result = new Map<string, Set<string>>(),
) => {
  for (const route of routes) {
    const implementations = result.get(route.path) || new Set<string>();
    if (route.component) {
      implementations.add(route.component);
    }
    if (route.redirect) {
      implementations.add(`redirect:${route.redirect}`);
    }
    result.set(route.path, implementations);
    if (route.routes) {
      collectRouteImplementations(route.routes, result);
    }
  }
  return result;
};

const extractEnabledSeedMenus = () => {
  const sql = readFileSync(fileURLToPath(new URL('../../../lumira-backend/sql/saas.sql', import.meta.url)), 'utf8');
  const rowPattern = /^\s*\([^,]+,[^,]+,\s*'([^']+)',\s*'[^']*',\s*'(CATALOG|MENU)',\s*'([^']+)',\s*'([^']+)',.*\s*'ENABLED',\s*0,\s*0,\s*0\),?$/;

  return sql.split(/\r?\n/).flatMap((line) => {
    const match = line.match(rowPattern);
    return match ? [{ menuCode: match[1], path: match[3], component: match[4] }] : [];
  });
};

describe('SQL menu and frontend route catalog consistency', () => {
  it('keeps every enabled seeded catalog and menu route executable by the frontend', () => {
    const routeImplementations = collectRouteImplementations(backendRoutes);
    const enabledMenus = extractEnabledSeedMenus();
    const mismatches = enabledMenus.flatMap((menu) => {
      const implementations = routeImplementations.get(menu.path);
      return implementations?.has(menu.component) ? [] : [{ ...menu, implementations: [...(implementations || [])] }];
    });

    expect(enabledMenus.length).toBeGreaterThan(40);
    expect(enabledMenus.map((menu) => menu.path)).toEqual(expect.arrayContaining([
      '/registration',
      '/certificates',
      '/competitions/expert-apply',
      '/workflows/tasks',
      '/workflows/config',
    ]));
    expect(mismatches).toEqual([]);
  });
});
