import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useParams } from '@umijs/max';
import { Alert, Card, Spin } from 'antd';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { notifyPluginLoadError } from '@/plugins/loader';
import { PluginErrorBoundary } from '@/plugins/errorBoundary';
import { mountPlugin, unmountPlugin } from '@/plugins/runtime';

type RuntimeErrorState = {
  type: 'info' | 'warning' | 'error';
  message: string;
};

const RuntimeContainer = () => {
  const params = useParams<{ pluginCode: string }>();
  const location = useLocation();
  const { initialState } = useInitialStateModel();
  const containerRef = useRef<HTMLDivElement>(null);
  const mountedRef = useRef<{ pluginCode: string; version: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<RuntimeErrorState>();

  const plugin = useMemo(
    () => initialState?.availablePlugins?.find((item) => item.pluginCode === params.pluginCode),
    [initialState?.availablePlugins, params.pluginCode],
  );

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    let active = true;
    const previousMounted = mountedRef.current;
    mountedRef.current = null;
    if (previousMounted) {
      void unmountPlugin(previousMounted.pluginCode, previousMounted.version, container);
    }
    if (!plugin) {
      setLoading(false);
      setError({
        type: 'warning',
        message: '当前未启用该插件',
      });
      return;
    }
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
        if (!active) {
          return;
        }
        mountedRef.current = {
          pluginCode: plugin.pluginCode,
          version: result.manifest.version,
        };
        setLoading(false);
      })
      .catch((pluginError) => {
        if (!active) {
          return;
        }
        const feedback = notifyPluginLoadError(pluginError);
        setError(feedback);
        setLoading(false);
      });
    return () => {
      active = false;
      const currentMounted = mountedRef.current;
      if (currentMounted) {
        void unmountPlugin(currentMounted.pluginCode, currentMounted.version, container);
        mountedRef.current = null;
      }
    };
  }, [initialState?.currentTenant, initialState?.currentUser, location.pathname, plugin]);

  if (error) {
    return (
      <Card>
        <Alert type={error.type} showIcon message="插件不可用" description={error.message} />
      </Card>
    );
  }

  return (
    <PluginErrorBoundary>
      <Card bodyStyle={{ position: 'relative', minHeight: 'calc(100vh - 160px)' }}>
        <div ref={containerRef} style={{ minHeight: 'calc(100vh - 220px)' }} />
        {loading ? (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              pointerEvents: 'none',
            }}
          >
            <Spin />
          </div>
        ) : null}
      </Card>
    </PluginErrorBoundary>
  );
};

export default RuntimeContainer;
