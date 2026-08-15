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

  it('keeps certificate administration under data management during menu migration', () => {
    const items = buildBreadcrumbItems([
      {
        id: 1079,
        menuCode: 'certificate.root',
        name: '证书中心',
        path: '/certificates',
        children: [
          { id: 1082, menuCode: 'certificate.records', name: '全局证书记录', path: '/certificates/records' },
        ],
      },
    ], '/certificates/records');

    expect(items).toEqual([
      { key: '/data-management', title: 'nav.data.management', href: '/data-management' },
      { key: '/certificates/records', title: 'nav.certificates.records' },
    ]);
  });
});
