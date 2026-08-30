export const DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS = '负责人\n成员';

type RegistrationFieldMetadata = Record<string, unknown> & {
  fieldType?: string;
  options?: string;
  optionSource?: 'CUSTOM' | 'DICTIONARY';
  dictCode?: string;
};

export const normalizeIndependentMemberRoleMetadata = <T extends RegistrationFieldMetadata>(
  itemType: string | undefined,
  itemKey: string | undefined,
  metadata: T,
): T => {
  if (itemType !== 'MEMBER_FIELD' || itemKey !== 'role' || metadata.fieldType?.toUpperCase() !== 'ROLE') {
    return metadata;
  }
  return {
    ...metadata,
    fieldType: 'SELECT',
    options: metadata.options?.trim() || DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS,
  };
};

export const isIndependentMemberRoleField = (
  scope: string | undefined,
  itemKey: string | undefined,
  fieldType: string | undefined,
) => scope === 'MEMBER_FIELD' && itemKey === 'role' && fieldType?.toUpperCase() !== 'ROLE';

export const normalizeSchoolDictionaryMetadata = <T extends RegistrationFieldMetadata>(
  itemKey: string | undefined,
  title: string | undefined,
  metadata: T,
): T => {
  const normalizedKey = (itemKey || '').replace(/[^a-z0-9]/gi, '').toLowerCase();
  const isSchoolField = ['school', 'schoolname', 'college', 'university'].includes(normalizedKey)
    || /所在院校|学校|院校/.test(title || '');
  if (!isSchoolField || !['TEXT', 'SELECT'].includes((metadata.fieldType || 'TEXT').toUpperCase())) {
    return metadata;
  }
  return {
    ...metadata,
    fieldType: 'SELECT',
    optionSource: 'DICTIONARY',
    dictCode: 'sys_school',
    options: undefined,
    placeholder: undefined,
  };
};

const normalizeFieldKey = (value?: string) => (value || '').replace(/[^a-z0-9]/gi, '').toLowerCase();

export const prioritizeRequiredMemberNameField = <T extends { itemKey: string; required?: boolean }>(
  fields: T[],
  fallback: T,
): T[] => {
  const memberNameIndex = fields.findIndex((field) => ['membername', 'name'].includes(normalizeFieldKey(field.itemKey)));
  if (memberNameIndex < 0) {
    return [{ ...fallback, required: true }, ...fields];
  }
  const memberNameField = fields[memberNameIndex];
  return [
    { ...memberNameField, required: true },
    ...fields.filter((_, index) => index !== memberNameIndex),
  ];
};

export const getNextScopedConfigItemSortOrder = <T extends { sortOrder?: number }>(
  items: readonly T[],
  scopedIndexes: readonly number[],
): number => {
  const maxSortOrder = scopedIndexes.reduce((max, itemIndex) => {
    const sortOrder = Number(items[itemIndex]?.sortOrder);
    return Number.isFinite(sortOrder) ? Math.max(max, sortOrder) : max;
  }, 0);
  return maxSortOrder + 10;
};

export const reorderScopedConfigItems = <T extends { sortOrder?: number }>(
  items: T[],
  scopedIndexes: number[],
  fromIndex: number,
  toIndex: number,
): T[] => {
  if (
    fromIndex === toIndex
    || fromIndex < 0
    || toIndex < 0
    || fromIndex >= scopedIndexes.length
    || toIndex >= scopedIndexes.length
  ) {
    return items;
  }
  const fromItemIndex = scopedIndexes[fromIndex];
  if (!items[fromItemIndex] || !items[scopedIndexes[toIndex]]) {
    return items;
  }
  const scopedItems = scopedIndexes.map((itemIndex) => items[itemIndex]);
  const [moved] = scopedItems.splice(fromIndex, 1);
  scopedItems.splice(toIndex, 0, moved);
  const nextItems = [...items];
  scopedIndexes.forEach((itemIndex, index) => {
    nextItems[itemIndex] = {
      ...scopedItems[index],
      sortOrder: (index + 1) * 10,
    };
  });
  return nextItems;
};
