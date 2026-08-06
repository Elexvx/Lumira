import { formatMessage } from '@/i18n/formatMessage';
import { BuildOutlined, DeleteOutlined } from '@ant-design/icons';
import { Button, Card, Col, Empty, Row, Space, Switch, Tag, Typography } from 'antd';
import type { PluginAvailability, PluginDefinition } from '@/types/api';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

const pluginMessage = (id: string, defaultMessage: string) => formatMessage({ id, defaultMessage });

export const PluginCardsGrid = ({
  isMobile,
  loading,
  definitions,
  currentAvailableMap,
  getPreferredEnableVersion,
  mutationLoading,
  canEnable,
  canDisable,
  onToggleEnable,
  onOpenDetails,
  onUninstall,
}: {
  isMobile: boolean;
  loading: boolean;
  definitions: PluginDefinition[];
  currentAvailableMap: Map<string, PluginAvailability>;
  getPreferredEnableVersion: (pluginCode: string) => { version: string } | undefined;
  mutationLoading: boolean;
  canEnable: boolean;
  canDisable: boolean;
  onToggleEnable: (pluginCode: string, enabled: boolean, versionLabel?: string) => void;
  onOpenDetails: (plugin: PluginDefinition) => void;
  onUninstall: (plugin: PluginDefinition) => void;
}) => {
  const rowGutter = resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile);
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);

  if (!loading && !definitions.length) {
    return (
      <div style={{ minHeight: 'var(--saas-spacing-240)', display: 'grid', placeItems: 'center' }}>
        <Empty description={pluginMessage('page.plugins.empty', 'No plugin definitions')} />
      </div>
    );
  }

  return (
    <Row gutter={rowGutter}>
      {definitions.map((plugin) => {
        const preferredEnableVersion = getPreferredEnableVersion(plugin.pluginCode);
        const enabledPlugin = currentAvailableMap.get(plugin.pluginCode);
        const enabled = Boolean(enabledPlugin);
        const versionLabel = enabledPlugin?.version || preferredEnableVersion?.version;
        const canToggle = enabled ? canDisable : canEnable;

        return (
          <Col key={plugin.pluginCode} xs={24} lg={12} xxl={8}>
            <Card
              loading={loading}
              style={{ height: '100%' }}
              bodyStyle={{ height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}
              title={
                <Space wrap>
                  <BuildOutlined />
                  <span>{plugin.pluginName}</span>
                  <Tag color={enabled ? 'green' : 'default'}>
                    {enabled
                      ? pluginMessage('page.plugins.enabled.true', 'Enabled')
                      : pluginMessage('page.plugins.enabled.false', 'Disabled')}
                  </Tag>
                </Space>
              }
              extra={
                <Switch
                  checked={enabled}
                  disabled={mutationLoading || !versionLabel || !canToggle}
                  onChange={(checked) => onToggleEnable(plugin.pluginCode, checked, versionLabel)}
                />
              }
            >
              <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                <Typography.Paragraph style={{ marginBottom: 0 }}>
                  {plugin.description || pluginMessage('page.plugins.noDescription', 'No plugin description')}
                </Typography.Paragraph>
                <Space wrap>
                  <Button onClick={() => onOpenDetails(plugin)}>{pluginMessage('page.plugins.details', 'Details')}</Button>
                  <Button danger disabled={!canDisable || plugin.builtinFlag === 1} icon={<DeleteOutlined />} onClick={() => onUninstall(plugin)}>
                    {pluginMessage('page.plugins.uninstallAction', 'Uninstall')}
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
