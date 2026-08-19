import { ArrowDownOutlined, ArrowUpOutlined, CheckCircleOutlined, DeleteOutlined, DownloadOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, RollbackOutlined, SettingOutlined, TeamOutlined, UploadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Alert, Avatar, Button, Card, Checkbox, DatePicker, Descriptions, Divider, Form, Image, Input, InputNumber, Menu, Modal, Popconfirm, Radio, Result, Select, Space, Spin, Steps, Switch, Tabs, Tag, Tooltip, Typography, Upload } from 'antd';
import type { DatePickerProps, TableProps, UploadFile } from 'antd';
import type { FormInstance } from 'antd';
import ImgCrop from 'antd-img-crop';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { history, useLocation, useModel, useParams } from '@umijs/max';
import { formatMessage } from '@/i18n/formatMessage';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import { XMarkdown } from '@ant-design/x-markdown';
import { useOptionalCompetitionWorkspace } from '@/features/competition-workspace/CompetitionWorkspaceContext';
import { CompetitionWorkspacePageFrame } from '@/features/competition-workspace/CompetitionWorkspacePageFrame';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { DataTable } from '@/features/table/DataTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { useDictOptions } from '@/hooks/useDictOptions';
import { useResponsive } from '@/hooks/useResponsive';
import { databaseMessage } from '@/i18n/databaseMessage';
import {
  createCompetition,
  createCompetitionDraft,
  confirmRegistration,
  createRegistrationPaymentOrder,
  deleteCompetition,
  deleteRegistration,
  getCompetition,
  getRegistration,
  getCompetitionSettings,
  getCompetitionStageForm,
  getRegistrationPaymentStatus,
  listRegistrationMaterials,
  listCompetitionStages,
  listCompetitions,
  listRegistrations,
  listRegistrationPaymentOptions,
  saveCompetitionSettingsModule,
  reconfirmRegistration,
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
  CompetitionStatus,
  CompetitionUpsertPayload,
} from '@/services/competition/types';
import { request, requestFile } from '@/services/common/request';
import type { FileObjectRecord, PaymentProviderSettings } from '@/types/api';
import ActivityRegistrationPage from '@/pages/competition/ActivityRegistrationPage';
import ExpertApplicationPage from '@/pages/competition/ExpertApplicationPage';
import PaymentResultPage from '@/pages/competition/PaymentResultPage';
import {
  getCompetitionCreateMissingFields,
  isBasicSettingsPageReadyToSave,
  isConfigModuleDraftSaveCurrent,
  isConfigModuleItemKeyDuplicate,
  isConfigModuleReadyToSave,
  isPaymentSettingsPageReadyToSave,
  isTimelineSettingsPageReadyToSave,
  mergeStageMaterialSaveItems,
  shouldValidateTeamMemberLimitsForPage,
  shouldHydrateConfigModuleDraft,
} from '@/pages/competition/competitionSettingsSave';
import {
  buildRegistrationCompetitionFallback,
  filterOpenRegistrationCompetitions,
  hasRegistrationCompetitionPricing,
  mergeRegistrationCompetitionOptions,
} from '@/pages/competition/utils/registrationCompetition';
import {
  buildRegistrationDocumentAcceptanceStorageKey,
  buildRegistrationDocumentCountdowns,
  getRegistrationDocumentAcceptanceKey,
  resolveAcceptedRegistrationDocumentKeys,
} from '@/pages/competition/utils/registrationDocumentAcceptance';
import { buildRegistrationDraftStorageKey } from '@/pages/competition/utils/registrationDraftStorageKey';
import { buildRegistrationDraftIdentifiers } from '@/pages/competition/utils/registrationDraftIdentifiers';
import {
  clearLocalRegistrationDraft,
  getRegistrationDraftUpdatedAt,
  hasNewerRegistrationDraft,
  nextRegistrationDraftUpdatedAt,
  readLocalRegistrationDraft,
  resolveNewestRegistrationDraft,
  writeLocalRegistrationDraft,
  type RegistrationDraftRestoreSource,
} from '@/pages/competition/utils/registrationDraftPersistence';
import {
  buildCompetitionMaterialFileStorageContext,
  buildCompetitionStorageKey,
  shouldResetCompetitionMaterialValues,
} from '@/pages/competition/utils/competitionMaterialFileStorage';
import {
  getMissingRequiredRegistrationMaterials,
  restoreRegistrationMaterialValues,
} from '@/pages/competition/utils/registrationMaterials';
import {
  resolveMaterialFilePreviewKind,
  type MaterialFilePreviewKind,
} from '@/pages/competition/utils/materialFilePreview';
import {
  buildRegistrationProjectExtraValues,
  getMissingRequiredIntellectualPropertyFields,
  hasRegistrationIntellectualPropertyContent,
  INTELLECTUAL_PROPERTY_ENTRIES_KEY,
  migrateRegistrationIntellectualPropertyValues,
  normalizeRegistrationIntellectualPropertyEntries,
} from '@/pages/competition/utils/registrationIntellectualProperties';
import {
  buildRegistrationPaymentResultUrl,
  calculateRegistrationPayableAmount,
  isRegistrationPaymentSuccessful,
  pickEnabledCollectedValues,
  retainAvailablePaymentProvider,
} from '@/pages/competition/utils/registrationCheckout';
import {
  getRegistrationStatusLabel,
  registrationStatusValueEnum,
} from '@/pages/competition/utils/registrationStatus';
import { normalizeCompetitionDraftBasicDefaults } from '@/pages/competition/utils/competitionDraftDefaults';
import {
  isChronologicalTimeRange,
  isTimeRangeAtOrAfterPreviousEnd,
  isTimeRangeWithinBounds,
} from '@/pages/competition/utils/competitionTimeline';
import { loadOptionalPreliminaryStageForm } from '@/pages/competition/utils/loadOptionalStageForm';
import {
  formatRegistrationYearValue,
  isRegistrationYearField,
  normalizeRegistrationDateValue,
} from '@/pages/competition/utils/registrationDateValue';
import {
  isSupportedRegistrationFieldValidationConfig,
  resolveRegistrationFieldValidationRule,
  validateRegistrationFieldValue,
} from '@/pages/competition/utils/registrationFieldValidation';
import {
  IMAGE_CROP_ASPECT_RATIO_OPTIONS,
  normalizeImageCropAspectRatio,
  resolveImageCropAspect,
} from '@/pages/competition/utils/imageCropAspectRatio';
import {
  REGISTRATION_WIZARD_FLOW_VERSION,
  isMissingPreliminaryMaterialsError,
  normalizeRegistrationWizardDraftStep,
  registrationWizardStep,
  registrationWizardStepItems,
  resolveAllowedRegistrationWizardStep,
  resolveRegistrationResumeStep,
  shouldLoadPreliminaryStageForm,
} from '@/pages/competition/utils/registrationWizardFlow';
import {
  buildFormalRegistrationListQuery,
  deleteRegistrationListEntry,
  REGISTRATION_LIST_PAGE_SIZE,
  saveRegistrationListEntry,
  shouldPaginateRegistrationList,
} from '@/pages/competition/utils/registrationListEditor';
import {
  isDeprecatedRegistrationContactField,
  removeDeprecatedRegistrationContactFields,
  resolveRegistrationFieldScope,
} from '@/pages/competition/utils/registrationFieldScope';
import {
  DEFAULT_STUDENT_MAX_MEMBERS,
  DEFAULT_STUDENT_MIN_MEMBERS,
  DEFAULT_TEACHER_MAX_MEMBERS,
  DEFAULT_TEACHER_MIN_MEMBERS,
  MAX_REGISTRATION_PARTICIPANTS_PER_TYPE,
  buildRegistrationParticipantLimitMetadata,
  filterRegistrationParticipants,
  findRegistrationParticipantSourceIndex,
  getRegistrationParticipantLimits,
  normalizeRegistrationParticipantType,
  type RegistrationParticipantLimits,
  type RegistrationParticipantType,
} from '@/pages/competition/utils/competitionParticipantConfig';
import {
  createCompetitionSettingsSearch,
  getCompetitionSettingsStageTabFallback,
  parseCompetitionSettingsNavigation,
  type CompetitionSettingsRegistrationTab,
  type CompetitionSettingsSectionKey,
  type CompetitionSettingsStageTab,
} from '@/pages/competition/utils/competitionSettingsNavigation';
import { AgreementMarkdownEditor } from '@/pages/settings/personalization/components/AgreementMarkdownEditor';
import { CompetitionPaymentStep } from '@/pages/competition/components/CompetitionPaymentStep';
import CompetitionAwardSettingsPanel from '@/pages/competition/components/CompetitionAwardSettingsPanel';
import type { CompetitionSettingsPanelHandle } from '@/pages/competition/components/CompetitionSettingsPanelHandle';
import { message, modal } from '@/theme/antdFeedbackBridge';
import { API_OPTS, extractErrorMessage, showErrorMessage } from '@/utils/errorMessage';
import { sanitizeMarkdownInput } from '@/utils/markdownSecurity';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import {
  DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS,
  isIndependentMemberRoleField,
  normalizeIndependentMemberRoleMetadata,
  prioritizeRequiredMemberNameField,
  reorderScopedConfigItems,
} from './utils/competitionFieldConfig';
import {
  deriveCompetitionOverallWindow,
  preserveCompetitionTimelineSnapshot,
  sanitizeCompetitionSchedules,
  type CompetitionJsonSchedule,
  type CompetitionScheduleFormItem,
  type CompetitionTimeMode,
} from './competitionSchedulePayload';
import './CompetitionPage.css';

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
  stageCode?: string;
};

type RegistrationTeamMemberDraft = RegistrationSnapshotMemberPayload;

type RegistrationTeamDraft = RegistrationSnapshotTeamPayload & {
  initialMembers?: RegistrationTeamMemberDraft[];
};

type RegistrationMemberEditorKey = {
  participantType: RegistrationParticipantType;
  participantIndex: number | 'new';
};
const COMPETITION_REGISTRATION_SCOPE_RESOURCE = 'competition:registration';
const fallbackRegistrationTeamTypeOptions = () => [
  { value: 'GENERAL', label: databaseMessage('competition.teamType.general') },
  { value: 'DEV', label: databaseMessage('competition.teamType.development') },
  { value: 'COMPETITION', label: databaseMessage('competition.teamType.competition') },
  { value: 'CLUB', label: databaseMessage('competition.teamType.club') },
  { value: 'OTHER', label: databaseMessage('competition.teamType.other') },
];
const emptyRegistrationTeamMember = (
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

const getCompetitionMaterialStageTabs = (competition: CompetitionRecord): CompetitionMaterialStageTab[] =>
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

type CompetitionRegistrationDraftStorage = {
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

type RegistrationDraftSyncStatus = 'IDLE' | 'SAVING_LOCAL' | 'LOCAL_ONLY' | 'SYNCING' | 'SYNCED' | 'SYNC_ERROR';

class RegistrationDraftCloudSyncError extends Error {
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

type CompetitionRegistrationListRecord = CompetitionRegistrationRecord & {
  isCurrentUserDraft?: boolean;
  draftCompetitionTitle?: string;
  draftTeamName?: string;
  draftProjectTitle?: string;
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

const useCompetitionDictFallbackOptions = () => ({
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

const organizerLabel = (organizer: CompetitionOrganizerFormItem) =>
  [organizer.role, organizer.name].map(trimOptional).filter(Boolean).join('：');

const mojibakeReplacementPattern = new RegExp(`${String.fromCharCode(0xfffd)}\\??`, 'g');

const normalizeMojibakeText = (value?: string | null) =>
  trimOptional(value)?.replace(mojibakeReplacementPattern, '');

const normalizePayload = (
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

const recordToFormValues = (record: CompetitionRecord): Partial<CompetitionFormValues> => {
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

const CompetitionDateTimeRangePicker = ({
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

const toPositiveId = (value: unknown) => {
  const id = Number(value);
  return Number.isFinite(id) && id > 0 ? id : undefined;
};

const getCompleteTimeRange = (
  range?: CompetitionFormValues['registrationRange'] | CompetitionScheduleFormItem['materialRange'],
): [Dayjs, Dayjs] | undefined => {
  if (!Array.isArray(range) || range.length !== 2) {
    return undefined;
  }
  const start = toValidDayjs(range[0]);
  const end = toValidDayjs(range[1]);
  return start && end ? [start, end] : undefined;
};

const getScheduleRangePickerBounds = (
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

const isOutsideScheduleRangePickerBounds = (
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

interface StoredUserDraft<T> {
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

const clearUserDraft = async (draftKey: string) => {
  await request<void>(`/v2/user-drafts/${draftKey}`, {
    method: 'DELETE',
    silent: true,
  });
};

const readCompetitionCreateDraft = () => readUserDraft<CompetitionCreateDraftStorage>(COMPETITION_CREATE_DRAFT_STORAGE_KEY);
const writeCompetitionCreateDraft = (draft: CompetitionCreateDraftStorage) => writeUserDraft(COMPETITION_CREATE_DRAFT_STORAGE_KEY, draft);
const clearCompetitionCreateDraft = () => clearUserDraft(COMPETITION_CREATE_DRAFT_STORAGE_KEY);
const readCompetitionRegistrationDraftEnvelope = (draftKey: string) => readUserDraftEnvelope<CompetitionRegistrationDraftStorage>(draftKey);
const writeCompetitionRegistrationDraft = (draftKey: string, draft: CompetitionRegistrationDraftStorage) => writeUserDraft(draftKey, draft);
const clearCompetitionRegistrationDraft = (draftKey: string) => clearUserDraft(draftKey);
const readCompetitionRegistrationDocumentAcceptance = (competitionUuid: string) =>
  readUserDraft<CompetitionRegistrationDocumentAcceptanceStorage>(
    buildRegistrationDocumentAcceptanceStorageKey(competitionUuid),
  );
const writeCompetitionRegistrationDocumentAcceptance = (
  competitionUuid: string,
  acceptedDocumentKeys: string[],
) => writeUserDraft(
  buildRegistrationDocumentAcceptanceStorageKey(competitionUuid),
  { acceptedDocumentKeys, savedAt: Date.now() },
);

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

const getCompetitionMaterialFileAccept = (field: CompetitionStageFormField) => {
  const format = (field.fileFormat || 'ANY').toUpperCase();
  const config = competitionMaterialFileFormatConfig[format];
  return config ? config.extensions.map((extension) => `.${extension}`).join(',') : undefined;
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
      stageCode: field.stageCode || config.metadata.stageCode,
    };
  });
};

const MaterialFileUploadInput = ({
  field,
  competitionUuid,
  value,
  onChange,
}: {
  field: CompetitionStageFormField;
  competitionUuid?: string;
  value?: number;
  onChange?: (value?: number) => void;
}) => {
  const [uploading, setUploading] = useState(false);
  const [fileRecord, setFileRecord] = useState<FileObjectRecord>();
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewRecord, setPreviewRecord] = useState<FileObjectRecord>();
  const [previewKind, setPreviewKind] = useState<MaterialFilePreviewKind>('UNSUPPORTED');
  const [previewText, setPreviewText] = useState('');
  const [previewHtml, setPreviewHtml] = useState('');
  const [previewError, setPreviewError] = useState('');
  const [previewUrl, setPreviewUrl] = useState('');
  const previewUrlRef = useRef('');
  const maxSizeMb = Number(field.maxSizeMb) || 20;
  const fileFormatLabel = competitionMaterialFileFormatConfig[(field.fileFormat || 'ANY').toUpperCase()]?.label || '任意格式文件';
  const uploadedFileList: UploadFile[] = value ? [{
    uid: String(value),
    name: fileRecord?.originalFileName || '已上传文件',
    status: 'done',
    url: fileRecord ? normalizeUploadUrl(fileRecord.previewUrl || fileRecord.publicUrl) : undefined,
  }] : [];

  const clearPreviewUrl = useCallback(() => {
    if (previewUrlRef.current) {
      window.URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = '';
    }
    setPreviewUrl('');
  }, []);

  const closePreview = useCallback(() => {
    setPreviewOpen(false);
    setPreviewLoading(false);
    setPreviewRecord(undefined);
    setPreviewKind('UNSUPPORTED');
    setPreviewText('');
    setPreviewHtml('');
    setPreviewError('');
    clearPreviewUrl();
  }, [clearPreviewUrl]);

  const handlePreview = useCallback(async () => {
    if (!value) {
      return;
    }

    setPreviewOpen(true);
    setPreviewLoading(true);
    setPreviewText('');
    setPreviewHtml('');
    setPreviewError('');
    clearPreviewUrl();
    try {
      const record = fileRecord || await request<FileObjectRecord>(`/v1/files/${value}`, {
        method: 'GET',
        silent: true,
      });
      setFileRecord(record);
      setPreviewRecord(record);
      const kind = resolveMaterialFilePreviewKind(record);
      setPreviewKind(kind);
      if (kind === 'UNSUPPORTED') {
        return;
      }

      let blob: Blob;
      try {
        blob = await requestFile(
          kind === 'EXTRACTED_TEXT'
            ? `/v1/files/${value}/text-preview`
            : kind === 'OFFICE_HTML'
              ? `/v1/files/${value}/html-preview`
              : `/v1/files/${value}/preview`,
          {
            method: 'GET',
            silent: true,
          },
        );
      } catch (error) {
        if (kind !== 'OFFICE_HTML') {
          throw error;
        }
        blob = await requestFile(`/v1/files/${value}/text-preview`, {
          method: 'GET',
          silent: true,
        });
        setPreviewKind('EXTRACTED_TEXT');
        setPreviewText(await blob.text());
        message.warning('版式预览暂不可用，已切换为文本预览');
        return;
      }
      if (kind === 'EXTRACTED_TEXT') {
        setPreviewText(await blob.text());
        return;
      }
      if (kind === 'OFFICE_HTML') {
        setPreviewHtml(await blob.text());
        return;
      }
      const objectUrl = window.URL.createObjectURL(blob);
      previewUrlRef.current = objectUrl;
      setPreviewUrl(objectUrl);
    } catch (error) {
      setPreviewError(extractErrorMessage(error, '文件预览加载失败，请下载原文件查看'));
      showErrorMessage(error, '文件预览加载失败');
    } finally {
      setPreviewLoading(false);
    }
  }, [clearPreviewUrl, fileRecord, value]);

  useEffect(() => {
    if (!value) {
      setFileRecord(undefined);
      return;
    }
    let active = true;
    void request<FileObjectRecord>(`/v1/files/${value}`, { method: 'GET', silent: true })
      .then((record) => {
        if (active) setFileRecord(record);
      })
      .catch(() => {
        if (active) setFileRecord(undefined);
      });
    return () => {
      active = false;
    };
  }, [value]);

  useEffect(() => () => {
    if (previewUrlRef.current) {
      window.URL.revokeObjectURL(previewUrlRef.current);
    }
  }, []);

  return (
    <>
      <Upload.Dragger
        accept={getCompetitionMaterialFileAccept(field)}
        maxCount={1}
        fileList={uploadedFileList}
        showUploadList={{ showPreviewIcon: true, showRemoveIcon: true }}
        disabled={uploading}
        onPreview={() => void handlePreview()}
        onRemove={() => {
          closePreview();
          setFileRecord(undefined);
          onChange?.(undefined);
          return true;
        }}
        beforeUpload={async (file) => {
          const competitionStorageKey = buildCompetitionStorageKey(competitionUuid);
          const storageContext = buildCompetitionMaterialFileStorageContext(
            competitionUuid,
            field.stageCode,
            field.key,
            competitionStorageKey,
          );
          if (!storageContext || !competitionStorageKey) {
            message.error('赛事标识缺失，无法按比赛隔离存储文件');
            return Upload.LIST_IGNORE;
          }
          const validationMessage = validateCompetitionMaterialFile(file as File, field);
          if (validationMessage) {
            message.error(validationMessage);
            return Upload.LIST_IGNORE;
          }
          const formData = new FormData();
          formData.append('file', file as File);
          formData.append('category', '赛事材料');
          formData.append('tags', storageContext.tags);
          if (storageContext.directory) {
            formData.append('directory', storageContext.directory);
          }
          if (field.label) {
            formData.append('remark', field.label);
          }
          formData.append('bucket', competitionStorageKey);
          setUploading(true);
          try {
            const uploaded = await request<FileObjectRecord>('/v1/files/upload', {
              method: 'POST',
              headers: {},
              data: formData,
              silent: true,
            });
            setFileRecord(uploaded);
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
        <p className="ant-upload-drag-icon">
          <UploadOutlined />
        </p>
        <p className="ant-upload-text">
          {uploading ? '文件上传中...' : value ? '拖拽文件到这里，或点击重新上传' : '拖拽文件到这里，或点击上传'}
        </p>
        <p className="ant-upload-hint">
          支持{fileFormatLabel}，单个文件不超过 {maxSizeMb}MB
        </p>
      </Upload.Dragger>

      <Modal
        title={previewRecord?.originalFileName || '文件预览'}
        open={previewOpen}
        width={960}
        centered
        destroyOnHidden
        footer={(
          <Space>
            {value ? (
              <Button
                icon={<DownloadOutlined />}
                href={`/api/v1/files/${value}/download`}
                target="_blank"
                rel="noopener noreferrer"
              >
                下载原文件
              </Button>
            ) : null}
            <Button onClick={closePreview}>关闭</Button>
          </Space>
        )}
        onCancel={closePreview}
      >
        <Spin spinning={previewLoading} tip="文件预览加载中">
          <div className="competition-material-preview">
            {previewError ? (
              <Result
                status="error"
                title="文件预览失败"
                subTitle={previewError}
              />
            ) : null}
            {!previewError && previewKind === 'UNSUPPORTED' && previewRecord ? (
              <Result
                status="info"
                title="当前格式暂不支持在线预览"
                subTitle="可以下载原文件，使用本地应用打开查看。"
              />
            ) : null}
            {!previewError && previewRecord && previewUrl && previewKind === 'IMAGE' ? (
              <Image
                src={previewUrl}
                alt={previewRecord.originalFileName}
                preview={false}
                className="competition-material-preview__image"
              />
            ) : null}
            {!previewError && previewRecord && previewUrl && previewKind === 'PDF' ? (
              <iframe
                title={previewRecord.originalFileName}
                src={`${previewUrl}#view=FitH`}
                className="competition-material-preview__frame"
              />
            ) : null}
            {!previewError && previewRecord && previewHtml && previewKind === 'OFFICE_HTML' ? (
              <iframe
                title={`${previewRecord.originalFileName} 版式预览`}
                srcDoc={previewHtml}
                sandbox=""
                referrerPolicy="no-referrer"
                className="competition-material-preview__frame"
              />
            ) : null}
            {!previewError && previewKind === 'EXTRACTED_TEXT' && previewText ? (
              <div className="competition-material-preview__document">
                <Alert
                  type="info"
                  showIcon
                  title="当前为文档文本预览，复杂排版、图片和批注请下载原文件查看。"
                />
                <pre className="competition-material-preview__text">{previewText}</pre>
              </div>
            ) : null}
          </div>
        </Spin>
      </Modal>
    </>
  );
};

const buildCurrentUserRegistrationDraftRecord = (
  draft: CompetitionRegistrationDraftStorage | undefined,
  ownerUserId?: number,
): CompetitionRegistrationListRecord | undefined => {
  const values = draft?.values || {};
  const hasDraftProgress = Boolean(
    (draft?.currentStep || 0) > 0
      || draft?.acceptedDocumentKeys?.length
      || hasCompetitionRegistrationDraftContent(values),
  );
  if (!draft || draft.registrationId || !hasDraftProgress) {
    return undefined;
  }

  const generatedIdentifiers = buildRegistrationDraftIdentifiers(
    draft.savedAt,
    draft.competitionUuid || toPositiveId(values.competitionId) || 'registration-draft',
  );

  return {
    id: 0,
    registrationNo: draft.registrationNo || generatedIdentifiers.registrationNo,
    competitionId: toPositiveId(values.competitionId) || 0,
    teamId: toPositiveId(values.teamId) || 0,
    projectId: toPositiveId(values.projectId) || 0,
    ownerUserId,
    status: 'DRAFT',
    feeMode: 'TEAM',
    entryFeeMinor: 0,
    memberCount: filterRegistrationParticipants(values.newTeam?.initialMembers || [], 'STUDENT').length,
    payableAmountMinor: 0,
    currency: 'CNY',
    participantNo: draft.participantNo || generatedIdentifiers.participantNo,
    createdAt: draft.savedAt ? new Date(draft.savedAt).toISOString() : undefined,
    updatedAt: draft.savedAt ? new Date(draft.savedAt).toISOString() : undefined,
    isCurrentUserDraft: true,
    draftCompetitionTitle: draft.competitionTitle,
    draftTeamName: trimOptional(values.newTeamName),
    draftProjectTitle: trimOptional(values.newProjectTitle),
  };
};

type RegistrationCollectedField = {
  scope?: Extract<CompetitionConfigItemType, 'REGISTRATION_FIELD' | 'TEAM_FIELD' | 'MEMBER_FIELD' | 'TEACHER_FIELD' | 'PROJECT_FIELD'>;
  itemKey: string;
  title: string;
  fieldType?: string;
  placeholder?: string;
  required?: boolean;
  options?: string;
  validationRule?: string;
  groupLabel?: string;
  cropAspectRatio?: string;
};

type RegistrationCollectedFieldSplit = {
  allFields: RegistrationCollectedField[];
  customFields: RegistrationCollectedField[];
  overrides: Map<string, RegistrationCollectedField>;
};

type RegistrationImageFieldInputProps = {
  value?: string;
  onChange?: (value?: string) => void;
  cropAspectRatio?: string;
};

const validateRegistrationImageFile = (file: File) => {
  if (!file.type.startsWith('image/')) {
    message.error('请上传图片文件');
    return false;
  }
  if (file.size > 20 * 1024 * 1024) {
    message.error('请上传小于 20MB 的图片');
    return false;
  }
  return true;
};

const RegistrationImageFieldInput = ({
  value,
  onChange,
  cropAspectRatio,
}: RegistrationImageFieldInputProps) => {
  const [uploading, setUploading] = useState(false);
  return (
    <Space orientation="vertical" size={8}>
      {value ? <Image width={96} height={96} src={normalizeUploadUrl(value)} alt="已上传图片" /> : null}
      <Space>
        <ImgCrop
          aspect={resolveImageCropAspect(cropAspectRatio)}
          cropShape="rect"
          showGrid
          zoomSlider
          rotationSlider
          modalTitle={`裁切图片（${normalizeImageCropAspectRatio('IMAGE', cropAspectRatio)}）`}
          modalOk="确认上传"
          modalCancel="取消"
          modalWidth={520}
          beforeCrop={validateRegistrationImageFile}
        >
          <Upload
            accept="image/*"
            showUploadList={false}
            disabled={uploading}
            beforeUpload={async (file) => {
              if (!validateRegistrationImageFile(file)) {
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
        </ImgCrop>
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
    validationRule: resolveRegistrationFieldValidationRule(
      metadata.fieldType,
      metadata.validationRule,
      scope,
      item.itemKey,
    ),
    groupLabel: metadata.groupLabel,
    cropAspectRatio: normalizeImageCropAspectRatio(metadata.fieldType, metadata.cropAspectRatio),
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
  TEACHER_FIELD: {
    memberName: ['membername', 'teachername', 'name'],
    employeeNo: ['employeeno', 'teacherno', 'memberno'],
    departmentName: ['departmentname', 'department', 'organization'],
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
  scope: Extract<CompetitionConfigItemType, 'REGISTRATION_FIELD' | 'TEAM_FIELD' | 'MEMBER_FIELD' | 'TEACHER_FIELD' | 'PROJECT_FIELD'>,
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

const buildCollectedFieldRule = (field: RegistrationCollectedField) => {
  return [
    ...(field.required ? [{ required: true, message: `请输入${field.title}` }] : []),
    {
      validator: async (_: unknown, value: unknown) => {
        const validationError = validateRegistrationFieldValue(
          field.fieldType,
          field.validationRule,
          field.title,
          value,
          field.scope,
          field.itemKey,
        );
        if (validationError) {
          throw new Error(validationError);
        }
      },
    },
  ];
};

type RegistrationDatePickerProps = Omit<DatePickerProps, 'value'> & {
  value?: unknown;
};

const RegistrationDatePicker = ({ value, ...props }: RegistrationDatePickerProps) => (
  <DatePicker {...props} value={normalizeRegistrationDateValue(value)} />
);

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
  validationRule: resolveRegistrationFieldValidationRule('TEXT', 'NONE', scope, itemKey),
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
      return <RegistrationImageFieldInput cropAspectRatio={field.cropAspectRatio} />;
    case 'DATE': {
      const yearOnly = isRegistrationYearField(field.itemKey);
      return (
        <RegistrationDatePicker
          style={{ width: '100%' }}
          picker={yearOnly ? 'year' : undefined}
          format={yearOnly ? 'YYYY' : undefined}
          placeholder={placeholder}
        />
      );
    }
    case 'SELECT':
      return <Select options={parseConfigFieldOptions(field.options)} placeholder={placeholder} />;
    case 'MULTI_SELECT':
      return <Select mode="multiple" options={parseConfigFieldOptions(field.options)} placeholder={placeholder} />;
    case 'MOBILE':
      return <Input inputMode="numeric" placeholder={placeholder} maxLength={11} />;
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
  const memberScope = field.scope === 'TEACHER_FIELD' ? 'TEACHER_FIELD' : 'MEMBER_FIELD';
  return resolveStandardCollectedFieldKey(memberScope, field.itemKey) as keyof Pick<
    RegistrationTeamMemberDraft,
    'memberName' | 'employeeNo' | 'departmentName' | 'role' | 'remark'
  > | undefined;
};

const getMemberCollectedFieldValue = (member: RegistrationTeamMemberDraft, field: RegistrationCollectedField) => {
  const standardFieldKey = resolveMemberStandardFieldKey(field);
  return standardFieldKey ? member[standardFieldKey] : member.extraValues?.[field.itemKey];
};

const getMemberCollectedFieldFormName = (field: RegistrationCollectedField) => {
  const standardFieldKey = resolveMemberStandardFieldKey(field);
  return standardFieldKey || ['extraValues', field.itemKey];
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
    .flatMap<RegistrationSnapshotMemberPayload>((member) => {
      const collectedValues = {
        memberName: normalizeSnapshotValue(member.memberName) as string | undefined,
        employeeNo: normalizeSnapshotValue(member.employeeNo) as string | undefined,
        departmentName: normalizeSnapshotValue(member.departmentName) as string | undefined,
        role: normalizeSnapshotValue(member.role) as string | undefined,
        remark: normalizeSnapshotValue(member.remark) as string | undefined,
        extraValues: normalizeSnapshotValue(member.extraValues) as Record<string, unknown> | undefined,
      };
      return hasCollectedValue(collectedValues)
        ? [{
            participantType: normalizeRegistrationParticipantType(member.participantType),
            ...collectedValues,
          }]
        : [];
    });

const registrationStatusColor: Record<string, string> = {
  DRAFT: 'default',
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
  return <Tag color={registrationStatusColor[normalized] || 'default'}>{getRegistrationStatusLabel(normalized)}</Tag>;
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

const getRegistrationCollectedFieldDisplayText = (field: RegistrationCollectedField, value: unknown) => {
  if (!hasCollectedValue(value)) return '-';
  const fieldType = (field.fieldType || 'TEXT').toUpperCase();
  if (fieldType === 'ROLE') {
    return resolveOptionLabel(
      buildOptionLabelMap(parseConfigFieldOptions(field.options || DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS)),
      value,
    ) || '-';
  }
  if (fieldType === 'SELECT' || fieldType === 'MULTI_SELECT') {
    return resolveOptionLabel(buildOptionLabelMap(parseConfigFieldOptions(field.options)), value) || '-';
  }
  if (fieldType === 'DATE' && isRegistrationYearField(field.itemKey)) {
    return formatRegistrationYearValue(value) || '-';
  }
  return normalizeDisplayText(normalizeSnapshotValue(value)) || '-';
};

const renderCollectedFieldReviewValue = (field: RegistrationCollectedField, value: unknown) => {
  if (!hasCollectedValue(value)) return <Typography.Text type="secondary">未填写</Typography.Text>;
  if ((field.fieldType || 'TEXT').toUpperCase() === 'IMAGE' && typeof value === 'string') {
    return <Image width={72} height={72} src={normalizeUploadUrl(value)} alt={field.title} />;
  }
  return <Typography.Text>{getRegistrationCollectedFieldDisplayText(field, value)}</Typography.Text>;
};

function buildRegistrationReviewColumns<T extends object>(
  fields: RegistrationCollectedField[],
  getValue: (record: T, field: RegistrationCollectedField) => unknown,
): NonNullable<TableProps<T>['columns']> {
  return fields.map((field) => ({
    title: field.title,
    key: field.itemKey,
    width: 160,
    render: (_: unknown, record: T) => {
      const value = getValue(record, field);
      if ((field.fieldType || 'TEXT').toUpperCase() === 'IMAGE' && typeof value === 'string') {
        return (
          <Image
            width={40}
            height={40}
            src={normalizeUploadUrl(value)}
            alt={field.title}
            className="competition-registration-table-image"
          />
        );
      }
      const displayText = getRegistrationCollectedFieldDisplayText(field, value);
      return (
        <Tooltip title={displayText === '-' ? undefined : displayText}>
          <Typography.Text className="competition-registration-table-cell" ellipsis>
            {displayText}
          </Typography.Text>
        </Tooltip>
      );
    },
  }));
}

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
  return {
    ...rest,
    initialMembers: normalizeRegistrationMembers(rest.initialMembers),
  };
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
  const members = values.newTeam?.initialMembers || [];
  const students = filterRegistrationParticipants(members, 'STUDENT');
  return resolveAllowedRegistrationWizardStep(requestedStep, {
    competitionReady: Boolean(toPositiveId(values.competitionId) && documentsAccepted),
    teamReady: Boolean(trimOptional(values.newTeamName) && students.length),
    projectReady: Boolean(activeRegistrationId || toPositiveId(values.projectId) || trimOptional(values.newProjectTitle)),
    hasActiveRegistration: Boolean(activeRegistrationId),
  });
};

const CompetitionRegistrationPage = () => {
  const { initialState } = useModel('@@initialState');
  const currentUserId = initialState?.currentUser?.userId;
  const registrationDraftStorageKey = useMemo(
    () => buildRegistrationDraftStorageKey(currentUserId),
    [currentUserId],
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
  const [registrationSettingsStatus, setRegistrationSettingsStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');
  const [registrationSettingsReloadRevision, setRegistrationSettingsReloadRevision] = useState(0);
  const [participantLimits, setParticipantLimits] = useState<RegistrationParticipantLimits>({
    studentMinMembers: DEFAULT_STUDENT_MIN_MEMBERS,
    studentMaxMembers: DEFAULT_STUDENT_MAX_MEMBERS,
    teacherMinMembers: DEFAULT_TEACHER_MIN_MEMBERS,
    teacherMaxMembers: DEFAULT_TEACHER_MAX_MEMBERS,
  });
  const [stageMaterialConfigs, setStageMaterialConfigs] = useState<CompetitionConfigItem[]>([]);
  const [registrationDocumentsLoading, setRegistrationDocumentsLoading] = useState(false);
  const [documentReadingCountdowns, setDocumentReadingCountdowns] = useState<Record<string, number>>({});
  const [acceptedDocumentKeys, setAcceptedDocumentKeys] = useState<string[]>([]);
  const [registrationDocumentsCompetitionUuid, setRegistrationDocumentsCompetitionUuid] = useState<string>();
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
  const [registrationDraftSyncStatus, setRegistrationDraftSyncStatus] = useState<RegistrationDraftSyncStatus>('IDLE');
  const [registrationDraftHydrated, setRegistrationDraftHydrated] = useState(false);
  const [registrationCompetitionFallback, setRegistrationCompetitionFallback] = useState<CompetitionRecord>();
  const [teamAvatarUploading, setTeamAvatarUploading] = useState(false);
  const [projectAvatarUploading, setProjectAvatarUploading] = useState(false);
  const confirmedTeamIdRef = useRef<number | undefined>(undefined);
  const confirmedProjectIdRef = useRef<number | undefined>(undefined);
  const registrationDraftSaveTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const pendingLocalRegistrationDraftRef = useRef<{
    draft: CompetitionRegistrationDraftStorage;
    updatedAt: number;
  }>();
  const latestRegistrationDraftRef = useRef<CompetitionRegistrationDraftStorage | undefined>(undefined);
  const registrationDraftRestoreNoticeRef = useRef<RegistrationDraftRestoreSource>();
  const registrationDraftHydratedKeyRef = useRef<string>();
  const [form] = Form.useForm<RegistrationFormValues>();
  const [memberForm] = Form.useForm<RegistrationTeamMemberDraft>();
  const [intellectualPropertyForm] = Form.useForm<Record<string, unknown>>();
  const [memberEditorKey, setMemberEditorKey] = useState<RegistrationMemberEditorKey>();
  const [intellectualPropertyEditorIndex, setIntellectualPropertyEditorIndex] = useState<number | 'new'>();
  const selectedCompetitionId = Form.useWatch('competitionId', { form, preserve: true });
  const newTeamAvatarUrl = Form.useWatch(['newTeam', 'avatarUrl'], form);
  const newProjectImageUrl = Form.useWatch('newProjectImageUrl', form);
  const newProjectExtraValues = Form.useWatch('newProjectExtraValues', { form, preserve: true }) as Record<string, unknown> | undefined;
  const registrationMaterialValues = Form.useWatch('materials', { form, preserve: true }) as Record<string, unknown> | undefined;
  const watchedRegistrationParticipants = Form.useWatch(
    ['newTeam', 'initialMembers'],
    { form, preserve: true },
  ) as RegistrationTeamMemberDraft[] | undefined;
  const registrationParticipants = useMemo(
    () => watchedRegistrationParticipants || [],
    [watchedRegistrationParticipants],
  );
  const registrationStudents = useMemo(
    () => filterRegistrationParticipants(registrationParticipants, 'STUDENT'),
    [registrationParticipants],
  );
  const registrationTeachers = useMemo(
    () => filterRegistrationParticipants(registrationParticipants, 'TEACHER'),
    [registrationParticipants],
  );
  const registrationCompetitionOptions = useMemo(
    () => mergeRegistrationCompetitionOptions(
      filterOpenRegistrationCompetitions(competitions),
      registrationCompetitionFallback,
    ),
    [competitions, registrationCompetitionFallback],
  );
  const registrationCompetitionCatalog = useMemo(
    () => mergeRegistrationCompetitionOptions(competitions, registrationCompetitionFallback),
    [competitions, registrationCompetitionFallback],
  );
  const fields = useMemo(
    () => enrichCompetitionStageFormFields(parseFormFields(stageForm), stageMaterialConfigs),
    [stageForm, stageMaterialConfigs],
  );
  const missingRequiredMaterialFields = useMemo(
    () => getMissingRequiredRegistrationMaterials(fields, registrationMaterialValues || {}),
    [fields, registrationMaterialValues],
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
  const teacherFieldSplit = useMemo(
    () => splitConfiguredRegistrationFields(registrationFields, 'TEACHER_FIELD'),
    [registrationFields],
  );
  const projectFieldSplit = useMemo(
    () => splitConfiguredRegistrationFields(registrationFields, 'PROJECT_FIELD'),
    [registrationFields],
  );
  const effectiveStudentRegistrationFields = useMemo(
    () => prioritizeRequiredMemberNameField(
      memberFieldSplit.allFields,
      requiredSystemRegistrationField('MEMBER_FIELD', 'memberName', '学生姓名'),
    ),
    [memberFieldSplit.allFields],
  );
  const effectiveTeacherRegistrationFields = useMemo(
    () => prioritizeRequiredMemberNameField(
      teacherFieldSplit.allFields,
      requiredSystemRegistrationField('TEACHER_FIELD', 'memberName', '指导老师姓名'),
    ),
    [teacherFieldSplit.allFields],
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
  const projectCustomFields = useMemo(
    () => projectFieldSplit.customFields.filter((field) => field.groupLabel !== INTELLECTUAL_PROPERTY_GROUP_LABEL),
    [projectFieldSplit.customFields],
  );
  const intellectualPropertyFields = useMemo(
    () => projectFieldSplit.customFields.filter((field) => field.groupLabel === INTELLECTUAL_PROPERTY_GROUP_LABEL),
    [projectFieldSplit.customFields],
  );
  const intellectualPropertyFieldKeys = useMemo(
    () => intellectualPropertyFields.map((field) => field.itemKey),
    [intellectualPropertyFields],
  );
  const intellectualPropertyEntries = useMemo(
    () => normalizeRegistrationIntellectualPropertyEntries(
      newProjectExtraValues,
      intellectualPropertyFieldKeys,
    ),
    [intellectualPropertyFieldKeys, newProjectExtraValues],
  );
  const missingRequiredEvidenceFields = useMemo(
    () => getMissingRequiredIntellectualPropertyFields(intellectualPropertyFields, intellectualPropertyEntries),
    [intellectualPropertyEntries, intellectualPropertyFields],
  );
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
    const dateParticipantFields = {
      STUDENT: effectiveStudentRegistrationFields.filter((field) => field.fieldType?.toUpperCase() === 'DATE'),
      TEACHER: effectiveTeacherRegistrationFields.filter((field) => field.fieldType?.toUpperCase() === 'DATE'),
    };
    if ((dateParticipantFields.STUDENT.length || dateParticipantFields.TEACHER.length) && currentMembers.length) {
      let changed = false;
      const nextMembers = currentMembers.map((member) => dateParticipantFields[
        normalizeRegistrationParticipantType(member.participantType)
      ].reduce((current, field) => {
        const currentValue = getMemberCollectedFieldValue(current, field);
        const nextValue = toDateValue(currentValue);
        if (nextValue === currentValue) return current;
        changed = true;
        return setMemberCollectedFieldValue(current, field, nextValue);
      }, member));
      if (changed) form.setFieldValue(['newTeam', 'initialMembers'], nextMembers);
    }
  }, [effectiveStudentRegistrationFields, effectiveTeacherRegistrationFields, form, projectFieldSplit.customFields, registrationScopeFields, teamFieldSplit.customFields]);
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
  const buildCurrentRegistrationDraftState = useCallback((
    nextValues: Partial<RegistrationFormValues> = collectRegistrationValues(),
    nextStep = step,
    nextAcceptedDocumentKeys = acceptedDocumentKeys,
    nextRegistrationId = registrationId,
    nextPaymentStatus = paymentStatus,
    nextSyncStatus: CompetitionRegistrationDraftStorage['syncStatus'] = 'LOCAL_ONLY',
    updatedAt = Date.now(),
  ) => {
    const latestDraft = latestRegistrationDraftRef.current;
    const generatedIdentifiers = nextRegistrationId
      ? undefined
      : buildRegistrationDraftIdentifiers(
        latestDraft?.savedAt || updatedAt,
        latestDraft?.competitionUuid || selectedCompetition?.uuid || toPositiveId(nextValues.competitionId) || 'registration-draft',
      );
    const draftState: CompetitionRegistrationDraftStorage = {
      competitionTitle: selectedCompetition?.title || latestDraft?.competitionTitle,
      competitionUuid: selectedCompetition?.uuid || latestDraft?.competitionUuid,
      competitionFeeMode: selectedCompetition?.feeMode ?? latestDraft?.competitionFeeMode,
      competitionEntryFeeMinor: selectedCompetition?.entryFeeMinor ?? latestDraft?.competitionEntryFeeMinor,
      competitionCurrency: selectedCompetition?.currency ?? latestDraft?.competitionCurrency,
      registrationNo: nextRegistrationId
        ? latestDraft?.registrationNo
        : latestDraft?.registrationNo || generatedIdentifiers?.registrationNo,
      participantNo: nextRegistrationId
        ? latestDraft?.participantNo
        : latestDraft?.participantNo || generatedIdentifiers?.participantNo,
      registrationId: nextRegistrationId,
      currentStep: nextStep,
      flowVersion: REGISTRATION_WIZARD_FLOW_VERSION,
      acceptedDocumentKeys: nextAcceptedDocumentKeys,
      confirmedTeamId: confirmedTeamIdRef.current,
      confirmedProjectId: confirmedProjectIdRef.current,
      paymentStatus: nextPaymentStatus,
      savedAt: updatedAt,
      localUpdatedAt: updatedAt,
      cloudUpdatedAt: latestDraft?.cloudUpdatedAt,
      syncStatus: nextSyncStatus,
      values: sanitizeRegistrationFormValues({
        ...defaultRegistrationFormValues,
        ...nextValues,
      }),
    };
    return draftState;
  }, [acceptedDocumentKeys, collectRegistrationValues, paymentStatus, registrationId, selectedCompetition?.currency, selectedCompetition?.entryFeeMinor, selectedCompetition?.feeMode, selectedCompetition?.title, selectedCompetition?.uuid, step]);

  const writeLocalRegistrationDraftNow = useCallback((
    draft: CompetitionRegistrationDraftStorage,
    updatedAt = draft.localUpdatedAt || draft.savedAt || Date.now(),
  ) => {
    writeLocalRegistrationDraft(currentUserId, { payload: draft, updatedAt });
    pendingLocalRegistrationDraftRef.current = undefined;
    setRegistrationDraftSavedAt(updatedAt);
    setRegistrationDraftSyncStatus(
      draft.syncStatus === 'SYNCED'
        ? 'SYNCED'
        : draft.syncStatus === 'SYNC_ERROR'
          ? 'SYNC_ERROR'
          : 'LOCAL_ONLY',
    );
    return draft;
  }, [currentUserId]);

  const flushPendingLocalRegistrationDraft = useCallback(() => {
    if (registrationDraftSaveTimerRef.current) {
      clearTimeout(registrationDraftSaveTimerRef.current);
      registrationDraftSaveTimerRef.current = undefined;
    }
    const pendingDraft = pendingLocalRegistrationDraftRef.current;
    if (!pendingDraft) {
      return latestRegistrationDraftRef.current;
    }
    try {
      return writeLocalRegistrationDraftNow(pendingDraft.draft, pendingDraft.updatedAt);
    } catch (error) {
      setRegistrationDraftSyncStatus('SYNC_ERROR');
      showErrorMessage(error, '本机草稿保存失败');
      return pendingDraft.draft;
    }
  }, [writeLocalRegistrationDraftNow]);

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
      && !latestRegistrationDraftRef.current?.cloudUpdatedAt
    ) {
      clearLocalRegistrationDraft(currentUserId);
      pendingLocalRegistrationDraftRef.current = undefined;
      latestRegistrationDraftRef.current = undefined;
      setRegistrationDraftSavedAt(undefined);
      setRegistrationDraftSyncStatus('IDLE');
      return undefined;
    }
    const updatedAt = nextRegistrationDraftUpdatedAt(latestRegistrationDraftRef.current);
    const draftState = buildCurrentRegistrationDraftState(
      nextValues,
      nextStep,
      nextAcceptedDocumentKeys,
      nextRegistrationId,
      nextPaymentStatus,
      'LOCAL_ONLY',
      updatedAt,
    );
    latestRegistrationDraftRef.current = draftState;
    pendingLocalRegistrationDraftRef.current = { draft: draftState, updatedAt };
    setRegistrationDraftSyncStatus('SAVING_LOCAL');
    if (registrationDraftSaveTimerRef.current) {
      clearTimeout(registrationDraftSaveTimerRef.current);
    }
    registrationDraftSaveTimerRef.current = setTimeout(() => {
      flushPendingLocalRegistrationDraft();
    }, 350);
    return draftState;
  }, [
    acceptedDocumentKeys,
    buildCurrentRegistrationDraftState,
    collectRegistrationValues,
    currentUserId,
    flushPendingLocalRegistrationDraft,
    paymentStatus,
    registrationId,
    step,
  ]);

  const saveRegistrationDraftToCloud = useCallback(async (
    nextValues: Partial<RegistrationFormValues> = collectRegistrationValues(),
    nextStep = step,
    nextAcceptedDocumentKeys = acceptedDocumentKeys,
    nextRegistrationId = registrationId,
    nextPaymentStatus = paymentStatus,
  ) => {
    const localUpdatedAt = nextRegistrationDraftUpdatedAt(latestRegistrationDraftRef.current);
    const draftState = buildCurrentRegistrationDraftState(
      nextValues,
      nextStep,
      nextAcceptedDocumentKeys,
      nextRegistrationId,
      nextPaymentStatus,
      'LOCAL_ONLY',
      localUpdatedAt,
    );
    latestRegistrationDraftRef.current = draftState;
    pendingLocalRegistrationDraftRef.current = { draft: draftState, updatedAt: localUpdatedAt };
    flushPendingLocalRegistrationDraft();
    setRegistrationDraftSyncStatus('SYNCING');
    const preserveNewerLocalDraft = (
      syncStatus: CompetitionRegistrationDraftStorage['syncStatus'],
    ) => {
      const latestDraft = latestRegistrationDraftRef.current;
      if (!latestDraft || !hasNewerRegistrationDraft(latestDraft, localUpdatedAt)) {
        return undefined;
      }
      const newerUpdatedAt = getRegistrationDraftUpdatedAt(latestDraft);
      const newerDraft: CompetitionRegistrationDraftStorage = {
        ...latestDraft,
        syncStatus,
      };
      latestRegistrationDraftRef.current = newerDraft;
      const pendingDraft = pendingLocalRegistrationDraftRef.current;
      if (pendingDraft && pendingDraft.updatedAt === newerUpdatedAt) {
        pendingLocalRegistrationDraftRef.current = {
          draft: newerDraft,
          updatedAt: newerUpdatedAt,
        };
      } else {
        try {
          writeLocalRegistrationDraft(currentUserId, {
            payload: newerDraft,
            updatedAt: newerUpdatedAt,
          });
        } catch {
          setRegistrationDraftSyncStatus('SYNC_ERROR');
          return newerDraft;
        }
      }
      setRegistrationDraftSavedAt(newerUpdatedAt);
      setRegistrationDraftSyncStatus(
        syncStatus === 'SYNC_ERROR'
          ? 'SYNC_ERROR'
          : pendingLocalRegistrationDraftRef.current
            ? 'SAVING_LOCAL'
            : 'LOCAL_ONLY',
      );
      return newerDraft;
    };
    try {
      const storedDraft = await writeCompetitionRegistrationDraft(registrationDraftStorageKey, draftState);
      const newerDraft = preserveNewerLocalDraft('LOCAL_ONLY');
      if (newerDraft) {
        return newerDraft;
      }
      const syncedDraft: CompetitionRegistrationDraftStorage = {
        ...draftState,
        savedAt: storedDraft.updatedAt,
        localUpdatedAt: storedDraft.updatedAt,
        cloudUpdatedAt: storedDraft.updatedAt,
        syncStatus: 'SYNCED',
      };
      latestRegistrationDraftRef.current = syncedDraft;
      writeLocalRegistrationDraftNow(syncedDraft, storedDraft.updatedAt);
      return syncedDraft;
    } catch (error) {
      if (preserveNewerLocalDraft('SYNC_ERROR')) {
        throw new RegistrationDraftCloudSyncError(error);
      }
      const failedAt = nextRegistrationDraftUpdatedAt(draftState);
      const failedDraft: CompetitionRegistrationDraftStorage = {
        ...draftState,
        savedAt: failedAt,
        localUpdatedAt: failedAt,
        syncStatus: 'SYNC_ERROR',
      };
      latestRegistrationDraftRef.current = failedDraft;
      try {
        writeLocalRegistrationDraftNow(failedDraft, failedAt);
      } catch {
        setRegistrationDraftSyncStatus('SYNC_ERROR');
      }
      throw new RegistrationDraftCloudSyncError(error);
    }
  }, [acceptedDocumentKeys, buildCurrentRegistrationDraftState, collectRegistrationValues, currentUserId, flushPendingLocalRegistrationDraft, paymentStatus, registrationDraftStorageKey, registrationId, step, writeLocalRegistrationDraftNow]);

  const readLatestRegistrationDraft = useCallback(async () => {
    const localDraft = readLocalRegistrationDraft<CompetitionRegistrationDraftStorage>(currentUserId);
    let cloudDraft: StoredUserDraft<CompetitionRegistrationDraftStorage> | undefined;
    let cloudError: unknown;
    try {
      cloudDraft = await readCompetitionRegistrationDraftEnvelope(registrationDraftStorageKey);
    } catch (error) {
      cloudError = error;
    }
    const restored = resolveNewestRegistrationDraft(localDraft, cloudDraft);
    if (!restored) {
      if (cloudError) {
        throw cloudError;
      }
      return undefined;
    }
    const source: RegistrationDraftRestoreSource = restored.source;
    const restoredDraft: CompetitionRegistrationDraftStorage = {
      ...restored.envelope.payload,
      localUpdatedAt: source === 'local'
        ? restored.envelope.updatedAt
        : restored.envelope.payload.localUpdatedAt || restored.envelope.updatedAt,
      cloudUpdatedAt: source === 'cloud'
        ? restored.envelope.updatedAt
        : restored.envelope.payload.cloudUpdatedAt,
      syncStatus: source === 'cloud'
        ? 'SYNCED'
        : cloudError
          ? 'SYNC_ERROR'
          : restored.envelope.payload.syncStatus || 'LOCAL_ONLY',
    };
    if (source === 'cloud') {
      try {
        writeLocalRegistrationDraft(currentUserId, {
          payload: restoredDraft,
          updatedAt: restored.envelope.updatedAt,
        });
      } catch {
        // Cloud recovery remains usable even when local storage is unavailable.
      }
    }
    return { draft: restoredDraft, source };
  }, [currentUserId, registrationDraftStorageKey]);

  const clearAllRegistrationDrafts = useCallback(async () => {
    if (registrationDraftSaveTimerRef.current) {
      clearTimeout(registrationDraftSaveTimerRef.current);
      registrationDraftSaveTimerRef.current = undefined;
    }
    const competitionUuid = registrationDocumentsCompetitionUuid
      || latestRegistrationDraftRef.current?.competitionUuid;
    pendingLocalRegistrationDraftRef.current = undefined;
    try {
      clearLocalRegistrationDraft(currentUserId);
    } catch {
      // Continue deleting the cloud draft when local storage is unavailable.
    }
    latestRegistrationDraftRef.current = undefined;
    setRegistrationDraftSavedAt(undefined);
    setRegistrationDraftSyncStatus('IDLE');
    await Promise.all([
      clearCompetitionRegistrationDraft(registrationDraftStorageKey),
      competitionUuid
        ? clearUserDraft(buildRegistrationDocumentAcceptanceStorageKey(competitionUuid))
        : Promise.resolve(),
    ]);
  }, [currentUserId, registrationDocumentsCompetitionUuid, registrationDraftStorageKey]);
  const hydrateRegistrationDraft = useCallback((draft?: CompetitionRegistrationDraftStorage) => {
    const values = sanitizeRegistrationFormValues({
      ...defaultRegistrationFormValues,
      ...(draft?.values || {}),
    });
    values.newProjectExtraValues = migrateRegistrationIntellectualPropertyValues(
      values.newProjectExtraValues,
      intellectualPropertyFieldKeys,
    );
    if (!draft?.registrationId) {
      values.projectId = undefined;
    }
    form.resetFields();
    form.setFieldsValue(values);
    confirmedTeamIdRef.current = draft?.confirmedTeamId;
    confirmedProjectIdRef.current = draft?.registrationId ? draft.confirmedProjectId : undefined;
    setRegistrationId(draft?.registrationId);
    setPaymentStatus(draft?.paymentStatus);
    setAcceptedDocumentKeys(draft?.acceptedDocumentKeys || []);
    setRegistrationDraftSavedAt(draft?.localUpdatedAt || draft?.savedAt);
    setRegistrationDraftSyncStatus(
      draft?.syncStatus === 'SYNCED'
        ? 'SYNCED'
        : draft?.syncStatus === 'SYNC_ERROR'
          ? 'SYNC_ERROR'
          : draft
            ? 'LOCAL_ONLY'
            : 'IDLE',
    );
    setRegistrationCompetitionFallback(buildRegistrationCompetitionFallback(
      toPositiveId(values.competitionId),
      {
        competitionUuid: draft?.competitionUuid,
        competitionTitle: draft?.competitionTitle,
        feeMode: draft?.competitionFeeMode,
        entryFeeMinor: draft?.competitionEntryFeeMinor,
        currency: draft?.competitionCurrency,
      },
    ));
    latestRegistrationDraftRef.current = draft;
    return values;
  }, [form, intellectualPropertyFieldKeys]);
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
    (documents: CompetitionConfigItem[], nextAcceptedDocumentKeys: string[] = []) => {
      setDocumentReadingCountdowns(buildRegistrationDocumentCountdowns(
        documents,
        nextAcceptedDocumentKeys,
        getConfigItemReadingSeconds,
      ));
      setAcceptedDocumentKeys(nextAcceptedDocumentKeys);
    },
    [],
  );

  const setWizardStep = useCallback((
    nextStep: number,
    replace = true,
    draftOptions: {
      acceptedDocumentKeys?: string[];
      registrationId?: number;
      paymentStatus?: string;
      skipDraftWrite?: boolean;
    } = {},
  ) => {
    const normalizedStep = Math.min(Math.max(nextStep, 0), registrationWizardMaxStep);
    if (!draftOptions.skipDraftWrite) {
      persistRegistrationDraft(
        collectRegistrationValues(),
        normalizedStep,
        draftOptions.acceptedDocumentKeys ?? acceptedDocumentKeys,
        draftOptions.registrationId ?? registrationId,
        draftOptions.paymentStatus ?? paymentStatus,
      );
    }
    setStep(normalizedStep);
    setViewMode('wizard');
    const navigate = replace ? history.replace : history.push;
    navigate({
      pathname: location.pathname,
      search: createRegistrationWizardSearch(normalizedStep),
    });
  }, [acceptedDocumentKeys, collectRegistrationValues, location.pathname, paymentStatus, persistRegistrationDraft, registrationId]);

  const showRegistrationList = useCallback(() => {
    flushPendingLocalRegistrationDraft();
    setViewMode('list');
    history.replace({ pathname: location.pathname, search: '' });
  }, [flushPendingLocalRegistrationDraft, location.pathname]);

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
    if (registrationDraftHydratedKeyRef.current === registrationDraftStorageKey) {
      return;
    }
    registrationDraftHydratedKeyRef.current = registrationDraftStorageKey;
    setRegistrationDraftHydrated(false);
    let cancelled = false;
    void readLatestRegistrationDraft()
      .then((restored) => {
        if (cancelled) return;
        hydrateRegistrationDraft(restored?.draft);
        if (restored && registrationDraftRestoreNoticeRef.current !== restored.source) {
          registrationDraftRestoreNoticeRef.current = restored.source;
          message.info(restored.source === 'local' ? '已恢复本机较新的报名草稿' : '已恢复云端较新的报名草稿');
        }
      })
      .catch((error) => {
        if (!cancelled) showErrorMessage(error, '报名草稿加载失败');
      })
      .finally(() => {
        if (!cancelled) setRegistrationDraftHydrated(true);
      });
    return () => { cancelled = true; };
  }, [hydrateRegistrationDraft, readLatestRegistrationDraft, registrationDraftStorageKey]);

  useEffect(() => () => {
    flushPendingLocalRegistrationDraft();
  }, [flushPendingLocalRegistrationDraft]);

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
      message.warning(allowedStep === 0 ? '请先选择赛事并确认报名文书' : '请先补全团队、学生及项目信息');
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
    const loadedCompetition = competitions.find((item) => item.id === competitionId);
    if (hasRegistrationCompetitionPricing(loadedCompetition)) {
      setRegistrationCompetitionFallback(undefined);
      return () => {
        mounted = false;
      };
    }
    const persistedFallback = buildRegistrationCompetitionFallback(competitionId, {
      competitionUuid: latestRegistrationDraftRef.current?.competitionUuid,
      competitionTitle: latestRegistrationDraftRef.current?.competitionTitle,
      feeMode: latestRegistrationDraftRef.current?.competitionFeeMode,
      entryFeeMinor: latestRegistrationDraftRef.current?.competitionEntryFeeMinor,
      currency: latestRegistrationDraftRef.current?.competitionCurrency,
    });
    if (persistedFallback) {
      setRegistrationCompetitionFallback(persistedFallback);
      if (hasRegistrationCompetitionPricing(persistedFallback)) {
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
    setRegistrationDocumentsCompetitionUuid(undefined);
    setRegistrationFields([]);
    setParticipantLimits({
      studentMinMembers: DEFAULT_STUDENT_MIN_MEMBERS,
      studentMaxMembers: DEFAULT_STUDENT_MAX_MEMBERS,
      teacherMinMembers: DEFAULT_TEACHER_MIN_MEMBERS,
      teacherMaxMembers: DEFAULT_TEACHER_MAX_MEMBERS,
    });
    setStageMaterialConfigs([]);
    resetRegistrationDocumentProgress([]);
    if (viewMode !== 'wizard') {
      setRegistrationSettingsStatus('idle');
      setRegistrationDocumentsLoading(false);
      return () => {
        mounted = false;
      };
    }
    if (!activeCompetitionId) {
      setRegistrationSettingsStatus('idle');
      setRegistrationDocumentsLoading(false);
      return () => {
        mounted = false;
      };
    }
    setRegistrationSettingsStatus('loading');
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
        const rememberedAcceptance = await readCompetitionRegistrationDocumentAcceptance(competitionUuid)
          .catch(() => undefined);
        if (!mounted) {
          return;
        }
        const currentDraft = latestRegistrationDraftRef.current;
        const draftAcceptedDocumentKeys = currentDraft
          && toPositiveId(currentDraft.values?.competitionId) === activeCompetitionId
          ? currentDraft.acceptedDocumentKeys || []
          : [];
        const nextAcceptedDocumentKeys = resolveAcceptedRegistrationDocumentKeys(
          nextDocuments,
          rememberedAcceptance?.acceptedDocumentKeys,
          draftAcceptedDocumentKeys,
        );
        setRegistrationDocuments(nextDocuments);
        setRegistrationDocumentsCompetitionUuid(competitionUuid);
        resetRegistrationDocumentProgress(nextDocuments, nextAcceptedDocumentKeys);
        const nextRegistrationFields = (settings.fields || [])
          .filter((item) => ['REGISTRATION_FIELD', 'TEAM_FIELD', 'MEMBER_FIELD', 'TEACHER_FIELD', 'PROJECT_FIELD'].includes(item.itemType) && item.enabled !== false)
          .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0));
        const invalidRegistrationField = nextRegistrationFields
          .map(toRegistrationCollectedField)
          .find((field) => !isSupportedRegistrationFieldValidationConfig(field.fieldType, field.validationRule));
        if (invalidRegistrationField) {
          throw new Error(`报名字段“${invalidRegistrationField.title}”的类型或校验规则不受支持`);
        }
        setRegistrationFields(nextRegistrationFields);
        setParticipantLimits(getTeamMemberLimits(settings.fields || []));
        setStageMaterialConfigs(
          ([...(settings.files || []), ...(settings.stageMaterials || [])])
            .filter((item) => item.enabled !== false)
            .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0)),
        );
        setRegistrationSettingsStatus('ready');
      } catch (error) {
        if (mounted) {
          setRegistrationSettingsStatus('error');
          showErrorMessage(error, '报名字段配置加载失败');
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
    registrationSettingsReloadRevision,
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
    if (!shouldLoadPreliminaryStageForm(step)) {
      return;
    }
    const competitionId = toPositiveId(selectedCompetitionId)
      || (registrationDraftHydrated
        ? toPositiveId(latestRegistrationDraftRef.current?.values?.competitionId)
        : undefined);
    if (!competitionId) {
      return;
    }
    void loadStageFormForCompetition(competitionId).catch((error) => {
      showErrorMessage(error, '初赛材料表单加载失败');
    });
  }, [loadStageFormForCompetition, registrationDraftHydrated, selectedCompetitionId, step]);

  const cancelMemberInlineEditor = useCallback(() => {
    setMemberEditorKey(undefined);
    memberForm.resetFields();
  }, [memberForm]);

  const startNewRegistration = useCallback(async () => {
    const restored = await readLatestRegistrationDraft();
    const draft = restored?.draft;
    hydrateRegistrationDraft(draft);
    setStageForm(undefined);
    setRegistrationRecord(undefined);
    setPaymentOrder(undefined);
    setPaymentModalOpen(false);
    setMemberEditorKey(undefined);
    setIntellectualPropertyEditorIndex(undefined);
    setWizardStep(normalizeRegistrationWizardDraftStep(draft?.currentStep, draft?.flowVersion), false, {
      acceptedDocumentKeys: draft?.acceptedDocumentKeys || [],
      registrationId: draft?.registrationId,
      paymentStatus: draft?.paymentStatus,
      skipDraftWrite: true,
    });
  }, [hydrateRegistrationDraft, readLatestRegistrationDraft, setWizardStep]);

  const openRegistrationFlow = useCallback(async (record: CompetitionRegistrationRecord) => {
    setLoading(true);
    try {
      const latest = await getRegistration(record.id);
      if (isRegistrationPaymentSuccessful(latest.status)) {
        history.push(`/competitions/register/payment-result?registrationId=${latest.id}`);
        return;
      }
      const competitionId = toPositiveId(latest.competitionId);
      const [activeStageForm, materialSubmissions] = await Promise.all([
        competitionId
          ? loadOptionalPreliminaryStageForm(competitionId, listCompetitionStages, getCompetitionStageForm)
          : Promise.resolve(undefined),
        listRegistrationMaterials(record.id),
      ]);
      const teamId = toPositiveId(latest.teamId);
      const projectId = toPositiveId(latest.projectId);
      const teamSnapshot = parseRegistrationSnapshot<RegistrationSnapshotTeamPayload & { registrationExtraValues?: Record<string, unknown> }>(latest.teamSnapshotJson);
      const registrationSnapshot = parseRegistrationSnapshot<Record<string, unknown>>(latest.registrationSnapshotJson);
      const projectSnapshot = parseRegistrationSnapshot<{ title?: string; description?: string; imageUrl?: string; extraValues?: Record<string, unknown> }>(latest.projectSnapshotJson);
      const members = parseRegistrationSnapshot<RegistrationTeamMemberDraft[]>(latest.memberSnapshotJson, [])
        .map((member) => ({
          ...member,
          participantType: normalizeRegistrationParticipantType(member.participantType),
        }));
      const students = filterRegistrationParticipants(members, 'STUDENT');
      const teachers = filterRegistrationParticipants(members, 'TEACHER');
      const restoredProjectExtraValues = migrateRegistrationIntellectualPropertyValues(
        projectSnapshot.extraValues,
        intellectualPropertyFieldKeys,
      );
      form.resetFields();
      form.setFieldsValue({
        competitionId,
        teamId,
        projectId,
        registrationExtraValues: Object.keys(registrationSnapshot).length
          ? registrationSnapshot
          : teamSnapshot.registrationExtraValues,
        newTeamName: teamSnapshot.teamName,
        newTeam: { ...teamSnapshot, initialMembers: members },
        newProjectTitle: projectSnapshot.title,
        newProjectDescription: projectSnapshot.description,
        newProjectImageUrl: projectSnapshot.imageUrl,
        newProjectExtraValues: restoredProjectExtraValues,
      });
      confirmedTeamIdRef.current = teamId;
      confirmedProjectIdRef.current = projectId;
      setRegistrationId(latest.id);
      setRegistrationRecord(latest);
      setPaymentStatus(latest.status);
      setStageForm(activeStageForm);
      const restoredMaterialValues = activeStageForm
        ? restoreRegistrationMaterialValues(materialSubmissions, activeStageForm.stageId)
        : {};
      if (activeStageForm) {
        form.setFieldValue('materials', restoredMaterialValues);
      }
      const restoredMaterialFields = activeStageForm
        ? enrichCompetitionStageFormFields(parseFormFields(activeStageForm), stageMaterialConfigs)
        : [];
      const hasMissingRequiredMaterials = getMissingRequiredRegistrationMaterials(
        restoredMaterialFields,
        restoredMaterialValues,
      ).length > 0;
      const restoredIntellectualPropertyEntries = normalizeRegistrationIntellectualPropertyEntries(
        restoredProjectExtraValues,
        intellectualPropertyFieldKeys,
      );
      const hasMissingRequiredEvidence = getMissingRequiredIntellectualPropertyFields(
        intellectualPropertyFields,
        restoredIntellectualPropertyEntries,
      ).length > 0;
      const hasIncompleteTeamOrProject = !trimOptional(teamSnapshot.teamName)
        || students.length < participantLimits.studentMinMembers
        || students.length > participantLimits.studentMaxMembers
        || teachers.length < participantLimits.teacherMinMembers
        || teachers.length > participantLimits.teacherMaxMembers
        || !trimOptional(projectSnapshot.title);
      const resumeStep = resolveRegistrationResumeStep({
        hasPaymentOrder: Boolean(latest.paymentOrderNo),
        hasIncompleteTeamOrProject,
        hasMissingRequiredMaterials,
        hasMissingRequiredEvidence,
      });
      if (resumeStep !== registrationWizardStep.review && resumeStep !== registrationWizardStep.payment) {
        message.info(`请先完成${registrationWizardStepItems[resumeStep].title}`);
      }
      setWizardStep(resumeStep, false, {
        registrationId: latest.id,
        paymentStatus: latest.status,
      });
    } catch (error) {
      showErrorMessage(error, '报名记录加载失败');
    } finally {
      setLoading(false);
    }
  }, [form, intellectualPropertyFieldKeys, intellectualPropertyFields, participantLimits, setWizardStep, stageMaterialConfigs]);

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
        if (latestRegistrationDraftRef.current?.registrationId === record.id) {
          await clearAllRegistrationDrafts();
        }
        message.success(record.paymentOrderNo ? '报名与待支付订单已取消' : '报名已取消');
        registrationActionRef.current?.reload();
      },
    });
  }, [clearAllRegistrationDrafts]);

  const abandonCurrentRegistrationDraft = useCallback(() => {
    modal.confirm({
      title: '确认放弃当前报名草稿？',
      content: '本机和云端草稿将同时删除，已上传文件仍保留在文件中心。',
      okText: '确认放弃',
      cancelText: '继续填写',
      okButtonProps: { danger: true },
      onOk: async () => {
        await clearAllRegistrationDrafts();
        hydrateRegistrationDraft(undefined);
        setViewMode('list');
        history.replace({ pathname: location.pathname, search: '' });
        registrationActionRef.current?.reload();
        message.success('报名草稿已删除');
      },
    });
  }, [clearAllRegistrationDrafts, hydrateRegistrationDraft, location.pathname]);

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
    setLoading(true);
    try {
      if (step === 0) {
        await form.validateFields([
          'competitionId',
          ...registrationScopeFields.map((field) => ['registrationExtraValues', field.itemKey]),
        ]);
        if (registrationSettingsStatus !== 'ready') {
          message.error(registrationSettingsStatus === 'loading' ? '报名字段配置仍在加载，请稍后' : '报名字段配置加载失败，请重试');
          return;
        }
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
        if (!registrationId) {
          form.setFieldValue('projectId', undefined);
        }
        await saveRegistrationDraftToCloud(collectRegistrationValues(), registrationWizardStep.team);
        if (registrationDocumentsCompetitionUuid) {
          await writeCompetitionRegistrationDocumentAcceptance(
            registrationDocumentsCompetitionUuid,
            acceptedDocumentKeys,
          ).catch(() => undefined);
        }
        setWizardStep(registrationWizardStep.team, true, { skipDraftWrite: true });
      } else if (step === 1) {
        if (registrationSettingsStatus !== 'ready') {
          message.error(registrationSettingsStatus === 'loading' ? '报名字段配置仍在加载，请稍后' : '报名字段配置加载失败，请重试');
          return;
        }
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
        const students = filterRegistrationParticipants(members, 'STUDENT');
        const teachers = filterRegistrationParticipants(members, 'TEACHER');
        if (students.length < participantLimits.studentMinMembers) {
          message.error(`至少需要 ${participantLimits.studentMinMembers} 位学生`);
          return;
        }
        if (students.length > participantLimits.studentMaxMembers) {
          message.error(`最多只能有 ${participantLimits.studentMaxMembers} 位学生`);
          return;
        }
        if (teachers.length < participantLimits.teacherMinMembers) {
          message.error(`至少需要 ${participantLimits.teacherMinMembers} 位指导老师`);
          return;
        }
        if (teachers.length > participantLimits.teacherMaxMembers) {
          message.error(`最多只能有 ${participantLimits.teacherMaxMembers} 位指导老师`);
          return;
        }
        const participantGroups = [
          { label: '学生', participants: students, fields: effectiveStudentRegistrationFields },
          { label: '指导老师', participants: teachers, fields: effectiveTeacherRegistrationFields },
        ];
        for (const group of participantGroups) {
          for (let participantIndex = 0; participantIndex < group.participants.length; participantIndex += 1) {
            for (const field of group.fields) {
              const value = getMemberCollectedFieldValue(group.participants[participantIndex], field);
              const validationError = field.required && !hasCollectedValue(value)
                ? `请填写${field.title}`
                : validateRegistrationFieldValue(
                    field.fieldType,
                    field.validationRule,
                    field.title,
                    value,
                    field.scope,
                    field.itemKey,
                  );
              if (validationError) {
                message.error(`${group.label} ${participantIndex + 1}：${validationError}`);
                return;
              }
            }
          }
        }
        if (!students.length) {
          message.error('请至少添加一位学生');
          return;
        }
        if (!form.getFieldValue('newProjectTitle')?.trim()) {
          message.error(`请输入${projectTitleField.title}`);
          return;
        }
        form.setFieldValue(['newTeam', 'initialMembers'], members);
        await form.validateFields([
          'newTeamName',
          'newTeam',
          'newProjectTitle',
          ...(projectImageField ? ['newProjectImageUrl'] : []),
          ...(projectDescriptionField ? ['newProjectDescription'] : []),
          ...projectCustomFields.map((field) => ['newProjectExtraValues', field.itemKey]),
        ], { recursive: true });
        form.setFieldValue('teamId', undefined);
        confirmedTeamIdRef.current = undefined;
        const competitionId = toPositiveId(form.getFieldValue('competitionId')) || toPositiveId(selectedCompetitionId);
        if (!competitionId) {
          message.error('赛事信息不存在');
          return;
        }
        await loadStageFormForCompetition(competitionId);
        await saveRegistrationDraftToCloud(
          collectRegistrationValues(),
          registrationWizardStep.preliminaryMaterials,
        );
        setWizardStep(registrationWizardStep.preliminaryMaterials, true, { skipDraftWrite: true });
      } else if (step === registrationWizardStep.preliminaryMaterials) {
        if (stageFormLoading) {
          message.info('初赛材料表单仍在加载，请稍后');
          return;
        }
        await form.validateFields(fields.map((field) => ['materials', field.key]));
        await saveRegistrationDraftToCloud(
          collectRegistrationValues(),
          registrationWizardStep.projectEvidence,
        );
        setWizardStep(registrationWizardStep.projectEvidence, true, { skipDraftWrite: true });
      } else if (step === registrationWizardStep.projectEvidence) {
        if (intellectualPropertyEditorIndex !== undefined) {
          message.error('请先保存当前正在编辑的知识产权信息');
          return;
        }
        if (missingRequiredEvidenceFields.length) {
          message.error(`请填写 ${missingRequiredEvidenceFields.map((field) => field.title).join('、')}`);
          return;
        }
        await saveRegistrationDraftToCloud(collectRegistrationValues(), registrationWizardStep.review);
        setWizardStep(registrationWizardStep.review, true, { skipDraftWrite: true });
      } else if (step === registrationWizardStep.review) {
        if (missingRequiredMaterialFields.length) {
          message.warning(`请先上传 ${missingRequiredMaterialFields.map((field) => field.label || field.key).join('、')}`);
          setWizardStep(registrationWizardStep.preliminaryMaterials);
          return;
        }
        if (missingRequiredEvidenceFields.length) {
          message.warning(`请先填写 ${missingRequiredEvidenceFields.map((field) => field.title).join('、')}`);
          setWizardStep(registrationWizardStep.projectEvidence);
          return;
        }
        await form.validateFields();
        if (!hasRegistrationCompetitionPricing(selectedCompetition)) {
          message.error('赛事收费信息仍在加载，请稍后重试');
          return;
        }
        const competitionId = toPositiveId(form.getFieldValue('competitionId')) || toPositiveId(selectedCompetitionId);
        if (!competitionId) {
          message.error('赛事信息不存在');
          return;
        }
        await saveRegistrationDraftToCloud(collectRegistrationValues(), registrationWizardStep.review);
        const teamDraft = (form.getFieldValue('newTeam') || {}) as RegistrationTeamDraft;
        const registrationExtraValues = pickEnabledCollectedValues(
          normalizeSnapshotValue(form.getFieldValue('registrationExtraValues')) as Record<string, unknown> | undefined,
          registrationScopeFields.map((field) => field.itemKey),
        );
        const projectExtraValues = buildRegistrationProjectExtraValues(
          normalizeSnapshotValue(form.getFieldValue('newProjectExtraValues')) as Record<string, unknown> | undefined,
          projectCustomFields.map((field) => field.itemKey),
          intellectualPropertyFieldKeys,
        );
        const enabledStudentStandardKeys = new Set(
          effectiveStudentRegistrationFields
            .map((field) => resolveMemberStandardFieldKey(field))
            .filter(Boolean),
        );
        const enabledTeacherStandardKeys = new Set(
          effectiveTeacherRegistrationFields
            .map((field) => resolveMemberStandardFieldKey(field))
            .filter(Boolean),
        );
        const members = normalizeRegistrationMembers(teamDraft.initialMembers).map((member) => {
          const participantType = normalizeRegistrationParticipantType(member.participantType);
          const standardKeys = participantType === 'TEACHER'
            ? enabledTeacherStandardKeys
            : enabledStudentStandardKeys;
          const customFields = participantType === 'TEACHER'
            ? teacherFieldSplit.customFields
            : memberFieldSplit.customFields;
          return {
            participantType,
            memberName: member.memberName,
            employeeNo: standardKeys.has('employeeNo') ? member.employeeNo : undefined,
            departmentName: standardKeys.has('departmentName') ? member.departmentName : undefined,
            role: standardKeys.has('role') ? member.role : undefined,
            remark: standardKeys.has('remark') ? member.remark : undefined,
            extraValues: pickEnabledCollectedValues(member.extraValues, customFields.map((field) => field.itemKey)),
          };
        });
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
          await clearAllRegistrationDrafts().catch(() => {
            message.warning('报名已完成，云端草稿将在结果页继续清理');
          });
          history.replace(`/competitions/register/payment-result?registrationId=${registration.id}`);
          return;
        }
        await saveRegistrationDraftToCloud(
          collectRegistrationValues(),
          registrationWizardStep.payment,
          acceptedDocumentKeys,
          registration.id,
          registration.status,
        );
        setWizardStep(registrationWizardStep.payment, true, {
          registrationId: registration.id,
          paymentStatus: registration.status,
          skipDraftWrite: true,
        });
      }
    } catch (error) {
      if (error instanceof RegistrationDraftCloudSyncError) {
        message.error('已保存到本机，云端同步失败，请重试');
      } else {
        showErrorMessage(error, '操作失败');
      }
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
      const errorMessage = extractErrorMessage(error, '支付订单生成失败');
      if (isMissingPreliminaryMaterialsError(errorMessage)) {
        message.error('请先提交初赛材料');
        setWizardStep(registrationWizardStep.preliminaryMaterials);
      } else {
        showErrorMessage(error, '支付订单生成失败');
      }
    } finally {
      setLoading(false);
    }
  };

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
    void getRegistration(registrationId)
      .then((registration) => {
        if (!active) return;
        setRegistrationRecord(registration);
        setPaymentStatus(registration.status);
      })
      .catch((error) => showErrorMessage(error, '报名信息加载失败'));
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
        await clearAllRegistrationDrafts().catch(() => {
          message.warning('支付已确认，云端草稿将在结果页继续清理');
        });
        history.replace(`/competitions/register/payment-result?registrationId=${registrationId}`);
      }
    } catch (error) {
      if (!silent) showErrorMessage(error, '支付结果查询失败');
    }
  }, [clearAllRegistrationDrafts, registrationId]);

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

  const saveMemberEditor = useCallback(async () => {
    if (!memberEditorKey) {
      return;
    }
    const { participantType, participantIndex } = memberEditorKey;
    const editorValues = await memberForm.validateFields();
    const [member] = normalizeRegistrationMembers([{ ...editorValues, participantType }]);
    if (!member) {
      message.error('请填写成员信息');
      return;
    }
    const currentMembers = (form.getFieldValue(['newTeam', 'initialMembers']) || []) as RegistrationTeamMemberDraft[];
    let nextMembers: RegistrationTeamMemberDraft[];
    if (participantIndex === 'new') {
      const maxMembers = participantType === 'TEACHER'
        ? participantLimits.teacherMaxMembers
        : participantLimits.studentMaxMembers;
      if (filterRegistrationParticipants(currentMembers, participantType).length >= maxMembers) {
        message.error(`${participantType === 'TEACHER' ? '指导老师' : '学生'}最多只能添加 ${maxMembers} 人`);
        return;
      }
      nextMembers = saveRegistrationListEntry(currentMembers, 'new', member);
    } else {
      const sourceIndex = findRegistrationParticipantSourceIndex(currentMembers, participantType, participantIndex);
      if (sourceIndex < 0) {
        message.error('待编辑人员已不存在，请重新操作');
        cancelMemberInlineEditor();
        return;
      }
      nextMembers = saveRegistrationListEntry(currentMembers, sourceIndex, member);
    }
    form.setFieldValue(['newTeam', 'initialMembers'], nextMembers);
    persistRegistrationDraft({
      ...collectRegistrationValues(),
      newTeam: {
        ...(form.getFieldValue('newTeam') || {}),
        initialMembers: nextMembers,
      },
    });
    flushPendingLocalRegistrationDraft();
    setMemberEditorKey(undefined);
    memberForm.resetFields();
  }, [cancelMemberInlineEditor, collectRegistrationValues, flushPendingLocalRegistrationDraft, form, memberEditorKey, memberForm, participantLimits, persistRegistrationDraft]);

  const openMemberInlineEditor = useCallback((key: RegistrationMemberEditorKey) => {
    const currentMembers = (form.getFieldValue(['newTeam', 'initialMembers']) || []) as RegistrationTeamMemberDraft[];
    const sourceIndex = key.participantIndex === 'new'
      ? -1
      : findRegistrationParticipantSourceIndex(currentMembers, key.participantType, key.participantIndex);
    memberForm.resetFields();
    memberForm.setFieldsValue(key.participantIndex === 'new'
      ? emptyRegistrationTeamMember(key.participantType)
      : {
          ...(currentMembers[sourceIndex] || emptyRegistrationTeamMember(key.participantType)),
          participantType: key.participantType,
        });
    setMemberEditorKey(key);
  }, [form, memberForm]);

  const removeMemberInline = useCallback((participantType: RegistrationParticipantType, participantIndex: number) => {
    const members = (form.getFieldValue(['newTeam', 'initialMembers']) || []) as RegistrationTeamMemberDraft[];
    const sourceIndex = findRegistrationParticipantSourceIndex(members, participantType, participantIndex);
    const participantLabel = participantType === 'TEACHER' ? '指导老师' : '学生';
    const memberName = normalizeDisplayText(members[sourceIndex]?.memberName) || `${participantLabel} ${participantIndex + 1}`;
    modal.confirm({
      title: `确认移除该${participantLabel}？`,
      content: `移除“${memberName}”后，已填写的信息将从当前报名草稿中删除。`,
      okText: '确认移除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => {
        const currentMembers = (form.getFieldValue(['newTeam', 'initialMembers']) || []) as RegistrationTeamMemberDraft[];
        const currentSourceIndex = findRegistrationParticipantSourceIndex(currentMembers, participantType, participantIndex);
        if (currentSourceIndex < 0) {
          return;
        }
        const nextMembers = deleteRegistrationListEntry(currentMembers, currentSourceIndex);
        form.setFieldValue(['newTeam', 'initialMembers'], nextMembers);
        persistRegistrationDraft({
          ...collectRegistrationValues(),
          newTeam: {
            ...(form.getFieldValue('newTeam') || {}),
            initialMembers: nextMembers,
          },
        });
        flushPendingLocalRegistrationDraft();
        setMemberEditorKey((current) => {
          if (!current || current.participantType !== participantType || current.participantIndex === 'new') {
            return current;
          }
          return current.participantIndex > participantIndex
            ? { ...current, participantIndex: current.participantIndex - 1 }
            : current;
        });
      },
    });
  }, [collectRegistrationValues, flushPendingLocalRegistrationDraft, form, persistRegistrationDraft]);

  useEffect(() => {
    if (memberEditorKey && memberEditorKey.participantIndex !== 'new'
      && memberEditorKey.participantIndex >= filterRegistrationParticipants(
        registrationParticipants,
        memberEditorKey.participantType,
      ).length) {
      cancelMemberInlineEditor();
    }
  }, [cancelMemberInlineEditor, memberEditorKey, registrationParticipants]);

  const updateIntellectualPropertyEntries = useCallback((
    nextEntries: Array<Record<string, unknown>>,
  ) => {
    const nextExtraValues = {
      ...((form.getFieldValue('newProjectExtraValues') || {}) as Record<string, unknown>),
    };
    if (nextEntries.length) {
      nextExtraValues[INTELLECTUAL_PROPERTY_ENTRIES_KEY] = nextEntries;
    } else {
      delete nextExtraValues[INTELLECTUAL_PROPERTY_ENTRIES_KEY];
    }
    form.setFieldValue('newProjectExtraValues', nextExtraValues);
    persistRegistrationDraft({
      ...collectRegistrationValues(),
      newProjectExtraValues: nextExtraValues,
    });
    flushPendingLocalRegistrationDraft();
  }, [collectRegistrationValues, flushPendingLocalRegistrationDraft, form, persistRegistrationDraft]);

  const openIntellectualPropertyEditor = useCallback((index: number | 'new') => {
    intellectualPropertyForm.resetFields();
    intellectualPropertyForm.setFieldsValue(index === 'new' ? {} : intellectualPropertyEntries[index] || {});
    setIntellectualPropertyEditorIndex(index);
  }, [intellectualPropertyEntries, intellectualPropertyForm]);

  const cancelIntellectualPropertyEditor = useCallback(() => {
    setIntellectualPropertyEditorIndex(undefined);
    intellectualPropertyForm.resetFields();
  }, [intellectualPropertyForm]);

  const saveIntellectualPropertyEditor = useCallback(async () => {
    if (intellectualPropertyEditorIndex === undefined) {
      return;
    }
    const editorValues = await intellectualPropertyForm.validateFields();
    const normalizedEntry = Object.fromEntries(
      intellectualPropertyFields
        .map((field) => [field.itemKey, normalizeSnapshotValue(editorValues[field.itemKey])] as const)
        .filter(([, value]) => hasCollectedValue(value)),
    );
    if (!Object.keys(normalizedEntry).length) {
      message.error('请至少填写一项知识产权信息');
      return;
    }
    const nextEntries = saveRegistrationListEntry(
      intellectualPropertyEntries,
      intellectualPropertyEditorIndex,
      normalizedEntry,
    );
    updateIntellectualPropertyEntries(nextEntries);
    cancelIntellectualPropertyEditor();
  }, [cancelIntellectualPropertyEditor, intellectualPropertyEditorIndex, intellectualPropertyEntries, intellectualPropertyFields, intellectualPropertyForm, updateIntellectualPropertyEntries]);

  const removeIntellectualPropertyEntry = useCallback((index: number) => {
    modal.confirm({
      title: '确认删除该知识产权？',
      content: '删除后，该项信息将从当前报名草稿中移除。',
      okText: '确认删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => {
        updateIntellectualPropertyEntries(deleteRegistrationListEntry(intellectualPropertyEntries, index));
      },
    });
  }, [intellectualPropertyEntries, updateIntellectualPropertyEntries]);

  const getMemberFieldDisplayText = useCallback((member: RegistrationTeamMemberDraft, field: RegistrationCollectedField) => {
    return getRegistrationCollectedFieldDisplayText(field, getMemberCollectedFieldValue(member, field));
  }, []);

  const intellectualPropertyColumns = useMemo<NonNullable<TableProps<Record<string, unknown>>['columns']>>(() => [
    ...intellectualPropertyFields.map((field) => ({
      title: field.title,
      key: field.itemKey,
      width: 180,
      render: (_: unknown, entry: Record<string, unknown>) => {
        const value = entry[field.itemKey];
        if ((field.fieldType || 'TEXT').toUpperCase() === 'IMAGE' && typeof value === 'string') {
          return (
            <Image
              width={40}
              height={40}
              src={normalizeUploadUrl(value)}
              alt={field.title}
              className="competition-registration-table-image"
            />
          );
        }
        const displayText = getRegistrationCollectedFieldDisplayText(field, value);
        return (
          <Tooltip title={displayText === '-' ? undefined : displayText}>
            <Typography.Text className="competition-registration-table-cell" ellipsis>
              {displayText}
            </Typography.Text>
          </Tooltip>
        );
      },
    })),
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 148,
      align: 'right',
      render: (_: unknown, entry: Record<string, unknown>) => {
        const entryIndex = intellectualPropertyEntries.indexOf(entry);
        return (
          <Space size={4} wrap={false}>
            <Button type="link" onClick={() => openIntellectualPropertyEditor(entryIndex)}>编辑</Button>
            <Button danger type="link" onClick={() => removeIntellectualPropertyEntry(entryIndex)}>删除</Button>
          </Space>
        );
      },
    },
  ], [intellectualPropertyEntries, intellectualPropertyFields, openIntellectualPropertyEditor, removeIntellectualPropertyEntry]);
  const studentReviewColumns = useMemo(
    () => buildRegistrationReviewColumns<RegistrationTeamMemberDraft>(
      effectiveStudentRegistrationFields,
      getMemberCollectedFieldValue,
    ),
    [effectiveStudentRegistrationFields],
  );
  const teacherReviewColumns = useMemo(
    () => buildRegistrationReviewColumns<RegistrationTeamMemberDraft>(
      effectiveTeacherRegistrationFields,
      getMemberCollectedFieldValue,
    ),
    [effectiveTeacherRegistrationFields],
  );
  const intellectualPropertyReviewColumns = useMemo(
    () => buildRegistrationReviewColumns<Record<string, unknown>>(
      intellectualPropertyFields,
      (entry, field) => entry[field.itemKey],
    ),
    [intellectualPropertyFields],
  );

  const competitionTitleMap = useMemo(
    () => new Map(registrationCompetitionCatalog.map((item) => [item.id, item.title || item.code])),
    [registrationCompetitionCatalog],
  );
  const canViewAllRegistrations = useMemo(() => {
    const matchedScopes = (initialState?.currentUser?.dataScopes || []).filter(
      (scope) => scope.resourceCode === '*' || scope.resourceCode === COMPETITION_REGISTRATION_SCOPE_RESOURCE,
    );
    return matchedScopes.some((scope) => scope.scopeType === 'ALL');
  }, [initialState?.currentUser?.dataScopes]);
  const registrationCompetitionPricingReady = hasRegistrationCompetitionPricing(selectedCompetition);
  const registrationSettingsUnavailable = step === 0
    ? Boolean(selectedCompetitionId) && registrationSettingsStatus !== 'ready'
    : step < registrationWizardStep.payment && registrationSettingsStatus !== 'ready';
  const nextButtonDisabled = registrationSettingsUnavailable
    || (step === 0 && (registrationDocumentsLoading || !allRegistrationDocumentsAccepted))
    || (step === registrationWizardStep.preliminaryMaterials && stageFormLoading)
    || (step === registrationWizardStep.review && !registrationCompetitionPricingReady);
  const canAdvanceRegistration = registrationId ? canUpdateRegistration : canCreateRegistration;
  const nextButtonText = step === 0 && pendingRegistrationDocumentCount > 0
    ? `下一步（剩余 ${pendingRegistrationDocumentCount} 项）`
    : step === registrationWizardStep.review
      ? missingRequiredMaterialFields.length
        ? '去上传材料'
        : missingRequiredEvidenceFields.length
          ? '去完善项目佐证'
          : '确认并生成订单'
      : '\u4e0b\u4e00\u6b65';
  const previewPayableAmount = calculateRegistrationPayableAmount(
    selectedCompetition?.entryFeeMinor,
    selectedCompetition?.feeMode,
    registrationStudents.length,
  );

  const registrationColumns = useMemo<ProColumns<CompetitionRegistrationListRecord>[]>(
    () => [
      {
        title: '\u62a5\u540d\u8bb0\u5f55',
        dataIndex: 'registrationNo',
        width: 220,
        minWidth: 220,
        fieldProps: {
          placeholder: 'Registration No.',
        },
        render: (_, record) => (
          <Typography.Text className="competition-registration-record-cell__no" strong ellipsis={{ tooltip: record.registrationNo }}>
            {record.registrationNo || `\u62a5\u540d ${record.id}`}
          </Typography.Text>
        ),
      },
      {
        title: '\u53c2\u8d5b\u7f16\u53f7',
        dataIndex: 'participantNo',
        search: false,
        width: 160,
        ellipsis: true,
        render: (_, record) => record.participantNo || '-',
      },
      {
        title: '\u8d5b\u4e8b',
        dataIndex: 'competitionId',
        search: false,
        ellipsis: true,
        render: (_, record) => record.draftCompetitionTitle
          || competitionTitleMap.get(record.competitionId)
          || (record.competitionId ? `\u8d5b\u4e8b ${record.competitionId}` : '-'),
      },
      {
        title: '\u56e2\u961f',
        dataIndex: 'teamId',
        search: false,
        ellipsis: true,
        render: (_, record) => record.draftTeamName
          || record.teamName
          || parseSnapshotName(record.teamSnapshotJson, ['teamName', 'name'])
          || (record.teamId ? `\u56e2\u961f ${record.teamId}` : '-'),
      },
      {
        title: '\u9879\u76ee',
        dataIndex: 'projectId',
        search: false,
        ellipsis: true,
        render: (_, record) => record.draftProjectTitle
          || record.projectTitle
          || parseSnapshotName(record.projectSnapshotJson, ['title', 'projectTitle', 'name'])
          || (record.projectId ? `\u9879\u76ee ${record.projectId}` : '-'),
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
        render: (_, record) => record.isCurrentUserDraft
          ? '-'
          : formatRegistrationAmount(record.payableAmountMinor, record.currency),
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
            <Button
              type="text"
              icon={<EyeOutlined />}
              loading={!record.isCurrentUserDraft && loading && registrationId === record.id}
              onClick={() => void (record.isCurrentUserDraft ? startNewRegistration() : openRegistrationFlow(record))}
            >
              {record.status === 'PAID' || record.status === 'CONFIRMED' ? '\u67e5\u770b' : '\u7ee7\u7eed'}
            </Button>
            {!record.isCurrentUserDraft && record.status === 'PENDING_PAYMENT' ? (
              <Button danger type="text" icon={<DeleteOutlined />} onClick={() => removePendingRegistration(record)}>
                取消报名
              </Button>
            ) : null}
            {record.isCurrentUserDraft ? (
              <Button danger type="text" icon={<DeleteOutlined />} onClick={abandonCurrentRegistrationDraft}>
                放弃草稿
              </Button>
            ) : null}
          </Space>
        ),
      },
    ],
    [abandonCurrentRegistrationDraft, competitionTitleMap, loading, openRegistrationFlow, registrationId, removePendingRegistration, responsive.isDesktop, startNewRegistration],
  );
  const registrationBreadcrumb = useMemo(
    () => ({
      items: [{ title: '赛事报名' }],
    }),
    [],
  );

  const registrationTableRequest = useMemo(
    () => buildTableRequest<CompetitionRegistrationListRecord>(async (params) => {
      const pageNo = Math.max(1, Number(params.pageNo) || 1);
      const pageSize = Math.max(1, Number(params.pageSize) || 10);
      const draft = (await readLatestRegistrationDraft())?.draft;
      const draftRecord = buildCurrentUserRegistrationDraftRecord(draft, currentUserId);
      const requestedStatus = typeof params.status === 'string' ? params.status : undefined;
      const registrationKeyword = typeof params.registrationNo === 'string'
        ? params.registrationNo.trim()
        : '';
      const normalizedRegistrationKeyword = registrationKeyword.toLowerCase();
      const draftMatchesKeyword = !normalizedRegistrationKeyword || Boolean(draftRecord && [
        draftRecord.registrationNo,
        draftRecord.participantNo,
        draftRecord.draftCompetitionTitle,
        draftRecord.draftTeamName,
        draftRecord.draftProjectTitle,
      ].some((value) => value?.toLowerCase().includes(normalizedRegistrationKeyword)));

      const loadFormalPage = async (formalPageNo: number) => {
        const response = await listRegistrations(buildFormalRegistrationListQuery(
          formalPageNo,
          pageSize,
          requestedStatus,
          registrationKeyword,
        ));
        const records = currentUserId == null || canViewAllRegistrations
          ? (response.records || [])
          : (response.records || []).filter((record) => record.ownerUserId == null || record.ownerUserId === currentUserId);
        return { records, total: response.total };
      };

      if (requestedStatus === 'DRAFT') {
        return {
          records: pageNo === 1 && draftRecord && draftMatchesKeyword ? [draftRecord] : [],
          total: draftRecord && draftMatchesKeyword ? 1 : 0,
        };
      }

      if (!draftRecord || !draftMatchesKeyword || requestedStatus) {
        const response = await loadFormalPage(pageNo);
        return { records: response.records, total: response.total };
      }

      // The current user's draft is a private, non-registration row. Shift the
      // formal-record window by one so table pagination remains stable.
      const formalStart = Math.max(0, (pageNo - 1) * pageSize - 1);
      const formalEnd = Math.max(0, pageNo * pageSize - 1);
      const firstFormalPage = Math.floor(formalStart / pageSize) + 1;
      const lastFormalPage = formalEnd > formalStart
        ? Math.floor((formalEnd - 1) / pageSize) + 1
        : firstFormalPage;
      const formalPages = await Promise.all(
        Array.from(
          { length: lastFormalPage - firstFormalPage + 1 },
          (_, index) => loadFormalPage(firstFormalPage + index),
        ),
      );
      const formalPageOffset = formalStart - (firstFormalPage - 1) * pageSize;
      const formalRecords = formalPages
        .flatMap((response) => response.records)
        .slice(formalPageOffset, formalPageOffset + (formalEnd - formalStart));
      return {
        records: pageNo === 1 ? [draftRecord, ...formalRecords] : formalRecords,
        total: (formalPages[0]?.total || 0) + 1,
      };
    }),
    [canViewAllRegistrations, currentUserId, readLatestRegistrationDraft],
  );

  if (viewMode === 'list') {
    return (
      <ManagementPage title={'\u8d5b\u4e8b\u62a5\u540d'} breadcrumb={registrationBreadcrumb}>
        <ManagementPageBody>
          <ManagementTable<CompetitionRegistrationListRecord>
            actionRef={registrationActionRef}
            rowKey={(record) => record.isCurrentUserDraft ? `draft:${registrationDraftStorageKey}` : record.id}
            columns={registrationColumns}
            isMobile={responsive.isMobile}
            autoContentWidth
            scroll={{ x: 'max-content' }}
            tableLayout="auto"
            request={registrationTableRequest}
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

  const renderRegistrationParticipantManager = (
    participantType: RegistrationParticipantType,
    participantLabel: string,
    participants: RegistrationTeamMemberDraft[],
    participantFields: RegistrationCollectedField[],
    minMembers: number,
    maxMembers: number,
  ) => {
    const memberTableMinWidth = participantFields.length * 160 + 148;
    const columns: NonNullable<TableProps<RegistrationTeamMemberDraft>['columns']> = [
      ...participantFields.map((field) => ({
        title: (
          <span>
            {field.required ? <span className="competition-registration-member-manager__required">*</span> : null}
            {field.title}
          </span>
        ),
        key: field.itemKey,
        width: 160,
        render: (_: unknown, member: RegistrationTeamMemberDraft) => {
          const fieldValue = getMemberCollectedFieldValue(member, field);
          if ((field.fieldType || 'TEXT').toUpperCase() === 'IMAGE' && typeof fieldValue === 'string') {
            return (
              <Image
                width={40}
                height={40}
                src={normalizeUploadUrl(fieldValue)}
                alt={field.title}
                className="competition-registration-table-image"
              />
            );
          }
          const displayText = getMemberFieldDisplayText(member, field);
          return (
            <Tooltip title={displayText === '-' ? undefined : displayText}>
              <Typography.Text className="competition-registration-table-cell" ellipsis>
                {displayText}
              </Typography.Text>
            </Tooltip>
          );
        },
      })),
      {
        title: '操作',
        key: 'actions',
        fixed: 'right',
        width: 148,
        align: 'right',
        render: (_: unknown, member: RegistrationTeamMemberDraft) => {
          const participantIndex = participants.indexOf(member);
          return (
            <Space size={4} wrap={false}>
              <Button type="link" onClick={() => openMemberInlineEditor({ participantType, participantIndex })}>
                编辑
              </Button>
              <Button danger type="link" onClick={() => removeMemberInline(participantType, participantIndex)}>
                删除
              </Button>
            </Space>
          );
        },
      },
    ];

    return (
      <div className="competition-registration-member-manager">
        <div className="competition-registration-member-manager__header">
          <div>
            <Typography.Title className="competition-registration-member-manager__title" level={5}>
              {participantLabel}信息
            </Typography.Title>
            <Typography.Text type="secondary">人数范围：{minMembers}–{maxMembers} 人</Typography.Text>
          </div>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={participants.length >= maxMembers}
            onClick={() => openMemberInlineEditor({ participantType, participantIndex: 'new' })}
          >
            {participants.length >= maxMembers ? `已达 ${maxMembers} 人上限` : `添加${participantLabel}`}
          </Button>
        </div>
        <DataTable<RegistrationTeamMemberDraft>
          className="competition-registration-member-manager__table"
          size="small"
          isMobile={responsive.isMobile}
          toolBarRender={false}
          options={{ density: false, reload: false, setting: false }}
          columns={columns}
          dataSource={participants}
          rowKey={(_, index) => `${participantType}-${index}`}
          scroll={{ x: memberTableMinWidth }}
          pagination={shouldPaginateRegistrationList(participants.length)
            ? { pageSize: REGISTRATION_LIST_PAGE_SIZE, showSizeChanger: false }
            : false}
          locale={{
            emptyText: minMembers > 0
              ? `请至少添加 ${minMembers} 位${participantLabel}`
              : `当前还没有${participantLabel}`,
          }}
        />
      </div>
    );
  };

  const renderRegistrationProjectForm = () => (
    <section className="competition-registration-project-form">
      <Typography.Title level={5}>项目信息</Typography.Title>
      <Form.Item name="projectId" hidden>
        <Input />
      </Form.Item>
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
      {projectImageField ? (
        <Form.Item label={projectImageField.title} required={projectImageField.required}>
          <Space>
            <Avatar
              shape="square"
              size={64}
              src={normalizeUploadUrl(newProjectImageUrl) || undefined}
              icon={<EyeOutlined />}
            />
            <ImgCrop
              aspect={resolveImageCropAspect(projectImageField.cropAspectRatio)}
              cropShape="rect"
              showGrid
              zoomSlider
              rotationSlider
              modalTitle={`裁切项目图片（${normalizeImageCropAspectRatio('IMAGE', projectImageField.cropAspectRatio)}）`}
              modalOk="确认上传"
              modalCancel="取消"
              modalWidth={520}
              beforeCrop={validateRegistrationImageFile}
            >
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
            </ImgCrop>
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
        </Form.Item>
      ) : null}
      {projectDescriptionField ? (
        <Form.Item
          name="newProjectDescription"
          label={projectDescriptionField.title}
          rules={buildCollectedFieldRule(projectDescriptionField)}
        >
          <Input.TextArea
            rows={3}
            maxLength={1000}
            placeholder={projectDescriptionField.placeholder || projectDescriptionField.title}
          />
        </Form.Item>
      ) : null}
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
    </section>
  );

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
              <ImgCrop
                aspect={resolveImageCropAspect(teamAvatarField.cropAspectRatio)}
                cropShape="rect"
                showGrid
                zoomSlider
                rotationSlider
                modalTitle={`裁切团队头像（${normalizeImageCropAspectRatio('IMAGE', teamAvatarField.cropAspectRatio)}）`}
                modalOk="确认上传"
                modalCancel="取消"
                modalWidth={520}
                beforeCrop={validateRegistrationImageFile}
              >
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
              </ImgCrop>
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
        {renderRegistrationParticipantManager(
          'STUDENT',
          '学生',
          registrationStudents,
          effectiveStudentRegistrationFields,
          participantLimits.studentMinMembers,
          participantLimits.studentMaxMembers,
        )}
        {renderRegistrationParticipantManager(
          'TEACHER',
          '指导老师',
          registrationTeachers,
          effectiveTeacherRegistrationFields,
          participantLimits.teacherMinMembers,
          participantLimits.teacherMaxMembers,
        )}
      </section>
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
                    <Select
                      options={registrationCompetitionOptions.map((item) => ({ label: item.title, value: item.id }))}
                      onChange={(nextCompetitionId) => {
                        const previousCompetitionId = toPositiveId(selectedCompetitionId);
                        if (shouldResetCompetitionMaterialValues(previousCompetitionId, toPositiveId(nextCompetitionId))) {
                          form.setFieldValue('materials', {});
                          setMaterialFileRecords({});
                          setStageForm(undefined);
                        }
                      }}
                    />
                  </Form.Item>
                  {selectedCompetitionId ? (
                    <div className="competition-registration-documents">
                      {registrationSettingsStatus === 'error' ? (
                        <Alert
                          type="error"
                          showIcon
                          title="报名字段配置加载失败，已阻止继续报名"
                          description="请重新加载赛事在数据管理中配置的报名文书、字段和人数限制。"
                          action={<Button size="small" onClick={() => setRegistrationSettingsReloadRevision((current) => current + 1)}>重新加载</Button>}
                        />
                      ) : registrationDocumentsLoading ? (
                        <Alert type="info" showIcon title="正在加载报名文书..." />
                      ) : registrationDocumentStates.length ? (
                        <Space orientation="vertical" size={12} style={{ width: '100%' }}>
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
                              <Space orientation="vertical" size={12} style={{ width: '100%' }}>
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
                              title={`请先完成剩余 ${pendingRegistrationDocumentCount} 份协议确认后再继续。`}
                            />
                          ) : (
                            <Alert type="success" showIcon title="阅读文书条款已确认，可进入下一步。" />
                          )}
                        </Space>
                      ) : (
                        <Alert type="info" showIcon title="当前赛事未配置报名前展示文书，可直接进入下一步。" />
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
              {step === registrationWizardStep.team ? (
                registrationSettingsStatus === 'ready' ? (
                  <>
                    {renderTeamForm()}
                    {renderRegistrationProjectForm()}
                  </>
                ) : (
                  <Alert
                    type={registrationSettingsStatus === 'error' ? 'error' : 'info'}
                    showIcon
                    title={registrationSettingsStatus === 'error' ? '报名字段配置加载失败，已阻止填写与提交' : '正在加载数据管理中的报名字段配置...'}
                    description={registrationSettingsStatus === 'error' ? '重新加载成功后，才能继续填写团队、成员和项目信息。' : undefined}
                    action={registrationSettingsStatus === 'error'
                      ? <Button size="small" onClick={() => setRegistrationSettingsReloadRevision((current) => current + 1)}>重新加载</Button>
                      : undefined}
                  />
                )
              ) : null}
              {step === registrationWizardStep.projectEvidence ? (
                <>
                  {intellectualPropertyFields.length ? (
                    <>
                      <Typography.Title level={5}>知识产权信息</Typography.Title>
                      <Typography.Paragraph type="secondary">可添加多项软件著作权或专利，每项信息单独保存。</Typography.Paragraph>
                      <div className="competition-registration-member-manager competition-registration-evidence-manager">
                        <div className="competition-registration-member-manager__header">
                          <Typography.Text type="secondary">
                            共 {intellectualPropertyEntries.length} 项，超过 5 项后分页展示
                          </Typography.Text>
                          <Button type="primary" icon={<PlusOutlined />} onClick={() => openIntellectualPropertyEditor('new')}>
                            添加知识产权
                          </Button>
                        </div>
                        <DataTable<Record<string, unknown>>
                          size="small"
                          isMobile={responsive.isMobile}
                          toolBarRender={false}
                          options={{ density: false, reload: false, setting: false }}
                          columns={intellectualPropertyColumns}
                          dataSource={intellectualPropertyEntries}
                          rowKey={(_, index) => `intellectual-property-${index}`}
                          scroll={{ x: intellectualPropertyFields.length * 180 + 148 }}
                          pagination={shouldPaginateRegistrationList(intellectualPropertyEntries.length)
                            ? { pageSize: REGISTRATION_LIST_PAGE_SIZE, showSizeChanger: false }
                            : false}
                          locale={{ emptyText: '暂未添加知识产权' }}
                        />
                      </div>
                    </>
                  ) : (
                    <Alert type="info" showIcon title="当前赛事未配置项目佐证字段，可直接进入信息确认。" />
                  )}
                </>
              ) : null}
              {step === registrationWizardStep.preliminaryMaterials ? (
                stageFormLoading ? (
                  <Alert type="info" showIcon title="正在加载初赛材料表单..." />
                ) : fields.length ? (
                  <Space orientation="vertical" size={16} style={{ width: '100%' }}>
                    <Alert
                      type="info"
                      showIcon
                      title={fields.some((field) => field.required)
                        ? `请完成初赛材料，其中 ${fields.filter((field) => field.required).length} 项为必填。`
                        : '本步骤材料均为选填，可按需提交。'}
                    />
                    {fields.map((field) => (
                      <Form.Item
                        key={field.key}
                        name={["materials", field.key]}
                        label={field.label || field.key}
                        rules={[{
                          required: Boolean(field.required),
                          message: `请${field.type === 'file' ? '上传' : '填写'}${field.label || field.key}`,
                        }]}
                      >
                        {field.type === 'textarea' ? (
                          <Input.TextArea rows={4} maxLength={field.maxLength} />
                        ) : field.type === 'file' ? (
                          <MaterialFileUploadInput
                            field={field}
                            competitionUuid={registrationDocumentsCompetitionUuid}
                          />
                        ) : (
                          <Input maxLength={field.maxLength} />
                        )}
                      </Form.Item>
                    ))}
                  </Space>
                ) : (
                  <Alert type="info" showIcon title="当前赛事未配置初赛材料表单，可继续进入信息确认。" />
                )
              ) : null}
              {step === registrationWizardStep.review ? (
                <Space orientation="vertical" style={{ width: '100%' }} size={16}>
                  <Alert type="info" showIcon title="请核对以下全部报名信息。确认后将生成报名订单；生成支付订单后内容将不能修改。" />
                  {missingRequiredMaterialFields.length ? (
                    <Alert
                      type="warning"
                      showIcon
                      title={`还有 ${missingRequiredMaterialFields.length} 项必填材料未上传`}
                      description={missingRequiredMaterialFields.map((field) => field.label || field.key).join('、')}
                      action={<Button size="small" onClick={() => setWizardStep(registrationWizardStep.preliminaryMaterials)}>去上传材料</Button>}
                    />
                  ) : null}
                  {!missingRequiredMaterialFields.length && missingRequiredEvidenceFields.length ? (
                    <Alert
                      type="warning"
                      showIcon
                      title={`还有 ${missingRequiredEvidenceFields.length} 项项目佐证信息未填写`}
                      description={missingRequiredEvidenceFields.map((field) => field.title).join('、')}
                      action={<Button size="small" onClick={() => setWizardStep(registrationWizardStep.projectEvidence)}>去完善</Button>}
                    />
                  ) : null}
                  <Card size="small" title="赛事与报名信息" extra={<Button type="link" onClick={() => setWizardStep(0)}>返回修改</Button>}>
                    <Descriptions size="small" bordered column={responsive.isMobile ? 1 : 2}>
                      <Descriptions.Item label="赛事">{selectedCompetition?.title || '-'}</Descriptions.Item>
                      <Descriptions.Item label="已确认文书">
                        {registrationDocumentStates.map(({ item }) => item.title || '报名文书').join('、') || '无'}
                      </Descriptions.Item>
                      {registrationScopeFields.map((field) => (
                        <Descriptions.Item key={field.itemKey} label={field.title}>
                          {renderCollectedFieldReviewValue(field, form.getFieldValue(['registrationExtraValues', field.itemKey]))}
                        </Descriptions.Item>
                      ))}
                    </Descriptions>
                  </Card>
                  <Card size="small" title="团队与参赛人员" extra={<Button type="link" onClick={() => setWizardStep(registrationWizardStep.team)}>返回修改</Button>}>
                    <Space orientation="vertical" style={{ width: '100%' }} size={16}>
                      <Descriptions size="small" bordered column={responsive.isMobile ? 1 : 2}>
                        <Descriptions.Item label={teamNameField.title}>{form.getFieldValue('newTeamName') || '-'}</Descriptions.Item>
                        {teamFieldSplit.overrides.get('teamType') ? (
                          <Descriptions.Item label={teamFieldSplit.overrides.get('teamType')!.title}>
                            {renderCollectedFieldReviewValue(teamFieldSplit.overrides.get('teamType')!, form.getFieldValue(['newTeam', 'teamType']))}
                          </Descriptions.Item>
                        ) : null}
                        {teamAvatarField ? (
                          <Descriptions.Item label={teamAvatarField.title}>
                            {form.getFieldValue(['newTeam', 'avatarUrl'])
                              ? <Image width={48} height={48} src={normalizeUploadUrl(form.getFieldValue(['newTeam', 'avatarUrl']))} alt={teamAvatarField.title} />
                              : '-'}
                          </Descriptions.Item>
                        ) : null}
                        {teamDescriptionField ? (
                          <Descriptions.Item label={teamDescriptionField.title}>{form.getFieldValue(['newTeam', 'description']) || '-'}</Descriptions.Item>
                        ) : null}
                        {teamFieldSplit.customFields.map((field) => (
                          <Descriptions.Item key={field.itemKey} label={field.title}>
                            {renderCollectedFieldReviewValue(field, form.getFieldValue(['newTeam', 'extraValues', field.itemKey]))}
                          </Descriptions.Item>
                        ))}
                      </Descriptions>
                      <Typography.Title level={5}>学生（{registrationStudents.length}）</Typography.Title>
                      <DataTable<RegistrationTeamMemberDraft>
                        size="small"
                        isMobile={responsive.isMobile}
                        toolBarRender={false}
                        options={{ density: false, reload: false, setting: false }}
                        columns={studentReviewColumns}
                        dataSource={registrationStudents}
                        rowKey={(_, index) => `review-student-${index}`}
                        scroll={{ x: effectiveStudentRegistrationFields.length * 160 }}
                        pagination={shouldPaginateRegistrationList(registrationStudents.length)
                          ? { pageSize: REGISTRATION_LIST_PAGE_SIZE, showSizeChanger: false }
                          : false}
                      />
                      <Typography.Title level={5}>指导老师（{registrationTeachers.length}）</Typography.Title>
                      <DataTable<RegistrationTeamMemberDraft>
                        size="small"
                        isMobile={responsive.isMobile}
                        toolBarRender={false}
                        options={{ density: false, reload: false, setting: false }}
                        columns={teacherReviewColumns}
                        dataSource={registrationTeachers}
                        rowKey={(_, index) => `review-teacher-${index}`}
                        scroll={{ x: effectiveTeacherRegistrationFields.length * 160 }}
                        pagination={shouldPaginateRegistrationList(registrationTeachers.length)
                          ? { pageSize: REGISTRATION_LIST_PAGE_SIZE, showSizeChanger: false }
                          : false}
                      />
                    </Space>
                  </Card>
                  <Card size="small" title="本次报名项目" extra={<Button type="link" onClick={() => setWizardStep(registrationWizardStep.team)}>返回修改</Button>}>
                    <Descriptions size="small" bordered column={responsive.isMobile ? 1 : 2}>
                      <Descriptions.Item label={projectTitleField.title}>{form.getFieldValue('newProjectTitle') || '-'}</Descriptions.Item>
                      {projectImageField ? (
                        <Descriptions.Item label={projectImageField.title}>
                          {form.getFieldValue('newProjectImageUrl')
                            ? <Image width={48} height={48} src={normalizeUploadUrl(form.getFieldValue('newProjectImageUrl'))} alt={projectImageField.title} />
                            : '-'}
                        </Descriptions.Item>
                      ) : null}
                      {projectDescriptionField ? (
                        <Descriptions.Item label={projectDescriptionField.title}>{form.getFieldValue('newProjectDescription') || '-'}</Descriptions.Item>
                      ) : null}
                      {projectCustomFields.map((field) => (
                        <Descriptions.Item key={field.itemKey} label={field.title}>
                          {renderCollectedFieldReviewValue(field, form.getFieldValue(['newProjectExtraValues', field.itemKey]))}
                        </Descriptions.Item>
                      ))}
                    </Descriptions>
                  </Card>
                  <Card size="small" title="项目佐证材料" extra={<Button type="link" onClick={() => setWizardStep(registrationWizardStep.projectEvidence)}>返回修改</Button>}>
                    {intellectualPropertyFields.length ? (
                      <DataTable<Record<string, unknown>>
                        size="small"
                        isMobile={responsive.isMobile}
                        toolBarRender={false}
                        options={{ density: false, reload: false, setting: false }}
                        columns={intellectualPropertyReviewColumns}
                        dataSource={intellectualPropertyEntries}
                        rowKey={(_, index) => `review-intellectual-property-${index}`}
                        scroll={{ x: intellectualPropertyFields.length * 160 }}
                        pagination={shouldPaginateRegistrationList(intellectualPropertyEntries.length)
                          ? { pageSize: REGISTRATION_LIST_PAGE_SIZE, showSizeChanger: false }
                          : false}
                        locale={{ emptyText: '未填写知识产权信息' }}
                      />
                    ) : <Typography.Text type="secondary">当前赛事未配置知识产权字段</Typography.Text>}
                  </Card>
                  <Card size="small" title="初赛材料" extra={<Button type="link" onClick={() => setWizardStep(registrationWizardStep.preliminaryMaterials)}>返回修改</Button>}>
                    {fields.length ? (
                      <Descriptions size="small" bordered column={1}>
                        {fields.map((field) => (
                          <Descriptions.Item key={field.key} label={field.label || field.key}>
                            {field.type === 'file' ? (() => {
                              const fileId = Number(form.getFieldValue(['materials', field.key]));
                              const fileRecord = materialFileRecords[fileId];
                              return fileRecord ? <Space size={4}>
                                <span>{fileRecord.originalFileName}</span>
                                <Button type="link" size="small" href={normalizeUploadUrl(fileRecord.previewUrl || fileRecord.publicUrl)} target="_blank" rel="noopener noreferrer">查看</Button>
                              </Space> : fileId
                                ? '已上传文件'
                                : <Typography.Text type="danger">未上传</Typography.Text>;
                            })() : normalizeDisplayText(form.getFieldValue(['materials', field.key])) || '-'}
                          </Descriptions.Item>
                        ))}
                      </Descriptions>
                    ) : <Typography.Text type="secondary">无需提交初赛材料</Typography.Text>}
                  </Card>
                  <Card size="small" title="应付金额">
                    {registrationCompetitionPricingReady ? (
                      <Descriptions size="small" bordered column={responsive.isMobile ? 1 : 2}>
                        <Descriptions.Item label="金额">
                          <Typography.Title level={4} style={{ margin: 0 }}>{formatRegistrationAmount(previewPayableAmount, selectedCompetition?.currency)}</Typography.Title>
                        </Descriptions.Item>
                        <Descriptions.Item label="计费规则">
                          {selectedCompetition?.feeMode === 'MEMBER' ? `按 ${registrationStudents.length} 位学生计费` : '按团队计费'}
                        </Descriptions.Item>
                      </Descriptions>
                    ) : (
                      <Space>
                        <Spin size="small" />
                        <Typography.Text type="secondary">正在加载赛事收费规则</Typography.Text>
                      </Space>
                    )}
                  </Card>
                </Space>
              ) : null}
              {step === 5 ? (
                <CompetitionPaymentStep
                  registrationNo={registrationRecord?.registrationNo || String(registrationId || '-')}
                  amount={formatRegistrationAmount(
                    registrationRecord?.payableAmountMinor ?? previewPayableAmount,
                    registrationRecord?.currency || selectedCompetition?.currency,
                  )}
                  paymentStatus={paymentStatus}
                  paymentOptions={paymentOptions}
                  selectedProvider={selectedPaymentProvider}
                  onSelectProvider={setSelectedPaymentProvider}
                />
              ) : null}
            </div>
          </Form>
          <div className={`competition-create-actions${step === 5 ? ' competition-create-actions--payment' : ''}`}>
            {registrationDraftSyncStatus !== 'IDLE' ? (
              <Typography.Text
                className="competition-create-draft-status"
                type={registrationDraftSyncStatus === 'SYNC_ERROR' ? 'danger' : 'secondary'}
                title={registrationDraftSavedAt ? new Date(registrationDraftSavedAt).toLocaleString() : undefined}
              >
                {registrationDraftSyncStatus === 'SAVING_LOCAL'
                  ? '正在保存到本机'
                  : registrationDraftSyncStatus === 'SYNCING'
                    ? '正在同步云端'
                    : registrationDraftSyncStatus === 'SYNCED'
                      ? '已同步云端'
                      : registrationDraftSyncStatus === 'SYNC_ERROR'
                        ? '已保存到本机，云端同步失败'
                        : '已保存到本机，待同步云端'}
              </Typography.Text>
            ) : null}
            {step > 0 ? <Button onClick={() => setWizardStep(step - 1)}>上一步</Button> : null}
            {step < 5 ? (
              <Button type="primary" loading={loading} disabled={nextButtonDisabled || !canAdvanceRegistration} onClick={() => void goNext()}>
                {nextButtonText}
              </Button>
            ) : (
              <Button className="competition-payment-submit" type="primary" loading={loading} disabled={stageFormLoading || !canPayRegistration || !selectedPaymentProvider} onClick={() => void pay()}>
                立即支付
              </Button>
            )}
          </div>
          <Modal
            title={memberEditorKey
              ? `${memberEditorKey.participantIndex === 'new' ? '添加' : '编辑'}${memberEditorKey.participantType === 'TEACHER' ? '指导老师' : '学生'}`
              : '人员信息'}
            open={memberEditorKey !== undefined}
            width={720}
            okText="保存"
            cancelText="取消"
            onOk={() => void saveMemberEditor().catch(() => undefined)}
            onCancel={cancelMemberInlineEditor}
            forceRender
            className="competition-registration-editor-modal"
          >
            <Form form={memberForm} layout="vertical" preserve={false}>
              <div className="competition-registration-editor-modal__grid">
                {(memberEditorKey?.participantType === 'TEACHER'
                  ? effectiveTeacherRegistrationFields
                  : effectiveStudentRegistrationFields).map((field) => (
                    <Form.Item
                      key={`${memberEditorKey?.participantType || 'STUDENT'}-${field.itemKey}`}
                      className={(field.fieldType || 'TEXT').toUpperCase() === 'TEXTAREA'
                        || (field.fieldType || 'TEXT').toUpperCase() === 'IMAGE'
                        ? 'competition-registration-editor-modal__wide'
                        : undefined}
                      name={getMemberCollectedFieldFormName(field)}
                      label={field.title}
                      rules={buildCollectedFieldRule(field)}
                    >
                      {renderRegistrationCollectedFieldInput(field)}
                    </Form.Item>
                  ))}
              </div>
            </Form>
          </Modal>
          <Modal
            title={intellectualPropertyEditorIndex === 'new' ? '添加知识产权' : '编辑知识产权'}
            open={intellectualPropertyEditorIndex !== undefined}
            width={720}
            okText="保存"
            cancelText="取消"
            onOk={() => void saveIntellectualPropertyEditor().catch(() => undefined)}
            onCancel={cancelIntellectualPropertyEditor}
            forceRender
            className="competition-registration-editor-modal"
          >
            <Form form={intellectualPropertyForm} layout="vertical" preserve={false}>
              <div className="competition-registration-editor-modal__grid">
                {intellectualPropertyFields.map((field) => (
                  <Form.Item
                    key={field.itemKey}
                    className={(field.fieldType || 'TEXT').toUpperCase() === 'TEXTAREA'
                      || (field.fieldType || 'TEXT').toUpperCase() === 'IMAGE'
                      ? 'competition-registration-editor-modal__wide'
                      : undefined}
                    name={field.itemKey}
                    label={field.title}
                    rules={buildCollectedFieldRule(field)}
                  >
                    {renderRegistrationCollectedFieldInput(field)}
                  </Form.Item>
                ))}
              </div>
            </Form>
          </Modal>
          <Modal
            title="前往付款"
            open={paymentModalOpen}
            onCancel={() => setPaymentModalOpen(false)}
            footer={null}
            destroyOnHidden
          >
            <Space orientation="vertical" size={16} style={{ width: '100%' }}>
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

type CompetitionSettingsConfigModuleKey = 'documents' | 'fields' | 'payments' | 'files' | 'awards';
type CompetitionSettingsModuleKey = CompetitionSettingsSectionKey;
type RegistrationFieldScope = Extract<
  CompetitionConfigItemType,
  'REGISTRATION_FIELD' | 'TEAM_FIELD' | 'MEMBER_FIELD' | 'TEACHER_FIELD' | 'PROJECT_FIELD'
>;
type CompetitionConfigFieldScope = RegistrationFieldScope | 'EXPERT_FIELD';

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

type EditableCompetitionConfigItem = CompetitionConfigItem & {
  metadata?: ConfigItemMetadata;
};

type StorageSpaceOption = {
  label: string;
  value: string;
  defaultStorage?: boolean;
};

type PaymentProviderOption = {
  label: string;
  value: string;
  disabled?: boolean;
};

const localizeLegacyConfigItemTitle = (item: CompetitionConfigItem): CompetitionConfigItem => {
  const legacyTitles: Record<string, { source: string; messageKey: string }> = {
    'AGREEMENT:commitment': { source: 'Commitment', messageKey: 'competition.legacy.commitment' },
    'CONSENT:informed-consent': { source: 'Informed consent', messageKey: 'competition.legacy.informedConsent' },
    'REGISTRATION_FIELD:contact-name': { source: 'Contact name', messageKey: 'competition.legacy.contactName' },
    'REQUIRED_FILE:work-file': { source: 'Work file', messageKey: 'competition.legacy.workFile' },
  };
  const localizedTitle = legacyTitles[`${item.itemType}:${item.itemKey}`];
  return localizedTitle && item.title.trim().toLowerCase() === localizedTitle.source.toLowerCase()
    ? { ...item, title: databaseMessage(localizedTitle.messageKey) }
    : item;
};

const localizeLegacyCompetitionSettings = (settings: CompetitionSettingsRecord): CompetitionSettingsRecord => ({
  ...settings,
  competition: {
    ...settings.competition,
    title: settings.competition.title === 'Untitled competition'
      ? databaseMessage('competition.legacy.untitled')
      : settings.competition.title,
  },
  documents: settings.documents.map(localizeLegacyConfigItemTitle),
  fields: settings.fields.map(localizeLegacyConfigItemTitle),
  files: settings.files.map(localizeLegacyConfigItemTitle),
  stageMaterials: settings.stageMaterials.map(localizeLegacyConfigItemTitle),
  payments: settings.payments.map(localizeLegacyConfigItemTitle),
  timeline: settings.timeline.map(localizeLegacyConfigItemTitle),
  awards: (settings.awards || []).map(localizeLegacyConfigItemTitle),
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
    itemTypes: ['TEAM_SETTINGS', 'REGISTRATION_FIELD', 'TEAM_FIELD', 'MEMBER_FIELD', 'TEACHER_FIELD', 'PROJECT_FIELD', 'EXPERT_FIELD'],
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
    key: 'awards',
    labelId: 'page.competition.settings.module.awards',
    defaultLabel: '获奖设置',
    descriptionId: 'page.competition.settings.module.awards.description',
    defaultDescription: '配置用于生成已发布排行获奖名单的四档奖项。',
    itemTypes: ['AWARD_SETTINGS'],
  },
];

const getCompetitionSettingsModuleLabel = (module: CompetitionSettingsModuleConfig) =>
  formatMessage({ id: module.labelId, defaultMessage: module.defaultLabel });

const getCompetitionSettingsFieldLabel = (
  fieldScope: CompetitionConfigFieldScope,
  fieldGroupLabel?: string,
) => {
  if (fieldScope === 'PROJECT_FIELD') {
    return fieldGroupLabel || '项目信息';
  }
  return {
    REGISTRATION_FIELD: '报名信息',
    TEAM_FIELD: '团队信息',
    MEMBER_FIELD: '学生信息',
    TEACHER_FIELD: '指导老师信息',
    EXPERT_FIELD: '专家信息',
  }[fieldScope];
};

const competitionSettingsMenuItems = [
  { key: 'basic' as const, label: '基础信息' },
  { key: 'registration' as const, label: '报名设置' },
  { key: 'stages' as const, label: '赛程与材料' },
  { key: 'payments' as const, label: '费用设置' },
  { key: 'awards' as const, label: '获奖设置' },
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

const getTeamMemberLimits = (items: CompetitionConfigItem[]) => {
  const settingsItem = items.find((item) => item.itemType === 'TEAM_SETTINGS' && item.itemKey === TEAM_SETTINGS_ITEM_KEY);
  const metadata = parseConfigItemMetadata(settingsItem?.contentJson);
  return getRegistrationParticipantLimits(metadata);
};

const buildTeamSettingsConfigItem = (limits: RegistrationParticipantLimits): CompetitionConfigItem => ({
  itemType: 'TEAM_SETTINGS',
  itemKey: TEAM_SETTINGS_ITEM_KEY,
  title: '参赛人员数量限制',
  contentJson: serializeConfigItemMetadata(buildRegistrationParticipantLimitMetadata(limits)),
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
  getRegistrationDocumentAcceptanceKey(item, index);

const isCompetitionConfigFieldType = (itemType: CompetitionConfigItemType): itemType is CompetitionConfigFieldScope =>
  ['REGISTRATION_FIELD', 'TEAM_FIELD', 'MEMBER_FIELD', 'TEACHER_FIELD', 'PROJECT_FIELD', 'EXPERT_FIELD'].includes(itemType);

const toEditableConfigItems = (items: CompetitionConfigItem[]): EditableCompetitionConfigItem[] =>
  items.map((item) => {
    const fieldScope = isCompetitionConfigFieldType(item.itemType)
      ? item.itemType === 'EXPERT_FIELD' ? 'EXPERT_FIELD' : resolveRegistrationFieldScope(item)
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
        validationRule: fieldScope
          ? resolveRegistrationFieldValidationRule(metadata.fieldType, metadata.validationRule, fieldScope, item.itemKey)
          : metadata.validationRule,
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
        cropAspectRatio: normalizeImageCropAspectRatio(metadata.fieldType, metadata.cropAspectRatio),
      },
    };
  });

const buildAutomaticConfigItemKey = (itemType: CompetitionConfigItemType, title: string | undefined, index: number) => {
  const titleKey = normalizeConfigKey((title || '').trim().replace(/\s+/g, '-').toLowerCase());
  return titleKey || `${itemType.toLowerCase()}-${index + 1}`;
};

const normalizeFileStageCode = (value?: string): NonNullable<ConfigItemMetadata['stageCode']> =>
  value === 'PRELIMINARY' || value === '初赛' || value === 'STAGE_1'
    ? 'PRELIMINARY'
    : value === 'FINAL' || value === '决赛' || value === 'STAGE_2'
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
    const normalizedFieldMetadata = isCompetitionConfigFieldType(itemType)
      ? {
          ...documentMetadata,
          validationRule: resolveRegistrationFieldValidationRule(
            documentMetadata?.fieldType,
            documentMetadata?.validationRule,
            itemType,
            itemKey,
          ),
          cropAspectRatio: normalizeImageCropAspectRatio(
            documentMetadata?.fieldType,
            documentMetadata?.cropAspectRatio,
          ),
        }
      : documentMetadata;
    const nextMetadata = fileStageCode
      ? {
          ...normalizedFieldMetadata,
          stageCode: fileStageCode,
          stageName: resolveFileStageName(fileStageCode),
          fileFormat: normalizeFileFormat(normalizedFieldMetadata?.fileFormat),
          materialType: fileStageCode === 'GENERAL' ? undefined : 'FILE',
        }
      : normalizedFieldMetadata;
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
  { label: '手机号（自动校验）', value: 'MOBILE' },
  { label: '邮箱（自动校验）', value: 'EMAIL' },
];

const validationRuleOptions = [
  { label: '不限制', value: 'NONE' },
  { label: '人员姓名（中文、英文或间隔号）', value: 'PERSON_NAME' },
  { label: '名称文本（中英文、数字及常用符号）', value: 'DISPLAY_NAME' },
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

const buildCompetitionStorageSpaceOptions = (competition: CompetitionRecord): StorageSpaceOption[] => {
  const storageKey = competition.storageKey || buildCompetitionStorageKey(competition.uuid);
  return storageKey ? [{
    value: storageKey,
    label: `比赛专属存储 / ${storageKey}`,
    defaultStorage: false,
  }] : [];
};

const INTELLECTUAL_PROPERTY_GROUP_LABEL = '知识产权信息';

const protectedCollectionFieldKeys: Partial<Record<CompetitionConfigFieldScope, Set<string>>> = {
  TEAM_FIELD: new Set(['teamName']),
  MEMBER_FIELD: new Set(['memberName']),
  TEACHER_FIELD: new Set(['memberName']),
  PROJECT_FIELD: new Set(['title']),
  EXPERT_FIELD: new Set(['name', 'expertise']),
};

const isParticipantNameStandardField = (
  scope: CompetitionConfigFieldScope,
  itemKey: string,
) => ['MEMBER_FIELD', 'TEACHER_FIELD'].includes(scope)
  && ['membername', 'teachername', 'name'].includes(normalizeCollectedFieldConfigKey(itemKey));

const fieldScopeOptions: Array<{ label: string; value: CompetitionConfigFieldScope }> = [
  { label: '报名信息', value: 'REGISTRATION_FIELD' },
  { label: '团队信息', value: 'TEAM_FIELD' },
  { label: '学生信息', value: 'MEMBER_FIELD' },
  { label: '指导老师信息', value: 'TEACHER_FIELD' },
  { label: '项目信息', value: 'PROJECT_FIELD' },
  { label: '专家信息', value: 'EXPERT_FIELD' },
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

const emptyConfigItem = (itemType: CompetitionConfigItemType, sortOrder: number): CompetitionConfigItem => ({
  itemType,
  itemKey: `${itemType.toLowerCase()}-${Date.now()}`,
  title: '',
  contentJson: serializeConfigItemMetadata(
    isCompetitionConfigFieldType(itemType)
      ? { fieldScope: itemType, fieldType: 'TEXT', validationRule: 'NONE' }
      : itemType === 'REQUIRED_FILE'
        ? { fileFormat: 'ANY', maxSizeMb: 20, stageCode: 'GENERAL', stageName: '通用' }
        : itemType === 'STAGE_MATERIAL'
          ? { materialType: 'FILE', stageCode: 'PRELIMINARY', stageName: '初赛', fileFormat: 'ANY', maxSizeMb: 20 }
          : itemType === 'AGREEMENT' || itemType === 'CONSENT'
            ? { documentKind: itemType, readingSeconds: 0 }
            : {},
  ),
  contentText: '',
  sortOrder,
  requiredFlag: false,
  enabled: true,
});

const ensureCombinedTeamFieldItems = (
  items: EditableCompetitionConfigItem[],
): EditableCompetitionConfigItem[] => {
  const normalizedItems = items.map((item) => {
    const scope = (item.metadata?.fieldScope || item.itemType) as CompetitionConfigFieldScope;
    if (!isParticipantNameStandardField(scope, item.itemKey)) {
      return item;
    }
    return {
      ...item,
      itemType: scope,
      itemKey: 'memberName',
      requiredFlag: true,
      enabled: true,
      metadata: {
        ...item.metadata,
        fieldScope: scope,
        fieldType: 'TEXT',
        validationRule: 'PERSON_NAME',
        standardField: true,
      },
    };
  });
  const hasField = (scope: CompetitionConfigFieldScope, itemKey: string) => normalizedItems.some((item) => (
    (item.metadata?.fieldScope || item.itemType) === scope
      && normalizeCollectedFieldConfigKey(item.itemKey) === normalizeCollectedFieldConfigKey(itemKey)
  ));
  const requiredNameField = (
    scope: Extract<CompetitionConfigFieldScope, 'MEMBER_FIELD' | 'TEACHER_FIELD'>,
    title: string,
    sortOrder: number,
  ) => toEditableConfigItems([{
    ...emptyConfigItem(scope, sortOrder),
    itemKey: 'memberName',
    title,
    requiredFlag: true,
    contentJson: serializeConfigItemMetadata({
      fieldScope: scope,
      fieldType: 'TEXT',
      placeholder: `请输入${title}`,
      validationRule: 'PERSON_NAME',
      standardField: true,
    }),
  }])[0];

  return [
    ...normalizedItems,
    ...(!hasField('MEMBER_FIELD', 'memberName')
      ? [requiredNameField('MEMBER_FIELD', '学生姓名', 110)]
      : []),
    ...(!hasField('TEACHER_FIELD', 'memberName')
      ? [requiredNameField('TEACHER_FIELD', '指导老师姓名', 110)]
      : []),
  ];
};

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
  if (key === 'awards') {
    return settings.awards || [];
  }
  return [];
};

const splitFileConfigItemsByModule = (items: CompetitionConfigItem[]) => ({
  files: items.filter((item) => item.itemType === 'REQUIRED_FILE'),
  stageMaterials: items.filter((item) => item.itemType === 'STAGE_MATERIAL'),
});

const isFormValidationError = (error: unknown) =>
  Boolean(error && typeof error === 'object' && 'errorFields' in error);

const renderConfigItemFields = (
  module: CompetitionSettingsModuleConfig,
  fieldName: number,
  storageSpaceOptions: StorageSpaceOption[],
  forcedFieldScope?: CompetitionConfigFieldScope,
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
          {forcedFieldScope ? (
            <Form.Item name={[fieldName, 'metadata', 'fieldScope']} initialValue={forcedFieldScope} hidden>
              <Input />
            </Form.Item>
          ) : (
            <Form.Item name={[fieldName, 'metadata', 'fieldScope']} label="适用范围" initialValue="REGISTRATION_FIELD">
              <Select options={fieldScopeOptions} />
            </Form.Item>
          )}
          <Form.Item name={[fieldName, 'metadata', 'fieldType']} label="字段类型" rules={[{ required: true, message: '请选择字段类型' }]}>
            <Select options={fieldTypeOptions} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(previous, current) => (
            previous?.items?.[fieldName]?.metadata?.fieldType !== current?.items?.[fieldName]?.metadata?.fieldType
          )}>
            {({ getFieldValue }) => getFieldValue(['items', fieldName, 'metadata', 'fieldType']) === 'IMAGE' ? (
              <Form.Item
                name={[fieldName, 'metadata', 'cropAspectRatio']}
                label="裁切比例"
                initialValue="1:1"
                rules={[{ required: true, message: '请选择裁切比例' }]}
              >
                <Select options={[...IMAGE_CROP_ASPECT_RATIO_OPTIONS]} />
              </Form.Item>
            ) : null}
          </Form.Item>
          <Form.Item
            name={[fieldName, 'itemKey']}
            label="字段标识"
            normalize={normalizeConfigKey}
            rules={[
              { required: true, message: '请输入字段标识' },
              ({ getFieldValue }) => ({
                validator: () => isConfigModuleItemKeyDuplicate(getFieldValue('items') || [], fieldName)
                  ? Promise.reject(new Error('同一适用范围内的字段标识不能重复'))
                  : Promise.resolve(),
              }),
            ]}
          >
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
        <Form.Item
          name={[fieldName, 'metadata', 'fileFormat']}
          label="文件格式"
          rules={[{ required: true, message: '请选择文件格式' }]}
        >
          <Select options={fileFormatOptions} />
        </Form.Item>
        <Form.Item
          name={[fieldName, 'metadata', 'storageKey']}
          label="保存位置"
          rules={[{ required: true, message: '请选择保存位置' }]}
        >
          <Select
            disabled={storageSpaceOptions.length === 1}
            showSearch
            optionFilterProp="label"
            placeholder="请选择保存位置"
            options={storageSpaceOptions}
            notFoundContent="没有可用的存储空间"
          />
        </Form.Item>
        <Form.Item
          name={[fieldName, 'metadata', 'maxSizeMb']}
          label="大小上限 MB"
          rules={[{ required: true, message: '请输入大小上限' }]}
        >
          <InputNumber min={1} max={1024} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item className="competition-config-grid__full" name={[fieldName, 'metadata', 'description']} label="上传说明">
          <Input.TextArea rows={2} placeholder="说明文件要求、命名规则或盖章要求" maxLength={200} />
        </Form.Item>
      </div>
    );
  }

  return null;
};

const renderFieldSettingsTable = (
  fields: Array<{ key: number; name: number }>,
  add: (defaultValue?: EditableCompetitionConfigItem) => void,
  remove: (index: number | number[]) => void,
  scope: CompetitionConfigFieldScope,
  markDraftChanged: () => void,
  reorderField: (fields: Array<{ key: number; name: number }>, fromIndex: number, toIndex: number) => void,
  openOptionsEditor: (fieldName: number, fieldTitle?: string, options?: string) => void,
  fieldGroupLabel?: string,
  standalone = false,
) => {
  const fieldName = (index: number, ...path: Array<string | number>) => standalone
    ? ['items', index, ...path]
    : [index, ...path];
  return (
    <Space className="competition-config-list" orientation="vertical" size={16}>
      <div className="competition-field-table">
        <div className="competition-field-table__head">
          <span>字段名称</span>
          <span>字段标识</span>
          <span>类型</span>
          <span>占位提示</span>
          <span>类型配置</span>
          <span>必填</span>
          <span>排序</span>
          <span>启用</span>
          <span>操作</span>
        </div>
        {fields.map((field, index) => (
          <div className="competition-field-table__row" key={field.key}>
            <Form.Item name={fieldName(field.name, 'title')} rules={[{ required: true, message: '请输入字段名称' }]}>
              <Input placeholder="字段名称" maxLength={64} />
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {({ getFieldValue }) => {
                const itemScope = (getFieldValue(['items', field.name, 'metadata', 'fieldScope']) || scope) as CompetitionConfigFieldScope;
                const itemKey = String(getFieldValue(['items', field.name, 'itemKey']) || '');
                return (
                  <Form.Item
                    name={fieldName(field.name, 'itemKey')}
                    normalize={normalizeConfigKey}
                    rules={[
                      { required: true, message: '请输入字段标识' },
                      ({ getFieldValue: getFormFieldValue }) => ({
                        validator: () => isConfigModuleItemKeyDuplicate(getFormFieldValue('items') || [], field.name)
                          ? Promise.reject(new Error('同一适用范围内的字段标识不能重复'))
                          : Promise.resolve(),
                      }),
                    ]}
                  >
                    <Input
                      disabled={isParticipantNameStandardField(itemScope, itemKey)}
                      placeholder="字段标识"
                      maxLength={64}
                    />
                  </Form.Item>
                );
              }}
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {({ getFieldValue }) => {
                const itemScope = (getFieldValue(['items', field.name, 'metadata', 'fieldScope']) || scope) as CompetitionConfigFieldScope;
                const itemKey = String(getFieldValue(['items', field.name, 'itemKey']) || '');
                return (
                  <Form.Item name={fieldName(field.name, 'metadata', 'fieldType')} rules={[{ required: true, message: '请选择字段类型' }]}>
                    <Select disabled={isParticipantNameStandardField(itemScope, itemKey)} options={fieldTypeOptions} />
                  </Form.Item>
                );
              }}
            </Form.Item>
            <Form.Item name={fieldName(field.name, 'metadata', 'placeholder')}>
              <Input placeholder="占位提示" maxLength={120} />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(previous, current) => (
              previous?.items?.[field.name]?.metadata?.fieldType !== current?.items?.[field.name]?.metadata?.fieldType
            )}>
              {({ getFieldValue }) => {
                const fieldType = getFieldValue(['items', field.name, 'metadata', 'fieldType']);
                if (['SELECT', 'MULTI_SELECT'].includes(fieldType)) {
                  return (
                    <>
                      <Form.Item name={fieldName(field.name, 'metadata', 'options')} hidden>
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
                  );
                }
                if (fieldType === 'IMAGE') {
                  return (
                    <Form.Item
                      name={fieldName(field.name, 'metadata', 'cropAspectRatio')}
                      initialValue="1:1"
                      rules={[{ required: true, message: '请选择裁切比例' }]}
                    >
                      <Select
                        aria-label="裁切比例"
                        options={[...IMAGE_CROP_ASPECT_RATIO_OPTIONS]}
                      />
                    </Form.Item>
                  );
                }
                return <Typography.Text type="secondary">—</Typography.Text>;
              }}
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {({ getFieldValue }) => {
                const itemScope = (getFieldValue(['items', field.name, 'metadata', 'fieldScope']) || scope) as CompetitionConfigFieldScope;
                const itemKey = String(getFieldValue(['items', field.name, 'itemKey']) || '');
                return (
                  <Form.Item name={fieldName(field.name, 'requiredFlag')} valuePropName="checked">
                    <Switch disabled={isParticipantNameStandardField(itemScope, itemKey)} />
                  </Form.Item>
                );
              }}
            </Form.Item>
            <div className="competition-field-table__sort-cell">
              <Form.Item name={fieldName(field.name, 'sortOrder')} hidden>
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
            <Form.Item noStyle shouldUpdate>
              {({ getFieldValue }) => {
                const itemScope = (getFieldValue(['items', field.name, 'metadata', 'fieldScope']) || scope) as CompetitionConfigFieldScope;
                const itemKey = String(getFieldValue(['items', field.name, 'itemKey']) || '');
                return (
                  <Form.Item name={fieldName(field.name, 'enabled')} valuePropName="checked">
                    <Switch disabled={isParticipantNameStandardField(itemScope, itemKey)} />
                  </Form.Item>
                );
              }}
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {({ getFieldValue }) => {
                const itemScope = (getFieldValue(['items', field.name, 'metadata', 'fieldScope']) || scope) as CompetitionConfigFieldScope;
                const itemKey = String(getFieldValue(['items', field.name, 'itemKey']) || '');
                const isProtectedField = Boolean(protectedCollectionFieldKeys[itemScope]?.has(itemKey));
                return (
                  <Popconfirm
                    title={isProtectedField
                      ? '核心识别字段不可删除'
                      : `确认删除${itemKey ? `“${String(getFieldValue(['items', field.name, 'title']) || itemKey)}”` : '该'}字段？`}
                    okText="确认删除"
                    cancelText="取消"
                    onConfirm={() => {
                      remove(field.name);
                      markDraftChanged();
                    }}
                  >
                    <Button
                      danger
                      disabled={isProtectedField}
                      title={isProtectedField ? '核心识别字段不可删除' : '删除字段'}
                      type="link"
                    >
                      删除
                    </Button>
                  </Popconfirm>
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
          markDraftChanged();
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
  fieldScope?: CompetitionConfigFieldScope;
  fieldGroupLabel?: string;
  fileStageCode?: string;
  paymentProviderOptions?: PaymentProviderOption[];
  onSaved: (settings: CompetitionSettingsRecord) => void;
};

const EMPTY_PAYMENT_PROVIDER_OPTIONS: PaymentProviderOption[] = [];

const ConfigModulePanel = forwardRef<CompetitionSettingsPanelHandle, ConfigModulePanelProps>(({
  competitionUuid,
  module,
  items,
  storageSpaceOptions,
  fieldScope,
  fieldGroupLabel,
  fileStageCode,
  paymentProviderOptions = EMPTY_PAYMENT_PROVIDER_OPTIONS,
  onSaved,
}, ref) => {
  const responsive = useResponsive();
  const [form] = Form.useForm<{
    items: EditableCompetitionConfigItem[];
    studentMinMembers?: number;
    studentMaxMembers?: number;
    teacherMinMembers?: number;
    teacherMaxMembers?: number;
  }>();
  const [optionsEditor, setOptionsEditor] = useState<{
    fieldName: number;
    fieldTitle?: string;
    value: string;
  }>();
  const draftRevisionRef = useRef(0);
  const syncedRevisionRef = useRef(0);
  const hydratedContextKeyRef = useRef<string | undefined>(undefined);
  const draftContextKey = `${competitionUuid}:${module.key}:${fileStageCode || ''}`;

  const getInitialValues = useCallback(() => {
    const limits = getTeamMemberLimits(items);
    const dedicatedStorageKey = storageSpaceOptions[0]?.value;
    const sourceItems = removeDeprecatedRegistrationContactFields(
      items.filter((item) => item.itemType !== 'TEAM_SETTINGS'),
    ).map((item) => dedicatedStorageKey && ['REQUIRED_FILE', 'STAGE_MATERIAL'].includes(item.itemType)
      ? {
          ...item,
          contentJson: serializeConfigItemMetadata({
            ...parseConfigItemMetadata(item.contentJson),
            storageKey: dedicatedStorageKey,
          }),
        }
      : item);
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
    const moduleItems = module.key === 'payments'
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
    const initialItems = module.key === 'fields' && fieldScope === 'TEAM_FIELD'
      ? ensureCombinedTeamFieldItems(moduleItems)
      : moduleItems;
    return {
      items: initialItems,
      ...limits,
    };
  }, [fieldScope, fileStageCode, items, module.key, paymentProviderOptions, storageSpaceOptions]);

  useEffect(() => {
    if (!shouldHydrateConfigModuleDraft({
      hydratedContextKey: hydratedContextKeyRef.current,
      nextContextKey: draftContextKey,
      draftRevision: draftRevisionRef.current,
      syncedRevision: syncedRevisionRef.current,
    })) {
      return;
    }
    form.setFieldsValue(getInitialValues());
    hydratedContextKeyRef.current = draftContextKey;
    syncedRevisionRef.current = draftRevisionRef.current;
  }, [draftContextKey, form, getInitialValues]);

  const save = useCallback(async () => {
    const saveRevision = draftRevisionRef.current;
    const values = form.getFieldsValue(true);
    const effectiveItems = module.key === 'fields' && fieldScope === 'TEAM_FIELD'
      ? ensureCombinedTeamFieldItems(values.items || [])
      : values.items || [];
    const validationScope = module.key === 'fields' && fieldScope
      ? fieldScope === 'TEAM_FIELD'
        ? { fieldScopes: ['TEAM_FIELD', 'MEMBER_FIELD', 'TEACHER_FIELD'] }
        : {
          fieldScope,
          includeGroupLabel: fieldGroupLabel === INTELLECTUAL_PROPERTY_GROUP_LABEL
            ? INTELLECTUAL_PROPERTY_GROUP_LABEL
            : undefined,
          excludeGroupLabel: fieldScope === 'PROJECT_FIELD' && fieldGroupLabel !== INTELLECTUAL_PROPERTY_GROUP_LABEL
            ? INTELLECTUAL_PROPERTY_GROUP_LABEL
            : undefined,
        }
      : undefined;
    if (!isConfigModuleReadyToSave(module.key, effectiveItems, validationScope)) {
      return false;
    }
    if (shouldValidateTeamMemberLimitsForPage(module.key, fieldScope)) {
      const studentMinMembers = Number(values.studentMinMembers);
      const studentMaxMembers = Number(values.studentMaxMembers);
      const teacherMinMembers = Number(values.teacherMinMembers);
      const teacherMaxMembers = Number(values.teacherMaxMembers);
      if (!Number.isInteger(studentMinMembers) || !Number.isInteger(studentMaxMembers)
        || !Number.isInteger(teacherMinMembers) || !Number.isInteger(teacherMaxMembers)
        || studentMinMembers < 1 || studentMaxMembers > MAX_REGISTRATION_PARTICIPANTS_PER_TYPE
        || teacherMinMembers < 0 || teacherMaxMembers > MAX_REGISTRATION_PARTICIPANTS_PER_TYPE
        || studentMinMembers > studentMaxMembers || teacherMinMembers > teacherMaxMembers) {
        return false;
      }
    }
    try {
      const fieldItems = toConfigItems(effectiveItems);
      const configItems = module.key === 'fields'
        ? [
            buildTeamSettingsConfigItem({
              studentMinMembers: Number(values.studentMinMembers),
              studentMaxMembers: Number(values.studentMaxMembers),
              teacherMinMembers: Number(values.teacherMinMembers),
              teacherMaxMembers: Number(values.teacherMaxMembers),
            }),
            ...fieldItems,
          ]
        : fieldItems;
      const saved = module.key === 'files'
        ? await (async () => {
            const groupedItems = splitFileConfigItemsByModule(fileStageCode
              ? mergeStageMaterialSaveItems(
                  items,
                  fileStageCode,
                  configItems,
                  getFileConfigItemStageCode,
                )
              : configItems);
            return saveCompetitionSettingsModule(
              competitionUuid,
              'materials',
              [...groupedItems.files, ...groupedItems.stageMaterials],
              API_OPTS.SILENT,
            );
          })()
        : await saveCompetitionSettingsModule(competitionUuid, module.key, configItems, API_OPTS.SILENT);
      if (isConfigModuleDraftSaveCurrent(saveRevision, draftRevisionRef.current)) {
        syncedRevisionRef.current = saveRevision;
      }
      onSaved(saved);
      return true;
    } catch (error) {
      showErrorMessage(error, formatMessage({ id: 'page.competition.settings.item.saveFailed', defaultMessage: 'Settings save failed' }));
      return false;
    }
  }, [competitionUuid, fieldGroupLabel, fieldScope, fileStageCode, form, items, module.key, onSaved]);
  const markDraftChanged = useCallback(() => {
    draftRevisionRef.current += 1;
  }, []);

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
    markDraftChanged();
  }, [form, markDraftChanged]);

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
    markDraftChanged();
  }, [form, markDraftChanged, optionsEditor]);

  useImperativeHandle(ref, () => ({
    saveNow: save,
  }), [save]);

  const renderCombinedTeamSettings = () => (
    <Form.Item noStyle shouldUpdate>
      {({ getFieldValue }) => {
        const currentItems = (getFieldValue('items') || []) as EditableCompetitionConfigItem[];
        const fields = currentItems.map((_, index) => ({ key: index, name: index }));
        const fieldsForScope = (scope: CompetitionConfigFieldScope) => fields.filter((field) => {
          const item = currentItems[field.name];
          return (item?.metadata?.fieldScope || item?.itemType) === scope;
        });
        const add = (defaultValue?: EditableCompetitionConfigItem) => {
          if (defaultValue) {
            form.setFieldValue('items', [...currentItems, defaultValue]);
          }
        };
        const remove = (index: number | number[]) => {
          const indexes = new Set(Array.isArray(index) ? index : [index]);
          form.setFieldValue('items', currentItems.filter((_, itemIndex) => !indexes.has(itemIndex)));
        };
        const openOptionsEditor = (fieldName: number, fieldTitle?: string, options?: string) => setOptionsEditor({
          fieldName,
          fieldTitle,
          value: options || '',
        });
        return (
          <div className="competition-config-sections">
            <section className="competition-config-section">
              <Typography.Title level={5} className="competition-config-section__title">团队设置</Typography.Title>
              {renderFieldSettingsTable(
                fieldsForScope('TEAM_FIELD'),
                add,
                remove,
                'TEAM_FIELD',
                markDraftChanged,
                reorderField,
                openOptionsEditor,
                undefined,
                true,
              )}
            </section>
            <Divider className="competition-config-section__divider" />
            <section className="competition-config-section">
              <Typography.Title level={5} className="competition-config-section__title">学生信息设置</Typography.Title>
              <div className="competition-config-grid">
                <Form.Item
                  name="studentMinMembers"
                  label="学生最小人数"
                  dependencies={['studentMaxMembers']}
                  rules={[
                    { required: true, message: '请输入学生最小人数' },
                    ({ getFieldValue: getLimitFieldValue }) => ({
                      validator: (_, value) => Number(value) <= Number(getLimitFieldValue('studentMaxMembers'))
                        ? Promise.resolve()
                        : Promise.reject(new Error('学生最小人数不能大于最大人数')),
                    }),
                  ]}
                >
                  <InputNumber min={1} max={MAX_REGISTRATION_PARTICIPANTS_PER_TYPE} precision={0} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item
                  name="studentMaxMembers"
                  label="学生最大人数"
                  dependencies={['studentMinMembers']}
                  rules={[
                    { required: true, message: '请输入学生最大人数' },
                    ({ getFieldValue: getLimitFieldValue }) => ({
                      validator: (_, value) => Number(value) >= Number(getLimitFieldValue('studentMinMembers'))
                        ? Promise.resolve()
                        : Promise.reject(new Error('学生最大人数不能小于最小人数')),
                    }),
                  ]}
                >
                  <InputNumber min={1} max={MAX_REGISTRATION_PARTICIPANTS_PER_TYPE} precision={0} style={{ width: '100%' }} />
                </Form.Item>
              </div>
              <Typography.Paragraph type="secondary">学生人数用于报名资格校验；按人收费时仅计算学生人数。</Typography.Paragraph>
              {renderFieldSettingsTable(
                fieldsForScope('MEMBER_FIELD'),
                add,
                remove,
                'MEMBER_FIELD',
                markDraftChanged,
                reorderField,
                openOptionsEditor,
                undefined,
                true,
              )}
            </section>
            <Divider className="competition-config-section__divider" />
            <section className="competition-config-section">
              <Typography.Title level={5} className="competition-config-section__title">指导老师信息设置</Typography.Title>
              <div className="competition-config-grid">
                <Form.Item
                  name="teacherMinMembers"
                  label="指导老师最小人数"
                  dependencies={['teacherMaxMembers']}
                  rules={[
                    { required: true, message: '请输入指导老师最小人数' },
                    ({ getFieldValue: getLimitFieldValue }) => ({
                      validator: (_, value) => Number(value) <= Number(getLimitFieldValue('teacherMaxMembers'))
                        ? Promise.resolve()
                        : Promise.reject(new Error('指导老师最小人数不能大于最大人数')),
                    }),
                  ]}
                >
                  <InputNumber min={0} max={MAX_REGISTRATION_PARTICIPANTS_PER_TYPE} precision={0} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item
                  name="teacherMaxMembers"
                  label="指导老师最大人数"
                  dependencies={['teacherMinMembers']}
                  rules={[
                    { required: true, message: '请输入指导老师最大人数' },
                    ({ getFieldValue: getLimitFieldValue }) => ({
                      validator: (_, value) => Number(value) >= Number(getLimitFieldValue('teacherMinMembers'))
                        ? Promise.resolve()
                        : Promise.reject(new Error('指导老师最大人数不能小于最小人数')),
                    }),
                  ]}
                >
                  <InputNumber min={0} max={MAX_REGISTRATION_PARTICIPANTS_PER_TYPE} precision={0} style={{ width: '100%' }} />
                </Form.Item>
              </div>
              <Typography.Paragraph type="secondary">指导老师独立校验人数，不计入学生人数和报名费用。</Typography.Paragraph>
              {renderFieldSettingsTable(
                fieldsForScope('TEACHER_FIELD'),
                add,
                remove,
                'TEACHER_FIELD',
                markDraftChanged,
                reorderField,
                openOptionsEditor,
                undefined,
                true,
              )}
            </section>
          </div>
        );
      }}
    </Form.Item>
  );

  return (
    <section className="competition-config-module">
      {module.key === 'fields' && fieldScope === 'TEAM_FIELD'
        ? null
        : module.key === 'files' && fileStageCode ? null : (
        <div className="competition-config-module__header">
          <Typography.Title className="competition-config-module__title" level={4}>
            {module.key === 'fields' && fieldScope
              ? getCompetitionSettingsFieldLabel(fieldScope, fieldGroupLabel)
              : getCompetitionSettingsModuleLabel(module)}
          </Typography.Title>
        </div>
      )}
      {module.key === 'payments' && !paymentProviderOptions.length ? (
        <Alert
          showIcon
          type="warning"
          title="暂无可绑定的支付渠道"
          description="请先在系统支付设置中完成渠道配置并启用，已启用的渠道会自动出现在这里。"
          style={{ marginBottom: 16 }}
        />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={getInitialValues()}
        onValuesChange={markDraftChanged}
      >
        {module.key === 'fields' && fieldScope === 'TEAM_FIELD' ? renderCombinedTeamSettings() : (
          <Form.List name="items">
            {(fields, { add, remove }) => module.key === 'fields' ? (
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
                  return {
                    key: scopeOption.value,
                    label: fieldGroupLabel || scopeOption.label,
                    children: (
                      <Space className="competition-config-list" orientation="vertical" size={16}>
                        {renderFieldSettingsTable(
                          scopedFields,
                          add,
                          remove,
                          scopeOption.value,
                          markDraftChanged,
                          reorderField,
                          (fieldName, fieldTitle, options) => setOptionsEditor({
                            fieldName,
                            fieldTitle,
                            value: options || '',
                          }),
                          fieldGroupLabel,
                        )}
                      </Space>
                    ),
                  };
                })}
              />
            ) : module.key === 'payments' ? (
              <DataTable
                className="competition-payment-provider-table"
                rowKey="key"
                isMobile={responsive.isMobile}
                size="small"
                pagination={false}
                toolBarRender={false}
                options={{ density: false, reload: false, setting: false }}
                dataSource={fields.filter((field) => paymentProviderOptions.some(
                  (option) => option.value === form.getFieldValue(['items', field.name, 'itemKey']),
                ))}
                locale={{ emptyText: '系统支付设置中暂无已配置并启用的渠道' }}
                columns={[
                  {
                    title: '支付渠道',
                    key: 'provider',
                    width: 180,
                    render: (_, field) => {
                      const item = form.getFieldValue(['items', field.name]) as EditableCompetitionConfigItem | undefined;
                      const provider = paymentProviderOptions.find((option) => option.value === item?.itemKey);
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
                    width: 110,
                    render: () => <Tag color="success">可用</Tag>,
                  },
                  {
                    title: '绑定状态',
                    key: 'enabled',
                    width: 140,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'enabled']} valuePropName="checked" style={{ marginBottom: 0 }}>
                        <Switch aria-label="绑定状态" />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '排序',
                    key: 'sortOrder',
                    width: 110,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'sortOrder']} style={{ marginBottom: 0 }}>
                        <InputNumber min={0} precision={0} style={{ width: 110 }} />
                      </Form.Item>
                    ),
                  },
                ]}
              />
            ) : (
              <Space className="competition-config-list" orientation="vertical" size={16}>
              {fields.map((field, index) => (
                <Card
                  key={field.key}
                  className="competition-config-item"
                  size="small"
                  title={
                    <Space size={8} wrap>
                      <span>{module.key === 'payments'
                        ? (paymentProviderOptions.find((option) => option.value === form.getFieldValue(['items', field.name, 'itemKey']))?.label
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
                    <Popconfirm
                      title="确认删除该配置项？"
                      okText="确认删除"
                      cancelText="取消"
                      onConfirm={() => {
                        remove(field.name);
                        markDraftChanged();
                      }}
                    >
                      <Button danger>
                        {formatMessage({ id: 'page.competition.settings.item.remove', defaultMessage: 'Remove' })}
                      </Button>
                    </Popconfirm>
                  )}
                >
                  <Space orientation="vertical" style={{ width: '100%' }}>
                    <div className="competition-config-item__fields">
                      {renderConfigItemFields(module, field.name, storageSpaceOptions, fieldScope)}
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
                      {module.key !== 'fields' && module.key !== 'files' ? (
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
                  if (module.key === 'files' && storageSpaceOptions[0]?.value) {
                    nextItem.metadata = {
                      ...nextItem.metadata,
                      storageKey: storageSpaceOptions[0].value,
                    };
                  }
                  add(nextItem);
                  markDraftChanged();
                }}
              >
                {formatMessage({ id: 'page.competition.settings.item.add', defaultMessage: 'Add item' })}
              </Button>
            </Space>
            )
          }
          </Form.List>
        )}
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
      } as CompetitionFormValues, { preserveTimelineFrom: competition }), API_OPTS.SILENT);
      onSaved(saved);
      return true;
    } catch (error) {
      showErrorMessage(error, '基础信息保存失败');
      return false;
    }
  }, [competition, form, onSaved]);
  useImperativeHandle(ref, () => ({
    saveNow: save,
  }), [save]);

  return (
    <section className="competition-config-module">
      <div className="competition-config-module__header">
        <Typography.Title className="competition-config-module__title" level={4}>
          基础信息
        </Typography.Title>
      </div>
      <Form<CompetitionFormValues> form={form} layout="vertical" initialValues={defaultCompetitionFormValues}>
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
                            aria-label="Add organizer"
                            title="Add organizer"
                            icon={<PlusOutlined />}
                            onClick={() => {
                              add({ role: '', name: '' });
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
    if (!isPaymentSettingsPageReadyToSave(form.getFieldsValue(true))) {
      return false;
    }
    try {
      const values = await form.validateFields();
      const saved = await updateCompetition(competition.id, normalizePayload({
        ...defaultCompetitionFormValues,
        ...recordToFormValues(competition),
        ...values,
      } as CompetitionFormValues, { preserveTimelineFrom: competition }), API_OPTS.SILENT);
      onCompetitionSaved(saved);
      return true;
    } catch (error) {
      if (isFormValidationError(error)) {
        return false;
      }
      showErrorMessage(error, '费用设置保存失败');
      return false;
    }
  }, [competition, form, onCompetitionSaved]);
  const saveNow = useCallback(async () => {
    const feeSaved = await saveFeeSettings();
    if (!feeSaved) {
      return false;
    }
    const methodsSaved = await paymentMethodsRef.current?.saveNow() ?? true;
    return methodsSaved;
  }, [saveFeeSettings]);

  useImperativeHandle(ref, () => ({
    saveNow,
  }), [saveNow]);

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <section className="competition-config-module">
        <div className="competition-config-module__header">
          <Typography.Title className="competition-config-module__title" level={4}>
            费用设置
          </Typography.Title>
        </div>
        <Form<CompetitionFormValues> form={form} layout="vertical">
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
  const [scheduleModalForm] = Form.useForm<CompetitionScheduleFormItem>();
  const [savingScheduleKey, setSavingScheduleKey] = useState<number>();
  const [scheduleModalOpen, setScheduleModalOpen] = useState(false);
  const schedules = Form.useWatch('schedules', form) || [];
  const registrationRange = Form.useWatch('registrationRange', form);
  const scheduleModalMaterialRange = Form.useWatch('materialRange', scheduleModalForm);

  const openScheduleModal = useCallback(() => {
    scheduleModalForm.resetFields();
    scheduleModalForm.setFieldsValue({ timeMode: 'CONFIRMED' });
    setScheduleModalOpen(true);
  }, [scheduleModalForm]);

  const closeScheduleModal = useCallback(() => {
    setScheduleModalOpen(false);
    scheduleModalForm.resetFields();
  }, [scheduleModalForm]);

  useEffect(() => {
    let cancelled = false;
    form.resetFields();
    form.setFieldsValue({ ...defaultCompetitionFormValues, ...recordToFormValues(competition) });
    void listCompetitionStages(competition.id).then((stages) => {
      if (cancelled) {
        return;
      }
      const currentSchedules = (form.getFieldValue('schedules') || []) as CompetitionScheduleFormItem[];
      const hydratedSchedules = currentSchedules.map((schedule, index) => {
        const stageCode = index === 0 ? 'PRELIMINARY' : index === 1 ? 'FINAL' : `STAGE_${index + 1}`;
        const stage = stages.find((item) => item.stageCode === stageCode);
        if (!stage) {
          return schedule;
        }
        return {
          ...schedule,
          materialRange: schedule.materialRange
            || parseRange(stage.materialSubmitStart, stage.materialSubmitEnd),
          reviewRange: schedule.reviewRange
            || parseRange(stage.reviewStart, stage.reviewEnd),
        };
      });
      form.setFieldValue('schedules', hydratedSchedules);
    }).catch(() => undefined);
    return () => {
      cancelled = true;
    };
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
  const handleSaveSchedules = useCallback(async (scheduleKey: number) => {
    setSavingScheduleKey(scheduleKey);
    try {
      await form.validateFields();
      const saved = await save();
      if (saved) {
        message.success('竞赛安排已保存');
      }
    } catch (error) {
      if (isFormValidationError(error)) {
        message.warning('请先补全报名时间及所有竞赛安排');
      } else {
        showErrorMessage(error, '竞赛安排保存失败');
      }
    } finally {
      setSavingScheduleKey(undefined);
    }
  }, [form, save]);

  useImperativeHandle(ref, () => ({
    saveNow: save,
  }), [save]);

  return (
    <section className="competition-config-module">
      <Form<CompetitionFormValues> form={form} layout="vertical" initialValues={defaultCompetitionFormValues}>
        <section className="competition-basic-section">
          <Typography.Title className="competition-basic-section__title" level={5}>
            报名时间
          </Typography.Title>
          <Typography.Paragraph type="secondary">
            提交材料和评审时间均需在报名时间范围内，系统会同步限制可选择的日期和时间。
          </Typography.Paragraph>
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
                          return;
                        }
                        form.setFieldValue('schedules', [{ timeMode: 'TBD' }]);
                      }}
                    />
                  </Form.Item>
                ) : null}
                {schedules[0]?.timeMode === 'CONFIRMED' ? (
                  <Space orientation="vertical" size={16} className="competition-schedule-settings__content">
                    <div className="competition-schedule-table">
                      <div className="competition-schedule-table__head">
                        <span>阶段名称</span>
                        <span>提交材料时间</span>
                        <span>评审时间</span>
                        <span>操作</span>
                      </div>
                      {fields.map((field) => (
                        <div key={field.key} className="competition-schedule-table__row">
                          <Form.Item name={[field.name, 'title']} rules={[{ required: true, message: '请输入阶段名称' }]}>
                            <Input maxLength={128} placeholder="例如：初赛" />
                          </Form.Item>
                          <Form.Item
                            name={[field.name, 'materialRange']}
                            dependencies={['registrationRange']}
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
                          <Space size={0} className="competition-schedule-table__actions">
                            <Button
                              type="link"
                              aria-label={`保存第${field.name + 1}条竞赛安排`}
                              title="保存全部竞赛安排"
                              htmlType="button"
                              loading={savingScheduleKey === field.key}
                              disabled={savingScheduleKey !== undefined && savingScheduleKey !== field.key}
                              onClick={() => void handleSaveSchedules(field.key)}
                            >
                              保存
                            </Button>
                            <Popconfirm
                              title="确认删除该竞赛安排？"
                              okText="确认删除"
                              cancelText="取消"
                              onConfirm={() => {
                                remove(field.name);
                              }}
                            >
                              <Button
                                danger
                                type="link"
                                aria-label="删除竞赛安排"
                                title={fields.length <= 1 ? '至少保留一个竞赛安排' : '删除竞赛安排'}
                                disabled={savingScheduleKey !== undefined || fields.length <= 1}
                              >
                                删除
                              </Button>
                            </Popconfirm>
                          </Space>
                        </div>
                      ))}
                    </div>
                    <Button
                      block
                      icon={<PlusOutlined />}
                      disabled={savingScheduleKey !== undefined}
                      onClick={openScheduleModal}
                    >
                      新增竞赛安排
                    </Button>
                  </Space>
                ) : null}
                <Modal
                  title="新增竞赛安排"
                  open={scheduleModalOpen}
                  width={720}
                  okText="确定"
                  cancelText="取消"
                  destroyOnHidden
                  onCancel={closeScheduleModal}
                  onOk={async () => {
                    try {
                      const values = await scheduleModalForm.validateFields();
                      add({
                        ...values,
                        timeMode: 'CONFIRMED',
                        title: String(values.title || '').trim(),
                      });
                      closeScheduleModal();
                    } catch (error) {
                      if (!isFormValidationError(error)) {
                        showErrorMessage(error, '新增竞赛安排失败');
                      }
                    }
                  }}
                >
                  <Form form={scheduleModalForm} component={false} layout="vertical">
                    <Form.Item
                      name="title"
                      label="阶段名称"
                      rules={[{ required: true, message: '请输入阶段名称' }]}
                    >
                      <Input maxLength={128} placeholder="例如：初赛" />
                    </Form.Item>
                    <Form.Item
                      name="materialRange"
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
                      name="reviewRange"
                      label="评审时间"
                      dependencies={['materialRange']}
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
                            if (!getCompleteTimeRange(scheduleModalMaterialRange)) {
                              return Promise.reject(new Error('请先选择提交材料时间'));
                            }
                            return isTimeRangeAtOrAfterPreviousEnd(value, scheduleModalMaterialRange)
                              ? Promise.resolve()
                              : Promise.reject(new Error('评审开始时间不得早于材料提交截止时间'));
                          },
                        },
                      ]}
                    >
                      <CompetitionDateTimeRangePicker
                        minDate={getScheduleRangePickerBounds(registrationRange, scheduleModalMaterialRange).minDate}
                        maxDate={getScheduleRangePickerBounds(registrationRange).maxDate}
                        disabledDate={(current) => isOutsideScheduleRangePickerBounds(
                          current,
                          getScheduleRangePickerBounds(registrationRange, scheduleModalMaterialRange),
                        )}
                      />
                    </Form.Item>
                  </Form>
                </Modal>
              </div>
            )}
          </Form.List>
        </section>
      </Form>
    </section>
  );
});

CompetitionTimelineSettingsPanel.displayName = 'CompetitionTimelineSettingsPanel';


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
  const materialStageTabs = useMemo(() => getCompetitionMaterialStageTabs(competition), [competition]);
  const activeStage = materialStageTabs.find((stage) => stage.key === activeTab) || materialStageTabs[0];

  useImperativeHandle(ref, () => ({
    saveNow: async () => {
      if (activeTab === 'timeline') {
        return timelineRef.current?.saveNow() ?? true;
      }
      return materialsRef.current?.saveNow() ?? true;
    },
  }), [activeTab]);

  return activeTab === 'timeline' ? (
    <CompetitionTimelineSettingsPanel ref={timelineRef} competition={competition} onSaved={onCompetitionSaved} />
  ) : activeStage ? (
    <section className="competition-config-module">
      <div className="competition-config-module__header">
        <Typography.Title className="competition-config-module__title" level={4}>
          {activeStage.stageName}提交材料设置
        </Typography.Title>
      </div>
      <ConfigModulePanel
        ref={materialsRef}
        competitionUuid={competitionUuid}
        module={module}
        items={items}
        storageSpaceOptions={storageSpaceOptions}
        fileStageCode={activeStage.stageCode}
        onSaved={onSettingsSaved}
      />
    </section>
  ) : null;
});

CompetitionStageAndMaterialPanel.displayName = 'CompetitionStageAndMaterialPanel';

const CompetitionSettingsPage = () => {
  const params = useParams<{ competitionUuid: string }>();
  const location = useLocation();
  const workspace = useOptionalCompetitionWorkspace();
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
    const nextPath = `${location.pathname}${nextSearch}`;
    if (replace) {
      history.replace(nextPath);
    } else {
      history.push(nextPath);
    }
    window.dispatchEvent(new PopStateEvent('popstate'));
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
    const navigationMatchesCurrentPanel = navigation.section === activeKey
      && (navigation.section !== 'registration' || navigation.registrationTab === registrationDetail)
      && (navigation.section !== 'stages' || navigation.stageTab === stageDetail);
    if (navigationMatchesCurrentPanel) {
      return;
    }

    setActiveKey(navigation.section);
    setRegistrationDetail(navigation.registrationTab);
    setStageDetail(navigation.stageTab);
  }, [activeKey, location.search, registrationDetail, stageDetail]);

  useEffect(() => {
    const tabValue = new URLSearchParams(location.search).get('tab');
    if (activeKey === 'registration' && ['students', 'team-members'].includes(tabValue || '')) {
      updateNavigationUrl('registration', 'TEAM_FIELD', true);
    }
  }, [activeKey, location.search, updateNavigationUrl]);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    Promise.all([
      getCompetitionSettings(competitionUuid),
      loadConfiguredPaymentProviderOptions().catch(() => [] as PaymentProviderOption[]),
    ])
      .then(([result, nextPaymentProviderOptions]) => {
        if (mounted) {
          const localizedSettings = localizeLegacyCompetitionSettings(result);
          setSettings(localizedSettings);
          setStorageSpaceOptions(buildCompetitionStorageSpaceOptions(localizedSettings.competition));
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
        : activeKey === 'awards'
          ? 'awards'
        : undefined;
  const activeModule = activeConfigModuleKey
    ? competitionSettingsModules.find((item) => item.key === activeConfigModuleKey)
    : undefined;

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

  const handleModuleChange = useCallback((nextKey: CompetitionSettingsModuleKey) => {
    if (nextKey === activeKey) {
      return;
    }
    setActiveKey(nextKey);
    if (nextKey === 'registration') {
      setRegistrationDetail('TEAM_FIELD');
      updateNavigationUrl(nextKey, 'TEAM_FIELD');
      return;
    }
    if (nextKey === 'stages') {
      setStageDetail('timeline');
      updateNavigationUrl(nextKey, 'timeline');
      return;
    }
    updateNavigationUrl(nextKey);
  }, [activeKey, updateNavigationUrl]);

  const handleRegistrationDetailChange = useCallback((nextKey: string) => {
    const nextDetail = nextKey as CompetitionSettingsRegistrationTab;
    if (nextDetail === registrationDetail) {
      return;
    }
    setRegistrationDetail(nextDetail);
    updateNavigationUrl('registration', nextDetail);
  }, [registrationDetail, updateNavigationUrl]);

  const handleStageDetailChange = useCallback((nextKey: string) => {
    const nextDetail = nextKey as CompetitionSettingsStageTab;
    if (nextDetail === stageDetail) {
      return;
    }
    setStageDetail(nextDetail);
    updateNavigationUrl('stages', nextDetail);
  }, [stageDetail, updateNavigationUrl]);

  return (
    <CompetitionWorkspacePageFrame
      embeddedInWorkspace={Boolean(workspace)}
      title="赛事设置"
      showWorkspaceHeader={Boolean(workspace)}
      workspaceVariant="flush"
    >
        {loading ? (
          <Card loading />
        ) : settings ? (
          <div className="competition-settings-layout">
            <aside className="competition-settings-sidebar">
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
                      { key: 'TEAM_FIELD', label: '团队信息' },
                      { key: 'PROJECT_FIELD', label: '项目信息' },
                      { key: 'EXPERT_FIELD', label: '专家信息' },
                      { key: 'INTELLECTUAL_PROPERTY', label: '知识产权信息' },
                      { key: 'documents', label: '报名须知与文书' },
                    ]}
                    onChange={(key) => void handleRegistrationDetailChange(key)}
                  />
                  {activeModule ? (
                    <ConfigModulePanel
                      key={`${activeModule.key}-${registrationDetail}`}
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
                      key={stageDetail}
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
              ) : activeKey === 'awards' ? (
                <CompetitionAwardSettingsPanel
                  ref={activePanelRef}
                  competitionUuid={settings.competition.uuid || competitionUuid}
                  items={settings.awards || []}
                  onSaved={setSettings}
                />
              ) : null}
              <div className="competition-settings-content__footer">
                <Button
                  type="primary"
                  loading={saving}
                  disabled={loading || saving || !settings}
                  onClick={() => void handleSave()}
                >
                  保存
                </Button>
              </div>
            </main>
          </div>
        ) : (
          <Alert type="error" showIcon title={formatMessage({ id: 'page.competition.settings.notFound', defaultMessage: '未找到赛事配置' })} />
        )}
    </CompetitionWorkspacePageFrame>
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
