import { describe, expect, it } from 'vitest';
import { buildBreadcrumbItems } from './managementBreadcrumb';

describe('buildBreadcrumbItems', () => {
  it('uses absolute hrefs instead of concatenated breadcrumb paths', () => {
    const items = buildBreadcrumbItems(undefined, '/competitions/register/payment-result');

    expect(items).toHaveLength(3);
    expect(items[0]).toMatchObject({ href: '/competitions' });
    expect(items[1]).toMatchObject({ href: '/competitions/register' });
    expect(items[2]?.href).toBeUndefined();
    expect(items.every((item) => !('path' in item))).toBe(true);
  });
});
