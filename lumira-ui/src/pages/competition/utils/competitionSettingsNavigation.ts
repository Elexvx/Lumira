export const competitionSettingsSectionKeys = [
  'basic',
  'registration',
  'stages',
  'payments',
  'review',
] as const;

export type CompetitionSettingsSectionKey = (typeof competitionSettingsSectionKeys)[number];

export type CompetitionSettingsRegistrationTab =
  | 'TEAM_AND_MEMBER'
  | 'PROJECT_FIELD'
  | 'INTELLECTUAL_PROPERTY'
  | 'documents';

export type CompetitionSettingsStageTab = string;

export type CompetitionSettingsNavigation = {
  section: CompetitionSettingsSectionKey;
  registrationTab: CompetitionSettingsRegistrationTab;
  stageTab: CompetitionSettingsStageTab;
};

export const getCompetitionSettingsStageTabFallback = (
  section: CompetitionSettingsSectionKey,
  stageTab: CompetitionSettingsStageTab,
  availableStageTabs: readonly CompetitionSettingsStageTab[],
): CompetitionSettingsStageTab | undefined => {
  if (section !== 'stages' || stageTab === 'timeline' || availableStageTabs.includes(stageTab)) {
    return undefined;
  }
  return 'timeline';
};

const registrationTabByQueryValue: Record<string, CompetitionSettingsRegistrationTab> = {
  registration: 'PROJECT_FIELD',
  'team-members': 'TEAM_AND_MEMBER',
  'other-fields': 'PROJECT_FIELD',
  project: 'PROJECT_FIELD',
  'intellectual-property': 'INTELLECTUAL_PROPERTY',
  documents: 'documents',
};

const registrationQueryValueByTab: Record<CompetitionSettingsRegistrationTab, string> = {
  TEAM_AND_MEMBER: 'team-members',
  PROJECT_FIELD: 'project',
  INTELLECTUAL_PROPERTY: 'intellectual-property',
  documents: 'documents',
};

const isSectionKey = (value: string | null): value is CompetitionSettingsSectionKey =>
  competitionSettingsSectionKeys.includes(value as CompetitionSettingsSectionKey);

export const parseCompetitionSettingsNavigation = (search: string): CompetitionSettingsNavigation => {
  const params = new URLSearchParams(search);
  const sectionValue = params.get('section');
  const section = isSectionKey(sectionValue) ? sectionValue : 'basic';
  const tabValue = params.get('tab') || '';

  return {
    section,
    registrationTab: section === 'registration'
      ? registrationTabByQueryValue[tabValue] || 'PROJECT_FIELD'
      : 'PROJECT_FIELD',
    stageTab: section === 'stages'
      ? tabValue === 'files'
        ? 'preliminary'
        : tabValue || 'timeline'
      : 'timeline',
  };
};

export const createCompetitionSettingsSearch = (
  currentSearch: string,
  section: CompetitionSettingsSectionKey,
  detail?: CompetitionSettingsRegistrationTab | CompetitionSettingsStageTab,
) => {
  const params = new URLSearchParams(currentSearch);
  params.set('section', section);

  if (section === 'registration') {
    const registrationTab = detail && detail in registrationQueryValueByTab
      ? detail as CompetitionSettingsRegistrationTab
      : 'PROJECT_FIELD';
    params.set('tab', registrationQueryValueByTab[registrationTab]);
  } else if (section === 'stages') {
    params.set('tab', detail || 'timeline');
  } else {
    params.delete('tab');
  }

  const query = params.toString();
  return query ? `?${query}` : '';
};
