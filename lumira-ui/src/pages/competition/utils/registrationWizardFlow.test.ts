import { describe, expect, it } from 'vitest';
import {
  REGISTRATION_WIZARD_FLOW_VERSION,
  isMissingPreliminaryMaterialsError,
  normalizeRegistrationWizardDraftStep,
  registrationWizardStep,
  registrationWizardStepItems,
  resolveAllowedRegistrationWizardStep,
  resolveRegistrationResumeStep,
  shouldLoadPreliminaryStageForm,
} from './registrationWizardFlow';

describe('competition registration wizard flow', () => {
  it('collects project basics with the team before sequential materials', () => {
    expect(registrationWizardStepItems.map((item) => item.title)).toEqual([
      '选择赛事',
      '基本信息',
      '初赛材料',
      '项目佐证材料',
      '信息确认',
      '支付方式',
    ]);
    expect(registrationWizardStep.preliminaryMaterials).toBeLessThan(registrationWizardStep.projectEvidence);
  });

  it('loads preliminary material configuration after direct wizard re-entry', () => {
    expect(shouldLoadPreliminaryStageForm(registrationWizardStep.competition)).toBe(false);
    expect(shouldLoadPreliminaryStageForm(registrationWizardStep.preliminaryMaterials)).toBe(true);
    expect(shouldLoadPreliminaryStageForm(registrationWizardStep.review)).toBe(true);
    expect(shouldLoadPreliminaryStageForm(registrationWizardStep.payment)).toBe(true);
  });

  it('recognizes the payment material gate returned by the backend', () => {
    expect(isMissingPreliminaryMaterialsError('Preliminary materials must be submitted before payment')).toBe(true);
    expect(isMissingPreliminaryMaterialsError('Invalid date')).toBe(false);
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

  it('resumes incomplete registrations at the missing material step', () => {
    expect(resolveRegistrationResumeStep({
      hasPaymentOrder: false,
      hasIncompleteTeamOrProject: false,
      hasMissingRequiredMaterials: true,
      hasMissingRequiredEvidence: false,
    })).toBe(registrationWizardStep.preliminaryMaterials);
    expect(resolveRegistrationResumeStep({
      hasPaymentOrder: false,
      hasIncompleteTeamOrProject: false,
      hasMissingRequiredMaterials: false,
      hasMissingRequiredEvidence: false,
    })).toBe(registrationWizardStep.review);
  });

  it('resumes at the earliest incomplete page', () => {
    expect(resolveRegistrationResumeStep({
      hasPaymentOrder: false,
      hasIncompleteTeamOrProject: true,
      hasMissingRequiredMaterials: true,
      hasMissingRequiredEvidence: true,
    })).toBe(registrationWizardStep.team);
    expect(resolveRegistrationResumeStep({
      hasPaymentOrder: false,
      hasIncompleteTeamOrProject: false,
      hasMissingRequiredMaterials: false,
      hasMissingRequiredEvidence: true,
    })).toBe(registrationWizardStep.projectEvidence);
  });

  it('resumes an existing payment order at payment', () => {
    expect(resolveRegistrationResumeStep({
      hasPaymentOrder: true,
      hasIncompleteTeamOrProject: true,
      hasMissingRequiredMaterials: true,
      hasMissingRequiredEvidence: true,
    })).toBe(registrationWizardStep.payment);
  });
});
