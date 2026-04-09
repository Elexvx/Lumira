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
    const response = await fetch('/api/p/2fa' + path, {
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
      throw new Error(payload?.userMessage || payload?.message || '2FA 接口调用失败');
    }
    return payload?.data ?? payload;
  };

  const App = function () {
    const [profile, setProfile] = React.useState(null);
    const [setupInfo, setSetupInfo] = React.useState(null);
    const [challengeId, setChallengeId] = React.useState('');
    const [verificationCode, setVerificationCode] = React.useState('');
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState('');
    const [success, setSuccess] = React.useState('');

    const loadProfile = React.useCallback(async function () {
      try {
        setLoading(true);
        const nextProfile = await request('/profile');
        setProfile(nextProfile);
      } catch (loadError) {
        setError(loadError.message || '加载 2FA 状态失败');
      } finally {
        setLoading(false);
      }
    }, []);

    React.useEffect(function () {
      void loadProfile();
    }, [loadProfile]);

    const bind = async function () {
      try {
        setLoading(true);
        setError('');
        setSuccess('');
        const result = await request('/bind', { method: 'POST' });
        setSetupInfo(result);
        setChallengeId(result.challengeId || '');
        setProfile((prev) => (prev ? { ...prev, enabled: true, bound: true, maskedContact: result.maskedContact || prev.maskedContact } : prev));
        setSuccess('绑定信息已生成，请使用扫码工具完成配置');
      } catch (bindError) {
        setError(bindError.message || '绑定失败');
      } finally {
        setLoading(false);
      }
    };

    const createChallenge = async function () {
      try {
        setLoading(true);
        setError('');
        setSuccess('');
        const result = await request('/challenge', { method: 'POST' });
        setChallengeId(result.challengeId || '');
        setSuccess('登录挑战已创建，请输入验证码');
      } catch (challengeError) {
        setError(challengeError.message || '创建挑战失败');
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
        setSetupInfo(null);
        await loadProfile();
        setSuccess('已解绑 2FA');
      } catch (unbindError) {
        setError(unbindError.message || '解绑失败');
      } finally {
        setLoading(false);
      }
    };

    return React.createElement(
      'div',
      {
        style: {
          display: 'grid',
          gap: '16px',
          maxWidth: '960px',
        },
      },
      React.createElement('h2', { style: { margin: 0 } }, '2FA 验证'),
      error ? React.createElement('div', { style: { color: '#d4380d' } }, error) : null,
      success ? React.createElement('div', { style: { color: '#389e0d' } }, success) : null,
      React.createElement(
        'div',
        {
          style: {
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: '16px',
          },
        },
        React.createElement(
          'section',
          {
            style: {
              border: '1px solid #f0f0f0',
              borderRadius: '12px',
              padding: '16px',
              background: '#fff',
              display: 'grid',
              gap: '12px',
            },
          },
          React.createElement('h3', { style: { margin: 0 } }, '当前状态'),
          React.createElement('div', null, '启用状态：', profile ? String(profile.enabled) : '-'),
          React.createElement('div', null, '绑定状态：', profile ? String(profile.bound) : '-'),
          React.createElement('div', null, '联系人：', profile?.maskedContact || '-'),
          React.createElement('div', null, '提示：', profile?.statusMessage || '请先绑定后使用'),
          React.createElement(
            'div',
            { style: { display: 'flex', gap: '8px', flexWrap: 'wrap' } },
            React.createElement(
              'button',
              {
                onClick: bind,
                disabled: loading,
                style: buttonStyle('#1677ff'),
              },
              '生成绑定信息'
            ),
            React.createElement(
              'button',
              {
                onClick: createChallenge,
                disabled: loading,
                style: buttonStyle('#13c2c2'),
              },
              '刷新登录挑战'
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
        ),
        React.createElement(
          'section',
          {
            style: {
              border: '1px solid #f0f0f0',
              borderRadius: '12px',
              padding: '16px',
              background: '#fff',
              display: 'grid',
              gap: '12px',
            },
          },
          React.createElement('h3', { style: { margin: 0 } }, '绑定信息 / 验证'),
          React.createElement('div', null, 'Challenge ID'),
          React.createElement('input', {
            value: challengeId,
            onChange: function (event) {
              setChallengeId(event.target.value);
            },
            placeholder: 'challenge id',
            style: inputStyle,
          }),
          React.createElement('div', null, '验证码'),
          React.createElement('input', {
            value: verificationCode,
            onChange: function (event) {
              setVerificationCode(event.target.value);
            },
            placeholder: '6 位验证码',
            style: inputStyle,
          }),
          React.createElement(
            'button',
            {
              onClick: verify,
              disabled: loading,
              style: buttonStyle('#722ed1'),
            },
            '验证'
          ),
          setupInfo
            ? React.createElement(
                'div',
                {
                  style: {
                    border: '1px dashed #d9d9d9',
                    borderRadius: '8px',
                    padding: '12px',
                    display: 'grid',
                    gap: '8px',
                  },
                },
                React.createElement('div', null, '扫码 URI：'),
                React.createElement('pre', { style: preStyle }, setupInfo.setupUri || '-'),
                React.createElement('div', null, '密钥：'),
                React.createElement('pre', { style: preStyle }, setupInfo.setupSecret || '-'),
                React.createElement('div', null, '恢复码：'),
                React.createElement('pre', { style: preStyle }, Array.isArray(setupInfo.recoveryCodes) ? setupInfo.recoveryCodes.join('\n') : '-')
              )
            : null
        )
      ),
      loading ? React.createElement('div', null, '处理中...') : null
    );
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

  const inputStyle = {
    border: '1px solid #d9d9d9',
    borderRadius: '8px',
    height: '36px',
    padding: '0 12px',
    outline: 'none',
  };

  const preStyle = {
    margin: 0,
    padding: '10px',
    background: '#fafafa',
    borderRadius: '8px',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  };

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
  window.__SAAS_PLUGIN_BUNDLES__['2fa@1.0.0'] = {
    mount: mount,
    unmount: unmount,
    getMenus: function () {
      return [];
    },
    getRoutes: function () {
      return ['/plugins/2fa'];
    },
    getPermissions: function () {
      return ['plugin:2fa:view', 'plugin:2fa:manage'];
    },
  };
})();
