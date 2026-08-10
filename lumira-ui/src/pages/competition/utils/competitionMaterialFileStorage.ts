const COMPETITION_UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const normalizeStorageTagSegment = (value: string | undefined, fallback: string) => {
  const normalized = (value || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 64);
  return normalized || fallback;
};

export type CompetitionMaterialFileStorageContext = {
  directory: string;
  tags: string;
};

export const buildCompetitionMaterialFileStorageContext = (
  competitionUuid: string | undefined,
  stageCode: string | undefined,
  fieldKey: string,
): CompetitionMaterialFileStorageContext | undefined => {
  const normalizedCompetitionUuid = (competitionUuid || '').trim().toLowerCase();
  if (!COMPETITION_UUID_PATTERN.test(normalizedCompetitionUuid)) {
    return undefined;
  }
  const compactCompetitionUuid = normalizedCompetitionUuid.replace(/-/g, '');
  const normalizedStageCode = normalizeStorageTagSegment(stageCode, 'general');
  const normalizedFieldKey = normalizeStorageTagSegment(fieldKey, 'material');
  return {
    directory: `competitions/${compactCompetitionUuid}`,
    tags: [
      'competition-material',
      `competition:${normalizedCompetitionUuid}`,
      `stage:${normalizedStageCode}`,
      `field:${normalizedFieldKey}`,
    ].join(','),
  };
};

export const shouldResetCompetitionMaterialValues = (
  previousCompetitionId: number | undefined,
  nextCompetitionId: number | undefined,
) => Boolean(previousCompetitionId && nextCompetitionId && previousCompetitionId !== nextCompetitionId);
