import { formatMessage } from '@umijs/max';
import { history } from '@umijs/max';
import { Button, Space } from 'antd';
import { getConfiguredDefaultHomePath } from '@/auth/defaultHomePath';
import ExceptionResult from './ExceptionResult';

export default () => (
  <ExceptionResult
    status="403"
    title="403"
    subTitle={formatMessage({ id: 'page.exception.noPermission.subtitle', defaultMessage: '当前账号没有访问该页面的权限' })}
    extra={
      <Space>
        <Button onClick={() => history.back()}>{formatMessage({ id: 'page.exception.noPermission.back', defaultMessage: '返回上一页' })}</Button>
        <Button type="primary" onClick={() => history.push(getConfiguredDefaultHomePath())}>
          {formatMessage({ id: 'page.exception.noPermission.home', defaultMessage: '回到首页' })}
        </Button>
      </Space>
    }
  />
);
