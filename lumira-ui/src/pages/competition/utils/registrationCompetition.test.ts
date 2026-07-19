import { describe, expect, it } from 'vitest';

import {
  buildRegistrationCompetitionFallback,
  hasRegistrationCompetitionPricing,
  mergeRegistrationCompetitionOptions,
} from './registrationCompetition';

describe('registrationCompetition helpers', () => {
  it('builds a fallback competition from persisted draft metadata', () => {
    expect(buildRegistrationCompetitionFallback(103, {
      competitionUuid: 'competition-uuid-103',
      competitionTitle: 'AI Challenge 2026',
    })).toMatchObject({
      id: 103,
      uuid: 'competition-uuid-103',
      title: 'AI Challenge 2026',
      code: 'competition-uuid-103',
    });
  });

  it('returns undefined when draft metadata cannot identify the competition', () => {
    expect(buildRegistrationCompetitionFallback(103, {})).toBeUndefined();
    expect(buildRegistrationCompetitionFallback(undefined, {
      competitionUuid: 'competition-uuid-103',
      competitionTitle: 'AI Challenge 2026',
    })).toBeUndefined();
  });

  it('appends the fallback competition only when the selected record is missing from the loaded options', () => {
    const options = [
      {
        id: 101,
        uuid: 'competition-uuid-101',
        code: 'comp-101',
        locale: 'zh',
        title: 'Competition 101',
        category: 'OTHER',
        competitionStart: '',
        location: '',
        status: 'published' as const,
        featured: false,
        sort: 101,
      },
    ];
    const fallback = buildRegistrationCompetitionFallback(103, {
      competitionUuid: 'competition-uuid-103',
      competitionTitle: 'Competition 103',
    });

    expect(mergeRegistrationCompetitionOptions(options, fallback)).toHaveLength(2);
    expect(mergeRegistrationCompetitionOptions([...options, fallback!], fallback)).toHaveLength(2);
  });

  it('preserves pricing metadata in a restored competition fallback', () => {
    const fallback = buildRegistrationCompetitionFallback(103, {
      competitionUuid: 'competition-uuid-103',
      competitionTitle: 'Competition 103',
      feeMode: 'MEMBER',
      entryFeeMinor: 1234,
      currency: 'CNY',
    });

    expect(fallback).toMatchObject({ feeMode: 'MEMBER', entryFeeMinor: 1234, currency: 'CNY' });
    expect(hasRegistrationCompetitionPricing(fallback)).toBe(true);
  });

  it('does not treat an incomplete restored competition as priced', () => {
    expect(hasRegistrationCompetitionPricing(buildRegistrationCompetitionFallback(103, {
      competitionUuid: 'competition-uuid-103',
      competitionTitle: 'Competition 103',
    }))).toBe(false);
  });
});
