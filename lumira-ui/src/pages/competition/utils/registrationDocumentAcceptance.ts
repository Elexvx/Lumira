type RegistrationDocumentIdentity = {
  id?: number;
  itemKey?: string;
  itemType?: string;
  title?: string;
  contentJson?: string | null;
  contentText?: string | null;
  updatedAt?: string | null;
};

const REGISTRATION_DOCUMENT_ACCEPTANCE_PREFIX = 'lumira.registration.documents.accepted.v1';

const hashText = (value: string) => {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(36);
};

export const getLegacyRegistrationDocumentKey = (
  item: RegistrationDocumentIdentity,
  index: number,
) => String(item.id || item.itemKey || `${item.itemType}-${index}`);

export const getRegistrationDocumentAcceptanceKey = (
  item: RegistrationDocumentIdentity,
  index: number,
) => {
  const identity = getLegacyRegistrationDocumentKey(item, index);
  const version = hashText([
    item.title || '',
    item.contentText || '',
    item.contentJson || '',
    item.updatedAt || '',
  ].join('\u001f'));
  return `${identity}:${version}`;
};

export const buildRegistrationDocumentAcceptanceStorageKey = (competitionUuid: string) =>
  `${REGISTRATION_DOCUMENT_ACCEPTANCE_PREFIX}:${competitionUuid.trim()}`;

export const resolveAcceptedRegistrationDocumentKeys = (
  documents: RegistrationDocumentIdentity[],
  rememberedKeys: string[] = [],
  draftKeys: string[] = [],
) => {
  const remembered = new Set(rememberedKeys);
  const draft = new Set(draftKeys);
  return documents.flatMap((item, index) => {
    const currentKey = getRegistrationDocumentAcceptanceKey(item, index);
    const legacyKey = getLegacyRegistrationDocumentKey(item, index);
    return remembered.has(currentKey) || draft.has(currentKey) || draft.has(legacyKey)
      ? [currentKey]
      : [];
  });
};

export const buildRegistrationDocumentCountdowns = <T extends RegistrationDocumentIdentity>(
  documents: T[],
  acceptedKeys: string[],
  getReadingSeconds: (item: T) => number,
) => {
  const accepted = new Set(acceptedKeys);
  return Object.fromEntries(documents.map((item, index) => {
    const key = getRegistrationDocumentAcceptanceKey(item, index);
    return [key, accepted.has(key) ? 0 : getReadingSeconds(item)];
  }));
};
