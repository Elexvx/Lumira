import type { Dayjs } from 'dayjs';
import type { CompetitionLocale, CompetitionUpsertPayload } from '@/services/competition/types';

export type CompetitionSettingsOrganizerFormItem = {
  role?: string;
  name?: string;
};

export type CompetitionSettingsTimeMode = 'CONFIRMED' | 'TBD';

export type CompetitionSettingsScheduleFormItem = {
  timeMode?: CompetitionSettingsTimeMode;
  title?: string;
  timeRange?: [Dayjs, Dayjs] | [string, string];
};

export type CompetitionSettingsConfigModuleKey = 'documents' | 'fields' | 'payments' | 'files' | 'timeline';

export type CompetitionSettingsConfigItemDraft = {
  title?: string | null;
  itemKey?: string | null;
  metadata?: {
    fieldType?: string | null;
    options?: string | null;
    stageCode?: string | null;
  } | null;
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

const hasCompleteSchedules = (schedules?: CompetitionSettingsScheduleFormItem[]) => {
  const normalized = schedules?.length ? schedules : [{ timeMode: 'TBD' as const }];
  if (normalized[0]?.timeMode !== 'CONFIRMED') {
    return true;
  }
  return normalized.every((schedule) => trimOptional(schedule.title) && hasCompleteTimeRange(schedule.timeRange));
};

export const isBasicSettingsPageReadyToSave = (values: Partial<CompetitionSettingsFormValues>) =>
  Boolean(
    trimOptional(values.title) &&
      normalizeOptionValue(values.category) &&
      normalizeOptionValue(values.competitionLevel || values.level) &&
      hasCompleteOrganizer(values.organizers) &&
      trimOptional(values.participationScope) &&
      values.feeMode &&
      values.entryFeeMinor !== undefined &&
      trimOptional(values.currency) &&
      values.locale?.length,
  );

export const isTimelineSettingsPageReadyToSave = (values: Partial<CompetitionSettingsFormValues>) =>
  Boolean(hasCompleteTimeRange(values.registrationRange) && hasCompleteSchedules(values.schedules));

const hasText = (value?: string | null) => Boolean(trimOptional(value));

export const isConfigModuleReadyToSave = (
  moduleKey: CompetitionSettingsConfigModuleKey,
  items: CompetitionSettingsConfigItemDraft[],
) => {
  if (!items.length) {
    return true;
  }
  return items.every((item) => {
    if (moduleKey === 'documents') {
      return hasText(item.title);
    }
    if (moduleKey === 'fields') {
      return hasText(item.title)
        && hasText(item.itemKey)
        && hasText(item.metadata?.fieldType)
        && (!['SELECT', 'MULTI_SELECT'].includes(item.metadata?.fieldType || '') || hasText(item.metadata?.options));
    }
    if (moduleKey === 'files') {
      return hasText(item.title) && hasText(item.itemKey) && hasText(item.metadata?.stageCode);
    }
    return hasText(item.title) && hasText(item.itemKey);
  });
};
