import assert from 'node:assert/strict';
import { theme as antdTheme } from 'antd';
import { buildAntdThemeConfig } from '../src/theme/antdTheme';
import { syncThemeRuntimeSnapshot } from '../src/theme/runtime';

const run = () => {
  syncThemeRuntimeSnapshot('dark', false);
  const darkTheme = buildAntdThemeConfig();
  const darkAlgorithm = Array.isArray(darkTheme?.algorithm) ? darkTheme.algorithm[0] : darkTheme?.algorithm;
  assert.equal(darkAlgorithm, antdTheme.darkAlgorithm, 'dark theme should use the dark algorithm');
  assert.equal(darkTheme?.token?.colorPrimary, '#1677ff', 'dark theme should keep the primary seed token');
  assert.equal(darkTheme?.token?.colorBgElevated, '#1b1b1b', 'dark theme should lift modal surfaces');
  assert.equal(darkTheme?.components, undefined, 'dark theme should not keep legacy component color overrides');
  assert.deepEqual(darkTheme?.cssVar, { key: 'lumira-dark' }, 'dark theme should use an explicit antd css var scope');

  syncThemeRuntimeSnapshot('light', false);
  const lightTheme = buildAntdThemeConfig();
  const lightAlgorithm = Array.isArray(lightTheme?.algorithm) ? lightTheme.algorithm[0] : lightTheme?.algorithm;
  assert.equal(lightAlgorithm, antdTheme.defaultAlgorithm, 'light theme should use the default algorithm');
  assert.equal(lightTheme?.token?.colorPrimary, '#1677ff', 'light theme should keep the primary seed token');
  assert.equal(lightTheme?.components, undefined, 'light theme should not keep legacy component color overrides');
  assert.deepEqual(lightTheme?.cssVar, { key: 'lumira-light' }, 'light theme should use an explicit antd css var scope');

  syncThemeRuntimeSnapshot('compact', false);
  const compactTheme = buildAntdThemeConfig();
  const compactAlgorithms = Array.isArray(compactTheme?.algorithm) ? compactTheme.algorithm : [compactTheme?.algorithm];
  assert.deepEqual(compactAlgorithms, [antdTheme.defaultAlgorithm, antdTheme.compactAlgorithm], 'compact theme should compose default and compact algorithms');
  assert.equal(compactTheme?.components, undefined, 'compact theme should not keep legacy component color overrides');
  assert.deepEqual(compactTheme?.cssVar, { key: 'lumira-compact' }, 'compact theme should use an explicit antd css var scope');

  console.log('antd-static-theme-smoke: ok');
};

run();
