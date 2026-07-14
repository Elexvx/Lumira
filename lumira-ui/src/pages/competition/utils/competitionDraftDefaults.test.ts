import { describe, expect, it } from 'vitest';
import { normalizeCompetitionDraftBasicDefaults } from './competitionDraftDefaults';

describe('competition draft basic defaults', () => {
  it('removes legacy placeholder values from an empty draft', () => {
    expect(normalizeCompetitionDraftBasicDefaults({
      status: 'draft',
      category: 'OTHER',
      organizer: 'Organizer',
      location: 'TBD',
    }, [{ role: 'Organizer', name: '' }])).toEqual({
      category: undefined,
      organizer: undefined,
      organizers: [{ role: '', name: '' }],
      participationScope: undefined,
    });
  });

  it('keeps values that were explicitly completed by the user', () => {
    expect(normalizeCompetitionDraftBasicDefaults({
      status: 'draft',
      category: 'OTHER',
      organizer: '主办方：赛事组委会',
      participationScope: '全国高校学生',
      location: '全国高校学生',
    }, [{ role: '主办方', name: '赛事组委会' }])).toEqual({
      category: 'OTHER',
      organizer: '主办方：赛事组委会',
      organizers: [{ role: '主办方', name: '赛事组委会' }],
      participationScope: '全国高校学生',
    });
  });

  it('starts a new draft organizer row empty', () => {
    expect(normalizeCompetitionDraftBasicDefaults({ status: 'draft' }, [])).toEqual({
      category: undefined,
      organizer: undefined,
      organizers: [{ role: '', name: '' }],
      participationScope: undefined,
    });
  });

  it('keeps a real legacy organizer when no organizer list exists', () => {
    expect(normalizeCompetitionDraftBasicDefaults({
      status: 'draft',
      organizer: '赛事组委会',
    }, []).organizers).toEqual([{ role: '主办方', name: '赛事组委会' }]);
  });
});
