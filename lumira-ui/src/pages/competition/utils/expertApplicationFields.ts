import dayjs from 'dayjs';
import type { CompetitionConfigItem } from '@/services/competition/types';
import type { ExpertUpsertPayload } from '@/services/expert/types';
import { resolveRegistrationFieldValidationRule } from './registrationFieldValidation';

export type ExpertApplicationFieldType =
  | 'TEXT'
  | 'TEXTAREA'
  | 'IMAGE'
  | 'ROLE'
  | 'NUMBER'
  | 'DATE'
  | 'SELECT'
  | 'MULTI_SELECT'
  | 'MOBILE'
  | 'EMAIL';

export type ExpertApplicationField = {
  itemKey: string;
  title: string;
  fieldType: ExpertApplicationFieldType;
  placeholder?: string;
  description?: string;
  groupLabel?: string;
  options?: string;
  validationRule?: string;
  required: boolean;
};
export type ExpertApplicationFormValues = Record<string, unknown>;

type ExpertStandardField = keyof Pick<
  ExpertUpsertPayload,
  'name' | 'title' | 'organization' | 'position' | 'expertise' | 'phone' | 'mobile'
    | 'idCardNumber' | 'email' | 'avatarUrl' | 'bio' | 'tags'
>;

const DEFAULT_EXPERT_APPLICATION_FIELDS: ExpertApplicationField[] = [
  { itemKey: 'name', title: '专家姓名', fieldType: 'TEXT', validationRule: 'PERSON_NAME', required: true },
  { itemKey: 'expertise', title: '专业领域', fieldType: 'TEXT', required: true },
  { itemKey: 'organization', title: '所属机构', fieldType: 'TEXT', required: false },
  { itemKey: 'position', title: '职务', fieldType: 'TEXT', required: false },
  { itemKey: 'mobile', title: '手机号码', fieldType: 'MOBILE', validationRule: 'CHINA_MOBILE', required: false },
  { itemKey: 'email', title: '邮箱', fieldType: 'EMAIL', validationRule: 'EMAIL', required: false },
  { itemKey: 'bio', title: '专家简介', fieldType: 'TEXTAREA', required: false },
];

const parseMetadata = (contentJson?: string | null): Record<string, unknown> => {
  if (!contentJson) {
    return {};
  }
  try {
    const parsed = JSON.parse(contentJson);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : {};
  } catch {
    return {};
  }
};

const normalizeFieldType = (value: unknown): ExpertApplicationFieldType => {
  const fieldType = String(value || 'TEXT').toUpperCase();
  return ['TEXT', 'TEXTAREA', 'IMAGE', 'ROLE', 'NUMBER', 'DATE', 'SELECT', 'MULTI_SELECT', 'MOBILE', 'EMAIL']
    .includes(fieldType)
    ? fieldType as ExpertApplicationFieldType
    : 'TEXT';
};

export const parseExpertApplicationFields = (items: CompetitionConfigItem[]): ExpertApplicationField[] => {
  const fields = items
    .filter((item) => item.itemType === 'EXPERT_FIELD' && item.enabled !== false)
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
    .map((item) => {
      const metadata = parseMetadata(item.contentJson);
      const fieldType = normalizeFieldType(metadata.fieldType);
      return {
        itemKey: item.itemKey,
        title: item.title || item.itemKey,
        fieldType,
        placeholder: typeof metadata.placeholder === 'string' ? metadata.placeholder : undefined,
        description: typeof metadata.description === 'string' ? metadata.description : undefined,
        groupLabel: typeof metadata.groupLabel === 'string' ? metadata.groupLabel : undefined,
        options: typeof metadata.options === 'string' ? metadata.options : undefined,
        validationRule: resolveRegistrationFieldValidationRule(
          fieldType,
          typeof metadata.validationRule === 'string' ? metadata.validationRule : undefined,
          'EXPERT_FIELD',
          item.itemKey,
        ),
        required: Boolean(item.requiredFlag),
      } satisfies ExpertApplicationField;
    });
  return fields.length ? fields : DEFAULT_EXPERT_APPLICATION_FIELDS;
};

export const parseExpertApplicationFieldOptions = (options?: string) =>
  (options || '')
    .split(/\r?\n/u)
    .map((option) => option.trim())
    .filter(Boolean)
    .filter((option, index, allOptions) => allOptions.indexOf(option) === index)
    .map((option) => ({ label: option, value: option }));

const normalizeKey = (value?: string) => (value || '').replace(/[^a-z0-9]/gi, '').toLowerCase();

const standardFieldAliases: Record<ExpertStandardField, string[]> = {
  name: ['name', 'expertname', 'fullname'],
  title: ['title', 'experttitle'],
  organization: ['organization', 'company', 'institution'],
  position: ['position', 'jobtitle'],
  expertise: ['expertise', 'specialty', 'speciality'],
  phone: ['phone', 'telephone'],
  mobile: ['mobile', 'mobilephone'],
  idCardNumber: ['idcardnumber', 'idcard', 'identitycard'],
  email: ['email', 'mail'],
  avatarUrl: ['avatarurl', 'avatar'],
  bio: ['bio', 'introduction', 'profile'],
  tags: ['tags', 'tag'],
};

export const resolveExpertStandardFieldKey = (itemKey?: string): ExpertStandardField | undefined => {
  const normalizedKey = normalizeKey(itemKey);
  return (Object.entries(standardFieldAliases).find(([, aliases]) => aliases.includes(normalizedKey))?.[0]
    || undefined) as ExpertStandardField | undefined;
};

const serializeFormValue = (value: unknown): unknown => {
  if (dayjs.isDayjs(value)) {
    return value.format('YYYY-MM-DD');
  }
  if (Array.isArray(value)) {
    return value.map(serializeFormValue);
  }
  return value;
};

const toStringValue = (value: unknown) => {
  const normalized = serializeFormValue(value);
  if (Array.isArray(normalized)) {
    return normalized.map((item) => String(item)).filter(Boolean).join(',');
  }
  if (normalized === undefined || normalized === null) {
    return undefined;
  }
  const result = String(normalized).trim();
  return result || undefined;
};

export const buildExpertApplicationPayload = (
  fields: ExpertApplicationField[],
  values: ExpertApplicationFormValues,
  competitionUuid: string,
): ExpertUpsertPayload => {
  const standardValues: Partial<Record<ExpertStandardField, string>> = {};
  const extraValues: Record<string, unknown> = {};
  fields.forEach((field) => {
    const value = serializeFormValue(values[field.itemKey]);
    if (value !== undefined && value !== null && value !== '') {
      extraValues[field.itemKey] = value;
    }
    const standardField = resolveExpertStandardFieldKey(field.itemKey);
    if (standardField) {
      const stringValue = toStringValue(value);
      if (stringValue !== undefined) {
        standardValues[standardField] = stringValue;
      }
    }
  });

  return {
    competitionUuid,
    name: standardValues.name || '',
    expertise: standardValues.expertise || '',
    title: standardValues.title,
    organization: standardValues.organization,
    position: standardValues.position,
    phone: standardValues.phone,
    mobile: standardValues.mobile,
    idCardNumber: standardValues.idCardNumber,
    email: standardValues.email,
    avatarUrl: standardValues.avatarUrl,
    bio: standardValues.bio,
    tags: standardValues.tags,
    status: 'active',
    sort: 100,
    extraValues,
  };
};
