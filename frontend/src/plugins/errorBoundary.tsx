import React from 'react';
import { Alert } from 'antd';

interface PluginErrorBoundaryProps {
  children: React.ReactNode;
}

interface PluginErrorBoundaryState {
  error?: Error;
}

export class PluginErrorBoundary extends React.Component<PluginErrorBoundaryProps, PluginErrorBoundaryState> {
  state: PluginErrorBoundaryState = {};

  static getDerivedStateFromError(error: Error): PluginErrorBoundaryState {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <Alert
          type="error"
          showIcon
          message="插件渲染失败"
          description={this.state.error.message}
        />
      );
    }
    return this.props.children;
  }
}
