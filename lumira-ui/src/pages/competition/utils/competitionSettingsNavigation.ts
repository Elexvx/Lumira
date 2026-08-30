export const competitionSettingsSectionKeys = [
  'basic',
  'notice',
  'registration',
  'experts',
  'stages',
  'payments',
  'awards',
  'danger',
] as const;

export type CompetitionSettingsSectionKey = (typeof competitionSettingsSectionKeys)[number];

export type CompetitionSettingsRegistrationTab =
  | 'PROJECT_FIELD'
  | 'TEAM_FIELD'
  | 'MEMBER_FIELD'
  | 'TEACHER_FIELD'
  | 'INTELLECTUAL_PROPERTY';

export type CompetitionSettingsStageTab = string;

export type CompetitionSettingsNavigation = {
  section: CompetitionSettingsSectionKey;
  registrationTab: CompetitionSettingsRegistrationTab;
  stageTab: CompetitionSettingsStageTab;
};

export const competitionSettingsMenuItems: Array<{
  key: CompetitionSettingsSectionKey;
  label: string;
  className?: string;
}> = [
  { key: 'basic', label: '基础信息' },
  { key: 'notice', label: '赛事须知' },
  { key: 'registration', label: '报名设置' },
  { key: 'experts', label: '专家设置' },
  { key: 'stages', label: '赛程与材料' },
  { key: 'payments', label: '费用设置' },
  { key: 'awards', label: '获奖设置' },
  { key: 'danger', label: '危险操作', className: 'competition-settings-sidebar__danger-item' },
];

export const competitionSettingsRegistrationTabItems: Array<{
  key: CompetitionSettingsRegistrationTab;
  label: string;
}> = [
  { key: 'PROJECT_FIELD', label: '项目信息' },
  { key: 'TEAM_FIELD', label: '团队信息' },
  { key: 'MEMBER_FIELD', label: '学生信息' },
  { key: 'TEACHER_FIELD', label: '指导老师信息' },
  { key: 'INTELLECTUAL_PROPERTY', label: '知识产权信息' },
];

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
  'team-members': 'TEAM_FIELD',
  students: 'MEMBER_FIELD',
  student: 'MEMBER_FIELD',
  teachers: 'TEACHER_FIELD',
  teacher: 'TEACHER_FIELD',
  team: 'TEAM_FIELD',
  'other-fields': 'PROJECT_FIELD',
  project: 'PROJECT_FIELD',
  'intellectual-property': 'INTELLECTUAL_PROPERTY',
};

const registrationQueryValueByTab: Record<CompetitionSettingsRegistrationTab, string> = {
  PROJECT_FIELD: 'project',
  TEAM_FIELD: 'team',
  MEMBER_FIELD: 'students',
  TEACHER_FIELD: 'teachers',
  INTELLECTUAL_PROPERTY: 'intellectual-property',
};

const legacyRegistrationSectionByQueryValue: Record<string, CompetitionSettingsSectionKey> = {
  experts: 'experts',
  'expert-fields': 'experts',
  documents: 'notice',
};

const isSectionKey = (value: string | null): value is CompetitionSettingsSectionKey =>
  competitionSettingsSectionKeys.includes(value as CompetitionSettingsSectionKey);

export const parseCompetitionSettingsNavigation = (search: string): CompetitionSettingsNavigation => {
  const params = new URLSearchParams(search);
  const sectionValue = params.get('section');
  const tabValue = params.get('tab') || '';
  const canUseLegacyRegistrationTab = !sectionValue || sectionValue === 'registration';
  const section = canUseLegacyRegistrationTab && legacyRegistrationSectionByQueryValue[tabValue]
    ? legacyRegistrationSectionByQueryValue[tabValue]
    : canUseLegacyRegistrationTab && registrationTabByQueryValue[tabValue]
      ? 'registration'
      : isSectionKey(sectionValue) ? sectionValue : 'basic';

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
