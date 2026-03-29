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
    const response = await fetch('/api/p/announcement' + path, {
      method: options && options.method ? options.method : 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: accessToken ? 'Bearer ' + accessToken : '',
        'X-Tenant-Id': window.__SAAS_CURRENT_TENANT_ID__ || '',
        'X-Request-Id': crypto.randomUUID(),
      },
      body: options && options.body ? JSON.stringify(options.body) : undefined,
    });
    if (!response.ok) {
      throw new Error('公告接口调用失败: ' + response.status);
    }
    return response.json();
  };

  const AnnouncementApp = function (props) {
    const [loading, setLoading] = React.useState(true);
    const [title, setTitle] = React.useState('');
    const [content, setContent] = React.useState('');
    const [items, setItems] = React.useState([]);
    const [error, setError] = React.useState('');

    const reload = React.useCallback(async function () {
      try {
        setLoading(true);
        const nextItems = await request('/list');
        setItems(Array.isArray(nextItems) ? nextItems : []);
      } catch (loadError) {
        setError(loadError.message || '公告加载失败');
      } finally {
        setLoading(false);
      }
    }, []);

    React.useEffect(function () {
      reload();
    }, [reload]);

    const submit = async function () {
      if (!title || !content) {
        setError('请填写公告标题和内容');
        return;
      }
      try {
        await request('/create', {
          method: 'POST',
          body: {
            title: title,
            content: content,
          },
        });
        setTitle('');
        setContent('');
        setError('');
        await reload();
      } catch (submitError) {
        setError(submitError.message || '公告创建失败');
      }
    };

    return React.createElement(
      'div',
      {
        style: {
          display: 'flex',
          flexDirection: 'column',
          gap: '16px',
        },
      },
      React.createElement('h2', null, '公告管理插件'),
      error ? React.createElement('div', { style: { color: '#ff4d4f' } }, error) : null,
      React.createElement(
        'div',
        {
          style: {
            display: 'grid',
            gap: '8px',
            maxWidth: '520px',
          },
        },
        React.createElement('input', {
          placeholder: '公告标题',
          value: title,
          onChange: function (event) {
            setTitle(event.target.value);
          },
          style: {
            height: '36px',
            border: '1px solid #d9d9d9',
            borderRadius: '6px',
            padding: '0 12px',
          },
        }),
        React.createElement('textarea', {
          placeholder: '公告内容',
          value: content,
          onChange: function (event) {
            setContent(event.target.value);
          },
          rows: 4,
          style: {
            border: '1px solid #d9d9d9',
            borderRadius: '6px',
            padding: '12px',
          },
        }),
        React.createElement(
          'button',
          {
            onClick: submit,
            style: {
              width: '120px',
              height: '36px',
              border: 'none',
              borderRadius: '6px',
              background: '#1677ff',
              color: '#ffffff',
              cursor: 'pointer',
            },
          },
          '新增公告'
        )
      ),
      loading
        ? React.createElement('div', null, '公告加载中...')
        : React.createElement(
            'div',
            {
              style: {
                display: 'grid',
                gap: '12px',
              },
            },
            items.map(function (item) {
              return React.createElement(
                'div',
                {
                  key: item.id,
                  style: {
                    border: '1px solid #f0f0f0',
                    borderRadius: '8px',
                    padding: '16px',
                    background: '#ffffff',
                  },
                },
                React.createElement('div', { style: { fontWeight: 600, marginBottom: '8px' } }, item.title),
                React.createElement('div', { style: { color: '#595959', marginBottom: '8px' } }, item.content),
                React.createElement('div', { style: { color: '#8c8c8c', fontSize: '12px' } }, item.createdAt || '')
              );
            })
          )
    );
  };

  const roots = new Map();

  const mount = function (container, context) {
    window.__SAAS_CURRENT_TENANT_ID__ = context && context.currentTenant ? context.currentTenant.tenantId : '';
    const root = ReactDOM.createRoot(container);
    roots.set(container, root);
    root.render(React.createElement(AnnouncementApp, null));
  };

  const unmount = function (container) {
    const root = roots.get(container);
    if (root) {
      root.unmount();
      roots.delete(container);
    }
  };

  window.__SAAS_PLUGIN_BUNDLES__ = window.__SAAS_PLUGIN_BUNDLES__ || {};
  window.__SAAS_PLUGIN_BUNDLES__['announcement@1.0.0'] = {
    mount: mount,
    unmount: unmount,
    getMenus: function () {
      return [];
    },
    getRoutes: function () {
      return ['/plugins/announcement'];
    },
    getPermissions: function () {
      return ['plugin:announcement:view', 'plugin:announcement:write'];
    },
  };
})();
