import { defineConfig } from '@umijs/max';
import { backendRoutes } from './src/routes/meta';
import { createLocalePreferenceBootstrapScript } from './src/i18n/locale';
import { createThemePreferenceBootstrapScript } from './src/theme/settings';

export default defineConfig({
  access: {},
  initialState: {
    loading: '@/loading',
  },
  model: {},
  request: {},
  layout: {},
  locale: {
    default: 'zh-CN',
    baseNavigator: true,
    useLocalStorage: true,
    antd: true,
    baseSeparator: '-',
  },
  utoopack: {},
  routePrefetch: {
    defaultPrefetch: 'intent',
  },
  esbuildMinifyIIFE: true,
  headScripts: [
    {
      content: createThemePreferenceBootstrapScript(),
    },
    {
      content: createLocalePreferenceBootstrapScript(),
    },
  ],
  npmClient: 'pnpm',
  routes: backendRoutes,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/ws': {
      target: 'ws://localhost:8080',
      changeOrigin: true,
      ws: true,
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
