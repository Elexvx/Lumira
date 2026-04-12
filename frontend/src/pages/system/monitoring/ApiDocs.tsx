import { ReloadOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Result, Space } from 'antd';
import SwaggerUI from 'swagger-ui-react';
import 'swagger-ui-react/swagger-ui.css';
import { tokenManager } from '@/auth/token';
import { tenantContext } from '@/tenant/context';

const SWAGGER_SPEC_URL = '/api-docs';

type SwaggerRequest = {
  headers?: Record<string, string>;
  [key: string]: unknown;
};

const buildSwaggerHeaders = () => {
  const headers: Record<string, string> = {};
  const accessToken = tokenManager.getAccessToken();
  const tenantId = tenantContext.getTenantId();

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  if (tenantId) {
    headers['X-Tenant-Id'] = tenantId;
  }

  return headers;
};

const swaggerRequestInterceptor = (request: SwaggerRequest) => {
  request.headers = {
    ...(request.headers || {}),
    ...buildSwaggerHeaders(),
  };
  return request;
};

export default () => {
  const isLoggedIn = tokenManager.hasToken();

  if (!isLoggedIn) {
    return (
      <PageContainer title="接口文档" ghost>
        <Result
          status="403"
          title="请先登录"
          subTitle="接口文档只对已登录用户开放。"
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="接口文档"
      ghost
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
          <SwaggerUI
            url={SWAGGER_SPEC_URL}
            requestInterceptor={swaggerRequestInterceptor}
            docExpansion="none"
            deepLinking
            displayRequestDuration
            filter
            persistAuthorization
            tryItOutEnabled
          />
        </div>
      </Card>
    </PageContainer>
  );
};
