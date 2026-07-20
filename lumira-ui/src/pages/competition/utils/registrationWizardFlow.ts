export const REGISTRATION_WIZARD_FLOW_VERSION = 2;

export const registrationWizardStep = {
  competition: 0,
  team: 1,
  preliminaryMaterials: 2,
  projectEvidence: 3,
  review: 4,
  payment: 5,
} as const;

export const registrationWizardStepItems = [
  { title: '选择赛事' },
  { title: '团队与学生' },
  { title: '初赛材料' },
  { title: '项目佐证材料' },
  { title: '信息确认' },
  { title: '支付方式' },
];

export const shouldLoadPreliminaryStageForm = (step: number) => (
  step >= registrationWizardStep.preliminaryMaterials
);

export const isMissingPreliminaryMaterialsError = (message: string) => (
  message.includes('Preliminary materials must be submitted before payment')
);

type RegistrationWizardAccessState = {
  competitionReady: boolean;
  teamReady: boolean;
  projectReady: boolean;
  hasActiveRegistration: boolean;
};

export const resolveAllowedRegistrationWizardStep = (
  requestedStep: number,
  accessState: RegistrationWizardAccessState,
) => {
  const normalizedStep = Math.min(
    Math.max(Number(requestedStep) || registrationWizardStep.competition, registrationWizardStep.competition),
    registrationWizardStep.payment,
  );
  if (normalizedStep <= registrationWizardStep.competition) {
    return registrationWizardStep.competition;
  }
  if (!accessState.competitionReady) {
    return registrationWizardStep.competition;
  }
  if (normalizedStep <= registrationWizardStep.team) {
    return registrationWizardStep.team;
  }
  if (!accessState.teamReady) {
    return registrationWizardStep.team;
  }
  if (!accessState.projectReady) {
    return registrationWizardStep.team;
  }
  if (normalizedStep <= registrationWizardStep.projectEvidence) {
    return normalizedStep;
  }
  if (normalizedStep >= registrationWizardStep.payment && !accessState.hasActiveRegistration) {
    return registrationWizardStep.review;
  }
  return normalizedStep;
};

export const normalizeRegistrationWizardDraftStep = (
  currentStep: number | undefined,
  flowVersion: number | undefined,
) => {
  const normalizedStep = Math.min(Math.max(Number(currentStep) || 0, 0), registrationWizardStep.payment);
  if ((flowVersion || 1) >= REGISTRATION_WIZARD_FLOW_VERSION) {
    return normalizedStep;
  }
  if (normalizedStep === registrationWizardStep.preliminaryMaterials) {
    return registrationWizardStep.projectEvidence;
  }
  if (normalizedStep === registrationWizardStep.projectEvidence) {
    return registrationWizardStep.preliminaryMaterials;
  }
  return normalizedStep;
};
