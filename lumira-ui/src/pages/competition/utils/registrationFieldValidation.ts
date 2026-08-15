export const REGISTRATION_FIELD_TYPES = new Set([
  'TEXT', 'TEXTAREA', 'IMAGE', 'ROLE', 'NUMBER', 'DATE', 'SELECT', 'MULTI_SELECT', 'MOBILE', 'EMAIL',
]);
export const REGISTRATION_FIELD_VALIDATION_RULES = new Set([
  'NONE', 'PERSON_NAME', 'DISPLAY_NAME', 'CHINA_MOBILE', 'EMAIL', 'ID_CARD',
]);

export const resolveRegistrationFieldValidationRule = (
  fieldType?: string,
  validationRule?: string,
  scope?: string,
  itemKey?: string,
) => {
  const normalizedFieldType = (fieldType || '').toUpperCase();
  if (normalizedFieldType === 'MOBILE') {
    return 'CHINA_MOBILE';
  }
  if (normalizedFieldType === 'EMAIL') {
    return 'EMAIL';
  }
  const configuredRule = (validationRule || 'NONE').toUpperCase();
  if (configuredRule !== 'NONE') {
    return configuredRule;
  }

  const normalizedScope = (scope || '').toUpperCase();
  const normalizedKey = (itemKey || '').replace(/[^a-z0-9]/gi, '').toLowerCase();
  if (normalizedScope === 'TEAM_FIELD' && ['teamname', 'name'].includes(normalizedKey)) {
    return 'DISPLAY_NAME';
  }
  if (normalizedScope === 'MEMBER_FIELD' && ['membername', 'name'].includes(normalizedKey)) {
    return 'PERSON_NAME';
  }
  if (normalizedScope === 'PROJECT_FIELD' && ['projecttitle', 'projectname', 'title', 'name'].includes(normalizedKey)) {
    return 'DISPLAY_NAME';
  }
  if (normalizedScope === 'EXPERT_FIELD' && ['expertname', 'fullname', 'name'].includes(normalizedKey)) {
    return 'PERSON_NAME';
  }
  return configuredRule;
};

export const CHINA_MOBILE_PATTERN = /^1[3-9]\d{9}$/;
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const ID_CARD_PATTERN = /^(?:\d{15}|\d{17}[\dXx])$/;
export const PERSON_NAME_PATTERN = /^[\p{Script=Han}A-Za-z·]{2,64}$/u;
export const DISPLAY_NAME_PATTERN = /^(?=.{1,128}$)(?=.*[\p{Script=Han}A-Za-z0-9])[\p{Script=Han}A-Za-z0-9 ·•&＋+（）()《》【】[\]—_:/：，,.、'’#-]+$/u;
const SUSPICIOUS_DISPLAY_INITIALISM_PATTERN = /(?:^|[^A-Za-z])(?:[A-Za-z]['’]){2,}[A-Za-z](?:$|[^A-Za-z])/;

export const isSupportedRegistrationFieldValidationConfig = (
  fieldType?: string,
  validationRule?: string,
) => REGISTRATION_FIELD_TYPES.has((fieldType || 'TEXT').toUpperCase())
  && REGISTRATION_FIELD_VALIDATION_RULES.has((validationRule || 'NONE').toUpperCase());

export const validateRegistrationFieldValue = (
  fieldType: string | undefined,
  validationRule: string | undefined,
  title: string | undefined,
  value: unknown,
  scope?: string,
  itemKey?: string,
): string | undefined => {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  const resolvedRule = resolveRegistrationFieldValidationRule(fieldType, validationRule, scope, itemKey);
  if (!isSupportedRegistrationFieldValidationConfig(fieldType, resolvedRule)) {
    return `${title || '字段'}的校验规则配置无效`;
  }
  const rawText = String(value);
  const text = rawText.trim();
  if (resolvedRule === 'CHINA_MOBILE' && !CHINA_MOBILE_PATTERN.test(text)) {
    return `请输入正确的${title || '手机号'}`;
  }
  if (resolvedRule === 'EMAIL' && !EMAIL_PATTERN.test(text)) {
    return `请输入正确的${title || '邮箱'}`;
  }
  if (resolvedRule === 'ID_CARD' && !ID_CARD_PATTERN.test(text)) {
    return `请输入正确的${title || '身份证号'}`;
  }
  if (resolvedRule === 'PERSON_NAME') {
    if (rawText !== text || /\s/.test(rawText)) {
      return `${title || '姓名'}不能包含空格`;
    }
    if (!PERSON_NAME_PATTERN.test(rawText)) {
      return `${title || '姓名'}只能输入中文、英文字母或间隔号`;
    }
  }
  if (resolvedRule === 'DISPLAY_NAME') {
    if (rawText !== text || /\s{2,}|[\t\r\n]/.test(rawText)) {
      return `${title || '名称'}不能包含首尾空格、连续空格或换行`;
    }
    if (!DISPLAY_NAME_PATTERN.test(rawText) || SUSPICIOUS_DISPLAY_INITIALISM_PATTERN.test(rawText)) {
      return `${title || '名称'}只能输入中文、英文字母、数字及常用命名符号`;
    }
  }
  return undefined;
};
