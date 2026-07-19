export const resolveRegistrationFieldValidationRule = (
  fieldType?: string,
  validationRule?: string,
) => {
  const normalizedFieldType = (fieldType || '').toUpperCase();
  if (normalizedFieldType === 'MOBILE') {
    return 'CHINA_MOBILE';
  }
  if (normalizedFieldType === 'EMAIL') {
    return 'EMAIL';
  }
  return (validationRule || 'NONE').toUpperCase();
};

export const CHINA_MOBILE_PATTERN = /^1[3-9]\d{9}$/;

export const validateRegistrationFieldValue = (
  fieldType: string | undefined,
  validationRule: string | undefined,
  title: string | undefined,
  value: unknown,
): string | undefined => {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  const resolvedRule = resolveRegistrationFieldValidationRule(fieldType, validationRule);
  if (resolvedRule === 'CHINA_MOBILE' && !CHINA_MOBILE_PATTERN.test(String(value).trim())) {
    return `请输入正确的${title || '手机号'}`;
  }
  return undefined;
};
