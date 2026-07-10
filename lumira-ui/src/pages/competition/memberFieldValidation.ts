export type MemberFieldValidationTarget = 'name' | 'school' | undefined;

const MEMBER_NAME_PATTERN = /^[\p{Script=Han}A-Za-z·]{2,64}$/u;
const SCHOOL_NAME_PATTERN = /^[\p{Script=Han}A-Za-z（）()]+(?: [\p{Script=Han}A-Za-z（）()]+)*$/u;

const normalizeFieldIdentity = (itemKey?: string, title?: string) =>
  `${itemKey || ''}|${title || ''}`.toLowerCase();

export const resolveMemberFieldValidationTarget = (
  itemKey?: string,
  title?: string,
): MemberFieldValidationTarget => {
  const identity = normalizeFieldIdentity(itemKey, title);
  if (/(membername|realname|fullname|姓名)/.test(identity)) {
    return 'name';
  }
  if (/(school|university|college|学校|院校)/.test(identity)) {
    return 'school';
  }
  return undefined;
};

export const validateMemberTextField = (
  itemKey: string | undefined,
  title: string | undefined,
  value: unknown,
): string | undefined => {
  if (typeof value !== 'string' || !value) {
    return undefined;
  }
  const target = resolveMemberFieldValidationTarget(itemKey, title);
  if (!target) {
    return undefined;
  }
  if (value !== value.trim() || (target === 'name' && /\s/.test(value))) {
    return `${title || '该字段'}不能包含空格`;
  }
  if (target === 'school' && (/\s{2,}/.test(value) || /[\t\r\n]/.test(value))) {
    return `${title || '学校'}不能包含连续空格`;
  }

  if (target === 'name' && !MEMBER_NAME_PATTERN.test(value)) {
    return `${title || '姓名'}只能输入中文、英文字母或间隔号`;
  }
  if (target === 'school' && !SCHOOL_NAME_PATTERN.test(value)) {
    return `${title || '学校'}只能输入中文、英文字母、空格或括号`;
  }
  return undefined;
};
