import { resolveBuiltinMessage } from '@/i18n/messages';

export const ROLE_TYPE_OPTIONS = [
  { label: resolveBuiltinMessage('role.system', '系统角色'), value: 'SYSTEM' },
  { label: resolveBuiltinMessage('role.builtin', '内置角色'), value: 'BUILTIN' },
  { label: resolveBuiltinMessage('role.custom', '自定义角色'), value: 'CUSTOM' },
] as const;

export const ROLE_TYPE_LABEL_MAP: Record<string, string> = {
  SYSTEM: resolveBuiltinMessage('role.system', '系统角色'),
  BUILTIN: resolveBuiltinMessage('role.builtin', '内置角色'),
  CUSTOM: resolveBuiltinMessage('role.custom', '自定义角色'),
};
