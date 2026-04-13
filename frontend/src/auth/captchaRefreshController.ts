import type { CaptchaChallenge, CaptchaType } from '@/types/api';

export interface CaptchaRefreshControllerDeps {
  getCaptchaEnabled: () => boolean;
  getCaptchaType: () => CaptchaType;
  loadChallenge: (captchaType: CaptchaType) => Promise<CaptchaChallenge>;
  setCaptchaChallenge: (challenge: CaptchaChallenge | null) => void;
  setCaptchaLoading: (loading: boolean) => void;
  setCaptchaImageLoadFailed: (failed: boolean) => void;
  onRefreshFailure: () => void;
}

export interface CaptchaRefreshController {
  refresh: () => Promise<CaptchaChallenge | null>;
  invalidate: () => void;
}

export const createCaptchaRefreshController = (deps: CaptchaRefreshControllerDeps): CaptchaRefreshController => {
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
