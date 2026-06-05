import assert from 'node:assert/strict';

const CAPTCHA_ALLOWED_CHAR_PATTERN = /^[A-Za-z0-9]$/;
const CAPTCHA_SANITIZE_PATTERN = /[^A-Za-z0-9]/g;

const sanitizeCaptchaValue = (value: string) => value.replace(CAPTCHA_SANITIZE_PATTERN, '');

const getCaptchaValueFromEvent = (event: { target?: { value?: unknown } } | string | number | null | undefined) => {
  if (typeof event === 'string' || typeof event === 'number') {
    return sanitizeCaptchaValue(String(event));
  }

  return sanitizeCaptchaValue(String(event?.target?.value ?? ''));
};

const shouldBlockCaptchaKey = (event: {
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

const run = () => {
  assert.equal(sanitizeCaptchaValue('a中b 1-2_3'), 'ab123', 'captcha sanitization should keep only ASCII letters and numbers');
  assert.equal(
    getCaptchaValueFromEvent({ target: { value: 'NvKwx' } }),
    'NvKwx',
    'captcha event parsing should preserve ASCII captcha text',
  );
  assert.equal(
    getCaptchaValueFromEvent({ target: { value: 'Nv中文Kwx' } }),
    'NvKwx',
    'captcha event parsing should drop non-ASCII input',
  );
  assert.equal(
    shouldBlockCaptchaKey({ key: '你' }),
    true,
    'captcha input should block non-ASCII key presses',
  );
  assert.equal(
    shouldBlockCaptchaKey({ key: 'a' }),
    false,
    'captcha input should allow ASCII letters',
  );
  assert.equal(
    shouldBlockCaptchaKey({ isComposing: true, key: 'a' }),
    true,
    'captcha input should block IME composition',
  );

  console.log('captcha-input-smoke: ok');
};

run();
