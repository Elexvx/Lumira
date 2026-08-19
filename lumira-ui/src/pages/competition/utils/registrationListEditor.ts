export const REGISTRATION_LIST_PAGE_SIZE = 5;

export type RegistrationListEditorIndex = number | 'new';

export const saveRegistrationListEntry = <T,>(
  entries: readonly T[],
  editorIndex: RegistrationListEditorIndex,
  entry: T,
) => editorIndex === 'new'
  ? [...entries, entry]
  : entries.map((current, index) => (index === editorIndex ? entry : current));

export const deleteRegistrationListEntry = <T,>(entries: readonly T[], index: number) => (
  entries.filter((_, currentIndex) => currentIndex !== index)
);

export const shouldPaginateRegistrationList = (entryCount: number) => (
  entryCount > REGISTRATION_LIST_PAGE_SIZE
);

export const buildFormalRegistrationListQuery = (
  pageNo: number,
  pageSize: number,
  status?: string,
  keyword?: string,
) => ({
  pageNo,
  pageSize,
  status: status?.trim() || undefined,
  keyword: keyword?.trim() || undefined,
});
