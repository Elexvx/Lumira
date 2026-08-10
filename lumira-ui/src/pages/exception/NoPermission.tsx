import { formatMessage } from '@/i18n/formatMessage';
import { history } from '@umijs/max';
import { Button, Space } from 'antd';
import { useEffect } from 'react';
import { resolvePermissionRecoveryTarget } from '@/auth/loginRedirect';
import { getStoredCurrentUser } from '@/auth/sessionState';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import ExceptionResult from './ExceptionResult';

export default () => {
  const { initialState } = useInitialStateModel();
  const currentUser = initialState?.currentUser || getStoredCurrentUser();
  const recoveryTarget = resolvePermissionRecoveryTarget(currentUser, initialState?.menuTree);

  // Permission changes and role switches can land on /403 while the new
  // bootstrap snapshot is still being applied. Once an authenticated,
  // trusted snapshot is available, recover to its first accessible route so
  // users do not remain stranded on a stale 403 page.
  useEffect(() => {
    if (
      history.location.pathname !== '/403'
      || !currentUser
      || recoveryTarget === '/403'
      || recoveryTarget === '/user/login'
    ) {
      return;
    }
    history.replace(recoveryTarget);
  }, [currentUser, recoveryTarget]);

  return (
    <ExceptionResult
      status="403"
      title="403"
      subTitle={formatMessage({ id: 'page.exception.noPermission.subtitle', defaultMessage: '当前账号没有访问该页面的权限' })}
      extra={
        <Space>
          <Button onClick={() => history.back()}>{formatMessage({ id: 'page.exception.noPermission.back', defaultMessage: '返回上一页' })}</Button>
          <Button
            type="primary"
            onClick={() => history.replace(resolvePermissionRecoveryTarget(currentUser, initialState?.menuTree))}
          >
            {formatMessage({ id: 'page.exception.noPermission.home', defaultMessage: '回到首页' })}
          </Button>
        </Space>
      }
    />
  );
};
