import { defineConfig } from '@umijs/max';
import fs from 'node:fs';
import path from 'node:path';
import { backendRoutes } from './src/routes/meta';
import { createLocalePreferenceBootstrapScript } from './src/i18n/locale';
import { createThemePreferenceBootstrapScript } from './src/theme/settings';

const pnpmModulesPath = path.resolve(process.cwd(), 'node_modules/.pnpm');
const devApiTarget = process.env.UMI_DEV_API_TARGET || 'http://127.0.0.1:8080';
const devWebSocketTarget = process.env.UMI_DEV_WS_TARGET || 'ws://127.0.0.1:8080';
const eventEmitterPackageDirectory = fs
  .readdirSync(pnpmModulesPath)
  .find((directory) => directory.startsWith('event-emitter@'));
if (!eventEmitterPackageDirectory) {
  throw new Error('Unable to resolve the event-emitter package used by the Umi locale plugin.');
}
const eventEmitterPackagePath = path
  .resolve(pnpmModulesPath, eventEmitterPackageDirectory, 'node_modules/event-emitter')
  .replace(/\\/g, '/');

export default defineConfig({
  alias: {
    '@umijs/max': path.resolve(process.cwd(), 'src/.umi/exports.ts'),
    [eventEmitterPackagePath]: path.resolve(process.cwd(), 'src/shims/eventEmitter.ts'),
  },
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
  moment2dayjs: {
    preset: 'antd',
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
    'process.env.UMI_APP_LOCAL_NATIVE_MODE': process.env.UMI_APP_LOCAL_NATIVE_MODE || 'false',
    'process.env.UMI_APP_REQUEST_TIMEOUT': process.env.UMI_APP_REQUEST_TIMEOUT || '',
    'process.env.UMI_APP_FRONTEND_VERSION': process.env.UMI_APP_FRONTEND_VERSION || '',
    'process.env.UMI_APP_BUILD_TIME': process.env.UMI_APP_BUILD_TIME || '',
    'process.env.UMI_APP_GIT_COMMIT': process.env.UMI_APP_GIT_COMMIT || '',
    'process.env.UMI_APP_GIT_BRANCH': process.env.UMI_APP_GIT_BRANCH || '',
  },
  routes: backendRoutes,
  proxy: {
    '/api': {
      target: devApiTarget,
      changeOrigin: true,
    },
    '/ws': {
      target: devWebSocketTarget,
      changeOrigin: true,
      ws: true,
    },
    '/api-docs': {
      target: devApiTarget,
      changeOrigin: true,
    },
    '/swagger-ui': {
      target: devApiTarget,
      changeOrigin: true,
    },
    '/v3/api-docs': {
      target: devApiTarget,
      changeOrigin: true,
    },
  },
});
