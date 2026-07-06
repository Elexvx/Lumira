import { formatMessage, history } from '@umijs/max';
import { Button } from 'antd';
import { getConfiguredDefaultHomePath } from '@/auth/defaultHomePath';
import ExceptionResult from './ExceptionResult';

export default () => (
  <ExceptionResult
    status="500"
    title="500"
    subTitle={formatMessage({ id: 'page.exception.serverError.subtitle', defaultMessage: '服务器发生异常，请稍后再试。' })}
    extra={
      <Button type="primary" onClick={() => history.push(getConfiguredDefaultHomePath())}>
        {formatMessage({ id: 'page.exception.serverError.backHome', defaultMessage: '返回首页' })}
      </Button>
    }
  />
);
