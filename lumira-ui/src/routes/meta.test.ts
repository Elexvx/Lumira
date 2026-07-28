import { describe, expect, it } from 'vitest';
import { realPageRouteMetaMap, resolveCanonicalRoutePath } from './meta';

describe('route meta', () => {
  it('keeps personal file center under the personal center route group', () => {
    expect(resolveCanonicalRoutePath('/user-center/files')).toBe('/user-center/personal-center/files');
    expect(resolveCanonicalRoutePath('/user-center/files/')).toBe('/user-center/personal-center/files');
    expect(realPageRouteMetaMap.has('/user-center/personal-center/files')).toBe(true);
    expect(realPageRouteMetaMap.has('/user-center/files')).toBe(false);
  });

  it('removes standalone management and query-center pages', () => {
    expect(resolveCanonicalRoutePath('/projects')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/projects/management')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/projects/search')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/team')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/team/management')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/team/search')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/activities/search')).toBe('/activities/management');
    expect(resolveCanonicalRoutePath('/payments/status')).toBe('/payments/management');
    expect(resolveCanonicalRoutePath('/data-management/query-center')).toBe('/dashboard/home');
    expect(realPageRouteMetaMap.has('/projects/management')).toBe(false);
    expect(realPageRouteMetaMap.has('/team/management')).toBe(false);
    expect(realPageRouteMetaMap.has('/projects/search')).toBe(false);
    expect(realPageRouteMetaMap.has('/team/search')).toBe(false);
    expect(realPageRouteMetaMap.has('/activities/search')).toBe(false);
    expect(realPageRouteMetaMap.has('/payments/status')).toBe(false);
  });
});
