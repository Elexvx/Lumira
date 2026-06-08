import { formatMessage } from '@umijs/max';
import { Button, Result } from 'antd';
import { history } from '@umijs/max';

export default () => (
  <Result
    status="404"
    title="404"
    subTitle={formatMessage({ id: 'page.exception.notFound.subtitle', defaultMessage: '页面不存在，请返回首页继续操作。' })}
    extra={
      <Button type="primary" onClick={() => history.push('/dashboard/home')}>
        {formatMessage({ id: 'page.exception.notFound.backHome', defaultMessage: '返回首页' })}
      </Button>
    }
  />
);
