import { formatMessage } from '@/i18n/formatMessage';
import { Button } from 'antd';
import { history } from '@umijs/max';
import { getConfiguredDefaultHomePath } from '@/auth/defaultHomePath';
import ExceptionResult from './ExceptionResult';

export default () => (
  <ExceptionResult
    status="404"
    title="404"
    subTitle={formatMessage({ id: 'page.exception.notFound.subtitle', defaultMessage: '页面不存在，请返回首页继续操作。' })}
    extra={
      <Button type="primary" onClick={() => history.push(getConfiguredDefaultHomePath())}>
        {formatMessage({ id: 'page.exception.notFound.backHome', defaultMessage: '返回首页' })}
      </Button>
    }
  />
);
