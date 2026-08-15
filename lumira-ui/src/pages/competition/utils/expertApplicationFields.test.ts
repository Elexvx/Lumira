import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';
import type { CompetitionConfigItem } from '@/services/competition/types';
import {
  buildExpertApplicationPayload,
  parseExpertApplicationFields,
  resolveExpertStandardFieldKey,
} from './expertApplicationFields';

const item = (itemKey: string, title: string, contentJson: string, requiredFlag = false): CompetitionConfigItem => ({
  itemType: 'EXPERT_FIELD',
  itemKey,
  title,
  contentJson,
  requiredFlag,
  enabled: true,
});

describe('competition expert application fields', () => {
  it('reads enabled fields in the configured order and drops disabled fields', () => {
    const fields = parseExpertApplicationFields([
      { ...item('bio', '简介', '{"fieldType":"TEXTAREA"}'), sortOrder: 20 },
      { ...item('name', '姓名', '{"fieldType":"TEXT"}', true), sortOrder: 10 },
      { ...item('retired', '旧字段', '{"fieldType":"TEXT"}'), enabled: false, sortOrder: 0 },
    ]);

    expect(fields.map((field) => field.itemKey)).toEqual(['name', 'bio']);
    expect(fields[0]).toMatchObject({ title: '姓名', required: true, validationRule: 'PERSON_NAME' });
  });

  it('falls back to a safe minimum form when a legacy competition has no expert fields', () => {
    expect(parseExpertApplicationFields([]).map((field) => field.itemKey)).toContain('name');
    expect(parseExpertApplicationFields([]).map((field) => field.itemKey)).toContain('expertise');
  });

  it('maps standard aliases and preserves configured dynamic values in the application snapshot', () => {
    expect(resolveExpertStandardFieldKey('expert-name')).toBe('name');
    const fields = parseExpertApplicationFields([
      item('expert-name', '专家姓名', '{"fieldType":"TEXT"}', true),
      item('expertise', '专业领域', '{"fieldType":"MULTI_SELECT"}', true),
      item('availableFrom', '可参与时间', '{"fieldType":"DATE"}'),
    ]);
    const payload = buildExpertApplicationPayload(fields, {
      'expert-name': 'Ada',
      expertise: ['AI', 'Robotics'],
      availableFrom: dayjs('2026-08-15'),
    }, 'competition-uuid');

    expect(payload).toMatchObject({
      competitionUuid: 'competition-uuid',
      name: 'Ada',
      expertise: 'AI,Robotics',
    });
    expect(payload.extraValues).toEqual({
      'expert-name': 'Ada',
      expertise: ['AI', 'Robotics'],
      availableFrom: '2026-08-15',
    });
  });
});
