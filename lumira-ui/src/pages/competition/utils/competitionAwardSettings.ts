import type { CompetitionConfigItem } from '@/services/competition/types';

export const COMPETITION_AWARD_SETTINGS_ITEM_TYPE = 'AWARD_SETTINGS' as const;
export const COMPETITION_AWARD_SETTINGS_ITEM_KEY = 'award-rules';

export type CompetitionAwardRuleSetting = {
  awardName: string;
  quota: number;
};

export type CompetitionAwardRankRule = CompetitionAwardRuleSetting & {
  minRank: number;
  maxRank: number;
};

export const DEFAULT_COMPETITION_AWARD_RULES: CompetitionAwardRuleSetting[] = [
  { awardName: '一等奖', quota: 1 },
  { awardName: '二等奖', quota: 2 },
  { awardName: '三等奖', quota: 3 },
  { awardName: '优秀奖', quota: 5 },
];

const normalizeQuota = (value: unknown, fallback: number) => {
  const quota = Number(value);
  return Number.isInteger(quota) && quota > 0 ? quota : fallback;
};

const parseAwardSettingsContent = (contentJson?: string | null): unknown => {
  if (!contentJson) {
    return undefined;
  }
  try {
    return JSON.parse(contentJson);
  } catch {
    return undefined;
  }
};

export const normalizeCompetitionAwardRules = (value: unknown): CompetitionAwardRuleSetting[] => {
  const rules = Array.isArray(value) ? value : [];
  return DEFAULT_COMPETITION_AWARD_RULES.map((fallback, index) => {
    const candidate = rules[index];
    if (!candidate || typeof candidate !== 'object') {
      return { ...fallback };
    }
    const record = candidate as Record<string, unknown>;
    return {
      awardName: fallback.awardName,
      quota: normalizeQuota(record.quota, fallback.quota),
    };
  });
};

export const getCompetitionAwardRules = (items?: CompetitionConfigItem[]): CompetitionAwardRuleSetting[] => {
  const item = items?.find((candidate) =>
    candidate.itemType === COMPETITION_AWARD_SETTINGS_ITEM_TYPE
    && candidate.itemKey === COMPETITION_AWARD_SETTINGS_ITEM_KEY,
  );
  const parsed = parseAwardSettingsContent(item?.contentJson);
  const value = parsed && typeof parsed === 'object' && !Array.isArray(parsed)
    ? (parsed as Record<string, unknown>).rules
    : parsed;
  return normalizeCompetitionAwardRules(value);
};

export const toCompetitionAwardRankRules = (rules: CompetitionAwardRuleSetting[]): CompetitionAwardRankRule[] => {
  let nextRank = 1;
  return rules.map((rule) => {
    const quota = normalizeQuota(rule.quota, 1);
    const minRank = nextRank;
    const maxRank = nextRank + quota - 1;
    nextRank = maxRank + 1;
    return { ...rule, quota, minRank, maxRank };
  });
};

export const buildCompetitionAwardSettingsItem = (
  existing: CompetitionConfigItem | undefined,
  rules: CompetitionAwardRuleSetting[],
): CompetitionConfigItem => ({
  ...existing,
  itemType: COMPETITION_AWARD_SETTINGS_ITEM_TYPE,
  itemKey: COMPETITION_AWARD_SETTINGS_ITEM_KEY,
  title: existing?.title || '获奖设置',
  contentJson: JSON.stringify({ version: 1, rules: normalizeCompetitionAwardRules(rules) }),
  contentText: existing?.contentText || '按评审发布结果的最终排名生成获奖名单。',
  sortOrder: existing?.sortOrder ?? 600,
  requiredFlag: true,
  enabled: true,
});
