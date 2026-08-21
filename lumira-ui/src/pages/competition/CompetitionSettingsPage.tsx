import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, InboxOutlined, PlusOutlined, SettingOutlined } from '@ant-design/icons';
import { Alert, Button, Card, ConfigProvider, Divider, Form, Input, InputNumber, Menu, Modal, Popconfirm, Radio, Select, Space, Switch, Tabs, Tag, Typography } from 'antd';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState, type ReactNode } from 'react';
import { history, useLocation, useParams } from '@umijs/max';
import { formatMessage } from '@/i18n/formatMessage';
import { useOptionalCompetitionWorkspace } from '@/features/competition-workspace/CompetitionWorkspaceContext';
import { CompetitionWorkspacePageFrame } from '@/features/competition-workspace/CompetitionWorkspacePageFrame';
import { isCompetitionWorkspaceReadOnly } from '@/features/competition-workspace/competitionWorkspaceReadOnly';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { DataTable } from '@/features/table/DataTable';
import { useDictOptions } from '@/hooks/useDictOptions';
import { useResponsive } from '@/hooks/useResponsive';
import { databaseMessage } from '@/i18n/databaseMessage';
import {
  deleteCompetition,
  getCompetitionSettings,
  listCompetitionStages,
  saveCompetitionSettingsModule,
  updateCompetition,
} from '@/services/competition/api';
import type {
  CompetitionConfigItem,
  CompetitionConfigItemType,
  CompetitionRecord,
  CompetitionSettingsRecord,
} from '@/services/competition/types';
import { request } from '@/services/common/request';
import type { PaymentProviderSettings } from '@/types/api';
import {
  isBasicSettingsPageReadyToSave,
  isConfigModuleDraftSaveCurrent,
  isConfigModuleItemKeyDuplicate,
  isConfigModuleReadyToSave,
  isPaymentSettingsPageReadyToSave,
  isTimelineSettingsPageReadyToSave,
  mergeStageMaterialSaveItems,
  shouldHydrateConfigModuleDraft,
  shouldValidateTeamMemberLimitsForPage,
} from './competitionSettingsSave';
import { buildCompetitionStorageKey } from './utils/competitionMaterialFileStorage';
import {
  isChronologicalTimeRange,
  isTimeRangeAtOrAfterPreviousEnd,
  isTimeRangeWithinBounds,
} from './utils/competitionTimeline';
import {
  IMAGE_CROP_ASPECT_RATIO_OPTIONS,
  normalizeImageCropAspectRatio,
} from './utils/imageCropAspectRatio';
import {
  MAX_REGISTRATION_PARTICIPANTS_PER_TYPE,
} from './utils/competitionParticipantConfig';
import {
  createCompetitionSettingsSearch,
  getCompetitionSettingsStageTabFallback,
  parseCompetitionSettingsNavigation,
  type CompetitionSettingsRegistrationTab,
  type CompetitionSettingsSectionKey,
  type CompetitionSettingsStageTab,
} from './utils/competitionSettingsNavigation';
import {
  removeDeprecatedRegistrationContactFields,
  resolveRegistrationFieldScope,
} from './utils/registrationFieldScope';
import { resolveRegistrationFieldValidationRule } from './utils/registrationFieldValidation';
import {
  normalizeIndependentMemberRoleMetadata,
  reorderScopedConfigItems,
} from './utils/competitionFieldConfig';
import {
  INTELLECTUAL_PROPERTY_GROUP_LABEL,
  buildTeamSettingsConfigItem,
  getTeamMemberLimits,
  normalizeCollectedFieldConfigKey,
  normalizeFileFormat,
  normalizeReadingSeconds,
  parseConfigItemMetadata,
  serializeConfigItemMetadata,
  type CompetitionConfigFieldScope,
  type ConfigItemMetadata,
} from './utils/competitionConfigShared';
import type { CompetitionScheduleFormItem, CompetitionTimeMode } from './competitionSchedulePayload';
import { AgreementMarkdownEditor } from '@/pages/settings/personalization/components/AgreementMarkdownEditor';
import CompetitionAwardSettingsPanel from './components/CompetitionAwardSettingsPanel';
import type { CompetitionSettingsPanelHandle } from './components/CompetitionSettingsPanelHandle';
import { message } from '@/theme/antdFeedbackBridge';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import {
  COMPETITION_CATEGORY_DICT,
  COMPETITION_LEVEL_DICT,
  CompetitionDateTimeRangePicker,
  defaultCompetitionFormValues,
  feeModeOptions,
  getCompetitionMaterialStageTabs,
  getCompleteTimeRange,
  getScheduleRangePickerBounds,
  isOutsideScheduleRangePickerBounds,
  normalizePayload,
  parseRange,
  recordToFormValues,
  timeModeOptions,
  useCompetitionDictFallbackOptions,
  type CompetitionFormValues,
} from './CompetitionPage';
import './CompetitionPage.css';

type CompetitionSettingsConfigModuleKey = 'documents' | 'fields' | 'payments' | 'files' | 'awards';
type CompetitionSettingsModuleKey = CompetitionSettingsSectionKey;
type CompetitionSettingsModuleConfig = {
  key: CompetitionSettingsConfigModuleKey;
  labelId: string;
  defaultLabel: string;
  descriptionId: string;
  defaultDescription: string;
  itemTypes: CompetitionConfigItemType[];
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
  readOnly: boolean;
  canArchive: boolean;
  canDelete: boolean;
  onSaved: (competition: CompetitionRecord) => void;
};

const CompetitionBasicSettingsPanel = forwardRef<
  CompetitionSettingsPanelHandle,
  CompetitionBasicSettingsPanelProps
>(({
  competition,
  categoryOptions,
  levelOptions,
  readOnly,
  canArchive,
  canDelete,
  onSaved,
}, ref) => {
  const [form] = Form.useForm<CompetitionFormValues>();
  const [archiving, setArchiving] = useState(false);
  const [deleting, setDeleting] = useState(false);

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

  const archiveCompetition = useCallback(async () => {
    setArchiving(true);
    try {
      const archived = await updateCompetition(competition.id, normalizePayload({
        ...defaultCompetitionFormValues,
        ...recordToFormValues(competition),
        status: 'archived',
      } as CompetitionFormValues, { preserveTimelineFrom: competition }), API_OPTS.SILENT);
      onSaved(archived);
      message.success('赛事已归档');
    } catch (error) {
      showErrorMessage(error, '赛事归档失败');
    } finally {
      setArchiving(false);
    }
  }, [competition, onSaved]);

  const deleteCurrentCompetition = useCallback(async () => {
    setDeleting(true);
    try {
      await deleteCompetition(competition.id);
      message.success('赛事已删除');
      history.replace('/competitions/management');
    } catch (error) {
      showErrorMessage(error, '赛事删除失败');
    } finally {
      setDeleting(false);
    }
  }, [competition.id]);
  useImperativeHandle(ref, () => ({
    saveNow: save,
  }), [save]);

  return (
    <section className="competition-config-module">
      <CompetitionSettingsReadOnlyBoundary readOnly={readOnly}>
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
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={categoryOptions}
                  placeholder="请选择竞赛类别"
                />
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
      </CompetitionSettingsReadOnlyBoundary>

      {canArchive || canDelete ? (
        <section className="competition-basic-section competition-basic-section--danger">
          <Typography.Title className="competition-basic-section__title" level={5}>
            危险操作
          </Typography.Title>
          <div className="competition-danger-actions">
            {canArchive ? (
              <div className="competition-danger-action">
                <div className="competition-danger-action__copy">
                  <Typography.Text strong>归档赛事</Typography.Text>
                  <Typography.Text type="secondary">
                    {competition.status === 'archived'
                      ? '赛事已归档，工作空间当前为只读状态。'
                      : '归档后赛事将转为只读，不再接受报名、评审或设置修改。'}
                  </Typography.Text>
                </div>
                <Popconfirm
                  disabled={competition.status === 'archived'}
                  title="确认归档该赛事？"
                  description="归档后赛事工作空间将变为只读状态。"
                  okText="确认归档"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => archiveCompetition()}
                >
                  <Button
                    danger
                    type="primary"
                    icon={<InboxOutlined />}
                    loading={archiving}
                    disabled={competition.status === 'archived'}
                  >
                    归档
                  </Button>
                </Popconfirm>
              </div>
            ) : null}
            {canDelete ? (
              <div className="competition-danger-action">
                <div className="competition-danger-action__copy">
                  <Typography.Text strong>删除赛事</Typography.Text>
                  <Typography.Text type="secondary">
                    删除后赛事将从列表移除，且无法通过页面恢复；已有报名记录的赛事无法删除。
                  </Typography.Text>
                </div>
                <Popconfirm
                  title="确认删除该赛事？"
                  description="此操作不可通过页面恢复，请确认后再删除。"
                  okText="确认删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => deleteCurrentCompetition()}
                >
                  <Button danger type="primary" icon={<DeleteOutlined />} loading={deleting}>
                    删除
                  </Button>
                </Popconfirm>
              </div>
            ) : null}
          </div>
        </section>
      ) : null}
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

const CompetitionSettingsReadOnlyBoundary = ({
  readOnly,
  children,
}: {
  readOnly: boolean;
  children: ReactNode;
}) => (
  <ConfigProvider componentDisabled={readOnly}>
    <fieldset
      disabled={readOnly}
      style={{ border: 0, margin: 0, minWidth: 0, padding: 0, width: '100%' }}
    >
      {children}
    </fieldset>
  </ConfigProvider>
);

const CompetitionSettingsPage = () => {
  const params = useParams<{ competitionUuid: string }>();
  const location = useLocation();
  const actionPermission = useActionPermission();
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
  const settingsArchived = isCompetitionWorkspaceReadOnly(
    settings?.competition.status,
    workspace?.workspace?.readOnly,
  );
  const canManageSettings = workspace ? workspace.can('settings.manage') : true;
  const canArchiveCompetition = actionPermission.can('aiadc:competition:update');
  const canDeleteCompetition = actionPermission.can('aiadc:competition:delete');
  const settingsReadOnly = settingsArchived || !canManageSettings;
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
    if (settingsReadOnly || !activePanelRef.current) {
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
  }, [settingsReadOnly]);

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

  const handleCompetitionSaved = useCallback((competition: CompetitionRecord) => {
    setSettings((current) => current ? { ...current, competition } : current);
    workspace?.refresh();
  }, [workspace]);

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
              {settingsArchived ? (
                <Alert
                  type="info"
                  showIcon
                  title="赛事已归档，设置仅供查看"
                  description="归档赛事不再接受设置变更。"
                  style={{ marginBottom: 16 }}
                />
              ) : !canManageSettings ? (
                <Alert
                  type="warning"
                  showIcon
                  title="当前账号仅可查看赛事设置"
                  description="需要赛事设置管理权限才能修改或归档赛事。"
                  style={{ marginBottom: 16 }}
                />
              ) : null}
              {activeKey === 'basic' ? (
                <CompetitionBasicSettingsPanel
                  ref={activePanelRef}
                  competition={settings.competition}
                  categoryOptions={categoryOptions as Array<{ label: string; value: string }>}
                  levelOptions={levelOptions as Array<{ label: string; value: string }>}
                  readOnly={settingsReadOnly}
                  canArchive={canArchiveCompetition}
                  canDelete={canDeleteCompetition}
                  onSaved={handleCompetitionSaved}
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
                    <CompetitionSettingsReadOnlyBoundary readOnly={settingsReadOnly}>
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
                    </CompetitionSettingsReadOnlyBoundary>
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
                    <CompetitionSettingsReadOnlyBoundary readOnly={settingsReadOnly}>
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
                    </CompetitionSettingsReadOnlyBoundary>
                  </>
                ) : null
              ) : activeKey === 'payments' && activeModule ? (
                <CompetitionSettingsReadOnlyBoundary readOnly={settingsReadOnly}>
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
                </CompetitionSettingsReadOnlyBoundary>
              ) : activeKey === 'awards' ? (
                <CompetitionSettingsReadOnlyBoundary readOnly={settingsReadOnly}>
                  <CompetitionAwardSettingsPanel
                    ref={activePanelRef}
                    competitionUuid={settings.competition.uuid || competitionUuid}
                    items={settings.awards || []}
                    onSaved={setSettings}
                  />
                </CompetitionSettingsReadOnlyBoundary>
              ) : null}
              <div className="competition-settings-content__footer">
                <Button
                  type="primary"
                  loading={saving}
                  disabled={settingsReadOnly || loading || saving || !settings}
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


export default CompetitionSettingsPage;
