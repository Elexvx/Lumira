const SWAGGER_UI_VENDOR_ROOT = '/vendor/swagger-ui';

export const SWAGGER_UI_ASSET_PATHS = {
  stylesheet: `${SWAGGER_UI_VENDOR_ROOT}/swagger-ui.css`,
  bundle: `${SWAGGER_UI_VENDOR_ROOT}/swagger-ui-bundle.js`,
  standalonePreset: `${SWAGGER_UI_VENDOR_ROOT}/swagger-ui-standalone-preset.js`,
  bootstrap: `${SWAGGER_UI_VENDOR_ROOT}/lumira-bootstrap.js?v=4`,
} as const;

export const SWAGGER_UI_SHELL_HTML = `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>接口文档</title>
    <link rel="stylesheet" href="${SWAGGER_UI_ASSET_PATHS.stylesheet}" />
    <style>
      html, body, #swagger-ui { min-height: 100%; margin: 0; background: #fff; }
      #swagger-status { box-sizing: border-box; padding: 48px 24px; color: #667085; font: 14px/1.6 system-ui, sans-serif; text-align: center; }
      #swagger-status.is-error { color: #d92d20; }
      .swagger-ui .topbar { display: none; }
      .swagger-ui .scheme-container { padding: var(--swagger-scheme-padding, 16px) 0; box-shadow: none; }
    </style>
  </head>
  <body>
    <div id="swagger-ui"><div id="swagger-status">正在加载接口文档…</div></div>
    <script src="${SWAGGER_UI_ASSET_PATHS.bootstrap}"></script>
    <script src="${SWAGGER_UI_ASSET_PATHS.bundle}"></script>
    <script src="${SWAGGER_UI_ASSET_PATHS.standalonePreset}"></script>
  </body>
</html>`;
