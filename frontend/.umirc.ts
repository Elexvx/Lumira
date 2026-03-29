import { defineConfig } from '@umijs/max';

export default defineConfig({
  antd: {},
  access: {},
  initialState: {},
  model: {},
  request: {},
  layout: false,
  npmClient: 'pnpm',
  routes: [
    {
      path: '/user',
      component: '@/layouts/UserLayout',
      routes: [
        { path: '/user/login', component: '@/pages/user/Login' },
      ],
    },
    {
      path: '/',
      component: '@/layouts/BasicLayout',
      routes: [
        { path: '/', redirect: '/dashboard/home' },
        { path: '/dashboard/home', component: '@/pages/dashboard/Home' },
        { path: '/system/plugins', component: '@/pages/system/Plugins' },
        { path: '/plugins/:pluginCode', component: '@/pages/plugins/RuntimeContainer' },
        { path: '/profile/center', component: '@/pages/profile/Center' },
        { path: '/403', component: '@/pages/exception/NoPermission' },
      ],
    },
    {
      path: '/blank',
      component: '@/layouts/BlankLayout',
      routes: [{ path: '/blank/workflow', component: '@/pages/exception/BlankFlow' }],
    },
    { path: '*', component: '@/pages/exception/NotFound' },
  ],
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
});
