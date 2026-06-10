import SliderCaptcha from 'rc-slider-captcha';
import { useCallback, useRef, useState } from 'react';
import { useIntl } from '@umijs/max';
import { request } from '@/services/common/request';
import type { CaptchaChallenge, CaptchaVerifyResult } from '@/types/api';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { useResponsive } from '@/hooks/useResponsive';

interface SliderCaptchaBoxProps {
  mode?: 'embed' | 'float';
  onChallengeChange?: (challenge: CaptchaChallenge | null) => void;
  onVerified?: (result: CaptchaVerifyResult) => void;
  onReset?: () => void;
}

export const SliderCaptchaBox = ({ mode = 'embed', onChallengeChange, onVerified, onReset }: SliderCaptchaBoxProps) => {
  const intl = useIntl();
  const captchaRootRef = useRef<HTMLDivElement | null>(null);
  const activeChallengeRef = useRef<CaptchaChallenge | null>(null);
  const { isMobile } = useResponsive();
  const sliderCaptchaWidth = resolveResponsiveValue(APP_SPACING.sliderCaptcha.width, isMobile);
  const sliderCaptchaHeight = resolveResponsiveValue(APP_SPACING.sliderCaptcha.height, isMobile);
  const sliderPuzzleSize = resolveResponsiveValue(APP_SPACING.sliderCaptcha.puzzleSize, isMobile);
  const sliderPuzzleTop = resolveResponsiveValue(APP_SPACING.sliderCaptcha.puzzleTop, isMobile);
  const [puzzleSize, setPuzzleSize] = useState({ width: sliderPuzzleSize, height: sliderPuzzleSize, left: 0, top: sliderPuzzleTop });
  const panelPaddingTop = isMobile ? APP_SPACING.antdMobileTokens.paddingSM : APP_SPACING.antdDesktopTokens.paddingSM;
  const getRenderedWidthScale = useCallback(() => {
    const renderedWidth = captchaRootRef.current
      ?.querySelector('.rc-slider-captcha-jigsaw')
      ?.getBoundingClientRect()
      .width;

    if (!renderedWidth || renderedWidth <= 0) {
      return 1;
    }

    return sliderCaptchaWidth / renderedWidth;
  }, [sliderCaptchaWidth]);

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
      width: challenge.puzzleWidth ?? sliderPuzzleSize,
      height: challenge.puzzleHeight ?? sliderPuzzleSize,
      left: challenge.puzzleLeft ?? 0,
      top: challenge.puzzleTop ?? sliderPuzzleTop,
    });
    onChallengeChange?.(challenge);

    if (!challenge.bgUrl || !challenge.puzzleUrl) {
      throw new Error(intl.formatMessage({ id: 'common.captchaResourceIncomplete', defaultMessage: '拖动验证码资源不完整' }));
    }

    return {
      bgUrl: challenge.bgUrl,
      puzzleUrl: challenge.puzzleUrl,
    };
  }, [intl, onChallengeChange, onReset, sliderPuzzleSize, sliderPuzzleTop]);

  const verifySliderCaptcha = useCallback(
    async (data: import('rc-slider-captcha').VerifyParam) => {
      const activeChallenge = activeChallengeRef.current;
      if (!activeChallenge?.captchaId) {
        throw new Error(intl.formatMessage({ id: 'common.captchaExpired', defaultMessage: '拖动验证码已失效' }));
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
    [getRenderedWidthScale, intl, onVerified],
  );

  return (
    <div ref={captchaRootRef} className="saas-slider-captcha-box">
      <SliderCaptcha
        mode={mode}
        request={requestSliderCaptcha}
        onVerify={verifySliderCaptcha}
        style={{ width: sliderCaptchaWidth, maxWidth: '100%', margin: '0 auto' }}
        bgSize={{ width: sliderCaptchaWidth, height: sliderCaptchaHeight }}
        puzzleSize={puzzleSize}
        styles={{
          panel: {
            width: sliderCaptchaWidth,
            maxWidth: '100%',
            paddingTop: panelPaddingTop,
          },
          jigsaw: {
            width: sliderCaptchaWidth,
            maxWidth: '100%',
            overflow: 'hidden',
            borderRadius: 'var(--saas-card-radius)',
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
            width: sliderCaptchaWidth,
            maxWidth: '100%',
          },
        }}
        tipText={{
          default: intl.formatMessage({ id: 'common.captchaDefault', defaultMessage: '向右拖动滑块完成验证' }),
          loading: intl.formatMessage({ id: 'common.captchaLoading', defaultMessage: '验证码加载中...' }),
          verifying: intl.formatMessage({ id: 'common.captchaVerifying', defaultMessage: '正在校验...' }),
          success: intl.formatMessage({ id: 'common.captchaSuccess', defaultMessage: '验证通过' }),
          error: intl.formatMessage({ id: 'common.captchaError', defaultMessage: '验证失败，请重试' }),
          loadFailed: intl.formatMessage({ id: 'common.captchaLoadFailed', defaultMessage: '加载失败，点击重试' }),
        }}
      />
    </div>
  );
};
