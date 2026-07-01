import { ErrorCode } from '../../../../enums/errorCode';
import { resolveApiErrorFeedback, type ApiErrorLike, type ErrorFeedback } from '../../../../services/common/errorFeedback';
import { ApiRequestError } from '../../../../services/common/requestInternalsTypes';

export type LoginFeedbackTranslator = (descriptor: { id: string; defaultMessage: string }) => string;

const LOGIN_CREDENTIAL_ERROR_CODES = new Set<string>([
  ErrorCode.UNAUTHORIZED,
  ErrorCode.LOGIN_FAILED,
  ErrorCode.ACCOUNT_NOT_FOUND,
  ErrorCode.PASSWORD_ERROR,
]);

const LOGIN_WARNING_CODES = new Set<string>([
  ErrorCode.ACCOUNT_DISABLED,
  ErrorCode.LOGIN_RATE_LIMITED,
  ErrorCode.CAPTCHA_INVALID,
]);

const normalizeErrorText = (error: ApiErrorLike) => `${error.userMessage || ''} ${error.message || ''}`.trim().toLowerCase();

const includesAny = (text: string, keywords: string[]) => keywords.some((keyword) => text.includes(keyword.toLowerCase()));

export const shouldFallbackToLegacyPasswordLogin = (error: unknown) =>
  error instanceof ApiRequestError && (error.httpStatus === 404 || error.code === ErrorCode.NOT_FOUND);

export const resolveLoginErrorFeedback = (error: ApiErrorLike, translate: LoginFeedbackTranslator): ErrorFeedback => {
  const feedback = resolveApiErrorFeedback(error, false);
  const text = normalizeErrorText(error);

  if (LOGIN_CREDENTIAL_ERROR_CODES.has(error.code)) {
    return {
      type: 'warning',
      message: translate({ id: 'page.login.error.invalidCredentials', defaultMessage: '账号或密码错误' }),
    };
  }

  if (
    error.code === ErrorCode.CAPTCHA_INVALID ||
    includesAny(text, ['captcha invalid', 'captcha required', '图形验证码', '图片验证码', '验证码错误，请重新输入'])
  ) {
    return {
      type: 'warning',
      message: translate({ id: 'page.login.error.invalidCaptcha', defaultMessage: '验证码错误，请重新输入' }),
    };
  }

  if (error.code === ErrorCode.ACCOUNT_DISABLED) {
    return {
      type: 'warning',
      message: translate({ id: 'page.login.error.accountDisabled', defaultMessage: '账号已被禁用，请联系管理员' }),
    };
  }

  if (error.code === ErrorCode.LOGIN_RATE_LIMITED) {
    return {
      type: 'warning',
      message: translate({ id: 'page.login.error.loginRateLimited', defaultMessage: '登录失败次数过多，请稍后再试' }),
    };
  }

  if (includesAny(text, ['验证码错误', '验证码不正确', 'verification code invalid', 'invalid verification code'])) {
    return {
      type: 'warning',
      message: translate({ id: 'page.login.error.invalidVerificationCode', defaultMessage: '验证码错误，请重试' }),
    };
  }

  if (includesAny(text, ['验证码已过期', '验证码会话不存在', 'captcha expired', 'captcha not found'])) {
    return {
      type: 'warning',
      message: translate({ id: 'page.login.error.captchaExpired', defaultMessage: '验证码已过期，请刷新后重试' }),
    };
  }

  if (LOGIN_WARNING_CODES.has(error.code)) {
    return {
      type: 'warning',
      message: feedback.message,
    };
  }

  return feedback.type === 'info'
    ? {
        ...feedback,
        type: 'warning',
      }
    : feedback;
};
