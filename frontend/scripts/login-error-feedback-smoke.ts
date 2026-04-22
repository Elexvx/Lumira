import assert from 'node:assert/strict';
import { ErrorCode } from '../src/enums/errorCode';
import { resolveLoginErrorFeedback } from '../src/pages/user/login/loginErrorFeedback';
import type { ApiErrorLike } from '../src/services/common/errorFeedback';

const buildError = (code: string, message: string, userMessage?: string, httpStatus = 401): ApiErrorLike => ({
  code,
  message,
  userMessage,
  httpStatus,
});

const run = () => {
  const accountError = resolveLoginErrorFeedback(
    buildError(ErrorCode.ACCOUNT_NOT_FOUND, '登录失败，账号不存在: demo', '账号或密码错误'),
  );
  assert.equal(accountError.type, 'warning', 'account lookup failures should stay on the login page');
  assert.equal(accountError.message, '账号或密码错误', 'account failures should show the backend message');

  const passwordError = resolveLoginErrorFeedback(
    buildError(ErrorCode.PASSWORD_ERROR, '登录失败，密码错误: demo', '账号或密码错误'),
  );
  assert.equal(passwordError.type, 'warning', 'password failures should stay on the login page');
  assert.equal(passwordError.message, '账号或密码错误', 'password failures should show the backend message');

  const disabledError = resolveLoginErrorFeedback(
    buildError(ErrorCode.ACCOUNT_DISABLED, '登录失败，账号已禁用: demo', '账号已被禁用，请联系管理员'),
  );
  assert.equal(disabledError.type, 'warning', 'disabled-account failures should stay on the login page');
  assert.equal(disabledError.message, '账号已被禁用，请联系管理员', 'disabled-account failures should show the backend message');

  console.log('login-error-feedback-smoke: ok');
};

run();
