import { formatMessage } from '@umijs/max';
import { history } from '@umijs/max';
import { Button, Result, Space } from 'antd';
import './Exception.css';

export default () => (
  <div className="saas-exception-page-shell">
    <Result
      className="saas-exception-result"
      status="403"
      title="403"
      subTitle={formatMessage({ id: 'page.exception.noPermission.subtitle', defaultMessage: '当前账号没有访问该页面的权限' })}
      extra={
        <Space>
          <Button onClick={() => history.back()}>{formatMessage({ id: 'page.exception.noPermission.back', defaultMessage: '返回上一页' })}</Button>
          <Button type="primary" onClick={() => history.push('/dashboard/home')}>
            {formatMessage({ id: 'page.exception.noPermission.home', defaultMessage: '回到首页' })}
          </Button>
        </Space>
      }
    />
  </div>
);
