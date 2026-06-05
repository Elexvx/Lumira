import assert from 'node:assert/strict';
import { commitThemePreference } from '../src/theme/apply';
import { resolveThemeRuntimeSnapshot, syncThemeRuntimeSnapshot } from '../src/theme/runtime';

const resolveProLayoutNavTheme = () => (resolveThemeRuntimeSnapshot().resolvedColorMode === 'dark' ? 'realDark' : 'light');

const resolveProLayoutThemeSettings = () => ({
  navTheme: resolveProLayoutNavTheme(),
});

const run = () => {
  syncThemeRuntimeSnapshot('light', false);
  assert.equal(resolveProLayoutNavTheme(), 'light', 'light theme should map to light nav theme');
  assert.deepEqual(resolveProLayoutThemeSettings(), { navTheme: 'light' }, 'layout should consume ProLayout settings');

  syncThemeRuntimeSnapshot('dark', false);
  assert.equal(resolveProLayoutNavTheme(), 'realDark', 'dark theme should map to realDark nav theme');

  syncThemeRuntimeSnapshot('system', true);
  assert.equal(resolveProLayoutNavTheme(), 'realDark', 'system dark mode should map to realDark nav theme');

  const previousDocument = (globalThis as { document?: unknown }).document;
  (globalThis as { document?: unknown }).document = {
    documentElement: {
      dataset: {
        theme: 'dark',
      },
      style: {},
    },
    body: {
      style: {},
    },
  };
  syncThemeRuntimeSnapshot('light', false);
  assert.equal(resolveProLayoutNavTheme(), 'light', 'synced runtime theme should win over stale document theme');

  const committedSnapshot = commitThemePreference('dark', { systemDarkMode: false, persist: false });
  assert.equal(committedSnapshot.resolvedColorMode, 'dark', 'committed dark theme should sync runtime immediately');
  assert.equal(resolveProLayoutNavTheme(), 'realDark', 'layout nav theme should follow committed theme immediately');

  if (previousDocument) {
    (globalThis as { document?: unknown }).document = previousDocument;
  } else {
    delete (globalThis as { document?: unknown }).document;
  }

  console.log('theme-nav-sync-smoke: ok');
};

run();
