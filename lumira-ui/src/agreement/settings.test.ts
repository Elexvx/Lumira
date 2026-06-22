import { describe, expect, it } from 'vitest';
import { hasConfiguredAgreementSettings, normalizeAgreementSettings } from './settings';

describe('agreement settings', () => {
  it('treats missing or whitespace-only markdown as not configured', () => {
    expect(hasConfiguredAgreementSettings()).toBe(false);
    expect(hasConfiguredAgreementSettings(normalizeAgreementSettings({ userAgreementMarkdown: ' ', privacyAgreementMarkdown: '\n\t' }))).toBe(false);
  });

  it('requires login agreement when either agreement has content', () => {
    expect(hasConfiguredAgreementSettings(normalizeAgreementSettings({ userAgreementMarkdown: 'User terms' }))).toBe(true);
    expect(hasConfiguredAgreementSettings(normalizeAgreementSettings({ privacyAgreementMarkdown: 'Privacy policy' }))).toBe(true);
  });
});
