import { useState } from 'react';
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Result, Space, message } from 'antd';
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
  const [exporting, setExporting] = useState(false);
  const isLoggedIn = tokenManager.hasToken();

  const handleOpenRawSpec = async () => {
    const previewWindow = window.open('', '_blank', 'noopener,noreferrer');
    if (!previewWindow) {
      message.warning('请允许浏览器弹窗后再打开原始文档');
      return;
    }

    try {
      setExporting(true);
      previewWindow.document.write('<p style="font-family: sans-serif; padding: 16px;">正在加载原始文档...</p>');
      const response = await fetch(SWAGGER_SPEC_URL, {
        headers: buildSwaggerHeaders(),
      });

      if (!response.ok) {
        throw new Error(`请求 OpenAPI 失败: ${response.status}`);
      }

      const blob = await response.blob();
      const objectUrl = window.URL.createObjectURL(blob);
      previewWindow.location.href = objectUrl;
      window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
    } catch (error) {
      previewWindow.close();
      message.error(error instanceof Error ? error.message : '打开原始文档失败');
    } finally {
      setExporting(false);
    }
  };

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
          <Button icon={<DownloadOutlined />} loading={exporting} onClick={handleOpenRawSpec}>
            打开原始文档
          </Button>
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
