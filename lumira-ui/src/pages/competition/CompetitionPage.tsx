import { ArrowDownOutlined, ArrowUpOutlined, CheckCircleOutlined, DeleteOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, RollbackOutlined, SettingOutlined, TeamOutlined, UploadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Alert, Avatar, Button, Card, Checkbox, DatePicker, Form, Image, Input, InputNumber, Menu, Modal, Radio, Result, Select, Space, Steps, Switch, Table, Tabs, Tag, Typography, Upload } from 'antd';
import type { FormInstance } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import ImgCrop from 'antd-img-crop';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { formatMessage, getLocale, history, useLocation, useModel, useParams } from '@umijs/max';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import { XMarkdown } from '@ant-design/x-markdown';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useDictOptions } from '@/hooks/useDictOptions';
import { useResponsive } from '@/hooks/useResponsive';
import { normalizeLocale } from '@/i18n/locale';
import {
  createCompetition,
  createCompetitionDraft,
  createCompetitionStage,
  confirmRegistration,
  createRegistrationPaymentOrder,
  deleteCompetition,
  deleteRegistration,
  getCompetition,
  getRegistration,
  getCompetitionSettings,
  getCompetitionStageForm,
  getRegistrationPaymentStatus,
  listCompetitionStages,
  listCompetitions,
  listRegistrations,
  listRegistrationPaymentOptions,
  saveCompetitionSettingsModule,
  reconfirmRegistration,
  updateCompetitionStage,
  upsertCompetitionStageForm,
  updateCompetition,
  updateCompetitionDraft,
  type RegistrationSnapshotMemberPayload,
  type RegistrationProjectSnapshotPayload,
  type RegistrationSnapshotTeamPayload,
  type RegistrationUpsertPayload,
} from '@/services/competition/api';
import type { CompetitionPaymentOptionRecord, CompetitionPaymentOrderRecord } from '@/services/competition/types';
import type {
  CompetitionFeeMode,
  CompetitionLocale,
  CompetitionConfigItem,
  CompetitionConfigItemType,
  CompetitionRecord,
  CompetitionRegistrationRecord,
  CompetitionSettingsRecord,
  CompetitionStageFormRecord,
  CompetitionStageRecord,
  CompetitionStatus,
  CompetitionUpsertPayload,
} from '@/services/competition/types';
import { request } from '@/services/common/request';
import type { FileObjectRecord, FileStorageSpaceRecord, PagedResult, PaymentProviderSettings } from '@/types/api';
import ActivityRegistrationPage from '@/pages/competition/ActivityRegistrationPage';
import ExpertApplicationPage from '@/pages/competition/ExpertApplicationPage';
import PaymentResultPage from '@/pages/competition/PaymentResultPage';
import { isBasicSettingsPageReadyToSave, isConfigModuleReadyToSave, isTimelineSettingsPageReadyToSave } from '@/pages/competition/competitionSettingsSave';
import { buildRegistrationCompetitionFallback, mergeRegistrationCompetitionOptions } from '@/pages/competition/utils/registrationCompetition';
import { buildRegistrationDraftStorageKey } from '@/pages/competition/utils/registrationDraftStorageKey';
import {
  buildRegistrationPaymentResultUrl,
  calculateRegistrationPayableAmount,
  isRegistrationPaymentSuccessful,
  pickEnabledCollectedValues,
  retainAvailablePaymentProvider,
} from '@/pages/competition/utils/registrationCheckout';
import { normalizeCompetitionDraftBasicDefaults } from '@/pages/competition/utils/competitionDraftDefaults';
import { loadOptionalPreliminaryStageForm } from '@/pages/competition/utils/loadOptionalStageForm';
import {
  REGISTRATION_WIZARD_FLOW_VERSION,
  normalizeRegistrationWizardDraftStep,
  registrationWizardStep,
  registrationWizardStepItems,
} from '@/pages/competition/utils/registrationWizardFlow';
import {
  isDeprecatedRegistrationContactField,
  removeDeprecatedRegistrationContactFields,
  resolveRegistrationFieldScope,
} from '@/pages/competition/utils/registrationFieldScope';
import {
  createCompetitionSettingsSearch,
  getCompetitionSettingsStageTabFallback,
  parseCompetitionSettingsNavigation,
  type CompetitionSettingsRegistrationTab,
  type CompetitionSettingsSectionKey,
  type CompetitionSettingsStageTab,
} from '@/pages/competition/utils/competitionSettingsNavigation';
import { AgreementMarkdownEditor } from '@/pages/settings/personalization/components/AgreementMarkdownEditor';
import { message, modal } from '@/theme/antdFeedbackBridge';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { sanitizeMarkdownInput } from '@/utils/markdownSecurity';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { validateMemberTextField } from './memberFieldValidation';
import {
  DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS,
  isIndependentMemberRoleField,
  normalizeIndependentMemberRoleMetadata,
  prioritizeRequiredMemberNameField,
  reorderScopedConfigItems,
} from './utils/competitionFieldConfig';
import './CompetitionPage.css';

type CompetitionTimeMode = 'CONFIRMED' | 'TBD';

const detectPaymentClientType = (): 'DESKTOP' | 'MOBILE' | 'WECHAT' => {
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

type CompetitionScheduleFormItem = {
  timeMode?: CompetitionTimeMode;
  title?: string;
  timeRange?: [Dayjs, Dayjs] | [string, string];
  reviewRange?: [Dayjs, Dayjs] | [string, string];
};

type CompetitionFormValues = Omit<Partial<CompetitionUpsertPayload>, 'locale'> & {
  locale?: CompetitionLocale[];
  registrationRange?: [Dayjs, Dayjs] | [string, string];
  organizers?: CompetitionOrganizerFormItem[];
  schedules?: CompetitionScheduleFormItem[];
};

type RegistrationFormValues = {
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

type CompetitionStageFormField = {
  key: string;
  label?: string;
  type?: string;
  required?: boolean;
  maxLength?: number;
  fileFormat?: string;
  maxSizeMb?: number;
  storageKey?: string;
};

type RegistrationTeamMemberDraft = RegistrationSnapshotMemberPayload;

type RegistrationTeamDraft = RegistrationSnapshotTeamPayload & {
  initialMembers?: RegistrationTeamMemberDraft[];
};

type RegistrationMemberEditorKey = number | 'new';
const COMPETITION_REGISTRATION_SCOPE_RESOURCE = 'competition:registration';
const zhFallbackRegistrationTeamTypeOptions = [
  { value: 'GENERAL', label: '通用团队' },
  { value: 'DEV', label: '开发团队' },
  { value: 'COMPETITION', label: '竞赛团队' },
  { value: 'CLUB', label: '社团组织' },
  { value: 'OTHER', label: '其他' },
];
const enFallbackRegistrationTeamTypeOptions = [
  { value: 'GENERAL', label: 'General' },
  { value: 'DEV', label: 'Development' },
  { value: 'COMPETITION', label: 'Competition' },
  { value: 'CLUB', label: 'Club' },
  { value: 'OTHER', label: 'Other' },
];
const emptyRegistrationTeamMember = (): RegistrationTeamMemberDraft => ({
  memberName: '',
  employeeNo: '',
  departmentName: '',
  role: 'MEMBER',
  remark: '',
  extraValues: {},
});

type CompetitionJsonSchedule = {
  timeMode?: CompetitionTimeMode;
  title?: string;
  start?: string;
  end?: string;
  reviewStart?: string;
  reviewEnd?: string;
};

type CompetitionMaterialStageTab = {
  key: string;
  label: string;
  stageCode: string;
  stageName: string;
};

const getCompetitionMaterialStageTabs = (competition: CompetitionRecord): CompetitionMaterialStageTab[] =>
  parseJsonArray<CompetitionJsonSchedule>(competition.scheduleJson)
    .filter((schedule) => schedule.timeMode === 'CONFIRMED' && trimOptional(schedule.title))
    .map((schedule, index) => ({
      key: `stage-${index + 1}`,
      label: `${schedule.title}材料设置`,
      stageCode: `STAGE_${index + 1}`,
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

type CompetitionRegistrationDraftStorage = {
  competitionTitle?: string;
  competitionUuid?: string;
  registrationId?: number;
  currentStep?: number;
  flowVersion?: number;
  acceptedDocumentKeys?: string[];
  confirmedTeamId?: number;
  confirmedProjectId?: number;
  paymentStatus?: string;
  savedAt?: number;
  values?: Partial<RegistrationFormValues>;
};

const COMPETITION_CATEGORY_DICT = 'aiadc_competition_category';
const COMPETITION_LEVEL_DICT = 'aiadc_competition_level';
const COMPETITION_CREATE_DRAFT_STORAGE_KEY = 'competition.create';

const defaultRegistrationFormValues: Partial<RegistrationFormValues> = {
  newTeam: {
    teamType: 'GENERAL',
    initialMembers: [],
  },
};

const localeOptions: Array<{ label: string; value: CompetitionLocale }> = [
  { label: '中文', value: 'zh' },
  { label: 'English', value: 'en' },
];

const zhCategoryOptions = [
  { label: '创新赛', value: 'INNOVATION' },
  { label: '应用赛', value: 'APPLICATION' },
  { label: '专项赛', value: 'SPECIAL' },
  { label: '其他', value: 'OTHER' },
];

const enCategoryOptions = [
  { label: 'Innovation', value: 'INNOVATION' },
  { label: 'Application', value: 'APPLICATION' },
  { label: 'Special', value: 'SPECIAL' },
  { label: 'Other', value: 'OTHER' },
];

const zhLevelOptions = [
  { label: '校级', value: 'SCHOOL' },
  { label: '省级', value: 'PROVINCE' },
  { label: '国家级', value: 'NATIONAL' },
  { label: '国际级', value: 'INTERNATIONAL' },
];

const enLevelOptions = [
  { label: 'School', value: 'SCHOOL' },
  { label: 'Provincial', value: 'PROVINCE' },
  { label: 'National', value: 'NATIONAL' },
  { label: 'International', value: 'INTERNATIONAL' },
];

const useCompetitionDictFallbackOptions = () => {
  const isEnglish = normalizeLocale(getLocale()) === 'en-US';
  return useMemo(() => ({
    categoryOptions: isEnglish ? enCategoryOptions : zhCategoryOptions,
    levelOptions: isEnglish ? enLevelOptions : zhLevelOptions,
    registrationTeamTypeOptions: isEnglish ? enFallbackRegistrationTeamTypeOptions : zhFallbackRegistrationTeamTypeOptions,
  }), [isEnglish]);
};

const timeModeOptions: Array<{ label: string; value: CompetitionTimeMode }> = [
  { label: '确定', value: 'CONFIRMED' },
  { label: '不确定', value: 'TBD' },
];

const feeModeOptions: Array<{ label: string; value: CompetitionFeeMode }> = [
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
  { title: '竞赛主页' },
];

const competitionCreateStepQueryKey = 'step';

const getCompetitionCreateSteps = () => [
  { title: formatMessage({ id: 'page.competition.create.steps.terms', defaultMessage: 'Terms' }) },
  { title: formatMessage({ id: 'page.competition.create.steps.basic', defaultMessage: 'Basic information' }) },
  { title: formatMessage({ id: 'page.competition.create.steps.homepage', defaultMessage: 'Competition homepage' }) },
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
2. 竞赛主页、二维码和联系方式不得包含违法、侵权、误导或与赛事无关的内容。
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

const trimOptional = (value?: unknown) => normalizeDisplayText(value);

const normalizeDisplayText = (value: unknown): string | undefined => {
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

const buildOptionLabelMap = (options: Array<{ label?: unknown; value?: unknown }>) => {
  const entries = options
    .map((option) => {
      const value = normalizeOptionValue(option.value);
      return value ? ([value, option.label] as [string, unknown]) : undefined;
    })
    .filter((entry): entry is [string, unknown] => Boolean(entry));
  return new Map(entries);
};

const resolveOptionLabel = (optionLabelMap: Map<string, unknown>, value: unknown) => {
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

const parseRange = (start?: string | null, end?: string | null): [Dayjs, Dayjs] | undefined => {
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

const sanitizeSchedules = (schedules?: CompetitionScheduleFormItem[]): CompetitionJsonSchedule[] => {
  const normalized = (schedules || [])
    .map((item) => {
      const timeMode = normalizeTimeMode(item.timeMode);
      if (timeMode !== 'CONFIRMED') {
        return { timeMode: 'TBD' as const };
      }
      const [start, end] = item.timeRange || [];
      const [reviewStart, reviewEnd] = item.reviewRange || [];
      return {
        timeMode,
        title: trimOptional(item.title),
        start: formatRangeValue(start),
        end: formatRangeValue(end),
        reviewStart: formatRangeValue(reviewStart),
        reviewEnd: formatRangeValue(reviewEnd),
      };
    })
    .filter((item) => item.timeMode === 'TBD' || item.title || item.start || item.end);
  const confirmedSchedules = normalized.filter((item) => item.timeMode === 'CONFIRMED');
  return confirmedSchedules.length ? confirmedSchedules : [{ timeMode: 'TBD' }];
};

const organizerLabel = (organizer: CompetitionOrganizerFormItem) =>
  [organizer.role, organizer.name].map(trimOptional).filter(Boolean).join('：');

const mojibakeReplacementPattern = new RegExp(`${String.fromCharCode(0xfffd)}\\??`, 'g');

const normalizeMojibakeText = (value?: string | null) =>
  trimOptional(value)?.replace(mojibakeReplacementPattern, '');

const normalizePayload = (values: CompetitionFormValues): CompetitionUpsertPayload => {
  const [registrationStart, registrationEnd] = values.registrationRange || [];
  const organizers = sanitizeOrganizers(values.organizers);
  const schedules = sanitizeSchedules(values.schedules);
  const firstConfirmedSchedule = schedules.find((item) => item.timeMode === 'CONFIRMED' && item.start);
  const homepageContent = trimOptional(values.homepageContent);
  const participationScope = trimOptional(values.participationScope);
  const category = normalizeOptionValue(values.category);
  const competitionLevel = normalizeOptionValue(values.competitionLevel || values.level);
  const description = homepageContent ? homepageContent.slice(0, 1000) : undefined;

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
    registrationStart: formatRangeValue(registrationStart),
    registrationEnd: formatRangeValue(registrationEnd),
    competitionStart: firstConfirmedSchedule?.start || 'TBD',
    competitionEnd: firstConfirmedSchedule?.end,
    location: participationScope || 'TBD',
    participationScope,
    participationRequirement: trimOptional(values.participationRequirement),
    scheduleJson: schedules.length ? JSON.stringify(schedules) : undefined,
    description,
    imageUrl: trimOptional(values.imageUrl),
    contactName: trimOptional(values.contactName),
    contactQrCodeUrl: trimOptional(values.contactQrCodeUrl),
    homepageContent,
    tags: trimOptional(values.tags),
    status: values.status || 'draft',
    feeMode: values.feeMode,
    entryFeeMinor: Math.max(0, Math.round(Number(values.entryFeeMinor || 0) * 100)),
    currency: values.currency || 'CNY',
    featured: Boolean(values.featured),
    sort: values.sort ?? 100,
  };
};

const recordToFormValues = (record: CompetitionRecord): Partial<CompetitionFormValues> => {
  const organizers = parseJsonArray<CompetitionOrganizerFormItem>(record.organizersJson);
  const draftBasicDefaults = normalizeCompetitionDraftBasicDefaults(record, organizers);
  const schedules = parseJsonArray<CompetitionJsonSchedule>(record.scheduleJson).map((item) => ({
    timeMode: normalizeTimeMode(item.timeMode),
    title: item.title,
    timeRange: item.timeMode === 'CONFIRMED' ? parseRange(item.start, item.end) : undefined,
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
    schedules: schedules.length ? schedules : [{ timeMode: 'CONFIRMED', title: '竞赛时间', timeRange: parseRange(record.competitionStart, record.competitionEnd) }],
    participationScope: draftBasicDefaults.participationScope,
    participationRequirement: record.participationRequirement || undefined,
    contactName: record.contactName || undefined,
    contactQrCodeUrl: record.contactQrCodeUrl || undefined,
    homepageContent: record.homepageContent || record.description || undefined,
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

const defaultCompetitionFormValues: Partial<CompetitionFormValues> = {
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
  range?: CompetitionFormValues['registrationRange'] | CompetitionScheduleFormItem['timeRange'],
): [string, string] | undefined => {
  if (!Array.isArray(range) || range.length !== 2) {
    return undefined;
  }
  const [start, end] = range;
  const normalizedStart = typeof start === 'string' ? start : formatRangeValue(start);
  const normalizedEnd = typeof end === 'string' ? end : formatRangeValue(end);
  return normalizedStart && normalizedEnd ? [normalizedStart, normalizedEnd] : undefined;
};

const serializeCompetitionCreateDraftValues = (values: Partial<CompetitionFormValues>): Partial<CompetitionFormValues> => ({
  ...values,
  registrationRange: serializeDraftRangeValue(values.registrationRange),
  schedules: values.schedules?.map((schedule) => ({
    ...schedule,
    timeRange: serializeDraftRangeValue(schedule.timeRange),
  })),
});

const restoreDraftRangeValue = (
  range?: CompetitionFormValues['registrationRange'] | CompetitionScheduleFormItem['timeRange'],
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

type CompetitionDateTimeRange = CompetitionFormValues['registrationRange'] | CompetitionScheduleFormItem['timeRange'];

const CompetitionDateTimeRangePicker = ({
  value,
  onChange,
  disabledDate,
}: {
  value?: CompetitionDateTimeRange;
  onChange?: (value?: [Dayjs, Dayjs]) => void;
  disabledDate?: (date: Dayjs) => boolean;
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
        }}
        format="YYYY-MM-DD HH:mm"
        placeholder={['开始日期', '结束日期']}
        placement="topRight"
        getPopupContainer={() => document.body}
        disabledDate={disabledDate}
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

const toPositiveId = (value: unknown) => {
  const id = Number(value);
  return Number.isFinite(id) && id > 0 ? id : undefined;
};

const getCompleteTimeRange = (
  range?: CompetitionFormValues['registrationRange'] | CompetitionScheduleFormItem['timeRange'],
): [Dayjs, Dayjs] | undefined => {
  if (!Array.isArray(range) || range.length !== 2) {
    return undefined;
  }
  const start = toValidDayjs(range[0]);
  const end = toValidDayjs(range[1]);
  return start && end ? [start, end] : undefined;
};

const isScheduleWithinRegistrationRange = (
  scheduleRange: CompetitionScheduleFormItem['timeRange'],
  registrationRange: CompetitionFormValues['registrationRange'],
) => {
  const scheduleBounds = getCompleteTimeRange(scheduleRange);
  const registrationBounds = getCompleteTimeRange(registrationRange);
  if (!scheduleBounds || !registrationBounds) {
    return false;
  }
  const [scheduleStart, scheduleEnd] = scheduleBounds;
  const [registrationStart, registrationEnd] = registrationBounds;
  return !scheduleStart.isBefore(registrationStart) && !scheduleEnd.isAfter(registrationEnd);
};

const isOutsideRegistrationDate = (
  current: Dayjs,
  registrationRange: CompetitionFormValues['registrationRange'],
) => {
  const registrationBounds = getCompleteTimeRange(registrationRange);
  if (!current || !registrationBounds) {
    return false;
  }
  const [registrationStart, registrationEnd] = registrationBounds;
  return current.isBefore(registrationStart, 'day') || current.isAfter(registrationEnd, 'day');
};

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
      timeRange: restoreDraftRangeValue(schedule.timeRange),
    })),
  };
};

interface StoredUserDraft<T> {
  payload: T;
  updatedAt: number;
}

const readUserDraft = async <T,>(draftKey: string): Promise<T | undefined> => {
  const stored = await request<StoredUserDraft<T> | null>(`/v2/user-drafts/${draftKey}`, {
    method: 'GET',
    silent: true,
  });
  return stored?.payload;
};

const writeUserDraft = async <T,>(draftKey: string, draft: T) => {
  await request<StoredUserDraft<T>>(`/v2/user-drafts/${draftKey}`, {
    method: 'PUT',
    data: draft,
    silent: true,
  });
};

const clearUserDraft = async (draftKey: string) => {
  await request<void>(`/v2/user-drafts/${draftKey}`, {
    method: 'DELETE',
    silent: true,
  });
};

const readCompetitionCreateDraft = () => readUserDraft<CompetitionCreateDraftStorage>(COMPETITION_CREATE_DRAFT_STORAGE_KEY);
const writeCompetitionCreateDraft = (draft: CompetitionCreateDraftStorage) => writeUserDraft(COMPETITION_CREATE_DRAFT_STORAGE_KEY, draft);
const clearCompetitionCreateDraft = () => clearUserDraft(COMPETITION_CREATE_DRAFT_STORAGE_KEY);
const readCompetitionRegistrationDraft = (draftKey: string) => readUserDraft<CompetitionRegistrationDraftStorage>(draftKey);
const writeCompetitionRegistrationDraft = (draftKey: string, draft: CompetitionRegistrationDraftStorage) => writeUserDraft(draftKey, draft);
const clearCompetitionRegistrationDraft = (draftKey: string) => clearUserDraft(draftKey);

const hasCompetitionRegistrationDraftContent = (values: Partial<RegistrationFormValues>) => {
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
      || Object.values(values.newProjectExtraValues || {}).some((value) => value !== undefined && value !== null && String(value).trim())
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
      || trimOptional(values.homepageContent)
      || trimOptional(values.contactName)
      || trimOptional(values.contactQrCodeUrl)
      || trimOptional(values.imageUrl)
      || trimOptional(values.tags)
      || getCompleteTimeRange(values.registrationRange)
      || schedules.some((schedule) => (
        normalizeTimeMode(schedule.timeMode) === 'CONFIRMED'
        || trimOptional(schedule.title)
        || getCompleteTimeRange(schedule.timeRange)
      ))
  );
};

const getCompetitionCreateMissingFields = (values: Partial<CompetitionFormValues>) => {
  const missingFields: string[] = [];
  const organizers = values.organizers || [];
  const organizerComplete = organizers.some((organizer) => trimOptional(organizer.role) && trimOptional(organizer.name));
  const firstSchedule = values.schedules?.[0];

  if (!trimOptional(values.title)) {
    missingFields.push('竞赛名称');
  }
  if (!organizerComplete) {
    missingFields.push('组织者列表');
  }
  if (!normalizeOptionValue(values.category)) {
    missingFields.push('竞赛类别');
  }
  if (!normalizeOptionValue(values.competitionLevel || values.level)) {
    missingFields.push('竞赛级别');
  }
  if (!trimOptional(values.participationScope)) {
    missingFields.push('参赛范围');
  }
  if (!values.feeMode) {
    missingFields.push('收费方式');
  }
  if (values.entryFeeMinor === undefined || values.entryFeeMinor === null || Number.isNaN(Number(values.entryFeeMinor))) {
    missingFields.push('参赛费用');
  }
  if (!trimOptional(values.currency)) {
    missingFields.push('货币');
  }
  if (!getCompleteTimeRange(values.registrationRange)) {
    missingFields.push('报名时间');
  }
  if (!firstSchedule?.timeMode) {
    missingFields.push('竞赛安排');
  }
  if (firstSchedule?.timeMode === 'CONFIRMED') {
    const hasValidSchedule = values.schedules?.some((schedule) => trimOptional(schedule.title) && Array.isArray(schedule.timeRange) && schedule.timeRange.length === 2);
    if (!hasValidSchedule) {
      missingFields.push('竞赛安排');
    }
    const hasOutOfRangeSchedule = values.schedules?.some(
      (schedule) => getCompleteTimeRange(schedule.timeRange) && !isScheduleWithinRegistrationRange(schedule.timeRange, values.registrationRange),
    );
    if (hasOutOfRangeSchedule) {
      missingFields.push('竞赛安排需在报名时间内');
    }
  }
  if (!values.locale?.length) {
    missingFields.push('语言');
  }
  return missingFields;
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

const uploadCompetitionImage = async (file: File) => {
  if (!file.type.startsWith('image/')) {
    message.error('请上传图片文件');
    return undefined;
  }
  const formData = new FormData();
  formData.append('file', file);
  const uploadedUrl = await request<string>('/v1/system/uploads/image', {
    method: 'POST',
    headers: {},
    data: formData,
    ...API_OPTS.NO_REDIRECT,
  });
  return normalizeUploadUrl(uploadedUrl);
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
  const contactQrCodeUrl = Form.useWatch('contactQrCodeUrl', form);
  const schedules = Form.useWatch('schedules', form) || [];
  const registrationRange = Form.useWatch('registrationRange', form);
  const [uploadingQrCode, setUploadingQrCode] = useState(false);
  const qrPreviewUrl = normalizeUploadUrl(contactQrCodeUrl);

  const handleQrCodeUpload = async (file: File) => {
    setUploadingQrCode(true);
    try {
      const uploadedUrl = await uploadCompetitionImage(file);
      if (uploadedUrl) {
        form.setFieldValue('contactQrCodeUrl', uploadedUrl);
        onDraftChange?.();
        message.success('联系方式二维码已上传');
      }
    } catch (error) {
      showErrorMessage(error, '联系方式二维码上传失败');
    } finally {
      setUploadingQrCode(false);
    }
  };

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
            <Space direction="vertical" size={12} className="competition-dynamic-list">
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
                    <Button aria-label="移除组织者" title="移除组织者" icon={<DeleteOutlined />} disabled={fields.length <= 1} onClick={() => remove(field.name)} />
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
      <Form.Item name="participationRequirement" label="参赛要求">
        <Input.TextArea rows={4} placeholder="请输入参赛要求" />
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
                  <Space direction="vertical" size={8} className="competition-dynamic-list">
                    {fields.map((field, index) => (
                      <div key={field.key} className="competition-schedule-row">
                        <Form.Item name={[field.name, 'title']} rules={[{ required: true, message: '请输入赛程名称' }]} className="competition-schedule-row__title">
                          <Input maxLength={128} placeholder="例如：初赛" />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'timeRange']}
                          rules={[
                            { required: true, message: '请选择比赛时间' },
                            {
                              validator: (_, value: CompetitionScheduleFormItem['timeRange']) => {
                                if (!getCompleteTimeRange(value)) {
                                  return Promise.reject(new Error('请选择开始和结束时间'));
                                }
                                if (!getCompleteTimeRange(registrationRange)) {
                                  return Promise.reject(new Error('请先选择报名时间'));
                                }
                                return isScheduleWithinRegistrationRange(value, registrationRange)
                                  ? Promise.resolve()
                                  : Promise.reject(new Error('竞赛安排需在报名时间内'));
                              },
                            },
                          ]}
                          className="competition-schedule-row__time"
                        >
                          <CompetitionDateTimeRangePicker disabledDate={(current) => isOutsideRegistrationDate(current, registrationRange)} />
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
                          <Button aria-label="删除竞赛安排" title="删除竞赛安排" icon={<DeleteOutlined />} disabled={fields.length <= 1} onClick={() => remove(field.name)} />
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
      <Form.Item name="contactName" label="联系主办方">
        <Input maxLength={128} placeholder="请输入主办方联系方式" />
      </Form.Item>
      <Form.Item label="上传联系方式二维码">
        <Space direction="vertical" size={8} className="competition-qr-upload">
          <ImgCrop
            modalTitle="Crop QR Code"
            rotationSlider
            aspect={1}
            beforeCrop={(file) => {
              if (!file.type.startsWith('image/')) {
                message.error('请上传图片文件');
                return false;
              }
              return true;
            }}
          >
            <Upload
              accept="image/*"
              showUploadList={false}
              disabled={uploadingQrCode}
              beforeUpload={async (file) => {
                await handleQrCodeUpload(file);
                return Upload.LIST_IGNORE;
              }}
            >
              <div
                className={`competition-qr-upload__preview${uploadingQrCode ? ' is-uploading' : ''}${qrPreviewUrl ? ' has-image' : ''}`}
                role="button"
                aria-label="Upload contact QR code"
                tabIndex={uploadingQrCode ? -1 : 0}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    event.currentTarget.click();
                  }
                }}
              >
                {qrPreviewUrl ? (
                  <Image width={144} height={144} src={qrPreviewUrl} preview={false} />
                ) : (
                  <Space direction="vertical" size={6} align="center">
                    <UploadOutlined />
                    <Typography.Text type="secondary">点击上传二维码</Typography.Text>
                  </Space>
                )}
                {qrPreviewUrl ? <span className="competition-qr-upload__hint">{uploadingQrCode ? '上传中...' : '点击更换二维码'}</span> : null}
              </div>
            </Upload>
          </ImgCrop>
          <Space wrap>
            <Button
              disabled={!contactQrCodeUrl || uploadingQrCode}
              onClick={() => {
                form.setFieldValue('contactQrCodeUrl', undefined);
                onDraftChange?.();
              }}
            >
              清空二维码
            </Button>
          </Space>
          <Form.Item name="contactQrCodeUrl" hidden>
            <Input />
          </Form.Item>
        </Space>
      </Form.Item>
      <Space size={0} className="competition-inline-fields" align="start">
        <Form.Item name="locale" label="语言" rules={[{ required: true }]} className="competition-inline-fields__item">
          <Select mode="multiple" maxTagCount="responsive" options={localeOptions} />
        </Form.Item>
        <Form.Item name="sort" label="排序" className="competition-inline-fields__item">
          <InputNumber min={0} max={9999} style={{ width: '100%' }} />
        </Form.Item>
      </Space>
      <Form.Item name="featured" label="推荐赛事" valuePropName="checked">
        <Switch checkedChildren="是" unCheckedChildren="否" />
      </Form.Item>
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
    <Form.Item name="homepageContent" label="竞赛主页">
      <AgreementMarkdownEditor placeholder="请输入竞赛主页内容，支持 Markdown 富文本" />
    </Form.Item>
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
          <Alert type="error" showIcon message="暂无新增赛事权限" />
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
    if (currentStep === 1) {
      await form.validateFields();
      setCompetitionCreateStep(2);
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
                  <Alert type="info" showIcon message="请先阅读赛事发布条款，确认后继续填写基本信息。" />
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
              {currentStep === 2 ? (
                <div className="competition-homepage-step">
                  <Form.Item name="homepageContent" label="竞赛主页">
                    <AgreementMarkdownEditor placeholder="请输入竞赛主页内容，支持 Markdown 富文本" />
                  </Form.Item>
                </div>
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

const parseFormFields = (form?: CompetitionStageFormRecord) => {
  if (!form?.formSchemaJson) {
    return [] as CompetitionStageFormField[];
  }
  try {
    const parsed = JSON.parse(form.formSchemaJson);
    return Array.isArray(parsed.fields) ? (parsed.fields as CompetitionStageFormField[]) : [];
  } catch {
    return [] as CompetitionStageFormField[];
  }
};

const competitionMaterialFileFormatConfig: Record<string, { extensions: string[]; label: string }> = {
  DOCUMENT: {
    extensions: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'md', 'txt'],
    label: '文档类文件',
  },
  IMAGE: {
    extensions: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'],
    label: '图片类文件',
  },
  ARCHIVE: {
    extensions: ['zip', 'rar', '7z', 'tar', 'gz', 'tar.gz', 'tgz'],
    label: '压缩包类文件',
  },
};

const getCompetitionMaterialFileExtension = (fileName?: string) => {
  const normalized = (fileName || '').trim().toLowerCase();
  if (normalized.endsWith('.tar.gz')) {
    return 'tar.gz';
  }
  const match = normalized.match(/\.([^.]+)$/);
  return match ? match[1] : '';
};

const validateCompetitionMaterialFile = (file: File, field: CompetitionStageFormField) => {
  const maxSizeMb = Number(field.maxSizeMb) || 20;
  const maxSizeBytes = maxSizeMb * 1024 * 1024;
  if (file.size > maxSizeBytes) {
    return `文件过大，单个文件不能超过 ${maxSizeMb}MB`;
  }
  const format = (field.fileFormat || 'ANY').toUpperCase();
  if (format === 'ANY') {
    return undefined;
  }
  const config = competitionMaterialFileFormatConfig[format];
  if (!config) {
    return undefined;
  }
  const extension = getCompetitionMaterialFileExtension(file.name);
  if (!extension || !config.extensions.includes(extension)) {
    return `文件类型不支持，请上传${config.label}`;
  }
  return undefined;
};

const enrichCompetitionStageFormFields = (
  fields: CompetitionStageFormField[],
  configItems: CompetitionConfigItem[],
) => {
  if (!fields.length || !configItems.length) {
    return fields;
  }
  const configByKey = new Map(
    configItems.map((item) => [item.itemKey, { item, metadata: parseConfigItemMetadata(item.contentJson) }]),
  );
  return fields.map((field) => {
    const config = configByKey.get(field.key);
    if (!config || field.type !== 'file') {
      return field;
    }
    return {
      ...field,
      label: field.label || config.item.title,
      fileFormat: field.fileFormat || normalizeFileFormat(config.metadata.fileFormat),
      maxSizeMb: field.maxSizeMb || config.metadata.maxSizeMb,
      storageKey: field.storageKey || config.metadata.storageKey,
    };
  });
};

const MaterialFileUploadInput = ({
  field,
  value,
  onChange,
}: {
  field: CompetitionStageFormField;
  value?: number;
  onChange?: (value?: number) => void;
}) => {
  const [uploading, setUploading] = useState(false);

  return (
    <Space direction="vertical" size={8}>
      <Upload
        maxCount={1}
        showUploadList={false}
        disabled={uploading}
        beforeUpload={async (file) => {
          const validationMessage = validateCompetitionMaterialFile(file as File, field);
          if (validationMessage) {
            message.error(validationMessage);
            return Upload.LIST_IGNORE;
          }
          const formData = new FormData();
          formData.append('file', file as File);
          formData.append('category', '赛事材料');
          formData.append('tags', `competition-material,${field.key}`);
          if (field.label) {
            formData.append('remark', field.label);
          }
          if (field.storageKey) {
            formData.append('bucket', field.storageKey);
          }
          setUploading(true);
          try {
            const uploaded = await request<FileObjectRecord>('/v1/files/upload', {
              method: 'POST',
              headers: {},
              data: formData,
              silent: true,
            });
            onChange?.(uploaded.id);
            message.success('文件上传成功');
          } catch (error) {
            showErrorMessage(error, '文件上传失败');
          } finally {
            setUploading(false);
          }
          return Upload.LIST_IGNORE;
        }}
      >
        <Button icon={<UploadOutlined />} loading={uploading}>
          {value ? '重新上传' : '上传文件'}
        </Button>
      </Upload>
      {value ? (
        <Space size={8}>
          <Tag color="blue">文件 ID：{value}</Tag>
          <Button size="small" type="link" onClick={() => onChange?.(undefined)}>
            移除
          </Button>
        </Space>
      ) : (
        <Typography.Text type="secondary">
          {field.storageKey ? `将上传到存储空间：${field.storageKey}` : '未指定存储空间，将使用默认存储空间'}
        </Typography.Text>
      )}
    </Space>
  );
};

type RegistrationCollectedField = {
  scope?: Extract<CompetitionConfigItemType, 'REGISTRATION_FIELD' | 'TEAM_FIELD' | 'MEMBER_FIELD' | 'PROJECT_FIELD'>;
  itemKey: string;
  title: string;
  fieldType?: string;
  placeholder?: string;
  required?: boolean;
  options?: string;
  validationRule?: string;
  groupLabel?: string;
};

type RegistrationCollectedFieldSplit = {
  allFields: RegistrationCollectedField[];
  customFields: RegistrationCollectedField[];
  overrides: Map<string, RegistrationCollectedField>;
};

type RegistrationImageFieldInputProps = {
  value?: string;
  onChange?: (value?: string) => void;
};

const RegistrationImageFieldInput = ({ value, onChange }: RegistrationImageFieldInputProps) => {
  const [uploading, setUploading] = useState(false);
  return (
    <Space direction="vertical" size={8}>
      {value ? <Image width={96} height={96} src={normalizeUploadUrl(value)} alt="已上传图片" /> : null}
      <Space>
        <Upload
          accept="image/*"
          showUploadList={false}
          disabled={uploading}
          beforeUpload={async (file) => {
            if (!file.type.startsWith('image/')) {
              message.error('请上传图片文件');
              return Upload.LIST_IGNORE;
            }
            if (file.size > 20 * 1024 * 1024) {
              message.error('请上传小于 20MB 的图片');
              return Upload.LIST_IGNORE;
            }
            const data = new FormData();
            data.append('file', file);
            setUploading(true);
            try {
              const uploadedUrl = await request<string>('/v1/system/uploads/image', { method: 'POST', headers: {}, data });
              onChange?.(uploadedUrl);
            } catch (error) {
              showErrorMessage(error, '图片上传失败');
            } finally {
              setUploading(false);
            }
            return Upload.LIST_IGNORE;
          }}
        >
          <Button icon={<UploadOutlined />} loading={uploading}>{value ? '更换图片' : '上传图片'}</Button>
        </Upload>
        {value ? <Button type="link" onClick={() => onChange?.(undefined)}>移除</Button> : null}
      </Space>
    </Space>
  );
};

const toRegistrationCollectedField = (item: CompetitionConfigItem): RegistrationCollectedField => {
  const scope = resolveRegistrationFieldScope(item);
  const metadata = normalizeIndependentMemberRoleMetadata(
    scope,
    item.itemKey,
    parseConfigItemMetadata(item.contentJson),
  );
  return {
    scope,
    itemKey: item.itemKey,
    title: item.title || item.itemKey,
    fieldType: metadata.fieldType || 'TEXT',
    placeholder: metadata.placeholder,
    required: Boolean(item.requiredFlag),
    options: metadata.options,
    validationRule: metadata.validationRule,
    groupLabel: metadata.groupLabel,
  };
};

const normalizeCollectedFieldConfigKey = (value?: string) => (value || '').replace(/[^a-z0-9]/gi, '').toLowerCase();

const standardFieldAliasMap = {
  TEAM_FIELD: {
    teamName: ['teamname', 'name'],
    teamType: ['teamtype', 'type'],
    avatarUrl: ['avatarurl', 'avatar'],
    description: ['description', 'teamdescription', 'intro'],
  },
  MEMBER_FIELD: {
    memberName: ['membername', 'name'],
    employeeNo: ['employeeno', 'studentno', 'memberno'],
    departmentName: ['departmentname', 'department'],
    role: ['role'],
    remark: ['remark', 'note'],
  },
  PROJECT_FIELD: {
    title: ['projecttitle', 'projectname', 'title', 'name'],
    imageUrl: ['imageurl', 'projectimage', 'projectavatar', 'logourl', 'logo'],
    description: ['projectdescription', 'description', 'intro'],
  },
} as const;

const resolveStandardCollectedFieldKey = (
  scope: keyof typeof standardFieldAliasMap,
  itemKey?: string,
) => {
  const normalizedItemKey = normalizeCollectedFieldConfigKey(itemKey);
  return Object.entries(standardFieldAliasMap[scope]).find(([, aliases]) => aliases.includes(normalizedItemKey))?.[0];
};

const splitConfiguredRegistrationFields = (
  items: CompetitionConfigItem[],
  scope: Extract<CompetitionConfigItemType, 'REGISTRATION_FIELD' | 'TEAM_FIELD' | 'MEMBER_FIELD' | 'PROJECT_FIELD'>,
): RegistrationCollectedFieldSplit => {
  const configuredFields = items
    .filter((item) => item.enabled !== false
      && !isDeprecatedRegistrationContactField(item)
      && resolveRegistrationFieldScope(item) === scope)
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
    .map(toRegistrationCollectedField);

  if (scope === 'REGISTRATION_FIELD') {
    return {
      allFields: configuredFields,
      customFields: configuredFields,
      overrides: new Map(),
    };
  }

  const overrides = new Map<string, RegistrationCollectedField>();
  const customFields: RegistrationCollectedField[] = [];
  configuredFields.forEach((field) => {
    const standardKey = resolveStandardCollectedFieldKey(scope, field.itemKey);
    if (standardKey && !isIndependentMemberRoleField(field.scope, field.itemKey, field.fieldType)) {
      overrides.set(standardKey, field);
      return;
    }
    customFields.push(field);
  });

  return { allFields: configuredFields, customFields, overrides };
};

const parseConfigFieldOptions = (options?: string) =>
  (options || '')
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => ({ label: item, value: item }));

const buildCollectedFieldRule = (field: RegistrationCollectedField) =>
  [
    ...(field.required ? [{ required: true, message: `请输入${field.title}` }] : []),
    ...((field.validationRule || '').toUpperCase() === 'CHINA_MOBILE'
      ? [{ pattern: /^1[3-9]\d{9}$/, message: `请输入正确的${field.title}` }]
      : []),
    ...((field.validationRule || '').toUpperCase() === 'EMAIL'
      ? [{ type: 'email' as const, message: `请输入正确的${field.title}` }]
      : []),
    ...((field.validationRule || '').toUpperCase() === 'ID_CARD'
      ? [{ pattern: /^(?:\d{15}|\d{17}[\dXx])$/, message: `请输入正确的${field.title}` }]
      : []),
  ];

const requiredSystemRegistrationField = (
  scope: RegistrationCollectedField['scope'],
  itemKey: string,
  title: string,
): RegistrationCollectedField => ({
  scope,
  itemKey,
  title,
  fieldType: 'TEXT',
  required: true,
});

const renderRegistrationCollectedFieldInput = (field: RegistrationCollectedField) => {
  const placeholder = field.placeholder || field.title || undefined;
  switch ((field.fieldType || 'TEXT').toUpperCase()) {
    case 'ROLE': {
      return (
        <Select
          options={parseConfigFieldOptions(field.options || DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS)}
          placeholder={placeholder}
        />
      );
    }
    case 'NUMBER':
      return <InputNumber min={0} style={{ width: '100%' }} placeholder={placeholder} />;
    case 'TEXTAREA':
      return <Input.TextArea rows={2} placeholder={placeholder} />;
    case 'IMAGE':
      return <RegistrationImageFieldInput />;
    case 'DATE':
      return <DatePicker style={{ width: '100%' }} placeholder={placeholder} />;
    case 'SELECT':
      return <Select options={parseConfigFieldOptions(field.options)} placeholder={placeholder} />;
    case 'MULTI_SELECT':
      return <Select mode="multiple" options={parseConfigFieldOptions(field.options)} placeholder={placeholder} />;
    case 'MOBILE':
      return <Input placeholder={placeholder} maxLength={20} />;
    case 'EMAIL':
      return <Input placeholder={placeholder} maxLength={128} />;
    default:
      return <Input placeholder={placeholder} />;
  }
};

const resolveMemberStandardFieldKey = (field: RegistrationCollectedField) => {
  if (isIndependentMemberRoleField(field.scope, field.itemKey, field.fieldType)) {
    return undefined;
  }
  return resolveStandardCollectedFieldKey('MEMBER_FIELD', field.itemKey) as keyof Pick<
    RegistrationTeamMemberDraft,
    'memberName' | 'employeeNo' | 'departmentName' | 'role' | 'remark'
  > | undefined;
};

const resolveMemberFieldFormName = (field: RegistrationCollectedField) => {
  const standardFieldKey = resolveMemberStandardFieldKey(field);
  return standardFieldKey || ['extraValues', field.itemKey];
};

const getMemberCollectedFieldValue = (member: RegistrationTeamMemberDraft, field: RegistrationCollectedField) => {
  const standardFieldKey = resolveMemberStandardFieldKey(field);
  return standardFieldKey ? member[standardFieldKey] : member.extraValues?.[field.itemKey];
};

const setMemberCollectedFieldValue = (
  member: RegistrationTeamMemberDraft,
  field: RegistrationCollectedField,
  value: unknown,
): RegistrationTeamMemberDraft => {
  const standardFieldKey = resolveMemberStandardFieldKey(field);
  if (standardFieldKey) {
    return {
      ...member,
      [standardFieldKey]: value,
    } as RegistrationTeamMemberDraft;
  }
  const nextExtraValues = { ...(member.extraValues || {}) };
  if (value === undefined || value === null || (typeof value === 'string' && !value.trim())) {
    delete nextExtraValues[field.itemKey];
  } else {
    nextExtraValues[field.itemKey] = value;
  }
  return {
    ...member,
    extraValues: nextExtraValues,
  };
};

const normalizeSnapshotValue = (value: unknown): unknown => {
  if (value == null) {
    return undefined;
  }
  if (dayjs.isDayjs(value)) {
    return value.format('YYYY-MM-DD');
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    return trimmed || undefined;
  }
  if (Array.isArray(value)) {
    return value.map(normalizeSnapshotValue).filter((item) => item !== undefined);
  }
  if (typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .map(([key, item]) => [key, normalizeSnapshotValue(item)] as const)
        .filter(([, item]) => item !== undefined),
    );
  }
  return value;
};

const hasCollectedValue = (value: unknown): boolean => {
  const normalized = normalizeSnapshotValue(value);
  if (normalized === undefined || normalized === null) {
    return false;
  }
  if (Array.isArray(normalized)) {
    return normalized.length > 0;
  }
  if (typeof normalized === 'object') {
    return Object.keys(normalized as Record<string, unknown>).length > 0;
  }
  return true;
};

const normalizeRegistrationMembers = (members?: RegistrationTeamMemberDraft[]): RegistrationSnapshotMemberPayload[] =>
  (members || [])
    .map((member) => ({
      memberName: normalizeSnapshotValue(member.memberName) as string | undefined,
      employeeNo: normalizeSnapshotValue(member.employeeNo) as string | undefined,
      departmentName: normalizeSnapshotValue(member.departmentName) as string | undefined,
      role: normalizeSnapshotValue(member.role) as string | undefined,
      remark: normalizeSnapshotValue(member.remark) as string | undefined,
      extraValues: normalizeSnapshotValue(member.extraValues) as Record<string, unknown> | undefined,
    }))
    .filter((member) => hasCollectedValue(member));

const registrationStatusValueEnum = {
  CREATED: { text: '\u5f85\u63d0\u4ea4\u6750\u6599' },
  MATERIAL_SUBMITTED: { text: '\u6750\u6599\u5df2\u63d0\u4ea4' },
  PENDING_PAYMENT: { text: '\u5f85\u652f\u4ed8' },
  PAID: { text: '\u5df2\u652f\u4ed8' },
  CONFIRMED: { text: '\u5df2\u786e\u8ba4' },
  CANCELLED: { text: '\u5df2\u53d6\u6d88' },
};

const registrationStatusColor: Record<string, string> = {
  CREATED: 'processing',
  MATERIAL_SUBMITTED: 'warning',
  PENDING_PAYMENT: 'warning',
  PAID: 'success',
  CONFIRMED: 'success',
  CANCELLED: 'default',
};

const formatRegistrationAmount = (amountMinor?: number | null, currency?: string | null) => {
  if (amountMinor == null) {
    return '-';
  }
  return `${currency || 'CNY'} ${(amountMinor / 100).toFixed(2)}`;
};

const formatRegistrationTime = (value?: string | null) => (value ? value.replace('T', ' ') : '-');

const renderRegistrationStatusTag = (status?: string | null) => {
  const normalized = status || 'CREATED';
  const text = registrationStatusValueEnum[normalized as keyof typeof registrationStatusValueEnum]?.text || normalized;
  return <Tag color={registrationStatusColor[normalized] || 'default'}>{text}</Tag>;
};

const parseSnapshotName = (snapshotJson?: string | null, keys: string[] = []) => {
  if (!snapshotJson) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(snapshotJson);
    if (!parsed || typeof parsed !== 'object') {
      return undefined;
    }
    for (const key of keys) {
      const value = (parsed as Record<string, unknown>)[key];
      if (typeof value === 'string' && value.trim()) {
        return value;
      }
    }
  } catch {
    return undefined;
  }
  return undefined;
};

const renderCollectedFieldReviewValue = (field: RegistrationCollectedField, value: unknown) => {
  if (!hasCollectedValue(value)) return <Typography.Text type="secondary">未填写</Typography.Text>;
  const fieldType = (field.fieldType || 'TEXT').toUpperCase();
  if (fieldType === 'IMAGE' && typeof value === 'string') {
    return <Image width={72} height={72} src={normalizeUploadUrl(value)} alt={field.title} />;
  }
  if (fieldType === 'SELECT' || fieldType === 'MULTI_SELECT') {
    const display = resolveOptionLabel(buildOptionLabelMap(parseConfigFieldOptions(field.options)), value);
    return <Typography.Text>{display || normalizeDisplayText(value)}</Typography.Text>;
  }
  return <Typography.Text>{normalizeDisplayText(normalizeSnapshotValue(value)) || '-'}</Typography.Text>;
};

const parseRegistrationSnapshot = <T,>(snapshotJson?: string | null, fallback: T = {} as T): T => {
  if (!snapshotJson) return fallback;
  try {
    return JSON.parse(snapshotJson) as T;
  } catch {
    return fallback;
  }
};

const registrationWizardModeQueryKey = 'mode';
const registrationWizardStepQueryKey = 'step';
const registrationWizardModeValue = 'wizard';
const registrationWizardMaxStep = 5;

const parseRegistrationWizardStepFromSearch = (search: string) => {
  const params = new URLSearchParams(search);
  if (params.get(registrationWizardModeQueryKey) !== registrationWizardModeValue) {
    return undefined;
  }
  const stepValue = Number(params.get(registrationWizardStepQueryKey));
  if (!Number.isInteger(stepValue) || stepValue < 1) {
    return 0;
  }
  return Math.min(stepValue - 1, registrationWizardMaxStep);
};

const createRegistrationWizardSearch = (stepIndex: number) => {
  const params = new URLSearchParams();
  params.set(registrationWizardModeQueryKey, registrationWizardModeValue);
  params.set(registrationWizardStepQueryKey, String(Math.min(Math.max(stepIndex, 0), registrationWizardMaxStep) + 1));
  return `?${params.toString()}`;
};

const sanitizeRegistrationTeamDraft = (teamDraft?: RegistrationTeamDraft): RegistrationTeamDraft | undefined => {
  if (!teamDraft) {
    return undefined;
  }
  const { visibility: _visibility, joinMode: _joinMode, ...rest } = teamDraft as RegistrationTeamDraft & {
    visibility?: string;
    joinMode?: string;
  };
  return rest;
};

const sanitizeRegistrationFormValues = (values: Partial<RegistrationFormValues>): Partial<RegistrationFormValues> => ({
  ...values,
  newTeam: sanitizeRegistrationTeamDraft(values.newTeam),
});

const getAllowedRegistrationWizardStep = (
  requestedStep: number,
  values: Partial<RegistrationFormValues>,
  documentsAccepted: boolean,
  activeRegistrationId?: number,
) => {
  const normalizedStep = Math.max(0, Math.min(requestedStep, registrationWizardMaxStep));
  if (normalizedStep <= 0) {
    return 0;
  }
  if (!toPositiveId(values.competitionId) || !documentsAccepted) {
    return 0;
  }
  if (normalizedStep <= 1) {
    return 1;
  }
  const members = values.newTeam?.initialMembers || [];
  if (!trimOptional(values.newTeamName) || !members.length) {
    return 1;
  }
  if (normalizedStep <= 2) {
    return 2;
  }
  if (!activeRegistrationId && !toPositiveId(values.projectId) && !trimOptional(values.newProjectTitle)) {
    return 2;
  }
  if (normalizedStep >= 5 && !activeRegistrationId) {
    return 4;
  }
  return normalizedStep;
};

const CompetitionRegistrationPage = () => {
  const { initialState } = useModel('@@initialState');
  const registrationDraftStorageKey = useMemo(
    () => buildRegistrationDraftStorageKey(initialState?.currentUser?.userId),
    [initialState?.currentUser?.userId],
  );
  const location = useLocation();
  const responsive = useResponsive();
  const registrationActionPermission = useActionPermission();
  const registrationActionRef = useRef<ActionType | undefined>(undefined);
  const [viewMode, setViewMode] = useState<'list' | 'wizard'>('list');
  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [competitions, setCompetitions] = useState<CompetitionRecord[]>([]);
  const [registrationDocuments, setRegistrationDocuments] = useState<CompetitionConfigItem[]>([]);
  const [registrationFields, setRegistrationFields] = useState<CompetitionConfigItem[]>([]);
  const [teamMemberLimits, setTeamMemberLimits] = useState({
    minMembers: DEFAULT_TEAM_MIN_MEMBERS,
    maxMembers: DEFAULT_TEAM_MAX_MEMBERS,
  });
  const [stageMaterialConfigs, setStageMaterialConfigs] = useState<CompetitionConfigItem[]>([]);
  const [registrationDocumentsLoading, setRegistrationDocumentsLoading] = useState(false);
  const [documentReadingCountdowns, setDocumentReadingCountdowns] = useState<Record<string, number>>({});
  const [acceptedDocumentKeys, setAcceptedDocumentKeys] = useState<string[]>([]);
  const [stageForm, setStageForm] = useState<CompetitionStageFormRecord>();
  const [stageFormLoading, setStageFormLoading] = useState(false);
  const [registrationId, setRegistrationId] = useState<number>();
  const [registrationRecord, setRegistrationRecord] = useState<CompetitionRegistrationRecord>();
  const [paymentStatus, setPaymentStatus] = useState<string>();
  const [paymentOrder, setPaymentOrder] = useState<CompetitionPaymentOrderRecord>();
  const [paymentModalOpen, setPaymentModalOpen] = useState(false);
  const [materialFileRecords, setMaterialFileRecords] = useState<Record<number, FileObjectRecord>>({});
  const [paymentOptions, setPaymentOptions] = useState<CompetitionPaymentOptionRecord[]>([]);
  const [selectedPaymentProvider, setSelectedPaymentProvider] = useState<string>();
  const [registrationDraftSavedAt, setRegistrationDraftSavedAt] = useState<number>();
  const [registrationDraftHydrated, setRegistrationDraftHydrated] = useState(false);
  const [registrationCompetitionFallback, setRegistrationCompetitionFallback] = useState<CompetitionRecord>();
  const [teamAvatarUploading, setTeamAvatarUploading] = useState(false);
  const [projectAvatarUploading, setProjectAvatarUploading] = useState(false);
  const confirmedTeamIdRef = useRef<number | undefined>(undefined);
  const confirmedProjectIdRef = useRef<number | undefined>(undefined);
  const registrationDraftSaveTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const latestRegistrationDraftRef = useRef<CompetitionRegistrationDraftStorage | undefined>(undefined);
  const [form] = Form.useForm<RegistrationFormValues>();
  const [memberForm] = Form.useForm<RegistrationTeamMemberDraft>();
  const [memberModalOpen, setMemberModalOpen] = useState(false);
  const [editingMemberIndex, setEditingMemberIndex] = useState<number | undefined>(undefined);
  const [memberEditorKey, setMemberEditorKey] = useState<RegistrationMemberEditorKey>();
  const [memberEditorDraft, setMemberEditorDraft] = useState<RegistrationTeamMemberDraft>();
  const [memberEditorErrors, setMemberEditorErrors] = useState<Record<string, string>>({});
  const selectedCompetitionId = Form.useWatch('competitionId', form);
  const newTeamAvatarUrl = Form.useWatch(['newTeam', 'avatarUrl'], form);
  const newProjectImageUrl = Form.useWatch('newProjectImageUrl', form);
  const registrationMembers = (Form.useWatch(['newTeam', 'initialMembers'], form) || []) as RegistrationTeamMemberDraft[];
  const registrationCompetitionOptions = useMemo(
    () => mergeRegistrationCompetitionOptions(competitions, registrationCompetitionFallback),
    [competitions, registrationCompetitionFallback],
  );
  const fields = useMemo(
    () => enrichCompetitionStageFormFields(parseFormFields(stageForm), stageMaterialConfigs),
    [stageForm, stageMaterialConfigs],
  );
  const selectedCompetition = registrationCompetitionOptions.find((item) => item.id === toPositiveId(selectedCompetitionId));
  const registrationScopeFields = useMemo(
    () => splitConfiguredRegistrationFields(registrationFields, 'REGISTRATION_FIELD').customFields,
    [registrationFields],
  );
  const teamFieldSplit = useMemo(
    () => splitConfiguredRegistrationFields(registrationFields, 'TEAM_FIELD'),
    [registrationFields],
  );
  const memberFieldSplit = useMemo(
    () => splitConfiguredRegistrationFields(registrationFields, 'MEMBER_FIELD'),
    [registrationFields],
  );
  const projectFieldSplit = useMemo(
    () => splitConfiguredRegistrationFields(registrationFields, 'PROJECT_FIELD'),
    [registrationFields],
  );
  const effectiveMemberRegistrationFields = useMemo(
    () => prioritizeRequiredMemberNameField(
      memberFieldSplit.allFields,
      requiredSystemRegistrationField('MEMBER_FIELD', 'memberName', '成员姓名'),
    ),
    [memberFieldSplit.allFields],
  );
  // Team name, member name and project title are persistence invariants in the
  // registration API. Older or partially migrated configurations may not
  // contain them, so keep only these three safe fallbacks. Every optional
  // standard field remains fully controlled by the competition settings.
  const teamNameField = teamFieldSplit.overrides.get('teamName')
    || requiredSystemRegistrationField('TEAM_FIELD', 'teamName', '团队名称');
  const teamAvatarField = teamFieldSplit.overrides.get('avatarUrl');
  const teamDescriptionField = teamFieldSplit.overrides.get('description');
  const projectTitleField = projectFieldSplit.overrides.get('title')
    || requiredSystemRegistrationField('PROJECT_FIELD', 'title', '项目名称');
  const projectImageField = projectFieldSplit.overrides.get('imageUrl');
  const projectDescriptionField = projectFieldSplit.overrides.get('description');
  const projectCustomFields = projectFieldSplit.customFields.filter((field) => field.groupLabel !== INTELLECTUAL_PROPERTY_GROUP_LABEL);
  const intellectualPropertyFields = projectFieldSplit.customFields.filter((field) => field.groupLabel === INTELLECTUAL_PROPERTY_GROUP_LABEL);
  const registrationDocumentStates = useMemo(
    () =>
      registrationDocuments.map((item, index) => {
        const documentKey = getRegistrationDocumentKey(item, index);
        return {
          item,
          documentKey,
          readingSeconds: getConfigItemReadingSeconds(item),
          countdown: documentReadingCountdowns[documentKey] || 0,
          accepted: acceptedDocumentKeys.includes(documentKey),
        };
      }),
    [acceptedDocumentKeys, documentReadingCountdowns, registrationDocuments],
  );
  const pendingRegistrationDocumentCount = useMemo(
    () => registrationDocumentStates.filter((item) => item.countdown > 0 || !item.accepted).length,
    [registrationDocumentStates],
  );
  const activeRegistrationDocumentCountdown = useMemo(
    () => Math.max(0, ...registrationDocumentStates.map((item) => item.countdown)),
    [registrationDocumentStates],
  );
  const allRegistrationDocumentsAccepted = useMemo(
    () => !registrationDocumentStates.length || pendingRegistrationDocumentCount === 0,
    [pendingRegistrationDocumentCount, registrationDocumentStates.length],
  );

  useEffect(() => {
    const toDateValue = (value: unknown) => {
      if (!value || dayjs.isDayjs(value)) return value;
      const parsed = dayjs(String(value));
      return parsed.isValid() ? parsed : value;
    };
    [...registrationScopeFields, ...teamFieldSplit.customFields, ...projectFieldSplit.customFields]
      .filter((field) => field.fieldType?.toUpperCase() === 'DATE')
      .forEach((field) => {
        const path = field.scope === 'REGISTRATION_FIELD'
          ? ['registrationExtraValues', field.itemKey]
          : field.scope === 'TEAM_FIELD'
            ? ['newTeam', 'extraValues', field.itemKey]
            : ['newProjectExtraValues', field.itemKey];
        const current = form.getFieldValue(path);
        const next = toDateValue(current);
        if (next !== current) form.setFieldValue(path, next);
      });
    const currentMembers = (form.getFieldValue(['newTeam', 'initialMembers']) || []) as RegistrationTeamMemberDraft[];
    const dateMemberFields = effectiveMemberRegistrationFields.filter((field) => field.fieldType?.toUpperCase() === 'DATE');
    if (dateMemberFields.length && currentMembers.length) {
      let changed = false;
      const nextMembers = currentMembers.map((member) => dateMemberFields.reduce((current, field) => {
        const currentValue = getMemberCollectedFieldValue(current, field);
        const nextValue = toDateValue(currentValue);
        if (nextValue === currentValue) return current;
        changed = true;
        return setMemberCollectedFieldValue(current, field, nextValue);
      }, member));
      if (changed) form.setFieldValue(['newTeam', 'initialMembers'], nextMembers);
    }
  }, [effectiveMemberRegistrationFields, form, projectFieldSplit.customFields, registrationScopeFields, teamFieldSplit.customFields]);
  const canAccessRegistrationPage = registrationActionPermission.can([
    'aiadc:registration:view',
    'aiadc:registration:create',
    'aiadc:registration:update',
    'aiadc:registration:pay',
    'aiadc:material:view',
    'aiadc:material:submit',
    'aiadc:stage:view',
    'aiadc:stage:manage',
    'payment:order:view',
  ]);
  const canViewRegistrationList = canAccessRegistrationPage;
  const canCreateRegistration = registrationActionPermission.can('aiadc:registration:create');
  const canUpdateRegistration = registrationActionPermission.can('aiadc:registration:update');
  const canPayRegistration = registrationActionPermission.can('aiadc:registration:pay');
  const canLoadRegistrationCompetitions = registrationActionPermission.can([
    'aiadc:competition:view',
    'aiadc:registration:view',
    'aiadc:registration:create',
    'aiadc:registration:update',
    'aiadc:registration:pay',
    'aiadc:material:view',
    'aiadc:material:submit',
    'aiadc:stage:view',
    'aiadc:stage:manage',
    'payment:order:view',
  ]);
  const collectRegistrationValues = useCallback(() => sanitizeRegistrationFormValues({
    ...defaultRegistrationFormValues,
    ...(form.getFieldsValue(true) as Partial<RegistrationFormValues>),
  }), [form]);
  const writeCurrentRegistrationDraftState = useCallback((
    nextValues: Partial<RegistrationFormValues> = collectRegistrationValues(),
    nextStep = step,
    nextAcceptedDocumentKeys = acceptedDocumentKeys,
    nextRegistrationId = registrationId,
    nextPaymentStatus = paymentStatus,
  ) => {
    const savedAt = Date.now();
    const latestDraft = latestRegistrationDraftRef.current;
    const draftState: CompetitionRegistrationDraftStorage = {
      competitionTitle: selectedCompetition?.title || latestDraft?.competitionTitle,
      competitionUuid: selectedCompetition?.uuid || latestDraft?.competitionUuid,
      registrationId: nextRegistrationId,
      currentStep: nextStep,
      flowVersion: REGISTRATION_WIZARD_FLOW_VERSION,
      acceptedDocumentKeys: nextAcceptedDocumentKeys,
      confirmedTeamId: confirmedTeamIdRef.current,
      confirmedProjectId: confirmedProjectIdRef.current,
      paymentStatus: nextPaymentStatus,
      savedAt,
      values: sanitizeRegistrationFormValues({
        ...defaultRegistrationFormValues,
        ...nextValues,
      }),
    };
    void writeCompetitionRegistrationDraft(registrationDraftStorageKey, draftState);
    setRegistrationDraftSavedAt(savedAt);
    return draftState;
  }, [acceptedDocumentKeys, collectRegistrationValues, paymentStatus, registrationDraftStorageKey, registrationId, selectedCompetition?.title, selectedCompetition?.uuid, step]);
  const persistRegistrationDraft = useCallback((
    nextValues: Partial<RegistrationFormValues> = collectRegistrationValues(),
    nextStep = step,
    nextAcceptedDocumentKeys = acceptedDocumentKeys,
    nextRegistrationId = registrationId,
    nextPaymentStatus = paymentStatus,
  ) => {
    if (
      !nextRegistrationId
      && !nextAcceptedDocumentKeys.length
      && !hasCompetitionRegistrationDraftContent(nextValues)
      && nextStep <= 0
    ) {
      void clearCompetitionRegistrationDraft(registrationDraftStorageKey);
      latestRegistrationDraftRef.current = undefined;
      setRegistrationDraftSavedAt(undefined);
      return;
    }
    const draftState = writeCurrentRegistrationDraftState(
      nextValues,
      nextStep,
      nextAcceptedDocumentKeys,
      nextRegistrationId,
      nextPaymentStatus,
    );
    latestRegistrationDraftRef.current = draftState;
    if (registrationDraftSaveTimerRef.current) {
      clearTimeout(registrationDraftSaveTimerRef.current);
    }
    registrationDraftSaveTimerRef.current = setTimeout(() => {
      if (!hasCompetitionRegistrationDraftContent(draftState.values || {}) && !draftState.registrationId) {
        writeCurrentRegistrationDraftState(draftState.values, draftState.currentStep, draftState.acceptedDocumentKeys);
      }
    }, 600);
  }, [
    acceptedDocumentKeys,
    collectRegistrationValues,
    paymentStatus,
    registrationDraftStorageKey,
    registrationId,
    step,
    writeCurrentRegistrationDraftState,
  ]);
  const hydrateRegistrationDraft = useCallback((draft?: CompetitionRegistrationDraftStorage) => {
    const values = sanitizeRegistrationFormValues({
      ...defaultRegistrationFormValues,
      ...(draft?.values || {}),
    });
    form.resetFields();
    form.setFieldsValue(values);
    confirmedTeamIdRef.current = draft?.confirmedTeamId;
    confirmedProjectIdRef.current = draft?.confirmedProjectId;
    setRegistrationId(draft?.registrationId);
    setPaymentStatus(draft?.paymentStatus);
    setAcceptedDocumentKeys(draft?.acceptedDocumentKeys || []);
    setRegistrationDraftSavedAt(draft?.savedAt);
    setRegistrationCompetitionFallback(buildRegistrationCompetitionFallback(
      toPositiveId(values.competitionId),
      {
        competitionUuid: draft?.competitionUuid,
        competitionTitle: draft?.competitionTitle,
      },
    ));
    latestRegistrationDraftRef.current = draft;
    return values;
  }, [form]);
  const acceptRegistrationDocument = useCallback((documentKey: string, checked: boolean) => {
    setAcceptedDocumentKeys((current) => {
      const nextKeys = checked
        ? (current.includes(documentKey) ? current : [...current, documentKey])
        : current.filter((key) => key !== documentKey);
      persistRegistrationDraft(collectRegistrationValues(), step, nextKeys);
      return nextKeys;
    });
  }, [collectRegistrationValues, persistRegistrationDraft, step]);
  const resetRegistrationDocumentProgress = useCallback(
    (documents: CompetitionConfigItem[]) => {
      setDocumentReadingCountdowns(
        Object.fromEntries(
          documents.map((item, index) => [getRegistrationDocumentKey(item, index), getConfigItemReadingSeconds(item)]),
        ),
      );
      setAcceptedDocumentKeys([]);
    },
    [],
  );

  const setWizardStep = useCallback((
    nextStep: number,
    replace = true,
    draftOptions: { acceptedDocumentKeys?: string[]; registrationId?: number; paymentStatus?: string } = {},
  ) => {
    const normalizedStep = Math.min(Math.max(nextStep, 0), registrationWizardMaxStep);
    persistRegistrationDraft(
      collectRegistrationValues(),
      normalizedStep,
      draftOptions.acceptedDocumentKeys ?? acceptedDocumentKeys,
      draftOptions.registrationId ?? registrationId,
      draftOptions.paymentStatus ?? paymentStatus,
    );
    setStep(normalizedStep);
    setViewMode('wizard');
    const navigate = replace ? history.replace : history.push;
    navigate({
      pathname: location.pathname,
      search: createRegistrationWizardSearch(normalizedStep),
    });
  }, [acceptedDocumentKeys, collectRegistrationValues, location.pathname, paymentStatus, persistRegistrationDraft, registrationId]);

  const showRegistrationList = useCallback(() => {
    persistRegistrationDraft();
    setViewMode('list');
    history.replace({ pathname: location.pathname, search: '' });
  }, [location.pathname, persistRegistrationDraft]);

  useEffect(() => {
    let mounted = true;
    if (!canLoadRegistrationCompetitions || (viewMode !== 'wizard' && !canViewRegistrationList)) {
      setCompetitions([]);
      return () => {
        mounted = false;
      };
    }
    void listCompetitions({ status: 'published', pageSize: 100 })
      .then((response) => {
        if (mounted) {
          setCompetitions(response.records || []);
        }
      })
      .catch((error) => {
        if (mounted) {
          showErrorMessage(error, '赛事列表加载失败');
          setCompetitions([]);
        }
      });
    return () => {
      mounted = false;
    };
  }, [canLoadRegistrationCompetitions, canViewRegistrationList, viewMode]);

  useEffect(() => {
    let cancelled = false;
    void readCompetitionRegistrationDraft(registrationDraftStorageKey)
      .then((draft) => {
        if (!cancelled) hydrateRegistrationDraft(draft);
      })
      .finally(() => {
        if (!cancelled) setRegistrationDraftHydrated(true);
      });
    return () => { cancelled = true; };
  }, [hydrateRegistrationDraft, registrationDraftStorageKey]);

  useEffect(() => () => {
    if (registrationDraftSaveTimerRef.current) {
      clearTimeout(registrationDraftSaveTimerRef.current);
    }
  }, []);

  useEffect(() => {
    if (!registrationDraftHydrated) {
      return;
    }
    const requestedStep = parseRegistrationWizardStepFromSearch(location.search);
    if (requestedStep === undefined) {
      setViewMode('list');
      return;
    }
    setViewMode('wizard');
    if (registrationDocumentsLoading) {
      setStep(requestedStep);
      return;
    }
    const values = collectRegistrationValues();
    const allowedStep = getAllowedRegistrationWizardStep(requestedStep, values, allRegistrationDocumentsAccepted, registrationId);
    if (allowedStep !== requestedStep) {
      setStep(allowedStep);
      history.replace({
        pathname: location.pathname,
        search: createRegistrationWizardSearch(allowedStep),
      });
      message.warning(allowedStep === 0 ? '请先选择赛事并确认报名文书' : allowedStep === 1 ? '请先补全团队信息' : '请先补全项目信息');
      return;
    }
    setStep((currentValue) => (currentValue === requestedStep ? currentValue : requestedStep));
  }, [
    allRegistrationDocumentsAccepted,
    collectRegistrationValues,
    location.pathname,
    location.search,
    registrationDocumentsLoading,
    registrationDraftHydrated,
    registrationId,
  ]);

  useEffect(() => {
    let mounted = true;
    const competitionId = toPositiveId(selectedCompetitionId);
    if (!competitionId) {
      setRegistrationCompetitionFallback(undefined);
      return () => {
        mounted = false;
      };
    }
    if (competitions.some((item) => item.id === competitionId)) {
      setRegistrationCompetitionFallback(undefined);
      return () => {
        mounted = false;
      };
    }
    const persistedFallback = buildRegistrationCompetitionFallback(competitionId, {
      competitionUuid: latestRegistrationDraftRef.current?.competitionUuid,
      competitionTitle: latestRegistrationDraftRef.current?.competitionTitle,
    });
    if (persistedFallback) {
      setRegistrationCompetitionFallback(persistedFallback);
      if (persistedFallback.uuid) {
        return () => {
          mounted = false;
        };
      }
    }
    void getCompetition(competitionId, API_OPTS.SILENT)
      .then((competition) => {
        if (mounted) {
          setRegistrationCompetitionFallback(competition);
        }
      })
      .catch(() => {
        if (mounted && !persistedFallback) {
          setRegistrationCompetitionFallback(undefined);
        }
      });
    return () => {
      mounted = false;
    };
  }, [competitions, selectedCompetitionId]);


  useEffect(() => {
    let mounted = true;
    const draftCompetitionState = latestRegistrationDraftRef.current;
    const activeCompetitionId = toPositiveId(selectedCompetitionId)
      || toPositiveId(draftCompetitionState?.values?.competitionId);
    const knownCompetitionUuid = selectedCompetition?.uuid
      || (toPositiveId(registrationCompetitionFallback?.id) === activeCompetitionId
        ? registrationCompetitionFallback?.uuid
        : undefined)
      || (toPositiveId(draftCompetitionState?.values?.competitionId) === activeCompetitionId
        ? draftCompetitionState?.competitionUuid
        : undefined);
    setRegistrationDocuments([]);
    setRegistrationFields([]);
    setTeamMemberLimits({ minMembers: DEFAULT_TEAM_MIN_MEMBERS, maxMembers: DEFAULT_TEAM_MAX_MEMBERS });
    setStageMaterialConfigs([]);
    resetRegistrationDocumentProgress([]);
    if (viewMode !== 'wizard') {
      setRegistrationDocumentsLoading(false);
      return () => {
        mounted = false;
      };
    }
    setRegistrationDocumentsLoading(true);
    const loadRegistrationSettings = async () => {
      try {
        let competitionUuid = knownCompetitionUuid;
        if (!competitionUuid) {
          if (!activeCompetitionId) {
            return;
          }
          const competition = await getCompetition(activeCompetitionId, API_OPTS.SILENT);
          if (!mounted) {
            return;
          }
          setRegistrationCompetitionFallback(competition);
          competitionUuid = competition.uuid;
        }
        if (!competitionUuid) {
          throw new Error('赛事 UUID 缺失，无法加载报名字段配置');
        }
        const settings = await getCompetitionSettings(competitionUuid);
        if (!mounted) {
          return;
        }
        const nextDocuments = (settings.documents || [])
          .filter((item) => item.enabled !== false)
          .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0));
        setRegistrationDocuments(nextDocuments);
        resetRegistrationDocumentProgress(nextDocuments);
        const currentDraft = latestRegistrationDraftRef.current;
        if (currentDraft && toPositiveId(currentDraft.values?.competitionId) === activeCompetitionId) {
          const nextDocumentKeys = nextDocuments.map((item, index) => getRegistrationDocumentKey(item, index));
          setAcceptedDocumentKeys((currentDraft.acceptedDocumentKeys || []).filter((key) => nextDocumentKeys.includes(key)));
        }
        setRegistrationFields(
          (settings.fields || [])
            .filter((item) => item.itemType !== 'TEAM_SETTINGS' && item.enabled !== false)
            .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0)),
        );
        setTeamMemberLimits(getTeamMemberLimits(settings.fields || []));
        setStageMaterialConfigs(
          ([...(settings.files || []), ...(settings.stageMaterials || [])])
            .filter((item) => item.enabled !== false)
            .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0)),
        );
      } catch (error) {
        if (mounted) {
          showErrorMessage(error, '报名文书加载失败');
        }
      } finally {
        if (mounted) {
          setRegistrationDocumentsLoading(false);
        }
      }
    };
    void loadRegistrationSettings();
    return () => {
      mounted = false;
    };
  }, [
    registrationCompetitionFallback?.id,
    registrationCompetitionFallback?.uuid,
    registrationDraftHydrated,
    resetRegistrationDocumentProgress,
    selectedCompetition?.uuid,
    selectedCompetitionId,
    viewMode,
  ]);

  useEffect(() => {
    if (step !== 0 || viewMode !== 'wizard' || activeRegistrationDocumentCountdown <= 0) {
      return;
    }
    const timer = window.setTimeout(() => {
      setDocumentReadingCountdowns((current) =>
        Object.fromEntries(Object.entries(current).map(([key, value]) => [key, Math.max(0, value - 1)])),
      );
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [activeRegistrationDocumentCountdown, step, viewMode]);

  const uploadRegistrationTeamAvatar = async (file: File) => {
    if (!file.type.startsWith('image/')) {
      message.error('请上传图片文件');
      return;
    }
    if (file.size > 20 * 1024 * 1024) {
      message.error('请上传小于 20MB 的图片');
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    setTeamAvatarUploading(true);
    try {
      const uploadedUrl = await request<string>('/v1/system/uploads/image', {
        method: 'POST',
        headers: {},
        data: formData,
      });
      if (uploadedUrl) {
        form.setFieldValue(['newTeam', 'avatarUrl'], uploadedUrl);
        persistRegistrationDraft({
          ...collectRegistrationValues(),
          newTeam: {
            ...(form.getFieldValue('newTeam') || {}),
            avatarUrl: uploadedUrl,
          },
        });
        message.success('上传成功');
      }
    } catch (error) {
      showErrorMessage(error, '上传失败');
    } finally {
      setTeamAvatarUploading(false);
    }
  };

  const uploadRegistrationProjectAvatar = async (file: File) => {
    if (!file.type.startsWith('image/')) {
      message.error('请上传图片文件');
      return;
    }
    if (file.size > 20 * 1024 * 1024) {
      message.error('请上传小于 20MB 的图片');
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    setProjectAvatarUploading(true);
    try {
      const uploadedUrl = await request<string>('/v1/system/uploads/image', {
        method: 'POST',
        headers: {},
        data: formData,
      });
      if (uploadedUrl) {
        form.setFieldValue('newProjectImageUrl', uploadedUrl);
        persistRegistrationDraft({
          ...collectRegistrationValues(),
          newProjectImageUrl: uploadedUrl,
        });
        message.success('项目头像上传成功');
      }
    } catch (error) {
      showErrorMessage(error, '项目头像上传失败');
    } finally {
      setProjectAvatarUploading(false);
    }
  };

  const loadStageFormForCompetition = useCallback(async (competitionId: number) => {
    setStageFormLoading(true);
    try {
      setStageForm(await loadOptionalPreliminaryStageForm(competitionId, listCompetitionStages, getCompetitionStageForm));
    } finally {
      setStageFormLoading(false);
    }
  }, []);

  useEffect(() => {
    if (step !== registrationWizardStep.preliminaryMaterials) {
      return;
    }
    const competitionId = toPositiveId(selectedCompetitionId);
    if (!competitionId) {
      return;
    }
    void loadStageFormForCompetition(competitionId).catch((error) => {
      showErrorMessage(error, '初赛材料表单加载失败');
    });
  }, [loadStageFormForCompetition, selectedCompetitionId, step]);

  const startNewRegistration = async () => {
    const draft = await readCompetitionRegistrationDraft(registrationDraftStorageKey);
    hydrateRegistrationDraft(draft);
    setStageForm(undefined);
    setRegistrationRecord(undefined);
    setPaymentOrder(undefined);
    setPaymentModalOpen(false);
    setMemberModalOpen(false);
    setEditingMemberIndex(undefined);
    cancelMemberInlineEditor();
    setWizardStep(normalizeRegistrationWizardDraftStep(draft?.currentStep, draft?.flowVersion), false, {
      acceptedDocumentKeys: draft?.acceptedDocumentKeys || [],
      registrationId: draft?.registrationId,
      paymentStatus: draft?.paymentStatus,
    });
  };

  const openRegistrationFlow = useCallback(async (record: CompetitionRegistrationRecord) => {
    setLoading(true);
    try {
      const latest = await getRegistration(record.id);
      if (isRegistrationPaymentSuccessful(latest.status)) {
        history.push(`/competitions/register/payment-result?registrationId=${latest.id}`);
        return;
      }
      const competitionId = toPositiveId(latest.competitionId);
      const teamId = toPositiveId(latest.teamId);
      const projectId = toPositiveId(latest.projectId);
      const teamSnapshot = parseRegistrationSnapshot<RegistrationSnapshotTeamPayload & { registrationExtraValues?: Record<string, unknown> }>(latest.teamSnapshotJson);
      const projectSnapshot = parseRegistrationSnapshot<{ title?: string; description?: string; imageUrl?: string; extraValues?: Record<string, unknown> }>(latest.projectSnapshotJson);
      const members = parseRegistrationSnapshot<RegistrationTeamMemberDraft[]>(latest.memberSnapshotJson, []);
      form.resetFields();
      form.setFieldsValue({
        competitionId,
        teamId,
        projectId,
        registrationExtraValues: teamSnapshot.registrationExtraValues,
        newTeamName: teamSnapshot.teamName,
        newTeam: { ...teamSnapshot, initialMembers: members },
        newProjectTitle: projectSnapshot.title,
        newProjectDescription: projectSnapshot.description,
        newProjectImageUrl: projectSnapshot.imageUrl,
        newProjectExtraValues: projectSnapshot.extraValues,
      });
      confirmedTeamIdRef.current = teamId;
      confirmedProjectIdRef.current = projectId;
      setRegistrationId(latest.id);
      setRegistrationRecord(latest);
      setPaymentStatus(latest.status);
      if (competitionId) {
        await loadStageFormForCompetition(competitionId);
      }
      setWizardStep(latest.paymentOrderNo ? 5 : 4, false, {
        registrationId: latest.id,
        paymentStatus: latest.status,
      });
    } catch (error) {
      showErrorMessage(error, '报名记录加载失败');
    } finally {
      setLoading(false);
    }
  }, [form, loadStageFormForCompetition, setWizardStep]);

  const removePendingRegistration = useCallback((record: CompetitionRegistrationRecord) => {
    modal.confirm({
      title: '确认取消该报名？',
      content: record.paymentOrderNo
        ? '取消报名后将同步关闭已生成的待支付订单，且无法恢复。'
        : '取消后该待支付报名将无法恢复。',
      okText: '确认取消',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        await deleteRegistration(record.id);
        message.success(record.paymentOrderNo ? '报名与待支付订单已取消' : '报名已取消');
        registrationActionRef.current?.reload();
      },
    });
  }, []);

  const goNext = async () => {
    const isUpdating = Boolean(registrationId);
    if (!isUpdating && !canCreateRegistration) {
      message.error('当前账号没有创建赛事报名权限');
      return;
    }
    if (isUpdating && !canUpdateRegistration) {
      message.error('当前账号没有编辑赛事报名权限');
      return;
    }
    try {
      if (step === 0) {
        await form.validateFields();
        if (registrationDocumentsLoading) {
          message.info('报名文书仍在加载，请稍后');
          return;
        }
        if (!allRegistrationDocumentsAccepted) {
          message.info(
            activeRegistrationDocumentCountdown > 0
              ? `请先完成文书阅读，还需等待 ${activeRegistrationDocumentCountdown} 秒后才能确认。`
              : '请先确认阅读文书条款',
          );
          return;
        }
        confirmedTeamIdRef.current = undefined;
        confirmedProjectIdRef.current = undefined;
        form.setFieldValue('teamId', undefined);
        setWizardStep(1);
      } else if (step === 1) {
        const teamName = form.getFieldValue('newTeamName')?.trim();
        if (!teamName) {
          message.error(`请输入${teamNameField.title}`);
          return;
        }
        if (memberEditorKey !== undefined) {
          message.error('请先保存当前正在编辑的成员信息');
          return;
        }
        const members = normalizeRegistrationMembers(form.getFieldValue(['newTeam', 'initialMembers']) as RegistrationTeamMemberDraft[]);
        if (members.length < teamMemberLimits.minMembers) {
          message.error(`团队至少需要 ${teamMemberLimits.minMembers} 位成员`);
          return;
        }
        if (members.length > teamMemberLimits.maxMembers) {
          message.error(`团队最多只能有 ${teamMemberLimits.maxMembers} 位成员`);
          return;
        }
        form.setFieldValue(['newTeam', 'initialMembers'], members);
        await form.validateFields();
        form.setFieldValue('teamId', undefined);
        confirmedTeamIdRef.current = undefined;
        const competitionId = toPositiveId(form.getFieldValue('competitionId')) || toPositiveId(selectedCompetitionId);
        if (!competitionId) {
          message.error('赛事信息不存在');
          return;
        }
        setWizardStep(registrationWizardStep.preliminaryMaterials);
      } else if (step === registrationWizardStep.preliminaryMaterials) {
        if (stageFormLoading) {
          message.info('初赛材料表单仍在加载，请稍后');
          return;
        }
        await form.validateFields();
        setWizardStep(registrationWizardStep.projectEvidence);
      } else if (step === registrationWizardStep.projectEvidence) {
        await form.validateFields();
        const competitionId = toPositiveId(form.getFieldValue('competitionId')) || toPositiveId(selectedCompetitionId);
        if (!competitionId || (!toPositiveId(form.getFieldValue('projectId')) && !form.getFieldValue('newProjectTitle')?.trim())) {
          message.error('请先选择赛事并填写项目名称');
          return;
        }
        setWizardStep(registrationWizardStep.review);
      } else if (step === registrationWizardStep.review) {
        await form.validateFields();
        const competitionId = toPositiveId(form.getFieldValue('competitionId')) || toPositiveId(selectedCompetitionId);
        if (!competitionId) {
          message.error('赛事信息不存在');
          return;
        }
        const teamDraft = (form.getFieldValue('newTeam') || {}) as RegistrationTeamDraft;
        const registrationExtraValues = pickEnabledCollectedValues(
          normalizeSnapshotValue(form.getFieldValue('registrationExtraValues')) as Record<string, unknown> | undefined,
          registrationScopeFields.map((field) => field.itemKey),
        );
        const projectExtraValues = pickEnabledCollectedValues(
          normalizeSnapshotValue(form.getFieldValue('newProjectExtraValues')) as Record<string, unknown> | undefined,
          projectFieldSplit.customFields.map((field) => field.itemKey),
        );
        const enabledMemberStandardKeys = new Set(
          effectiveMemberRegistrationFields
            .map((field) => resolveMemberStandardFieldKey(field))
            .filter(Boolean),
        );
        const members = normalizeRegistrationMembers(teamDraft.initialMembers).map((member) => ({
          memberName: member.memberName,
          employeeNo: enabledMemberStandardKeys.has('employeeNo') ? member.employeeNo : undefined,
          departmentName: enabledMemberStandardKeys.has('departmentName') ? member.departmentName : undefined,
          role: enabledMemberStandardKeys.has('role') ? member.role : undefined,
          remark: enabledMemberStandardKeys.has('remark') ? member.remark : undefined,
          extraValues: pickEnabledCollectedValues(member.extraValues, memberFieldSplit.customFields.map((field) => field.itemKey)),
        }));
        const teamSnapshot: RegistrationSnapshotTeamPayload = {
          teamName: form.getFieldValue('newTeamName')?.trim(),
          teamType: teamFieldSplit.overrides.has('teamType') ? normalizeSnapshotValue(teamDraft.teamType) as string | undefined : undefined,
          avatarUrl: teamAvatarField ? normalizeSnapshotValue(teamDraft.avatarUrl) as string | undefined : undefined,
          description: teamDescriptionField ? normalizeSnapshotValue(teamDraft.description) as string | undefined : undefined,
          extraValues: pickEnabledCollectedValues(
            normalizeSnapshotValue(teamDraft.extraValues) as Record<string, unknown> | undefined,
            teamFieldSplit.customFields.map((field) => field.itemKey),
          ),
        };
        const projectSnapshot: RegistrationProjectSnapshotPayload | undefined = Object.keys(projectExtraValues).length
          ? { extraValues: projectExtraValues }
          : undefined;
        const registrationPayload: RegistrationUpsertPayload = {
          competitionId,
          teamId: toPositiveId(form.getFieldValue('teamId')),
          projectId: toPositiveId(form.getFieldValue('projectId')),
          registrationExtraValues,
          teamSnapshot,
          projectSnapshot,
          members,
        };
        const materialValues = (form.getFieldValue('materials') || {}) as Record<string, unknown>;
        const confirmPayload = {
          registration: registrationPayload,
          project: registrationPayload.projectId ? undefined : {
            title: form.getFieldValue('newProjectTitle')?.trim(),
            category: 'INNOVATION',
            description: projectDescriptionField ? form.getFieldValue('newProjectDescription') : undefined,
            imageUrl: projectImageField ? form.getFieldValue('newProjectImageUrl') : undefined,
          },
          materials: stageForm ? {
            stageId: stageForm.stageId,
            values: fields.map((field) => ({
              fieldKey: field.key,
              fieldType: field.type || 'text',
              textValue: field.type === 'file' ? undefined : (materialValues[field.key] != null ? String(materialValues[field.key]) : undefined),
              fileId: field.type === 'file' && materialValues[field.key] ? Number(materialValues[field.key]) : undefined,
            })),
          } : undefined,
        };
        setLoading(true);
        const registration = registrationId
          ? await reconfirmRegistration(registrationId, confirmPayload)
          : await confirmRegistration(confirmPayload);
        setRegistrationId(registration.id);
        setRegistrationRecord(registration);
        setPaymentStatus(registration.status);
        confirmedTeamIdRef.current = registration.teamId;
        confirmedProjectIdRef.current = registration.projectId;
        form.setFieldValue('teamId', registration.teamId);
        form.setFieldValue('projectId', registration.projectId);
        registrationActionRef.current?.reload();
        if (registration.payableAmountMinor === 0 || isRegistrationPaymentSuccessful(registration.status)) {
          await clearCompetitionRegistrationDraft(registrationDraftStorageKey);
          latestRegistrationDraftRef.current = undefined;
          history.push(`/competitions/register/payment-result?registrationId=${registration.id}`);
          return;
        }
        setWizardStep(5, true, { registrationId: registration.id, paymentStatus: registration.status });
      }
    } catch (error) {
      showErrorMessage(error, '操作失败');
    } finally {
      setLoading(false);
    }
  };

  const pay = async () => {
    if (!canPayRegistration) {
      message.error('当前账号没有支付报名费用权限');
      return;
    }
    if (!registrationId) {
      message.error('报名记录不存在');
      return;
    }
    setLoading(true);
    try {
      if (!selectedPaymentProvider) {
        message.error('请选择支付方式');
        return;
      }
      const order = await createRegistrationPaymentOrder(registrationId, {
        providerCode: selectedPaymentProvider,
        clientType: detectPaymentClientType(),
        returnUrl: buildRegistrationPaymentResultUrl(window.location.origin, registrationId),
      });
      setPaymentOrder(order);
      setPaymentModalOpen(true);
      registrationActionRef.current?.reload();
      if (!order.paymentUrl) message.info('支付链接正在生成，弹窗会自动刷新');
    } catch (error) {
      showErrorMessage(error, '支付订单生成失败');
    } finally {
      setLoading(false);
    }
  };

  const confirmMemberModal = async () => {
    const values = await memberForm.validateFields();
    const [member] = normalizeRegistrationMembers([values]);
    if (!member) {
      message.error('请填写成员信息');
      return;
    }
    const currentMembers = [...((form.getFieldValue(['newTeam', 'initialMembers']) || []) as RegistrationTeamMemberDraft[])];
    if (editingMemberIndex === undefined) {
      currentMembers.push(member);
    } else {
      currentMembers[editingMemberIndex] = member;
    }
    form.setFieldValue(['newTeam', 'initialMembers'], currentMembers);
    persistRegistrationDraft({
      ...collectRegistrationValues(),
      newTeam: {
        ...(form.getFieldValue('newTeam') || {}),
        initialMembers: currentMembers,
      },
    });
    setMemberModalOpen(false);
    setEditingMemberIndex(undefined);
    memberForm.resetFields();
  };

  const removeMember = useCallback((index: number) => {
    const currentMembers = [...((form.getFieldValue(['newTeam', 'initialMembers']) || []) as RegistrationTeamMemberDraft[])];
    currentMembers.splice(index, 1);
    form.setFieldValue(['newTeam', 'initialMembers'], currentMembers);
    persistRegistrationDraft({
      ...collectRegistrationValues(),
      newTeam: {
        ...(form.getFieldValue('newTeam') || {}),
        initialMembers: currentMembers,
      },
    });
  }, [collectRegistrationValues, form, persistRegistrationDraft]);

  useEffect(() => {
    if (step !== 5 || !registrationId || isRegistrationPaymentSuccessful(paymentStatus)) {
      return;
    }
    let active = true;
    void listRegistrationPaymentOptions(registrationId, detectPaymentClientType())
      .then((options) => {
        if (!active) return;
        setPaymentOptions(options || []);
        setSelectedPaymentProvider((current) => retainAvailablePaymentProvider(
          current,
          options.map((item) => item.providerCode),
        ));
      })
      .catch((error) => showErrorMessage(error, '支付方式加载失败'));
    void getRegistrationPaymentStatus(registrationId)
      .then((order) => {
        if (active) setPaymentOrder(order);
      })
      .catch(() => undefined);
    return () => { active = false; };
  }, [paymentStatus, registrationId, step]);

  const refreshPaymentResult = useCallback(async (silent = false) => {
    if (!registrationId) return;
    try {
      const [latestOrderResult, latestRegistrationResult] = await Promise.allSettled([
        getRegistrationPaymentStatus(registrationId),
        getRegistration(registrationId),
      ]);
      if (latestRegistrationResult.status === 'rejected') throw latestRegistrationResult.reason;
      const latestRegistration = latestRegistrationResult.value;
      if (latestOrderResult.status === 'fulfilled') setPaymentOrder(latestOrderResult.value);
      setRegistrationRecord(latestRegistration);
      setPaymentStatus(latestRegistration.status);
      if (isRegistrationPaymentSuccessful(latestRegistration.status)) {
        setPaymentModalOpen(false);
        await clearCompetitionRegistrationDraft(registrationDraftStorageKey);
        latestRegistrationDraftRef.current = undefined;
        setRegistrationDraftSavedAt(undefined);
        history.push(`/competitions/register/payment-result?registrationId=${registrationId}`);
      }
    } catch (error) {
      if (!silent) showErrorMessage(error, '支付结果查询失败');
    }
  }, [registrationDraftStorageKey, registrationId]);

  useEffect(() => {
    if (!paymentModalOpen || !registrationId) return;
    const timer = window.setInterval(() => void refreshPaymentResult(true), 3000);
    return () => window.clearInterval(timer);
  }, [paymentModalOpen, refreshPaymentResult, registrationId]);

  useEffect(() => {
    if (step !== 4) return;
    const materialValues = (form.getFieldValue('materials') || {}) as Record<string, unknown>;
    const fileIds = Array.from(new Set(fields
      .filter((field) => field.type === 'file')
      .map((field) => Number(materialValues[field.key]))
      .filter((fileId) => Number.isSafeInteger(fileId) && fileId > 0)));
    if (!fileIds.length) {
      setMaterialFileRecords({});
      return;
    }
    let active = true;
    void Promise.all(fileIds.map((fileId) => request<FileObjectRecord>(`/v1/files/${fileId}`, { method: 'GET', silent: true })))
      .then((records) => {
        if (active) setMaterialFileRecords(Object.fromEntries(records.map((record) => [record.id, record])));
      })
      .catch(() => {
        if (active) setMaterialFileRecords({});
      });
    return () => { active = false; };
  }, [fields, form, step]);

  const getMemberDisplayValues = (member: RegistrationTeamMemberDraft) =>
    Array.from(
      new Set(
        effectiveMemberRegistrationFields
          .map((field) => normalizeDisplayText(getMemberCollectedFieldValue(member, field)))
          .filter(Boolean),
      ),
    ) as string[];

  const _getMemberSummary = (member: RegistrationTeamMemberDraft, index: number) => {
    const displayValues = getMemberDisplayValues(member);
    const extraValues = member.extraValues || {};
    return (
      displayValues[0] ||
      normalizeDisplayText(member.memberName) ||
      normalizeDisplayText(extraValues.name) ||
      normalizeDisplayText(extraValues.memberName) ||
      normalizeDisplayText(extraValues.mobile) ||
      `成员 ${index + 1}`
    );
  };

  const _getMemberDescription = (member: RegistrationTeamMemberDraft) => {
    const displayValues = getMemberDisplayValues(member);
    return displayValues.slice(1).join(' / ') || '已填写报名采集信息';
  };

  const updateMemberEditorField = useCallback((field: RegistrationCollectedField, value: unknown) => {
    setMemberEditorDraft((current) => (current ? setMemberCollectedFieldValue(current, field, value) : current));
    const validationError = field.required && !hasCollectedValue(value)
      ? `请填写${field.title}`
      : validateMemberTextField(field.itemKey, field.title, value);
    setMemberEditorErrors((current) => {
      if (validationError) {
        if (current[field.itemKey] === validationError) {
          return current;
        }
        return { ...current, [field.itemKey]: validationError };
      }
      if (!current[field.itemKey]) {
        return current;
      }
      const nextErrors = { ...current };
      delete nextErrors[field.itemKey];
      return nextErrors;
    });
  }, []);

  const validateMemberDraft = useCallback((member?: RegistrationTeamMemberDraft) => {
    if (!member) {
      return { memberName: '请填写成员信息' };
    }
    return effectiveMemberRegistrationFields.reduce<Record<string, string>>((errors, field) => {
      const value = getMemberCollectedFieldValue(member, field);
      if (field.required && !hasCollectedValue(value)) {
        errors[field.itemKey] = `请填写${field.title}`;
        return errors;
      }
      const validationError = validateMemberTextField(field.itemKey, field.title, value);
      if (validationError) {
        errors[field.itemKey] = validationError;
      }
      return errors;
    }, {});
  }, [effectiveMemberRegistrationFields]);

  const saveMemberEditor = useCallback(() => {
    const nextErrors = validateMemberDraft(memberEditorDraft);
    if (Object.keys(nextErrors).length) {
      setMemberEditorErrors(nextErrors);
      return;
    }
    const [member] = normalizeRegistrationMembers(memberEditorDraft ? [memberEditorDraft] : []);
    if (!member) {
      message.error('请填写成员信息');
      return;
    }
    const currentMembers = [...((form.getFieldValue(['newTeam', 'initialMembers']) || []) as RegistrationTeamMemberDraft[])];
    if (memberEditorKey === 'new') {
      if (currentMembers.length >= teamMemberLimits.maxMembers) {
        message.error(`团队最多只能添加 ${teamMemberLimits.maxMembers} 位成员`);
        return;
      }
      currentMembers.push(member);
    } else if (memberEditorKey !== undefined) {
      currentMembers[memberEditorKey] = member;
    }
    form.setFieldValue(['newTeam', 'initialMembers'], currentMembers);
    persistRegistrationDraft({
      ...collectRegistrationValues(),
      newTeam: {
        ...(form.getFieldValue('newTeam') || {}),
        initialMembers: currentMembers,
      },
    });
    setMemberEditorKey(undefined);
    setMemberEditorDraft(undefined);
    setMemberEditorErrors({});
  }, [collectRegistrationValues, form, memberEditorDraft, memberEditorKey, persistRegistrationDraft, teamMemberLimits.maxMembers, validateMemberDraft]);

  const openMemberInlineEditor = useCallback((key: RegistrationMemberEditorKey) => {
    const currentMembers = (form.getFieldValue(['newTeam', 'initialMembers']) || []) as RegistrationTeamMemberDraft[];
    setMemberEditorKey(key);
    setMemberEditorDraft(key === 'new' ? emptyRegistrationTeamMember() : currentMembers[key] || emptyRegistrationTeamMember());
    setMemberEditorErrors({});
  }, [form]);

  const cancelMemberInlineEditor = useCallback(() => {
    setMemberEditorKey(undefined);
    setMemberEditorDraft(undefined);
    setMemberEditorErrors({});
  }, []);

  const removeMemberInline = useCallback((index: number) => {
    removeMember(index);
    setMemberEditorKey((current) => {
      if (current === undefined || current === 'new') {
        return current;
      }
      return current > index ? current - 1 : current;
    });
  }, [removeMember]);

  useEffect(() => {
    if (typeof memberEditorKey === 'number' && memberEditorKey >= registrationMembers.length) {
      cancelMemberInlineEditor();
    }
  }, [cancelMemberInlineEditor, memberEditorKey, registrationMembers.length]);

  const getMemberFieldDisplayText = useCallback((member: RegistrationTeamMemberDraft, field: RegistrationCollectedField) => {
    const fieldValue = getMemberCollectedFieldValue(member, field);
    const fieldType = (field.fieldType || 'TEXT').toUpperCase();
    if (fieldType === 'ROLE') {
      return resolveOptionLabel(
        buildOptionLabelMap(parseConfigFieldOptions(field.options || DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS)),
        fieldValue,
      ) || '-';
    }
    if (fieldType === 'SELECT' || fieldType === 'MULTI_SELECT') {
      return resolveOptionLabel(buildOptionLabelMap(parseConfigFieldOptions(field.options)), fieldValue) || '-';
    }
    return normalizeDisplayText(fieldValue) || '-';
  }, []);

  const competitionTitleMap = useMemo(
    () => new Map(registrationCompetitionOptions.map((item) => [item.id, item.title || item.code])),
    [registrationCompetitionOptions],
  );
  const canViewAllRegistrations = useMemo(() => {
    const matchedScopes = (initialState?.currentUser?.dataScopes || []).filter(
      (scope) => scope.resourceCode === '*' || scope.resourceCode === COMPETITION_REGISTRATION_SCOPE_RESOURCE,
    );
    return matchedScopes.some((scope) => scope.scopeType === 'ALL');
  }, [initialState?.currentUser?.dataScopes]);
  const nextButtonDisabled = (step === 0 && (registrationDocumentsLoading || !allRegistrationDocumentsAccepted))
    || (step === registrationWizardStep.preliminaryMaterials && stageFormLoading);
  const canAdvanceRegistration = registrationId ? canUpdateRegistration : canCreateRegistration;
  const nextButtonText = step === 0 && pendingRegistrationDocumentCount > 0
    ? `下一步（剩余 ${pendingRegistrationDocumentCount} 项）`
    : step === registrationWizardStep.review ? '确认并生成订单' : '\u4e0b\u4e00\u6b65';
  const previewPayableAmount = calculateRegistrationPayableAmount(
    selectedCompetition?.entryFeeMinor,
    selectedCompetition?.feeMode,
    registrationMembers.length,
  );

  const registrationColumns = useMemo<ProColumns<CompetitionRegistrationRecord>[]>(
    () => [
      {
        title: '\u62a5\u540d\u8bb0\u5f55',
        dataIndex: 'registrationNo',
        width: 320,
        minWidth: 320,
        fieldProps: {
          placeholder: 'Registration No. / Participant No.',
        },
        render: (_, record) => (
          <Space className="competition-registration-record-cell" direction="vertical" size={0}>
            <Typography.Text className="competition-registration-record-cell__no" strong ellipsis={{ tooltip: record.registrationNo }}>
              {record.registrationNo || `\u62a5\u540d ${record.id}`}
            </Typography.Text>
            {record.participantNo ? (
              <Tag className="competition-registration-record-cell__participant" color="blue">
                {record.participantNo}
              </Tag>
            ) : null}
          </Space>
        ),
      },
      {
        title: '\u8d5b\u4e8b',
        dataIndex: 'competitionId',
        search: false,
        ellipsis: true,
        render: (_, record) => competitionTitleMap.get(record.competitionId) || `\u8d5b\u4e8b ${record.competitionId}`,
      },
      {
        title: '\u56e2\u961f',
        dataIndex: 'teamId',
        search: false,
        ellipsis: true,
        render: (_, record) => parseSnapshotName(record.teamSnapshotJson, ['teamName', 'name']) || `\u56e2\u961f ${record.teamId}`,
      },
      {
        title: '\u9879\u76ee',
        dataIndex: 'projectId',
        search: false,
        ellipsis: true,
        render: (_, record) => parseSnapshotName(record.projectSnapshotJson, ['title', 'projectTitle', 'name']) || `\u9879\u76ee ${record.projectId}`,
      },
      {
        title: '\u72b6\u6001',
        dataIndex: 'status',
        valueType: 'select',
        valueEnum: registrationStatusValueEnum,
        width: 128,
        render: (_, record) => renderRegistrationStatusTag(record.status),
      },
      {
        title: '\u5e94\u4ed8\u8d39\u7528',
        dataIndex: 'payableAmountMinor',
        search: false,
        width: 128,
        render: (_, record) => formatRegistrationAmount(record.payableAmountMinor, record.currency),
      },
      {
        title: '\u62a5\u540d\u65f6\u95f4',
        dataIndex: 'createdAt',
        search: false,
        width: 172,
        render: (value) => formatRegistrationTime(typeof value === 'string' ? value : undefined),
      },
      {
        title: '\u64cd\u4f5c',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 200,
        align: 'right',
        render: (_, record) => (
          <Space size={4}>
            <Button type="text" icon={<EyeOutlined />} loading={loading && registrationId === record.id} onClick={() => void openRegistrationFlow(record)}>
              {record.status === 'PAID' || record.status === 'CONFIRMED' ? '\u67e5\u770b' : '\u7ee7\u7eed'}
            </Button>
            {record.status === 'PENDING_PAYMENT' ? (
              <Button danger type="text" icon={<DeleteOutlined />} onClick={() => removePendingRegistration(record)}>
                取消报名
              </Button>
            ) : null}
          </Space>
        ),
      },
    ],
    [competitionTitleMap, loading, openRegistrationFlow, registrationId, removePendingRegistration, responsive.isDesktop],
  );
  const registrationBreadcrumb = useMemo(
    () => ({
      items: [{ title: '赛事报名' }],
    }),
    [],
  );

  if (viewMode === 'list') {
    return (
      <ManagementPage title={'\u8d5b\u4e8b\u62a5\u540d'} breadcrumb={registrationBreadcrumb}>
        <ManagementPageBody>
          <ManagementTable<CompetitionRegistrationRecord>
            actionRef={registrationActionRef}
            rowKey="id"
            columns={registrationColumns}
            isMobile={responsive.isMobile}
            scroll={{ x: 1360 }}
            request={async (params) => {
              const response = await listRegistrations({
                pageNo: params.current,
                pageSize: params.pageSize,
              });
              const currentUserId = initialState?.currentUser?.userId;
              const scopedRecords = currentUserId == null || canViewAllRegistrations
                ? (response.records || [])
                : (response.records || []).filter((record) => record.ownerUserId == null || record.ownerUserId === currentUserId);
              return {
                data: scopedRecords,
                total: currentUserId == null || canViewAllRegistrations ? response.total : scopedRecords.length,
                success: true,
              };
            }}
            pagination={{ pageSize: 10, showSizeChanger: true }}
            toolBarRender={() => [
              <Button key="refresh" icon={<ReloadOutlined />} onClick={() => registrationActionRef.current?.reload()}>
                {'\u5237\u65b0'}
              </Button>,
              registrationActionPermission.withPermissionGuard(
                'aiadc:registration:create',
                <Button key="new" type="primary" icon={<PlusOutlined />} onClick={startNewRegistration}>
                  {'\u65b0\u589e\u62a5\u540d'}
                </Button>,
              ),
            ]}
          />
          <Form form={form} component={false} />
        </ManagementPageBody>
      </ManagementPage>
    );
  }

  const renderRegistrationMemberManager = () => {
    const memberTableMinWidth = effectiveMemberRegistrationFields.length * 140 + 148;

    const renderMemberEditorInput = (field: RegistrationCollectedField) => {
      const fieldValue = memberEditorDraft ? getMemberCollectedFieldValue(memberEditorDraft, field) : undefined;
      const placeholder = field.placeholder || field.title || undefined;
      const fieldType = (field.fieldType || 'TEXT').toUpperCase();

      switch (fieldType) {
        case 'ROLE':
          return (
            <Select
              value={normalizeOptionValue(fieldValue)}
              options={parseConfigFieldOptions(field.options || DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS)}
              placeholder={placeholder}
              onChange={(value) => updateMemberEditorField(field, value)}
            />
          );
        case 'NUMBER': {
          const parsedNumber = typeof fieldValue === 'number'
            ? fieldValue
            : fieldValue === undefined || fieldValue === null || fieldValue === ''
              ? undefined
              : Number(fieldValue);
          return (
            <InputNumber
              min={0}
              style={{ width: '100%' }}
              value={typeof parsedNumber === 'number' && Number.isFinite(parsedNumber) ? parsedNumber : undefined}
              placeholder={placeholder}
              onChange={(value) => updateMemberEditorField(field, value ?? undefined)}
            />
          );
        }
        case 'TEXTAREA':
          return (
            <Input.TextArea
              autoSize={{ minRows: 1, maxRows: 3 }}
              value={fieldValue == null ? undefined : String(fieldValue)}
              placeholder={placeholder}
              onChange={(event) => updateMemberEditorField(field, event.target.value)}
            />
          );
        case 'DATE':
          return (
            <DatePicker
              style={{ width: '100%' }}
              value={dayjs.isDayjs(fieldValue) ? fieldValue : (typeof fieldValue === 'string' ? parseDateTime(fieldValue) : undefined)}
              placeholder={placeholder}
              onChange={(value) => updateMemberEditorField(field, value ?? undefined)}
            />
          );
        case 'SELECT':
          return (
            <Select
              value={normalizeOptionValue(fieldValue)}
              options={parseConfigFieldOptions(field.options)}
              placeholder={placeholder}
              onChange={(value) => updateMemberEditorField(field, value)}
            />
          );
        case 'MULTI_SELECT':
          return (
            <Select
              mode="multiple"
              value={Array.isArray(fieldValue)
                ? fieldValue.map(normalizeOptionValue).filter((value): value is string => Boolean(value))
                : []}
              options={parseConfigFieldOptions(field.options)}
              placeholder={placeholder}
              onChange={(value) => updateMemberEditorField(field, value)}
            />
          );
        default:
          return (
            <Input
              value={fieldValue == null ? undefined : String(fieldValue)}
              placeholder={placeholder}
              maxLength={fieldType === 'MOBILE' ? 20 : 128}
              onChange={(event) => updateMemberEditorField(field, event.target.value)}
            />
          );
      }
    };

    const renderMemberEditorRow = (rowKey: string | number) => (
      <tr key={rowKey}>
        {effectiveMemberRegistrationFields.map((field) => (
          <td key={`${rowKey}-${field.itemKey}`}>
            <div className="competition-registration-member-manager__editor">
              {renderMemberEditorInput(field)}
              {memberEditorErrors[field.itemKey] ? (
                <div className="competition-registration-member-manager__cell-error">{memberEditorErrors[field.itemKey]}</div>
              ) : null}
            </div>
          </td>
        ))}
        <td className="competition-registration-member-manager__actions">
          <Space size={4} wrap={false}>
            <Button type="link" onClick={() => saveMemberEditor()}>
              保存
            </Button>
            <Button type="link" onClick={cancelMemberInlineEditor}>
              取消
            </Button>
          </Space>
        </td>
      </tr>
    );

    return (
      <div className="competition-registration-member-manager">
        <div className="competition-registration-member-manager__header">
          <div>
            <Typography.Title className="competition-registration-member-manager__title" level={5}>
              成员管理
            </Typography.Title>
            <Typography.Text type="secondary">
              成员字段跟随赛事管理中的报名字段配置，支持逐行添加和编辑。
            </Typography.Text>
          </div>
        </div>
        <div className="competition-registration-member-manager__table-scroll">
          <table className="competition-registration-member-manager__table" style={{ minWidth: `${memberTableMinWidth}px` }}>
            <thead>
              <tr>
                {effectiveMemberRegistrationFields.map((field) => (
                  <th key={field.itemKey}>
                    {field.required ? <span className="competition-registration-member-manager__required">*</span> : null}
                    {field.title}
                  </th>
                ))}
                <th className="competition-registration-member-manager__actions">操作</th>
              </tr>
            </thead>
            <tbody>
              {registrationMembers.map((member, index) => (
                memberEditorKey === index ? (
                  renderMemberEditorRow(index)
                ) : (
                  <tr key={`member-${index}`}>
                    {effectiveMemberRegistrationFields.map((field) => (
                      <td key={`member-${index}-${field.itemKey}`}>
                        <div className="competition-registration-member-manager__cell-text">
                          {getMemberFieldDisplayText(member, field)}
                        </div>
                      </td>
                    ))}
                    <td className="competition-registration-member-manager__actions">
                      <Space size={4} wrap={false}>
                        <Button type="link" disabled={memberEditorKey !== undefined} onClick={() => openMemberInlineEditor(index)}>
                          编辑
                        </Button>
                        <Button danger type="link" disabled={memberEditorKey !== undefined} onClick={() => removeMemberInline(index)}>
                          移除
                        </Button>
                      </Space>
                    </td>
                  </tr>
                )
              ))}
              {memberEditorKey === 'new' ? renderMemberEditorRow('new-member') : null}
              {!registrationMembers.length && memberEditorKey !== 'new' ? (
                <tr>
                  <td className="competition-registration-member-manager__empty" colSpan={effectiveMemberRegistrationFields.length + 1}>
                    请先添加参赛成员，逐个确认成员信息。
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
        <Button
          block
          type="dashed"
          className="competition-registration-member-manager__add"
          disabled={memberEditorKey !== undefined || registrationMembers.length >= teamMemberLimits.maxMembers}
          icon={<PlusOutlined />}
          onClick={() => openMemberInlineEditor('new')}
        >
          {registrationMembers.length >= teamMemberLimits.maxMembers
            ? `已达上限（${teamMemberLimits.maxMembers} 人）`
            : `添加一位成员（${registrationMembers.length}/${teamMemberLimits.maxMembers}）`}
        </Button>
      </div>
    );
  };

  const renderTeamForm = () => (
    <>
      <section className="competition-registration-team-form">
        <div className="competition-registration-team-form__fields">
          <Typography.Title className="competition-registration-team-form__title" level={5}>
            团队信息
          </Typography.Title>
          <Form.Item name="newTeamName" label={teamNameField.title} rules={buildCollectedFieldRule(teamNameField)}>
            <Input maxLength={128} placeholder={teamNameField.placeholder || teamNameField.title} />
          </Form.Item>
          <Form.Item
            name={["newTeam", "avatarUrl"]}
            hidden
            rules={teamAvatarField ? buildCollectedFieldRule(teamAvatarField) : undefined}
          >
            <Input />
          </Form.Item>
          {teamAvatarField ? <Form.Item label={teamAvatarField.title} required={teamAvatarField.required}>
            <Space>
              <Avatar size={48} src={normalizeUploadUrl(newTeamAvatarUrl) || undefined} icon={<TeamOutlined />} />
              <Upload
                accept="image/*"
                showUploadList={false}
                disabled={teamAvatarUploading}
                beforeUpload={async (file) => {
                  await uploadRegistrationTeamAvatar(file);
                  return Upload.LIST_IGNORE;
                }}
              >
                <Button icon={<UploadOutlined />} loading={teamAvatarUploading}>
                  上传
                </Button>
              </Upload>
              {newTeamAvatarUrl ? (
                <Button
                  type="link"
                  onClick={() => {
                    form.setFieldValue(["newTeam", "avatarUrl"], undefined);
                    persistRegistrationDraft({
                      ...collectRegistrationValues(),
                      newTeam: {
                        ...(form.getFieldValue('newTeam') || {}),
                        avatarUrl: undefined,
                      },
                    });
                  }}
                >
                  移除
                </Button>
              ) : null}
            </Space>
          </Form.Item> : null}
          {teamDescriptionField ? <Form.Item name={["newTeam", "description"]} label={teamDescriptionField.title} rules={buildCollectedFieldRule(teamDescriptionField)}>
            <Input.TextArea rows={3} maxLength={1000} placeholder={teamDescriptionField.placeholder || teamDescriptionField.title} />
          </Form.Item> : null}
          {teamFieldSplit.customFields.map((field) => (
            <Form.Item
              key={field.itemKey}
              name={['newTeam', 'extraValues', field.itemKey]}
              label={field.title}
              rules={buildCollectedFieldRule(field)}
            >
              {renderRegistrationCollectedFieldInput(field)}
            </Form.Item>
          ))}
        </div>
        {renderRegistrationMemberManager()}
        <div style={{ display: 'none' }}>
        <Form.List name={["newTeam", "initialMembers"]}>
          {(memberFields, { add, remove }) => (
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              {memberFields.map((memberField, index) => (
                <Card
                  key={memberField.key}
                  size="small"
                  title={`成员 ${index + 1}`}
                  extra={
                    <Button danger type="link" disabled={memberFields.length <= 1} onClick={() => remove(memberField.name)}>
                      移除
                    </Button>
                  }
                >
                  {effectiveMemberRegistrationFields.map((field) => {
                    const fieldName = resolveMemberFieldFormName(field);
                    return (
                      <Form.Item
                        key={`${memberField.key}-${field.itemKey}`}
                        name={Array.isArray(fieldName) ? [memberField.name, ...fieldName] : [memberField.name, fieldName]}
                        label={field.title}
                        rules={buildCollectedFieldRule(field)}
                      >
                        {renderRegistrationCollectedFieldInput(field)}
                      </Form.Item>
                    );
                  })}
                </Card>
              ))}
              <Button block icon={<PlusOutlined />} onClick={() => add(emptyRegistrationTeamMember())}>
                添加成员
              </Button>
            </Space>
          )}
        </Form.List>
        </div>
      </section>
      <Modal
        title={editingMemberIndex === undefined ? '添加成员' : '编辑成员'}
        open={memberModalOpen}
        destroyOnHidden
        onCancel={() => {
          setMemberModalOpen(false);
          setEditingMemberIndex(undefined);
          memberForm.resetFields();
        }}
        onOk={() => void confirmMemberModal()}
      >
        <Form<RegistrationTeamMemberDraft> form={memberForm} layout="vertical">
          {effectiveMemberRegistrationFields.map((field) => (
            <Form.Item
              key={field.itemKey}
              name={resolveMemberFieldFormName(field)}
              label={field.title}
              rules={buildCollectedFieldRule(field)}
            >
              {renderRegistrationCollectedFieldInput(field)}
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </>
  );

  return (
    <ManagementPage title="赛事报名" breadcrumb={registrationBreadcrumb} extra={<Button onClick={showRegistrationList}>返回报名记录</Button>}>
      <ManagementPageBody className="competition-create-page">
        <Card className="competition-create-shell">
          <Steps
            current={step}
            responsive
            items={registrationWizardStepItems}
          />
          <Form<RegistrationFormValues>
            form={form}
            layout="vertical"
            initialValues={defaultRegistrationFormValues}
            onValuesChange={() => persistRegistrationDraft()}
          >
            <div className="competition-create-step">
              {step === 0 ? (
                <>
                  <Form.Item name="competitionId" label="赛事" rules={[{ required: true, message: '请选择赛事' }]}>
                    <Select options={registrationCompetitionOptions.map((item) => ({ label: item.title, value: item.id }))} />
                  </Form.Item>
                  {selectedCompetitionId ? (
                    <div className="competition-registration-documents">
                      {registrationDocumentsLoading ? (
                        <Alert type="info" showIcon message="正在加载报名文书..." />
                      ) : registrationDocumentStates.length ? (
                        <Space direction="vertical" size={12} style={{ width: '100%' }}>
                          {registrationDocumentStates.map(({ item: documentItem, documentKey, readingSeconds, countdown, accepted }, index) => (
                            <Card
                              key={documentKey}
                              size="small"
                              title={
                                <Space size={8} wrap>
                                  <span>{`条款 ${index + 1}：${documentItem.title || '报名文书'}`}</span>
                                  {readingSeconds > 0 ? (
                                    <Tag color={countdown > 0 ? 'orange' : 'green'}>
                                      {countdown > 0 ? `需阅读 ${countdown}s` : '已完成阅读'}
                                    </Tag>
                                  ) : (
                                    <Tag color="blue">可直接确认</Tag>
                                  )}
                                </Space>
                              }
                            >
                              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                                <div className="competition-registration-documents__content">
                                  <XMarkdown content={sanitizeMarkdownInput(documentItem.contentText || '')} openLinksInNewTab escapeRawHtml />
                                </div>
                                <Checkbox
                                  checked={accepted}
                                  disabled={countdown > 0}
                                  onChange={(event) => acceptRegistrationDocument(documentKey, event.target.checked)}
                                >
                                  {countdown > 0 ? `请阅读 ${countdown} 秒后确认` : '我已阅读并同意本条款'}
                                </Checkbox>
                              </Space>
                            </Card>
                          ))}
                          {!allRegistrationDocumentsAccepted ? (
                            <Alert
                              type="warning"
                              showIcon
                              message={`请先完成剩余 ${pendingRegistrationDocumentCount} 份协议确认后再继续。`}
                            />
                          ) : (
                            <Alert type="success" showIcon message="阅读文书条款已确认，可进入下一步。" />
                          )}
                        </Space>
                      ) : (
                        <Alert type="info" showIcon message="当前赛事未配置报名前展示文书，可直接进入下一步。" />
                      )}
                    </div>
                  ) : null}
                  {registrationScopeFields.map((field) => (
                    <Form.Item
                      key={field.itemKey}
                      name={['registrationExtraValues', field.itemKey]}
                      label={field.title}
                      rules={buildCollectedFieldRule(field)}
                    >
                      {renderRegistrationCollectedFieldInput(field)}
                    </Form.Item>
                  ))}
                </>
              ) : null}
              {step === 1 ? renderTeamForm() : null}
              {step === registrationWizardStep.projectEvidence ? (
                <>
                  <Typography.Title level={5}>项目与知识产权佐证</Typography.Title>
                  <Typography.Paragraph type="secondary">
                    请填写项目基本信息及已申请、已登记或已授权的知识产权，并标明知识产权分布区域。
                  </Typography.Paragraph>
                  <Form.Item name="newProjectTitle" label={projectTitleField.title} rules={buildCollectedFieldRule(projectTitleField)}>
                    <Input maxLength={128} placeholder={projectTitleField.placeholder || projectTitleField.title} />
                  </Form.Item>
                  <Form.Item
                    name="newProjectImageUrl"
                    hidden
                    rules={projectImageField ? buildCollectedFieldRule(projectImageField) : undefined}
                  >
                    <Input />
                  </Form.Item>
                  {projectImageField ? <Form.Item label={projectImageField.title} required={projectImageField.required}>
                    <Space>
                      <Avatar
                        shape="square"
                        size={64}
                        src={normalizeUploadUrl(newProjectImageUrl) || undefined}
                        icon={<EyeOutlined />}
                      />
                      <Upload
                        accept="image/*"
                        showUploadList={false}
                        disabled={projectAvatarUploading}
                        beforeUpload={async (file) => {
                          await uploadRegistrationProjectAvatar(file);
                          return Upload.LIST_IGNORE;
                        }}
                      >
                        <Button icon={<UploadOutlined />} loading={projectAvatarUploading}>上传</Button>
                      </Upload>
                      {newProjectImageUrl ? (
                        <Button
                          type="link"
                          onClick={() => {
                            form.setFieldValue('newProjectImageUrl', undefined);
                            persistRegistrationDraft({
                              ...collectRegistrationValues(),
                              newProjectImageUrl: undefined,
                            });
                          }}
                        >
                          移除
                        </Button>
                      ) : null}
                    </Space>
                  </Form.Item> : null}
                  {projectDescriptionField ? <Form.Item name="newProjectDescription" label={projectDescriptionField.title} rules={buildCollectedFieldRule(projectDescriptionField)}>
                    <Input.TextArea rows={3} maxLength={1000} placeholder={projectDescriptionField.placeholder || projectDescriptionField.title} />
                  </Form.Item> : null}
                  {projectCustomFields.length ? <Typography.Title level={5}>项目扩展信息</Typography.Title> : null}
                  {projectCustomFields.map((field) => (
                    <Form.Item
                      key={field.itemKey}
                      name={['newProjectExtraValues', field.itemKey]}
                      label={field.title}
                      rules={buildCollectedFieldRule(field)}
                    >
                      {renderRegistrationCollectedFieldInput(field)}
                    </Form.Item>
                  ))}
                  {intellectualPropertyFields.length ? <Typography.Title level={5}>知识产权信息</Typography.Title> : null}
                  {intellectualPropertyFields.map((field) => (
                    <Form.Item
                      key={field.itemKey}
                      name={['newProjectExtraValues', field.itemKey]}
                      label={field.title}
                      rules={buildCollectedFieldRule(field)}
                    >
                      {renderRegistrationCollectedFieldInput(field)}
                    </Form.Item>
                  ))}
                </>
              ) : null}
              {step === registrationWizardStep.preliminaryMaterials ? (
                stageFormLoading ? (
                  <Alert type="info" showIcon message="正在加载初赛材料表单..." />
                ) : fields.length ? (
                  fields.map((field) => (
                    <Form.Item
                      key={field.key}
                      name={["materials", field.key]}
                      label={field.label || field.key}
                      rules={[{ required: Boolean(field.required), message: `请填写${field.label || field.key}` }]}
                    >
                      {field.type === 'textarea' ? (
                        <Input.TextArea rows={4} maxLength={field.maxLength} />
                      ) : field.type === 'file' ? (
                        <MaterialFileUploadInput field={field} />
                      ) : (
                        <Input maxLength={field.maxLength} />
                      )}
                    </Form.Item>
                  ))
                ) : (
                  <Alert type="info" showIcon message="当前赛事未配置初赛材料表单，可继续进入信息确认。" />
                )
              ) : null}
              {step === registrationWizardStep.review ? (
                <Space direction="vertical" style={{ width: '100%' }} size={16}>
                  <Alert type="info" showIcon message="请核对以下全部报名信息。确认后将生成报名订单；生成支付订单后内容将不能修改。" />
                  <Card size="small" title="赛事与报名信息" extra={<Button type="link" onClick={() => setWizardStep(0)}>返回修改</Button>}>
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <Typography.Text><Typography.Text strong>赛事：</Typography.Text>{selectedCompetition?.title || '-'}</Typography.Text>
                      {registrationDocumentStates.map(({ item, documentKey }) => (
                        <Typography.Text key={documentKey}><Typography.Text strong>已确认文书：</Typography.Text>{item.title || '报名文书'}</Typography.Text>
                      ))}
                      {registrationScopeFields.map((field) => (
                        <Space key={field.itemKey} align="start">
                          <Typography.Text strong>{field.title}：</Typography.Text>
                          {renderCollectedFieldReviewValue(field, form.getFieldValue(['registrationExtraValues', field.itemKey]))}
                        </Space>
                      ))}
                    </Space>
                  </Card>
                  <Card size="small" title="团队与学生" extra={<Button type="link" onClick={() => setWizardStep(1)}>返回修改</Button>}>
                    <Space direction="vertical" style={{ width: '100%' }} size={12}>
                      <Typography.Text><Typography.Text strong>{teamNameField.title}：</Typography.Text>{form.getFieldValue('newTeamName') || '-'}</Typography.Text>
                      {teamFieldSplit.overrides.get('teamType') ? <Space align="start"><Typography.Text strong>{teamFieldSplit.overrides.get('teamType')?.title}：</Typography.Text>{renderCollectedFieldReviewValue(teamFieldSplit.overrides.get('teamType')!, form.getFieldValue(['newTeam', 'teamType']))}</Space> : null}
                      {teamAvatarField && form.getFieldValue(['newTeam', 'avatarUrl']) ? <Image width={72} height={72} src={normalizeUploadUrl(form.getFieldValue(['newTeam', 'avatarUrl']))} alt={teamAvatarField.title} /> : null}
                      {teamDescriptionField ? <Typography.Text><Typography.Text strong>{teamDescriptionField.title}：</Typography.Text>{form.getFieldValue(['newTeam', 'description']) || '-'}</Typography.Text> : null}
                      {teamFieldSplit.customFields.map((field) => (
                        <Space key={field.itemKey} align="start"><Typography.Text strong>{field.title}：</Typography.Text>{renderCollectedFieldReviewValue(field, form.getFieldValue(['newTeam', 'extraValues', field.itemKey]))}</Space>
                      ))}
                      {registrationMembers.map((member, index) => (
                        <Card key={`review-member-${index}`} size="small" title={`学生 ${index + 1}`}>
                          <Space direction="vertical">
                            {effectiveMemberRegistrationFields.map((field) => (
                              <Space key={field.itemKey} align="start"><Typography.Text strong>{field.title}：</Typography.Text>{renderCollectedFieldReviewValue(field, getMemberCollectedFieldValue(member, field))}</Space>
                            ))}
                          </Space>
                        </Card>
                      ))}
                    </Space>
                  </Card>
                  <Card size="small" title="项目与知识产权佐证" extra={<Button type="link" onClick={() => setWizardStep(registrationWizardStep.projectEvidence)}>返回修改</Button>}>
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <Typography.Text><Typography.Text strong>{projectTitleField.title}：</Typography.Text>{form.getFieldValue('newProjectTitle') || '-'}</Typography.Text>
                      {projectImageField && form.getFieldValue('newProjectImageUrl') ? <Image width={72} height={72} src={normalizeUploadUrl(form.getFieldValue('newProjectImageUrl'))} alt={projectImageField.title} /> : null}
                      {projectDescriptionField ? <Typography.Text><Typography.Text strong>{projectDescriptionField.title}：</Typography.Text>{form.getFieldValue('newProjectDescription') || '-'}</Typography.Text> : null}
                      {[...projectCustomFields, ...intellectualPropertyFields].map((field) => (
                        <Space key={field.itemKey} align="start"><Typography.Text strong>{field.title}：</Typography.Text>{renderCollectedFieldReviewValue(field, form.getFieldValue(['newProjectExtraValues', field.itemKey]))}</Space>
                      ))}
                    </Space>
                  </Card>
                  <Card size="small" title="初赛材料" extra={<Button type="link" onClick={() => setWizardStep(registrationWizardStep.preliminaryMaterials)}>返回修改</Button>}>
                    {fields.length ? <Space direction="vertical">
                      {fields.map((field) => (
                        <Typography.Text key={field.key}>
                          <Typography.Text strong>{field.label || field.key}：</Typography.Text>
                          {field.type === 'file' ? (() => {
                            const fileId = Number(form.getFieldValue(['materials', field.key]));
                            const fileRecord = materialFileRecords[fileId];
                            return fileRecord ? <Space size={4}>
                              <span>{fileRecord.originalFileName}</span>
                              <Button type="link" size="small" href={normalizeUploadUrl(fileRecord.previewUrl || fileRecord.publicUrl)} target="_blank" rel="noopener noreferrer">查看</Button>
                            </Space> : `附件文件 #${fileId || '-'}`;
                          })() : normalizeDisplayText(form.getFieldValue(['materials', field.key])) || '-'}
                        </Typography.Text>
                      ))}
                    </Space> : <Typography.Text type="secondary">无需提交初赛材料</Typography.Text>}
                  </Card>
                  <Card size="small" title="应付金额">
                    <Typography.Title level={3} style={{ margin: 0 }}>{formatRegistrationAmount(previewPayableAmount, selectedCompetition?.currency)}</Typography.Title>
                    <Typography.Text type="secondary">{selectedCompetition?.feeMode === 'MEMBER' ? `按 ${registrationMembers.length} 位学生计费` : '按团队计费'}</Typography.Text>
                  </Card>
                </Space>
              ) : null}
              {step === 5 ? (
                <Space direction="vertical" style={{ width: '100%' }} size={16}>
                  <Alert
                    type={paymentStatus === 'CONFIRMED' || paymentStatus === 'PAID' ? 'success' : 'info'}
                    showIcon
                    message={`报名编号：${registrationRecord?.registrationNo || registrationId || '-'}，应付金额：${formatRegistrationAmount(registrationRecord?.payableAmountMinor ?? previewPayableAmount, registrationRecord?.currency || selectedCompetition?.currency)}`}
                    description="支付方式必须单选。选定后生成支付链接，不会自动跳转。"
                  />
                  {paymentStatus !== 'CONFIRMED' && paymentStatus !== 'PAID' ? (
                    paymentOptions.length ? (
                      <Radio.Group value={selectedPaymentProvider} onChange={(event) => setSelectedPaymentProvider(event.target.value)}>
                        <Space direction="vertical">
                          {paymentOptions.map((option) => (
                            <Radio key={option.providerCode} value={option.providerCode}>
                              {option.displayName}
                              <Typography.Text type="secondary"> {option.paymentScene}</Typography.Text>
                            </Radio>
                          ))}
                        </Space>
                      </Radio.Group>
                    ) : <Alert type="warning" showIcon message="当前设备暂无可用支付方式，请联系管理员。" />
                  ) : null}
                </Space>
              ) : null}
            </div>
          </Form>
          <div className="competition-create-actions">
            {registrationDraftSavedAt ? (
              <Typography.Text className="competition-create-draft-status" type="secondary">
                草稿已自动保存
              </Typography.Text>
            ) : null}
            {step > 0 ? <Button onClick={() => setWizardStep(step - 1)}>上一步</Button> : null}
            {step < 5 ? (
              <Button type="primary" loading={loading} disabled={nextButtonDisabled || !canAdvanceRegistration} onClick={() => void goNext()}>
                {nextButtonText}
              </Button>
            ) : (
              <Button type="primary" loading={loading} disabled={!canPayRegistration || !selectedPaymentProvider} onClick={() => void pay()}>
                立即支付
              </Button>
            )}
          </div>
          <Modal
            title="前往付款"
            open={paymentModalOpen}
            onCancel={() => setPaymentModalOpen(false)}
            footer={null}
            destroyOnHidden
          >
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Typography.Text>支付订单号：{paymentOrder?.orderNo || '生成中'}</Typography.Text>
              <Typography.Text>支付金额：{formatRegistrationAmount(paymentOrder?.amountMinor ?? registrationRecord?.payableAmountMinor, paymentOrder?.currency || registrationRecord?.currency)}</Typography.Text>
              <Typography.Text>支付渠道：{paymentOptions.find((item) => item.providerCode === selectedPaymentProvider)?.displayName || selectedPaymentProvider || '-'}</Typography.Text>
              {paymentOrder?.paymentUrl ? (
                <Button type="primary" block href={paymentOrder.paymentUrl} target="_blank" rel="noopener noreferrer">前往支付</Button>
              ) : <Button type="primary" block loading disabled>支付链接生成中</Button>}
              {paymentOrder?.paymentUrl ? <Button block href={paymentOrder.paymentUrl} target="_blank" rel="noopener noreferrer">重新打开支付</Button> : null}
              <Button block onClick={() => void refreshPaymentResult()}>我已完成支付，检查结果</Button>
            </Space>
          </Modal>
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

type CompetitionSettingsConfigModuleKey = 'documents' | 'fields' | 'payments' | 'files' | 'timeline';
type CompetitionSettingsModuleKey = CompetitionSettingsSectionKey;

type CompetitionSettingsModuleConfig = {
  key: CompetitionSettingsConfigModuleKey;
  labelId: string;
  defaultLabel: string;
  descriptionId: string;
  defaultDescription: string;
  itemTypes: CompetitionConfigItemType[];
};

type ConfigItemMetadata = {
  documentKind?: 'AGREEMENT' | 'CONSENT';
  readingSeconds?: number;
  fieldScope?: CompetitionConfigItemType;
  fieldType?: string;
  placeholder?: string;
  description?: string;
  groupLabel?: string;
  standardField?: boolean;
  validationRule?: string;
  options?: string;
  weight?: number;
  fileFormat?: string;
  maxSizeMb?: number;
  storageKey?: string;
  stageCode?: string;
  stageName?: string;
  materialType?: string;
  timelineKind?: string;
  startAt?: string;
  endAt?: string;
  teamMinMembers?: number;
  teamMaxMembers?: number;
};

type EditableCompetitionConfigItem = CompetitionConfigItem & {
  metadata?: ConfigItemMetadata;
};

type StorageSpaceOption = {
  label: string;
  value: string;
};

type PaymentProviderOption = {
  label: string;
  value: string;
  disabled?: boolean;
};

const localizeLegacyConfigItemTitle = (item: CompetitionConfigItem): CompetitionConfigItem => {
  const legacyTitles: Record<string, { english: string; chinese: string }> = {
    'AGREEMENT:commitment': { english: 'Commitment', chinese: '赛事承诺书' },
    'CONSENT:informed-consent': { english: 'Informed consent', chinese: '知情同意书' },
    'REGISTRATION_FIELD:contact-name': { english: 'Contact name', chinese: '联系人姓名' },
    'REQUIRED_FILE:work-file': { english: 'Work file', chinese: '作品文件' },
  };
  const localizedTitle = legacyTitles[`${item.itemType}:${item.itemKey}`];
  return localizedTitle && item.title.trim().toLowerCase() === localizedTitle.english.toLowerCase()
    ? { ...item, title: localizedTitle.chinese }
    : item;
};

const localizeLegacyCompetitionSettings = (settings: CompetitionSettingsRecord): CompetitionSettingsRecord => ({
  ...settings,
  competition: {
    ...settings.competition,
    title: settings.competition.title === 'Untitled competition' ? '未命名赛事' : settings.competition.title,
  },
  documents: settings.documents.map(localizeLegacyConfigItemTitle),
  fields: settings.fields.map(localizeLegacyConfigItemTitle),
  files: settings.files.map(localizeLegacyConfigItemTitle),
  stageMaterials: settings.stageMaterials.map(localizeLegacyConfigItemTitle),
  payments: settings.payments.map(localizeLegacyConfigItemTitle),
  timeline: settings.timeline.map(localizeLegacyConfigItemTitle),
});

const competitionSettingsModules: CompetitionSettingsModuleConfig[] = [
  {
    key: 'documents',
    labelId: 'page.competition.settings.module.documents',
    defaultLabel: 'Documents',
    descriptionId: 'page.competition.settings.module.documents.description',
    defaultDescription: 'Commitment, informed consent and other required reading documents.',
    itemTypes: ['AGREEMENT', 'CONSENT'],
  },
  {
    key: 'fields',
    labelId: 'page.competition.settings.module.fields',
    defaultLabel: 'Team Management',
    descriptionId: 'page.competition.settings.module.fields.description',
    defaultDescription: 'Configure team size limits and registration fields.',
    itemTypes: ['TEAM_SETTINGS', 'REGISTRATION_FIELD', 'TEAM_FIELD', 'MEMBER_FIELD', 'PROJECT_FIELD'],
  },
  {
    key: 'payments',
    labelId: 'page.competition.settings.module.payments',
    defaultLabel: 'Payment Methods',
    descriptionId: 'page.competition.settings.module.payments.description',
    defaultDescription: 'Choose the payment providers available for this competition.',
    itemTypes: ['PAYMENT_SETTINGS'],
  },
  {
    key: 'files',
    labelId: 'page.competition.settings.module.files',
    defaultLabel: 'Required Files',
    descriptionId: 'page.competition.settings.module.files.description',
    defaultDescription: 'Files participants must upload, including works, proof and authorization files.',
    itemTypes: ['REQUIRED_FILE', 'STAGE_MATERIAL'],
  },
  {
    key: 'timeline',
    labelId: 'page.competition.settings.module.timeline',
    defaultLabel: 'Timeline',
    descriptionId: 'page.competition.settings.module.timeline.description',
    defaultDescription: 'Registration, competition, material submission and review windows.',
    itemTypes: ['TIMELINE'],
  },
];

const getCompetitionSettingsModuleLabel = (module: CompetitionSettingsModuleConfig) =>
  formatMessage({ id: module.labelId, defaultMessage: module.defaultLabel });

const getCompetitionSettingsModuleDescription = (module: CompetitionSettingsModuleConfig) =>
  formatMessage({ id: module.descriptionId, defaultMessage: module.defaultDescription });

const competitionSettingsMenuItems = [
  { key: 'basic' as const, label: '基础信息' },
  { key: 'registration' as const, label: '报名设置' },
  { key: 'stages' as const, label: '赛程与材料' },
  { key: 'payments' as const, label: '费用设置' },
];

const parseConfigItemMetadata = (contentJson?: string | null): ConfigItemMetadata => {
  if (!contentJson) {
    return {};
  }
  try {
    const parsed = JSON.parse(contentJson);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
};

const serializeConfigItemMetadata = (metadata?: ConfigItemMetadata) => {
  const cleaned = Object.fromEntries(
    Object.entries(metadata || {}).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
  return JSON.stringify(cleaned, null, 2);
};

const TEAM_SETTINGS_ITEM_KEY = 'team-size-limits';
const DEFAULT_TEAM_MIN_MEMBERS = 1;
const DEFAULT_TEAM_MAX_MEMBERS = 20;

const normalizeTeamMemberLimit = (value: unknown, fallback: number) => {
  const numericValue = Number(value);
  return Number.isInteger(numericValue) && numericValue > 0 ? numericValue : fallback;
};

const getTeamMemberLimits = (items: CompetitionConfigItem[]) => {
  const settingsItem = items.find((item) => item.itemType === 'TEAM_SETTINGS' && item.itemKey === TEAM_SETTINGS_ITEM_KEY);
  const metadata = parseConfigItemMetadata(settingsItem?.contentJson);
  const minMembers = normalizeTeamMemberLimit(metadata.teamMinMembers, DEFAULT_TEAM_MIN_MEMBERS);
  const maxMembers = normalizeTeamMemberLimit(metadata.teamMaxMembers, DEFAULT_TEAM_MAX_MEMBERS);
  return {
    minMembers: Math.min(minMembers, maxMembers),
    maxMembers: Math.max(minMembers, maxMembers),
  };
};

const buildTeamSettingsConfigItem = (minMembers: number, maxMembers: number): CompetitionConfigItem => ({
  itemType: 'TEAM_SETTINGS',
  itemKey: TEAM_SETTINGS_ITEM_KEY,
  title: '团队人数限制',
  contentJson: serializeConfigItemMetadata({ teamMinMembers: minMembers, teamMaxMembers: maxMembers }),
  sortOrder: 0,
  requiredFlag: false,
  enabled: true,
});

const normalizeReadingSeconds = (value?: number | string | null) => {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? Math.max(0, Math.floor(numericValue)) : 0;
};

const getConfigItemReadingSeconds = (item: CompetitionConfigItem) =>
  normalizeReadingSeconds(parseConfigItemMetadata(item.contentJson).readingSeconds);

const getRegistrationDocumentKey = (item: CompetitionConfigItem, index: number) =>
  String(item.id || item.itemKey || `${item.itemType}-${index}`);

const toEditableConfigItems = (items: CompetitionConfigItem[]): EditableCompetitionConfigItem[] =>
  items.map((item) => {
    const fieldScope = ['REGISTRATION_FIELD', 'TEAM_FIELD', 'MEMBER_FIELD', 'PROJECT_FIELD'].includes(item.itemType)
      ? resolveRegistrationFieldScope(item)
      : undefined;
    const metadata = normalizeIndependentMemberRoleMetadata(
      fieldScope,
      item.itemKey,
      parseConfigItemMetadata(item.contentJson),
    );
    return {
      ...item,
      metadata: {
        ...metadata,
        stageCode: item.itemType === 'STAGE_MATERIAL'
          ? normalizeFileStageCode(metadata.stageCode || metadata.stageName)
          : item.itemType === 'REQUIRED_FILE'
            ? normalizeFileStageCode(metadata.stageCode)
            : undefined,
        fileFormat: item.itemType === 'REQUIRED_FILE' || item.itemType === 'STAGE_MATERIAL'
          ? normalizeFileFormat(metadata.fileFormat)
          : undefined,
        fieldScope,
        documentKind: item.itemType === 'AGREEMENT' || item.itemType === 'CONSENT' ? item.itemType : undefined,
        readingSeconds: item.itemType === 'AGREEMENT' || item.itemType === 'CONSENT'
          ? normalizeReadingSeconds(metadata.readingSeconds)
          : undefined,
      },
    };
  });

const buildAutomaticConfigItemKey = (itemType: CompetitionConfigItemType, title: string | undefined, index: number) => {
  const titleKey = normalizeConfigKey((title || '').trim().replace(/\s+/g, '-').toLowerCase());
  return titleKey || `${itemType.toLowerCase()}-${index + 1}`;
};

const normalizeFileStageCode = (value?: string): NonNullable<ConfigItemMetadata['stageCode']> =>
  value === 'PRELIMINARY' || value === '初赛'
    ? 'PRELIMINARY'
    : value === 'FINAL' || value === '决赛'
      ? 'FINAL'
      : value?.trim() || 'GENERAL';

const getFileConfigItemStageCode = (item: CompetitionConfigItem) => {
  const metadata = parseConfigItemMetadata(item.contentJson);
  return normalizeFileStageCode(metadata.stageCode || metadata.stageName);
};

const resolveFileStageName = (stageCode: NonNullable<ConfigItemMetadata['stageCode']>) =>
  fileStageOptions.find((option) => option.value === stageCode)?.label || stageCode;

const normalizeFileFormat = (value?: string) => {
  if (value === 'PDF' || value === 'WORD') {
    return 'DOCUMENT';
  }
  return ['ANY', 'DOCUMENT', 'IMAGE', 'ARCHIVE'].includes(value || '') ? value : 'ANY';
};

const toConfigItems = (items: EditableCompetitionConfigItem[]): CompetitionConfigItem[] =>
  items.map(({ metadata, ...item }, index) => {
    const fileStageCode = item.itemType === 'REQUIRED_FILE' || item.itemType === 'STAGE_MATERIAL'
      ? normalizeFileStageCode(metadata?.stageCode)
      : undefined;
    const itemType = fileStageCode
      ? (fileStageCode === 'GENERAL' ? 'REQUIRED_FILE' : 'STAGE_MATERIAL')
      : metadata?.fieldScope || metadata?.documentKind || item.itemType;
    const itemKey = normalizeConfigKey(item.itemKey || '') || buildAutomaticConfigItemKey(itemType, item.title, index);
    const isDocumentItem = itemType === 'AGREEMENT' || itemType === 'CONSENT';
    const documentMetadata = isDocumentItem
      ? { ...metadata, readingSeconds: normalizeReadingSeconds(metadata?.readingSeconds) }
      : metadata;
    const nextMetadata = fileStageCode
      ? {
          ...documentMetadata,
          stageCode: fileStageCode,
          stageName: resolveFileStageName(fileStageCode),
          fileFormat: normalizeFileFormat(documentMetadata?.fileFormat),
          materialType: fileStageCode === 'GENERAL' ? undefined : 'FILE',
        }
      : documentMetadata;
    return {
      ...item,
      itemType,
      itemKey,
      contentJson: serializeConfigItemMetadata({ ...nextMetadata, fieldScope: undefined, documentKind: undefined }),
      sortOrder: item.sortOrder ?? 0,
      requiredFlag: isDocumentItem ? true : Boolean(item.requiredFlag),
      enabled: item.enabled ?? true,
    };
  });

const normalizeConfigKey = (value: string) => value.trim().replace(/[^A-Za-z0-9_-]/g, '');

const fieldTypeOptions = [
  { label: '单行文本', value: 'TEXT' },
  { label: '多行文本', value: 'TEXTAREA' },
  { label: '图片上传', value: 'IMAGE' },
  { label: '数字', value: 'NUMBER' },
  { label: '日期', value: 'DATE' },
  { label: '下拉选择', value: 'SELECT' },
  { label: '多选', value: 'MULTI_SELECT' },
  { label: '手机号', value: 'MOBILE' },
  { label: '邮箱', value: 'EMAIL' },
];

const validationRuleOptions = [
  { label: '不限制', value: 'NONE' },
  { label: '中国手机号', value: 'CHINA_MOBILE' },
  { label: '邮箱地址', value: 'EMAIL' },
  { label: '身份证号', value: 'ID_CARD' },
];

const fileFormatOptions = [
  { label: '不限格式', value: 'ANY' },
  { label: '文档类', value: 'DOCUMENT' },
  { label: '图片类', value: 'IMAGE' },
  { label: '压缩包类', value: 'ARCHIVE' },
];

const fileStageOptions = [
  { label: '报名后初赛提交', value: 'GENERAL' },
  { label: '初赛', value: 'PRELIMINARY' },
  { label: '决赛', value: 'FINAL' },
];

const buildStorageSpaceOptions = (records: FileStorageSpaceRecord[]): StorageSpaceOption[] =>
  records
    .filter((item) => item.status !== 'DISABLED')
    .map((item) => ({
      value: item.storageKey,
      label: [item.title || item.storageKey, item.storageKey, item.bucketName].filter(Boolean).join(' / '),
    }));

const loadStorageSpaceOptions = async () => {
  const result = await request<PagedResult<FileStorageSpaceRecord>>('/v1/files/storage-spaces', {
    method: 'GET',
    params: {
      pageNo: 1,
      pageSize: 1000,
    },
    ...API_OPTS.SILENT,
  });
  return buildStorageSpaceOptions(result.records || []);
};

type RegistrationFieldScope = Extract<
  CompetitionConfigItemType,
  'REGISTRATION_FIELD' | 'TEAM_FIELD' | 'MEMBER_FIELD' | 'PROJECT_FIELD'
>;

const INTELLECTUAL_PROPERTY_GROUP_LABEL = '知识产权信息';

const protectedCollectionFieldKeys: Partial<Record<RegistrationFieldScope, Set<string>>> = {
  TEAM_FIELD: new Set(['teamName']),
  MEMBER_FIELD: new Set(['memberName']),
  PROJECT_FIELD: new Set(['title']),
};

const fieldScopeOptions: Array<{ label: string; value: RegistrationFieldScope }> = [
  { label: '报名信息', value: 'REGISTRATION_FIELD' },
  { label: '团队信息', value: 'TEAM_FIELD' },
  { label: '成员信息', value: 'MEMBER_FIELD' },
  { label: '项目信息', value: 'PROJECT_FIELD' },
];

const loadConfiguredPaymentProviderOptions = async (): Promise<PaymentProviderOption[]> => {
  const providers = await request<PaymentProviderSettings[]>('/v1/payment/providers', {
    method: 'GET',
    ...API_OPTS.SILENT,
  });
  return providers
    .filter((provider) => provider.persisted && provider.configured && provider.enabled)
    .sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0))
    .map((provider) => ({
      value: provider.providerCode,
      label: provider.displayName || provider.providerName || provider.providerCode,
    }));
};

const timelineKindOptions = [
  { label: '报名时间', value: 'REGISTRATION' },
  { label: '比赛时间', value: 'COMPETITION' },
  { label: '材料提交时间', value: 'MATERIAL_SUBMISSION' },
  { label: '评审时间', value: 'REVIEW' },
  { label: '公示时间', value: 'PUBLICITY' },
];

const emptyConfigItem = (itemType: CompetitionConfigItemType, sortOrder: number): CompetitionConfigItem => ({
  itemType,
  itemKey: `${itemType.toLowerCase()}-${Date.now()}`,
  title: '',
  contentJson: serializeConfigItemMetadata(
    ['REGISTRATION_FIELD', 'TEAM_FIELD', 'MEMBER_FIELD', 'PROJECT_FIELD'].includes(itemType)
      ? { fieldScope: itemType, fieldType: 'TEXT', validationRule: 'NONE' }
      : itemType === 'REQUIRED_FILE'
        ? { fileFormat: 'ANY', maxSizeMb: 20, stageCode: 'GENERAL', stageName: '通用' }
        : itemType === 'STAGE_MATERIAL'
          ? { materialType: 'FILE', stageCode: 'PRELIMINARY', stageName: '初赛', fileFormat: 'ANY', maxSizeMb: 20 }
          : itemType === 'TIMELINE'
            ? { timelineKind: 'REGISTRATION' }
            : itemType === 'AGREEMENT' || itemType === 'CONSENT'
              ? { documentKind: itemType, readingSeconds: 0 }
              : {},
  ),
  contentText: '',
  sortOrder,
  requiredFlag: false,
  enabled: true,
});

const getModuleItems = (settings: CompetitionSettingsRecord | undefined, key: CompetitionSettingsConfigModuleKey) => {
  if (!settings) {
    return [];
  }
  if (key === 'documents') {
    return settings.documents;
  }
  if (key === 'fields') {
    return settings.fields;
  }
  if (key === 'files') {
    return [...settings.files, ...settings.stageMaterials].sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0));
  }
  if (key === 'payments') {
    return settings.payments || [];
  }
  return settings.timeline;
};

const splitFileConfigItemsByModule = (items: CompetitionConfigItem[]) => ({
  files: items.filter((item) => item.itemType === 'REQUIRED_FILE'),
  stageMaterials: items.filter((item) => item.itemType === 'STAGE_MATERIAL'),
});

const AUTO_SAVE_DELAY_MS = 800;

const isFormValidationError = (error: unknown) =>
  Boolean(error && typeof error === 'object' && 'errorFields' in error);

type CompetitionSettingsPanelHandle = {
  flushPendingSave: () => Promise<boolean>;
  saveNow: () => Promise<boolean>;
};

const useDebouncedAutoSave = (save: () => Promise<boolean>) => {
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const savingRef = useRef(false);
  const pendingRef = useRef(false);
  const activeSaveRef = useRef<Promise<boolean>>(Promise.resolve(true));

  const runSave = useCallback(async (): Promise<boolean> => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
    if (savingRef.current) {
      return activeSaveRef.current;
    }
    if (!pendingRef.current) {
      return activeSaveRef.current;
    }
    savingRef.current = true;
    pendingRef.current = false;
    const task: Promise<boolean> = (async (): Promise<boolean> => {
      let saved = false;
      try {
        saved = await save();
      } catch (error) {
        if (!isFormValidationError(error)) {
          throw error;
        }
      } finally {
        savingRef.current = false;
      }
      if (pendingRef.current) {
        saved = (await runSave()) && saved;
      }
      return saved;
    })();
    activeSaveRef.current = task;
    return task;
  }, [save]);

  const scheduleSave = useCallback(() => {
    pendingRef.current = true;
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }
    timerRef.current = setTimeout(() => {
      void runSave();
    }, AUTO_SAVE_DELAY_MS);
  }, [runSave]);

  const flushPendingSave = useCallback(async () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
    if (!pendingRef.current && !savingRef.current) {
      return true;
    }
    if (pendingRef.current) {
      return runSave();
    }
    return activeSaveRef.current;
  }, [runSave]);

  const saveNow = useCallback(async () => {
    pendingRef.current = true;
    return runSave();
  }, [runSave]);

  useEffect(() => () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }
  }, []);

  return { scheduleSave, flushPendingSave, saveNow };
};

const renderConfigItemFields = (
  module: CompetitionSettingsModuleConfig,
  fieldName: number,
  storageSpaceOptions: StorageSpaceOption[],
) => {
  if (module.key === 'documents') {
    return (
      <>
        <Form.Item name={[fieldName, 'metadata', 'documentKind']} hidden>
          <Input />
        </Form.Item>
        <Form.Item name={[fieldName, 'itemKey']} hidden normalize={normalizeConfigKey}>
          <Input />
        </Form.Item>
        <div className="competition-config-grid competition-config-grid--document">
          <Form.Item name={[fieldName, 'title']} label="文书标题" rules={[{ required: true, message: '请输入文书标题' }]}>
            <Input placeholder="例如 参赛承诺书、知情同意书" maxLength={64} />
          </Form.Item>
          <Form.Item name={[fieldName, 'metadata', 'readingSeconds']} label="阅读时间（秒）">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </div>
        <Form.Item className="competition-config-document-content" name={[fieldName, 'contentText']} label="文书内容">
          <AgreementMarkdownEditor placeholder="请输入承诺书、知情同意书等内容，支持 Markdown" />
        </Form.Item>
      </>
    );
  }

  if (module.key === 'fields') {
    return (
      <>
        <div className="competition-config-grid">
          <Form.Item name={[fieldName, 'metadata', 'fieldScope']} label="适用范围" initialValue="REGISTRATION_FIELD">
            <Select options={fieldScopeOptions} />
          </Form.Item>
          <Form.Item name={[fieldName, 'metadata', 'fieldType']} label="字段类型" rules={[{ required: true, message: '请选择字段类型' }]}>
            <Select options={fieldTypeOptions} />
          </Form.Item>
          <Form.Item name={[fieldName, 'itemKey']} label="字段标识" normalize={normalizeConfigKey} rules={[{ required: true, message: '请输入字段标识' }]}>
            <Input placeholder="例如 mobile、school、projectName" maxLength={64} />
          </Form.Item>
          <Form.Item name={[fieldName, 'title']} label="字段名称" rules={[{ required: true, message: '请输入字段名称' }]}>
            <Input placeholder="例如 手机号、学校、项目名称" maxLength={64} />
          </Form.Item>
          <Form.Item name={[fieldName, 'metadata', 'placeholder']} label="占位提示">
            <Input placeholder="例如 请输入 11 位手机号" maxLength={120} />
          </Form.Item>
          <Form.Item name={[fieldName, 'metadata', 'groupLabel']} label="字段分组">
            <Input placeholder="例如 联系方式、教育信息" maxLength={64} />
          </Form.Item>
          <Form.Item name={[fieldName, 'metadata', 'validationRule']} label="校验规则">
            <Select options={validationRuleOptions} />
          </Form.Item>
          <Form.Item name={[fieldName, 'sortOrder']} label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </div>
        <Form.Item name={[fieldName, 'metadata', 'description']} label="字段说明">
          <Input.TextArea rows={2} placeholder="说明该字段在报名或参赛资料中的用途" maxLength={200} />
        </Form.Item>
        <Form.Item name={[fieldName, 'metadata', 'options']} label="选项内容">
          <Input.TextArea rows={2} placeholder="下拉选择时填写，每行一个选项" maxLength={500} />
        </Form.Item>
      </>
    );
  }

  if (module.key === 'payments') {
    return (
      <>
        <Form.Item name={[fieldName, 'itemKey']} hidden rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name={[fieldName, 'title']} hidden rules={[{ required: true }]}>
          <Input />
        </Form.Item>
      </>
    );
  }

  if (module.key === 'files') {
    return (
      <div className="competition-config-grid">
        <Form.Item name={[fieldName, 'metadata', 'stageCode']} hidden initialValue="GENERAL">
          <Input />
        </Form.Item>
        <Form.Item name={[fieldName, 'itemKey']} hidden normalize={normalizeConfigKey}>
          <Input />
        </Form.Item>
        <Form.Item name={[fieldName, 'title']} label="文件名称" rules={[{ required: true, message: '请输入文件名称' }]}>
          <Input placeholder="例如 参赛作品、授权证明" maxLength={64} />
        </Form.Item>
        <Form.Item name={[fieldName, 'metadata', 'fileFormat']} label="文件格式">
          <Select options={fileFormatOptions} />
        </Form.Item>
        <Form.Item name={[fieldName, 'metadata', 'storageKey']} label="关联存储空间">
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="默认存储空间"
            options={storageSpaceOptions}
          />
        </Form.Item>
        <Form.Item name={[fieldName, 'metadata', 'maxSizeMb']} label="大小上限 MB">
          <InputNumber min={1} max={1024} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item className="competition-config-grid__full" name={[fieldName, 'metadata', 'description']} label="上传说明">
          <Input.TextArea rows={2} placeholder="说明文件要求、命名规则或盖章要求" maxLength={200} />
        </Form.Item>
      </div>
    );
  }

  return (
    <div className="competition-config-grid">
      <Form.Item name={[fieldName, 'metadata', 'timelineKind']} label="时间类型" rules={[{ required: true, message: '请选择时间类型' }]}>
        <Select options={timelineKindOptions} />
      </Form.Item>
      <Form.Item name={[fieldName, 'title']} label="时间名称" rules={[{ required: true, message: '请输入时间名称' }]}>
        <Input placeholder="例如 报名时间、初赛评审" maxLength={64} />
      </Form.Item>
      <Form.Item name={[fieldName, 'itemKey']} label="时间标识" normalize={normalizeConfigKey} rules={[{ required: true, message: '请输入时间标识' }]}>
        <Input placeholder="例如 registration、review_preliminary" maxLength={64} />
      </Form.Item>
      <Form.Item name={[fieldName, 'metadata', 'startAt']} label="开始时间">
        <Input placeholder="例如 2026-07-01 09:00" maxLength={32} />
      </Form.Item>
      <Form.Item name={[fieldName, 'metadata', 'endAt']} label="结束时间">
        <Input placeholder="例如 2026-07-31 18:00" maxLength={32} />
      </Form.Item>
      <Form.Item name={[fieldName, 'sortOrder']} label="排序">
        <InputNumber min={0} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item className="competition-config-grid__full" name={[fieldName, 'metadata', 'description']} label="时间说明">
        <Input.TextArea rows={2} placeholder="补充说明该时间窗口覆盖的事项" maxLength={200} />
      </Form.Item>
    </div>
  );
};

const renderFieldSettingsTable = (
  fields: Array<{ key: number; name: number }>,
  add: (defaultValue?: EditableCompetitionConfigItem) => void,
  remove: (index: number | number[]) => void,
  scope: RegistrationFieldScope,
  scheduleSave: () => void,
  reorderField: (fields: Array<{ key: number; name: number }>, fromIndex: number, toIndex: number) => void,
  openOptionsEditor: (fieldName: number, fieldTitle?: string, options?: string) => void,
  fieldGroupLabel?: string,
) => {
  return (
    <Space className="competition-config-list" direction="vertical" size={16}>
      <div className="competition-field-table">
        <div className="competition-field-table__head">
          <span>字段名称</span>
          <span>字段标识</span>
          <span>类型</span>
          <span>占位提示</span>
          <span>下拉选项</span>
          <span>必填</span>
          <span>排序</span>
          <span>启用</span>
          <span>操作</span>
        </div>
        {fields.map((field, index) => (
          <div className="competition-field-table__row" key={field.key}>
            <Form.Item name={[field.name, 'title']} rules={[{ required: true, message: '请输入字段名称' }]}>
              <Input placeholder="字段名称" maxLength={64} />
            </Form.Item>
            <Form.Item name={[field.name, 'itemKey']} normalize={normalizeConfigKey} rules={[{ required: true, message: '请输入字段标识' }]}>
              <Input placeholder="字段标识" maxLength={64} />
            </Form.Item>
            <Form.Item name={[field.name, 'metadata', 'fieldType']} rules={[{ required: true, message: '请选择字段类型' }]}>
              <Select options={fieldTypeOptions} />
            </Form.Item>
            <Form.Item name={[field.name, 'metadata', 'placeholder']}>
              <Input placeholder="占位提示" maxLength={120} />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(previous, current) => (
              previous?.items?.[field.name]?.metadata?.fieldType !== current?.items?.[field.name]?.metadata?.fieldType
            )}>
              {({ getFieldValue }) => ['SELECT', 'MULTI_SELECT'].includes(
                getFieldValue(['items', field.name, 'metadata', 'fieldType']),
              ) ? (
                <>
                  <Form.Item name={[field.name, 'metadata', 'options']} hidden>
                    <Input />
                  </Form.Item>
                  <Button
                    icon={<SettingOutlined />}
                    onClick={() => openOptionsEditor(
                      field.name,
                      getFieldValue(['items', field.name, 'title']),
                      getFieldValue(['items', field.name, 'metadata', 'options']),
                    )}
                  >
                    {String(getFieldValue(['items', field.name, 'metadata', 'options']) || '')
                      .split('\n').filter((option) => option.trim()).length > 0
                      ? `已设置 ${String(getFieldValue(['items', field.name, 'metadata', 'options']) || '')
                          .split('\n').filter((option) => option.trim()).length} 项`
                      : '设置选项'}
                  </Button>
                </>
              ) : <Typography.Text type="secondary">—</Typography.Text>}
            </Form.Item>
            <Form.Item name={[field.name, 'requiredFlag']} valuePropName="checked">
              <Switch />
            </Form.Item>
            <div className="competition-field-table__sort-cell">
              <Form.Item name={[field.name, 'sortOrder']} hidden>
                <InputNumber />
              </Form.Item>
              <Space className="competition-field-table__sort-actions" size={0}>
                <Button
                  aria-label={`上移字段 ${index + 1}`}
                  disabled={index === 0}
                  icon={<ArrowUpOutlined />}
                  size="small"
                  title="上移"
                  type="text"
                  onClick={() => reorderField(fields, index, index - 1)}
                />
                <Button
                  aria-label={`下移字段 ${index + 1}`}
                  disabled={index === fields.length - 1}
                  icon={<ArrowDownOutlined />}
                  size="small"
                  title="下移"
                  type="text"
                  onClick={() => reorderField(fields, index, index + 1)}
                />
              </Space>
            </div>
            <Form.Item name={[field.name, 'enabled']} valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {({ getFieldValue }) => {
                const itemScope = (getFieldValue(['items', field.name, 'metadata', 'fieldScope']) || scope) as RegistrationFieldScope;
                const itemKey = String(getFieldValue(['items', field.name, 'itemKey']) || '');
                const isProtectedField = Boolean(protectedCollectionFieldKeys[itemScope]?.has(itemKey));
                return (
                  <Button
                    danger
                    disabled={isProtectedField}
                    title={isProtectedField ? '核心识别字段不可删除' : '删除字段'}
                    type="link"
                    onClick={() => {
                      remove(field.name);
                      scheduleSave();
                    }}
                  >
                    删除
                  </Button>
                );
              }}
            </Form.Item>
          </div>
        ))}
      </div>
      <Button
        block
        icon={<PlusOutlined />}
        onClick={() => {
          const nextItem = toEditableConfigItems([emptyConfigItem(scope, (fields.length + 1) * 10)])[0];
          nextItem.metadata = {
            ...nextItem.metadata,
            groupLabel: fieldGroupLabel === INTELLECTUAL_PROPERTY_GROUP_LABEL
              ? INTELLECTUAL_PROPERTY_GROUP_LABEL
              : undefined,
          };
          add(nextItem);
          scheduleSave();
        }}
      >
        新增{fieldGroupLabel || fieldScopeOptions.find((option) => option.value === scope)?.label}字段
      </Button>
    </Space>
  );
};

type ConfigModulePanelProps = {
  competitionUuid: string;
  module: CompetitionSettingsModuleConfig;
  items: CompetitionConfigItem[];
  storageSpaceOptions: StorageSpaceOption[];
  fieldScope?: RegistrationFieldScope;
  fieldGroupLabel?: string;
  includeMemberFields?: boolean;
  fileStageCode?: string;
  paymentProviderOptions?: PaymentProviderOption[];
  onSaved: (settings: CompetitionSettingsRecord) => void;
};

const ConfigModulePanel = forwardRef<CompetitionSettingsPanelHandle, ConfigModulePanelProps>(({
  competitionUuid,
  module,
  items,
  storageSpaceOptions,
  fieldScope,
  fieldGroupLabel,
  includeMemberFields = false,
  fileStageCode,
  paymentProviderOptions = [],
  onSaved,
}, ref) => {
  const [form] = Form.useForm<{
    items: EditableCompetitionConfigItem[];
    teamMinMembers?: number;
    teamMaxMembers?: number;
  }>();
  const [optionsEditor, setOptionsEditor] = useState<{
    fieldName: number;
    fieldTitle?: string;
    value: string;
  }>();
  const resolvedPaymentProviderOptions = useMemo(() => {
    const configuredCodes = new Set(paymentProviderOptions.map((option) => option.value));
    const unavailableOptions = items
      .filter((item) => item.itemType === 'PAYMENT_SETTINGS' && item.itemKey && !configuredCodes.has(item.itemKey))
      .map((item) => ({
        value: item.itemKey,
        label: `${item.title || item.itemKey}（系统渠道已停用）`,
        disabled: true,
      }));
    return [...paymentProviderOptions, ...unavailableOptions];
  }, [items, paymentProviderOptions]);

  const getInitialValues = useCallback(() => {
    const limits = getTeamMemberLimits(items);
    const sourceItems = removeDeprecatedRegistrationContactFields(
      items.filter((item) => item.itemType !== 'TEAM_SETTINGS'),
    );
    const stageItems = fileStageCode
      ? sourceItems.filter((item) => getFileConfigItemStageCode(item) === fileStageCode)
      : [];
    const legacySharedItems = fileStageCode && !stageItems.length
      ? sourceItems.filter((item) => getFileConfigItemStageCode(item) === 'GENERAL')
      : [];
    const editableItems = toEditableConfigItems(fileStageCode
      ? (stageItems.length ? stageItems : legacySharedItems).map((item) => ({
          ...item,
          itemType: 'STAGE_MATERIAL' as const,
          contentJson: serializeConfigItemMetadata({
            ...parseConfigItemMetadata(item.contentJson),
            stageCode: fileStageCode,
            stageName: resolveFileStageName(fileStageCode),
            materialType: 'FILE',
          }),
        }))
      : sourceItems);
    const initialItems = module.key === 'payments'
      ? [
          ...paymentProviderOptions.map((provider, index) => {
            const existing = editableItems.find((item) => item.itemKey === provider.value);
            return existing
              ? { ...existing, title: provider.label }
              : {
                  ...toEditableConfigItems([emptyConfigItem('PAYMENT_SETTINGS', (index + 1) * 10)])[0],
                  itemKey: provider.value,
                  title: provider.label,
                  enabled: false,
                };
          }),
          ...editableItems.filter((item) => !paymentProviderOptions.some((provider) => provider.value === item.itemKey)),
        ]
      : editableItems;
    return {
      items: initialItems,
      teamMinMembers: limits.minMembers,
      teamMaxMembers: limits.maxMembers,
    };
  }, [fileStageCode, items, module.key, paymentProviderOptions]);

  useEffect(() => {
    form.setFieldsValue(getInitialValues());
  }, [form, getInitialValues]);

  const save = useCallback(async () => {
    const values = form.getFieldsValue(true);
    if (!isConfigModuleReadyToSave(module.key, values.items || [])) {
      return false;
    }
    if (module.key === 'fields') {
      const minMembers = Number(values.teamMinMembers);
      const maxMembers = Number(values.teamMaxMembers);
      if (!Number.isInteger(minMembers) || !Number.isInteger(maxMembers)
        || minMembers < 1 || maxMembers > 20 || minMembers > maxMembers) {
        return false;
      }
    }
    try {
      const fieldItems = toConfigItems(values.items || []);
      const configItems = module.key === 'fields'
        ? [
            buildTeamSettingsConfigItem(
              normalizeTeamMemberLimit(values.teamMinMembers, DEFAULT_TEAM_MIN_MEMBERS),
              normalizeTeamMemberLimit(values.teamMaxMembers, DEFAULT_TEAM_MAX_MEMBERS),
            ),
            ...fieldItems,
          ]
        : fieldItems;
      const saved = module.key === 'files'
        ? await (async () => {
            const oppositeStageCode = fileStageCode === 'PRELIMINARY' ? 'FINAL' : 'PRELIMINARY';
            const hasOppositeStageItems = fileStageCode && items.some((item) => (
              getFileConfigItemStageCode(item) === oppositeStageCode
            ));
            const preservedItems = fileStageCode
              ? items.filter((item) => {
                  const itemStageCode = getFileConfigItemStageCode(item);
                  if (itemStageCode === fileStageCode) return false;
                  if (itemStageCode === 'GENERAL') return !hasOppositeStageItems;
                  return true;
                })
              : [];
            const groupedItems = splitFileConfigItemsByModule(fileStageCode
              ? [...preservedItems, ...configItems]
              : configItems);
            await saveCompetitionSettingsModule(competitionUuid, 'files', groupedItems.files, API_OPTS.SILENT);
            return saveCompetitionSettingsModule(competitionUuid, 'stage-materials', groupedItems.stageMaterials, API_OPTS.SILENT);
          })()
        : await saveCompetitionSettingsModule(competitionUuid, module.key, configItems, API_OPTS.SILENT);
      onSaved(saved);
      return true;
    } catch (error) {
      showErrorMessage(error, formatMessage({ id: 'page.competition.settings.item.saveFailed', defaultMessage: 'Settings save failed' }));
      return false;
    }
  }, [competitionUuid, fileStageCode, form, items, module.key, onSaved]);
  const { scheduleSave, flushPendingSave, saveNow } = useDebouncedAutoSave(save);

  const reorderField = useCallback((
    scopedFields: Array<{ key: number; name: number }>,
    fromIndex: number,
    toIndex: number,
  ) => {
    const currentItems = (form.getFieldValue('items') || []) as EditableCompetitionConfigItem[];
    const nextItems = reorderScopedConfigItems(
      currentItems,
      scopedFields.map((field) => field.name),
      fromIndex,
      toIndex,
    );
    if (nextItems === currentItems) {
      return;
    }
    form.setFieldValue('items', nextItems);
    scheduleSave();
  }, [form, scheduleSave]);

  const confirmOptionsEditor = useCallback(() => {
    if (!optionsEditor) {
      return;
    }
    const normalizedOptions = optionsEditor.value
      .split('\n')
      .map((option) => option.trim())
      .filter(Boolean)
      .filter((option, index, allOptions) => allOptions.indexOf(option) === index)
      .join('\n');
    if (!normalizedOptions) {
      message.warning('请至少填写一个选项');
      return;
    }
    form.setFieldValue(['items', optionsEditor.fieldName, 'metadata', 'options'], normalizedOptions);
    setOptionsEditor(undefined);
    scheduleSave();
  }, [form, optionsEditor, scheduleSave]);

  useImperativeHandle(ref, () => ({
    flushPendingSave,
    saveNow,
  }), [flushPendingSave, saveNow]);

  return (
    <section className="competition-config-module">
      {module.key === 'files' && fileStageCode ? null : (
        <>
          <div className="competition-config-module__header">
            <Typography.Title className="competition-config-module__title" level={4}>
              {module.key === 'fields' && fieldScope === 'PROJECT_FIELD'
                ? (fieldGroupLabel || '项目信息')
                : getCompetitionSettingsModuleLabel(module)}
            </Typography.Title>
          </div>
          <Typography.Paragraph className="competition-config-module__description" type="secondary">
            {module.key === 'fields' && fieldScope === 'PROJECT_FIELD'
              ? fieldGroupLabel === INTELLECTUAL_PROPERTY_GROUP_LABEL
                ? '配置报名时需要收集的知识产权、权利状态及分布区域信息。下拉和多选内容可通过“设置选项”动态调整。'
                : '配置报名时需要收集的项目名称、项目简介及其他项目基础信息。'
              : getCompetitionSettingsModuleDescription(module)}
          </Typography.Paragraph>
        </>
      )}
      {module.key === 'payments' && !paymentProviderOptions.length ? (
        <Alert
          showIcon
          type="warning"
          message="暂无可绑定的支付渠道"
          description="请先在系统支付设置中完成渠道配置并启用，已启用的渠道会自动出现在这里。"
          style={{ marginBottom: 16 }}
        />
      ) : null}
      <Form form={form} layout="vertical" initialValues={getInitialValues()} onValuesChange={scheduleSave}>
        {module.key === 'fields' && fieldScope === 'TEAM_FIELD' ? (
          <Card className="competition-config-item competition-config-item--team-limits" size="small" title="团队人数设置">
            <div className="competition-config-grid">
              <Form.Item
                name="teamMinMembers"
                label="团队最小人数"
                dependencies={['teamMaxMembers']}
                rules={[
                  { required: true, message: '请输入团队最小人数' },
                  ({ getFieldValue }) => ({
                    validator: (_, value) => Number(value) <= Number(getFieldValue('teamMaxMembers'))
                      ? Promise.resolve()
                      : Promise.reject(new Error('最小人数不能大于最大人数')),
                  }),
                ]}
              >
                <InputNumber min={1} max={20} precision={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item
                name="teamMaxMembers"
                label="团队最大人数"
                dependencies={['teamMinMembers']}
                rules={[
                  { required: true, message: '请输入团队最大人数' },
                  ({ getFieldValue }) => ({
                    validator: (_, value) => Number(value) >= Number(getFieldValue('teamMinMembers'))
                      ? Promise.resolve()
                      : Promise.reject(new Error('最大人数不能小于最小人数')),
                  }),
                ]}
              >
                <InputNumber min={1} max={20} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </div>
            <Typography.Text type="secondary">报名时团队成员数必须在该范围内。</Typography.Text>
          </Card>
        ) : null}
        <Form.List name="items">
          {(fields, { add, remove }) =>
            module.key === 'fields' ? (
              <Tabs
                className={fieldScope ? 'competition-field-scope-tabs competition-field-scope-tabs--embedded' : 'competition-field-scope-tabs'}
                activeKey={fieldScope}
                items={fieldScopeOptions.filter((scopeOption) => !fieldScope || scopeOption.value === fieldScope).map((scopeOption) => {
                  const scopedFields = fields.filter((field) => {
                    const item = form.getFieldValue(['items', field.name]) as EditableCompetitionConfigItem | undefined;
                    if ((item?.metadata?.fieldScope || item?.itemType) !== scopeOption.value) {
                      return false;
                    }
                    if (scopeOption.value !== 'PROJECT_FIELD' || !fieldGroupLabel) {
                      return true;
                    }
                    const isIntellectualProperty = item?.metadata?.groupLabel === INTELLECTUAL_PROPERTY_GROUP_LABEL;
                    return fieldGroupLabel === INTELLECTUAL_PROPERTY_GROUP_LABEL
                      ? isIntellectualProperty
                      : !isIntellectualProperty;
                  });
                  const memberFields = includeMemberFields && scopeOption.value === 'TEAM_FIELD'
                    ? fields.filter((field) => {
                        const item = form.getFieldValue(['items', field.name]) as EditableCompetitionConfigItem | undefined;
                        return (item?.metadata?.fieldScope || item?.itemType) === 'MEMBER_FIELD';
                      })
                    : [];
                  return {
                    key: scopeOption.value,
                    label: fieldGroupLabel || scopeOption.label,
                    children: (
                      <Space className="competition-config-list" direction="vertical" size={16}>
                        {renderFieldSettingsTable(
                          scopedFields,
                          add,
                          remove,
                          scopeOption.value,
                          scheduleSave,
                          reorderField,
                          (fieldName, fieldTitle, options) => setOptionsEditor({
                            fieldName,
                            fieldTitle,
                            value: options || '',
                          }),
                          fieldGroupLabel,
                        )}
                        {includeMemberFields && scopeOption.value === 'TEAM_FIELD' ? (
                          <>
                            <Typography.Title level={5}>成员信息字段</Typography.Title>
                            {renderFieldSettingsTable(
                              memberFields,
                              add,
                              remove,
                              'MEMBER_FIELD',
                              scheduleSave,
                              reorderField,
                              (fieldName, fieldTitle, options) => setOptionsEditor({
                                fieldName,
                                fieldTitle,
                                value: options || '',
                              }),
                            )}
                          </>
                        ) : null}
                      </Space>
                    ),
                  };
                })}
              />
            ) : module.key === 'payments' ? (
              <Table
                className="competition-payment-provider-table"
                rowKey="key"
                size="small"
                pagination={false}
                dataSource={fields}
                scroll={{ x: 720 }}
                locale={{ emptyText: '系统支付设置中暂无已配置并启用的渠道' }}
                columns={[
                  {
                    title: '支付渠道',
                    key: 'provider',
                    width: 240,
                    render: (_, field) => {
                      const item = form.getFieldValue(['items', field.name]) as EditableCompetitionConfigItem | undefined;
                      const provider = resolvedPaymentProviderOptions.find((option) => option.value === item?.itemKey);
                      return (
                        <>
                          <Form.Item name={[field.name, 'itemKey']} hidden rules={[{ required: true }]}>
                            <Input />
                          </Form.Item>
                          <Form.Item name={[field.name, 'title']} hidden rules={[{ required: true }]}>
                            <Input />
                          </Form.Item>
                          <Typography.Text strong>{provider?.label || item?.title || item?.itemKey || '-'}</Typography.Text>
                        </>
                      );
                    },
                  },
                  {
                    title: '系统状态',
                    key: 'systemStatus',
                    width: 150,
                    render: (_, field) => {
                      const item = form.getFieldValue(['items', field.name]) as EditableCompetitionConfigItem | undefined;
                      const provider = resolvedPaymentProviderOptions.find((option) => option.value === item?.itemKey);
                      return provider?.disabled
                        ? <Tag>已停用</Tag>
                        : <Tag color="success">可用</Tag>;
                    },
                  },
                  {
                    title: '绑定状态',
                    key: 'enabled',
                    width: 190,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'enabled']} valuePropName="checked" style={{ marginBottom: 0 }}>
                        <Switch aria-label="绑定状态" />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '排序',
                    key: 'sortOrder',
                    width: 140,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'sortOrder']} style={{ marginBottom: 0 }}>
                        <InputNumber min={0} precision={0} style={{ width: 110 }} />
                      </Form.Item>
                    ),
                  },
                ]}
              />
            ) : (
              <Space className="competition-config-list" direction="vertical" size={16}>
              {fields.map((field, index) => (
                <Card
                  key={field.key}
                  className="competition-config-item"
                  size="small"
                  title={
                    <Space size={8} wrap>
                      <span>{module.key === 'payments'
                        ? (resolvedPaymentProviderOptions.find((option) => option.value === form.getFieldValue(['items', field.name, 'itemKey']))?.label
                          || form.getFieldValue(['items', field.name, 'title'])
                          || `支付渠道 ${index + 1}`)
                        : formatMessage({ id: 'page.competition.settings.item.title', defaultMessage: 'Item {index}' }, { index: index + 1 })}</span>
                      <Form.Item noStyle shouldUpdate>
                        {({ getFieldValue }) => {
                          const enabled = getFieldValue(['items', field.name, 'enabled']);
                          const required = getFieldValue(['items', field.name, 'requiredFlag']);
                          const isDocumentModule = module.key === 'documents';
                          return (
                            <>
                              {isDocumentModule && enabled !== false ? <Tag color="blue">需确认</Tag> : null}
                              {!isDocumentModule && required ? <Tag color="red">必填</Tag> : null}
                              {enabled === false
                                ? <Tag>{module.key === 'payments' ? '未绑定' : '停用'}</Tag>
                                : <Tag color="green">{module.key === 'payments' ? '已绑定' : '启用'}</Tag>}
                            </>
                          );
                        }}
                      </Form.Item>
                    </Space>
                  }
                  extra={module.key === 'payments' ? null : (
                    <Button
                      danger
                      onClick={() => {
                        remove(field.name);
                        scheduleSave();
                      }}
                    >
                      {formatMessage({ id: 'page.competition.settings.item.remove', defaultMessage: 'Remove' })}
                    </Button>
                  )}
                >
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div className="competition-config-item__fields">
                      {renderConfigItemFields(module, field.name, storageSpaceOptions)}
                    </div>
                    <div className="competition-config-switches">
                      {module.key !== 'documents' ? (
                        <Form.Item name={[field.name, 'requiredFlag']} label={formatMessage({ id: 'page.competition.settings.item.required', defaultMessage: 'Required' })} valuePropName="checked">
                          <Switch checkedChildren="必填" unCheckedChildren="选填" />
                        </Form.Item>
                      ) : null}
                      <Form.Item name={[field.name, 'enabled']} label={module.key === 'documents' ? '报名前展示' : formatMessage({ id: 'page.competition.settings.item.enabled', defaultMessage: 'Enabled' })} valuePropName="checked">
                        <Switch checkedChildren="启用" unCheckedChildren="停用" />
                      </Form.Item>
                      {module.key !== 'fields' && module.key !== 'timeline' && module.key !== 'files' ? (
                        <Form.Item name={[field.name, 'sortOrder']} label={formatMessage({ id: 'page.competition.settings.item.sort', defaultMessage: 'Sort' })}>
                          <InputNumber min={0} style={{ width: 120 }} />
                        </Form.Item>
                      ) : null}
                    </div>
                  </Space>
                </Card>
              ))}
              <Button
                block
                icon={<PlusOutlined />}
                onClick={() => {
                  const nextItem = toEditableConfigItems([emptyConfigItem(
                    fileStageCode ? 'STAGE_MATERIAL' : module.itemTypes[0],
                    (fields.length + 1) * 10,
                  )])[0];
                  if (fileStageCode) {
                    nextItem.metadata = {
                      ...nextItem.metadata,
                      stageCode: fileStageCode,
                      stageName: resolveFileStageName(fileStageCode),
                    };
                  }
                  add(nextItem);
                  scheduleSave();
                }}
              >
                {formatMessage({ id: 'page.competition.settings.item.add', defaultMessage: 'Add item' })}
              </Button>
            </Space>
            )
          }
        </Form.List>
      </Form>
      <Modal
        title={`设置下拉选项${optionsEditor?.fieldTitle ? ` · ${optionsEditor.fieldTitle}` : ''}`}
        open={Boolean(optionsEditor)}
        okText="保存选项"
        cancelText="取消"
        onOk={confirmOptionsEditor}
        onCancel={() => setOptionsEditor(undefined)}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          每行填写一个选项；保存时会自动移除空行和重复项。
        </Typography.Paragraph>
        <Input.TextArea
          autoFocus
          rows={8}
          maxLength={500}
          placeholder={'例如：\n男\n女'}
          value={optionsEditor?.value || ''}
          onChange={(event) => setOptionsEditor((current) => current
            ? { ...current, value: event.target.value }
            : current)}
        />
      </Modal>
    </section>
  );
});

ConfigModulePanel.displayName = 'ConfigModulePanel';

type CompetitionBasicSettingsPanelProps = {
  competition: CompetitionRecord;
  categoryOptions: Array<{ label: string; value: string }>;
  levelOptions: Array<{ label: string; value: string }>;
  onSaved: (competition: CompetitionRecord) => void;
};

const CompetitionBasicSettingsPanel = forwardRef<CompetitionSettingsPanelHandle, CompetitionBasicSettingsPanelProps>(({
  competition,
  categoryOptions,
  levelOptions,
  onSaved,
}, ref) => {
  const [form] = Form.useForm<CompetitionFormValues>();

  useEffect(() => {
    form.resetFields();
    form.setFieldsValue({ ...defaultCompetitionFormValues, ...recordToFormValues(competition) });
  }, [competition, form]);

  const save = useCallback(async () => {
    const values = form.getFieldsValue(true);
    if (!isBasicSettingsPageReadyToSave(values)) {
      return false;
    }
    try {
      const saved = await updateCompetition(competition.id, normalizePayload({
        ...defaultCompetitionFormValues,
        ...recordToFormValues(competition),
        ...values,
      } as CompetitionFormValues), API_OPTS.SILENT);
      onSaved(saved);
      return true;
    } catch (error) {
      showErrorMessage(error, '基础信息保存失败');
      return false;
    }
  }, [competition, form, onSaved]);
  const { scheduleSave, flushPendingSave, saveNow } = useDebouncedAutoSave(save);

  useImperativeHandle(ref, () => ({
    flushPendingSave,
    saveNow,
  }), [flushPendingSave, saveNow]);

  return (
    <section className="competition-config-module">
      <div className="competition-config-module__header">
        <Typography.Title className="competition-config-module__title" level={4}>
          基础信息
        </Typography.Title>
      </div>
      <Typography.Paragraph className="competition-config-module__description" type="secondary">
        管理赛事名称、组织者和参赛规则。
      </Typography.Paragraph>
      <Form<CompetitionFormValues> form={form} layout="vertical" initialValues={defaultCompetitionFormValues} onValuesChange={scheduleSave}>
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
                <Space direction="vertical" size={12} className="competition-dynamic-list">
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
                            aria-label="Add organizer"
                            title="Add organizer"
                            icon={<PlusOutlined />}
                            onClick={() => {
                              add({ role: '', name: '' });
                              scheduleSave();
                            }}
                          />
                        ) : null}
                        <Button
                          aria-label="Remove organizer"
                          title="Remove organizer"
                          icon={<DeleteOutlined />}
                          disabled={fields.length <= 1}
                          onClick={() => {
                            remove(field.name);
                            scheduleSave();
                          }}
                        />
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

        <Form.Item name="code" hidden>
          <Input />
        </Form.Item>
      </Form>
    </section>
  );
});

CompetitionBasicSettingsPanel.displayName = 'CompetitionBasicSettingsPanel';

type CompetitionPaymentSettingsPanelProps = {
  competition: CompetitionRecord;
  competitionUuid: string;
  module: CompetitionSettingsModuleConfig;
  items: CompetitionConfigItem[];
  storageSpaceOptions: StorageSpaceOption[];
  paymentProviderOptions: PaymentProviderOption[];
  onCompetitionSaved: (competition: CompetitionRecord) => void;
  onSettingsSaved: (settings: CompetitionSettingsRecord) => void;
};

const CompetitionPaymentSettingsPanel = forwardRef<CompetitionSettingsPanelHandle, CompetitionPaymentSettingsPanelProps>(({
  competition,
  competitionUuid,
  module,
  items,
  storageSpaceOptions,
  paymentProviderOptions,
  onCompetitionSaved,
  onSettingsSaved,
}, ref) => {
  const [form] = Form.useForm<CompetitionFormValues>();
  const paymentMethodsRef = useRef<CompetitionSettingsPanelHandle | null>(null);

  useEffect(() => {
    form.resetFields();
    form.setFieldsValue({
      feeMode: competition.feeMode || undefined,
      entryFeeMinor: Number(competition.entryFeeMinor || 0) / 100,
      currency: competition.currency || 'CNY',
    });
  }, [competition, form]);

  const saveFeeSettings = useCallback(async () => {
    const values = await form.validateFields();
    try {
      const saved = await updateCompetition(competition.id, normalizePayload({
        ...defaultCompetitionFormValues,
        ...recordToFormValues(competition),
        ...values,
      } as CompetitionFormValues), API_OPTS.SILENT);
      onCompetitionSaved(saved);
      return true;
    } catch (error) {
      showErrorMessage(error, '费用设置保存失败');
      return false;
    }
  }, [competition, form, onCompetitionSaved]);
  const { scheduleSave, flushPendingSave, saveNow } = useDebouncedAutoSave(saveFeeSettings);

  useImperativeHandle(ref, () => ({
    flushPendingSave: async () => {
      const feeSaved = await flushPendingSave();
      const methodsSaved = await paymentMethodsRef.current?.flushPendingSave() ?? true;
      return feeSaved && methodsSaved;
    },
    saveNow: async () => {
      const feeSaved = await saveNow();
      const methodsSaved = await paymentMethodsRef.current?.saveNow() ?? true;
      return feeSaved && methodsSaved;
    },
  }), [flushPendingSave, saveNow]);

  return (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      <section className="competition-config-module">
        <div className="competition-config-module__header">
          <Typography.Title className="competition-config-module__title" level={4}>
            费用设置
          </Typography.Title>
        </div>
        <Typography.Paragraph className="competition-config-module__description" type="secondary">
          配置赛事收费规则、参赛费用和结算货币。
        </Typography.Paragraph>
        <Form<CompetitionFormValues> form={form} layout="vertical" onValuesChange={scheduleSave}>
          <div className="competition-basic-section__grid">
            <Form.Item name="feeMode" label="收费方式" rules={[{ required: true, message: '请选择收费方式' }]}>
              <Select options={feeModeOptions} placeholder="请选择收费方式" />
            </Form.Item>
            <Form.Item name="entryFeeMinor" label="参赛费用（元）" rules={[{ required: true, message: '请输入参赛费用' }]}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="currency" label="货币" rules={[{ required: true, message: '请选择货币' }]}>
              <Select options={[{ label: 'CNY', value: 'CNY' }]} />
            </Form.Item>
          </div>
        </Form>
      </section>
      <ConfigModulePanel
        ref={paymentMethodsRef}
        competitionUuid={competitionUuid}
        module={module}
        items={items}
        storageSpaceOptions={storageSpaceOptions}
        paymentProviderOptions={paymentProviderOptions}
        onSaved={onSettingsSaved}
      />
    </Space>
  );
});

CompetitionPaymentSettingsPanel.displayName = 'CompetitionPaymentSettingsPanel';

type CompetitionTimelineSettingsPanelProps = {
  competition: CompetitionRecord;
  onSaved: (competition: CompetitionRecord) => void;
};

const CompetitionTimelineSettingsPanel = forwardRef<CompetitionSettingsPanelHandle, CompetitionTimelineSettingsPanelProps>(({
  competition,
  onSaved,
}, ref) => {
  const [form] = Form.useForm<CompetitionFormValues>();
  const schedules = Form.useWatch('schedules', form) || [];

  useEffect(() => {
    form.resetFields();
    form.setFieldsValue({ ...defaultCompetitionFormValues, ...recordToFormValues(competition) });
  }, [competition, form]);

  const save = useCallback(async () => {
    const values = form.getFieldsValue(true);
    if (!isTimelineSettingsPageReadyToSave(values)) {
      return false;
    }
    try {
      const saved = await updateCompetition(competition.id, normalizePayload({
        ...defaultCompetitionFormValues,
        ...recordToFormValues(competition),
        ...values,
      } as CompetitionFormValues), API_OPTS.SILENT);
      onSaved(saved);
      return true;
    } catch (error) {
      showErrorMessage(error, '赛事时间保存失败');
      return false;
    }
  }, [competition, form, onSaved]);
  const { scheduleSave, flushPendingSave, saveNow } = useDebouncedAutoSave(save);

  useImperativeHandle(ref, () => ({
    flushPendingSave,
    saveNow,
  }), [flushPendingSave, saveNow]);

  return (
    <section className="competition-config-module">
      <div className="competition-config-module__header">
        <Typography.Title className="competition-config-module__title" level={4}>
          赛事时间
        </Typography.Title>
      </div>
      <Typography.Paragraph className="competition-config-module__description" type="secondary">
        统一管理报名时间和各阶段竞赛安排。
      </Typography.Paragraph>
      <Form<CompetitionFormValues> form={form} layout="vertical" initialValues={defaultCompetitionFormValues} onValuesChange={scheduleSave}>
        <section className="competition-basic-section">
          <Typography.Title className="competition-basic-section__title" level={5}>
            报名时间
          </Typography.Title>
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
        </section>

        <section className="competition-basic-section">
          <Typography.Title className="competition-basic-section__title" level={5}>
            竞赛安排
          </Typography.Title>
          <Form.List name="schedules">
            {(fields, { add, remove }) => (
              <div className="competition-schedule-settings">
                {fields.length ? (
                  <Form.Item
                    name={[fields[0].name, 'timeMode']}
                    label="竞赛安排"
                    required
                    rules={[{ required: true, message: '请选择时间状态' }]}
                    className="competition-schedule-settings__status"
                  >
                    <Radio.Group
                      options={timeModeOptions}
                      onChange={(event) => {
                        const nextMode = event.target.value as CompetitionTimeMode;
                        const currentSchedules = form.getFieldValue('schedules') || [];
                        if (nextMode === 'CONFIRMED') {
                          form.setFieldValue('schedules', [{ ...currentSchedules[0], timeMode: 'CONFIRMED' }]);
                          scheduleSave();
                          return;
                        }
                        form.setFieldValue('schedules', [{ timeMode: 'TBD' }]);
                        scheduleSave();
                      }}
                    />
                  </Form.Item>
                ) : null}
                {schedules[0]?.timeMode === 'CONFIRMED' ? (
                  <Space orientation="vertical" size={16} className="competition-schedule-settings__content">
                    <div className="competition-schedule-table">
                      <div className="competition-schedule-table__head">
                        <span>阶段名称</span>
                        <span>比赛时间</span>
                        <span>评审时间</span>
                        <span>操作</span>
                      </div>
                      {fields.map((field) => (
                        <div key={field.key} className="competition-schedule-table__row">
                          <Form.Item name={[field.name, 'title']} rules={[{ required: true, message: '请输入阶段名称' }]}>
                            <Input maxLength={128} placeholder="例如：初赛" />
                          </Form.Item>
                          <Form.Item
                            name={[field.name, 'timeRange']}
                            rules={[
                              { required: true, message: '请选择比赛时间' },
                              {
                                validator: (_, value: CompetitionScheduleFormItem['timeRange']) => {
                                  if (!getCompleteTimeRange(value)) {
                                    return Promise.reject(new Error('请选择开始和结束时间'));
                                  }
                                  return Promise.resolve();
                                },
                              },
                            ]}
                          >
                            <CompetitionDateTimeRangePicker />
                          </Form.Item>
                          <Form.Item
                            name={[field.name, 'reviewRange']}
                            rules={[
                              { required: true, message: '请选择评审时间' },
                              {
                                validator: (_, value: CompetitionScheduleFormItem['reviewRange']) => (
                                  getCompleteTimeRange(value)
                                    ? Promise.resolve()
                                    : Promise.reject(new Error('请选择评审开始和结束时间'))
                                ),
                              },
                            ]}
                          >
                            <CompetitionDateTimeRangePicker />
                          </Form.Item>
                          <Button
                            danger
                            type="link"
                            aria-label="删除竞赛安排"
                            title={fields.length <= 1 ? '至少保留一个竞赛安排' : '删除竞赛安排'}
                            disabled={fields.length <= 1}
                            onClick={() => {
                              remove(field.name);
                              scheduleSave();
                            }}
                          >
                            删除
                          </Button>
                        </div>
                      ))}
                    </div>
                    <Button
                      block
                      icon={<PlusOutlined />}
                      onClick={() => {
                        add({ timeMode: 'CONFIRMED', title: '' });
                        scheduleSave();
                      }}
                    >
                      新增竞赛安排
                    </Button>
                  </Space>
                ) : null}
              </div>
            )}
          </Form.List>
        </section>
      </Form>
    </section>
  );
});

CompetitionTimelineSettingsPanel.displayName = 'CompetitionTimelineSettingsPanel';

type CompetitionStageWindowsPanelProps = {
  competitionId: number;
  stageCode: 'PRELIMINARY' | 'FINAL';
  onStagesChange: (stages: CompetitionStageRecord[]) => void;
};

const CompetitionStageWindowsPanel = ({ competitionId, stageCode, onStagesChange }: CompetitionStageWindowsPanelProps) => {
  const [stages, setStages] = useState<CompetitionStageRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [savingId, setSavingId] = useState<number>();

  const loadStages = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listCompetitionStages(competitionId);
      setStages(result);
      onStagesChange(result);
    } catch (error) {
      showErrorMessage(error, '阶段时间加载失败');
    } finally {
      setLoading(false);
    }
  }, [competitionId, onStagesChange]);

  useEffect(() => {
    void loadStages();
  }, [loadStages]);

  const initializeStage = async () => {
    setLoading(true);
    try {
      const existingCodes = new Set(stages.map((stage) => stage.stageCode));
      if (!existingCodes.has(stageCode)) {
        await createCompetitionStage(competitionId, {
          stageCode,
          stageName: stageCode === 'PRELIMINARY' ? '初赛' : '决赛',
          status: 'DRAFT',
          sort: stageCode === 'PRELIMINARY' ? 10 : 20,
        });
      }
      await loadStages();
      message.success(`${stageCode === 'PRELIMINARY' ? '初赛' : '决赛'}阶段已初始化`);
    } catch (error) {
      showErrorMessage(error, '阶段初始化失败');
    } finally {
      setLoading(false);
    }
  };

  const currentStage = stages.find((stage) => stage.stageCode === stageCode);

  const updateLocalStage = (stageId: number, patch: Partial<CompetitionStageRecord>) => {
    setStages((current) => current.map((stage) => stage.id === stageId ? { ...stage, ...patch } : stage));
  };

  const saveStage = async (stage: CompetitionStageRecord) => {
    setSavingId(stage.id);
    try {
      const saved = await updateCompetitionStage(stage.id, {
        stageCode: stage.stageCode as 'PRELIMINARY' | 'FINAL',
        stageName: stage.stageName,
        status: stage.status,
        sort: stage.sort,
        materialSubmitStart: stage.materialSubmitStart,
        materialSubmitEnd: stage.materialSubmitEnd,
        reviewStart: stage.reviewStart,
        reviewEnd: stage.reviewEnd,
        promotionRuleType: stage.stageCode === 'PRELIMINARY' ? (stage.promotionRuleType || 'PERCENTAGE') : undefined,
        promotionRuleValue: stage.stageCode === 'PRELIMINARY' ? (stage.promotionRuleValue || 30) : undefined,
        promotionTiePolicy: stage.stageCode === 'PRELIMINARY' ? 'MANUAL_REVIEW' : undefined,
      });
      updateLocalStage(stage.id, saved);
      onStagesChange(stages.map((item) => item.id === saved.id ? saved : item));
      message.success(`${saved.stageName}时间与权限已保存`);
    } catch (error) {
      showErrorMessage(error, '阶段保存失败');
    } finally {
      setSavingId(undefined);
    }
  };

  const columns: ColumnsType<CompetitionStageRecord> = [
    { title: '阶段', dataIndex: 'stageName', width: 130 },
    {
      title: '材料修改时间', key: 'materialWindow', width: 390,
      render: (_, stage) => (
        <DatePicker.RangePicker
          showTime
          value={stage.materialSubmitStart && stage.materialSubmitEnd
            ? [dayjs(stage.materialSubmitStart), dayjs(stage.materialSubmitEnd)]
            : undefined}
          onChange={(value) => updateLocalStage(stage.id, {
            materialSubmitStart: value?.[0]?.format('YYYY-MM-DDTHH:mm:ss') || null,
            materialSubmitEnd: value?.[1]?.format('YYYY-MM-DDTHH:mm:ss') || null,
          })}
        />
      ),
    },
    {
      title: '评审时间', key: 'reviewWindow', width: 390,
      render: (_, stage) => (
        <DatePicker.RangePicker
          showTime
          value={stage.reviewStart && stage.reviewEnd ? [dayjs(stage.reviewStart), dayjs(stage.reviewEnd)] : undefined}
          onChange={(value) => updateLocalStage(stage.id, {
            reviewStart: value?.[0]?.format('YYYY-MM-DDTHH:mm:ss') || null,
            reviewEnd: value?.[1]?.format('YYYY-MM-DDTHH:mm:ss') || null,
          })}
        />
      ),
    },
    {
      title: '材料权限', key: 'permission', width: 190,
      render: (_, stage) => stage.stageCode === 'FINAL'
        ? <Tag color="blue">仅已公布晋级团队</Tag>
        : <Tag>已报名团队</Tag>,
    },
    {
      title: '晋级方式', key: 'promotionRule', width: 290,
      render: (_, stage) => stage.stageCode === 'PRELIMINARY' ? (
        <Space size={8}>
          <Select
            style={{ width: 110 }}
            value={stage.promotionRuleType || 'PERCENTAGE'}
            options={[
              { label: '按比例', value: 'PERCENTAGE' },
              { label: '按人数', value: 'COUNT' },
            ]}
            onChange={(value) => updateLocalStage(stage.id, { promotionRuleType: value })}
          />
          <InputNumber
            min={1}
            max={stage.promotionRuleType === 'COUNT' ? undefined : 100}
            precision={stage.promotionRuleType === 'COUNT' ? 0 : 2}
            value={stage.promotionRuleValue ?? 30}
            addonAfter={stage.promotionRuleType === 'COUNT' ? '人' : '%'}
            onChange={(value) => updateLocalStage(stage.id, { promotionRuleValue: value })}
          />
        </Space>
      ) : <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: '开放', dataIndex: 'status', width: 100,
      render: (_, stage) => (
        <Switch
          checked={stage.status === 'ENABLED'}
          onChange={(checked) => updateLocalStage(stage.id, { status: checked ? 'ENABLED' : 'DRAFT' })}
        />
      ),
    },
    {
      title: '操作', key: 'actions', width: 110,
      render: (_, stage) => (
        <Button type="primary" loading={savingId === stage.id} onClick={() => void saveStage(stage)}>保存</Button>
      ),
    },
  ];

  return (
    <section className="competition-config-module">
      <div className="competition-config-module__header">
        <div>
          <Typography.Title className="competition-config-module__title" level={4}>
            {stageCode === 'PRELIMINARY' ? '初赛' : '决赛'}设置
          </Typography.Title>
          <Typography.Paragraph type="secondary">
            {stageCode === 'PRELIMINARY'
              ? '设置初赛材料修改、评审时间和晋级规则。'
              : '设置决赛材料修改和评审时间；参赛范围自动与“评审与晋级”结果联动。'}
          </Typography.Paragraph>
        </div>
        {!currentStage ? (
          <Button type="primary" onClick={() => void initializeStage()}>
            初始化{stageCode === 'PRELIMINARY' ? '初赛' : '决赛'}
          </Button>
        ) : null}
      </div>
      <Table
        rowKey="id"
        loading={loading}
        pagination={false}
        dataSource={currentStage ? [currentStage] : []}
        columns={columns}
        scroll={{ x: 1610 }}
        locale={{ emptyText: `请先初始化${stageCode === 'PRELIMINARY' ? '初赛' : '决赛'}阶段` }}
      />
    </section>
  );
};

type CompetitionStageAndMaterialPanelProps = {
  competition: CompetitionRecord;
  competitionUuid: string;
  activeTab: CompetitionSettingsStageTab;
  module: CompetitionSettingsModuleConfig;
  items: CompetitionConfigItem[];
  storageSpaceOptions: StorageSpaceOption[];
  onCompetitionSaved: (competition: CompetitionRecord) => void;
  onSettingsSaved: (settings: CompetitionSettingsRecord) => void;
};

const CompetitionStageAndMaterialPanel = forwardRef<CompetitionSettingsPanelHandle, CompetitionStageAndMaterialPanelProps>(({
  competition,
  competitionUuid,
  activeTab,
  module,
  items,
  storageSpaceOptions,
  onCompetitionSaved,
  onSettingsSaved,
}, ref) => {
  const timelineRef = useRef<CompetitionSettingsPanelHandle | null>(null);
  const materialsRef = useRef<CompetitionSettingsPanelHandle | null>(null);
  const [stages, setStages] = useState<CompetitionStageRecord[]>([]);
  const materialStageTabs = useMemo(() => getCompetitionMaterialStageTabs(competition), [competition]);
  const activeStage = materialStageTabs.find((stage) => stage.key === activeTab) || materialStageTabs[0];

  useEffect(() => {
    let active = true;
    void listCompetitionStages(competition.id)
      .then((records) => {
        if (active) setStages(records || []);
      })
      .catch((error) => showErrorMessage(error, '赛事阶段加载失败'));
    return () => { active = false; };
  }, [competition.id]);

  const syncStageForms = useCallback(async (
    materialItems: CompetitionConfigItem[],
    currentStages: CompetitionStageRecord[],
  ) => {
    await Promise.all(currentStages.map((stage) => {
      const exactItems = materialItems.filter((item) => (
        getFileConfigItemStageCode(item) === stage.stageCode
      ));
      const fallbackItems = materialItems.filter((item) => (
        getFileConfigItemStageCode(item) === 'GENERAL'
      ));
      const stageItems = exactItems.length ? exactItems : fallbackItems;
      const formSchemaJson = JSON.stringify({
        fields: stageItems
          .filter((item) => item.enabled !== false)
          .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
          .map((item) => {
            const metadata = parseConfigItemMetadata(item.contentJson);
            return {
              key: item.itemKey,
              label: item.title,
              type: 'file',
              required: Boolean(item.requiredFlag),
              fileFormat: normalizeFileFormat(metadata.fileFormat),
              maxSizeMb: metadata.maxSizeMb || 20,
              storageKey: metadata.storageKey,
            };
          }),
      });
      return upsertCompetitionStageForm(stage.id, {
        formName: `${stage.stageName}参赛材料`,
        formSchemaJson,
        status: 'ENABLED',
      });
    }));
  }, []);

  useImperativeHandle(ref, () => ({
    flushPendingSave: async () => {
      const timelineSaved = await timelineRef.current?.flushPendingSave() ?? true;
      const materialsSaved = await materialsRef.current?.flushPendingSave() ?? true;
      return timelineSaved && materialsSaved;
    },
    saveNow: async () => {
      const timelineSaved = await timelineRef.current?.saveNow() ?? true;
      const materialsSaved = await materialsRef.current?.saveNow() ?? true;
      return timelineSaved && materialsSaved;
    },
  }), []);

  useEffect(() => {
    if (!stages.length) return;
    void syncStageForms(items, stages).catch((error) => showErrorMessage(error, '阶段材料模板同步失败'));
  }, [items, stages, syncStageForms]);

  const handleMaterialsSaved = useCallback(async (saved: CompetitionSettingsRecord) => {
    onSettingsSaved(saved);
    const materialItems = [...saved.files, ...saved.stageMaterials]
      .filter((item) => item.enabled !== false)
      .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0));
    await syncStageForms(materialItems, stages);
  }, [onSettingsSaved, stages, syncStageForms]);

  return activeTab === 'timeline' ? (
    <CompetitionTimelineSettingsPanel ref={timelineRef} competition={competition} onSaved={onCompetitionSaved} />
  ) : activeStage ? (
    <section className="competition-config-module">
      <div className="competition-config-module__header">
        <Typography.Title className="competition-config-module__title" level={4}>
          {activeStage.stageName}提交材料设置
        </Typography.Title>
      </div>
      <Typography.Paragraph className="competition-config-module__description" type="secondary">
        配置参赛者在{activeStage.stageName}阶段需要提交的材料和字段要求。
      </Typography.Paragraph>
      <ConfigModulePanel
        ref={materialsRef}
        competitionUuid={competitionUuid}
        module={module}
        items={items}
        storageSpaceOptions={storageSpaceOptions}
        fileStageCode={activeStage.stageCode}
        onSaved={(saved) => void handleMaterialsSaved(saved)}
      />
    </section>
  ) : null;
});

CompetitionStageAndMaterialPanel.displayName = 'CompetitionStageAndMaterialPanel';

const CompetitionSettingsPage = () => {
  const params = useParams<{ competitionUuid: string }>();
  const location = useLocation();
  const competitionUuid = params.competitionUuid || '';
  const initialNavigation = parseCompetitionSettingsNavigation(location.search);
  const [settings, setSettings] = useState<CompetitionSettingsRecord>();
  const [activeKey, setActiveKey] = useState<CompetitionSettingsModuleKey>(initialNavigation.section);
  const [registrationDetail, setRegistrationDetail] = useState<CompetitionSettingsRegistrationTab>(initialNavigation.registrationTab);
  const [stageDetail, setStageDetail] = useState<CompetitionSettingsStageTab>(initialNavigation.stageTab);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [storageSpaceOptions, setStorageSpaceOptions] = useState<StorageSpaceOption[]>([]);
  const [paymentProviderOptions, setPaymentProviderOptions] = useState<PaymentProviderOption[]>([]);
  const activePanelRef = useRef<CompetitionSettingsPanelHandle | null>(null);
  const fallbackDictOptions = useCompetitionDictFallbackOptions();
  const { options: categoryOptions } = useDictOptions(COMPETITION_CATEGORY_DICT, fallbackDictOptions.categoryOptions);
  const { options: levelOptions } = useDictOptions(COMPETITION_LEVEL_DICT, fallbackDictOptions.levelOptions);
  const materialStageTabs = useMemo(
    () => settings ? getCompetitionMaterialStageTabs(settings.competition) : [],
    [settings],
  );

  const updateNavigationUrl = useCallback((
    section: CompetitionSettingsModuleKey,
    detail?: CompetitionSettingsRegistrationTab | CompetitionSettingsStageTab,
    replace = false,
  ) => {
    const nextSearch = createCompetitionSettingsSearch(location.search, section, detail);
    if (nextSearch === location.search) {
      return;
    }
    const nextLocation = { pathname: location.pathname, search: nextSearch };
    if (replace) {
      history.replace(nextLocation);
    } else {
      history.push(nextLocation);
    }
  }, [location.pathname, location.search]);

  useEffect(() => {
    if (!settings) {
      return;
    }
    const fallbackStageTab = getCompetitionSettingsStageTabFallback(
      activeKey,
      stageDetail,
      materialStageTabs.map((tab) => tab.key),
    );
    if (!fallbackStageTab) {
      return;
    }
    setStageDetail(fallbackStageTab);
    updateNavigationUrl('stages', fallbackStageTab, true);
  }, [activeKey, materialStageTabs, settings, stageDetail, updateNavigationUrl]);

  useEffect(() => {
    const navigation = parseCompetitionSettingsNavigation(location.search);
    setActiveKey(navigation.section);
    setRegistrationDetail(navigation.registrationTab);
    setStageDetail(navigation.stageTab);

    const detail = navigation.section === 'registration'
      ? navigation.registrationTab
      : navigation.section === 'stages'
        ? navigation.stageTab
        : undefined;
    updateNavigationUrl(navigation.section, detail, true);
  }, [location.search, updateNavigationUrl]);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    Promise.all([
      getCompetitionSettings(competitionUuid),
      loadStorageSpaceOptions().catch(() => [] as StorageSpaceOption[]),
      loadConfiguredPaymentProviderOptions().catch(() => [] as PaymentProviderOption[]),
    ])
      .then(([result, nextStorageSpaceOptions, nextPaymentProviderOptions]) => {
        if (mounted) {
          setSettings(localizeLegacyCompetitionSettings(result));
          setStorageSpaceOptions(nextStorageSpaceOptions);
          setPaymentProviderOptions(nextPaymentProviderOptions);
        }
      })
      .catch((error) => showErrorMessage(error, formatMessage({ id: 'page.competition.settings.loadFailed', defaultMessage: 'Competition settings load failed' })))
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, [competitionUuid]);

  const activeConfigModuleKey: CompetitionSettingsConfigModuleKey | undefined = activeKey === 'registration'
    ? registrationDetail === 'documents' ? 'documents' : 'fields'
    : activeKey === 'stages'
      ? 'files'
      : activeKey === 'payments'
        ? 'payments'
        : undefined;
  const activeModule = activeConfigModuleKey
    ? competitionSettingsModules.find((item) => item.key === activeConfigModuleKey)
    : undefined;

  const flushActivePanel = useCallback(async () => {
    await activePanelRef.current?.flushPendingSave();
  }, []);

  const handleBack = useCallback(async () => {
    await flushActivePanel();
    history.push('/competitions/management');
  }, [flushActivePanel]);

  const handleSave = useCallback(async () => {
    if (!activePanelRef.current) {
      return;
    }
    setSaving(true);
    try {
      const saved = await activePanelRef.current.saveNow();
      if (saved) {
        message.success('保存成功');
      } else {
        message.warning('当前设置未保存，请检查必填项或错误提示');
      }
    } finally {
      setSaving(false);
    }
  }, []);

  const handleModuleChange = useCallback(async (nextKey: CompetitionSettingsModuleKey) => {
    if (nextKey === activeKey) {
      return;
    }
    await flushActivePanel();
    setActiveKey(nextKey);
    if (nextKey === 'registration') {
      setRegistrationDetail('MEMBER_FIELD');
      updateNavigationUrl(nextKey, 'MEMBER_FIELD');
      return;
    }
    if (nextKey === 'stages') {
      setStageDetail('timeline');
      updateNavigationUrl(nextKey, 'timeline');
      return;
    }
    updateNavigationUrl(nextKey);
  }, [activeKey, flushActivePanel, updateNavigationUrl]);

  const handleRegistrationDetailChange = useCallback(async (nextKey: string) => {
    const pendingSave = flushActivePanel();
    const nextDetail = nextKey as CompetitionSettingsRegistrationTab;
    setRegistrationDetail(nextDetail);
    updateNavigationUrl('registration', nextDetail);
    await pendingSave;
  }, [flushActivePanel, updateNavigationUrl]);

  const handleStageDetailChange = useCallback(async (nextKey: string) => {
    const pendingSave = flushActivePanel();
    const nextDetail = nextKey as CompetitionSettingsStageTab;
    setStageDetail(nextDetail);
    updateNavigationUrl('stages', nextDetail);
    await pendingSave;
  }, [flushActivePanel, updateNavigationUrl]);

  return (
    <ManagementPage
      title={formatMessage({ id: 'page.competition.settings.title', defaultMessage: '赛事配置' })}
      extra={
        <Space>
          <Button onClick={() => void handleBack()}>
            {formatMessage({ id: 'page.competition.settings.back', defaultMessage: '返回' })}
          </Button>
          <Button type="primary" loading={saving} disabled={loading || !settings} onClick={() => void handleSave()}>
            保存
          </Button>
        </Space>
      }
    >
      <ManagementPageBody>
        {loading ? (
          <Card loading />
        ) : settings ? (
          <div className="competition-settings-layout">
            <aside className="competition-settings-sidebar">
              <Typography.Title level={5} ellipsis={{ tooltip: settings.competition.title }}>
                {settings.competition.title}
              </Typography.Title>
              <Typography.Text type="secondary">
                {formatMessage(
                  { id: 'page.competition.settings.no', defaultMessage: '编号 {competitionNo}' },
                  { competitionNo: settings.competition.competitionNo || settings.competition.code },
                )}
              </Typography.Text>
              <Menu
                mode="inline"
                selectedKeys={[activeKey]}
                items={competitionSettingsMenuItems}
                onClick={({ key }) => void handleModuleChange(key as CompetitionSettingsModuleKey)}
              />
            </aside>
            <main className="competition-settings-content">
              {activeKey === 'basic' ? (
                <CompetitionBasicSettingsPanel
                  ref={activePanelRef}
                  competition={settings.competition}
                  categoryOptions={categoryOptions as Array<{ label: string; value: string }>}
                  levelOptions={levelOptions as Array<{ label: string; value: string }>}
                  onSaved={(competition) => setSettings({ ...settings, competition })}
                />
              ) : activeKey === 'registration' ? (
                <>
                  <Tabs
                    className="competition-settings-detail-tabs competition-settings-detail-tabs--top"
                    activeKey={registrationDetail}
                    items={[
                      { key: 'MEMBER_FIELD', label: '学生信息' },
                      { key: 'TEAM_FIELD', label: '团队信息' },
                      { key: 'PROJECT_FIELD', label: '项目信息' },
                      { key: 'INTELLECTUAL_PROPERTY', label: '知识产权信息' },
                      { key: 'documents', label: '报名须知与文书' },
                    ]}
                    onChange={(key) => void handleRegistrationDetailChange(key)}
                  />
                  {activeModule ? (
                    <ConfigModulePanel
                      key={activeModule.key}
                      ref={activePanelRef}
                      competitionUuid={settings.competition.uuid || competitionUuid}
                      module={activeModule}
                      items={getModuleItems(settings, activeModule.key)}
                      storageSpaceOptions={storageSpaceOptions}
                      fieldScope={registrationDetail === 'documents'
                        ? undefined
                        : registrationDetail === 'INTELLECTUAL_PROPERTY'
                          ? 'PROJECT_FIELD'
                          : registrationDetail}
                      fieldGroupLabel={registrationDetail === 'INTELLECTUAL_PROPERTY'
                        ? INTELLECTUAL_PROPERTY_GROUP_LABEL
                        : registrationDetail === 'PROJECT_FIELD'
                          ? '项目信息'
                          : undefined}
                      includeMemberFields={false}
                      onSaved={setSettings}
                    />
                  ) : null}
                </>
              ) : activeKey === 'stages' ? (
                activeModule ? (
                  <>
                    <Tabs
                      className="competition-settings-detail-tabs competition-settings-detail-tabs--top"
                      activeKey={stageDetail}
                      items={[
                        { key: 'timeline', label: '时间设置' },
                        ...materialStageTabs.map(({ key, label }) => ({ key, label })),
                      ]}
                      onChange={(key) => void handleStageDetailChange(key)}
                    />
                    <CompetitionStageAndMaterialPanel
                      ref={activePanelRef}
                      activeTab={stageDetail}
                      competition={settings.competition}
                      competitionUuid={settings.competition.uuid || competitionUuid}
                      module={activeModule}
                      items={getModuleItems(settings, activeModule.key)}
                      storageSpaceOptions={storageSpaceOptions}
                      onCompetitionSaved={(competition) => setSettings((current) => current ? { ...current, competition } : current)}
                      onSettingsSaved={setSettings}
                    />
                  </>
                ) : null
              ) : activeKey === 'payments' && activeModule ? (
                <CompetitionPaymentSettingsPanel
                  key={`${activeModule.key}-${settings.competition.id}`}
                  ref={activePanelRef}
                  competition={settings.competition}
                  competitionUuid={settings.competition.uuid || competitionUuid}
                  module={activeModule}
                  items={getModuleItems(settings, activeModule.key)}
                  storageSpaceOptions={storageSpaceOptions}
                  paymentProviderOptions={paymentProviderOptions}
                  onCompetitionSaved={(competition) => setSettings((current) => current
                    ? { ...current, competition }
                    : current)}
                  onSettingsSaved={setSettings}
                />
              ) : null}
            </main>
          </div>
        ) : (
          <Alert type="error" showIcon message={formatMessage({ id: 'page.competition.settings.notFound', defaultMessage: '未找到赛事配置' })} />
        )}
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
      await updateCompetition(record.id, normalizePayload({ ...recordToFormValues(record), status: nextStatus }));
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
        title: '赛事',
        dataIndex: 'title',
        search: false,
        minWidth: 260,
        render: (_, record) => (
          <Space className="competition-name-cell" direction="vertical" size={0}>
            <Typography.Text strong>{record.title}</Typography.Text>
            <span className="competition-name-cell__meta">{record.shortName || record.code}</span>
          </Space>
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
        render: (_, record) => resolveOptionLabel(levelLabelMap, record.competitionLevel || record.level) || '-',
      },
      {
        title: '收费',
        dataIndex: 'feeMode',
        search: false,
        width: 140,
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
        render: (value) => normalizeMojibakeText(value as string | null | undefined) || '-',
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
        render: (value) => {
          const locales = splitCompetitionLocales(typeof value === 'string' ? value : undefined);
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
        render: (_, record) => `${record.competitionStart || '-'}${record.competitionEnd ? ` - ${record.competitionEnd}` : ''}`,
      },
      {
        title: '参赛范围',
        dataIndex: 'participationScope',
        search: false,
        ellipsis: true,
        render: (_, record) => record.participationScope || record.location || '-',
      },
      {
        title: '标签',
        dataIndex: 'tags',
        search: false,
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
        render: (_, record) => (record.featured ? <Tag color="gold">推荐</Tag> : <Tag>普通</Tag>),
      },
      {
        title: '排序',
        dataIndex: 'sort',
        search: false,
        width: 80,
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

  if (/^\/competitions\/[^/]+\/settings$/.test(location.pathname)) {
    return <CompetitionSettingsPage />;
  }

  if (location.pathname === '/competitions/register/payment-result') {
    return <PaymentResultPage />;
  }

  if (location.pathname === '/competitions/register') {
    return <CompetitionRegistrationPage />;
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
          isMobile={responsive.isMobile}
          autoContentWidth
          scroll={{ x: 'max-content' }}
          tableLayout="auto"
          request={async (params) => {
            const response = await listCompetitions({
              keyword: typeof params.keyword === 'string' ? params.keyword : undefined,
              category: typeof params.category === 'string' ? params.category : undefined,
              locale: params.locale as string | undefined,
              status: params.status as CompetitionStatus | undefined,
              featured: parseFeaturedFilter(params.featured),
              pageNo: params.current,
              pageSize: params.pageSize,
            });
            return {
              data: response.records,
              total: response.total,
              success: true,
            };
          }}
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
