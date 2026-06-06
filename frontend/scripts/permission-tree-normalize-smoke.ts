import assert from 'node:assert/strict';
import type { PermissionTreeRecord } from '../src/types/api';
import { realPageRouteMetaMap, realPageRoutePaths } from '../src/routes/meta';
import { normalizePermissionTree, type NormalizedPermissionTreeRecord } from '../src/pages/system/rolesPermissionTree/normalize';

const walkPermissionTree = (nodes: NormalizedPermissionTreeRecord[], visit: (node: NormalizedPermissionTreeRecord) => void) => {
  nodes.forEach((node) => {
    visit(node);
    if (node.children?.length) {
      walkPermissionTree(node.children, visit);
    }
  });
};

const collectSelectablePages = (nodes: NormalizedPermissionTreeRecord[], result: NormalizedPermissionTreeRecord[] = []) => {
  walkPermissionTree(nodes, (node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      result.push(node);
    }
  });
  return result;
};

const collectPermissionKeyToPageKeyMap = (nodes: NormalizedPermissionTreeRecord[], result = new Map<string, string[]>()) => {
  walkPermissionTree(nodes, (node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      const pageKeys = result.get(node.permissionKey) ?? [];
      if (!pageKeys.includes(node.pageKey)) {
        pageKeys.push(node.pageKey);
      }
      result.set(node.permissionKey, pageKeys);
    }
  });
  return result;
};

const collectExpandableKeys = (nodes: NormalizedPermissionTreeRecord[], result: string[] = []) => {
  walkPermissionTree(nodes, (node) => {
    if (node.children?.length) {
      result.push(node.pageKey);
    }
  });
  return result;
};

const collectActionPermissionPageMap = (nodes: NormalizedPermissionTreeRecord[], result = new Map<string, string>()) => {
  walkPermissionTree(nodes, (node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      node.actionPermissions?.forEach((action) => {
        if (action.permissionKey) {
          result.set(action.permissionKey, node.permissionKey as string);
        }
      });
    }
  });
  return result;
};

const buildPermissionTreeData = (nodes: NormalizedPermissionTreeRecord[]): any[] =>
  nodes.map((node) => ({
    ...node,
    key: node.pageKey,
    checkable: node.nodeType === 'PAGE',
    disableCheckbox: node.nodeType === 'PAGE' ? !node.selectable : !node.children?.length,
    selectable: node.selectable,
    title: node.pageName,
    children: node.children?.length ? buildPermissionTreeData(node.children) : undefined,
  }));

const sampleTree: PermissionTreeRecord[] = [
  {
    nodeType: 'CATALOG',
    pageKey: 'system',
    pageName: '系统总览',
    selectable: false,
    children: [
      {
        nodeType: 'PAGE',
        pageKey: 'user-center-users',
        pageName: '用户管理',
        routePath: '/user-center/users',
        permissionKey: 'system:user:view',
        selectable: true,
      },
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
        pageKey: 'legacy-users',
        pageName: '用户管理-重复',
        routePath: '/user-center/users',
        permissionKey: 'system:user:view',
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
      {
        nodeType: 'CATALOG',
        pageKey: 'ai',
        pageName: 'AI',
        routePath: '/ai',
        selectable: false,
        children: [
          {
            nodeType: 'PAGE',
            pageKey: 'ai-assistant',
            pageName: 'AI 助手',
            routePath: '/ai/assistant/',
            permissionKey: 'ai:chat:send',
            selectable: true,
          },
          {
            nodeType: 'PAGE',
            pageKey: 'ai-knowledge',
            pageName: '知识库',
            routePath: '/ai/knowledge?from=menu',
            permissionKey: 'ai:knowledge:view',
            selectable: true,
          },
        ],
      },
      {
        nodeType: 'CATALOG',
        pageKey: 'personal-center',
        pageName: '个人中心',
        routePath: '/user-center/personal-center',
        permissionKey: 'profile:view',
        selectable: false,
        children: [
          {
            nodeType: 'PAGE',
            pageKey: 'personal-profile',
            pageName: '个人资料',
            routePath: '/user-center/personal-center/profile',
            permissionKey: 'profile:view',
            selectable: true,
          },
          {
            nodeType: 'PAGE',
            pageKey: 'personal-files',
            pageName: '我的文件',
            routePath: '/user-center/files',
            permissionKey: 'system:file:view',
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
assert.equal(root?.children?.length, 4, 'alias nodes should flatten into valid children and invalid routes should be removed');

const childNames = root?.children?.map((item) => item.pageName) || [];
assert.ok(childNames.includes('用户管理'), 'valid assignable page should remain');
assert.ok(childNames.includes('在线用户'), 'alias child should be promoted');
assert.ok(childNames.includes('AI'), 'AI catalog should remain');
assert.ok(childNames.includes('个人中心'), 'personal center catalog should remain');

const matchedPage = root?.children?.find((item) => item.pageName === '用户管理');
assert.equal(matchedPage?.nodeType, 'PAGE');
assert.equal(matchedPage?.selectable, true);
assert.equal(matchedPage?.routeMatched, true);

const mismatchedPage = root?.children?.find((item) => item.pageName === '缺失路由');
assert.equal(mismatchedPage, undefined, 'mismatched route should be removed from selectable permission tree');

const selectablePages = collectSelectablePages(normalized);
assert.equal(selectablePages.length, 6, 'only valid PAGE nodes should be selectable');
const treeData = buildPermissionTreeData(normalized);
assert.equal(treeData[0]?.disableCheckbox, false, 'catalog nodes with children should not be forced disabled');
assert.deepEqual(collectExpandableKeys(normalized), ['system', 'ai', 'personal-center'], 'catalog roots with valid children should still be expandable');
assert.equal(collectPermissionKeyToPageKeyMap(normalized).get('system:user:view')?.length, 1, 'duplicate route path should be deduplicated');
assert.equal(collectActionPermissionPageMap(normalized).size, 0, 'no action permissions are present in the smoke tree');
assert.ok(realPageRoutePaths.has('/user-center/users'), 'user management route should be registered');
assert.ok(realPageRoutePaths.has('/user-center/personal-center/profile'), 'personal profile route should be registered');
assert.ok(realPageRoutePaths.has('/user-center/files'), 'personal files route should be registered');
assert.ok(realPageRoutePaths.has('/ai/assistant'), 'AI assistant route should be registered');
assert.ok(realPageRoutePaths.has('/ai/knowledge'), 'AI knowledge route should be registered');
assert.ok(realPageRoutePaths.has('/settings/security'), 'system security route should be registered');
assert.equal(collectPermissionKeyToPageKeyMap(normalized).get('ai:chat:send')?.length, 1, 'AI assistant should be assignable once');
assert.equal(collectPermissionKeyToPageKeyMap(normalized).get('ai:knowledge:view')?.length, 1, 'AI knowledge should be assignable once');
assert.equal(collectPermissionKeyToPageKeyMap(normalized).get('profile:view')?.length, 1, 'personal profile should be assignable once');
assert.equal(collectPermissionKeyToPageKeyMap(normalized).get('system:file:view')?.length, 1, 'personal files should be assignable once');
assert.equal(realPageRouteMetaMap.get('/settings/security')?.name, 'nav.system.security', 'system security route should be named correctly');
assert.ok(!realPageRoutePaths.has('/system/management'), 'legacy management route should not be registered');

console.log('permission-tree-normalize-smoke: ok');
