(function () {
  const sharedDeps = window.SaaSSharedDeps || {};
  const React = sharedDeps.React;
  const ReactDOM = sharedDeps.ReactDOM;

  if (!React || !ReactDOM) {
    throw new Error('缺少 React 共享依赖');
  }

  const tokenState = JSON.parse(localStorage.getItem('saas:portal:auth_tokens') || '{}');
  const accessToken = tokenState.accessToken || '';

  const request = async (path, options) => {
    const response = await fetch('/api/p/sms' + path, {
      method: options && options.method ? options.method : 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: accessToken ? 'Bearer ' + accessToken : '',
        'X-Tenant-Id': window.__SAAS_CURRENT_TENANT_ID__ || '',
        'X-Request-Id': crypto.randomUUID(),
      },
      body: options && options.body ? JSON.stringify(options.body) : undefined,
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(payload?.userMessage || payload?.message || '短信接口调用失败');
    }
    return payload?.data ?? payload;
  };

  const App = function () {
    const [profile, setProfile] = React.useState(null);
    const [config, setConfig] = React.useState(null);
    const [challengeId, setChallengeId] = React.useState('');
    const [verificationCode, setVerificationCode] = React.useState('');
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState('');
    const [success, setSuccess] = React.useState('');

    const loadState = React.useCallback(async function () {
      try {
        setLoading(true);
        const [nextProfile, nextConfig] = await Promise.all([request('/profile'), request('/config')]);
        setProfile(nextProfile);
        setConfig(nextConfig);
      } catch (loadError) {
        setError(loadError.message || '加载短信状态失败');
      } finally {
        setLoading(false);
      }
    }, []);

    React.useEffect(function () {
      void loadState();
    }, [loadState]);

    const saveConfig = async function () {
      try {
        setLoading(true);
        setError('');
        setSuccess('');
        const result = await request('/config', {
          method: 'PUT',
          body: config || {},
        });
        setConfig(result);
        setSuccess('短信供应商配置已保存');
      } catch (saveError) {
        setError(saveError.message || '保存失败');
      } finally {
        setLoading(false);
      }
    };

    const bind = async function () {
      try {
        setLoading(true);
        setError('');
        setSuccess('');
        const result = await request('/bind', { method: 'POST' });
        setChallengeId(result.challengeId || '');
        setSuccess('短信验证码已发送，请输入验证码完成验证');
      } catch (bindError) {
        setError(bindError.message || '绑定失败');
      } finally {
        setLoading(false);
      }
    };

    const challenge = async function () {
      try {
        setLoading(true);
        setError('');
        setSuccess('');
        const result = await request('/challenge', { method: 'POST' });
        setChallengeId(result.challengeId || '');
        setSuccess('已重新发送短信验证码');
      } catch (challengeError) {
        setError(challengeError.message || '创建验证码失败');
      } finally {
        setLoading(false);
      }
    };

    const verify = async function () {
      if (!challengeId || !verificationCode) {
        setError('请先填写挑战 ID 和验证码');
        return;
      }
      try {
        setLoading(true);
        setError('');
        setSuccess('');
        await request('/verify', {
          method: 'POST',
          body: {
            challengeId: challengeId,
            verificationCode: verificationCode,
          },
        });
        setSuccess('验证成功');
        setVerificationCode('');
      } catch (verifyError) {
        setError(verifyError.message || '验证失败');
      } finally {
        setLoading(false);
      }
    };

    const unbind = async function () {
      try {
        setLoading(true);
        setError('');
        setSuccess('');
        await request('/unbind', { method: 'POST' });
        await loadState();
        setSuccess('已解绑短信验证码');
      } catch (unbindError) {
        setError(unbindError.message || '解绑失败');
      } finally {
        setLoading(false);
      }
    };

    const updateConfig = function (field, value) {
      setConfig((prev) => ({ ...(prev || {}), [field]: value }));
    };

    return React.createElement(
      'div',
      {
        style: {
          display: 'grid',
          gap: '16px',
          maxWidth: '1100px',
        },
      },
      React.createElement('h2', { style: { margin: 0 } }, '短信验证码'),
      error ? React.createElement('div', { style: { color: '#d4380d' } }, error) : null,
      success ? React.createElement('div', { style: { color: '#389e0d' } }, success) : null,
      React.createElement(
        'div',
        {
          style: {
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
            gap: '16px',
          },
        },
        React.createElement(
          'section',
          panelStyle(),
          React.createElement('h3', { style: headingStyle }, '供应商配置'),
          React.createElement('div', null, '供应商类型'),
          React.createElement(
            'select',
            {
              value: config?.providerType || 'MOCK',
              onChange: function (event) {
                updateConfig('providerType', event.target.value);
              },
              style: inputStyle,
            },
            React.createElement('option', { value: 'MOCK' }, 'MOCK'),
            React.createElement('option', { value: 'ALIYUN' }, '阿里云'),
            React.createElement('option', { value: 'TENCENT' }, '腾讯云')
          ),
          React.createElement('div', null, 'AccessKey ID / SecretId'),
          React.createElement('input', {
            value: config?.accessKeyId || '',
            onChange: function (event) {
              updateConfig('accessKeyId', event.target.value);
            },
            style: inputStyle,
          }),
          React.createElement('div', null, 'AccessKey Secret / SecretKey'),
          React.createElement('input', {
            value: config?.accessKeySecret || '',
            onChange: function (event) {
              updateConfig('accessKeySecret', event.target.value);
            },
            type: 'password',
            style: inputStyle,
          }),
          React.createElement('div', null, '签名'),
          React.createElement('input', {
            value: config?.signName || '',
            onChange: function (event) {
              updateConfig('signName', event.target.value);
            },
            style: inputStyle,
          }),
          React.createElement('div', null, '模板 ID / Code'),
          React.createElement('input', {
            value: config?.templateCode || '',
            onChange: function (event) {
              updateConfig('templateCode', event.target.value);
            },
            style: inputStyle,
          }),
          React.createElement('div', null, '短信 AppId（腾讯云需要）'),
          React.createElement('input', {
            value: config?.smsSdkAppId || '',
            onChange: function (event) {
              updateConfig('smsSdkAppId', event.target.value);
            },
            style: inputStyle,
          }),
          React.createElement('div', null, 'Region / Endpoint'),
          React.createElement('input', {
            value: config?.regionId || '',
            onChange: function (event) {
              updateConfig('regionId', event.target.value);
            },
            placeholder: '例如 ap-guangzhou / dysmsapi.aliyuncs.com',
            style: inputStyle,
          }),
          React.createElement('div', null, 'Endpoint'),
          React.createElement('input', {
            value: config?.endpoint || '',
            onChange: function (event) {
              updateConfig('endpoint', event.target.value);
            },
            placeholder: '可留空使用默认官方 endpoint',
            style: inputStyle,
          }),
          React.createElement(
            'div',
            { style: { display: 'flex', gap: '8px', flexWrap: 'wrap' } },
            React.createElement(
              'button',
              {
                onClick: saveConfig,
                disabled: loading,
                style: buttonStyle('#1677ff'),
              },
              '保存配置'
            ),
            React.createElement(
              'button',
              {
                onClick: loadState,
                disabled: loading,
                style: buttonStyle('#13c2c2'),
              },
              '刷新'
            )
          ),
          React.createElement(
            'div',
            {
              style: {
                border: '1px dashed #d9d9d9',
                borderRadius: '8px',
                padding: '10px',
                background: '#fafafa',
              },
            },
            '当前配置：',
            config?.configured ? '已配置' : '未配置'
          )
        ),
        React.createElement(
          'section',
          panelStyle(),
          React.createElement('h3', { style: headingStyle }, '验证状态'),
          React.createElement('div', null, '启用状态：', profile ? String(profile.enabled) : '-'),
          React.createElement('div', null, '绑定状态：', profile ? String(profile.bound) : '-'),
          React.createElement('div', null, '联系人：', profile?.maskedContact || '-'),
          React.createElement('div', null, '提示：', profile?.statusMessage || '请先绑定手机号'),
          React.createElement('div', null, 'Challenge ID'),
          React.createElement('input', {
            value: challengeId,
            onChange: function (event) {
              setChallengeId(event.target.value);
            },
            style: inputStyle,
          }),
          React.createElement('div', null, '验证码'),
          React.createElement('input', {
            value: verificationCode,
            onChange: function (event) {
              setVerificationCode(event.target.value);
            },
            style: inputStyle,
          }),
          React.createElement(
            'div',
            { style: { display: 'flex', gap: '8px', flexWrap: 'wrap' } },
            React.createElement(
              'button',
              {
                onClick: bind,
                disabled: loading,
                style: buttonStyle('#722ed1'),
              },
              '绑定并发送验证码'
            ),
            React.createElement(
              'button',
              {
                onClick: challenge,
                disabled: loading,
                style: buttonStyle('#13c2c2'),
              },
              '重新发送验证码'
            ),
            React.createElement(
              'button',
              {
                onClick: verify,
                disabled: loading,
                style: buttonStyle('#389e0d'),
              },
              '验证'
            ),
            React.createElement(
              'button',
              {
                onClick: unbind,
                disabled: loading,
                style: buttonStyle('#ff4d4f'),
              },
              '解绑'
            )
          )
        )
      ),
      loading ? React.createElement('div', null, '处理中...') : null
    );
  };

  const panelStyle = () => ({
    border: '1px solid #f0f0f0',
    borderRadius: '12px',
    padding: '16px',
    background: '#fff',
    display: 'grid',
    gap: '10px',
  });

  const headingStyle = {
    margin: 0,
  };

  const inputStyle = {
    border: '1px solid #d9d9d9',
    borderRadius: '8px',
    height: '36px',
    padding: '0 12px',
    outline: 'none',
  };

  const buttonStyle = (background) => ({
    border: 'none',
    borderRadius: '8px',
    padding: '0 14px',
    height: '36px',
    cursor: 'pointer',
    color: '#fff',
    background: background,
  });

  const roots = new Map();

  const mount = function (container, context) {
    window.__SAAS_CURRENT_TENANT_ID__ = context && context.currentTenant ? context.currentTenant.tenantId : '';
    const root = ReactDOM.createRoot(container);
    roots.set(container, root);
    root.render(React.createElement(App, null));
  };

  const unmount = function (container) {
    const root = roots.get(container);
    if (root) {
      root.unmount();
      roots.delete(container);
    }
  };

  window.__SAAS_PLUGIN_BUNDLES__ = window.__SAAS_PLUGIN_BUNDLES__ || {};
  window.__SAAS_PLUGIN_BUNDLES__['sms@1.0.0'] = {
    mount: mount,
    unmount: unmount,
    getMenus: function () {
      return [];
    },
    getRoutes: function () {
      return [];
    },
    getPermissions: function () {
      return ['plugin:sms:view', 'plugin:sms:manage'];
    },
  };
})();
