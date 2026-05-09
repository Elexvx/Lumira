import { ReloadOutlined } from '@ant-design/icons';
import { Button, Card, Result, Space } from 'antd';
import { tokenManager } from '@/auth/token';
import { ManagementPage } from '@/features/management';

const SWAGGER_UI_URL = '/swagger-ui/index.html?url=/api-docs';

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
            src={SWAGGER_UI_URL}
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
