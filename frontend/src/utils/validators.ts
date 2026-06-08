import { resolveBuiltinMessage } from '@/i18n/messages';

const CHINA_MOBILE_PATTERN = /^1[3-9]\d{9}$/;
const CHINA_ID_CARD_PATTERN = /^(?:\d{15}|\d{17}[\dXx])$/;

const CHINA_ID_CARD_WEIGHTS = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
const CHINA_ID_CARD_CHECK_CODES = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'];

export const trimString = (value: unknown) => (typeof value === 'string' ? value.trim() : value);

export const validateOptionalChinaMobile = async (_: unknown, value?: string) => {
  const normalizedValue = typeof value === 'string' ? value.trim() : '';
  if (!normalizedValue) {
    return;
  }
  if (!CHINA_MOBILE_PATTERN.test(normalizedValue)) {
    throw new Error(resolveBuiltinMessage('common.invalidMobile', '请输入有效手机号'));
  }
};

export const validateOptionalChinaIdCard = async (_: unknown, value?: string) => {
  const normalizedValue = typeof value === 'string' ? value.trim().toUpperCase() : '';
  if (!normalizedValue) {
    return;
  }

  if (normalizedValue.length === 15) {
    if (!CHINA_ID_CARD_PATTERN.test(normalizedValue)) {
      throw new Error(resolveBuiltinMessage('common.invalidIdCard', '请输入有效身份证号码'));
    }
    return;
  }

  if (!CHINA_ID_CARD_PATTERN.test(normalizedValue)) {
    throw new Error(resolveBuiltinMessage('common.invalidIdCard', '请输入有效身份证号码'));
  }

  const checksum = normalizedValue
    .slice(0, 17)
    .split('')
    .reduce((sum, char, index) => sum + Number(char) * CHINA_ID_CARD_WEIGHTS[index], 0);
  const checkCode = CHINA_ID_CARD_CHECK_CODES[checksum % 11];
  if (checkCode !== normalizedValue.slice(-1)) {
    throw new Error(resolveBuiltinMessage('common.invalidIdCard', '请输入有效身份证号码'));
  }
};
