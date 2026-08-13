const LAST_COMPETITION_WORKSPACE_KEY = 'lumira.competition-workspace.last-uuid';

export const readLastCompetitionWorkspaceUuid = () => {
  if (typeof window === 'undefined') return undefined;
  try {
    return window.sessionStorage.getItem(LAST_COMPETITION_WORKSPACE_KEY) || undefined;
  } catch {
    return undefined;
  }
};

export const writeLastCompetitionWorkspaceUuid = (competitionUuid: string) => {
  if (typeof window === 'undefined') return;
  try {
    window.sessionStorage.setItem(LAST_COMPETITION_WORKSPACE_KEY, competitionUuid);
  } catch {
    // Session storage is a convenience only; route context remains authoritative.
  }
};

export const clearLastCompetitionWorkspaceUuid = () => {
  if (typeof window === 'undefined') return;
  try {
    window.sessionStorage.removeItem(LAST_COMPETITION_WORKSPACE_KEY);
  } catch {
    // Ignore storage policy failures.
  }
};
