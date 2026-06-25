import { DeleteOutlined, EditOutlined, PlusOutlined, SettingOutlined, UploadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Alert, Button, Card, Checkbox, DatePicker, Form, Image, Input, InputNumber, Modal, Radio, Select, Space, Steps, Switch, Tag, Typography, Upload } from 'antd';
import type { FormInstance } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { useEffect, useMemo, useRef, useState } from 'react';
import { history, useLocation } from '@umijs/max';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import { XMarkdown } from '@ant-design/x-markdown';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useDictOptions } from '@/hooks/useDictOptions';
import { useResponsive } from '@/hooks/useResponsive';
import {
  createCompetition,
  createCompetitionStage,
  createProject,
  createRegistration,
  createRegistrationPaymentOrder,
  deleteCompetition,
  getCompetitionStageForm,
  listCompetitionStages,
  listCompetitions,
  listProjects,
  submitRegistrationMaterials,
  updateCompetition,
  upsertCompetitionStageForm,
} from '@/services/competition/api';
import type {
  CompetitionFeeMode,
  CompetitionLocale,
  CompetitionRecord,
  CompetitionStageFormRecord,
  CompetitionStatus,
  CompetitionUpsertPayload,
  ProjectRecord,
} from '@/services/competition/types';
import { request } from '@/services/common/request';
import { createTeam, listMyTeams } from '@/services/team/api';
import type { TeamRecord } from '@/services/team/types';
import { AgreementMarkdownEditor } from '@/pages/settings/personalization/components/AgreementMarkdownEditor';
import { message } from '@/theme/antdFeedbackBridge';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { sanitizeMarkdownInput } from '@/utils/markdownSecurity';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import './CompetitionPage.css';

type CompetitionTimeMode = 'CONFIRMED' | 'TBD';

type CompetitionOrganizerFormItem = {
  role?: string;
  name?: string;
};

type CompetitionScheduleFormItem = {
  timeMode?: CompetitionTimeMode;
  title?: string;
  timeRange?: [Dayjs, Dayjs] | [string, string];
};

type CompetitionFormValues = Omit<Partial<CompetitionUpsertPayload>, 'locale'> & {
  locale?: CompetitionLocale[];
  registrationRange?: [Dayjs, Dayjs] | [string, string];
  organizers?: CompetitionOrganizerFormItem[];
  schedules?: CompetitionScheduleFormItem[];
};

type CompetitionJsonSchedule = {
  timeMode?: CompetitionTimeMode;
  title?: string;
  start?: string;
  end?: string;
};

type CompetitionCreateDraftStorage = {
  currentStep?: number;
  termsAccepted?: boolean;
  savedAt?: number;
  values?: Partial<CompetitionFormValues>;
};

type MaterialStageFormValues = {
  stageName?: string;
  formName?: string;
  formSchemaJson?: string;
};

const COMPETITION_CATEGORY_DICT = 'aiadc_competition_category';
const COMPETITION_LEVEL_DICT = 'aiadc_competition_level';
const COMPETITION_CREATE_DRAFT_STORAGE_KEY = 'lumira.competition.create.draft.v1';

const localeOptions: Array<{ label: string; value: CompetitionLocale }> = [
  { label: '中文', value: 'zh' },
  { label: 'English', value: 'en' },
];

const statusOptions: Array<{ label: string; value: CompetitionStatus }> = [
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' },
  { label: '已归档', value: 'archived' },
];

const fallbackCategoryOptions = [
  { label: '创新赛', value: 'INNOVATION' },
  { label: '应用赛', value: 'APPLICATION' },
  { label: '专项赛', value: 'SPECIAL' },
  { label: '其他', value: 'OTHER' },
];

const fallbackLevelOptions = [
  { label: '校级', value: 'SCHOOL' },
  { label: '省级', value: 'PROVINCE' },
  { label: '国家级', value: 'NATIONAL' },
  { label: '国际级', value: 'INTERNATIONAL' },
];

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

const defaultPreliminaryFormSchema = JSON.stringify(
  {
    fields: [
      {
        key: 'project_plan',
        label: '项目计划书',
        type: 'file',
        required: true,
        accept: ['.pdf', '.doc', '.docx'],
        maxSizeMb: 20,
      },
      {
        key: 'project_intro',
        label: '项目简介',
        type: 'textarea',
        required: true,
        maxLength: 1000,
      },
    ],
  },
  null,
  2,
);

const trimOptional = (value?: string | null) => {
  const trimmed = value?.trim();
  return trimmed || undefined;
};

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
  if (!value || typeof value === 'string') {
    return undefined;
  }
  return value.format('YYYY.MM.DD HH:mm');
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
      return {
        timeMode,
        title: trimOptional(item.title),
        start: formatRangeValue(start),
        end: formatRangeValue(end),
      };
    })
    .filter((item) => item.timeMode === 'TBD' || item.title || item.start || item.end);
  const confirmedSchedules = normalized.filter((item) => item.timeMode === 'CONFIRMED');
  return confirmedSchedules.length ? confirmedSchedules : [{ timeMode: 'TBD' }];
};

const organizerLabel = (organizer: CompetitionOrganizerFormItem) =>
  [organizer.role, organizer.name].map(trimOptional).filter(Boolean).join('：');

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
  const schedules = parseJsonArray<CompetitionJsonSchedule>(record.scheduleJson).map((item) => ({
    timeMode: normalizeTimeMode(item.timeMode),
    title: item.title,
    timeRange: item.timeMode === 'CONFIRMED' ? parseRange(item.start, item.end) : undefined,
  }));

  return {
    code: record.code,
    locale: splitCompetitionLocales(record.locale),
    title: record.title,
    shortName: record.shortName || undefined,
    category: normalizeOptionValue(record.category) || undefined,
    level: normalizeOptionValue(record.level) || undefined,
    competitionLevel: normalizeOptionValue(record.competitionLevel || record.level) || undefined,
    organizer: record.organizer || undefined,
    organizers: organizers.length ? organizers : [{ role: '主办方', name: record.organizer || '' }],
    registrationRange: parseRange(record.registrationStart, record.registrationEnd),
    schedules: schedules.length ? schedules : [{ timeMode: 'CONFIRMED', title: '竞赛时间', timeRange: parseRange(record.competitionStart, record.competitionEnd) }],
    participationScope: record.participationScope || record.location || undefined,
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
  organizers: [{ role: '主办方', name: '' }],
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

const readCompetitionCreateDraft = () => {
  if (typeof window === 'undefined') {
    return undefined;
  }
  try {
    const stored = window.localStorage.getItem(COMPETITION_CREATE_DRAFT_STORAGE_KEY);
    return stored ? (JSON.parse(stored) as CompetitionCreateDraftStorage) : undefined;
  } catch {
    return undefined;
  }
};

const writeCompetitionCreateDraft = (draft: CompetitionCreateDraftStorage) => {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.setItem(COMPETITION_CREATE_DRAFT_STORAGE_KEY, JSON.stringify(draft));
};

const clearCompetitionCreateDraft = () => {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.removeItem(COMPETITION_CREATE_DRAFT_STORAGE_KEY);
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
  if (!firstSchedule?.timeMode) {
    missingFields.push('竞赛安排');
  }
  if (firstSchedule?.timeMode === 'CONFIRMED') {
    const hasValidSchedule = values.schedules?.some((schedule) => trimOptional(schedule.title) && Array.isArray(schedule.timeRange) && schedule.timeRange.length === 2);
    if (!hasValidSchedule) {
      missingFields.push('竞赛安排');
    }
  }
  if (!values.locale?.length) {
    missingFields.push('语言');
  }
  if (!values.status) {
    missingFields.push('状态');
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
  if (file.size > 20 * 1024 * 1024) {
    message.error('图片过大，请上传不超过 20MB 的文件');
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
      showErrorMessage(error, '二维码上传失败');
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
          <Form.Item label="组织者列表" required>
            <Space direction="vertical" size={12} className="competition-dynamic-list">
              {fields.map((field, index) => (
                <div key={field.key} className="competition-dynamic-list__row">
                  <Form.Item name={[field.name, 'role']} rules={[{ required: true, message: '请输入组织者类型' }]} className="competition-dynamic-list__role">
                    <Input maxLength={64} placeholder="例如：主办方" />
                  </Form.Item>
                  <Form.Item name={[field.name, 'name']} rules={[{ required: true, message: '请输入组织者名称' }]} className="competition-dynamic-list__main">
                    <Input maxLength={128} placeholder="例如：大学生赛事组委会" />
                  </Form.Item>
                  <div className="competition-dynamic-list__actions">
                    {index === fields.length - 1 ? (
                      <Button aria-label="添加组织者" title="添加组织者" icon={<PlusOutlined />} onClick={() => add({ role: '', name: '' })} />
                    ) : null}
                    <Button aria-label="删除组织者" title="删除组织者" icon={<DeleteOutlined />} disabled={fields.length <= 1} onClick={() => remove(field.name)} />
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
                        <Form.Item name={[field.name, 'title']} rules={[{ required: true, message: '请输入安排名称' }]} className="competition-schedule-row__title">
                          <Input maxLength={128} placeholder="例如：初赛" />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'timeRange']}
                          rules={[
                            { required: true, message: '请选择比赛时间' },
                            {
                              validator: (_, value: CompetitionScheduleFormItem['timeRange']) =>
                                Array.isArray(value) && value.length === 2 ? Promise.resolve() : Promise.reject(new Error('请选择开始和结束时间')),
                            },
                          ]}
                          className="competition-schedule-row__time"
                        >
                          <DatePicker.RangePicker showTime format="YYYY.MM.DD HH:mm" minuteStep={15} style={{ width: '100%' }} />
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
          <div className="competition-qr-upload__preview">
            {qrPreviewUrl ? <Image width={144} height={144} src={qrPreviewUrl} preview={false} /> : <Typography.Text type="secondary">未上传二维码</Typography.Text>}
          </div>
          <Space wrap>
            <Upload
              accept="image/*"
              showUploadList={false}
              disabled={uploadingQrCode}
              beforeUpload={async (file) => {
                await handleQrCodeUpload(file);
                return Upload.LIST_IGNORE;
              }}
            >
              <Button icon={<UploadOutlined />} loading={uploadingQrCode}>
                上传二维码
              </Button>
            </Upload>
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
      <Form.Item name="registrationRange" label="报名时间">
        <DatePicker.RangePicker showTime format="YYYY.MM.DD HH:mm" minuteStep={15} style={{ width: '100%' }} />
      </Form.Item>
      <Space size={0} className="competition-inline-fields" align="start">
        <Form.Item name="locale" label="语言" rules={[{ required: true }]} className="competition-inline-fields__item">
          <Select mode="multiple" maxTagCount="responsive" options={localeOptions} />
        </Form.Item>
        <Form.Item name="status" label="状态" rules={[{ required: true }]} className="competition-inline-fields__item">
          <Select options={statusOptions} />
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

const CompetitionForm = ({
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
  const { options: categoryOptions } = useDictOptions(COMPETITION_CATEGORY_DICT, fallbackCategoryOptions);
  const { options: levelOptions } = useDictOptions(COMPETITION_LEVEL_DICT, fallbackLevelOptions);
  const [form] = Form.useForm<CompetitionFormValues>();
  const [currentStep, setCurrentStep] = useState(0);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [draftSavedAt, setDraftSavedAt] = useState<number>();
  const [draftHydrated, setDraftHydrated] = useState(false);

  const collectCompetitionCreateValues = () => ({
    ...defaultCompetitionFormValues,
    ...(form.getFieldsValue(true) as Partial<CompetitionFormValues>),
  });

  const persistCompetitionCreateDraft = (
    nextValues: Partial<CompetitionFormValues> = collectCompetitionCreateValues(),
    nextStep = currentStep,
    nextTermsAccepted = termsAccepted,
  ) => {
    const normalizedValues = {
      ...defaultCompetitionFormValues,
      ...nextValues,
    };
    const savedAt = Date.now();
    writeCompetitionCreateDraft({
      currentStep: nextStep,
      termsAccepted: nextTermsAccepted,
      savedAt,
      values: serializeCompetitionCreateDraftValues(normalizedValues),
    });
    setDraftSavedAt(savedAt);
  };

  useEffect(() => {
    const draft = readCompetitionCreateDraft();
    const nextValues = {
      ...defaultCompetitionFormValues,
      ...restoreCompetitionCreateDraftValues(draft?.values),
    };
    form.resetFields();
    form.setFieldsValue(nextValues);
    setTermsAccepted(Boolean(draft?.termsAccepted));
    setDraftSavedAt(draft?.savedAt);
    setDraftHydrated(true);
  }, [form]);

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
  }, [draftHydrated, location.search, termsAccepted]);

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
      await createCompetition(normalizePayload(values as CompetitionFormValues));
      clearCompetitionCreateDraft();
      setDraftSavedAt(undefined);
      message.success('赛事已新增');
      history.push('/competitions/management');
    } catch (error) {
      showErrorMessage(error, '赛事保存失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ManagementPage title="新增赛事" extra={<Button onClick={() => history.push('/competitions/management')}>返回赛事管理</Button>}>
      <ManagementPageBody className="competition-create-page">
        <Card className="competition-create-shell">
          <Steps current={currentStep} items={competitionCreateSteps} responsive />
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
    return [];
  }
  try {
    const parsed = JSON.parse(form.formSchemaJson);
    return Array.isArray(parsed.fields) ? parsed.fields : [];
  } catch {
    return [];
  }
};

const CompetitionRegistrationPage = () => {
  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [competitions, setCompetitions] = useState<CompetitionRecord[]>([]);
  const [teams, setTeams] = useState<TeamRecord[]>([]);
  const [projects, setProjects] = useState<ProjectRecord[]>([]);
  const [stageForm, setStageForm] = useState<CompetitionStageFormRecord>();
  const [registrationId, setRegistrationId] = useState<number>();
  const [paymentStatus, setPaymentStatus] = useState<string>();
  const [form] = Form.useForm();
  const selectedCompetitionId = Form.useWatch('competitionId', form);
  const selectedTeamId = Form.useWatch('teamId', form);
  const selectedProjectId = Form.useWatch('projectId', form);

  useEffect(() => {
    void listCompetitions({ status: 'published', pageSize: 100 }).then((response) => setCompetitions(response.records || []));
  }, []);

  const loadTeams = async () => {
    const records = await listMyTeams();
    setTeams(records || []);
    if (records?.length) {
      Modal.confirm({
        title: '检测到你已有团队',
        content: '是否复用已有团队？',
        onOk: () => form.setFieldValue('teamId', records[0].id),
      });
    }
  };

  const loadProjects = async () => {
    const response = await listProjects({ pageSize: 100 });
    const records = response.records || [];
    setProjects(records);
    if (records.length) {
      Modal.confirm({
        title: '检测到你已有项目',
        content: '是否复用已有项目？',
        onOk: () => form.setFieldValue('projectId', records[0].id),
      });
    }
  };

  const goNext = async () => {
    setLoading(true);
    try {
      if (step === 0) {
        await form.validateFields(['competitionId']);
        await loadTeams();
        setStep(1);
      } else if (step === 1) {
        if (!selectedTeamId) {
          const teamName = form.getFieldValue('newTeamName');
          if (!teamName) {
            await form.validateFields(['teamId']);
          }
          const team = await createTeam({ teamName, teamType: 'GENERAL', visibility: 'PRIVATE', joinMode: 'INVITE_ONLY' });
          form.setFieldValue('teamId', team.id);
        }
        await loadProjects();
        setStep(2);
      } else if (step === 2) {
        if (!selectedProjectId) {
          const title = form.getFieldValue('newProjectTitle');
          if (!title) {
            await form.validateFields(['projectId']);
          }
          const project = await createProject({
            code: `proj-${Date.now()}`,
            title,
            category: 'INNOVATION',
            ownerName: '',
            description: form.getFieldValue('newProjectDescription'),
          });
          form.setFieldValue('projectId', project.id);
        }
        const registration = await createRegistration({
          competitionId: Number(selectedCompetitionId),
          teamId: Number(form.getFieldValue('teamId')),
          projectId: Number(form.getFieldValue('projectId')),
        });
        setRegistrationId(registration.id);
        const stages = await listCompetitionStages(Number(selectedCompetitionId));
        const preliminary = stages.find((item) => item.stageCode === 'PRELIMINARY') || stages[0];
        if (preliminary) {
          try {
            setStageForm(await getCompetitionStageForm(preliminary.id));
          } catch {
            setStageForm(undefined);
          }
        }
        setStep(3);
      } else if (step === 3) {
        const fields = parseFormFields(stageForm);
        const materialValues = fields.map((field: any) => ({
          fieldKey: field.key,
          fieldType: field.type,
          textValue: field.type === 'file' ? undefined : form.getFieldValue(['materials', field.key]),
          fileId: field.type === 'file' ? form.getFieldValue(['materials', field.key]) : undefined,
        }));
        if (stageForm && registrationId) {
          await submitRegistrationMaterials(registrationId, { stageId: stageForm.stageId, values: materialValues });
        }
        setStep(4);
      }
    } catch (error) {
      showErrorMessage(error, '报名流程处理失败');
    } finally {
      setLoading(false);
    }
  };

  const pay = async () => {
    if (!registrationId) {
      return;
    }
    setLoading(true);
    try {
      const order = await createRegistrationPaymentOrder(registrationId, { providerCode: 'manual' });
      setPaymentStatus(order.status);
      message.success(order.orderNo ? '支付订单已生成' : '报名已确认');
    } catch (error) {
      showErrorMessage(error, '支付订单创建失败');
    } finally {
      setLoading(false);
    }
  };

  const fields = parseFormFields(stageForm);

  return (
    <ManagementPage title="赛事报名" extra={<Button onClick={() => history.push('/competitions/management')}>返回赛事</Button>}>
      <ManagementPageBody>
        <Card>
          <Steps current={step} items={['选择赛事', '选择/创建团队', '选择/创建项目', '初赛材料', '确认支付'].map((title) => ({ title }))} />
          <Form form={form} layout="vertical" style={{ maxWidth: 760, marginTop: 24 }}>
            {step === 0 ? (
              <Form.Item name="competitionId" label="赛事" rules={[{ required: true, message: '请选择赛事' }]}>
                <Select options={competitions.map((item) => ({ label: item.title, value: item.id }))} />
              </Form.Item>
            ) : null}
            {step === 1 ? (
              <>
                <Form.Item name="teamId" label="复用团队">
                  <Select allowClear options={teams.map((item) => ({ label: item.teamName, value: item.id }))} />
                </Form.Item>
                <Form.Item name="newTeamName" label="新建团队名称">
                  <Input disabled={Boolean(selectedTeamId)} />
                </Form.Item>
              </>
            ) : null}
            {step === 2 ? (
              <>
                <Form.Item name="projectId" label="复用项目">
                  <Select allowClear options={projects.map((item) => ({ label: item.title, value: item.id }))} />
                </Form.Item>
                <Form.Item name="newProjectTitle" label="新建项目名称">
                  <Input disabled={Boolean(selectedProjectId)} />
                </Form.Item>
                <Form.Item name="newProjectDescription" label="项目简介">
                  <Input.TextArea disabled={Boolean(selectedProjectId)} rows={3} />
                </Form.Item>
              </>
            ) : null}
            {step === 3 ? (
              fields.length ? (
                fields.map((field: any) => (
                  <Form.Item
                    key={field.key}
                    name={['materials', field.key]}
                    label={field.label || field.key}
                    rules={[{ required: Boolean(field.required), message: `请填写${field.label || field.key}` }]}
                  >
                    {field.type === 'textarea' ? <Input.TextArea rows={4} maxLength={field.maxLength} /> : field.type === 'file' ? <InputNumber min={1} style={{ width: '100%' }} placeholder="请输入已上传文件ID" /> : <Input maxLength={field.maxLength} />}
                  </Form.Item>
                ))
              ) : (
                <Alert type="info" showIcon message="当前赛事未配置初赛材料表单，可继续进入支付确认。" />
              )
            ) : null}
            {step === 4 ? (
              <Alert
                type={paymentStatus === 'CONFIRMED' ? 'success' : 'info'}
                showIcon
                message={paymentStatus ? `当前支付状态：${paymentStatus}` : '材料已提交，请确认费用并生成支付订单。'}
              />
            ) : null}
          </Form>
          <Space style={{ marginTop: 24 }}>
            {step > 0 ? <Button onClick={() => setStep((current) => current - 1)}>上一步</Button> : null}
            {step < 4 ? (
              <Button type="primary" loading={loading} onClick={() => void goNext()}>
                下一步
              </Button>
            ) : (
              <Button type="primary" loading={loading} onClick={() => void pay()}>
                生成支付订单
              </Button>
            )}
          </Space>
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

const CompetitionPage = () => {
  const location = useLocation();
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const { options: categoryOptions } = useDictOptions(COMPETITION_CATEGORY_DICT, fallbackCategoryOptions);
  const { options: levelOptions } = useDictOptions(COMPETITION_LEVEL_DICT, fallbackLevelOptions);
  const categoryLabelMap = useMemo(() => buildOptionLabelMap(categoryOptions), [categoryOptions]);
  const levelLabelMap = useMemo(() => buildOptionLabelMap(levelOptions), [levelOptions]);
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [form] = Form.useForm<CompetitionFormValues>();
  const [materialForm] = Form.useForm<MaterialStageFormValues>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<CompetitionRecord>();
  const [materialRecord, setMaterialRecord] = useState<CompetitionRecord>();
  const [materialStageId, setMaterialStageId] = useState<number>();
  const [materialModalOpen, setMaterialModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [materialSaving, setMaterialSaving] = useState(false);

  useEffect(() => {
    if (location.pathname === '/competitions') {
      history.replace('/competitions/management');
    }
  }, [location.pathname]);

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingRecord(undefined);
  };

  const openCreateDrawer = () => {
    history.push({
      pathname: '/competitions/create',
      search: createCompetitionStepSearch(0),
    });
  };

  const openEditDrawer = (record: CompetitionRecord) => {
    setEditingRecord(record);
    form.resetFields();
    form.setFieldsValue({ ...defaultCompetitionFormValues, ...recordToFormValues(record) });
    setDrawerOpen(true);
  };

  const closeMaterialModal = () => {
    setMaterialModalOpen(false);
    setMaterialRecord(undefined);
    setMaterialStageId(undefined);
    materialForm.resetFields();
  };

  const openMaterialModal = async (record: CompetitionRecord) => {
    setMaterialRecord(record);
    setMaterialStageId(undefined);
    materialForm.setFieldsValue({
      stageName: '初赛',
      formName: '初赛材料',
      formSchemaJson: defaultPreliminaryFormSchema,
    });
    setMaterialModalOpen(true);
    try {
      const stages = await listCompetitionStages(record.id);
      const preliminary = stages.find((item) => item.stageCode === 'PRELIMINARY');
      if (!preliminary) {
        return;
      }
      setMaterialStageId(preliminary.id);
      materialForm.setFieldValue('stageName', preliminary.stageName || '初赛');
      try {
        const formRecord = await getCompetitionStageForm(preliminary.id);
        materialForm.setFieldsValue({
          formName: formRecord.formName || '初赛材料',
          formSchemaJson: formRecord.formSchemaJson || defaultPreliminaryFormSchema,
        });
      } catch {
        materialForm.setFieldValue('formSchemaJson', defaultPreliminaryFormSchema);
      }
    } catch (error) {
      showErrorMessage(error, '材料表单加载失败');
    }
  };

  const saveCompetition = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editingRecord) {
        await updateCompetition(editingRecord.id, normalizePayload(values));
        message.success('赛事已更新');
      } else {
        await createCompetition(normalizePayload(values));
        message.success('赛事已新增');
      }
      closeDrawer();
      actionRef.current?.reload();
    } catch (error) {
      showErrorMessage(error, '赛事保存失败');
    } finally {
      setSaving(false);
    }
  };

  const saveMaterialForm = async () => {
    if (!materialRecord) {
      return;
    }
    const values = await materialForm.validateFields();
    let parsedSchema: unknown;
    try {
      parsedSchema = JSON.parse(values.formSchemaJson || '');
    } catch {
      message.error('表单 Schema 必须是合法 JSON');
      return;
    }
    if (!Array.isArray((parsedSchema as { fields?: unknown }).fields)) {
      message.error('表单 Schema 必须包含 fields 数组');
      return;
    }
    setMaterialSaving(true);
    try {
      let stageId = materialStageId;
      if (!stageId) {
        const stage = await createCompetitionStage(materialRecord.id, {
          stageCode: 'PRELIMINARY',
          stageName: values.stageName || '初赛',
          status: 'ENABLED',
          sort: 10,
        });
        stageId = stage.id;
        setMaterialStageId(stage.id);
      }
      await upsertCompetitionStageForm(stageId, {
        formName: values.formName || '初赛材料',
        formSchemaJson: values.formSchemaJson || defaultPreliminaryFormSchema,
        version: 1,
        status: 'ENABLED',
      });
      message.success('初赛材料表单已保存');
      closeMaterialModal();
    } catch (error) {
      showErrorMessage(error, '材料表单保存失败');
    } finally {
      setMaterialSaving(false);
    }
  };

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
        render: (_, record) => (
          <Space className="competition-name-cell" direction="vertical" size={0}>
            <Typography.Text strong>{record.title}</Typography.Text>
            <span className="competition-name-cell__meta">{record.shortName || record.code}</span>
          </Space>
        ),
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
        render: (value) => value || '-',
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
                <Tag key={item}>{item === 'zh' ? '涓枃' : 'English'}</Tag>
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
        title: '状态',
        dataIndex: 'status',
        valueType: 'select',
        valueEnum: {
          draft: { text: '草稿' },
          published: { text: '已发布' },
          archived: { text: '已归档' },
        },
        width: 110,
        render: (_, record) => <Tag color={statusColor[record.status]}>{statusText[record.status]}</Tag>,
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
        width: 208,
        align: 'right',
        className: 'saas-table-action-column',
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={actionPermission.buildTableActions([
              {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined />,
                permission: 'aiadc:competition:update',
                onClick: () => openEditDrawer(record),
              },
              {
                key: 'materials',
                label: '材料',
                icon: <SettingOutlined />,
                permission: 'aiadc:stage:manage',
                onClick: () => void openMaterialModal(record),
              },
              {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined />,
                permission: 'aiadc:competition:delete',
                danger: true,
                onClick: () => {
                  Modal.confirm({
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
    [actionPermission, categoryLabelMap, categoryOptions, levelLabelMap, responsive.isDesktop, responsive.isMobile],
  );

  if (location.pathname === '/competitions/create') {
    return <CreateCompetitionPage />;
  }

  if (location.pathname === '/competitions/register') {
    return <CompetitionRegistrationPage />;
  }

  return (
    <ManagementPage title="赛事管理">
      <ManagementPageBody>
        <ManagementTable<CompetitionRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          scroll={{ x: 1420 }}
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

      <ManagementDrawer
        title={editingRecord ? '编辑赛事' : '新增赛事'}
        open={drawerOpen}
        onClose={closeDrawer}
        destroyOnHidden
        footerActions={[
          { key: 'cancel', label: '取消', onClick: closeDrawer },
          {
            key: 'save',
            label: '保存',
            type: 'primary',
            loading: saving,
            onClick: () => void saveCompetition(),
          },
        ]}
      >
        <CompetitionForm form={form} categoryOptions={categoryOptions as Array<{ label: string; value: string }>} levelOptions={levelOptions as Array<{ label: string; value: string }>} />
      </ManagementDrawer>

      <Modal
        title={materialRecord ? `配置初赛材料：${materialRecord.title}` : '配置初赛材料'}
        open={materialModalOpen}
        onCancel={closeMaterialModal}
        onOk={() => void saveMaterialForm()}
        confirmLoading={materialSaving}
        destroyOnHidden
        width={760}
      >
        <Form<MaterialStageFormValues> form={materialForm} layout="vertical">
          <Form.Item name="stageName" label="阶段名称" rules={[{ required: true, message: '请输入阶段名称' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="formName" label="表单名称" rules={[{ required: true, message: '请输入表单名称' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="formSchemaJson"
            label="材料表单 Schema"
            rules={[
              { required: true, message: '请输入表单 Schema' },
              {
                validator: (_, value?: string) => {
                  try {
                    const parsed = JSON.parse(value || '');
                    return Array.isArray(parsed.fields) ? Promise.resolve() : Promise.reject(new Error('Schema 必须包含 fields 数组'));
                  } catch {
                    return Promise.reject(new Error('Schema 必须是合法 JSON'));
                  }
                },
              },
            ]}
          >
            <Input.TextArea rows={14} spellCheck={false} />
          </Form.Item>
        </Form>
      </Modal>
    </ManagementPage>
  );
};

export default CompetitionPage;
