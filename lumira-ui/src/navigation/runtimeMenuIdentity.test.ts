import { describe, expect, it } from 'vitest';
import {
  resolveRuntimeMenuHideInMenu,
  resolveRuntimeMenuIdentity,
  resolveRuntimeMenuPath,
} from './runtimeMenuIdentity';
import { resolveCanonicalRoutePath } from '@/routes/meta';

describe('resolveRuntimeMenuIdentity', () => {
  it.each([
    ['/registration', '/competitions/register'],
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

  it.each([
    ['/registration', '/competitions/register'],
    ['/certificates', '/certificates/mine'],
    ['/experts', '/experts/management'],
    ['/expert-review', '/expert-review/reviews'],
    ['/workflows', '/workflows/tasks'],
  ])('keeps the stable redirect catalog path %s instead of making it pathless', (catalogPath, leafPath) => {
    expect(resolveRuntimeMenuPath({
      backendPath: catalogPath,
      canonicalPath: leafPath,
      component: `redirect:${leafPath}`,
      hasChildren: true,
      stableKeyByPath: { [catalogPath]: `main:${catalogPath.slice(1)}` },
    })).toBe(catalogPath);
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

  it('normalizes a trailing slash before resolving catalog metadata and stable keys', () => {
    const catalog = { key: 'catalog:certificates' };
    const leaf = { key: 'leaf:mine' };
    const identity = resolveRuntimeMenuIdentity({
      backendPath: '/certificates/',
      canonicalPath: '/certificates/mine',
      localByPath: new Map([
        ['/certificates', catalog],
        ['/certificates/mine', leaf],
      ]),
      stableKeyByPath: { '/certificates': 'main:certificates' },
    });

    expect(identity.localItem).toBe(catalog);
    expect(identity.key).toBe('main:certificates');
  });

  it.each([
    '/certificates',
    '/experts',
    '/expert-review',
    '/workflows',
  ])('keeps stable catalog %s visible when a same-path redirect marks local metadata hidden', (catalogPath) => {
    expect(resolveRuntimeMenuHideInMenu({
      backendPath: catalogPath,
      canonicalPath: resolveCanonicalRoutePath(catalogPath),
      localHideInMenu: true,
      stableKeyByPath: { [catalogPath]: `main:${catalogPath.slice(1)}` },
    })).toBe(false);
  });

  it('preserves hidden metadata for a non-stable route', () => {
    expect(resolveRuntimeMenuHideInMenu({
      backendPath: '/settings',
      canonicalPath: '/settings',
      routeHideInMenu: true,
    })).toBe(true);
  });
});
