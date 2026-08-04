import { afterEach, describe, expect, it, vi } from 'vitest';
import { applyBrandingRuntime, buildCopyrightText, DEFAULT_BRANDING_SETTINGS } from './settings';

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
});
