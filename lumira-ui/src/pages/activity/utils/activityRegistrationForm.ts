import dayjs from 'dayjs';
import type {
  ActivityRegistrationAnswer,
  ActivityRegistrationField,
  ActivityRegistrationFieldType,
  ActivityRegistrationValue,
} from '@/services/activity/types';

export const activityRegistrationFieldTypeOptions: Array<{
  label: string;
  value: ActivityRegistrationFieldType;
}> = [
  { label: '单行文本', value: 'TEXT' },
  { label: '多行文本', value: 'TEXTAREA' },
  { label: '数字', value: 'NUMBER' },
  { label: '日期', value: 'DATE' },
  { label: '下拉选择', value: 'SELECT' },
  { label: '多选', value: 'MULTI_SELECT' },
  { label: '手机号（自动校验）', value: 'MOBILE' },
  { label: '邮箱（自动校验）', value: 'EMAIL' },
];

const defaultRegistrationFields: ActivityRegistrationField[] = [
  { fieldKey: 'name', label: '姓名', fieldType: 'TEXT', placeholder: '请输入姓名', required: true, options: [] },
  { fieldKey: 'mobile', label: '手机号', fieldType: 'MOBILE', placeholder: '请输入手机号', required: true, options: [] },
  { fieldKey: 'email', label: '邮箱', fieldType: 'EMAIL', placeholder: '请输入邮箱', required: false, options: [] },
  { fieldKey: 'organization', label: '单位', fieldType: 'TEXT', placeholder: '请输入单位', required: false, options: [] },
  { fieldKey: 'position', label: '职务', fieldType: 'TEXT', placeholder: '请输入职务', required: false, options: [] },
  { fieldKey: 'remark', label: '备注', fieldType: 'TEXTAREA', placeholder: '请输入备注', required: false, options: [] },
];

export const createDefaultActivityRegistrationFields = () =>
  defaultRegistrationFields.map((field) => ({ ...field, options: [...(field.options || [])] }));

export const isActivityRegistrationChoiceField = (fieldType?: ActivityRegistrationFieldType) =>
  fieldType === 'SELECT' || fieldType === 'MULTI_SELECT';

export const normalizeActivityRegistrationFields = (fields?: ActivityRegistrationField[]) =>
  (fields || []).map((field) => ({
    ...field,
    fieldKey: field.fieldKey.trim(),
    label: field.label.trim(),
    placeholder: field.placeholder?.trim() || undefined,
    description: field.description?.trim() || undefined,
    required: Boolean(field.required),
    options: isActivityRegistrationChoiceField(field.fieldType)
      ? Array.from(new Set((field.options || []).map((option) => option.trim()).filter(Boolean)))
      : [],
  }));

export const normalizeActivityRegistrationAnswers = (
  fields: ActivityRegistrationField[],
  values?: Record<string, unknown>,
) => Object.fromEntries(fields.map((field) => {
  const value = values?.[field.fieldKey];
  if (field.fieldType === 'DATE' && value && dayjs.isDayjs(value)) {
    return [field.fieldKey, value.format('YYYY-MM-DD')];
  }
  if (field.fieldType === 'NUMBER' && typeof value === 'number' && Number.isFinite(value)) {
    return [field.fieldKey, value];
  }
  if (field.fieldType === 'MULTI_SELECT') {
    return [field.fieldKey, Array.isArray(value) ? value.map(String) : []];
  }
  if (value === undefined || value === null || value === '') {
    return [field.fieldKey, null];
  }
  return [field.fieldKey, String(value)];
})) as Record<string, ActivityRegistrationValue>;

export const formatActivityRegistrationValue = (value: ActivityRegistrationValue | unknown) => {
  if (Array.isArray(value)) {
    return value.length ? value.join('、') : '-';
  }
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  return String(value);
};

export const summarizeActivityRegistrationAnswers = (answers?: ActivityRegistrationAnswer[]) =>
  (answers || [])
    .filter((answer) => formatActivityRegistrationValue(answer.value) !== '-')
    .map((answer) => `${answer.label}：${formatActivityRegistrationValue(answer.value)}`)
    .join('；');
