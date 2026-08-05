(function () {
  'use strict';

  var statusElement = document.getElementById('swagger-status');

  function showError(message) {
    if (!statusElement) {
      return;
    }
    statusElement.className = 'is-error';
    statusElement.textContent = message;
  }

  window.addEventListener('message', function (event) {
    if (event.source !== window.parent || !event.data || event.data.type !== 'lumira:swagger-spec') {
      return;
    }

    if (typeof window.SwaggerUIBundle !== 'function' || typeof window.SwaggerUIStandalonePreset === 'undefined') {
      showError('接口文档组件加载失败，请刷新页面后重试。');
      return;
    }

    var padding = Number(event.data.schemeContainerVerticalPadding);
    if (Number.isFinite(padding) && padding >= 0) {
      document.documentElement.style.setProperty('--swagger-scheme-padding', padding + 'px');
    }

    window.ui = window.SwaggerUIBundle({
      spec: event.data.spec,
      dom_id: '#swagger-ui',
      deepLinking: true,
      displayRequestDuration: true,
      supportedSubmitMethods: [],
      presets: [window.SwaggerUIBundle.presets.apis, window.SwaggerUIStandalonePreset],
      layout: 'StandaloneLayout',
    });
  });

  window.addEventListener('error', function () {
    showError('接口文档组件加载失败，请刷新页面后重试。');
  });
})();
