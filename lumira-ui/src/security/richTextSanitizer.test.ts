// @vitest-environment jsdom

import { describe, expect, it } from 'vitest';
import { sanitizeRichText } from '@/security/richTextSanitizer';

describe('sanitizeRichText', () => {
  it('removes executable markup, event handlers, styles, and unsafe URLs', () => {
    const sanitized = sanitizeRichText(`
      <p onclick="alert(1)" style="color:red">Hello</p>
      <script>alert(1)</script>
      <a href="javascript:alert(1)">bad</a>
      <img src="/api/v1/files/42/preview" onerror="alert(1)">
    `);

    expect(sanitized).toContain('<p>Hello</p>');
    expect(sanitized).toContain('/api/v1/files/42/preview');
    expect(sanitized).not.toMatch(/onclick|onerror|javascript:|<script|style=/i);
  });

  it('preserves legitimate formatting and hardens external links', () => {
    const sanitized = sanitizeRichText(
      '<p><strong>Details</strong></p><a href="https://example.com/help">Help</a>',
    );

    expect(sanitized).toContain('<strong>Details</strong>');
    expect(sanitized).toContain('href="https://example.com/help"');
    expect(sanitized).toContain('target="_blank"');
    expect(sanitized).toContain('rel="noopener noreferrer nofollow"');
  });
});
