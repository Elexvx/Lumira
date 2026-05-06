import assert from 'node:assert/strict';
import type { PermissionTreeRecord } from '../src/types/api';
import { realPageRouteMetaMap, realPageRoutePaths } from '../src/routes/meta';
import {
  buildPermissionTreeData,
  collectActionPermissionPageMap,
  collectExpandableKeys,
  collectPermissionKeyToPageKeyMap,
  collectSelectablePages,
  normalizePermissionTree,
  type NormalizedPermissionTreeRecord,
} from '../src/pages/system/rolesPermissionTree';

const sampleTree: PermissionTreeRecord[] = [
  {
    nodeType: 'CATALOG',
    pageKey: 'system',
    pageName: '系统总览',
    selectable: false,
    children: [
      {
        nodeType: 'PAGE',
        pageKey: 'system-security',
        pageName: '安全设置',
        routePath: '/settings/security',
        permissionKey: 'system:config:view',
        selectable: true,
      },
      {
        nodeType: 'PAGE',
        pageKey: 'legacy-configs',
        pageName: '参数配置-历史',
        routePath: '/system/configs',
        permissionKey: 'system:config:view',
        selectable: true,
      },
      {
        nodeType: 'PAGE',
        pageKey: 'missing-route',
        pageName: '缺失路由',
        routePath: '/system/not-exists',
        permissionKey: 'system:missing:view',
        selectable: true,
      },
      {
        nodeType: 'ALIAS',
        pageKey: 'legacy-users',
        pageName: '用户管理',
        routePath: '/system/users',
        permissionKey: 'system:user:view',
        selectable: false,
        children: [
          {
            nodeType: 'PAGE',
            pageKey: 'alias-child',
            pageName: '在线用户',
            routePath: '/user-center/online-users',
            permissionKey: 'system:online-user:view',
            selectable: true,
          },
        ],
      },
    ],
  },
];

const normalized: NormalizedPermissionTreeRecord[] = normalizePermissionTree(sampleTree);
const root = normalized[0];

assert.equal(normalized.length, 1, 'catalog root should be preserved');
assert.equal(root?.nodeType, 'CATALOG', 'root should remain a catalog node');
assert.equal(root?.children?.length, 4, 'alias nodes should flatten into children and duplicates should be removed');

const childNames = root?.children?.map((item) => item.pageName) || [];
assert.ok(childNames.includes('安全设置'), 'valid page should remain');
assert.ok(childNames.includes('缺失路由'), 'mismatched page should remain visible');
assert.ok(childNames.includes('在线用户'), 'alias child should be promoted');

const matchedPage = root?.children?.find((item) => item.pageName === '安全设置');
assert.equal(matchedPage?.nodeType, 'PAGE');
assert.equal(matchedPage?.selectable, true);
assert.equal(matchedPage?.routeMatched, true);

const mismatchedPage = root?.children?.find((item) => item.pageName === '缺失路由');
assert.equal(mismatchedPage?.selectable, false, 'mismatched route should not be selectable');
assert.equal(mismatchedPage?.routeMatched, false, 'mismatched route should be flagged');

const selectablePages = collectSelectablePages(normalized);
assert.equal(selectablePages.length, 2, 'only valid PAGE nodes should be selectable');
const treeData = buildPermissionTreeData(normalized);
assert.equal(treeData[0]?.disableCheckbox, false, 'catalog nodes with children should not be forced disabled');
assert.deepEqual(collectExpandableKeys(normalized), ['system'], 'catalog root should still be expandable');
assert.equal(collectPermissionKeyToPageKeyMap(normalized).get('system:config:view')?.length, 1, 'duplicate route path should be deduplicated');
assert.equal(collectActionPermissionPageMap(normalized).size, 0, 'no action permissions are present in the smoke tree');
assert.ok(realPageRoutePaths.has('/settings/security'), 'system security route should be registered');
assert.equal(realPageRouteMetaMap.get('/settings/security')?.name, 'nav.system.security', 'system security route should be named correctly');
assert.ok(!realPageRoutePaths.has('/system/management'), 'legacy management route should not be registered');

console.log('permission-tree-normalize-smoke: ok');
