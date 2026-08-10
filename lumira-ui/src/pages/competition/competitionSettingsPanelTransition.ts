export type CompetitionSettingsFlushablePanel = {
  flushPendingSave: () => Promise<boolean>;
};

export const transitionAfterCompetitionSettingsSave = async (
  panel: CompetitionSettingsFlushablePanel | null,
  transition: () => void,
): Promise<boolean> => {
  const saved = await panel?.flushPendingSave() ?? true;
  if (!saved) {
    return false;
  }
  transition();
  return true;
};
