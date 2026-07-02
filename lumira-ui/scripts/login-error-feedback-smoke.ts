import assert from 'node:assert/strict';
import { ErrorCode } from '../src/enums/errorCode';
import type { ApiErrorLike } from '../src/services/common/errorFeedback';
import { ApiRequestError } from '../src/services/common/requestInternalsTypes';
import { resolveLoginErrorFeedback, shouldFallbackToLegacyPasswordLogin } from '../src/pages/user/login/utils/loginErrorFeedback';

const t = ({ defaultMessage }: { defaultMessage: string }) => defaultMessage;

const buildError = (code: string, message: string, userMessage?: string, httpStatus = 401): ApiErrorLike => ({
  code,
  message,
  userMessage,
  httpStatus,
});

const assertWarning = (error: ApiErrorLike, expected: string, description: string) => {
  const feedback = resolveLoginErrorFeedback(error, t);
  assert.equal(feedback.type, 'warning', `${description} should stay on the login page`);
  assert.equal(feedback.message, expected, description);
};

const run = () => {
  assertWarning(
    buildError(ErrorCode.ACCOUNT_NOT_FOUND, 'Login failed, account not found: demo', '账号或密码错误'),
    '账号或密码错误',
    'account lookup failures should show the credential message',
  );

  assertWarning(
    buildError(ErrorCode.PASSWORD_ERROR, 'Login failed, password mismatch: demo', '账号或密码错误'),
    '账号或密码错误',
    'password failures should show the credential message',
  );

  assertWarning(
    buildError(ErrorCode.LOGIN_FAILED, '登录失败', '登录失败'),
    '账号或密码错误',
    'generic password login failures should show the credential message',
  );

  assertWarning(
    buildError(ErrorCode.BAD_REQUEST, '登录密码解密失败', '请检查请求内容后重试', 400),
    '账号或密码错误',
    'login password payload failures should show the credential message instead of the generic bad-request message',
  );

  assertWarning(
    buildError(ErrorCode.UNAUTHORIZED, '信息错误', '信息错误'),
    '账号或密码错误',
    'unauthorized login failures should not show the generic backend message',
  );

  assertWarning(
    buildError(ErrorCode.CAPTCHA_INVALID, 'captcha invalid', undefined, 400),
    '验证码错误，请重新输入',
    'image captcha failures should show the captcha message',
  );

  assertWarning(
    buildError(ErrorCode.VALIDATION_ERROR, '验证码错误，请重新输入', '输入信息有误，请检查后重试', 400),
    '验证码错误，请重新输入',
    'validation-wrapped image captcha failures should not show the generic user message',
  );

  assertWarning(
    buildError(ErrorCode.VALIDATION_ERROR, '验证码错误，请重试', '验证码错误，请重试', 400),
    '验证码错误，请重试',
    'verification code failures should keep the verification-code message',
  );

  assertWarning(
    buildError(ErrorCode.ACCOUNT_DISABLED, 'Login failed, account disabled: demo', '账号已被禁用，请联系管理员'),
    '账号已被禁用，请联系管理员',
    'disabled-account failures should show the disabled-account message',
  );

  assertWarning(
    buildError(ErrorCode.LOGIN_RATE_LIMITED, '登录失败次数过多，请稍后再试', undefined, 429),
    '登录失败次数过多，请稍后再试',
    'rate-limited login failures should show the rate-limit message',
  );

  assert.equal(
    shouldFallbackToLegacyPasswordLogin(new ApiRequestError(ErrorCode.NOT_FOUND, 'Not Found', { httpStatus: 404 })),
    true,
    'missing v2 login endpoint may fall back to legacy v1 login',
  );
  assert.equal(
    shouldFallbackToLegacyPasswordLogin(new ApiRequestError(ErrorCode.LOGIN_FAILED, '账号或密码错误', { httpStatus: 401 })),
    false,
    'credential failures must not retry legacy login with a consumed captcha',
  );
  assert.equal(
    shouldFallbackToLegacyPasswordLogin(new ApiRequestError(ErrorCode.CAPTCHA_INVALID, 'captcha invalid', { httpStatus: 400 })),
    false,
    'captcha failures must not retry legacy login with the same captcha',
  );
  assert.equal(
    shouldFallbackToLegacyPasswordLogin(new ApiRequestError(ErrorCode.VALIDATION_ERROR, '验证码错误，请重新输入', { httpStatus: 400 })),
    false,
    'validation failures must not retry legacy login with the same captcha',
  );

  console.log('login-error-feedback-smoke: ok');
};

run();
