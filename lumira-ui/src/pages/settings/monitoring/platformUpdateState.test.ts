import { describe, expect, it } from 'vitest';

import { canSubmitPlatformUpdate, resolvePlatformUpdateConfirmationDetails } from './platformUpdateState';

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

  it('resolves the versions, commit and release notes shown before an update', () => {
    expect(resolvePlatformUpdateConfirmationDetails(
      {
        current: { version: '1.4.0', commitId: 'current-commit' },
        latest: { version: '1.5.0', commitId: 'latest-commit', title: 'Fallback release title' },
        manifest: { releaseNotes: '修复工作台菜单并完善升级确认' },
      },
      {
        preflightId: 'preflight-1',
        ready: true,
        targetVersion: '1.5.1',
        targetCommit: 'preflight-commit',
      },
    )).toEqual({
      currentVersion: '1.4.0',
      currentCommit: 'current-commit',
      targetVersion: '1.5.1',
      targetCommit: 'preflight-commit',
      releaseNotes: '修复工作台菜单并完善升级确认',
    });
  });

  it('falls back to the update source title when release notes are unavailable', () => {
    expect(resolvePlatformUpdateConfirmationDetails({
      latest: { version: 'main', commitId: 'latest-commit', title: 'Lumira main build' },
    })).toMatchObject({
      targetVersion: 'main',
      targetCommit: 'latest-commit',
      releaseNotes: 'Lumira main build',
    });
  });
});
