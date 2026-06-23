import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const proLayoutRequire = createRequire(require.resolve('@ant-design/pro-components/lib/layout/ProLayout.js'));
const { getMatchMenu, transformRoute } = proLayoutRequire('@umijs/route-utils');

const USER_CENTER_MENU_KEY = 'main:user-center';
const PERSONAL_CENTER_MENU_KEY = 'main:personal-center';

const { menuData } = transformRoute(
  [
    {
      key: USER_CENTER_MENU_KEY,
      name: 'User center',
      children: [
        {
          path: '/user-center/users',
          name: 'User management',
        },
      ],
    },
    {
      key: PERSONAL_CENTER_MENU_KEY,
      path: '/user-center/personal-center',
      name: 'Personal center',
      children: [
        {
          path: '/user-center/personal-center/profile',
          name: 'Profile',
        },
        {
          path: '/user-center/personal-center/files',
          name: 'My files',
        },
      ],
    },
  ],
  false,
  undefined,
  true,
);

const collectMatchedKeys = (pathname) =>
  getMatchMenu(pathname, menuData, true).map((item) => item.key || item.path);

const profileKeys = collectMatchedKeys('/user-center/personal-center/profile');
assert.ok(profileKeys.includes(PERSONAL_CENTER_MENU_KEY), 'personal profile should expand the personal center group');
assert.ok(!profileKeys.includes(USER_CENTER_MENU_KEY), 'personal profile must not expand the user center group');

const userKeys = collectMatchedKeys('/user-center/users');
assert.ok(userKeys.includes(USER_CENTER_MENU_KEY), 'user management should still expand the user center group');
assert.ok(!userKeys.includes(PERSONAL_CENTER_MENU_KEY), 'user management must not expand the personal center group');

console.log('user center menu isolation smoke passed');
