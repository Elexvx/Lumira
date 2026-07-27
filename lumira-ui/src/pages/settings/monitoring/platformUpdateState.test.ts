import { describe, expect, it } from 'vitest';

import { canSubmitPlatformUpdate } from './platformUpdateState';

describe('canSubmitPlatformUpdate', () => {
  it('allows an installable release only when a newer version is available', () => {
    expect(canSubmitPlatformUpdate('ghcr.io/elexvx/lumira/lumira-server@sha256:release', null, true)).toBe(true);
  });

  it('blocks redeploying the current release when the platform is already up to date', () => {
    expect(canSubmitPlatformUpdate('ghcr.io/elexvx/lumira/lumira-server@sha256:release', null, false)).toBe(false);
  });

  it.each(['PENDING', 'RUNNING'])('blocks duplicate submissions while a task is %s', (status) => {
    expect(canSubmitPlatformUpdate('ghcr.io/elexvx/lumira/lumira-server@sha256:release', status, true)).toBe(false);
  });

  it('requires an installable server image', () => {
    expect(canSubmitPlatformUpdate('   ', null, true)).toBe(false);
  });
});
