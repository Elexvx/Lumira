import { describe, expect, it } from 'vitest';
import {
  createCompetitionSettingsSearch,
  getCompetitionSettingsStageTabFallback,
  parseCompetitionSettingsNavigation,
  type CompetitionSettingsRegistrationTab,
} from './competitionSettingsNavigation';

describe('competition settings URL navigation', () => {
  it('defaults to the basic section for a URL without settings parameters', () => {
    expect(parseCompetitionSettingsNavigation('')).toEqual({
      section: 'basic',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'timeline',
    });
  });

  it('maps the legacy combined team and member tab to team settings', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=team-members')).toEqual({
      section: 'registration',
      registrationTab: 'TEAM_FIELD',
      stageTab: 'timeline',
    });
  });

  it('keeps the legacy registration tab URL on project settings', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=registration')).toEqual({
      section: 'registration',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'timeline',
    });
  });

  it('redirects the removed other-fields tab to project settings', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=other-fields')).toEqual({
      section: 'registration',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'timeline',
    });
  });

  it('uses student settings as the registration section default', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration')).toEqual({
      section: 'registration',
      registrationTab: 'MEMBER_FIELD',
      stageTab: 'timeline',
    });
  });

  it('restores the timeline tab from the URL', () => {
    expect(parseCompetitionSettingsNavigation('?section=stages&tab=timeline')).toEqual({
      section: 'stages',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'timeline',
    });
  });

  it('restores each competition stage tab from the URL', () => {
    expect(parseCompetitionSettingsNavigation('?section=stages&tab=preliminary').stageTab).toBe('preliminary');
    expect(parseCompetitionSettingsNavigation('?section=stages&tab=final').stageTab).toBe('final');
    expect(parseCompetitionSettingsNavigation('?section=stages&tab=files').stageTab).toBe('preliminary');
  });

  it('uses the timeline as the default stage tab', () => {
    expect(parseCompetitionSettingsNavigation('?section=stages').stageTab).toBe('timeline');
  });

  it('redirects the removed review section to basic settings', () => {
    expect(parseCompetitionSettingsNavigation('?section=review')).toEqual({
      section: 'basic',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'timeline',
    });
  });

  it('does not redirect another settings section while validating stage tabs', () => {
    expect(getCompetitionSettingsStageTabFallback('basic', 'preliminary', [])).toBeUndefined();
    expect(getCompetitionSettingsStageTabFallback('payments', 'missing-stage', ['stage-1'])).toBeUndefined();
  });

  it('falls back only when the active stage tab no longer exists', () => {
    expect(getCompetitionSettingsStageTabFallback('stages', 'stage-1', ['stage-1'])).toBeUndefined();
    expect(getCompetitionSettingsStageTabFallback('stages', 'missing-stage', ['stage-1'])).toBe('timeline');
  });

  it('keeps unrelated query parameters and removes irrelevant tab values', () => {
    expect(createCompetitionSettingsSearch('?from=list&tab=timeline', 'payments')).toBe('?from=list&section=payments');
  });

  it('writes stable query values for nested settings tabs', () => {
    expect(createCompetitionSettingsSearch('', 'registration', 'MEMBER_FIELD')).toBe('?section=registration&tab=students');
    expect(createCompetitionSettingsSearch('', 'registration', 'TEAM_FIELD')).toBe('?section=registration&tab=team');
    expect(createCompetitionSettingsSearch('', 'registration', 'INTELLECTUAL_PROPERTY')).toBe('?section=registration&tab=intellectual-property');
    expect(createCompetitionSettingsSearch('', 'registration', 'EXPERT_FIELD')).toBe('?section=registration&tab=experts');
    expect(createCompetitionSettingsSearch('', 'stages', 'timeline')).toBe('?section=stages&tab=timeline');
    expect(createCompetitionSettingsSearch('', 'stages', 'preliminary')).toBe('?section=stages&tab=preliminary');
    expect(createCompetitionSettingsSearch('', 'stages', 'final')).toBe('?section=stages&tab=final');
  });

  it('round-trips every registration page while switching forward and backward', () => {
    const switchSequence: CompetitionSettingsRegistrationTab[] = [
      'MEMBER_FIELD',
      'TEAM_FIELD',
      'PROJECT_FIELD',
      'EXPERT_FIELD',
      'INTELLECTUAL_PROPERTY',
      'documents',
      'INTELLECTUAL_PROPERTY',
      'PROJECT_FIELD',
      'TEAM_FIELD',
      'MEMBER_FIELD',
    ];

    let search = '?from=settings';
    switchSequence.forEach((tab) => {
      search = createCompetitionSettingsSearch(search, 'registration', tab);
      expect(parseCompetitionSettingsNavigation(search)).toMatchObject({
        section: 'registration',
        registrationTab: tab,
      });
      expect(new URLSearchParams(search).get('from')).toBe('settings');
    });
  });
});
