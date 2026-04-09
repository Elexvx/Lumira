import type { AgreementSettings } from '@/types/api';

export const DEFAULT_AGREEMENT_SETTINGS: AgreementSettings = {
  userAgreementMarkdown: '',
  privacyAgreementMarkdown: '',
};

export const normalizeAgreementSettings = (settings?: Partial<AgreementSettings> | null): AgreementSettings => ({
  userAgreementMarkdown: normalizeMarkdown(settings?.userAgreementMarkdown),
  privacyAgreementMarkdown: normalizeMarkdown(settings?.privacyAgreementMarkdown),
});

const normalizeMarkdown = (value: unknown) => {
  if (typeof value !== 'string') {
    return '';
  }
  return value;
};
