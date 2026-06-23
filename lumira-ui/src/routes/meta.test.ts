import { describe, expect, it } from 'vitest';
import { realPageRouteMetaMap, resolveCanonicalRoutePath } from './meta';

describe('route meta', () => {
  it('keeps personal file center under the personal center route group', () => {
    expect(resolveCanonicalRoutePath('/user-center/files')).toBe('/user-center/personal-center/files');
    expect(resolveCanonicalRoutePath('/user-center/files/')).toBe('/user-center/personal-center/files');
    expect(realPageRouteMetaMap.has('/user-center/personal-center/files')).toBe(true);
    expect(realPageRouteMetaMap.has('/user-center/files')).toBe(false);
  });
});
