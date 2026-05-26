export type LoginInputKind = 'account' | 'mobile' | 'email' | 'verificationCode';

type KeyEventLike = {
  altKey?: boolean;
  ctrlKey?: boolean;
  isComposing?: boolean;
  key?: string;
  keyCode?: number;
  metaKey?: boolean;
};

type PasteEventLike = {
  clipboardData?: {
    getData: (type: string) => string;
  };
};

const UNSAFE_ACCOUNT_CHARS_PATTERN = /[\s<>"'`;\\()[\]{}]/g;
const MOBILE_UNSAFE_CHARS_PATTERN = /\D/g;
const VERIFICATION_CODE_UNSAFE_CHARS_PATTERN = /[^A-Za-z0-9]/g;

const coerceInputValue = (value: unknown) => String(value ?? '');

export const sanitizeLoginInputValue = (value: unknown, kind: LoginInputKind) => {
  const text = coerceInputValue(value);

  if (kind === 'mobile') {
    return text.replace(MOBILE_UNSAFE_CHARS_PATTERN, '').slice(0, 11);
  }

  if (kind === 'verificationCode') {
    return text.replace(VERIFICATION_CODE_UNSAFE_CHARS_PATTERN, '').slice(0, 12);
  }

  return text.replace(UNSAFE_ACCOUNT_CHARS_PATTERN, '').slice(0, 128);
};

export const getLoginInputValueFromEvent =
  (kind: LoginInputKind) =>
  (event: { target?: { value?: unknown } } | string | number | null | undefined) => {
    if (typeof event === 'string' || typeof event === 'number') {
      return sanitizeLoginInputValue(event, kind);
    }

    return sanitizeLoginInputValue(event?.target?.value, kind);
  };

export const shouldBlockLoginInputKey = (kind: LoginInputKind, event: KeyEventLike) => {
  if (event.isComposing || event.keyCode === 229) {
    return kind === 'mobile' || kind === 'verificationCode';
  }

  if (event.ctrlKey || event.altKey || event.metaKey) {
    return false;
  }

  if (!event.key || event.key.length !== 1) {
    return false;
  }

  return sanitizeLoginInputValue(event.key, kind) !== event.key;
};

export const shouldBlockLoginInputPaste = (kind: LoginInputKind, event: PasteEventLike) => {
  const text = event.clipboardData?.getData('text') ?? '';
  return sanitizeLoginInputValue(text, kind) !== text;
};

export const rejectUnsafeLoginInput = async (_: unknown, value: unknown, kind: LoginInputKind, message: string) => {
  if (coerceInputValue(value) !== sanitizeLoginInputValue(value, kind)) {
    throw new Error(message);
  }
};
