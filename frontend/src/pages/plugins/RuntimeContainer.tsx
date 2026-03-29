import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Card, Spin } from 'antd';
import { history, useLocation, useParams } from 'umi';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { notifyPluginLoadError } from '@/plugins/loader';
import { PluginErrorBoundary } from '@/plugins/errorBoundary';
import { mountPlugin, unmountPlugin } from '@/plugins/runtime';

const RuntimeContainer = () => {
  const params = useParams<{ pluginCode: string }>();
  const location = useLocation();
  const { initialState } = useInitialStateModel();
  const containerRef = useRef<HTMLDivElement>(null);
  const [version, setVersion] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const plugin = useMemo(
    () => initialState?.availablePlugins?.find((item) => item.pluginCode === params.pluginCode),
    [initialState?.availablePlugins, params.pluginCode],
  );

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    if (!plugin) {
      setLoading(false);
      setError('当前租户未启用该插件');
      return;
    }
    let destroyed = false;
    setLoading(true);
    setError(undefined);
    mountPlugin(plugin.pluginCode, container, {
      pluginCode: plugin.pluginCode,
      version: plugin.version,
      routePath: location.pathname,
      currentUser: initialState?.currentUser,
      currentTenant: initialState?.currentTenant,
      requestId: crypto.randomUUID(),
    })
      .then((result) => {
        if (destroyed) {
          return;
        }
        setVersion(result.manifest.version);
        setLoading(false);
      })
      .catch((pluginError) => {
        if (destroyed) {
          return;
        }
        notifyPluginLoadError(pluginError);
        setError(pluginError instanceof Error ? pluginError.message : '插件加载失败');
        setLoading(false);
      });
    return () => {
      destroyed = true;
      if (version) {
        void unmountPlugin(plugin.pluginCode, version, container);
      }
    };
  }, [
    initialState?.currentTenant,
    initialState?.currentUser,
    location.pathname,
    plugin,
    version,
  ]);

  if (error) {
    return (
      <Card>
        <Alert type="error" showIcon message="插件不可用" description={error} />
      </Card>
    );
  }

  return (
    <PluginErrorBoundary>
      <Card bodyStyle={{ minHeight: 'calc(100vh - 160px)' }}>
        {loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 80 }}>
            <Spin />
          </div>
        ) : (
          <div ref={containerRef} />
        )}
      </Card>
    </PluginErrorBoundary>
  );
};

export default RuntimeContainer;
