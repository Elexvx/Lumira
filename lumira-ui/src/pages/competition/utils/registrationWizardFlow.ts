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
  { title: '项目与知识产权佐证' },
  { title: '信息确认' },
  { title: '支付方式' },
];

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
