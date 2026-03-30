import { defineConfig } from '@umijs/max';
import { backendRoutes } from './src/routes/meta';

export default defineConfig({
  antd: {},
  access: {},
  initialState: {},
  model: {},
  request: {},
  layout: {},
  npmClient: 'pnpm',
  routes: backendRoutes,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
});
