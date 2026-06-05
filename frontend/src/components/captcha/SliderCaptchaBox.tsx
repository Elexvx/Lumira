import SliderCaptcha from 'rc-slider-captcha';
import { useCallback, useRef, useState } from 'react';
import { request } from '@/services/common/request';
import type { CaptchaChallenge, CaptchaVerifyResult } from '@/types/api';

const SLIDER_CAPTCHA_WIDTH = 320;
const SLIDER_CAPTCHA_HEIGHT = 160;

interface SliderCaptchaBoxProps {
  mode?: 'embed' | 'float';
  onChallengeChange?: (challenge: CaptchaChallenge | null) => void;
  onVerified?: (result: CaptchaVerifyResult) => void;
  onReset?: () => void;
}

export const SliderCaptchaBox = ({ mode = 'embed', onChallengeChange, onVerified, onReset }: SliderCaptchaBoxProps) => {
  const captchaRootRef = useRef<HTMLDivElement | null>(null);
  const activeChallengeRef = useRef<CaptchaChallenge | null>(null);
  const [puzzleSize, setPuzzleSize] = useState({ width: 58, height: 58, left: 0, top: 48 });
  const getRenderedWidthScale = useCallback(() => {
    const renderedWidth = captchaRootRef.current
      ?.querySelector('.rc-slider-captcha-jigsaw')
      ?.getBoundingClientRect()
      .width;

    if (!renderedWidth || renderedWidth <= 0) {
      return 1;
    }

    return SLIDER_CAPTCHA_WIDTH / renderedWidth;
  }, []);

  const requestSliderCaptcha = useCallback(async () => {
    onReset?.();
    const challenge = await request<CaptchaChallenge>('/v1/public/captcha/challenge', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      params: { captchaType: 'SLIDER' },
      autoRedirectOnUnauthorized: false,
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
  }, [onChallengeChange, onReset]);

  const verifySliderCaptcha = useCallback(
    async (data: import('rc-slider-captcha').VerifyParam) => {
      const activeChallenge = activeChallengeRef.current;
      if (!activeChallenge?.captchaId) {
        throw new Error('拖动验证码已失效');
      }

      const coordinateScale = getRenderedWidthScale();
      const result = await request<CaptchaVerifyResult>('/v1/public/captcha/slider/verify', {
        method: 'POST',
        data: {
          captchaId: activeChallenge.captchaId,
          x: data.x * coordinateScale,
          y: data.y,
          sliderOffsetX: data.sliderOffsetX * coordinateScale,
          duration: data.duration,
          trail: data.trail,
          targetType: data.targetType,
          errorCount: data.errorCount,
        },
        skipAuth: true,
        silent: true,
        autoRedirectOnUnauthorized: false,
      });

      onVerified?.(result);
      return result;
    },
    [getRenderedWidthScale, onVerified],
  );

  return (
    <div ref={captchaRootRef} className="saas-slider-captcha-box">
      <SliderCaptcha
        mode={mode}
        request={requestSliderCaptcha}
        onVerify={verifySliderCaptcha}
        style={{ width: SLIDER_CAPTCHA_WIDTH, maxWidth: '100%', margin: '0 auto' }}
        bgSize={{ width: SLIDER_CAPTCHA_WIDTH, height: SLIDER_CAPTCHA_HEIGHT }}
        puzzleSize={puzzleSize}
        styles={{
          panel: {
            width: SLIDER_CAPTCHA_WIDTH,
            maxWidth: '100%',
            paddingTop: 12,
          },
          jigsaw: {
            width: SLIDER_CAPTCHA_WIDTH,
            maxWidth: '100%',
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
            width: SLIDER_CAPTCHA_WIDTH,
            maxWidth: '100%',
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
    </div>
  );
};
