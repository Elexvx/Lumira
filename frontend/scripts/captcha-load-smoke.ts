import assert from 'node:assert/strict';
import { loadCaptchaChallenge } from '../src/auth/captcha';
import { systemService } from '../src/services/system';
import type { CaptchaChallenge } from '../src/types/api';

const originalCaptchaChallenge = systemService.captchaChallenge;
const originalImage = globalThis.Image;

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
  const challenge: CaptchaChallenge = {
    captchaId: 'captcha-1',
    captchaType: 'IMAGE',
    imageUrl: 'https://example.com/captcha.png',
  };

  systemService.captchaChallenge = async () => challenge;
  globalThis.Image = FailingImage as unknown as typeof Image;

  const loadedChallenge = await loadCaptchaChallenge('IMAGE', {
    autoRedirectOnUnauthorized: false,
    silent: true,
    skipAuth: true,
  });

  assert.equal(loadedChallenge.captchaId, challenge.captchaId, 'captcha challenge should still resolve');
  assert.equal(loadedChallenge.imageUrl, challenge.imageUrl, 'preload failure must not drop the challenge');

  systemService.captchaChallenge = originalCaptchaChallenge;
  globalThis.Image = originalImage;
  console.log('captcha-load-smoke: ok');
};

void run().catch((error) => {
  systemService.captchaChallenge = originalCaptchaChallenge;
  globalThis.Image = originalImage;
  throw error;
});
