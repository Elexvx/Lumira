import type { RequestOptions } from '@/services/common/request';
import { systemService } from '@/services/system';
import type { CaptchaChallenge, CaptchaType } from '@/types/api';

const preloadImage = (imageUrl: string) =>
  new Promise<void>((resolve, reject) => {
    const image = new Image();
    image.decoding = 'async';
    image.onload = () => resolve();
    image.onerror = () => reject(new Error('验证码图片加载失败'));
    image.src = imageUrl;
  });

export const loadCaptchaChallenge = async (
  captchaType: CaptchaType,
  options: RequestOptions = {},
): Promise<CaptchaChallenge> => {
  const challenge = await systemService.captchaChallenge(captchaType, options);
  if (challenge?.imageUrl) {
    try {
      await preloadImage(challenge.imageUrl);
    } catch {
      // Keep the challenge usable even if the image prefetch fails.
    }
  }
  return challenge;
};
