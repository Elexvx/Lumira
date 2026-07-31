let activeRoleSwitchFlowCount = 0;

export const beginRoleSwitchFlow = () => {
  activeRoleSwitchFlowCount += 1;
  let finished = false;

  return () => {
    if (finished) {
      return;
    }
    finished = true;
    activeRoleSwitchFlowCount = Math.max(0, activeRoleSwitchFlowCount - 1);
  };
};

export const isRoleSwitchInProgress = () => activeRoleSwitchFlowCount > 0;
