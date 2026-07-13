import { describe, expect, it } from 'vitest';
import {
  createCompetitionSettingsSearch,
  getCompetitionSettingsStageTabFallback,
  parseCompetitionSettingsNavigation,
} from './competitionSettingsNavigation';

describe('competition settings URL navigation', () => {
  it('defaults to the basic section for a URL without settings parameters', () => {
    expect(parseCompetitionSettingsNavigation('')).toEqual({
      section: 'basic',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'timeline',
    });
  });

  it('restores the combined team and member tab from the URL', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=team-members')).toEqual({
      section: 'registration',
      registrationTab: 'TEAM_AND_MEMBER',
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

  it('uses project settings as the registration section default', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration')).toEqual({
      section: 'registration',
      registrationTab: 'PROJECT_FIELD',
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

  it('does not redirect another settings section while validating stage tabs', () => {
    expect(getCompetitionSettingsStageTabFallback('review', 'preliminary', [])).toBeUndefined();
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
    expect(createCompetitionSettingsSearch('', 'registration', 'TEAM_AND_MEMBER')).toBe('?section=registration&tab=team-members');
    expect(createCompetitionSettingsSearch('', 'registration', 'INTELLECTUAL_PROPERTY')).toBe('?section=registration&tab=intellectual-property');
    expect(createCompetitionSettingsSearch('', 'stages', 'timeline')).toBe('?section=stages&tab=timeline');
    expect(createCompetitionSettingsSearch('', 'stages', 'preliminary')).toBe('?section=stages&tab=preliminary');
    expect(createCompetitionSettingsSearch('', 'stages', 'final')).toBe('?section=stages&tab=final');
  });
});
