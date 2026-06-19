import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { request } from '@/services/common/request';
import type { SensitiveWordCheckResult } from '@/types/api';

type BubbleState = {
  top: number;
  left: number;
  text: string;
};

const INPUT_SELECTOR = 'input:not([type="password"]):not([type="hidden"]), textarea';

const GlobalSensitiveWordGuard = () => {
  const { initialState } = useInitialStateModel();
  const timerRef = useRef<number | null>(null);
  const activeElementRef = useRef<HTMLElement | null>(null);
  const [bubble, setBubble] = useState<BubbleState | null>(null);
  const pluginEnabled = Boolean(initialState?.availablePlugins?.some((item) => item.pluginCode === 'sensitive-words'));

  useEffect(() => {
    if (!pluginEnabled) {
      setBubble(null);
      return undefined;
    }
    const clearPending = () => {
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };

    const hideBubble = () => {
      clearPending();
      activeElementRef.current = null;
      setBubble(null);
    };

    const maybeShowBubble = async (target: HTMLElement) => {
      const text = target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement ? target.value : '';
      if (!text.trim()) {
        setBubble(null);
        return;
      }
      const fieldPath = target.getAttribute('name') || target.getAttribute('id') || target.getAttribute('placeholder') || 'input';
      let result: SensitiveWordCheckResult;
      try {
        result = await request<SensitiveWordCheckResult>('/v1/sensitive-words/check', {
          method: 'POST',
          data: { text, fieldPath },
          silent: true,
          ...{ autoRedirectOnUnauthorized: false },
        });
      } catch {
        setBubble(null);
        return;
      }
      if (!result.hit || activeElementRef.current !== target) {
        setBubble(null);
        return;
      }
      const rect = target.getBoundingClientRect();
      const masked = result.matches.map((item) => item.maskedWord).join('、');
      setBubble({
        top: Math.max(12, rect.top + window.scrollY - 10),
        left: rect.left + window.scrollX,
        text: `检测到敏感词：${masked}`,
      });
    };

    const handleInput = (event: Event) => {
      const target = event.target instanceof HTMLElement ? event.target : null;
      if (!target || !target.matches(INPUT_SELECTOR)) {
        return;
      }
      activeElementRef.current = target;
      clearPending();
      timerRef.current = window.setTimeout(() => {
        void maybeShowBubble(target);
      }, 280);
    };

    const handleFocusOut = (event: FocusEvent) => {
      if (!event.target || !(event.target instanceof HTMLElement) || !event.target.matches(INPUT_SELECTOR)) {
        return;
      }
      window.setTimeout(() => {
        if (document.activeElement !== event.target) {
          hideBubble();
        }
      }, 0);
    };

    document.addEventListener('input', handleInput, true);
    document.addEventListener('focusout', handleFocusOut, true);
    return () => {
      document.removeEventListener('input', handleInput, true);
      document.removeEventListener('focusout', handleFocusOut, true);
      hideBubble();
    };
  }, [pluginEnabled]);

  const bubbleNode = useMemo(() => {
    if (!bubble) {
      return null;
    }
    return (
      <div
        style={{
          position: 'absolute',
          top: bubble.top,
          left: bubble.left,
          transform: 'translateY(-100%)',
          background: '#fff7e6',
          color: '#ad6800',
          border: '1px solid #ffd591',
          borderRadius: 'var(--ant-border-radius)',
          padding: '6px 10px',
          fontSize: 12,
          lineHeight: 1.4,
          boxShadow: '0 8px 24px rgba(0, 0, 0, 0.08)',
          zIndex: 1200,
          pointerEvents: 'none',
          maxWidth: 320,
        }}
      >
        {bubble.text}
      </div>
    );
  }, [bubble]);

  if (!bubbleNode || typeof document === 'undefined') {
    return null;
  }
  return createPortal(bubbleNode, document.body);
};

export default GlobalSensitiveWordGuard;
