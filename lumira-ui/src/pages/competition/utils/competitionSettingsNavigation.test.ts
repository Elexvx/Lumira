import { describe, expect, it } from 'vitest';
import {
  createCompetitionSettingsSearch,
  parseCompetitionSettingsNavigation,
} from './competitionSettingsNavigation';

describe('competition settings URL navigation', () => {
  it('defaults to the basic section for a URL without settings parameters', () => {
    expect(parseCompetitionSettingsNavigation('')).toEqual({
      section: 'basic',
      registrationTab: 'REGISTRATION_FIELD',
      stageTab: 'files',
    });
  });

  it('restores the combined team and member tab from the URL', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=team-members')).toEqual({
      section: 'registration',
      registrationTab: 'TEAM_AND_MEMBER',
      stageTab: 'files',
    });
  });

  it('restores the timeline tab from the URL', () => {
    expect(parseCompetitionSettingsNavigation('?section=stages&tab=timeline')).toEqual({
      section: 'stages',
      registrationTab: 'REGISTRATION_FIELD',
      stageTab: 'timeline',
    });
  });

  it('keeps unrelated query parameters and removes irrelevant tab values', () => {
    expect(createCompetitionSettingsSearch('?from=list&tab=timeline', 'payments')).toBe('?from=list&section=payments');
  });

  it('writes stable query values for nested settings tabs', () => {
    expect(createCompetitionSettingsSearch('', 'registration', 'TEAM_AND_MEMBER')).toBe('?section=registration&tab=team-members');
    expect(createCompetitionSettingsSearch('', 'stages', 'timeline')).toBe('?section=stages&tab=timeline');
  });
});
