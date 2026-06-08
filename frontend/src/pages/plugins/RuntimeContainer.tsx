import React from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useIntl, useLocation, useParams } from '@umijs/max';
import { Alert, Card, Spin } from 'antd';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { notifyPluginLoadError } from '@/plugins/loader';
import { mountPlugin, unmountPlugin } from '@/plugins/runtime';

type RuntimeErrorState = {
  type: 'info' | 'warning' | 'error';
  message: string;
};

interface PluginErrorBoundaryProps {
  children: React.ReactNode;
  intl: ReturnType<typeof useIntl>;
}

interface PluginErrorBoundaryState {
  error?: Error;
}

class PluginErrorBoundary extends React.Component<PluginErrorBoundaryProps, PluginErrorBoundaryState> {
  state: PluginErrorBoundaryState = {};

  static getDerivedStateFromError(error: Error): PluginErrorBoundaryState {
    return { error };
  }

  render() {
    if (this.state.error) {
      return <Alert type="error" showIcon message={this.props.intl.formatMessage({ id: 'common.pluginRenderFailed', defaultMessage: '插件渲染失败' })} description={this.state.error.message} />;
    }
    return this.props.children;
  }
}

const RuntimeContainer = () => {
  const params = useParams<{ pluginCode: string }>();
  const intl = useIntl();
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
        message: intl.formatMessage({ id: 'common.pluginNotEnabled', defaultMessage: '当前未启用该插件' }),
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
  }, [initialState?.currentUser, intl, location.pathname, plugin]);

  if (error) {
    return (
      <Card>
        <Alert type={error.type} showIcon message={intl.formatMessage({ id: 'common.pluginUnavailable', defaultMessage: '插件不可用' })} description={error.message} />
      </Card>
    );
  }

  return (
      <PluginErrorBoundary intl={intl}>
      <Card style={{ height: '100%', display: 'flex', flexDirection: 'column' }} bodyStyle={{ position: 'relative', display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
        <div ref={containerRef} style={{ flex: 1, minHeight: 0 }} />
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
