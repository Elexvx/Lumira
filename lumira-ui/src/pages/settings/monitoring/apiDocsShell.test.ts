import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { SWAGGER_UI_ASSET_PATHS, SWAGGER_UI_SHELL_HTML } from './apiDocsShell';

const SWAGGER_UI_BOOTSTRAP_SOURCE = readFileSync(
  new URL('../../../../public/swagger-ui-bootstrap.js', import.meta.url),
  'utf8',
);

describe('API docs shell', () => {
  it('loads Swagger UI only from versioned local application assets', () => {
    expect(SWAGGER_UI_SHELL_HTML).not.toMatch(/https?:\/\//i);
    expect(SWAGGER_UI_SHELL_HTML).toContain(`href="${SWAGGER_UI_ASSET_PATHS.stylesheet}"`);
    expect(SWAGGER_UI_ASSET_PATHS.bootstrap).toMatch(/^\/vendor\/swagger-ui\//);
    expect(SWAGGER_UI_SHELL_HTML).toContain(`src="${SWAGGER_UI_ASSET_PATHS.bundle}"`);
    expect(SWAGGER_UI_SHELL_HTML).toContain(`src="${SWAGGER_UI_ASSET_PATHS.standalonePreset}"`);
  });

  it('registers the bootstrap error handler before loading the renderer bundles', () => {
    const bootstrapIndex = SWAGGER_UI_SHELL_HTML.indexOf(SWAGGER_UI_ASSET_PATHS.bootstrap);
    const bundleIndex = SWAGGER_UI_SHELL_HTML.indexOf(SWAGGER_UI_ASSET_PATHS.bundle);
    const presetIndex = SWAGGER_UI_SHELL_HTML.indexOf(SWAGGER_UI_ASSET_PATHS.standalonePreset);

    expect(bootstrapIndex).toBeGreaterThan(0);
    expect(bundleIndex).toBeGreaterThan(bootstrapIndex);
    expect(presetIndex).toBeGreaterThan(bundleIndex);
  });

  it('turns renderer loading failures and timeouts into visible feedback', () => {
    expect(SWAGGER_UI_BOOTSTRAP_SOURCE).toContain('window.setTimeout');
    expect(SWAGGER_UI_BOOTSTRAP_SOURCE).toContain("window.addEventListener('error'");
    expect(SWAGGER_UI_BOOTSTRAP_SOURCE).toContain("window.addEventListener('unhandledrejection'");
    expect(SWAGGER_UI_BOOTSTRAP_SOURCE).toContain('接口文档组件加载超时');
  });
});
