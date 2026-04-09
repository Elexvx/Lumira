import { LinkOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card } from 'antd';

const SWAGGER_URL = '/swagger-ui/index.html';

export default () => {
  const swaggerUrl = SWAGGER_URL;

  return (
    <PageContainer
      title="接口文档"
      ghost
      extra={<Button icon={<LinkOutlined />} onClick={() => window.open(swaggerUrl, '_blank', 'noopener,noreferrer')}>打开原始文档</Button>}
    >
      <Card bodyStyle={{ padding: 0, overflow: 'hidden', borderRadius: 16 }}>
        <div style={{ height: 'calc(100vh - 220px)', minHeight: 760, background: '#fff' }}>
          <iframe title="Swagger UI" src={swaggerUrl} style={{ width: '100%', height: '100%', border: 0 }} />
        </div>
      </Card>
    </PageContainer>
  );
};
