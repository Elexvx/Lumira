import type { DictOption } from '@/hooks/useDictOptions';

export const protectedUserStatusOptions = (options: DictOption[], protectedAdminSelected: boolean) =>
  protectedAdminSelected ? options.filter((option) => option.value === 'ENABLED') : options;
