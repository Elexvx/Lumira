import type { Dayjs } from 'dayjs';
import type { CompetitionLocale, CompetitionUpsertPayload } from '@/services/competition/types';
import {
  isChronologicalTimeRange,
  isScheduleAtOrAfterRegistrationEnd,
  isTimeRangeAtOrAfterPreviousEnd,
} from './utils/competitionTimeline';

export type CompetitionSettingsOrganizerFormItem = {
  role?: string;
  name?: string;
};

export type CompetitionSettingsTimeMode = 'CONFIRMED' | 'TBD';

export type CompetitionSettingsScheduleFormItem = {
  timeMode?: CompetitionSettingsTimeMode;
  title?: string;
  materialRange?: [Dayjs, Dayjs] | [string, string];
  timeRange?: [Dayjs, Dayjs] | [string, string];
  reviewRange?: [Dayjs, Dayjs] | [string, string];
};

export type CompetitionSettingsConfigModuleKey = 'documents' | 'fields' | 'payments' | 'files' | 'timeline';

export type CompetitionSettingsConfigItemDraft = {
  itemType?: string | null;
  title?: string | null;
  itemKey?: string | null;
  metadata?: {
    fieldScope?: string | null;
    groupLabel?: string | null;
    fieldType?: string | null;
    options?: string | null;
    stageCode?: string | null;
    fileFormat?: string | null;
    maxSizeMb?: number | null;
    storageKey?: string | null;
  } | null;
};

export type CompetitionSettingsConfigValidationScope = {
  fieldScope?: string | null;
  includeGroupLabel?: string | null;
  excludeGroupLabel?: string | null;
};

export type ConfigModuleDraftHydrationState = {
  hydratedContextKey?: string;
  nextContextKey: string;
  draftRevision: number;
  syncedRevision: number;
};

type StageMaterialSaveItem = {
  itemKey?: string | null;
};

export type CompetitionSettingsFormValues = Omit<Partial<CompetitionUpsertPayload>, 'locale'> & {
  locale?: CompetitionLocale[];
  registrationRange?: [Dayjs, Dayjs] | [string, string];
  organizers?: CompetitionSettingsOrganizerFormItem[];
  schedules?: CompetitionSettingsScheduleFormItem[];
};

const trimOptional = (value?: string | null) => {
  const trimmed = value?.trim();
  return trimmed || undefined;
};

const normalizeOptionValue = (value: unknown): string | undefined => {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    return trimmed && trimmed !== '[object Object]' ? trimmed : undefined;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  if (typeof value === 'object') {
    const optionLike = value as Record<string, unknown>;
    return normalizeOptionValue(optionLike.value ?? optionLike.itemValue ?? optionLike.code ?? optionLike.key ?? optionLike.label ?? optionLike.itemLabel);
  }
  return undefined;
};

const hasCompleteTimeRange = (range?: CompetitionSettingsFormValues['registrationRange'] | CompetitionSettingsScheduleFormItem['timeRange']) =>
  Array.isArray(range) && range.length === 2 && Boolean(range[0]) && Boolean(range[1]);

const hasCompleteOrganizer = (organizers?: CompetitionSettingsOrganizerFormItem[]) =>
  (organizers || []).some((organizer) => trimOptional(organizer.role) && trimOptional(organizer.name));

const appendMissingField = (missingFields: string[], field: string) => {
  if (!missingFields.includes(field)) {
    missingFields.push(field);
  }
};

export const getBasicSettingsMissingFields = (values: Partial<CompetitionSettingsFormValues>) => {
  const missingFields: string[] = [];
  if (!trimOptional(values.title)) {
    appendMissingField(missingFields, '竞赛名称');
  }
  if (!hasCompleteOrganizer(values.organizers)) {
    appendMissingField(missingFields, '组织者列表');
  }
  if (!normalizeOptionValue(values.category)) {
    appendMissingField(missingFields, '竞赛类别');
  }
  if (!normalizeOptionValue(values.competitionLevel || values.level)) {
    appendMissingField(missingFields, '竞赛级别');
  }
  if (!trimOptional(values.participationScope)) {
    appendMissingField(missingFields, '参赛范围');
  }
  return missingFields;
};

export const getPaymentSettingsMissingFields = (values: Partial<CompetitionSettingsFormValues>) => {
  const missingFields: string[] = [];
  if (!values.feeMode) {
    appendMissingField(missingFields, '收费方式');
  }
  if (values.entryFeeMinor === undefined || values.entryFeeMinor === null || Number.isNaN(Number(values.entryFeeMinor))) {
    appendMissingField(missingFields, '参赛费用');
  }
  if (!trimOptional(values.currency)) {
    appendMissingField(missingFields, '货币');
  }
  return missingFields;
};

export const getTimelineSettingsMissingFields = (
  values: Partial<CompetitionSettingsFormValues>,
  options: { requireScheduleMode?: boolean } = {},
) => {
  const missingFields: string[] = [];
  if (!hasCompleteTimeRange(values.registrationRange)) {
    appendMissingField(missingFields, '报名时间');
  }

  const schedules = values.schedules || [];
  const firstSchedule = schedules[0];
  if (!firstSchedule?.timeMode) {
    if (options.requireScheduleMode) {
      appendMissingField(missingFields, '竞赛安排');
    }
    return missingFields;
  }
  if (firstSchedule.timeMode !== 'CONFIRMED') {
    return missingFields;
  }

  const confirmedSchedules = schedules.filter((schedule) => schedule.timeMode === 'CONFIRMED');
  if (!confirmedSchedules.length || confirmedSchedules.some((schedule) => (
    !trimOptional(schedule.title) || !hasCompleteTimeRange(schedule.timeRange)
  ))) {
    appendMissingField(missingFields, '竞赛安排');
  }
  if (confirmedSchedules.some((schedule) => (
    !hasCompleteTimeRange(schedule.materialRange) || !isChronologicalTimeRange(schedule.materialRange)
  ))) {
    appendMissingField(missingFields, '提交材料时间');
  }
  if (confirmedSchedules.some((schedule) => (
    hasCompleteTimeRange(schedule.timeRange) && !isChronologicalTimeRange(schedule.timeRange)
  ))) {
    appendMissingField(missingFields, '比赛时间');
  }
  if (confirmedSchedules.some((schedule) => !hasCompleteTimeRange(schedule.reviewRange))) {
    appendMissingField(missingFields, '评审时间');
  }
  if (confirmedSchedules.some((schedule) => (
    hasCompleteTimeRange(schedule.reviewRange) && !isChronologicalTimeRange(schedule.reviewRange)
  ))) {
    appendMissingField(missingFields, '评审时间');
  }
  if (confirmedSchedules.some((schedule) => (
    hasCompleteTimeRange(schedule.timeRange)
      && !isScheduleAtOrAfterRegistrationEnd(schedule.timeRange, values.registrationRange)
  ))) {
    appendMissingField(missingFields, '竞赛开始时间不得早于报名结束时间');
  }
  if (confirmedSchedules.some((schedule) => (
    hasCompleteTimeRange(schedule.materialRange)
      && hasCompleteTimeRange(schedule.timeRange)
      && !isTimeRangeAtOrAfterPreviousEnd(schedule.timeRange, schedule.materialRange)
  ))) {
    appendMissingField(missingFields, '比赛开始时间不得早于材料提交截止时间');
  }
  if (confirmedSchedules.some((schedule) => (
    hasCompleteTimeRange(schedule.timeRange)
      && hasCompleteTimeRange(schedule.reviewRange)
      && !isTimeRangeAtOrAfterPreviousEnd(schedule.reviewRange, schedule.timeRange)
  ))) {
    appendMissingField(missingFields, '评审开始时间不得早于比赛结束时间');
  }
  return missingFields;
};

export const getCompetitionCreateMissingFields = (values: Partial<CompetitionSettingsFormValues>) => [
  ...getBasicSettingsMissingFields(values),
  ...getPaymentSettingsMissingFields(values),
  ...getTimelineSettingsMissingFields(values, { requireScheduleMode: true }),
];

export const isBasicSettingsPageReadyToSave = (values: Partial<CompetitionSettingsFormValues>) =>
  getBasicSettingsMissingFields(values).length === 0;

export const isPaymentSettingsPageReadyToSave = (values: Partial<CompetitionSettingsFormValues>) =>
  getPaymentSettingsMissingFields(values).length === 0;

export const isTimelineSettingsPageReadyToSave = (values: Partial<CompetitionSettingsFormValues>) =>
  getTimelineSettingsMissingFields(values).length === 0;

export const shouldHydrateConfigModuleDraft = ({
  hydratedContextKey,
  nextContextKey,
  draftRevision,
  syncedRevision,
}: ConfigModuleDraftHydrationState) =>
  hydratedContextKey !== nextContextKey || draftRevision === syncedRevision;

export const isConfigModuleDraftSaveCurrent = (saveRevision: number, currentRevision: number) =>
  saveRevision === currentRevision;

const hasText = (value?: string | null) => Boolean(trimOptional(value));

const configModuleItemIdentity = (item: CompetitionSettingsConfigItemDraft) => {
  const itemType = trimOptional(item.metadata?.fieldScope) || trimOptional(item.itemType);
  const itemKey = trimOptional(item.itemKey);
  return itemType && itemKey ? `${itemType.toUpperCase()}\u0000${itemKey.toLowerCase()}` : undefined;
};

export const isConfigModuleItemKeyDuplicate = (
  items: CompetitionSettingsConfigItemDraft[],
  itemIndex: number,
) => {
  const identity = configModuleItemIdentity(items[itemIndex] || {});
  return Boolean(identity && items.some((item, index) => (
    index !== itemIndex && configModuleItemIdentity(item) === identity
  )));
};

const hasDuplicateConfigModuleItemKeys = (items: CompetitionSettingsConfigItemDraft[]) =>
  items.some((_, index) => isConfigModuleItemKeyDuplicate(items, index));

const getConfigModuleItemsInValidationScope = (
  moduleKey: CompetitionSettingsConfigModuleKey,
  items: CompetitionSettingsConfigItemDraft[],
  validationScope?: CompetitionSettingsConfigValidationScope,
) => {
  const fieldScope = trimOptional(validationScope?.fieldScope);
  if (moduleKey !== 'fields' || !fieldScope) {
    return items;
  }
  const includeGroupLabel = trimOptional(validationScope?.includeGroupLabel);
  const excludeGroupLabel = trimOptional(validationScope?.excludeGroupLabel);
  return items.filter((item) => {
    const itemScope = trimOptional(item.metadata?.fieldScope) || trimOptional(item.itemType);
    if (itemScope !== fieldScope) {
      return false;
    }
    const groupLabel = trimOptional(item.metadata?.groupLabel);
    if (includeGroupLabel) {
      return groupLabel === includeGroupLabel;
    }
    if (excludeGroupLabel) {
      return groupLabel !== excludeGroupLabel;
    }
    return true;
  });
};

export const shouldValidateTeamMemberLimitsForPage = (
  moduleKey: CompetitionSettingsConfigModuleKey,
  fieldScope?: string | null,
) => moduleKey === 'fields' && (!fieldScope || fieldScope === 'TEAM_FIELD');

export const isConfigModuleReadyToSave = (
  moduleKey: CompetitionSettingsConfigModuleKey,
  items: CompetitionSettingsConfigItemDraft[],
  validationScope?: CompetitionSettingsConfigValidationScope,
) => {
  const validationItems = getConfigModuleItemsInValidationScope(moduleKey, items, validationScope);
  if (!validationItems.length) {
    return true;
  }
  if (hasDuplicateConfigModuleItemKeys(validationItems)) {
    return false;
  }
  return validationItems.every((item) => {
    if (moduleKey === 'documents') {
      return hasText(item.title);
    }
    if (moduleKey === 'fields') {
      const fieldType = item.metadata?.fieldType
        || (item.itemType === 'REGISTRATION_FIELD' ? 'TEXT' : undefined);
      return hasText(item.title)
        && hasText(item.itemKey)
        && hasText(fieldType)
        && (!['SELECT', 'MULTI_SELECT'].includes(fieldType || '') || hasText(item.metadata?.options));
    }
    if (moduleKey === 'files') {
      return hasText(item.title)
        && hasText(item.itemKey)
        && hasText(item.metadata?.stageCode)
        && hasText(item.metadata?.fileFormat)
        && Number(item.metadata?.maxSizeMb) > 0
        && hasText(item.metadata?.storageKey);
    }
    return hasText(item.title) && hasText(item.itemKey);
  });
};

export const mergeStageMaterialSaveItems = <T extends StageMaterialSaveItem>(
  existingItems: T[],
  currentStageCode: string,
  currentStageItems: T[],
  getStageCode: (item: T) => string,
) => {
  const replacementKeys = new Set(
    currentStageItems
      .map((item) => trimOptional(item.itemKey))
      .filter((itemKey): itemKey is string => Boolean(itemKey)),
  );
  const preservedItems = existingItems.filter((item) => {
    const itemStageCode = getStageCode(item);
    if (itemStageCode === currentStageCode) {
      return false;
    }
    if (itemStageCode === 'GENERAL' && replacementKeys.has(trimOptional(item.itemKey) || '')) {
      return false;
    }
    return true;
  });
  return [...preservedItems, ...currentStageItems];
};
