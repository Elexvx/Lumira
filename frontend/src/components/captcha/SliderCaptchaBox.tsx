import SliderCaptcha, { type VerifyParam } from 'rc-slider-captcha';
import { useRef } from 'react';
import { systemService } from '@/services/system';
import type { CaptchaChallenge, CaptchaVerifyResult } from '@/types/api';

interface SliderCaptchaBoxProps {
  mode?: 'embed' | 'float';
  onChallengeChange?: (challenge: CaptchaChallenge | null) => void;
  onVerified?: (result: CaptchaVerifyResult) => void;
  onReset?: () => void;
}

export const SliderCaptchaBox = ({
  mode = 'embed',
  onChallengeChange,
  onVerified,
  onReset,
}: SliderCaptchaBoxProps) => {
  const activeChallengeRef = useRef<CaptchaChallenge | null>(null);

  const requestSliderCaptcha = async () => {
    onReset?.();
    const challenge = await systemService.captchaChallenge('SLIDER', {
      autoRedirectOnUnauthorized: false,
      silent: true,
    });
    activeChallengeRef.current = challenge;
    onChallengeChange?.(challenge);

    if (!challenge.bgUrl || !challenge.puzzleUrl) {
      throw new Error('拖动验证码资源不完整');
    }

    return {
      bgUrl: challenge.bgUrl,
      puzzleUrl: challenge.puzzleUrl,
    };
  };

  const verifySliderCaptcha = async (data: VerifyParam) => {
    const activeChallenge = activeChallengeRef.current;
    if (!activeChallenge?.captchaId) {
      throw new Error('拖动验证码已失效');
    }

    const result = await systemService.captchaSliderVerify(
      {
        captchaId: activeChallenge.captchaId,
        x: data.x,
        y: data.y,
        sliderOffsetX: data.sliderOffsetX,
        duration: data.duration,
        trail: data.trail,
        targetType: data.targetType,
        errorCount: data.errorCount,
      },
      {
        autoRedirectOnUnauthorized: false,
        silent: true,
      },
    );

    onVerified?.(result);
    return result;
  };

  return (
    <SliderCaptcha
      mode={mode}
      request={requestSliderCaptcha}
      onVerify={verifySliderCaptcha}
      bgSize={{ width: 320, height: 160 }}
      puzzleSize={{ width: 60, height: 160 }}
      tipText={{
        default: '向右拖动滑块完成验证',
        loading: '验证码加载中...',
        verifying: '正在校验...',
        success: '验证通过',
        error: '验证失败，请重试',
        loadFailed: '加载失败，点击重试',
      }}
    />
  );
};
