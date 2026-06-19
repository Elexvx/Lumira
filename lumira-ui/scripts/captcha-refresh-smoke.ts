import assert from 'node:assert/strict';
import type { CaptchaChallenge } from '../src/types/api';

const createCaptchaRefreshController = (deps: {
  getCaptchaEnabled: () => boolean;
  getCaptchaType: () => 'IMAGE' | 'SLIDER';
  loadChallenge: (captchaType: 'IMAGE' | 'SLIDER') => Promise<CaptchaChallenge>;
  setCaptchaChallenge: (challenge: CaptchaChallenge | null) => void;
  setCaptchaLoading: (loading: boolean) => void;
  setCaptchaImageLoadFailed: (failed: boolean) => void;
  onRefreshFailure: () => void;
}) => {
  let requestSeq = 0;

  const refresh = async (): Promise<CaptchaChallenge | null> => {
    if (!deps.getCaptchaEnabled()) {
      deps.setCaptchaChallenge(null);
      deps.setCaptchaImageLoadFailed(false);
      deps.setCaptchaLoading(false);
      return null;
    }

    const seq = ++requestSeq;
    deps.setCaptchaLoading(true);
    deps.setCaptchaImageLoadFailed(false);

    try {
      const challenge = await deps.loadChallenge(deps.getCaptchaType());
      if (seq !== requestSeq) {
        return null;
      }
      deps.setCaptchaImageLoadFailed(false);
      deps.setCaptchaChallenge(challenge);
      return challenge;
    } catch {
      if (seq === requestSeq) {
        deps.onRefreshFailure();
      }
      return null;
    } finally {
      if (seq === requestSeq) {
        deps.setCaptchaLoading(false);
      }
    }
  };

  const invalidate = () => {
    requestSeq += 1;
  };

  return { refresh, invalidate };
};

const deferred = <T>() => {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
};

const run = async () => {
  const state: {
    challenge: CaptchaChallenge | null;
    loading: boolean;
    imageFailed: boolean;
    warnings: string[];
  } = {
    challenge: null,
    loading: false,
    imageFailed: true,
    warnings: [],
  };

  const first = deferred<CaptchaChallenge>();
  const second = deferred<CaptchaChallenge>();
  const failures = deferred<never>();
  let callCount = 0;

  const controller = createCaptchaRefreshController({
    getCaptchaEnabled: () => true,
    getCaptchaType: () => 'IMAGE',
    loadChallenge: async () => {
      callCount += 1;
      if (callCount === 1) {
        return await first.promise;
      }
      if (callCount === 2) {
        return await second.promise;
      }
      return await failures.promise;
    },
    setCaptchaChallenge: (challenge) => {
      state.challenge = challenge;
    },
    setCaptchaLoading: (loading) => {
      state.loading = loading;
    },
    setCaptchaImageLoadFailed: (failed) => {
      state.imageFailed = failed;
    },
    onRefreshFailure: () => {
      state.warnings.push('验证码刷新失败，请稍后重试');
    },
  });

  const firstRefresh = controller.refresh();
  assert.equal(state.loading, true, 'refresh should mark loading');
  assert.equal(state.imageFailed, false, 'refresh should clear image failure state');

  const secondRefresh = controller.refresh();
  second.resolve({
    captchaId: 'captcha-2',
    captchaType: 'IMAGE',
    imageUrl: 'https://example.com/captcha-2.png',
  });
  await secondRefresh;
  assert.equal(state.challenge?.captchaId, 'captcha-2', 'latest refresh should win');
  assert.equal(state.loading, false, 'loading should clear after latest refresh');
  assert.equal(state.imageFailed, false, 'successful refresh should clear image load failures');

  first.resolve({
    captchaId: 'captcha-1',
    captchaType: 'IMAGE',
    imageUrl: 'https://example.com/captcha-1.png',
  });
  await firstRefresh;
  assert.equal(state.challenge?.captchaId, 'captcha-2', 'older refresh must not overwrite the latest captcha');

  const previousChallenge = state.challenge;
  const failingController = createCaptchaRefreshController({
    getCaptchaEnabled: () => true,
    getCaptchaType: () => 'IMAGE',
    loadChallenge: async () => {
      throw new Error('network down');
    },
    setCaptchaChallenge: (challenge) => {
      state.challenge = challenge;
    },
    setCaptchaLoading: (loading) => {
      state.loading = loading;
    },
    setCaptchaImageLoadFailed: (failed) => {
      state.imageFailed = failed;
    },
    onRefreshFailure: () => {
      state.warnings.push('验证码刷新失败，请稍后重试');
    },
  });

  await failingController.refresh();
  assert.equal(state.challenge?.captchaId, previousChallenge?.captchaId, 'failed refresh should keep the previous captcha visible');
  assert.equal(state.warnings[state.warnings.length - 1], '验证码刷新失败，请稍后重试', 'failed refresh should warn the user');
  assert.equal(state.loading, false, 'failed refresh should clear loading state');

  let cleared = false;
  const disabledController = createCaptchaRefreshController({
    getCaptchaEnabled: () => false,
    getCaptchaType: () => 'IMAGE',
    loadChallenge: async () => {
      throw new Error('should not be called');
    },
    setCaptchaChallenge: (challenge) => {
      cleared = challenge === null;
    },
    setCaptchaLoading: () => undefined,
    setCaptchaImageLoadFailed: () => undefined,
    onRefreshFailure: () => undefined,
  });
  await disabledController.refresh();
  assert.equal(cleared, true, 'disabling captcha should clear the challenge');
  assert.equal(state.loading, false, 'disabling captcha should not leave loading behind');

  console.log('captcha-refresh-smoke: ok');
};

void run().catch((error) => {
  throw error;
});
