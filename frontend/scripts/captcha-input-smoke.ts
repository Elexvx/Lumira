import assert from 'node:assert/strict';
import { getCaptchaValueFromEvent, sanitizeCaptchaValue, shouldBlockCaptchaKey } from '../src/pages/user/login/captchaInput';

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
