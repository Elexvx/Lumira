export const INTELLECTUAL_PROPERTY_ENTRIES_KEY = 'intellectualProperties';

export type RegistrationIntellectualPropertyEntry = Record<string, unknown>;

const hasMeaningfulValue = (value: unknown): boolean => {
  if (Array.isArray(value)) {
    return value.some(hasMeaningfulValue);
  }
  if (value && typeof value === 'object') {
    return Object.values(value as Record<string, unknown>).some(hasMeaningfulValue);
  }
  return value !== undefined && value !== null && String(value).trim() !== '';
};

const pickEntryValues = (
  values: Record<string, unknown> | null | undefined,
  enabledKeys: readonly string[],
) => {
  const enabled = new Set(enabledKeys);
  return Object.fromEntries(
    Object.entries(values || {}).filter(([key, value]) => enabled.has(key) && hasMeaningfulValue(value)),
  );
};

export const normalizeRegistrationIntellectualPropertyEntries = (
  extraValues: Record<string, unknown> | null | undefined,
  intellectualPropertyFieldKeys: readonly string[],
): RegistrationIntellectualPropertyEntry[] => {
  const storedEntries = extraValues?.[INTELLECTUAL_PROPERTY_ENTRIES_KEY];
  if (Array.isArray(storedEntries)) {
    return storedEntries
      .filter((entry): entry is Record<string, unknown> => Boolean(entry) && typeof entry === 'object' && !Array.isArray(entry))
      .map((entry) => pickEntryValues(entry, intellectualPropertyFieldKeys))
      .filter(hasMeaningfulValue);
  }

  const legacyEntry = pickEntryValues(extraValues, intellectualPropertyFieldKeys);
  return hasMeaningfulValue(legacyEntry) ? [legacyEntry] : [];
};

export const migrateRegistrationIntellectualPropertyValues = (
  extraValues: Record<string, unknown> | null | undefined,
  intellectualPropertyFieldKeys: readonly string[],
) => {
  if (!intellectualPropertyFieldKeys.length) {
    return { ...(extraValues || {}) };
  }
  const intellectualPropertyKeys = new Set(intellectualPropertyFieldKeys);
  const migratedValues = Object.fromEntries(
    Object.entries(extraValues || {}).filter(([key]) => (
      key !== INTELLECTUAL_PROPERTY_ENTRIES_KEY && !intellectualPropertyKeys.has(key)
    )),
  );
  const entries = normalizeRegistrationIntellectualPropertyEntries(extraValues, intellectualPropertyFieldKeys);
  if (entries.length) {
    migratedValues[INTELLECTUAL_PROPERTY_ENTRIES_KEY] = entries;
  }
  return migratedValues;
};

export const buildRegistrationProjectExtraValues = (
  extraValues: Record<string, unknown> | null | undefined,
  projectFieldKeys: readonly string[],
  intellectualPropertyFieldKeys: readonly string[],
) => {
  const projectValues = pickEntryValues(extraValues, projectFieldKeys);
  const intellectualPropertyEntries = normalizeRegistrationIntellectualPropertyEntries(
    extraValues,
    intellectualPropertyFieldKeys,
  );
  return intellectualPropertyEntries.length
    ? {
        ...projectValues,
        [INTELLECTUAL_PROPERTY_ENTRIES_KEY]: intellectualPropertyEntries,
      }
    : projectValues;
};

export const hasRegistrationIntellectualPropertyContent = (
  extraValues: Record<string, unknown> | null | undefined,
) => hasMeaningfulValue(extraValues);
