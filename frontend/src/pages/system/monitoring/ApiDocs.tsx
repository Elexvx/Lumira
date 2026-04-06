import { useMemo } from 'react';
import { LinkOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Space, Tag, Typography } from 'antd';
import { API_PREFIX } from '@/constants/http';

const resolveSwaggerUrl = () => {
  try {
    const parsed = new URL(API_PREFIX);
    const basePath = parsed.pathname.replace(/\/api\/?$/, '');
    return `${parsed.origin}${basePath}/swagger-ui.html`;
  } catch {
    return '/swagger-ui.html';
  }
};

export default () => {
  const swaggerUrl = useMemo(() => resolveSwaggerUrl(), []);

  return (
    <PageContainer
      title="接口文档"
      ghost
      extra={
        <Space>
          <Tag color="green">Springdoc OpenAPI</Tag>
          <Button icon={<LinkOutlined />} onClick={() => window.open(swaggerUrl, '_blank', 'noopener,noreferrer')}>
            打开原始文档
          </Button>
        </Space>
      }
      content="当前页面直接嵌入后端生成的 Swagger UI，内容与 /swagger-ui.html 保持一致。"
    >
      <Card bodyStyle={{ padding: 0, overflow: 'hidden', borderRadius: 16 }}>
        <div style={{ padding: '16px 20px', borderBottom: '1px solid rgba(0,0,0,0.06)' }}>
          <Typography.Title level={4} style={{ marginBottom: 6 }}>
            系统接口文档
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            直接查看当前后端暴露的 OpenAPI 定义和在线调试能力，便于排查接口与联调。
          </Typography.Paragraph>
        </div>
        <div style={{ height: 'calc(100vh - 280px)', minHeight: 760, background: '#fff' }}>
          <iframe title="Swagger UI" src={swaggerUrl} style={{ width: '100%', height: '100%', border: 0 }} />
        </div>
      </Card>
    </PageContainer>
  );
};
