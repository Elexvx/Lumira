import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Result, Space, Spin, theme } from 'antd';
import { useEffect, useState } from 'react';
import { tokenManager } from '@/auth/token';
import { AUTHORIZATION_HEADER } from '@/constants/http';
import { ManagementPage } from '@/features/management';

const API_DOCS_URL = '/api/v1/system/monitor/api-docs';
const SWAGGER_UI_VERSION = '5.17.14';
const SWAGGER_UI_CSS = `https://cdn.jsdelivr.net/npm/swagger-ui-dist@${SWAGGER_UI_VERSION}/swagger-ui.css`;
const SWAGGER_UI_BUNDLE = `https://cdn.jsdelivr.net/npm/swagger-ui-dist@${SWAGGER_UI_VERSION}/swagger-ui-bundle.js`;
const SWAGGER_UI_PRESET = `https://cdn.jsdelivr.net/npm/swagger-ui-dist@${SWAGGER_UI_VERSION}/swagger-ui-standalone-preset.js`;

const serializeForScript = (value: unknown) => {
  return JSON.stringify(value)
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
};

const buildSwaggerHtml = (apiSpec: unknown) => {
  const serializedSpec = serializeForScript(apiSpec);

  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <link rel="stylesheet" href="${SWAGGER_UI_CSS}" />
    <style>
      html, body, #swagger-ui { height: 100%; margin: 0; background: #fff; }
      .swagger-ui .topbar { display: none; }
      .swagger-ui .scheme-container { padding: 12px 0; box-shadow: none; }
    </style>
  </head>
  <body>
    <div id="swagger-ui"></div>
    <script src="${SWAGGER_UI_BUNDLE}"></script>
    <script src="${SWAGGER_UI_PRESET}"></script>
    <script>
      const apiSpec = ${serializedSpec};
      window.onload = function () {
        window.ui = SwaggerUIBundle({
          spec: apiSpec,
          dom_id: '#swagger-ui',
          deepLinking: true,
          displayRequestDuration: true,
          supportedSubmitMethods: [],
          presets: [
            SwaggerUIBundle.presets.apis,
            SwaggerUIStandalonePreset
          ],
          layout: 'StandaloneLayout'
        });
      };
    </script>
  </body>
</html>`;
};

export default () => {
  const { token } = theme.useToken();
  const isLoggedIn = tokenManager.hasToken();
  const [apiSpec, setApiSpec] = useState<unknown>(null);
  const [isLoading, setIsLoading] = useState(isLoggedIn);
  const [loadError, setLoadError] = useState('');

  useEffect(() => {
    if (!isLoggedIn) {
      setIsLoading(false);
      return;
    }

    const controller = new AbortController();
    const tokenState = tokenManager.getTokenState();
    const authorization = tokenState?.accessToken ? `${tokenState.tokenType || 'Bearer'} ${tokenState.accessToken}` : '';

    setIsLoading(true);
    setLoadError('');

    fetch(API_DOCS_URL, {
      headers: authorization ? { [AUTHORIZATION_HEADER]: authorization } : undefined,
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`接口文档加载失败：${response.status}`);
        }
        return response.json();
      })
      .then((data) => {
        setApiSpec(data);
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }
        setLoadError(error instanceof Error ? error.message : '接口文档加载失败');
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [isLoggedIn]);

  if (!isLoggedIn) {
    return (
      <ManagementPage title="接口文档">
        <Result
          status="403"
          title="请先登录"
          subTitle="接口文档只对已登录用户开放。"
        />
      </ManagementPage>
    );
  }

  return (
    <ManagementPage
      title="接口文档"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => window.location.reload()}>
            刷新页面
          </Button>
        </Space>
      }
    >
      <Card bodyStyle={{ padding: 0, overflow: 'hidden', borderRadius: 16 }}>
        <div style={{ minHeight: 'calc(100vh - 220px)', background: token.colorBgContainer }}>
          {isLoading ? (
            <div style={{ display: 'grid', minHeight: 'calc(100vh - 220px)', placeItems: 'center' }}>
              <Spin tip="正在加载接口文档..." />
            </div>
          ) : loadError ? (
            <div style={{ padding: token.paddingLG }}>
              <Alert message="接口文档加载失败" description={loadError} type="error" showIcon />
            </div>
          ) : (
            <iframe
              title="接口文档"
              srcDoc={buildSwaggerHtml(apiSpec)}
              sandbox="allow-scripts allow-forms allow-popups"
              style={{
                width: '100%',
                minHeight: 'calc(100vh - 220px)',
                border: 0,
                display: 'block',
              }}
            />
          )}
        </div>
      </Card>
    </ManagementPage>
  );
};
