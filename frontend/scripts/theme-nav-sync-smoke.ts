import assert from 'node:assert/strict';
import { commitThemePreference } from '../src/theme/apply';
import { resolveLayoutNavTheme, syncThemeRuntimeSnapshot } from '../src/theme/runtime';

const run = () => {
  syncThemeRuntimeSnapshot('light', false);
  assert.equal(resolveLayoutNavTheme(), 'light', 'light theme should map to light nav theme');

  syncThemeRuntimeSnapshot('dark', false);
  assert.equal(resolveLayoutNavTheme(), 'realDark', 'dark theme should map to realDark nav theme');

  syncThemeRuntimeSnapshot('system', true);
  assert.equal(resolveLayoutNavTheme(), 'realDark', 'system dark mode should map to realDark nav theme');

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
  assert.equal(resolveLayoutNavTheme(), 'light', 'synced runtime theme should win over stale document theme');

  const committedSnapshot = commitThemePreference('dark', { systemDarkMode: false, persist: false });
  assert.equal(committedSnapshot.resolvedColorMode, 'dark', 'committed dark theme should sync runtime immediately');
  assert.equal(resolveLayoutNavTheme(), 'realDark', 'layout nav theme should follow committed theme immediately');

  if (previousDocument) {
    (globalThis as { document?: unknown }).document = previousDocument;
  } else {
    delete (globalThis as { document?: unknown }).document;
  }

  console.log('theme-nav-sync-smoke: ok');
};

run();
