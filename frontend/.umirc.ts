import { defineConfig } from '@umijs/max';
import { backendRoutes } from './src/routes/meta';
import { createThemePreferenceBootstrapScript } from './src/theme/settings';

export default defineConfig({
  antd: {},
  access: {},
  initialState: {
    loading: '@/loading',
  },
  model: {},
  request: {},
  layout: {},
  headScripts: [
    {
      content: createThemePreferenceBootstrapScript(),
    },
  ],
  npmClient: 'pnpm',
  routes: backendRoutes,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/api-docs': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/swagger-ui': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/v3/api-docs': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
});
