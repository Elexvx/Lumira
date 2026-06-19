import assert from 'node:assert/strict';
import { request, type RequestOptions } from '../src/services/common/request';
import type { CaptchaChallenge, CaptchaType } from '../src/types/api';

const preloadImage = (imageUrl: string) =>
  new Promise<void>((resolve, reject) => {
    const image = new Image();
    image.decoding = 'async';
    image.onload = () => resolve();
    image.onerror = () => reject(new Error('验证码图片加载失败'));
    image.src = imageUrl;
  });

const captchaRequests = {
  captchaChallenge: (captchaType: CaptchaType, options: RequestOptions = {}) =>
    request<CaptchaChallenge>('/v1/public/captcha/challenge', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      params: { captchaType },
      ...options,
    }),
};

const loadCaptchaChallenge = async (captchaType: CaptchaType, options: RequestOptions = {}) => {
  const challenge = await captchaRequests.captchaChallenge(captchaType, options);
  if (challenge?.imageUrl) {
    try {
      await preloadImage(challenge.imageUrl);
    } catch {
      // Keep the challenge usable even if the image prefetch fails.
    }
  }
  return challenge;
};

const originalImage = globalThis.Image;
const originalFetch = globalThis.fetch;

class FailingImage {
  decoding = 'async';
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;

  set src(_value: string) {
    queueMicrotask(() => {
      this.onerror?.();
    });
  }
}

const run = async () => {
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: {
      location: {
        origin: 'http://localhost:8000',
      },
      setTimeout: globalThis.setTimeout.bind(globalThis),
      clearTimeout: globalThis.clearTimeout.bind(globalThis),
    },
  });

  const challenge: CaptchaChallenge = {
    captchaId: 'captcha-1',
    captchaType: 'IMAGE',
    imageUrl: 'https://example.com/captcha.png',
  };

  globalThis.Image = FailingImage as unknown as typeof Image;
  globalThis.fetch = (async (input: RequestInfo | URL) => {
    const requestUrl = new URL(String(input));
    assert.equal(requestUrl.pathname, '/api/v1/public/captcha/challenge', 'captcha smoke should request captcha challenge');
    assert.equal(requestUrl.searchParams.get('captchaType'), 'IMAGE', 'captcha smoke should keep captcha type param');
    return new Response(JSON.stringify({
      code: '0',
      message: 'ok',
      data: challenge,
      requestId: 'captcha-smoke-request',
    }), {
      status: 200,
      headers: {
        'content-type': 'application/json',
      },
    });
  }) as typeof fetch;

  const loadedChallenge = await loadCaptchaChallenge('IMAGE', {
    autoRedirectOnUnauthorized: false,
    silent: true,
    skipAuth: true,
  });

  assert.equal(loadedChallenge.captchaId, challenge.captchaId, 'captcha challenge should still resolve');
  assert.equal(loadedChallenge.imageUrl, challenge.imageUrl, 'preload failure must not drop the challenge');

  globalThis.Image = originalImage;
  globalThis.fetch = originalFetch;
  console.log('captcha-load-smoke: ok');
};

void run().catch((error) => {
  globalThis.Image = originalImage;
  globalThis.fetch = originalFetch;
  throw error;
});
