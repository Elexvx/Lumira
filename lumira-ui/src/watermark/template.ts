import type { CurrentUser } from '@/types/api';
import { maskEmail, maskMobile } from '@/utils/sensitive';

export const WATERMARK_TEMPLATE_VARIABLES = [
  { name: 'username', token: '{{username}}' },
  { name: 'nickname', token: '{{nickname}}' },
  { name: 'mobile', token: '{{mobile}}' },
  { name: 'email', token: '{{email}}' },
  { name: 'realName', token: '{{realName}}' },
  { name: 'userId', token: '{{userId}}' },
] as const;

export type WatermarkTemplateVariableName = (typeof WATERMARK_TEMPLATE_VARIABLES)[number]['name'];

const ALLOWED_VARIABLE_NAMES = new Set<string>(WATERMARK_TEMPLATE_VARIABLES.map(({ name }) => name));
const WATERMARK_TEMPLATE_TOKEN_PATTERN = /\{\{([A-Za-z][A-Za-z0-9_]*)\}\}/g;

export const normalizeWatermarkTextLines = (value: unknown): string[] => {
  const raw = Array.isArray(value)
    ? value.filter((line): line is string => typeof line === 'string').join('\n')
    : typeof value === 'string'
      ? value
      : '';

  return raw
    .replace(/\\n/g, '\n')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
};

export const insertWatermarkTemplateToken = (
  value: unknown,
  token: string,
  selectionStart?: number,
  selectionEnd?: number,
) => {
  const text = normalizeWatermarkTextLines(value).join('\n');
  const start = Math.max(0, Math.min(selectionStart ?? text.length, text.length));
  const end = Math.max(start, Math.min(selectionEnd ?? start, text.length));
  const nextText = `${text.slice(0, start)}${token}${text.slice(end)}`;
  return {
    lines: normalizeWatermarkTextLines(nextText),
    cursor: start + token.length,
  };
};

const containsBrace = (value: string) => value.includes('{') || value.includes('}');

export const isValidWatermarkTemplateLines = (value: unknown): boolean => {
  const lines = normalizeWatermarkTextLines(value);
  for (const line of lines) {
    const tokenPattern = new RegExp(WATERMARK_TEMPLATE_TOKEN_PATTERN.source, 'g');
    let cursor = 0;
    let match: RegExpExecArray | null;
    while ((match = tokenPattern.exec(line)) !== null) {
      if (containsBrace(line.slice(cursor, match.index)) || !ALLOWED_VARIABLE_NAMES.has(match[1])) {
        return false;
      }
      cursor = tokenPattern.lastIndex;
    }
    if (containsBrace(line.slice(cursor))) {
      return false;
    }
  }
  return true;
};

const getTemplateValues = (user: CurrentUser): Record<WatermarkTemplateVariableName, string> => ({
  username: user.username?.trim() || '',
  nickname: user.nickname?.trim() || '',
  mobile: maskMobile(user.mobile),
  email: maskEmail(user.email),
  realName: user.realName?.trim() || '',
  userId: user.userId > 0 ? String(user.userId) : '',
});

/**
 * Resolves a saved template only in memory. A missing referenced field makes
 * the complete personalized template invalid so callers can use the static
 * visitor text without rendering partial identity data.
 */
export const resolveWatermarkTextLines = (value: unknown, user?: CurrentUser | null): string[] => {
  const lines = normalizeWatermarkTextLines(value);
  if (!user || !lines.length || !isValidWatermarkTemplateLines(lines)) {
    return [];
  }

  const values = getTemplateValues(user);
  const resolvedLines: string[] = [];
  for (const line of lines) {
    let missingValue = false;
    const resolved = line.replace(WATERMARK_TEMPLATE_TOKEN_PATTERN, (_, name: WatermarkTemplateVariableName) => {
      const replacement = values[name];
      if (!replacement) {
        missingValue = true;
      }
      return replacement || '';
    }).trim();
    if (missingValue) {
      return [];
    }
    if (resolved) {
      resolvedLines.push(resolved);
    }
  }
  return resolvedLines;
};
