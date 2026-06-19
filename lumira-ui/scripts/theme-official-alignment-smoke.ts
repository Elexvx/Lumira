import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { theme as antdTheme } from 'antd';
import { buildAntdThemeConfig } from '../src/theme/antdTheme';

const projectRoot = fileURLToPath(new URL('..', import.meta.url));
const sourceRoot = join(projectRoot, 'src');
const sourceFiles = [
  'src/theme/antdTheme.tsx',
  'src/theme/ThemePreferenceProvider.tsx',
  'src/theme/apply.ts',
  'src/theme/settings.ts',
  'src/global.css',
  'src/pages/ai/Assistant.css',
  'src/pages/user/Login.css',
  'src/pages/settings/menus/components/MenuIconPicker.css',
  'src/pages/settings/personalization/components/AgreementMarkdownEditor.css',
  'src/pages/system/roles.css',
  'src/pages/system/users.css',
];
const radiusScanExtensions = new Set(['.css', '.ts', '.tsx']);
const allowedRadiusValuePatterns = [
  /^var\(--ant-border-radius(?:-[a-z]+)?\)$/,
  /^var\(--ant-border-radius(?:-[a-z]+)?,\s*\d+px\)$/,
  /^var\(--saas-(?:card-radius|border-radius-(?:base|sm|lg)|profile-card-radius|ai-[a-z-]+-radius)\)$/,
  /^calc\(var\(--ant-border-radius\) \* \d+\)$/,
  /^inherit$/,
  /^0(?: !important)?$/,
  /^50%$/,
];

const legacyThemePatterns = [
  /var\(--saas-(page-bg|text|surface|border(?!-radius)|input|icon|code|action)/,
  /var\(--ant-[^)]*,\s*(var\(--saas-|#[0-9a-fA-F]{3,8}|rgba?\()/,
  /rgba\(22,\s*119,\s*255/,
  /#52c41a/i,
  /#fa8c16/i,
  /#f0f0f0/i,
  /components:\s*buildComponentTokens/,
  /buildComponentTokens/,
];

const themeReloadPatterns = [
  /location\.reload/,
  /window\.location\.reload/,
  /history\.go\(0\)/,
  /<ConfigProvider[^>]*\skey=/,
];

const readProjectFile = (relativePath: string) =>
  readFileSync(join(projectRoot, relativePath), 'utf8');

const collectSourceFiles = (directory: string): string[] =>
  readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const absolutePath = join(directory, entry.name);
    if (entry.isDirectory()) {
      return collectSourceFiles(absolutePath);
    }

    const extension = entry.name.slice(entry.name.lastIndexOf('.'));
    if (!radiusScanExtensions.has(extension)) {
      return [];
    }

    return [relative(projectRoot, absolutePath).replaceAll('\\', '/')];
  });

const assertNoLegacyThemePatterns = () => {
  for (const relativePath of sourceFiles) {
    const content = readProjectFile(relativePath);
    for (const pattern of legacyThemePatterns) {
      assert.equal(pattern.test(content), false, `${relativePath} should not contain legacy theme pattern ${pattern}`);
    }
  }
};

const assertThemeSwitchDoesNotForcePageReload = () => {
  for (const relativePath of ['src/theme/ThemePreferenceProvider.tsx', 'src/theme/apply.ts', 'src/theme/settings.ts']) {
    const content = readProjectFile(relativePath);
    for (const pattern of themeReloadPatterns) {
      assert.equal(pattern.test(content), false, `${relativePath} should not force a full page reload or remount for theme switching`);
    }
  }
};

const assertRadiusTokensFollowOfficialTheme = () => {
  const radiusDeclarationPattern = /\bborder-radius\s*:\s*([^;]+);/g;
  const radiusStylePattern = /\bborderRadius\s*:\s*([^,\n}]+)/g;
  const tokenDeclarationPattern = /--saas-(?:card-radius|border-radius-(?:base|sm|lg))\s*:\s*([^;]+);/g;

  for (const relativePath of collectSourceFiles(sourceRoot)) {
    const content = readProjectFile(relativePath);
    const checks = [
      { pattern: radiusDeclarationPattern, label: 'border-radius' },
      { pattern: radiusStylePattern, label: 'borderRadius' },
      { pattern: tokenDeclarationPattern, label: 'radius token' },
    ];

    for (const { pattern, label } of checks) {
      for (const match of content.matchAll(pattern)) {
        if (relativePath === 'src/theme/antdTheme.tsx' && label === 'borderRadius') {
          continue;
        }

        const value = match[1].trim().replace(/^['"]|['"]$/g, '');
        const isAllowed = allowedRadiusValuePatterns.some((allowedPattern) => allowedPattern.test(value));
        assert.equal(
          isAllowed,
          true,
          `${relativePath} ${label} should use Ant Design radius tokens instead of ${value}`,
        );
      }
    }
  }
};

const assertOfficialTokenDerivation = () => {
  const lightConfig = buildAntdThemeConfig({ themePreference: 'light', resolvedColorMode: 'light' });
  const darkConfig = buildAntdThemeConfig({ themePreference: 'dark', resolvedColorMode: 'dark' });
  const compactConfig = buildAntdThemeConfig({ themePreference: 'compact', resolvedColorMode: 'light' });

  assert.equal(lightConfig?.components, undefined, 'light theme should not retain legacy component token overrides');
  assert.equal(darkConfig?.components, undefined, 'dark theme should not retain legacy component token overrides');
  assert.equal(compactConfig?.components, undefined, 'compact theme should not retain legacy component token overrides');
  assert.deepEqual(lightConfig?.cssVar, { key: 'lumira-light' }, 'light theme should use an explicit official cssVar key');
  assert.deepEqual(darkConfig?.cssVar, { key: 'lumira-dark' }, 'dark theme should use an explicit official cssVar key');
  assert.deepEqual(compactConfig?.cssVar, { key: 'lumira-compact' }, 'compact theme should use an explicit official cssVar key');

  const lightToken = antdTheme.getDesignToken(lightConfig);
  const darkToken = antdTheme.getDesignToken(darkConfig);
  const compactToken = antdTheme.getDesignToken(compactConfig);

  assert.notEqual(lightToken.colorBgContainer, darkToken.colorBgContainer, 'container background should be algorithm-derived per color mode');
  assert.notEqual(lightToken.colorText, darkToken.colorText, 'text color should be algorithm-derived per color mode');
  assert.equal(lightConfig?.token?.colorPrimary, darkConfig?.token?.colorPrimary, 'primary seed config should remain stable across modes');
  assert.notEqual(lightToken.colorPrimary, darkToken.colorPrimary, 'primary design token should be algorithm-derived per color mode');
  assert.notEqual(lightToken.controlHeight, compactToken.controlHeight, 'compact algorithm should change control sizing');
};

const run = () => {
  assertOfficialTokenDerivation();
  assertNoLegacyThemePatterns();
  assertThemeSwitchDoesNotForcePageReload();
  assertRadiusTokensFollowOfficialTheme();
  console.log('theme-official-alignment-smoke: ok');
};

run();
