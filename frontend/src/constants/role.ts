export const ROLE_TYPE_OPTIONS = [
  { label: '系统角色', value: 'SYSTEM' },
  { label: '内置角色', value: 'BUILTIN' },
  { label: '自定义角色', value: 'CUSTOM' },
] as const;

export const ROLE_TYPE_LABEL_MAP: Record<string, string> = {
  SYSTEM: '系统角色',
  BUILTIN: '内置角色',
  CUSTOM: '自定义角色',
};
