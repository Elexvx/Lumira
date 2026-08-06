import { describe, expect, it } from 'vitest';

import {
  buildRegistrationCompetitionFallback,
  filterOpenRegistrationCompetitions,
  hasRegistrationCompetitionPricing,
  isRegistrationCompetitionOpen,
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

  it('uses the registration window to decide whether a competition is selectable', () => {
    const competition = {
      registrationStart: '2026-07-28T09:00:00',
      registrationEnd: '2026-07-28T17:00:00',
    };

    expect(isRegistrationCompetitionOpen(competition, '2026-07-28T09:00:00')).toBe(true);
    expect(isRegistrationCompetitionOpen(competition, '2026-07-28T17:00:00')).toBe(true);
    expect(isRegistrationCompetitionOpen(competition, '2026-07-28T08:59:59')).toBe(false);
    expect(isRegistrationCompetitionOpen(competition, '2026-07-28T17:00:01')).toBe(false);
    expect(isRegistrationCompetitionOpen({}, '2026-07-28T12:00:00')).toBe(false);
  });

  it('filters published options by registration time rather than competition time', () => {
    const openCompetition = {
      id: 101,
      code: 'open',
      locale: 'zh',
      title: 'Open registration',
      category: 'OTHER',
      registrationStart: '2026-07-28T09:00:00',
      registrationEnd: '2026-07-28T17:00:00',
      competitionStart: '2027-01-01T09:00:00',
      location: '',
      status: 'published' as const,
      featured: false,
      sort: 101,
    };
    const closedCompetition = {
      ...openCompetition,
      id: 102,
      code: 'closed',
      title: 'Closed registration',
      registrationEnd: '2026-07-28T08:00:00',
    };

    expect(filterOpenRegistrationCompetitions(
      [openCompetition, closedCompetition],
      '2026-07-28T12:00:00',
    )).toEqual([openCompetition]);
  });
});
