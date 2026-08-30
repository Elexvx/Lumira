import { describe, expect, it } from 'vitest';
import {
  competitionSettingsMenuItems,
  competitionSettingsRegistrationTabItems,
  createCompetitionSettingsSearch,
  getCompetitionSettingsStageTabFallback,
  parseCompetitionSettingsNavigation,
  type CompetitionSettingsRegistrationTab,
} from './competitionSettingsNavigation';

describe('competition settings URL navigation', () => {
  it('keeps the requested sidebar order and registration tab order', () => {
    expect(competitionSettingsMenuItems.map((item) => item.key)).toEqual([
      'basic',
      'notice',
      'registration',
      'experts',
      'stages',
      'payments',
      'awards',
      'danger',
    ]);
    expect(competitionSettingsMenuItems.map((item) => item.label)).toEqual([
      '基础信息',
      '赛事须知',
      '报名设置',
      '专家设置',
      '赛程与材料',
      '费用设置',
      '获奖设置',
      '危险操作',
    ]);
    expect(competitionSettingsMenuItems.at(-1)).toMatchObject({
      key: 'danger',
      className: 'competition-settings-sidebar__danger-item',
    });
    expect(competitionSettingsRegistrationTabItems.map((item) => item.key)).toEqual([
      'PROJECT_FIELD',
      'TEAM_FIELD',
      'MEMBER_FIELD',
      'TEACHER_FIELD',
      'INTELLECTUAL_PROPERTY',
    ]);
  });

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

  it('uses project settings as the registration section default', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration')).toEqual({
      section: 'registration',
      registrationTab: 'PROJECT_FIELD',
      stageTab: 'timeline',
    });
  });

  it('maps the removed student tab to the independent student settings page', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=students')).toEqual({
      section: 'registration',
      registrationTab: 'MEMBER_FIELD',
      stageTab: 'timeline',
    });
  });

  it('moves legacy expert and document tabs to their independent sections', () => {
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=experts')).toMatchObject({
      section: 'experts',
    });
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=expert-fields')).toMatchObject({
      section: 'experts',
    });
    expect(parseCompetitionSettingsNavigation('?section=registration&tab=documents')).toMatchObject({
      section: 'notice',
    });
  });

  it('keeps legacy tab-only links on the matching settings page', () => {
    expect(parseCompetitionSettingsNavigation('?tab=experts').section).toBe('experts');
    expect(parseCompetitionSettingsNavigation('?tab=documents').section).toBe('notice');
    expect(parseCompetitionSettingsNavigation('?tab=team').registrationTab).toBe('TEAM_FIELD');
    expect(parseCompetitionSettingsNavigation('?tab=students').registrationTab).toBe('MEMBER_FIELD');
    expect(parseCompetitionSettingsNavigation('?tab=teachers').registrationTab).toBe('TEACHER_FIELD');
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

  it('writes stable query values for registration tabs and independent sections', () => {
    expect(createCompetitionSettingsSearch('', 'registration', 'PROJECT_FIELD')).toBe('?section=registration&tab=project');
    expect(createCompetitionSettingsSearch('', 'registration', 'TEAM_FIELD')).toBe('?section=registration&tab=team');
    expect(createCompetitionSettingsSearch('', 'registration', 'MEMBER_FIELD')).toBe('?section=registration&tab=students');
    expect(createCompetitionSettingsSearch('', 'registration', 'TEACHER_FIELD')).toBe('?section=registration&tab=teachers');
    expect(createCompetitionSettingsSearch('', 'registration', 'INTELLECTUAL_PROPERTY')).toBe('?section=registration&tab=intellectual-property');
    expect(createCompetitionSettingsSearch('', 'experts')).toBe('?section=experts');
    expect(createCompetitionSettingsSearch('', 'notice')).toBe('?section=notice');
    expect(createCompetitionSettingsSearch('', 'danger')).toBe('?section=danger');
    expect(createCompetitionSettingsSearch('', 'stages', 'timeline')).toBe('?section=stages&tab=timeline');
    expect(createCompetitionSettingsSearch('', 'stages', 'preliminary')).toBe('?section=stages&tab=preliminary');
    expect(createCompetitionSettingsSearch('', 'stages', 'final')).toBe('?section=stages&tab=final');
  });

  it('round-trips every registration page while switching forward and backward', () => {
    const switchSequence: CompetitionSettingsRegistrationTab[] = [
      'PROJECT_FIELD',
      'TEAM_FIELD',
      'MEMBER_FIELD',
      'TEACHER_FIELD',
      'INTELLECTUAL_PROPERTY',
      'INTELLECTUAL_PROPERTY',
      'PROJECT_FIELD',
      'TEAM_FIELD',
      'TEAM_FIELD',
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
