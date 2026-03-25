import { defineConfig } from '@umijs/max';

export default defineConfig({
  antd: {},
  access: {},
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
        { path: '/system/management', component: '@/pages/system/Management' },
        { path: '/tenant/overview', component: '@/pages/tenant/Overview' },
        { path: '/iam/overview', component: '@/pages/iam/Overview' },
        { path: '/audit/overview', component: '@/pages/audit/Overview' },
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
