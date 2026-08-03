import { describe, expect, it } from 'vitest';
import { resolveRuntimeMenuIdentity } from './runtimeMenuIdentity';
import { resolveCanonicalRoutePath } from '@/routes/meta';

describe('resolveRuntimeMenuIdentity', () => {
  it.each([
    ['/activities', '/activities/register'],
    ['/competitions', '/competitions/register'],
    ['/expert-review', '/expert-review/reviews'],
    ['/certificates', '/certificates/mine'],
    ['/experts', '/experts/management'],
    ['/workflows', '/workflows/tasks'],
  ])('keeps the catalog identity for %s instead of borrowing %s', (catalogPath, leafPath) => {
    const catalog = { key: `catalog:${catalogPath}` };
    const leaf = { key: `leaf:${leafPath}` };
    const localByPath = new Map([
      [catalogPath, catalog],
      [leafPath, leaf],
    ]);

    const identity = resolveRuntimeMenuIdentity({
      backendPath: catalogPath,
      canonicalPath: resolveCanonicalRoutePath(catalogPath),
      localByPath,
    });

    expect(resolveCanonicalRoutePath(catalogPath)).toBe(leafPath);
    expect(identity.localItem).toBe(catalog);
    expect(identity.key).toBe(catalog.key);
  });

  it('falls back to canonical metadata for a legacy alias without a direct route', () => {
    const leaf = { key: 'leaf:personal-files' };
    const identity = resolveRuntimeMenuIdentity({
      backendPath: '/user-center/files',
      canonicalPath: '/user-center/personal-center/files',
      localByPath: new Map([['/user-center/personal-center/files', leaf]]),
    });

    expect(identity.localItem).toBe(leaf);
    expect(identity.key).toBe(leaf.key);
  });

  it('prefers a stable catalog key over route-generated keys', () => {
    const identity = resolveRuntimeMenuIdentity({
      backendPath: '/certificates',
      canonicalPath: '/certificates/mine',
      localByPath: new Map([['/certificates', { key: '/certificates' }]]),
      stableKeyByPath: { '/certificates': 'main:certificates' },
    });

    expect(identity.key).toBe('main:certificates');
  });
});
