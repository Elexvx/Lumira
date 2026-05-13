import SliderCaptcha, { type VerifyParam } from 'rc-slider-captcha';
import { useRef, useState } from 'react';
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
  const [puzzleSize, setPuzzleSize] = useState({ width: 58, height: 58, left: 0, top: 48 });

  const requestSliderCaptcha = async () => {
    onReset?.();
    const challenge = await systemService.captchaChallenge('SLIDER', {
      autoRedirectOnUnauthorized: false,
      silent: true,
    });
    activeChallengeRef.current = challenge;
    setPuzzleSize({
      width: challenge.puzzleWidth ?? 58,
      height: challenge.puzzleHeight ?? 58,
      left: challenge.puzzleLeft ?? 0,
      top: challenge.puzzleTop ?? 48,
    });
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
      style={{ width: mode === 'embed' ? '100%' : 'fit-content', margin: '0 auto' }}
      bgSize={{ width: 320, height: 160 }}
      puzzleSize={puzzleSize}
      styles={{
        panel: {
          width: '100%',
          paddingTop: 12,
        },
        jigsaw: {
          overflow: 'hidden',
          borderRadius: 8,
          boxShadow: '0 12px 24px rgba(0, 0, 0, 0.16)',
          border: '1px solid var(--ant-color-border-secondary)',
          backgroundColor: 'var(--ant-color-bg-container)',
        },
        bgImg: {
          display: 'block',
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          opacity: 1,
        },
        puzzleImg: {
          display: 'block',
          height: '100%',
          objectFit: 'cover',
          opacity: 1,
        },
        control: {
          width: '100%',
        },
      }}
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
