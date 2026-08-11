import type { CompetitionRegistrationExportRequest } from '@/services/competition/types';

export const MAX_SELECTED_REGISTRATION_COUNT = 500;

export type RegistrationExportQuery = {
  competitionId?: number;
  status?: string;
  keyword?: string;
};

type RegistrationExportScopeInput = {
  hasCompetition: boolean;
  filteredCount: number;
  selectedCount: number;
};

export type RegistrationExportScope = {
  mode: 'filtered' | 'selected';
  count: number;
  disabled: boolean;
  exportLabel: string;
  materialPackageLabel: string;
};

const normalizeCount = (value: number) => (
  Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0
);

const withCount = (label: string, count: number) => (
  count > 0 ? `${label}（${count}）` : label
);

export const resolveRegistrationExportScope = ({
  hasCompetition,
  filteredCount,
  selectedCount,
}: RegistrationExportScopeInput): RegistrationExportScope => {
  const normalizedSelectedCount = normalizeCount(selectedCount);
  const normalizedFilteredCount = normalizeCount(filteredCount);
  const mode = normalizedSelectedCount > 0 ? 'selected' : 'filtered';
  const count = mode === 'selected' ? normalizedSelectedCount : normalizedFilteredCount;

  return {
    mode,
    count,
    disabled: !hasCompetition || count === 0,
    exportLabel: withCount(
      mode === 'selected' ? '仅导出所选报名记录' : '仅导出筛选报名记录',
      count,
    ),
    materialPackageLabel: withCount(
      mode === 'selected' ? '导出所选完整材料' : '导出筛选完整材料',
      count,
    ),
  };
};

const normalizeRegistrationIds = (registrationIds: number[]) => Array.from(new Set(
  registrationIds.filter((registrationId) => Number.isInteger(registrationId) && registrationId > 0),
));

export const buildCompetitionRegistrationExportRequest = (
  query: RegistrationExportQuery,
  registrationIds: number[],
): CompetitionRegistrationExportRequest | undefined => {
  if (!query.competitionId) return undefined;

  const normalizedRegistrationIds = normalizeRegistrationIds(registrationIds);
  if (normalizedRegistrationIds.length) {
    return {
      competitionId: query.competitionId,
      registrationIds: normalizedRegistrationIds,
    };
  }

  return {
    competitionId: query.competitionId,
    status: query.status,
    keyword: query.keyword,
  };
};

export const buildRegistrationQuerySignature = (query: RegistrationExportQuery) => JSON.stringify([
  query.competitionId ?? null,
  query.status ?? null,
  query.keyword ?? null,
]);
