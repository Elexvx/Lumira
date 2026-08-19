import { CheckCircleOutlined, DeleteOutlined, PlusOutlined, RollbackOutlined, SettingOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Alert, Button, Card, Checkbox, DatePicker, Form, Input, InputNumber, Popconfirm, Radio, Result, Select, Space, Steps, Tag, Typography } from 'antd';
import type { FormInstance } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { history, useLocation } from '@umijs/max';
import { formatMessage } from '@/i18n/formatMessage';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import { XMarkdown } from '@ant-design/x-markdown';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { useDictOptions } from '@/hooks/useDictOptions';
import { useResponsive } from '@/hooks/useResponsive';
import { databaseMessage } from '@/i18n/databaseMessage';
import {
  createCompetition,
  createCompetitionDraft,
  deleteCompetition,
  listCompetitions,
  updateCompetition,
  updateCompetitionDraft,
  type RegistrationSnapshotMemberPayload,
  type RegistrationSnapshotTeamPayload,
} from '@/services/competition/api';
import type {
  CompetitionFeeMode,
  CompetitionLocale,
  CompetitionRecord,
  CompetitionRegistrationRecord,
  CompetitionStatus,
  CompetitionUpsertPayload,
} from '@/services/competition/types';
import { request } from '@/services/common/request';
import ActivityRegistrationPage from '@/pages/competition/ActivityRegistrationPage';
import ExpertApplicationPage from '@/pages/competition/ExpertApplicationPage';
import PaymentResultPage from '@/pages/competition/PaymentResultPage';
import {
  getCompetitionCreateMissingFields,
} from '@/pages/competition/competitionSettingsSave';
import {
  buildRegistrationDocumentAcceptanceStorageKey,
} from '@/pages/competition/utils/registrationDocumentAcceptance';
import { normalizeCompetitionDraftBasicDefaults } from '@/pages/competition/utils/competitionDraftDefaults';
import {
  isChronologicalTimeRange,
  isTimeRangeAtOrAfterPreviousEnd,
  isTimeRangeWithinBounds,
} from '@/pages/competition/utils/competitionTimeline';
import { hasRegistrationIntellectualPropertyContent } from '@/pages/competition/utils/registrationIntellectualProperties';
import type { RegistrationParticipantType } from '@/pages/competition/utils/competitionParticipantConfig';
import { message, modal } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';
import { sanitizeMarkdownInput } from '@/utils/markdownSecurity';
import {
  deriveCompetitionOverallWindow,
  preserveCompetitionTimelineSnapshot,
  sanitizeCompetitionSchedules,
  type CompetitionJsonSchedule,
  type CompetitionScheduleFormItem,
  type CompetitionTimeMode,
} from './competitionSchedulePayload';
import './CompetitionPage.css';

export const detectPaymentClientType = (): 'DESKTOP' | 'MOBILE' | 'WECHAT' => {
  if (typeof navigator === 'undefined') {
    return 'DESKTOP';
  }
  const userAgent = navigator.userAgent.toLowerCase();
  if (/micromessenger/.test(userAgent)) {
    return 'WECHAT';
  }
  return /android|iphone|ipad|ipod|mobile/.test(userAgent) ? 'MOBILE' : 'DESKTOP';
};

type CompetitionOrganizerFormItem = {
  role?: string;
  name?: string;
};

export type CompetitionFormValues = Omit<Partial<CompetitionUpsertPayload>, 'locale'> & {
  locale?: CompetitionLocale[];
  registrationRange?: [Dayjs, Dayjs] | [string, string];
  organizers?: CompetitionOrganizerFormItem[];
  schedules?: CompetitionScheduleFormItem[];
};

export type RegistrationFormValues = {
  competitionId?: number;
  registrationExtraValues?: Record<string, unknown>;
  teamId?: number;
  newTeamName?: string;
  newTeam?: RegistrationTeamDraft;
  projectId?: number;
  newProjectTitle?: string;
  newProjectImageUrl?: string;
  newProjectDescription?: string;
  newProjectExtraValues?: Record<string, unknown>;
  materials?: Record<string, unknown>;
};

export type CompetitionStageFormField = {
  key: string;
  label?: string;
  type?: string;
  required?: boolean;
  maxLength?: number;
  fileFormat?: string;
  maxSizeMb?: number;
  storageKey?: string;
  stageCode?: string;
};

export type RegistrationTeamMemberDraft = RegistrationSnapshotMemberPayload;

export type RegistrationTeamDraft = RegistrationSnapshotTeamPayload & {
  initialMembers?: RegistrationTeamMemberDraft[];
};

export type RegistrationMemberEditorKey = {
  participantType: RegistrationParticipantType;
  participantIndex: number | 'new';
};
export const COMPETITION_REGISTRATION_SCOPE_RESOURCE = 'competition:registration';
const fallbackRegistrationTeamTypeOptions = () => [
  { value: 'GENERAL', label: databaseMessage('competition.teamType.general') },
  { value: 'DEV', label: databaseMessage('competition.teamType.development') },
  { value: 'COMPETITION', label: databaseMessage('competition.teamType.competition') },
  { value: 'CLUB', label: databaseMessage('competition.teamType.club') },
  { value: 'OTHER', label: databaseMessage('competition.teamType.other') },
];
export const emptyRegistrationTeamMember = (
  participantType: RegistrationParticipantType = 'STUDENT',
): RegistrationTeamMemberDraft => ({
  participantType,
  memberName: '',
  employeeNo: '',
  departmentName: '',
  role: 'MEMBER',
  remark: '',
  extraValues: {},
});

type CompetitionMaterialStageTab = {
  key: string;
  label: string;
  stageCode: string;
  stageName: string;
};

export const getCompetitionMaterialStageTabs = (competition: CompetitionRecord): CompetitionMaterialStageTab[] =>
  parseJsonArray<CompetitionJsonSchedule>(competition.scheduleJson)
    .filter((schedule) => schedule.timeMode === 'CONFIRMED' && trimOptional(schedule.title))
    .map((schedule, index) => ({
      key: `stage-${index + 1}`,
      label: `${schedule.title}材料设置`,
      stageCode: index === 0 ? 'PRELIMINARY' : index === 1 ? 'FINAL' : `STAGE_${index + 1}`,
      stageName: schedule.title || `阶段${index + 1}`,
    }));

type CompetitionCreateDraftStorage = {
  competitionId?: number;
  competitionUuid?: string;
  competitionNo?: string;
  currentStep?: number;
  termsAccepted?: boolean;
  savedAt?: number;
  values?: Partial<CompetitionFormValues>;
};

export type CompetitionRegistrationDraftStorage = {
  competitionTitle?: string;
  competitionUuid?: string;
  competitionFeeMode?: CompetitionFeeMode | null;
  competitionEntryFeeMinor?: number | null;
  competitionCurrency?: string | null;
  registrationNo?: string;
  participantNo?: string;
  registrationId?: number;
  currentStep?: number;
  flowVersion?: number;
  acceptedDocumentKeys?: string[];
  confirmedTeamId?: number;
  confirmedProjectId?: number;
  paymentStatus?: string;
  savedAt?: number;
  localUpdatedAt?: number;
  cloudUpdatedAt?: number;
  syncStatus?: 'LOCAL_ONLY' | 'SYNCED' | 'SYNC_ERROR';
  values?: Partial<RegistrationFormValues>;
};

export type RegistrationDraftSyncStatus = 'IDLE' | 'SAVING_LOCAL' | 'LOCAL_ONLY' | 'SYNCING' | 'SYNCED' | 'SYNC_ERROR';

export class RegistrationDraftCloudSyncError extends Error {
  readonly causeValue: unknown;

  constructor(causeValue: unknown) {
    super('Competition registration draft cloud sync failed');
    this.name = 'RegistrationDraftCloudSyncError';
    this.causeValue = causeValue;
  }
}

type CompetitionRegistrationDocumentAcceptanceStorage = {
  acceptedDocumentKeys?: string[];
  savedAt?: number;
};

export type CompetitionRegistrationListRecord = CompetitionRegistrationRecord & {
  isCurrentUserDraft?: boolean;
  draftCompetitionTitle?: string;
  draftTeamName?: string;
  draftProjectTitle?: string;
};

export const COMPETITION_CATEGORY_DICT = 'aiadc_competition_category';
export const COMPETITION_LEVEL_DICT = 'aiadc_competition_level';
const COMPETITION_CREATE_DRAFT_STORAGE_KEY = 'competition.create';

export const defaultRegistrationFormValues: Partial<RegistrationFormValues> = {
  newTeam: {
    teamType: 'GENERAL',
    initialMembers: [],
  },
};

export const useCompetitionDictFallbackOptions = () => ({
  categoryOptions: [
    { label: databaseMessage('competition.category.innovation'), value: 'INNOVATION' },
    { label: databaseMessage('competition.category.application'), value: 'APPLICATION' },
    { label: databaseMessage('competition.category.special'), value: 'SPECIAL' },
    { label: databaseMessage('competition.category.other'), value: 'OTHER' },
  ],
  levelOptions: [
    { label: databaseMessage('competition.level.school'), value: 'SCHOOL' },
    { label: databaseMessage('competition.level.provincial'), value: 'PROVINCE' },
    { label: databaseMessage('competition.level.national'), value: 'NATIONAL' },
    { label: databaseMessage('competition.level.international'), value: 'INTERNATIONAL' },
  ],
  registrationTeamTypeOptions: fallbackRegistrationTeamTypeOptions(),
});

export const timeModeOptions: Array<{ label: string; value: CompetitionTimeMode }> = [
  { label: '确定', value: 'CONFIRMED' },
  { label: '不确定', value: 'TBD' },
];

export const feeModeOptions: Array<{ label: string; value: CompetitionFeeMode }> = [
  { label: '按团队收费', value: 'TEAM' },
  { label: '按人数收费', value: 'MEMBER' },
];

const statusText: Record<CompetitionStatus, string> = {
  draft: '草稿',
  published: '已发布',
  archived: '已归档',
};

const statusColor: Record<CompetitionStatus, string> = {
  draft: 'default',
  published: 'green',
  archived: 'blue',
};

const competitionCreateSteps = [
  { title: '条款同意' },
  { title: '基本信息' },
];

const competitionCreateStepQueryKey = 'step';

const getCompetitionCreateSteps = () => [
  { title: formatMessage({ id: 'page.competition.create.steps.terms', defaultMessage: 'Terms' }) },
  { title: formatMessage({ id: 'page.competition.create.steps.basic', defaultMessage: 'Basic information' }) },
];

const parseCompetitionCreateStepFromSearch = (search: string) => {
  const stepValue = Number(new URLSearchParams(search).get(competitionCreateStepQueryKey));
  if (!Number.isInteger(stepValue) || stepValue < 1) {
    return 0;
  }
  return Math.min(stepValue - 1, competitionCreateSteps.length - 1);
};

const createCompetitionStepSearch = (stepIndex: number) => `?${competitionCreateStepQueryKey}=${Math.min(stepIndex + 1, competitionCreateSteps.length)}`;

const competitionTermsMarkdown = sanitizeMarkdownInput(`
## 赛事发布条款

请在新增赛事前确认以下内容：

1. 赛事名称、时间、主办方等信息应真实、准确、完整。
2. 二维码和联系方式不得包含违法、侵权、误导或与赛事无关的内容。
3. 如赛事涉及报名、评审、奖项或公开展示，请确保已取得必要授权，并提前准备可对外说明的规则。
4. 发布后，赛事可能展示在前台页面、团队协作和报名相关流程中，请在提交前完成最终核对。

继续创建即表示你已阅读并同意按平台规范发布赛事内容。
`);

const _defaultPreliminaryFormSchema = JSON.stringify(
  {
    fields: [
      {
        key: 'project_plan',
        label: 'Project Plan',
        type: 'file',
        required: true,
        accept: ['.pdf', '.doc', '.docx'],
        maxSizeMb: 20,
      },
      {
        key: 'project_intro',
        label: 'Project Description',
        type: 'textarea',
        required: true,
        maxLength: 1000,
      },
    ],
  },
  null,
  2,
);

export const trimOptional = (value?: unknown) => normalizeDisplayText(value);

export const normalizeDisplayText = (value: unknown): string | undefined => {
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
  if (Array.isArray(value)) {
    return value.map(normalizeDisplayText).filter(Boolean).join(' / ') || undefined;
  }
  if (typeof value === 'object') {
    const optionLike = value as Record<string, unknown>;
    return normalizeDisplayText(
      optionLike.label ??
        optionLike.itemLabel ??
        optionLike.title ??
        optionLike.name ??
        optionLike.text ??
        optionLike.value ??
        optionLike.itemValue ??
        optionLike.key,
    );
  }
  return undefined;
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

export const buildOptionLabelMap = (options: Array<{ label?: unknown; value?: unknown }>) => {
  const entries = options
    .map((option) => {
      const value = normalizeOptionValue(option.value);
      return value ? ([value, option.label] as [string, unknown]) : undefined;
    })
    .filter((entry): entry is [string, unknown] => Boolean(entry));
  return new Map(entries);
};

export const resolveOptionLabel = (optionLabelMap: Map<string, unknown>, value: unknown) => {
  const normalizedValue = normalizeOptionValue(value);
  return (normalizedValue ? normalizeDisplayText(optionLabelMap.get(normalizedValue)) : undefined) || normalizeDisplayText(value) || normalizedValue;
};

const parseDateTime = (value?: string | null) => {
  if (!value || value === 'TBD') {
    return undefined;
  }
  const parsed = dayjs(value.replace(/\./g, '-'));
  return parsed.isValid() ? parsed : undefined;
};

export const parseRange = (start?: string | null, end?: string | null): [Dayjs, Dayjs] | undefined => {
  const parsedStart = parseDateTime(start);
  const parsedEnd = parseDateTime(end);
  return parsedStart && parsedEnd ? [parsedStart, parsedEnd] : undefined;
};

const normalizeTimeMode = (value?: string | null): CompetitionTimeMode => (value === 'CONFIRMED' ? 'CONFIRMED' : 'TBD');

const formatRangeValue = (value?: Dayjs | string) => {
  if (!value) {
    return undefined;
  }
  if (typeof value === 'string') {
    return value;
  }
  return value.format('YYYY-MM-DD HH:mm');
};

const splitTags = (tags?: string | null) =>
  (tags || '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);

const splitCompetitionLocales = (value?: string | null): CompetitionLocale[] =>
  (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter((item): item is CompetitionLocale => item === 'zh' || item === 'en');

const joinCompetitionLocales = (values?: CompetitionLocale[]) => Array.from(new Set(values || [])).join(',');

const parseJsonArray = <T,>(value?: string | null): T[] => {
  if (!value) {
    return [];
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const sanitizeOrganizers = (organizers?: CompetitionOrganizerFormItem[]) =>
  (organizers || [])
    .map((item) => ({
      role: trimOptional(item.role),
      name: trimOptional(item.name),
    }))
    .filter((item) => item.role || item.name);

const organizerLabel = (organizer: CompetitionOrganizerFormItem) =>
  [organizer.role, organizer.name].map(trimOptional).filter(Boolean).join('：');

const mojibakeReplacementPattern = new RegExp(`${String.fromCharCode(0xfffd)}\\??`, 'g');

const normalizeMojibakeText = (value?: string | null) =>
  trimOptional(value)?.replace(mojibakeReplacementPattern, '');

export const normalizePayload = (
  values: CompetitionFormValues,
  options?: { preserveTimelineFrom?: CompetitionRecord },
): CompetitionUpsertPayload => {
  const [registrationStart, registrationEnd] = values.registrationRange || [];
  const organizers = sanitizeOrganizers(values.organizers);
  const schedules = sanitizeCompetitionSchedules(values.schedules);
  const overallWindow = deriveCompetitionOverallWindow(
    schedules,
    values.competitionStart,
    values.competitionEnd,
  );
  const timelineSnapshot = options?.preserveTimelineFrom
    ? preserveCompetitionTimelineSnapshot(options.preserveTimelineFrom)
    : {
        registrationStart: formatRangeValue(registrationStart),
        registrationEnd: formatRangeValue(registrationEnd),
        scheduleJson: schedules.length ? JSON.stringify(schedules) : undefined,
        ...overallWindow,
      };
  const participationScope = trimOptional(values.participationScope);
  const category = normalizeOptionValue(values.category);
  const competitionLevel = normalizeOptionValue(values.competitionLevel || values.level);

  return {
    code: trimOptional(values.code),
    locale: joinCompetitionLocales(values.locale) || 'zh',
    title: (values.title || '').trim(),
    shortName: trimOptional(values.shortName),
    category: category || '',
    level: competitionLevel,
    competitionLevel,
    organizer: organizers.length ? organizerLabel(organizers[0]) : undefined,
    organizersJson: organizers.length ? JSON.stringify(organizers) : undefined,
    registrationStart: timelineSnapshot.registrationStart,
    registrationEnd: timelineSnapshot.registrationEnd,
    competitionStart: timelineSnapshot.competitionStart,
    competitionEnd: timelineSnapshot.competitionEnd,
    location: participationScope || 'TBD',
    participationScope,
    participationRequirement: trimOptional(values.participationRequirement),
    scheduleJson: timelineSnapshot.scheduleJson,
    description: trimOptional(values.description),
    imageUrl: trimOptional(values.imageUrl),
    contactName: trimOptional(values.contactName),
    contactQrCodeUrl: trimOptional(values.contactQrCodeUrl),
    tags: trimOptional(values.tags),
    status: values.status || 'draft',
    feeMode: values.feeMode,
    entryFeeMinor: Math.max(0, Math.round(Number(values.entryFeeMinor || 0) * 100)),
    currency: values.currency || 'CNY',
    featured: Boolean(values.featured),
    sort: values.sort ?? 100,
  };
};

export const recordToFormValues = (record: CompetitionRecord): Partial<CompetitionFormValues> => {
  const organizers = parseJsonArray<CompetitionOrganizerFormItem>(record.organizersJson);
  const draftBasicDefaults = normalizeCompetitionDraftBasicDefaults(record, organizers);
  const schedules = parseJsonArray<CompetitionJsonSchedule>(record.scheduleJson).map((item) => ({
    timeMode: normalizeTimeMode(item.timeMode),
    title: item.title,
    materialRange: item.timeMode === 'CONFIRMED' ? parseRange(item.materialStart, item.materialEnd) : undefined,
    reviewRange: item.timeMode === 'CONFIRMED' ? parseRange(item.reviewStart, item.reviewEnd) : undefined,
  }));

  return {
    code: record.code,
    locale: splitCompetitionLocales(record.locale),
    title: record.title,
    shortName: record.shortName || undefined,
    category: normalizeOptionValue(draftBasicDefaults.category) || undefined,
    level: normalizeOptionValue(record.level) || undefined,
    competitionLevel: normalizeOptionValue(record.competitionLevel || record.level) || undefined,
    organizer: normalizeMojibakeText(draftBasicDefaults.organizer),
    organizers: draftBasicDefaults.organizers,
    registrationRange: parseRange(record.registrationStart, record.registrationEnd),
    schedules: schedules.length ? schedules : [{ timeMode: 'TBD' }],
    competitionStart: record.competitionStart,
    competitionEnd: record.competitionEnd || undefined,
    participationScope: draftBasicDefaults.participationScope,
    participationRequirement: record.participationRequirement || undefined,
    description: record.description || undefined,
    contactName: record.contactName || undefined,
    contactQrCodeUrl: record.contactQrCodeUrl || undefined,
    imageUrl: record.imageUrl || undefined,
    tags: record.tags || undefined,
    status: record.status,
    feeMode: record.feeMode || undefined,
    entryFeeMinor: Number(record.entryFeeMinor || 0) / 100,
    currency: record.currency || 'CNY',
    sort: record.sort,
    featured: Boolean(record.featured),
  };
};

export const defaultCompetitionFormValues: Partial<CompetitionFormValues> = {
  locale: ['zh'],
  status: 'draft',
  entryFeeMinor: 0,
  currency: 'CNY',
  sort: 100,
  featured: false,
  organizers: [{ role: '', name: '' }],
  schedules: [{ timeMode: 'TBD', title: '' }],
};

const serializeDraftRangeValue = (
  range?: CompetitionFormValues['registrationRange'] | CompetitionScheduleFormItem['materialRange'],
): [string, string] | undefined => {
  if (!Array.isArray(range) || range.length !== 2) {
    return undefined;
  }
  const [start, end] = range;
  const normalizedStart = typeof start === 'string' ? start : formatRangeValue(start);
  const normalizedEnd = typeof end === 'string' ? end : formatRangeValue(end);
  return normalizedStart && normalizedEnd ? [normalizedStart, normalizedEnd] : undefined;
};

const serializeCompetitionCreateDraftValues = (values: Partial<CompetitionFormValues>): Partial<CompetitionFormValues> => {
  return {
    ...values,
    registrationRange: serializeDraftRangeValue(values.registrationRange),
    schedules: values.schedules?.map((schedule) => ({
      ...schedule,
      materialRange: serializeDraftRangeValue(schedule.materialRange),
      reviewRange: serializeDraftRangeValue(schedule.reviewRange),
    })),
  };
};

const restoreDraftRangeValue = (
  range?: CompetitionFormValues['registrationRange'] | CompetitionScheduleFormItem['materialRange'],
): [Dayjs, Dayjs] | undefined => {
  if (!Array.isArray(range) || range.length !== 2) {
    return undefined;
  }
  const [start, end] = range;
  if (dayjs.isDayjs(start) && dayjs.isDayjs(end)) {
    return [start, end];
  }
  return typeof start === 'string' && typeof end === 'string' ? parseRange(start, end) : undefined;
};

const toValidDayjs = (value?: Dayjs | string) => {
  if (!value) {
    return undefined;
  }
  const parsed = dayjs.isDayjs(value) ? value : dayjs(value);
  return parsed.isValid() ? parsed : undefined;
};

type CompetitionDateTimeRange = CompetitionFormValues['registrationRange'] | CompetitionScheduleFormItem['materialRange'];

type CompetitionDateTimeDisabledTime = (
  date: Dayjs,
  range: 'start' | 'end',
  info: { from?: Dayjs },
) => {
  disabledHours?: () => number[];
  disabledMinutes?: (hour: number) => number[];
  disabledSeconds?: (hour: number, minute: number) => number[];
};

export const CompetitionDateTimeRangePicker = ({
  value,
  onChange,
  disabledDate,
  minDate,
  maxDate,
}: {
  value?: CompetitionDateTimeRange;
  onChange?: (value?: [Dayjs, Dayjs]) => void;
  disabledDate?: (date: Dayjs) => boolean;
  minDate?: Dayjs;
  maxDate?: Dayjs;
}) => {
  const pickerRef = useRef<HTMLDivElement | null>(null);
  const [draftRange, setDraftRange] = useState<[Dayjs | null, Dayjs | null]>([null, null]);

  useEffect(() => {
    const [startDateTime, endDateTime] = value || [];
    setDraftRange([toValidDayjs(startDateTime) || null, toValidDayjs(endDateTime) || null]);
  }, [value]);

  const commitRange = (startDateTime?: Dayjs | null, endDateTime?: Dayjs | null) => {
    const nextRange: [Dayjs | null, Dayjs | null] = [startDateTime || null, endDateTime || null];
    setDraftRange(nextRange);
    onChange?.(startDateTime && endDateTime ? [startDateTime, endDateTime] : undefined);
  };

  const focusEndInput = () => {
    window.setTimeout(() => {
      pickerRef.current?.querySelector<HTMLInputElement>('input[placeholder="结束日期"]')?.focus();
    });
  };

  return (
    <div ref={pickerRef} className="competition-date-time-range-picker">
      <DatePicker.RangePicker
        value={draftRange}
        showTime={{
          format: 'HH:mm',
          disabledTime: ((date) => {
            const sameDayAsMin = Boolean(minDate && date.isSame(minDate, 'day'));
            const sameDayAsMax = Boolean(maxDate && date.isSame(maxDate, 'day'));
            const minHour = sameDayAsMin ? minDate?.hour() : undefined;
            const maxHour = sameDayAsMax ? maxDate?.hour() : undefined;

            return {
              disabledHours: () => Array.from({ length: 24 }, (_, hour) => hour).filter((hour) => (
                (minHour !== undefined && hour < minHour) || (maxHour !== undefined && hour > maxHour)
              )),
              disabledMinutes: (hour) => Array.from({ length: 60 }, (_, minute) => minute).filter((minute) => (
                (sameDayAsMin && minHour === hour && minDate && minute < minDate.minute())
                  || (sameDayAsMax && maxHour === hour && maxDate && minute > maxDate.minute())
              )),
              disabledSeconds: (hour, minute) => Array.from({ length: 60 }, (_, second) => second).filter((second) => (
                (sameDayAsMin && minHour === hour && minDate && minute === minDate.minute() && second < minDate.second())
                  || (sameDayAsMax && maxHour === hour && maxDate && minute === maxDate.minute() && second > maxDate.second())
              )),
            };
          }) as CompetitionDateTimeDisabledTime,
        }}
        format="YYYY-MM-DD HH:mm"
        placeholder={['开始日期', '结束日期']}
        placement="topRight"
        getPopupContainer={() => document.body}
        disabledDate={disabledDate}
        minDate={minDate}
        maxDate={maxDate}
        style={{ width: '100%' }}
        onCalendarChange={(dates) => {
          const [nextStartDateTime, nextEndDateTime] = dates || [];
          const normalizedStartDateTime = toValidDayjs(nextStartDateTime || undefined);
          const normalizedEndDateTime = toValidDayjs(nextEndDateTime || undefined);
          const [draftStartDateTime, draftEndDateTime] = draftRange;

          if (!normalizedStartDateTime) {
            commitRange();
            return;
          }

          if (!draftStartDateTime || draftEndDateTime) {
            setDraftRange([normalizedStartDateTime, null]);
            onChange?.(undefined);
            focusEndInput();
            return;
          }

          if (normalizedEndDateTime) {
            commitRange(normalizedStartDateTime, normalizedEndDateTime);
            return;
          }

          if (normalizedStartDateTime.isSame(draftStartDateTime)) {
            setDraftRange([draftStartDateTime, null]);
            focusEndInput();
            return;
          }

          const [startDateTime, endDateTime] = normalizedStartDateTime.isBefore(draftStartDateTime)
            ? [normalizedStartDateTime, draftStartDateTime]
            : [draftStartDateTime, normalizedStartDateTime];
          commitRange(startDateTime, endDateTime);
        }}
        onChange={(dates) => {
          if (!dates) {
            commitRange();
          }
        }}
      />
    </div>
  );
};

export const toPositiveId = (value: unknown) => {
  const id = Number(value);
  return Number.isFinite(id) && id > 0 ? id : undefined;
};

export const getCompleteTimeRange = (
  range?: CompetitionFormValues['registrationRange'] | CompetitionScheduleFormItem['materialRange'],
): [Dayjs, Dayjs] | undefined => {
  if (!Array.isArray(range) || range.length !== 2) {
    return undefined;
  }
  const start = toValidDayjs(range[0]);
  const end = toValidDayjs(range[1]);
  return start && end ? [start, end] : undefined;
};

export const getScheduleRangePickerBounds = (
  registrationRange?: CompetitionDateTimeRange,
  previousRange?: CompetitionDateTimeRange,
) => {
  const registrationBounds = getCompleteTimeRange(registrationRange);
  const previousBounds = getCompleteTimeRange(previousRange);
  const lowerBounds = [registrationBounds?.[0], previousBounds?.[1]].filter(
    (value): value is Dayjs => Boolean(value),
  );
  const minDate = lowerBounds.reduce<Dayjs | undefined>(
    (latest, value) => (!latest || value.isAfter(latest) ? value : latest),
    undefined,
  );
  return {
    minDate,
    maxDate: registrationBounds?.[1],
  };
};

export const isOutsideScheduleRangePickerBounds = (
  current: Dayjs,
  bounds: ReturnType<typeof getScheduleRangePickerBounds>,
) => Boolean(
  (bounds.minDate && current.isBefore(bounds.minDate, 'day'))
    || (bounds.maxDate && current.isAfter(bounds.maxDate, 'day'))
);

const restoreCompetitionCreateDraftValues = (values?: Partial<CompetitionFormValues>): Partial<CompetitionFormValues> => {
  if (!values) {
    return {};
  }
  return {
    ...values,
    registrationRange: restoreDraftRangeValue(values.registrationRange),
    schedules: values.schedules?.map((schedule) => ({
      ...schedule,
      timeMode: normalizeTimeMode(schedule.timeMode),
      materialRange: restoreDraftRangeValue(schedule.materialRange),
      reviewRange: restoreDraftRangeValue(schedule.reviewRange),
    })),
  };
};

export interface StoredUserDraft<T> {
  payload: T;
  updatedAt: number;
}

const readUserDraft = async <T,>(draftKey: string): Promise<T | undefined> => {
  const stored = await readUserDraftEnvelope<T>(draftKey);
  return stored?.payload;
};

const readUserDraftEnvelope = async <T,>(draftKey: string): Promise<StoredUserDraft<T> | undefined> => {
  const stored = await request<StoredUserDraft<T> | null>(`/v2/user-drafts/${draftKey}`, {
    method: 'GET',
    silent: true,
  });
  return stored || undefined;
};

const writeUserDraft = async <T,>(draftKey: string, draft: T) => {
  return request<StoredUserDraft<T>>(`/v2/user-drafts/${draftKey}`, {
    method: 'PUT',
    data: draft,
    silent: true,
  });
};

export const clearUserDraft = async (draftKey: string) => {
  await request<void>(`/v2/user-drafts/${draftKey}`, {
    method: 'DELETE',
    silent: true,
  });
};

const readCompetitionCreateDraft = () => readUserDraft<CompetitionCreateDraftStorage>(COMPETITION_CREATE_DRAFT_STORAGE_KEY);
const writeCompetitionCreateDraft = (draft: CompetitionCreateDraftStorage) => writeUserDraft(COMPETITION_CREATE_DRAFT_STORAGE_KEY, draft);
const clearCompetitionCreateDraft = () => clearUserDraft(COMPETITION_CREATE_DRAFT_STORAGE_KEY);
export const readCompetitionRegistrationDraftEnvelope = (draftKey: string) => readUserDraftEnvelope<CompetitionRegistrationDraftStorage>(draftKey);
export const writeCompetitionRegistrationDraft = (draftKey: string, draft: CompetitionRegistrationDraftStorage) => writeUserDraft(draftKey, draft);
export const clearCompetitionRegistrationDraft = (draftKey: string) => clearUserDraft(draftKey);
export const readCompetitionRegistrationDocumentAcceptance = (competitionUuid: string) =>
  readUserDraft<CompetitionRegistrationDocumentAcceptanceStorage>(
    buildRegistrationDocumentAcceptanceStorageKey(competitionUuid),
  );
export const writeCompetitionRegistrationDocumentAcceptance = (
  competitionUuid: string,
  acceptedDocumentKeys: string[],
) => writeUserDraft(
  buildRegistrationDocumentAcceptanceStorageKey(competitionUuid),
  { acceptedDocumentKeys, savedAt: Date.now() },
);

export const hasCompetitionRegistrationDraftContent = (values: Partial<RegistrationFormValues>) => {
  const members = values.newTeam?.initialMembers || [];
  const materials = values.materials || {};
  return Boolean(
    toPositiveId(values.competitionId)
      || Object.values(values.registrationExtraValues || {}).some((value) => value !== undefined && value !== null && String(value).trim())
      || toPositiveId(values.teamId)
      || trimOptional(values.newTeamName)
      || trimOptional(values.newTeam?.avatarUrl)
      || trimOptional(values.newTeam?.description)
      || Object.values(values.newTeam?.extraValues || {}).some((value) => value !== undefined && value !== null && String(value).trim())
      || members.some((member) => (
        trimOptional(member.memberName)
        || trimOptional(member.employeeNo)
        || trimOptional(member.departmentName)
        || trimOptional(member.remark)
        || Object.values(member.extraValues || {}).some((value) => trimOptional(value))
      ))
      || toPositiveId(values.projectId)
      || trimOptional(values.newProjectTitle)
      || trimOptional(values.newProjectImageUrl)
      || trimOptional(values.newProjectDescription)
      || hasRegistrationIntellectualPropertyContent(values.newProjectExtraValues)
      || Object.values(materials).some((value) => value !== undefined && value !== null && String(value).trim())
  );
};

const hasCompetitionCreateDraftContent = (values: Partial<CompetitionFormValues>) => {
  const organizers = values.organizers || [];
  const schedules = values.schedules || [];
  return Boolean(
    trimOptional(values.title)
      || trimOptional(values.shortName)
      || organizers.some((organizer) => trimOptional(organizer.role) || trimOptional(organizer.name))
      || normalizeOptionValue(values.category)
      || normalizeOptionValue(values.competitionLevel || values.level)
      || trimOptional(values.participationScope)
      || trimOptional(values.participationRequirement)
      || trimOptional(values.contactName)
      || trimOptional(values.contactQrCodeUrl)
      || trimOptional(values.imageUrl)
      || trimOptional(values.tags)
      || getCompleteTimeRange(values.registrationRange)
      || schedules.some((schedule) => (
        normalizeTimeMode(schedule.timeMode) === 'CONFIRMED'
        || trimOptional(schedule.title)
        || getCompleteTimeRange(schedule.materialRange)
        || getCompleteTimeRange(schedule.reviewRange)
      ))
  );
};

const getAllowedCompetitionCreateStep = (requestedStep: number, values: Partial<CompetitionFormValues>, acceptedTerms: boolean) => {
  const normalizedStep = Math.max(0, Math.min(requestedStep, competitionCreateSteps.length - 1));
  if (normalizedStep <= 0) {
    return 0;
  }
  if (!acceptedTerms) {
    return 0;
  }
  if (normalizedStep <= 1) {
    return 1;
  }
  return getCompetitionCreateMissingFields(values).length ? 1 : normalizedStep;
};

const parseFeaturedFilter = (value: unknown) => {
  if (typeof value === 'boolean') {
    return value;
  }
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  return undefined;
};

const competitionTableRequest = buildTableRequest<CompetitionRecord>(async (params) =>
  listCompetitions({
    keyword: typeof params.keyword === 'string' ? params.keyword : undefined,
    category: typeof params.category === 'string' ? params.category : undefined,
    locale: params.locale as string | undefined,
    status: params.status as CompetitionStatus | undefined,
    featured: parseFeaturedFilter(params.featured),
    pageNo: params.pageNo,
    pageSize: params.pageSize,
  }),
);

const _CompetitionSharedBasicFields = ({
  categoryOptions,
  levelOptions,
  onListChange,
}: {
  categoryOptions: Array<{ label: string; value: string }>;
  levelOptions: Array<{ label: string; value: string }>;
  onListChange?: () => void;
}) => (
  <>
    <section className="competition-basic-section">
      <Typography.Title className="competition-basic-section__title" level={5}>
        基础信息
      </Typography.Title>
      <div className="competition-basic-section__grid">
        <Form.Item name="title" label="竞赛名称" rules={[{ required: true, message: '请输入竞赛名称' }]}>
          <Input maxLength={128} placeholder="请输入竞赛名称" />
        </Form.Item>
        <Form.Item name="shortName" label="竞赛简称">
          <Input maxLength={128} placeholder="请输入竞赛简称" />
        </Form.Item>
        <Form.Item name="category" label="竞赛类别" rules={[{ required: true, message: '请选择竞赛类别' }]}>
          <Select options={categoryOptions} placeholder="请选择竞赛类别" />
        </Form.Item>
        <Form.Item name="competitionLevel" label="竞赛级别" rules={[{ required: true, message: '请选择竞赛级别' }]}>
          <Select options={levelOptions} placeholder="请选择竞赛级别" />
        </Form.Item>
      </div>
    </section>

    <section className="competition-basic-section">
      <Typography.Title className="competition-basic-section__title" level={5}>
        组织与参赛
      </Typography.Title>
      <Form.List name="organizers">
        {(fields, { add, remove }) => (
          <Form.Item className="competition-organizer-list" label="组织者" required>
            <Space orientation="vertical" size={12} className="competition-dynamic-list">
              {fields.map((field, index) => (
                <div key={field.key} className="competition-dynamic-list__row">
                  <Form.Item name={[field.name, 'role']} rules={[{ required: true, message: '请输入组织者类型' }]} className="competition-dynamic-list__role">
                    <Input maxLength={64} placeholder="例如：主办方" />
                  </Form.Item>
                  <Form.Item name={[field.name, 'name']} rules={[{ required: true, message: '请输入组织者名称' }]} className="competition-dynamic-list__main">
                    <Input maxLength={128} placeholder="例如：大学赛事组委会" />
                  </Form.Item>
                  <div className="competition-dynamic-list__actions">
                    {index === fields.length - 1 ? (
                      <Button
                        aria-label="添加组织者"
                        title="添加组织者"
                        icon={<PlusOutlined />}
                        onClick={() => {
                          add({ role: '', name: '' });
                          onListChange?.();
                        }}
                      />
                    ) : null}
                    <Popconfirm
                      title="确认移除该组织者？"
                      okText="确认移除"
                      cancelText="取消"
                      onConfirm={() => {
                        remove(field.name);
                        onListChange?.();
                      }}
                    >
                      <Button
                        aria-label="移除组织者"
                        title="移除组织者"
                        icon={<DeleteOutlined />}
                        disabled={fields.length <= 1}
                      />
                    </Popconfirm>
                  </div>
                </div>
              ))}
            </Space>
          </Form.Item>
        )}
      </Form.List>
      <div className="competition-basic-section__grid">
        <Form.Item className="competition-basic-section__full" name="participationScope" label="参赛范围" rules={[{ required: true, message: '请输入参赛范围' }]}>
          <Input maxLength={255} placeholder="请输入参赛范围" />
        </Form.Item>
        <Form.Item className="competition-basic-section__full" name="participationRequirement" label="参赛要求">
          <Input.TextArea rows={4} placeholder="请输入参赛要求" />
        </Form.Item>
      </div>
    </section>
  </>
);

const CompetitionBasicFields = ({
  form,
  categoryOptions,
  levelOptions,
  onDraftChange,
}: {
  form: FormInstance<CompetitionFormValues>;
  categoryOptions: Array<{ label: string; value: string }>;
  levelOptions: Array<{ label: string; value: string }>;
  onDraftChange?: () => void;
}) => {
  const schedules = Form.useWatch('schedules', form) || [];
  const registrationRange = Form.useWatch('registrationRange', form);

  return (
    <>
      <Form.Item name="title" label="竞赛名称" rules={[{ required: true, message: '请输入竞赛名称' }]}>
        <Input maxLength={128} placeholder="请输入竞赛名称" />
      </Form.Item>
      <Form.Item name="shortName" label="竞赛简称">
        <Input maxLength={128} placeholder="请输入竞赛简称" />
      </Form.Item>
      <Form.List name="organizers">
        {(fields, { add, remove }) => (
          <Form.Item label="组织者" required>
            <Space orientation="vertical" size={12} className="competition-dynamic-list">
              {fields.map((field, index) => (
                <div key={field.key} className="competition-dynamic-list__row">
                  <Form.Item name={[field.name, 'role']} rules={[{ required: true, message: '请输入组织者类型' }]} className="competition-dynamic-list__role">
                    <Input maxLength={64} placeholder="例如：主办方" />
                  </Form.Item>
                  <Form.Item name={[field.name, 'name']} rules={[{ required: true, message: '请输入组织者名称' }]} className="competition-dynamic-list__main">
                    <Input maxLength={128} placeholder="例如：大学赛事组委会" />
                  </Form.Item>
                  <div className="competition-dynamic-list__actions">
                    {index === fields.length - 1 ? (
                      <Button aria-label="添加组织者" title="添加组织者" icon={<PlusOutlined />} onClick={() => add({ role: '', name: '' })} />
                    ) : null}
                    <Popconfirm
                      title="确认移除该组织者？"
                      okText="确认移除"
                      cancelText="取消"
                      onConfirm={() => remove(field.name)}
                    >
                      <Button aria-label="移除组织者" title="移除组织者" icon={<DeleteOutlined />} disabled={fields.length <= 1} />
                    </Popconfirm>
                  </div>
                </div>
              ))}
            </Space>
          </Form.Item>
        )}
      </Form.List>
      <Form.Item name="category" label="竞赛类别" rules={[{ required: true, message: '请选择竞赛类别' }]}>
        <Select options={categoryOptions} placeholder="请选择竞赛类别" />
      </Form.Item>
      <Form.Item name="competitionLevel" label="竞赛级别" rules={[{ required: true, message: '请选择竞赛级别' }]}>
        <Select options={levelOptions} placeholder="请选择竞赛级别" />
      </Form.Item>
      <Form.Item name="participationScope" label="参赛范围" rules={[{ required: true, message: '请输入参赛范围' }]}>
        <Input maxLength={255} placeholder="请输入参赛范围" />
      </Form.Item>
      <Space size="middle" className="competition-inline-fields" align="start">
        <Form.Item name="feeMode" label="收费方式" rules={[{ required: true, message: '请选择收费方式' }]} className="competition-inline-fields__item">
          <Select options={feeModeOptions} placeholder="请选择收费方式" />
        </Form.Item>
        <Form.Item name="entryFeeMinor" label="参赛费用（元）" rules={[{ required: true, message: '请输入参赛费用' }]} className="competition-inline-fields__item">
          <InputNumber min={0} precision={2} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="currency" label="货币" rules={[{ required: true, message: '请选择货币' }]} className="competition-inline-fields__item">
          <Select options={[{ label: 'CNY', value: 'CNY' }]} />
        </Form.Item>
      </Space>
      <Form.Item
        name="registrationRange"
        label="报名时间"
        rules={[
          { required: true, message: '请选择报名时间' },
          {
            validator: (_, value: CompetitionFormValues['registrationRange']) =>
              getCompleteTimeRange(value) ? Promise.resolve() : Promise.reject(new Error('请选择报名开始和结束时间')),
          },
        ]}
      >
        <CompetitionDateTimeRangePicker />
      </Form.Item>
      <Form.List name="schedules">
        {(fields, { add, remove }) => (
          <Form.Item label="竞赛安排" required>
            {fields.length ? (
              <div className="competition-schedule-list">
                <Form.Item name={[fields[0].name, 'timeMode']} rules={[{ required: true, message: '请选择时间状态' }]} className="competition-schedule-status">
                  <Radio.Group
                    options={timeModeOptions}
                    onChange={(event) => {
                      const nextMode = event.target.value as CompetitionTimeMode;
                      const currentSchedules = form.getFieldValue('schedules') || [];
                      if (nextMode === 'CONFIRMED') {
                        form.setFieldValue('schedules', [{ ...currentSchedules[0], timeMode: 'CONFIRMED' }]);
                        onDraftChange?.();
                        return;
                      }
                      form.setFieldValue('schedules', [{ timeMode: 'TBD' }]);
                      onDraftChange?.();
                    }}
                  />
                </Form.Item>
                {schedules[0]?.timeMode === 'CONFIRMED' ? (
                  <Space orientation="vertical" size={8} className="competition-dynamic-list">
                    {fields.map((field, index) => (
                      <div key={field.key} className="competition-schedule-row">
                        <Form.Item
                          name={[field.name, 'title']}
                          label="阶段名称"
                          rules={[{ required: true, message: '请输入阶段名称' }]}
                          className="competition-schedule-row__title"
                        >
                          <Input maxLength={128} placeholder="例如：初赛" />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'materialRange']}
                          dependencies={['registrationRange']}
                          label="提交材料时间"
                          rules={[
                            { required: true, message: '请选择提交材料时间' },
                            {
                              validator: (_, value: CompetitionScheduleFormItem['materialRange']) => {
                                if (!isChronologicalTimeRange(value)) {
                                  return Promise.reject(new Error('材料提交结束时间必须晚于开始时间'));
                                }
                                if (!getCompleteTimeRange(registrationRange)) {
                                  return Promise.reject(new Error('请先选择报名时间'));
                                }
                                return isTimeRangeWithinBounds(value, registrationRange)
                                  ? Promise.resolve()
                                  : Promise.reject(new Error('提交材料时间必须在报名时间范围内'));
                              },
                            },
                          ]}
                          className="competition-schedule-row__material-time"
                        >
                          <CompetitionDateTimeRangePicker
                            minDate={getScheduleRangePickerBounds(registrationRange).minDate}
                            maxDate={getScheduleRangePickerBounds(registrationRange).maxDate}
                            disabledDate={(current) => isOutsideScheduleRangePickerBounds(
                              current,
                              getScheduleRangePickerBounds(registrationRange),
                            )}
                          />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'reviewRange']}
                          label="评审时间"
                          dependencies={[['registrationRange'], ['schedules', field.name, 'materialRange']]}
                          rules={[
                            { required: true, message: '请选择评审时间' },
                            {
                              validator: (_, value: CompetitionScheduleFormItem['reviewRange']) => {
                                if (!isChronologicalTimeRange(value)) {
                                  return Promise.reject(new Error('评审结束时间必须晚于开始时间'));
                                }
                                if (!isTimeRangeWithinBounds(value, registrationRange)) {
                                  return Promise.reject(new Error('评审时间必须在报名时间范围内'));
                                }
                                const materialRange = form.getFieldValue(['schedules', field.name, 'materialRange']);
                                if (!getCompleteTimeRange(materialRange)) {
                                  return Promise.reject(new Error('请先选择提交材料时间'));
                                }
                                return isTimeRangeAtOrAfterPreviousEnd(value, materialRange)
                                  ? Promise.resolve()
                                  : Promise.reject(new Error('评审开始时间不得早于材料提交截止时间'));
                              },
                            },
                          ]}
                          className="competition-schedule-row__review-time"
                        >
                          <CompetitionDateTimeRangePicker
                            minDate={getScheduleRangePickerBounds(
                              registrationRange,
                              form.getFieldValue(['schedules', field.name, 'materialRange']),
                            ).minDate}
                            maxDate={getScheduleRangePickerBounds(registrationRange).maxDate}
                            disabledDate={(current) => isOutsideScheduleRangePickerBounds(
                              current,
                              getScheduleRangePickerBounds(
                                registrationRange,
                                form.getFieldValue(['schedules', field.name, 'materialRange']),
                              ),
                            )}
                          />
                        </Form.Item>
                        <div className="competition-schedule-row__actions">
                          {index === fields.length - 1 ? (
                            <Button
                              aria-label="添加竞赛安排"
                              title="添加竞赛安排"
                              icon={<PlusOutlined />}
                              onClick={() => add({ timeMode: 'CONFIRMED', title: '' })}
                            />
                          ) : null}
                          <Popconfirm
                            title="确认删除该竞赛安排？"
                            okText="确认删除"
                            cancelText="取消"
                            onConfirm={() => remove(field.name)}
                          >
                            <Button aria-label="删除竞赛安排" title="删除竞赛安排" icon={<DeleteOutlined />} disabled={fields.length <= 1} />
                          </Popconfirm>
                        </div>
                      </div>
                    ))}
                  </Space>
                ) : null}
              </div>
            ) : null}
          </Form.Item>
        )}
      </Form.List>
      <Form.Item name="code" hidden>
        <Input />
      </Form.Item>
    </>
  );
};

const _CompetitionForm = ({
  form,
  categoryOptions,
  levelOptions,
}: {
  form: FormInstance<CompetitionFormValues>;
  categoryOptions: Array<{ label: string; value: string }>;
  levelOptions: Array<{ label: string; value: string }>;
}) => (
  <Form<CompetitionFormValues> form={form} layout="vertical" initialValues={defaultCompetitionFormValues}>
    <CompetitionBasicFields form={form} categoryOptions={categoryOptions} levelOptions={levelOptions} />
  </Form>
);

const CreateCompetitionPage = () => {
  const location = useLocation();
  const actionPermission = useActionPermission();
  const fallbackDictOptions = useCompetitionDictFallbackOptions();
  const { options: categoryOptions } = useDictOptions(COMPETITION_CATEGORY_DICT, fallbackDictOptions.categoryOptions);
  const { options: levelOptions } = useDictOptions(COMPETITION_LEVEL_DICT, fallbackDictOptions.levelOptions);
  const [form] = Form.useForm<CompetitionFormValues>();
  const [currentStep, setCurrentStep] = useState(0);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [draftSavedAt, setDraftSavedAt] = useState<number>();
  const [draftHydrated, setDraftHydrated] = useState(false);
  const [draftRecord, setDraftRecord] = useState<CompetitionRecord>();
  const [createdCompetition, setCreatedCompetition] = useState<CompetitionRecord>();
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const draftSavingRef = useRef(false);
  const latestDraftPayloadRef = useRef<CompetitionCreateDraftStorage | undefined>(undefined);

  const collectCompetitionCreateValues = useCallback(() => ({
    ...defaultCompetitionFormValues,
    ...(form.getFieldsValue(true) as Partial<CompetitionFormValues>),
  }), [form]);

  const writeCurrentDraftState = useCallback((
    nextValues: Partial<CompetitionFormValues> = collectCompetitionCreateValues(),
    nextStep = currentStep,
    nextTermsAccepted = termsAccepted,
    nextDraftRecord = draftRecord,
  ) => {
    const normalizedValues = {
      ...defaultCompetitionFormValues,
      ...nextValues,
    };
    const savedAt = Date.now();
    const draftState: CompetitionCreateDraftStorage = {
      competitionId: nextDraftRecord?.id,
      competitionUuid: nextDraftRecord?.uuid,
      competitionNo: nextDraftRecord?.competitionNo,
      currentStep: nextStep,
      termsAccepted: nextTermsAccepted,
      savedAt,
      values: serializeCompetitionCreateDraftValues(normalizedValues),
    };
    void writeCompetitionCreateDraft(draftState);
    setDraftSavedAt(savedAt);
    return draftState;
  }, [collectCompetitionCreateValues, currentStep, draftRecord, termsAccepted]);

  const flushCompetitionCreateDraft = useCallback(async (draftState?: CompetitionCreateDraftStorage) => {
    const currentDraftState = draftState || latestDraftPayloadRef.current;
    if (!currentDraftState || draftSavingRef.current) {
      return;
    }
    latestDraftPayloadRef.current = currentDraftState;
    if (!hasCompetitionCreateDraftContent(currentDraftState.values || {})) {
      writeCurrentDraftState(currentDraftState.values, currentDraftState.currentStep, currentDraftState.termsAccepted);
      return;
    }

    draftSavingRef.current = true;
    let pendingDraftState: CompetitionCreateDraftStorage | undefined;
    try {
      const payload = normalizePayload({
        ...defaultCompetitionFormValues,
        ...restoreCompetitionCreateDraftValues(currentDraftState.values),
      } as CompetitionFormValues);
      const saved = currentDraftState.competitionId
        ? await updateCompetitionDraft(currentDraftState.competitionId, payload)
        : await createCompetitionDraft(payload);
      setDraftRecord(saved);
      const savedAt = Date.now();
      pendingDraftState = latestDraftPayloadRef.current && latestDraftPayloadRef.current !== currentDraftState
        ? latestDraftPayloadRef.current
        : undefined;
      const persistedDraftState: CompetitionCreateDraftStorage = {
        ...currentDraftState,
        competitionId: saved.id,
        competitionUuid: saved.uuid,
        competitionNo: saved.competitionNo,
        savedAt,
      };
      const nextDraftState = pendingDraftState
        ? {
            ...pendingDraftState,
            competitionId: saved.id,
            competitionUuid: saved.uuid,
            competitionNo: saved.competitionNo,
          }
        : persistedDraftState;
      await writeCompetitionCreateDraft(nextDraftState);
      latestDraftPayloadRef.current = nextDraftState;
      setDraftSavedAt(savedAt);
    } catch (error) {
      showErrorMessage(error, '赛事草稿自动保存失败');
    } finally {
      draftSavingRef.current = false;
      if (pendingDraftState) {
        void flushCompetitionCreateDraft(latestDraftPayloadRef.current);
      }
    }
  }, [writeCurrentDraftState]);

  const persistCompetitionCreateDraft = useCallback((
    nextValues: Partial<CompetitionFormValues> = collectCompetitionCreateValues(),
    nextStep = currentStep,
    nextTermsAccepted = termsAccepted,
  ) => {
    const draftState = writeCurrentDraftState(nextValues, nextStep, nextTermsAccepted);
    latestDraftPayloadRef.current = draftState;
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
    }
    saveTimerRef.current = setTimeout(() => {
      void flushCompetitionCreateDraft(draftState);
    }, 600);
  }, [collectCompetitionCreateValues, currentStep, flushCompetitionCreateDraft, termsAccepted, writeCurrentDraftState]);

  useEffect(() => {
    let cancelled = false;
    void readCompetitionCreateDraft().then((draft) => {
    if (cancelled) return;
    if (draft && !draft.competitionId && !draft.competitionUuid) {
      void clearCompetitionCreateDraft();
    }
    const nextValues = {
      ...defaultCompetitionFormValues,
      ...(draft?.competitionId || draft?.competitionUuid ? restoreCompetitionCreateDraftValues(draft?.values) : {}),
    };
    form.resetFields();
    form.setFieldsValue(nextValues);
    if (draft?.competitionId || draft?.competitionUuid) {
      setDraftRecord({
        id: draft.competitionId || 0,
        uuid: draft.competitionUuid,
        competitionNo: draft.competitionNo,
      } as CompetitionRecord);
      setTermsAccepted(Boolean(draft.termsAccepted));
      setDraftSavedAt(draft.savedAt);
    } else {
      setTermsAccepted(false);
      setDraftSavedAt(undefined);
    }
    setDraftHydrated(true);
    }).catch(() => {
      if (!cancelled) setDraftHydrated(true);
    });
    return () => { cancelled = true; };
  }, [form]);

  useEffect(() => () => {
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
    }
  }, []);

  useEffect(() => {
    if (!draftHydrated) {
      return;
    }
    const requestedStep = parseCompetitionCreateStepFromSearch(location.search);
    const values = collectCompetitionCreateValues();
    const allowedStep = getAllowedCompetitionCreateStep(requestedStep, values, termsAccepted);
    if (allowedStep !== requestedStep) {
      setCurrentStep(allowedStep);
      history.replace({
        pathname: '/competitions/create',
        search: createCompetitionStepSearch(allowedStep),
      });
      message.warning(allowedStep === 0 ? '请先阅读并同意赛事发布条款' : '请先补全基本信息');
      return;
    }
    const canonicalSearch = createCompetitionStepSearch(requestedStep);
    if (location.search !== canonicalSearch) {
      history.replace({
        pathname: '/competitions/create',
        search: canonicalSearch,
      });
    }
    setCurrentStep((currentValue) => (currentValue === requestedStep ? currentValue : requestedStep));
  }, [collectCompetitionCreateValues, draftHydrated, location.search, termsAccepted]);

  const setCompetitionCreateStep = (nextStep: number) => {
    const normalizedStep = Math.max(0, Math.min(nextStep, competitionCreateSteps.length - 1));
    persistCompetitionCreateDraft(collectCompetitionCreateValues(), normalizedStep);
    setCurrentStep(normalizedStep);
    history.push({
      pathname: '/competitions/create',
      search: createCompetitionStepSearch(normalizedStep),
    });
  };

  if (!actionPermission.can('aiadc:competition:create')) {
    return (
      <ManagementPage title="新增赛事" extra={<Button onClick={() => history.push('/competitions/management')}>返回</Button>}>
        <ManagementPageBody>
          <Alert type="error" showIcon title="暂无新增赛事权限" />
        </ManagementPageBody>
      </ManagementPage>
    );
  }

  const goNext = async () => {
    if (currentStep === 0) {
      if (!termsAccepted) {
        message.warning('请先阅读并同意赛事发布条款');
        return;
      }
      setCompetitionCreateStep(1);
      return;
    }
  };

  const submit = async () => {
    const values = collectCompetitionCreateValues();
    const missingFields = getCompetitionCreateMissingFields(values);
    if (missingFields.length) {
      message.error(`请先补全：${missingFields[0]}`);
      persistCompetitionCreateDraft(values, 1);
      setCompetitionCreateStep(1);
      return;
    }
    setSaving(true);
    try {
      if (saveTimerRef.current) {
        clearTimeout(saveTimerRef.current);
      }
      await flushCompetitionCreateDraft(latestDraftPayloadRef.current);
      const activeDraftId = draftRecord?.id || latestDraftPayloadRef.current?.competitionId;
      const created = activeDraftId
        ? await updateCompetition(activeDraftId, normalizePayload(values as CompetitionFormValues))
        : await createCompetition(normalizePayload(values as CompetitionFormValues));
      clearCompetitionCreateDraft();
      latestDraftPayloadRef.current = undefined;
      setDraftRecord(undefined);
      setDraftSavedAt(undefined);
      setCreatedCompetition(created);
      message.success('赛事已新增');
    } catch (error) {
      showErrorMessage(error, '赛事保存失败');
    } finally {
      setSaving(false);
    }
  };

  if (createdCompetition) {
    const competitionNo = createdCompetition.competitionNo || createdCompetition.code;
    return (
      <ManagementPage
        title={formatMessage({ id: 'page.competition.create.success.pageTitle', defaultMessage: 'Competition Created' })}
        extra={
          <Button onClick={() => history.push('/competitions/management')}>
            {formatMessage({ id: 'page.competition.create.success.backToList', defaultMessage: 'Back to list' })}
          </Button>
        }
      >
        <ManagementPageBody>
          <Card>
            <Result
              status="success"
              title={formatMessage({ id: 'page.competition.create.success.title', defaultMessage: 'Competition created' })}
              subTitle={formatMessage(
                { id: 'page.competition.create.success.subtitle', defaultMessage: '{title} 路 No. {competitionNo}' },
                { title: createdCompetition.title, competitionNo },
              )}
              extra={[
                <Button
                  key="continue"
                  onClick={() => {
                    form.resetFields();
                    form.setFieldsValue(defaultCompetitionFormValues);
                    clearCompetitionCreateDraft();
                    latestDraftPayloadRef.current = undefined;
                    setDraftRecord(undefined);
                    setDraftSavedAt(undefined);
                    setTermsAccepted(false);
                    setCurrentStep(0);
                    setCreatedCompetition(undefined);
                    history.push({ pathname: '/competitions/create', search: createCompetitionStepSearch(0) });
                  }}
                >
                  {formatMessage({ id: 'page.competition.create.success.continue', defaultMessage: 'Continue creating' })}
                </Button>,
                <Button
                  key="settings"
                  type="primary"
                  disabled={!createdCompetition.uuid}
                  onClick={() => createdCompetition.uuid && history.push(`/competitions/${createdCompetition.uuid}/settings`)}
                >
                  {formatMessage({ id: 'page.competition.create.success.settings', defaultMessage: 'Enter detailed settings' })}
                </Button>,
              ]}
            />
          </Card>
        </ManagementPageBody>
      </ManagementPage>
    );
  }

  return (
    <ManagementPage title="新增赛事" extra={<Button onClick={() => history.push('/competitions/management')}>返回赛事管理</Button>}>
      <ManagementPageBody className="competition-create-page">
        <Card className="competition-create-shell">
          <Steps current={currentStep} items={getCompetitionCreateSteps()} responsive />
          <Form<CompetitionFormValues>
            form={form}
            layout="vertical"
            initialValues={defaultCompetitionFormValues}
            onValuesChange={() => persistCompetitionCreateDraft()}
          >
            <div className="competition-create-step">
              {currentStep === 0 ? (
                <div className="competition-create-terms">
                  <Alert type="info" showIcon title="请先阅读赛事发布条款，确认后继续填写基本信息。" />
                  <div className="competition-create-terms__content">
                    <XMarkdown content={competitionTermsMarkdown} openLinksInNewTab escapeRawHtml />
                  </div>
                  <Checkbox
                    checked={termsAccepted}
                    onChange={(event) => {
                      const nextChecked = event.target.checked;
                      setTermsAccepted(nextChecked);
                      persistCompetitionCreateDraft(collectCompetitionCreateValues(), currentStep, nextChecked);
                    }}
                  >
                    我已阅读并同意赛事发布条款
                  </Checkbox>
                </div>
              ) : null}
              {currentStep === 1 ? (
                <CompetitionBasicFields
                  form={form}
                  categoryOptions={categoryOptions as Array<{ label: string; value: string }>}
                  levelOptions={levelOptions as Array<{ label: string; value: string }>}
                  onDraftChange={() => persistCompetitionCreateDraft()}
                />
              ) : null}
            </div>
          </Form>
          <div className="competition-create-actions">
            {draftSavedAt ? (
              <Typography.Text className="competition-create-draft-status" type="secondary">
                草稿已自动保存
              </Typography.Text>
            ) : null}
            {currentStep > 0 ? <Button onClick={() => setCompetitionCreateStep(currentStep - 1)}>上一步</Button> : null}
            {currentStep < competitionCreateSteps.length - 1 ? (
              <Button type="primary" onClick={() => void goNext()}>
                下一步
              </Button>
            ) : (
              <Button type="primary" loading={saving} onClick={() => void submit()}>
                提交
              </Button>
            )}
          </div>
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

const CompetitionPage = () => {
  const location = useLocation();
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const fallbackDictOptions = useCompetitionDictFallbackOptions();
  const { options: categoryOptions } = useDictOptions(COMPETITION_CATEGORY_DICT, fallbackDictOptions.categoryOptions);
  const { options: levelOptions } = useDictOptions(COMPETITION_LEVEL_DICT, fallbackDictOptions.levelOptions);
  const categoryLabelMap = useMemo(() => buildOptionLabelMap(categoryOptions), [categoryOptions]);
  const levelLabelMap = useMemo(() => buildOptionLabelMap(levelOptions), [levelOptions]);
  const actionRef = useRef<ActionType | undefined>(undefined);

  useEffect(() => {
    if (location.pathname === '/competitions') {
      history.replace('/competitions/management');
    }
  }, [location.pathname]);

  const openCreateDrawer = () => {
    clearCompetitionCreateDraft();
    history.push({
      pathname: '/competitions/create',
      search: createCompetitionStepSearch(0),
    });
  };

  const toggleCompetitionStatus = useCallback(async (record: CompetitionRecord) => {
    if (record.status === 'archived') {
      return;
    }
    const nextStatus: CompetitionStatus = record.status === 'published' ? 'draft' : 'published';
    try {
      await updateCompetition(
        record.id,
        normalizePayload(
          { ...recordToFormValues(record), status: nextStatus } as CompetitionFormValues,
          { preserveTimelineFrom: record },
        ),
      );
      message.success(nextStatus === 'published' ? '赛事已发布' : '赛事已切换为草稿');
      actionRef.current?.reload();
    } catch (error) {
      showErrorMessage(error, '状态切换失败');
    }
  }, []);

  const columns = useMemo<ProColumns<CompetitionRecord>[]>(
    () => [
      {
        title: '赛事查询',
        dataIndex: 'keyword',
        hideInTable: true,
        fieldProps: {
          placeholder: '输入赛事名称/编码/主办方',
        },
      },
      {
        title: '编号',
        dataIndex: 'competitionNo',
        search: false,
        width: 180,
        ellipsis: true,
        render: (_, record) => record.competitionNo || record.code || '-',
      },
      {
        title: '赛事',
        dataIndex: 'title',
        search: false,
        minWidth: 260,
        render: (_, record) => (
          <Typography.Text strong className="competition-name-cell">{record.title}</Typography.Text>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueType: 'select',
        valueEnum: {
          draft: { text: statusText.draft },
          published: { text: statusText.published },
          archived: { text: statusText.archived },
        },
        width: 110,
        render: (_, record) => <Tag color={statusColor[record.status]}>{statusText[record.status]}</Tag>,
      },
      {
        title: '类别',
        dataIndex: 'category',
        valueType: 'select',
        responsive: ['sm', 'md', 'lg', 'xl', 'xxl'],
        fieldProps: {
          options: categoryOptions,
          showSearch: true,
          optionFilterProp: 'label',
        },
        render: (_, record) => {
          const categoryLabel = resolveOptionLabel(categoryLabelMap, record.category);
          return categoryLabel ? <Tag color="blue">{categoryLabel}</Tag> : '-';
        },
      },
      {
        title: '级别',
        dataIndex: 'competitionLevel',
        search: false,
        responsive: ['md', 'lg', 'xl', 'xxl'],
        render: (_, record) => resolveOptionLabel(levelLabelMap, record.competitionLevel || record.level) || '-',
      },
      {
        title: '收费',
        dataIndex: 'feeMode',
        search: false,
        width: 140,
        responsive: ['sm', 'md', 'lg', 'xl', 'xxl'],
        render: (_, record) => {
          const amount = Number(record.entryFeeMinor || 0) / 100;
          return `${record.feeMode === 'MEMBER' ? '按人数' : '按团队'} / ${amount.toFixed(2)} ${record.currency || 'CNY'}`;
        },
      },
      {
        title: '组织者',
        dataIndex: 'organizer',
        search: false,
        ellipsis: true,
        responsive: ['md', 'lg', 'xl', 'xxl'],
        render: (_, record) => normalizeMojibakeText(record.organizer) || '-',
      },
      {
        title: '语言',
        dataIndex: 'locale',
        valueType: 'select',
        valueEnum: {
          zh: { text: '中文' },
          en: { text: 'English' },
        },
        width: 96,
        responsive: ['md', 'lg', 'xl', 'xxl'],
        render: (_, record) => {
          const locales = splitCompetitionLocales(record.locale);
          if (!locales.length) {
            return '-';
          }
          return (
            <Space size={4} wrap>
              {locales.map((item) => (
                <Tag key={item}>{item === 'zh' ? '中文' : 'English'}</Tag>
              ))}
            </Space>
          );
        },
      },
      {
        title: '赛事时间',
        dataIndex: 'competitionStart',
        search: false,
        width: 220,
        responsive: ['sm', 'md', 'lg', 'xl', 'xxl'],
        render: (_, record) => `${record.competitionStart || '-'}${record.competitionEnd ? ` - ${record.competitionEnd}` : ''}`,
      },
      {
        title: '参赛范围',
        dataIndex: 'participationScope',
        search: false,
        ellipsis: true,
        responsive: ['md', 'lg', 'xl', 'xxl'],
        render: (_, record) => record.participationScope || record.location || '-',
      },
      {
        title: '标签',
        dataIndex: 'tags',
        search: false,
        responsive: ['lg', 'xl', 'xxl'],
        render: (_, record) => (
          <Space className="competition-tags" size={[4, 4]} wrap>
            {splitTags(record.tags)
              .slice(0, 4)
              .map((tag) => (
                <Tag key={tag} color="geekblue">
                  {tag}
                </Tag>
              ))}
            {!splitTags(record.tags).length ? '-' : null}
          </Space>
        ),
      },
      {
        title: '推荐',
        dataIndex: 'featured',
        valueType: 'select',
        valueEnum: {
          true: { text: '是' },
          false: { text: '否' },
        },
        width: 90,
        responsive: ['md', 'lg', 'xl', 'xxl'],
        render: (_, record) => (record.featured ? <Tag color="gold">推荐</Tag> : <Tag>普通</Tag>),
      },
      {
        title: '排序',
        dataIndex: 'sort',
        search: false,
        width: 80,
        responsive: ['lg', 'xl', 'xxl'],
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 160,
        align: 'right',
        className: 'saas-table-action-column',
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={actionPermission.buildTableActions([
              {
                key: 'status',
                label: record.status === 'published' ? '撤回' : '发布',
                icon: record.status === 'published' ? <RollbackOutlined /> : <CheckCircleOutlined />,
                permission: 'aiadc:competition:update',
                hidden: record.status === 'archived',
                onClick: () => {
                  if (record.status === 'published') {
                    void toggleCompetitionStatus(record);
                    return;
                  }

                  modal.confirm({
                    title: '确认发布该赛事？',
                    content: `发布后，赛事「${record.title}」将出现在学生报名入口中。`,
                    okText: '确认发布',
                    cancelText: '取消',
                    onOk: async () => {
                      await toggleCompetitionStatus(record);
                    },
                  });
                },
              },
              {
                key: 'settings',
                label: '配置',
                icon: <SettingOutlined />,
                permission: 'aiadc:competition:update',
                onClick: () => {
                  if (!record.uuid) {
                    message.warning('赛事 UUID 缺失，请在数据库迁移后刷新数据。');
                    return;
                  }
                  history.push(`/competitions/${record.uuid}/settings`);
                },
              },
              {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined />,
                permission: 'aiadc:competition:delete',
                danger: true,
                onClick: () => {
                  modal.confirm({
                    title: '确认删除该赛事？',
                    content: `删除后赛事「${record.title}」不会再出现在赛事列表中。`,
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await deleteCompetition(record.id);
                      message.success('赛事已删除');
                      actionRef.current?.reload();
                    },
                  });
                },
              },
            ])}
          />
        ),
      },
    ],
    [actionPermission, categoryLabelMap, categoryOptions, levelLabelMap, responsive.isDesktop, responsive.isMobile, toggleCompetitionStatus],
  );

  if (location.pathname === '/competitions/create') {
    return <CreateCompetitionPage />;
  }

  if (location.pathname === '/competitions/register/payment-result') {
    return <PaymentResultPage />;
  }

  if (location.pathname === '/activities/register' || location.pathname === '/competitions/activity-register') {
    return <ActivityRegistrationPage />;
  }

  if (location.pathname === '/competitions/expert-apply') {
    return <ExpertApplicationPage />;
  }

  return (
    <ManagementPage title="赛事管理">
      <ManagementPageBody>
        <ManagementTable<CompetitionRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          adaptiveSpacing
          containerResponsive
          isMobile={responsive.isMobile}
          autoContentWidth
          scroll={{ x: 'max-content' }}
          tableLayout="auto"
          request={competitionTableRequest}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          toolBarRender={() =>
            actionPermission.buildToolbarActions([
              {
                permission: 'aiadc:competition:create',
                value: (
                  <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreateDrawer}>
                    新增赛事
                  </Button>
                ),
              },
            ])
          }
        />
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default CompetitionPage;
