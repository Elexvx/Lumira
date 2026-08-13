import type { CompetitionWorkspaceModule } from '@/services/competition/types';

export const COMPETITION_UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export const COMPETITION_WORKSPACE_MODULES: Array<{
  key: CompetitionWorkspaceModule;
  label: string;
}> = [
  { key: 'overview', label: '概览' },
  { key: 'registrations', label: '报名与材料' },
  { key: 'reviews', label: '评审' },
  { key: 'payments', label: '支付' },
  { key: 'certificates', label: '证书' },
  { key: 'settings', label: '赛事设置' },
  { key: 'audit', label: '审计' },
];

export const isCompetitionUuid = (value?: string | null): value is string =>
  Boolean(value && COMPETITION_UUID_PATTERN.test(value.trim()));

export const normalizeCompetitionUuid = (value?: string | null) => {
  const normalized = value?.trim().toLowerCase() || '';
  return isCompetitionUuid(normalized) ? normalized : undefined;
};

export const competitionWorkspacePath = (
  competitionUuid: string,
  module: CompetitionWorkspaceModule = 'overview',
) => `/competitions/${encodeURIComponent(competitionUuid)}/${module}`;

export const competitionWorkspaceModuleFromPath = (pathname: string): CompetitionWorkspaceModule => {
  const match = pathname.match(/^\/competitions\/[^/]+\/([^/?#]+)/i);
  const module = match?.[1] as CompetitionWorkspaceModule | undefined;
  return COMPETITION_WORKSPACE_MODULES.some((item) => item.key === module)
    ? module!
    : 'overview';
};
