import { describe, expect, it } from 'vitest';
import {
  REGISTRATION_WIZARD_FLOW_VERSION,
  normalizeRegistrationWizardDraftStep,
  registrationWizardStep,
  registrationWizardStepItems,
} from './registrationWizardFlow';

describe('competition registration wizard flow', () => {
  it('collects preliminary materials before project and intellectual property evidence', () => {
    expect(registrationWizardStepItems.map((item) => item.title)).toEqual([
      '选择赛事',
      '团队与学生',
      '初赛材料',
      '项目与知识产权佐证',
      '信息确认',
      '支付方式',
    ]);
    expect(registrationWizardStep.preliminaryMaterials).toBeLessThan(registrationWizardStep.projectEvidence);
  });

  it('moves legacy drafts to the matching content step after the order changes', () => {
    expect(normalizeRegistrationWizardDraftStep(2, undefined)).toBe(registrationWizardStep.projectEvidence);
    expect(normalizeRegistrationWizardDraftStep(3, 1)).toBe(registrationWizardStep.preliminaryMaterials);
    expect(normalizeRegistrationWizardDraftStep(2, REGISTRATION_WIZARD_FLOW_VERSION)).toBe(
      registrationWizardStep.preliminaryMaterials,
    );
  });
});
