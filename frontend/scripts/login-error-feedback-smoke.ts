import assert from 'node:assert/strict';
import { ErrorCode } from '../src/enums/errorCode';
import { resolveApiErrorFeedback } from '../src/services/common/errorFeedback';
import type { ApiErrorLike } from '../src/services/common/errorFeedback';

const LOGIN_WARNING_CODES = new Set<string>([
  ErrorCode.LOGIN_FAILED,
  ErrorCode.ACCOUNT_NOT_FOUND,
  ErrorCode.PASSWORD_ERROR,
  ErrorCode.ACCOUNT_DISABLED,
]);

const resolveLoginErrorFeedback = (error: ApiErrorLike) => {
  const feedback = resolveApiErrorFeedback(error, false);

  if (LOGIN_WARNING_CODES.has(error.code)) {
    return {
      type: 'warning' as const,
      message: feedback.message,
    };
  }

  return feedback.type === 'info'
    ? {
        ...feedback,
        type: 'warning' as const,
      }
    : feedback;
};

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
