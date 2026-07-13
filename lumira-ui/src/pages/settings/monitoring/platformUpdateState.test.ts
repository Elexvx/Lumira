import { describe, expect, it } from 'vitest';

import { canSubmitPlatformUpdate } from './platformUpdateState';

describe('canSubmitPlatformUpdate', () => {
  it('keeps update available when an installable release exists, even if the agent probe is handled later by the backend', () => {
    expect(canSubmitPlatformUpdate('ghcr.io/elexvx/lumira/lumira-server@sha256:release', null)).toBe(true);
  });

  it.each(['PENDING', 'RUNNING'])('blocks duplicate submissions while a task is %s', (status) => {
    expect(canSubmitPlatformUpdate('ghcr.io/elexvx/lumira/lumira-server@sha256:release', status)).toBe(false);
  });

  it('requires an installable server image', () => {
    expect(canSubmitPlatformUpdate('   ', null)).toBe(false);
  });
});
