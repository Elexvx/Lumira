import { describe, expect, it } from 'vitest';
import {
  REGISTRATION_WIZARD_FLOW_VERSION,
  normalizeRegistrationWizardDraftStep,
  registrationWizardStep,
  registrationWizardStepItems,
  resolveAllowedRegistrationWizardStep,
} from './registrationWizardFlow';

describe('competition registration wizard flow', () => {
  it('collects project basics with the team before sequential materials', () => {
    expect(registrationWizardStepItems.map((item) => item.title)).toEqual([
      '选择赛事',
      '团队与学生',
      '初赛材料',
      '项目佐证材料',
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

  it('keeps the user on the team step until the new project is entered', () => {
    expect(resolveAllowedRegistrationWizardStep(registrationWizardStep.projectEvidence, {
      competitionReady: true,
      teamReady: true,
      projectReady: false,
      hasActiveRegistration: false,
    })).toBe(registrationWizardStep.team);
  });

  it('allows sequential materials and review after the project is entered', () => {
    expect(resolveAllowedRegistrationWizardStep(registrationWizardStep.review, {
      competitionReady: true,
      teamReady: true,
      projectReady: false,
      hasActiveRegistration: false,
    })).toBe(registrationWizardStep.team);
    expect(resolveAllowedRegistrationWizardStep(registrationWizardStep.review, {
      competitionReady: true,
      teamReady: true,
      projectReady: true,
      hasActiveRegistration: false,
    })).toBe(registrationWizardStep.review);
  });
});
