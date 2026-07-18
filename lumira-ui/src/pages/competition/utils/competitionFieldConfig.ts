export const DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS = '负责人\n成员';

type RegistrationFieldMetadata = Record<string, unknown> & {
  fieldType?: string;
  options?: string;
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
  const toItemIndex = scopedIndexes[toIndex];
  if (!items[fromItemIndex] || !items[toItemIndex]) {
    return items;
  }
  const nextItems = [...items];
  [nextItems[fromItemIndex], nextItems[toItemIndex]] = [nextItems[toItemIndex], nextItems[fromItemIndex]];
  scopedIndexes.forEach((itemIndex, index) => {
    nextItems[itemIndex] = {
      ...nextItems[itemIndex],
      sortOrder: (index + 1) * 10,
    };
  });
  return nextItems;
};
