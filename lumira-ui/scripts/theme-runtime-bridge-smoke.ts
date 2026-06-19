import assert from 'node:assert/strict';

const buildThemeRuntimeRevisionKey = (themePreference: string, resolvedColorMode: 'light' | 'dark') =>
  `${themePreference}:${resolvedColorMode}`;

const shouldAdvanceThemeRevision = (previousThemeKey: string | undefined, nextThemeKey: string) =>
  Boolean(previousThemeKey && previousThemeKey !== nextThemeKey);

const run = () => {
  const lightKey = buildThemeRuntimeRevisionKey('light', 'light');
  const darkKey = buildThemeRuntimeRevisionKey('dark', 'dark');
  const systemDarkKey = buildThemeRuntimeRevisionKey('system', 'dark');

  assert.equal(lightKey, 'light:light', 'revision key should include the selected preference');
  assert.equal(systemDarkKey, 'system:dark', 'revision key should include resolved system mode');
  assert.equal(shouldAdvanceThemeRevision(undefined, lightKey), false, 'initial mount should not force layout refresh');
  assert.equal(shouldAdvanceThemeRevision(lightKey, lightKey), false, 'unchanged theme should not force layout refresh');
  assert.equal(shouldAdvanceThemeRevision(lightKey, darkKey), true, 'preference changes should refresh layout');
  assert.equal(shouldAdvanceThemeRevision(lightKey, systemDarkKey), true, 'resolved color changes should refresh layout');

  console.log('theme-runtime-bridge-smoke: ok');
};

run();
