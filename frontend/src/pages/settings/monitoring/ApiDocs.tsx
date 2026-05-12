import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Collapse, Empty, Result, Space, Spin, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { tokenManager } from '@/auth/token';
import { AUTHORIZATION_HEADER } from '@/constants/http';
import { ManagementPage } from '@/features/management';

type OpenApiOperation = {
  summary?: string;
  description?: string;
  tags?: string[];
  operationId?: string;
};

type OpenApiDocument = {
  openapi?: string;
  info?: {
    title?: string;
    version?: string;
    description?: string;
  };
  paths?: Record<string, Record<string, OpenApiOperation>>;
};

type EndpointRecord = {
  key: string;
  method: string;
  path: string;
  summary: string;
  description?: string;
  operationId?: string;
};

const OPENAPI_URL = '/api-docs';
const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options', 'trace'];
const METHOD_COLORS: Record<string, string> = {
  GET: 'green',
  POST: 'blue',
  PUT: 'gold',
  PATCH: 'purple',
  DELETE: 'red',
};

const buildAuthorization = () => {
  const accessToken = tokenManager.getAccessToken();
  return accessToken ? `Bearer ${accessToken}` : '';
};

const toEndpointRecords = (document: OpenApiDocument | null): EndpointRecord[] => {
  if (!document?.paths) {
    return [];
  }

  return Object.entries(document.paths).flatMap(([path, operations]) =>
    Object.entries(operations)
      .filter(([method]) => HTTP_METHODS.includes(method.toLowerCase()))
      .map(([method, operation]) => {
        const normalizedMethod = method.toUpperCase();
        return {
          key: `${normalizedMethod} ${path}`,
          method: normalizedMethod,
          path,
          summary: operation.summary || operation.operationId || '未命名接口',
          description: operation.description,
          operationId: operation.operationId,
        };
      }),
  );
};

export default () => {
  const isLoggedIn = tokenManager.hasToken();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [openApiDocument, setOpenApiDocument] = useState<OpenApiDocument | null>(null);

  const endpointRecords = useMemo(() => toEndpointRecords(openApiDocument), [openApiDocument]);

  const loadOpenApiDocument = async () => {
    if (!isLoggedIn) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const authorization = buildAuthorization();
      const response = await fetch(OPENAPI_URL, {
        headers: authorization ? { [AUTHORIZATION_HEADER]: authorization } : undefined,
        credentials: 'include',
      });
      if (!response.ok) {
        throw new Error(`OpenAPI 文档加载失败：HTTP ${response.status}。请确认后端已部署并放行 /api-docs。`);
      }
      const payload = (await response.json()) as OpenApiDocument;
      setOpenApiDocument(payload);
    } catch (err) {
      const nextError = err instanceof Error ? err.message : 'OpenAPI 文档加载失败';
      setError(nextError);
      message.error(nextError);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadOpenApiDocument();
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
          <Button icon={<ReloadOutlined />} onClick={() => void loadOpenApiDocument()}>
            刷新页面
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {error ? (
          <Alert
            type="error"
            showIcon
            message="接口文档暂不可用"
            description={error}
          />
        ) : null}
        <Spin spinning={loading}>
          {endpointRecords.length ? (
            <Collapse
              items={endpointRecords.map((endpoint) => ({
                key: endpoint.key,
                label: (
                  <Space size={12} wrap>
                    <Tag color={METHOD_COLORS[endpoint.method] || 'default'}>{endpoint.method}</Tag>
                    <Typography.Text code>{endpoint.path}</Typography.Text>
                    <Typography.Text>{endpoint.summary}</Typography.Text>
                  </Space>
                ),
                children: (
                  <Space direction="vertical" size={8} style={{ width: '100%' }}>
                    {endpoint.operationId ? (
                      <Typography.Text type="secondary">操作标识：{endpoint.operationId}</Typography.Text>
                    ) : null}
                    <Typography.Paragraph style={{ marginBottom: 0 }}>
                      {endpoint.description || endpoint.summary}
                    </Typography.Paragraph>
                  </Space>
                ),
              }))}
            />
          ) : (
            <Card>
              <Empty description={loading ? '正在加载接口文档' : '暂无接口文档'} />
            </Card>
          )}
        </Spin>
      </Space>
    </ManagementPage>
  );
};
