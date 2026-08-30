import { describe, expect, it } from 'vitest';
import type { CurrentUser } from '@/types/api';
import { insertWatermarkTemplateToken, isValidWatermarkTemplateLines, normalizeWatermarkTextLines, resolveWatermarkTextLines } from './template';

const user = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  userId: 42,
  username: 'alice',
  nickname: 'Alice',
  realName: 'Alice Chen',
  mobile: '13812345678',
  email: 'alice@example.com',
  ...overrides,
} as CurrentUser);

describe('watermark template', () => {
  it('resolves combinations with prefixes, suffixes, and custom separators', () => {
    expect(resolveWatermarkTextLines([
      '账号={{username}} | 昵称={{nickname}} | 手机={{mobile}} | 邮箱={{email}}',
      '【{{realName}}】/{{userId}}',
    ], user())).toEqual([
      '账号=alice | 昵称=Alice | 手机=138****5678 | 邮箱=a***e@example.com',
      '【Alice Chen】/42',
    ]);
  });

  it('masks mobile and email values without changing configured template text', () => {
    expect(resolveWatermarkTextLines(['{{mobile}}', '{{email}}'], user())).toEqual([
      '138****5678',
      'a***e@example.com',
    ]);
  });

  it('falls back by returning no personalized lines when a referenced field is missing', () => {
    expect(resolveWatermarkTextLines(['用户：{{nickname}}'], user({ nickname: '' }))).toEqual([]);
    expect(resolveWatermarkTextLines(['用户：{{username}}'], null)).toEqual([]);
  });

  it('accepts historical literal newlines while normalizing form values', () => {
    expect(normalizeWatermarkTextLines('第一行\\n第二行\n第三行')).toEqual(['第一行', '第二行', '第三行']);
  });

  it('inserts a selected variable at the textarea cursor', () => {
    expect(insertWatermarkTemplateToken(['用户：Alice'], '{{username}}', 3, 8)).toEqual({
      lines: ['用户：{{username}}'],
      cursor: 15,
    });
  });

  it('rejects unknown and malformed placeholders', () => {
    expect(isValidWatermarkTemplateLines(['{{username}} {{userId}}'])).toBe(true);
    expect(isValidWatermarkTemplateLines(['{{phone}}'])).toBe(false);
    expect(isValidWatermarkTemplateLines(['{{username}'])).toBe(false);
    expect(isValidWatermarkTemplateLines(['用户 {username}'])).toBe(false);
    expect(resolveWatermarkTextLines(['{{phone}}'], user())).toEqual([]);
  });
});
