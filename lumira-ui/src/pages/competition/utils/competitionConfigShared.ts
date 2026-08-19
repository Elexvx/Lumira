import type { CompetitionConfigItem } from '@/services/competition/types';
import {
  buildRegistrationParticipantLimitMetadata,
  getRegistrationParticipantLimits,
  type RegistrationParticipantLimits,
} from './competitionParticipantConfig';
import { getRegistrationDocumentAcceptanceKey } from './registrationDocumentAcceptance';

export type CompetitionConfigFieldScope =
  | 'REGISTRATION_FIELD'
  | 'TEAM_FIELD'
  | 'MEMBER_FIELD'
  | 'TEACHER_FIELD'
  | 'PROJECT_FIELD'
  | 'EXPERT_FIELD';

export type ConfigItemMetadata = {
  documentKind?: 'AGREEMENT' | 'CONSENT';
  readingSeconds?: number;
  fieldScope?: CompetitionConfigFieldScope;
  fieldType?: string;
  placeholder?: string;
  description?: string;
  groupLabel?: string;
  standardField?: boolean;
  validationRule?: string;
  options?: string;
  cropAspectRatio?: string;
  weight?: number;
  fileFormat?: string;
  maxSizeMb?: number;
  storageKey?: string;
  stageCode?: string;
  stageName?: string;
  materialType?: string;
  teamMinMembers?: number;
  teamMaxMembers?: number;
  studentMinMembers?: number;
  studentMaxMembers?: number;
  teacherMinMembers?: number;
  teacherMaxMembers?: number;
};

export const TEAM_SETTINGS_ITEM_KEY = 'team-size-limits';
export const INTELLECTUAL_PROPERTY_GROUP_LABEL = '知识产权信息';
export const normalizeCollectedFieldConfigKey = (value?: string) =>
  (value || '').replace(/[^a-z0-9]/gi, '').toLowerCase();

export const parseConfigItemMetadata = (contentJson?: string | null): ConfigItemMetadata => {
  if (!contentJson) return {};
  try {
    const parsed = JSON.parse(contentJson);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
};

export const serializeConfigItemMetadata = (metadata?: ConfigItemMetadata) => {
  const cleaned = Object.fromEntries(
    Object.entries(metadata || {}).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
  return JSON.stringify(cleaned, null, 2);
};

export const getTeamMemberLimits = (items: CompetitionConfigItem[]) => {
  const settingsItem = items.find(
    (item) => item.itemType === 'TEAM_SETTINGS' && item.itemKey === TEAM_SETTINGS_ITEM_KEY,
  );
  return getRegistrationParticipantLimits(parseConfigItemMetadata(settingsItem?.contentJson));
};

export const buildTeamSettingsConfigItem = (limits: RegistrationParticipantLimits): CompetitionConfigItem => ({
  itemType: 'TEAM_SETTINGS',
  itemKey: TEAM_SETTINGS_ITEM_KEY,
  title: '参赛人员数量限制',
  contentJson: serializeConfigItemMetadata(buildRegistrationParticipantLimitMetadata(limits)),
  sortOrder: 0,
  requiredFlag: false,
  enabled: true,
});

export const normalizeReadingSeconds = (value?: number | string | null) => {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? Math.max(0, Math.floor(numericValue)) : 0;
};

export const getConfigItemReadingSeconds = (item: CompetitionConfigItem) =>
  normalizeReadingSeconds(parseConfigItemMetadata(item.contentJson).readingSeconds);

export const getRegistrationDocumentKey = (item: CompetitionConfigItem, index: number) =>
  getRegistrationDocumentAcceptanceKey(item, index);

export const normalizeFileFormat = (value?: string) => {
  if (value === 'PDF' || value === 'WORD') return 'DOCUMENT';
  return ['ANY', 'DOCUMENT', 'IMAGE', 'ARCHIVE'].includes(value || '') ? value : 'ANY';
};
