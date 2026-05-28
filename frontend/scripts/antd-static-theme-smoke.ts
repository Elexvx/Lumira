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
  assert.equal(darkTheme?.cssVar?.key, 'saas-dark-dark', 'dark theme should have a unique css var key');

  syncThemeRuntimeSnapshot('compact', false);
  const compactTheme = buildAntdThemeConfig();
  const compactAlgorithm = Array.isArray(compactTheme?.algorithm) ? compactTheme.algorithm[0] : compactTheme?.algorithm;
  assert.equal(compactAlgorithm, antdTheme.compactAlgorithm, 'compact theme should use the compact algorithm');
  assert.equal(compactTheme?.cssVar?.key, 'saas-compact-light', 'compact theme should have a unique css var key');

  console.log('antd-static-theme-smoke: ok');
};

run();
