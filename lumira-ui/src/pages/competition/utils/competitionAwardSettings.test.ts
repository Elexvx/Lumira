import { describe, expect, it } from 'vitest';
import {
  DEFAULT_COMPETITION_AWARD_RULES,
  buildCompetitionAwardSettingsItem,
  getCompetitionAwardRules,
  toCompetitionAwardRankRules,
} from './competitionAwardSettings';

describe('competition award settings', () => {
  it('computes sequential rank ranges from the four award quotas', () => {
    expect(toCompetitionAwardRankRules(DEFAULT_COMPETITION_AWARD_RULES)).toEqual([
      { awardName: '一等奖', quota: 1, minRank: 1, maxRank: 1 },
      { awardName: '二等奖', quota: 2, minRank: 2, maxRank: 3 },
      { awardName: '三等奖', quota: 3, minRank: 4, maxRank: 6 },
      { awardName: '优秀奖', quota: 5, minRank: 7, maxRank: 11 },
    ]);
  });

  it('reads configured quotas and falls back safely for incomplete content', () => {
    const item = buildCompetitionAwardSettingsItem(undefined, [
      { awardName: '自定义奖项', quota: 2 },
      { awardName: '二等奖', quota: 4 },
      { awardName: '三等奖', quota: 6 },
      { awardName: '优秀奖', quota: 8 },
    ]);

    expect(getCompetitionAwardRules([item])).toEqual([
      { awardName: '一等奖', quota: 2 },
      { awardName: '二等奖', quota: 4 },
      { awardName: '三等奖', quota: 6 },
      { awardName: '优秀奖', quota: 8 },
    ]);
    expect(getCompetitionAwardRules([])).toEqual(DEFAULT_COMPETITION_AWARD_RULES);
  });
});
