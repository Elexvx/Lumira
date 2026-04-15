import { BuildOutlined, DeleteOutlined, FileSearchOutlined } from '@ant-design/icons';
import { Button, Card, Col, Empty, Row, Space, Switch, Tag, Typography } from 'antd';
import type { PluginDefinition, TenantPlugin } from '@/types/api';

interface PluginCardsGridProps {
  loading: boolean;
  definitions: PluginDefinition[];
  currentAvailableMap: Map<string, TenantPlugin>;
  getPreferredEnableVersion: (pluginCode: string) => { version: string } | undefined;
  mutationLoading: boolean;
  onToggleEnable: (pluginCode: string, enabled: boolean, versionLabel?: string) => void;
  onOpenDetails: (plugin: PluginDefinition) => void;
  onOpenVersions: (plugin: PluginDefinition) => void;
  onOpenLogs: (plugin: PluginDefinition) => void;
  onUninstall: (plugin: PluginDefinition) => void;
}

export const PluginCardsGrid = ({
  loading,
  definitions,
  currentAvailableMap,
  getPreferredEnableVersion,
  mutationLoading,
  onToggleEnable,
  onOpenDetails,
  onOpenVersions,
  onOpenLogs,
  onUninstall,
}: PluginCardsGridProps) => {
  if (!loading && !definitions.length) {
    return (
      <div style={{ minHeight: 240, display: 'grid', placeItems: 'center' }}>
        <Empty description="暂无插件定义" />
      </div>
    );
  }

  return (
    <Row gutter={[16, 16]}>
      {definitions.map((plugin) => {
        const preferredEnableVersion = getPreferredEnableVersion(plugin.pluginCode);
        const enabledPlugin = currentAvailableMap.get(plugin.pluginCode);
        const enabled = Boolean(enabledPlugin);
        const versionLabel = enabledPlugin?.version || preferredEnableVersion?.version;

        return (
          <Col key={plugin.pluginCode} xs={24} lg={12} xxl={8}>
            <Card
              loading={loading}
              title={
                <Space wrap>
                  <BuildOutlined />
                  <span>{plugin.pluginName}</span>
                  <Tag color={enabled ? 'green' : 'default'}>{enabled ? '已启用' : '未启用'}</Tag>
                </Space>
              }
              extra={
                <Switch
                  checked={enabled}
                  disabled={mutationLoading || !versionLabel}
                  onChange={(checked) => onToggleEnable(plugin.pluginCode, checked, versionLabel)}
                />
              }
            >
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Typography.Paragraph style={{ marginBottom: 0 }}>{plugin.description || '暂无插件描述'}</Typography.Paragraph>
                <Space wrap>
                  <Button onClick={() => onOpenDetails(plugin)}>详情</Button>
                  <Button onClick={() => onOpenVersions(plugin)}>版本</Button>
                  <Button onClick={() => onOpenLogs(plugin)} icon={<FileSearchOutlined />}>
                    日志
                  </Button>
                  <Button danger icon={<DeleteOutlined />} onClick={() => onUninstall(plugin)}>
                    卸载
                  </Button>
                </Space>
              </Space>
            </Card>
          </Col>
        );
      })}
    </Row>
  );
};
