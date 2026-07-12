import { describe, expect, it } from 'vitest';
import {
  createCompetitionSettingsSearch,
  parseCompetitionSettingsNavigation,
} from './competitionSettingsNavigation';

describe('competition settings URL navigation', () => {
  it('defaults to the basic section for a URL without settings parameters', () => {
    expect(parseCompetitionSettingsNavigation('')).toEqual({
      section: 'basic',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'preliminary',
    });
  });

  it('restores the combined team and member tab from the URL', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=team-members')).toEqual({
      section: 'registration',
      registrationTab: 'TEAM_AND_MEMBER',
      stageTab: 'preliminary',
    });
  });

  it('keeps the legacy registration tab URL on project settings', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=registration')).toEqual({
      section: 'registration',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'preliminary',
    });
  });

  it('redirects the removed other-fields tab to project settings', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=other-fields')).toEqual({
      section: 'registration',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'preliminary',
    });
  });

  it('uses project settings as the registration section default', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration')).toEqual({
      section: 'registration',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'preliminary',
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
