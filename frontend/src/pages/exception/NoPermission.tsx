import { history } from '@umijs/max';
import { Button, Result, Space } from 'antd';

export default () => (
  <Result
    status="403"
    title="403"
    subTitle="当前账号没有访问该页面的权限"
    extra={
      <Space>
        <Button onClick={() => history.back()}>返回上一页</Button>
        <Button type="primary" onClick={() => history.push('/dashboard/home')}>
          回到首页
        </Button>
      </Space>
    }
  />
);
