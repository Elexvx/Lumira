import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  applyBrandingRuntime,
  buildCopyrightText,
  DEFAULT_BRANDING_SETTINGS,
  normalizeBrandingSettings,
} from './settings';

class FakeHead {
  iconLink: { rel: string; href: string } | null = null;

  querySelector() {
    return this.iconLink;
  }

  appendChild(node: { rel: string; href: string }) {
    this.iconLink = node;
    return node;
  }
}

const installFakeDocument = () => {
  const head = new FakeHead();
  const fakeDocument = {
    title: 'Old Title',
    head,
    createElement: () => ({ rel: '', href: '' }),
  };
  vi.stubGlobal('document', fakeDocument);
  return { document: fakeDocument, head };
};

describe('applyBrandingRuntime', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('updates the browser title and favicon from branding settings', () => {
    const { document, head } = installFakeDocument();

    applyBrandingRuntime({
      websiteName: 'New Site',
      websiteFaviconUrl: 'https://cdn.example.com/favicon.ico',
    });

    expect(document.title).toBe('New Site');
    expect(head.iconLink).toEqual({
      rel: 'icon',
      href: 'https://cdn.example.com/favicon.ico',
    });
  });

  it('uses the Lumira project name in the default title and copyright', () => {
    expect(DEFAULT_BRANDING_SETTINGS.websiteName).toBe('Lumira');
    expect(DEFAULT_BRANDING_SETTINGS.companyName).toBe('Lumira');
    expect(buildCopyrightText()).toContain('Lumira All Rights Reserved');
  });

  it('uses a creative but editable maintenance copy by default', () => {
    expect(DEFAULT_BRANDING_SETTINGS.maintenanceTitle).toBe('马上回来，精彩不掉线');
    expect(DEFAULT_BRANDING_SETTINGS.maintenanceMessage).toContain('精彩不会缺席');
  });

  it('normalizes the optional maintenance end time without enabling it by default', () => {
    expect(DEFAULT_BRANDING_SETTINGS.maintenanceEndAt).toBe('');
    expect(normalizeBrandingSettings({ maintenanceEndAt: 'invalid' }).maintenanceEndAt).toBe('');
    expect(
      normalizeBrandingSettings({ maintenanceEndAt: '2026-08-07T12:00:00+08:00' }).maintenanceEndAt,
    ).toBe('2026-08-07T04:00:00.000Z');
  });

  it('defaults and normalizes maintenance login role ids', () => {
    expect(DEFAULT_BRANDING_SETTINGS.maintenanceAllowedRoleIds).toEqual([1001]);
    expect(normalizeBrandingSettings().maintenanceAllowedRoleIds).toEqual([1001]);
    expect(
      normalizeBrandingSettings({ maintenanceAllowedRoleIds: [3002, 1001, 3002, -1] }).maintenanceAllowedRoleIds,
    ).toEqual([1001, 3002]);
    expect(normalizeBrandingSettings({ maintenanceAllowedRoleIds: [] }).maintenanceAllowedRoleIds).toEqual([]);
  });
});
