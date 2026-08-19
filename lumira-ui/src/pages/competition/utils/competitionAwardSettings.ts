import type { CompetitionConfigItem } from '@/services/competition/types';

export const COMPETITION_AWARD_SETTINGS_ITEM_TYPE = 'AWARD_SETTINGS' as const;
export const COMPETITION_AWARD_SETTINGS_ITEM_KEY = 'award-rules';

export const MAIN_COMPETITION_AWARD_NAMES = ['一等奖', '二等奖', '三等奖'] as const;
export const EXCELLENCE_COMPETITION_AWARD_NAME = '优秀奖';

export type CompetitionAwardQuotaType = 'FIXED' | 'PERCENTAGE';

export type CompetitionAwardMainAward = {
  awardName: string;
  quota: number;
};

export type CompetitionAwardSpecialAward = {
  id: string;
  awardName: string;
  quota: number;
  quotaType: CompetitionAwardQuotaType;
};

export type CompetitionAwardSettings = {
  version: 2;
  mainQuotaType: CompetitionAwardQuotaType;
  mainAwards: CompetitionAwardMainAward[];
  excellenceEnabled: boolean;
  excellenceQuota: number;
  specialAwards: CompetitionAwardSpecialAward[];
};

export type CompetitionAwardRuleSetting = {
  awardName: string;
  quota: number;
  quotaType: CompetitionAwardQuotaType;
};

export type CompetitionAwardRankRule = CompetitionAwardRuleSetting & {
  minRank: number;
  maxRank: number;
};

export const DEFAULT_COMPETITION_AWARD_SETTINGS: CompetitionAwardSettings = {
  version: 2,
  mainQuotaType: 'FIXED',
  mainAwards: [
    { awardName: '一等奖', quota: 1 },
    { awardName: '二等奖', quota: 2 },
    { awardName: '三等奖', quota: 3 },
  ],
  excellenceEnabled: true,
  excellenceQuota: 5,
  specialAwards: [],
};

export const DEFAULT_COMPETITION_AWARD_RULES: CompetitionAwardRuleSetting[] = [
  ...DEFAULT_COMPETITION_AWARD_SETTINGS.mainAwards.map((award) => ({
    ...award,
    quotaType: DEFAULT_COMPETITION_AWARD_SETTINGS.mainQuotaType,
  })),
  {
    awardName: EXCELLENCE_COMPETITION_AWARD_NAME,
    quota: DEFAULT_COMPETITION_AWARD_SETTINGS.excellenceQuota,
    quotaType: DEFAULT_COMPETITION_AWARD_SETTINGS.mainQuotaType,
  },
];

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const isQuotaType = (value: unknown): value is CompetitionAwardQuotaType =>
  value === 'FIXED' || value === 'PERCENTAGE';

const asQuotaType = (value: unknown, fallback: CompetitionAwardQuotaType): CompetitionAwardQuotaType =>
  isQuotaType(value) ? value : fallback;

const normalizeQuota = (value: unknown, fallback: number, quotaType: CompetitionAwardQuotaType) => {
  const quota = Number(value);
  if (!Number.isFinite(quota)) {
    return fallback;
  }
  if (quotaType === 'PERCENTAGE') {
    return Number.isInteger(quota) && quota >= 1 && quota <= 100 ? quota : fallback;
  }
  return Number.isInteger(quota) && quota > 0 ? quota : fallback;
};

const createSpecialAwardId = (index: number) => `special-${index + 1}`;

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

const extractRules = (value: unknown): unknown[] => {
  if (Array.isArray(value)) {
    return value;
  }
  if (isRecord(value) && Array.isArray(value.rules)) {
    return value.rules;
  }
  return [];
};

const inferMainQuotaType = (rules: unknown[]): CompetitionAwardQuotaType => {
  const types = rules.slice(0, MAIN_COMPETITION_AWARD_NAMES.length).map((rule) =>
    isRecord(rule) ? asQuotaType(rule.quotaType, 'FIXED') : 'FIXED',
  );
  return types.length === MAIN_COMPETITION_AWARD_NAMES.length && types.every((type) => type === 'PERCENTAGE')
    ? 'PERCENTAGE'
    : 'FIXED';
};

export const normalizeCompetitionAwardSettings = (value: unknown): CompetitionAwardSettings => {
  const record = isRecord(value) ? value : undefined;
  const legacyRules = extractRules(value);
  const sourceMainAwards = record && Array.isArray(record.mainAwards)
    ? record.mainAwards
    : legacyRules.slice(0, MAIN_COMPETITION_AWARD_NAMES.length);
  const inferredQuotaType = inferMainQuotaType(legacyRules.length ? legacyRules : sourceMainAwards);
  const mainQuotaType = asQuotaType(record?.mainQuotaType, inferredQuotaType);

  const mainAwards = MAIN_COMPETITION_AWARD_NAMES.map((awardName, index) => {
    const source = isRecord(sourceMainAwards[index]) ? sourceMainAwards[index] : undefined;
    return {
      awardName,
      quota: normalizeQuota(
        source?.quota,
        DEFAULT_COMPETITION_AWARD_SETTINGS.mainAwards[index].quota,
        mainQuotaType,
      ),
    };
  });

  const recordExcellence = record?.excellence;
  const legacyExcellence = legacyRules[MAIN_COMPETITION_AWARD_NAMES.length];
  const isGroupedSettings = Boolean(record && Array.isArray(record.mainAwards));
  const rawExcellence: Record<string, unknown> | undefined = isRecord(recordExcellence)
    ? recordExcellence
    : isRecord(legacyExcellence)
      ? legacyExcellence
      : undefined;
  const excellenceEnabled = typeof record?.excellenceEnabled === 'boolean'
    ? record.excellenceEnabled
    : typeof rawExcellence?.enabled === 'boolean'
      ? Boolean(rawExcellence.enabled)
      : !isGroupedSettings;
  const excellenceQuota = normalizeQuota(
    rawExcellence?.quota,
    DEFAULT_COMPETITION_AWARD_SETTINGS.excellenceQuota,
    mainQuotaType,
  );

  const rawSpecialAwards = record && Array.isArray(record.specialAwards) ? record.specialAwards : [];
  const specialAwards = rawSpecialAwards
    .filter(isRecord)
    .map((special, index) => {
      const quotaType = asQuotaType(special.quotaType, 'FIXED');
      return {
        id: typeof special.id === 'string' && special.id.trim() ? special.id : createSpecialAwardId(index),
        awardName: typeof special.awardName === 'string' ? special.awardName : '',
        quotaType,
        quota: normalizeQuota(special.quota, 1, quotaType),
      };
    });

  return {
    version: 2,
    mainQuotaType,
    mainAwards,
    excellenceEnabled,
    excellenceQuota,
    specialAwards,
  };
};

export const getCompetitionAwardSettings = (items?: CompetitionConfigItem[]): CompetitionAwardSettings => {
  const item = items?.find((candidate) =>
    candidate.itemType === COMPETITION_AWARD_SETTINGS_ITEM_TYPE
    && candidate.itemKey === COMPETITION_AWARD_SETTINGS_ITEM_KEY,
  );
  return normalizeCompetitionAwardSettings(parseAwardSettingsContent(item?.contentJson));
};

const configuredRulesFromSettings = (settings: CompetitionAwardSettings): CompetitionAwardRuleSetting[] => [
  ...settings.mainAwards.map((award) => ({
    awardName: award.awardName,
    quota: award.quota,
    quotaType: settings.mainQuotaType,
  })),
  ...(settings.excellenceEnabled
    ? [{
        awardName: EXCELLENCE_COMPETITION_AWARD_NAME,
        quota: settings.excellenceQuota,
        quotaType: settings.mainQuotaType,
      }]
    : []),
  ...settings.specialAwards.map((award) => ({
    awardName: award.awardName,
    quota: award.quota,
    quotaType: award.quotaType,
  })),
];

export const getCompetitionAwardRules = (items?: CompetitionConfigItem[]): CompetitionAwardRuleSetting[] =>
  configuredRulesFromSettings(getCompetitionAwardSettings(items));

export const resolveCompetitionAwardQuota = (rule: CompetitionAwardRuleSetting, candidateCount?: number) => {
  if (rule.quotaType !== 'PERCENTAGE') {
    return normalizeQuota(rule.quota, 1, 'FIXED');
  }
  const normalizedPercentage = normalizeQuota(rule.quota, 1, 'PERCENTAGE');
  const normalizedCandidateCount = Number(candidateCount);
  if (!Number.isFinite(normalizedCandidateCount) || normalizedCandidateCount <= 0) {
    return 1;
  }
  return Math.max(1, Math.ceil(normalizedCandidateCount * normalizedPercentage / 100));
};

export const toCompetitionAwardRankRules = (
  settingsOrRules: CompetitionAwardSettings | CompetitionAwardRuleSetting[],
  candidateCount?: number,
): CompetitionAwardRankRule[] => {
  const rules = Array.isArray(settingsOrRules)
    ? settingsOrRules
    : configuredRulesFromSettings(normalizeCompetitionAwardSettings(settingsOrRules));
  let nextRank = 1;
  return rules.map((rule) => {
    const quota = resolveCompetitionAwardQuota(rule, candidateCount);
    const minRank = nextRank;
    const maxRank = nextRank + quota - 1;
    nextRank = maxRank + 1;
    return { ...rule, quota, minRank, maxRank };
  });
};

export const buildCompetitionAwardSettingsItem = (
  existing: CompetitionConfigItem | undefined,
  settingsOrRules: CompetitionAwardSettings | CompetitionAwardRuleSetting[],
): CompetitionConfigItem => {
  const settings = Array.isArray(settingsOrRules)
    ? normalizeCompetitionAwardSettings({ rules: settingsOrRules })
    : normalizeCompetitionAwardSettings(settingsOrRules);
  return {
    ...existing,
    itemType: COMPETITION_AWARD_SETTINGS_ITEM_TYPE,
    itemKey: COMPETITION_AWARD_SETTINGS_ITEM_KEY,
    title: existing?.title || '获奖设置',
    contentJson: JSON.stringify({
      version: 2,
      mainQuotaType: settings.mainQuotaType,
      mainAwards: settings.mainAwards,
      excellence: {
        enabled: settings.excellenceEnabled,
        quota: settings.excellenceQuota,
      },
      specialAwards: settings.specialAwards,
    }),
    contentText: existing?.contentText || '按评审发布结果的最终排名生成获奖名单。',
    sortOrder: existing?.sortOrder ?? 600,
    requiredFlag: true,
    enabled: true,
  };
};
