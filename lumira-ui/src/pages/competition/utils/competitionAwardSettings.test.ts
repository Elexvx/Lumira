import { describe, expect, it } from 'vitest';
import {
  DEFAULT_COMPETITION_AWARD_RULES,
  DEFAULT_COMPETITION_AWARD_SETTINGS,
  buildCompetitionAwardSettingsItem,
  getCompetitionAwardRules,
  getCompetitionAwardSettings,
  normalizeCompetitionAwardSettings,
  toCompetitionAwardRankRules,
} from './competitionAwardSettings';

describe('competition award settings', () => {
  it('computes sequential rank ranges from the default grouped settings', () => {
    expect(toCompetitionAwardRankRules(DEFAULT_COMPETITION_AWARD_SETTINGS)).toEqual([
      { awardName: '一等奖', quota: 1, quotaType: 'FIXED', minRank: 1, maxRank: 1 },
      { awardName: '二等奖', quota: 2, quotaType: 'FIXED', minRank: 2, maxRank: 3 },
      { awardName: '三等奖', quota: 3, quotaType: 'FIXED', minRank: 4, maxRank: 6 },
      { awardName: '优秀奖', quota: 5, quotaType: 'FIXED', minRank: 7, maxRank: 11 },
    ]);
  });

  it('uses one main quota type for main and excellence awards and keeps special types independent', () => {
    expect(toCompetitionAwardRankRules({
      version: 2,
      mainQuotaType: 'PERCENTAGE',
      mainAwards: [
        { awardName: '一等奖', quota: 10 },
        { awardName: '二等奖', quota: 20 },
        { awardName: '三等奖', quota: 8 },
      ],
      excellenceEnabled: true,
      excellenceQuota: 5,
      specialAwards: [{ id: 'special-a', awardName: '创新专项奖', quotaType: 'FIXED', quota: 2 }],
    }, 25)).toEqual([
      { awardName: '一等奖', quota: 3, quotaType: 'PERCENTAGE', minRank: 1, maxRank: 3 },
      { awardName: '二等奖', quota: 5, quotaType: 'PERCENTAGE', minRank: 4, maxRank: 8 },
      { awardName: '三等奖', quota: 2, quotaType: 'PERCENTAGE', minRank: 9, maxRank: 10 },
      { awardName: '优秀奖', quota: 2, quotaType: 'PERCENTAGE', minRank: 11, maxRank: 12 },
      { awardName: '创新专项奖', quota: 2, quotaType: 'FIXED', minRank: 13, maxRank: 14 },
    ]);
  });

  it('omits disabled excellence and preserves special award order', () => {
    const settings = normalizeCompetitionAwardSettings({
      version: 2,
      mainQuotaType: 'FIXED',
      mainAwards: [
        { awardName: '一等奖', quota: 1 },
        { awardName: '二等奖', quota: 2 },
        { awardName: '三等奖', quota: 3 },
      ],
      excellence: { enabled: false, quota: 5 },
      specialAwards: [
        { id: 'special-b', awardName: '专项乙', quotaType: 'PERCENTAGE', quota: 10 },
        { id: 'special-a', awardName: '专项甲', quotaType: 'FIXED', quota: 4 },
      ],
    });

    expect(settings.excellenceEnabled).toBe(false);
    expect(settings.specialAwards.map((award) => award.awardName)).toEqual(['专项乙', '专项甲']);
    expect(toCompetitionAwardRankRules(settings, 20).map(({ awardName, minRank, maxRank }) => ({ awardName, minRank, maxRank }))).toEqual([
      { awardName: '一等奖', minRank: 1, maxRank: 1 },
      { awardName: '二等奖', minRank: 2, maxRank: 3 },
      { awardName: '三等奖', minRank: 4, maxRank: 6 },
      { awardName: '专项乙', minRank: 7, maxRank: 8 },
      { awardName: '专项甲', minRank: 9, maxRank: 12 },
    ]);
  });

  it('normalizes legacy mixed modes to fixed quantities', () => {
    const item = buildCompetitionAwardSettingsItem(undefined, [
      { awardName: '一等奖', quotaType: 'PERCENTAGE', quota: 10 },
      { awardName: '二等奖', quotaType: 'FIXED', quota: 2 },
      { awardName: '三等奖', quotaType: 'PERCENTAGE', quota: 20 },
      { awardName: '优秀奖', quotaType: 'FIXED', quota: 5 },
    ]);

    expect(getCompetitionAwardSettings([item])).toMatchObject({
      mainQuotaType: 'FIXED',
      mainAwards: [
        { awardName: '一等奖', quota: 10 },
        { awardName: '二等奖', quota: 2 },
        { awardName: '三等奖', quota: 20 },
      ],
      excellenceEnabled: true,
      excellenceQuota: 5,
    });
  });

  it('keeps quota-only legacy rules as fixed settings and defaults missing configuration', () => {
    expect(getCompetitionAwardRules([{
      itemType: 'AWARD_SETTINGS',
      itemKey: 'award-rules',
      title: '获奖设置',
      contentJson: JSON.stringify({ rules: [{ awardName: '一等奖', quota: 2 }] }),
    }])).toEqual([
      { awardName: '一等奖', quotaType: 'FIXED', quota: 2 },
      { awardName: '二等奖', quotaType: 'FIXED', quota: 2 },
      { awardName: '三等奖', quotaType: 'FIXED', quota: 3 },
      { awardName: '优秀奖', quotaType: 'FIXED', quota: 5 },
    ]);
    expect(getCompetitionAwardRules([])).toEqual(DEFAULT_COMPETITION_AWARD_RULES);
  });
});
