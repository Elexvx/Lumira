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
  utoopack: {
    output: {
      clean: true,
    },
    optimization: {
      splitChunks: {
        js: {
          minChunkSize: 20_000,
          maxMergeChunkSize: 180_000,
          maxChunkCountPerGroup: 120,
        },
        css: {
          minChunkSize: 10_000,
          maxMergeChunkSize: 120_000,
          maxChunkCountPerGroup: 40,
        },
      },
    },
  },
  routePrefetch: {
    defaultPrefetch: 'intent',
  },
  esbuildMinifyIIFE: true,
  extraBabelPlugins: [
    [
      'import',
      {
        libraryName: '@ant-design/icons',
        libraryDirectory: 'es/icons',
        camel2DashComponentName: false,
      },
      'ant-design-icons',
    ],
  ],
  headScripts: [
    {
      content: createThemePreferenceBootstrapScript(),
    },
    {
      content: createLocalePreferenceBootstrapScript(),
    },
  ],
  npmClient: 'pnpm',
  hash: true,
  define: {
    'process.env.UMI_APP_API_BASE_URL': process.env.UMI_APP_API_BASE_URL || '',
    'process.env.UMI_APP_API_PREFIX': process.env.UMI_APP_API_PREFIX || '',
    'process.env.UMI_APP_REQUEST_TIMEOUT': process.env.UMI_APP_REQUEST_TIMEOUT || '',
  },
  routes: backendRoutes,
  proxy: {
    '/api': {
      target: process.env.UMI_DEV_API_TARGET || 'http://localhost:8080',
      changeOrigin: true,
    },
    '/ws': {
      target: process.env.UMI_DEV_WS_TARGET || 'ws://localhost:8080',
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
