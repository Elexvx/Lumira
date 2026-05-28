import assert from 'node:assert/strict';
import { theme as antdTheme } from 'antd';
import { buildAntdThemeConfig } from '../src/theme/antdTheme';
import { syncThemeRuntimeSnapshot } from '../src/theme/runtime';

const run = () => {
  syncThemeRuntimeSnapshot('dark', false);
  const darkTheme = buildAntdThemeConfig();
  const darkAlgorithm = Array.isArray(darkTheme?.algorithm) ? darkTheme.algorithm[0] : darkTheme?.algorithm;
  assert.equal(darkAlgorithm, antdTheme.darkAlgorithm, 'dark theme should use the dark algorithm');
  assert.equal(darkTheme?.token?.colorBgElevated, '#1b1b1b', 'dark theme should lift modal surfaces');
  assert.equal(darkTheme?.components?.Table?.headerBg, '#151515', 'dark table header should be defined by component token');
  assert.equal(darkTheme?.components?.Button?.defaultBg, '#151515', 'dark default button should be defined by component token');
  assert.deepEqual(darkTheme?.cssVar, {}, 'dark theme should let antd manage css var scope');

  syncThemeRuntimeSnapshot('light', false);
  const lightTheme = buildAntdThemeConfig();
  const lightAlgorithm = Array.isArray(lightTheme?.algorithm) ? lightTheme.algorithm[0] : lightTheme?.algorithm;
  assert.equal(lightAlgorithm, antdTheme.defaultAlgorithm, 'light theme should use the default algorithm');
  assert.equal(lightTheme?.components?.Table?.headerBg, '#ffffff', 'light table header should be defined by component token');
  assert.equal(lightTheme?.components?.Button?.defaultBg, '#ffffff', 'light default button should be defined by component token');
  assert.deepEqual(lightTheme?.cssVar, {}, 'light theme should let antd manage css var scope');

  syncThemeRuntimeSnapshot('compact', false);
  const compactTheme = buildAntdThemeConfig();
  const compactAlgorithm = Array.isArray(compactTheme?.algorithm) ? compactTheme.algorithm[0] : compactTheme?.algorithm;
  assert.equal(compactAlgorithm, antdTheme.compactAlgorithm, 'compact theme should use the compact algorithm');
  assert.deepEqual(compactTheme?.cssVar, {}, 'compact theme should let antd manage css var scope');

  console.log('antd-static-theme-smoke: ok');
};

run();
