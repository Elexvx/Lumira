import { ReloadOutlined } from '@ant-design/icons';
import { Button, Card, Result, Space } from 'antd';
import { tokenManager } from '@/auth/token';
import { ManagementPage } from '@/features/management';

const SWAGGER_UI_CSS = 'https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui.css';
const SWAGGER_UI_BUNDLE = 'https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-bundle.js';
const SWAGGER_UI_PRESET = 'https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-standalone-preset.js';

const buildSwaggerHtml = () => {
  const tokenState = tokenManager.getTokenState();
  const authorization = tokenState?.accessToken ? `${tokenState.tokenType || 'Bearer'} ${tokenState.accessToken}` : '';

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
      const authorization = ${JSON.stringify(authorization)};
      window.onload = function () {
        window.ui = SwaggerUIBundle({
          url: '/api-docs',
          dom_id: '#swagger-ui',
          deepLinking: true,
          displayRequestDuration: true,
          presets: [
            SwaggerUIBundle.presets.apis,
            SwaggerUIStandalonePreset
          ],
          layout: 'StandaloneLayout',
          requestInterceptor: function (request) {
            if (authorization) {
              request.headers = request.headers || {};
              request.headers.Authorization = authorization;
            }
            return request;
          }
        });
      };
    </script>
  </body>
</html>`;
};

export default () => {
  const isLoggedIn = tokenManager.hasToken();

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
        <div style={{ minHeight: 'calc(100vh - 220px)', background: '#fff' }}>
          <iframe
            title="接口文档"
            srcDoc={buildSwaggerHtml()}
            sandbox="allow-scripts allow-forms allow-same-origin allow-popups"
            style={{
              width: '100%',
              minHeight: 'calc(100vh - 220px)',
              border: 0,
              display: 'block',
            }}
          />
        </div>
      </Card>
    </ManagementPage>
  );
};
