import { defineConfig } from '@umijs/max';
import { backendRoutes } from './src/routes/meta';
import { createThemePreferenceBootstrapScript } from './src/theme/settings';

export default defineConfig({
  antd: {},
  access: {},
  initialState: {},
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
  },
});
