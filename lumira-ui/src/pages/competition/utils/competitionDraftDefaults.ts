export type CompetitionDraftOrganizerValue = {
  role?: string;
  name?: string;
};

export type CompetitionDraftBasicSource = {
  status?: string | null;
  category?: string | null;
  level?: string | null;
  competitionLevel?: string | null;
  organizer?: string | null;
  participationScope?: string | null;
  location?: string | null;
};

const trimToUndefined = (value?: string | null) => value?.trim() || undefined;

export const normalizeCompetitionDraftBasicDefaults = (
  source: CompetitionDraftBasicSource,
  organizers: CompetitionDraftOrganizerValue[],
) => {
  const normalizedOrganizer = trimToUndefined(source.organizer);
  const hasLegacyOrganizerRow = source.status === 'draft'
    && organizers.length === 1
    && organizers[0]?.role?.trim().toLowerCase() === 'organizer'
    && !trimToUndefined(organizers[0]?.name);
  const hasLegacyOrganizerText = source.status === 'draft'
    && normalizedOrganizer?.toLowerCase() === 'organizer';
  const normalizedParticipationScope = trimToUndefined(source.participationScope);
  const normalizedLocation = trimToUndefined(source.location);
  const hasLegacyLocation = source.status === 'draft'
    && [normalizedParticipationScope, normalizedLocation]
      .filter(Boolean)
      .every((value) => value?.toUpperCase() === 'TBD')
    && [normalizedParticipationScope, normalizedLocation]
      .some((value) => value?.toUpperCase() === 'TBD');
  const hasLegacyCategory = source.status === 'draft'
    && source.category?.trim().toUpperCase() === 'OTHER'
    && !trimToUndefined(source.competitionLevel || source.level)
    && hasLegacyLocation
    && (hasLegacyOrganizerRow || hasLegacyOrganizerText);

  return {
    category: hasLegacyCategory ? undefined : trimToUndefined(source.category),
    organizer: hasLegacyOrganizerText ? undefined : normalizedOrganizer,
    organizers: hasLegacyOrganizerRow
      ? [{ role: '', name: '' }]
      : organizers.length
        ? organizers
        : [{
            role: normalizedOrganizer && !hasLegacyOrganizerText ? '主办方' : '',
            name: hasLegacyOrganizerText ? '' : normalizedOrganizer || '',
          }],
    participationScope: hasLegacyLocation
      ? undefined
      : normalizedParticipationScope || normalizedLocation,
  };
};
