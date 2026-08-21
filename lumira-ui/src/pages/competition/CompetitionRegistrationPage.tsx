import { DeleteOutlined, DownloadOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, TeamOutlined, UploadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Alert, Avatar, Button, Card, Checkbox, DatePicker, Descriptions, Form, Image, Input, InputNumber, Modal, Result, Select, Space, Spin, Steps, Tag, Tooltip, Typography, Upload } from 'antd';
import type { DatePickerProps, TableProps, UploadFile } from 'antd';
import ImgCrop from 'antd-img-crop';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { history, useLocation, useModel } from '@umijs/max';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import { XMarkdown } from '@ant-design/x-markdown';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { DataTable } from '@/features/table/DataTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { useResponsive } from '@/hooks/useResponsive';
import {
  confirmRegistration,
  createRegistrationPaymentOrder,
  deleteRegistration,
  getCompetition,
  getCompetitionSettings,
  getCompetitionStageForm,
  getRegistration,
  getRegistrationPaymentStatus,
  listCompetitionStages,
  listCompetitions,
  listRegistrationMaterials,
  listRegistrationPaymentOptions,
  listRegistrations,
  reconfirmRegistration,
  type RegistrationProjectSnapshotPayload,
  type RegistrationSnapshotMemberPayload,
  type RegistrationSnapshotTeamPayload,
  type RegistrationUpsertPayload,
} from '@/services/competition/api';
import type {
  CompetitionConfigItem,
  CompetitionConfigItemType,
  CompetitionPaymentOptionRecord,
  CompetitionPaymentOrderRecord,
  CompetitionRecord,
  CompetitionRegistrationRecord,
  CompetitionStageFormRecord,
} from '@/services/competition/types';
import { request, requestFile } from '@/services/common/request';
import {
  canPresentPaymentCheckout,
  presentPaymentCheckout,
  type PaymentCheckoutOrder,
} from '@/services/payment/paymentCheckout';
import type { FileObjectRecord } from '@/types/api';
import { CompetitionPaymentStep } from './components/CompetitionPaymentStep';
import {
  buildRegistrationCompetitionFallback,
  filterOpenRegistrationCompetitions,
  hasRegistrationCompetitionPricing,
  mergeRegistrationCompetitionOptions,
} from './utils/registrationCompetition';
import {
  buildRegistrationDocumentAcceptanceStorageKey,
  buildRegistrationDocumentCountdowns,
  resolveAcceptedRegistrationDocumentKeys,
} from './utils/registrationDocumentAcceptance';
import { buildRegistrationDraftStorageKey } from './utils/registrationDraftStorageKey';
import { buildRegistrationDraftIdentifiers } from './utils/registrationDraftIdentifiers';
import {
  clearLocalRegistrationDraft,
  getRegistrationDraftUpdatedAt,
  hasNewerRegistrationDraft,
  nextRegistrationDraftUpdatedAt,
  readLocalRegistrationDraft,
  resolveNewestRegistrationDraft,
  writeLocalRegistrationDraft,
  type RegistrationDraftRestoreSource,
} from './utils/registrationDraftPersistence';
import {
  buildCompetitionMaterialFileStorageContext,
  buildCompetitionStorageKey,
  shouldResetCompetitionMaterialValues,
} from './utils/competitionMaterialFileStorage';
import {
  getMissingRequiredRegistrationMaterials,
  restoreRegistrationMaterialValues,
} from './utils/registrationMaterials';
import {
  resolveMaterialFilePreviewKind,
  type MaterialFilePreviewKind,
} from './utils/materialFilePreview';
import {
  buildRegistrationProjectExtraValues,
  getMissingRequiredIntellectualPropertyFields,
  INTELLECTUAL_PROPERTY_ENTRIES_KEY,
  migrateRegistrationIntellectualPropertyValues,
  normalizeRegistrationIntellectualPropertyEntries,
} from './utils/registrationIntellectualProperties';
import {
  buildRegistrationPaymentResultUrl,
  calculateRegistrationPayableAmount,
  isRegistrationPaymentSuccessful,
  pickEnabledCollectedValues,
  retainAvailablePaymentProvider,
} from './utils/registrationCheckout';
import {
  getRegistrationStatusLabel,
  registrationStatusValueEnum,
} from './utils/registrationStatus';
import { loadOptionalPreliminaryStageForm } from './utils/loadOptionalStageForm';
import {
  formatRegistrationYearValue,
  isRegistrationYearField,
  normalizeRegistrationDateValue,
} from './utils/registrationDateValue';
import {
  isSupportedRegistrationFieldValidationConfig,
  resolveRegistrationFieldValidationRule,
  validateRegistrationFieldValue,
} from './utils/registrationFieldValidation';
import { normalizeImageCropAspectRatio, resolveImageCropAspect } from './utils/imageCropAspectRatio';
import {
  DEFAULT_STUDENT_MAX_MEMBERS,
  DEFAULT_STUDENT_MIN_MEMBERS,
  DEFAULT_TEACHER_MAX_MEMBERS,
  DEFAULT_TEACHER_MIN_MEMBERS,
  filterRegistrationParticipants,
  findRegistrationParticipantSourceIndex,
  normalizeRegistrationParticipantType,
  type RegistrationParticipantLimits,
  type RegistrationParticipantType,
} from './utils/competitionParticipantConfig';
import {
  REGISTRATION_WIZARD_FLOW_VERSION,
  isMissingPreliminaryMaterialsError,
  normalizeRegistrationWizardDraftStep,
  registrationWizardStep,
  registrationWizardStepItems,
  resolveAllowedRegistrationWizardStep,
  resolveRegistrationResumeStep,
  shouldLoadPreliminaryStageForm,
} from './utils/registrationWizardFlow';
import {
  buildFormalRegistrationListQuery,
  deleteRegistrationListEntry,
  REGISTRATION_LIST_PAGE_SIZE,
  saveRegistrationListEntry,
  shouldPaginateRegistrationList,
} from './utils/registrationListEditor';
import {
  isDeprecatedRegistrationContactField,
  resolveRegistrationFieldScope,
} from './utils/registrationFieldScope';
import {
  DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS,
  isIndependentMemberRoleField,
  normalizeIndependentMemberRoleMetadata,
  prioritizeRequiredMemberNameField,
} from './utils/competitionFieldConfig';
import {
  INTELLECTUAL_PROPERTY_GROUP_LABEL,
  getConfigItemReadingSeconds,
  getRegistrationDocumentKey,
  getTeamMemberLimits,
  normalizeCollectedFieldConfigKey,
  normalizeFileFormat,
  parseConfigItemMetadata,
} from './utils/competitionConfigShared';
import { message, modal } from '@/theme/antdFeedbackBridge';
import { API_OPTS, extractErrorMessage, showErrorMessage } from '@/utils/errorMessage';
import { sanitizeMarkdownInput } from '@/utils/markdownSecurity';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import {
  COMPETITION_REGISTRATION_SCOPE_RESOURCE,
  RegistrationDraftCloudSyncError,
  buildOptionLabelMap,
  clearCompetitionRegistrationDraft,
  clearUserDraft,
  defaultRegistrationFormValues,
  detectPaymentClientType,
  emptyRegistrationTeamMember,
  hasCompetitionRegistrationDraftContent,
  normalizeDisplayText,
  readCompetitionRegistrationDocumentAcceptance,
  readCompetitionRegistrationDraftEnvelope,
  resolveOptionLabel,
  toPositiveId,
  trimOptional,
  writeCompetitionRegistrationDocumentAcceptance,
  writeCompetitionRegistrationDraft,
  type CompetitionRegistrationDraftStorage,
  type CompetitionRegistrationListRecord,
  type CompetitionStageFormField,
  type RegistrationDraftSyncStatus,
  type RegistrationFormValues,
  type RegistrationMemberEditorKey,
  type RegistrationTeamDraft,
  type RegistrationTeamMemberDraft,
  type StoredUserDraft,
} from './CompetitionPage';
import './CompetitionPage.css';

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
      if (!canPresentPaymentCheckout({
        ...order,
        providerCode: order.providerCode || selectedPaymentProvider,
      })) {
        message.info('支付操作正在生成，弹窗会自动刷新');
      }
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

  const registrationCheckoutOrder = useMemo(() => paymentOrder ? ({
    ...paymentOrder,
    providerCode: paymentOrder.providerCode || selectedPaymentProvider,
  }) : undefined, [paymentOrder, selectedPaymentProvider]);

  const openRegistrationPaymentCheckout = useCallback(() => {
    if (!registrationCheckoutOrder) return;
    presentPaymentCheckout(registrationCheckoutOrder, {
      onOrderUpdated: (update: PaymentCheckoutOrder) => {
        setPaymentOrder((current) => current ? ({ ...current, ...update } as CompetitionPaymentOrderRecord) : current);
        void refreshPaymentResult(true);
      },
    });
  }, [refreshPaymentResult, registrationCheckoutOrder]);

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
          || record.competitionTitle
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
              {canPresentPaymentCheckout(registrationCheckoutOrder) ? (
                <Button type="primary" block onClick={openRegistrationPaymentCheckout}>前往支付</Button>
              ) : <Button type="primary" block loading disabled>支付链接生成中</Button>}
              {canPresentPaymentCheckout(registrationCheckoutOrder) ? (
                <Button block onClick={openRegistrationPaymentCheckout}>重新打开支付</Button>
              ) : null}
              <Button block onClick={() => void refreshPaymentResult()}>我已完成支付，检查结果</Button>
            </Space>
          </Modal>
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default CompetitionRegistrationPage;
