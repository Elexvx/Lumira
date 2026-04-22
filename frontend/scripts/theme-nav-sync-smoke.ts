import assert from 'node:assert/strict';
import { resolveLayoutNavTheme, syncThemeRuntimeSnapshot } from '../src/theme/runtime';

const run = () => {
  syncThemeRuntimeSnapshot('light', false);
  assert.equal(resolveLayoutNavTheme(), 'light', 'light theme should map to light nav theme');

  syncThemeRuntimeSnapshot('dark', false);
  assert.equal(resolveLayoutNavTheme(), 'realDark', 'dark theme should map to realDark nav theme');

  syncThemeRuntimeSnapshot('system', true);
  assert.equal(resolveLayoutNavTheme(), 'realDark', 'system dark mode should map to realDark nav theme');

  console.log('theme-nav-sync-smoke: ok');
};

run();
