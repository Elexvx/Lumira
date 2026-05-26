const CAPTCHA_ALLOWED_CHAR_PATTERN = /^[A-Za-z0-9]$/;
const CAPTCHA_SANITIZE_PATTERN = /[^A-Za-z0-9]/g;

export const sanitizeCaptchaValue = (value: string) => value.replace(CAPTCHA_SANITIZE_PATTERN, '');

export const getCaptchaValueFromEvent = (event: { target?: { value?: unknown } } | string | number | null | undefined) => {
  if (typeof event === 'string' || typeof event === 'number') {
    return sanitizeCaptchaValue(String(event));
  }

  return sanitizeCaptchaValue(String(event?.target?.value ?? ''));
};

export const shouldBlockCaptchaKey = (event: {
  altKey?: boolean;
  ctrlKey?: boolean;
  isComposing?: boolean;
  key?: string;
  keyCode?: number;
  metaKey?: boolean;
}) => {
  if (event.isComposing || event.keyCode === 229) {
    return true;
  }

  if (event.ctrlKey || event.altKey || event.metaKey) {
    return false;
  }

  if (!event.key || event.key.length !== 1) {
    return false;
  }

  return !CAPTCHA_ALLOWED_CHAR_PATTERN.test(event.key);
};

export const shouldBlockCaptchaPaste = (event: { clipboardData?: { getData: (type: string) => string } }) => {
  const text = event.clipboardData?.getData('text') ?? '';
  return sanitizeCaptchaValue(text) !== text;
};
