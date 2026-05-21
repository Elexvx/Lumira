import { CloudUploadOutlined, SyncOutlined } from '@ant-design/icons';
import { formatMessage } from '@umijs/max';
import { Button, Card, Descriptions, Input, Modal, Radio, Space, Tag, Typography, Upload, message, theme } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { ManagementDrawer, ManagementPage, ManagementPageBody, ManagementTable } from '@/features/management';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { ApiRequestError } from '@/services/common/request';
import { buildVersionColumns, logColumns } from '@/pages/settings/plugins/columns';
import { PluginCardsGrid } from '@/pages/settings/plugins/components/PluginCardsGrid';
import { buildAvailablePluginMap, filterPluginDefinitions, getPreferredEnableVersion } from '@/pages/settings/plugins/utils';
import { pluginService } from '@/services/plugin';
import type { PluginDefinition, PluginRuntimeLog, PluginVersion, TenantPlugin } from '@/types/api';

const PLATFORM_TENANT_ID = 1001;

const PluginsPage = () => {
  const { token } = theme.useToken();
  const { initialState, setInitialState } = useInitialStateModel();
  const { responsive } = usePagePermissionActions();
  const [definitions, setDefinitions] = useState<PluginDefinition[]>([]);
  const [availablePlugins, setAvailablePlugins] = useState<TenantPlugin[]>([]);
  const [versionMap, setVersionMap] = useState<Record<string, PluginVersion[]>>({});
  const [loading, setLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedPlugin, setSelectedPlugin] = useState<PluginDefinition | null>(null);
  const [versionDrawerOpen, setVersionDrawerOpen] = useState(false);
  const [logDrawerOpen, setLogDrawerOpen] = useState(false);
  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [runtimeLogs, setRuntimeLogs] = useState<PluginRuntimeLog[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [mutationLoading, setMutationLoading] = useState(false);
  const [uninstallDialogOpen, setUninstallDialogOpen] = useState(false);
  const [uninstallTarget, setUninstallTarget] = useState<PluginDefinition | null>(null);
  const [removePluginData, setRemovePluginData] = useState(false);
  const detailDescriptionsProps = useDetailDescriptionsProps({ column: responsive.isMobile ? 1 : 2 });

  const handlePluginPageError = (error: unknown, fallbackMessage: string) => {
    if (error instanceof ApiRequestError) {
      return;
    }
    message.error(error instanceof Error && error.message ? error.message : fallbackMessage);
  };

  const loadOverview = async () => {
    setLoading(true);
    try {
      const [definitionList, tenantPlugins, versionResult] = await Promise.all([
        pluginService.definitions({ autoRedirectOnUnauthorized: false }),
        pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
        pluginService.allVersions({ autoRedirectOnUnauthorized: false }),
      ]);
      const nextVersionMap: Record<string, PluginVersion[]> = { ...versionResult };
      definitionList.forEach((plugin) => {
        nextVersionMap[plugin.pluginCode] = nextVersionMap[plugin.pluginCode] || [];
      });

      setDefinitions(definitionList);
      setAvailablePlugins(tenantPlugins);
      setVersionMap(nextVersionMap);
      if (!selectedPlugin && definitionList.length > 0) {
        setSelectedPlugin(definitionList[0]);
      }

      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              availablePlugins: tenantPlugins,
            }
        : prev,
      );
    } catch (error) {
      handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.load', defaultMessage: 'Failed to load plugin information, please try again later' }));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadOverview();
  }, []);

  const refreshBootstrap = async () => {
    const [menuTree, available] = await Promise.all([
      pluginService.currentMenus({ autoRedirectOnUnauthorized: false }),
      pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
    ]);
    setInitialState((prev) =>
      prev
        ? {
            ...prev,
            menuTree,
            menuVersion: (prev.menuVersion ?? 0) + 1,
            availablePlugins: available,
            securitySettings: prev.securitySettings,
          }
        : prev,
    );
  };

  const showConfirm = (title: string, content: string, action: () => Promise<void>) =>
    Modal.confirm({
      title,
      content,
      okText: formatMessage({ id: 'page.plugins.confirm', defaultMessage: 'Confirm' }),
      cancelText: formatMessage({ id: 'page.plugins.cancel', defaultMessage: 'Cancel' }),
      onOk: async () => {
        try {
          await action();
        } catch (error) {
          handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.operation', defaultMessage: 'Operation failed, please try again later' }));
        }
      },
    });

  const refreshAfterMutation = async () => {
    try {
      await loadOverview();
    } catch {
      message.warning(formatMessage({ id: 'page.plugins.error.listRefresh', defaultMessage: 'Plugins were updated, but the list failed to refresh. Please refresh the page manually.' }));
    }
    try {
      await refreshBootstrap();
    } catch {
      message.warning(formatMessage({ id: 'page.plugins.error.menuRefresh', defaultMessage: 'Plugins were updated, but the menu failed to refresh. Please refresh the page manually.' }));
    }
  };

  const handleInstall = async (pluginCode: string, version: string) => {
    showConfirm(formatMessage({ id: 'page.plugins.installVersion', defaultMessage: 'Install plugin version' }), `${pluginCode} @ ${version}`, async () => {
      setMutationLoading(true);
      try {
        await pluginService.install({ pluginCode, version }, { autoRedirectOnUnauthorized: false });
        message.success(formatMessage({ id: 'page.plugins.success.installed', defaultMessage: 'Plugin installed successfully' }));
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.install', defaultMessage: 'Failed to install plugin, please try again later' }));
      } finally {
        setMutationLoading(false);
      }
    });
  };

  const handleActivate = async (pluginCode: string, version: string) => {
    showConfirm(formatMessage({ id: 'page.plugins.activateVersion', defaultMessage: 'Activate plugin version' }), `${pluginCode} @ ${version}`, async () => {
      setMutationLoading(true);
      try {
        await pluginService.upgrade({ pluginCode, version }, { autoRedirectOnUnauthorized: false });
        message.success(formatMessage({ id: 'page.plugins.success.activated', defaultMessage: 'Plugin active version switched' }));
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.activate', defaultMessage: 'Failed to activate plugin, please try again later' }));
      } finally {
        setMutationLoading(false);
      }
    });
  };

  const handleEnable = async (pluginCode: string, version?: string) => {
    const versionToUse = version || getPreferredEnableVersion(pluginCode, versionMap)?.version;
    if (!versionToUse) {
      message.warning(formatMessage({ id: 'page.plugins.error.installableVersion', defaultMessage: 'Please install an available version first' }));
      setSelectedPlugin(definitions.find((item) => item.pluginCode === pluginCode) || null);
      setVersionDrawerOpen(true);
      return;
    }
    showConfirm(formatMessage({ id: 'page.plugins.enable', defaultMessage: 'Enable plugin' }), `${pluginCode} @ ${versionToUse}`, async () => {
      setMutationLoading(true);
      try {
        await pluginService.enable({ tenantId: PLATFORM_TENANT_ID, pluginCode, version: versionToUse }, { autoRedirectOnUnauthorized: false });
        message.success(formatMessage({ id: 'page.plugins.success.enabled', defaultMessage: 'Plugin enabled' }));
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.enable', defaultMessage: 'Failed to enable plugin, please try again later' }));
      } finally {
        setMutationLoading(false);
      }
    });
  };

  const handleDisable = async (pluginCode: string) => {
    showConfirm(formatMessage({ id: 'page.plugins.disable', defaultMessage: 'Disable plugin' }), pluginCode, async () => {
      setMutationLoading(true);
      try {
        await pluginService.disable({ tenantId: PLATFORM_TENANT_ID, pluginCode }, { autoRedirectOnUnauthorized: false });
        message.success(formatMessage({ id: 'page.plugins.success.disabled', defaultMessage: 'Plugin disabled' }));
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.disable', defaultMessage: 'Failed to disable plugin, please try again later' }));
      } finally {
        setMutationLoading(false);
      }
    });
  };

  const handleRollback = async (pluginCode: string, version: string) => {
    showConfirm(formatMessage({ id: 'page.plugins.rollbackVersion', defaultMessage: 'Rollback plugin version' }), `${pluginCode} -> ${version}`, async () => {
      setMutationLoading(true);
      try {
        await pluginService.rollback({ pluginCode, targetVersion: version }, { autoRedirectOnUnauthorized: false });
        message.success(formatMessage({ id: 'page.plugins.success.rollback', defaultMessage: 'Plugin rolled back' }));
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.rollback', defaultMessage: 'Failed to rollback plugin, please try again later' }));
      } finally {
        setMutationLoading(false);
      }
    });
  };

  const handleUninstall = (plugin: PluginDefinition) => {
    setUninstallTarget(plugin);
    setRemovePluginData(false);
    setUninstallDialogOpen(true);
  };

  const confirmUninstall = async () => {
    if (!uninstallTarget) {
      return;
    }

    setMutationLoading(true);
    try {
      await pluginService.uninstall(
        uninstallTarget.pluginCode,
        { removeData: removePluginData },
        { autoRedirectOnUnauthorized: false },
      );
      message.success(removePluginData ? formatMessage({ id: 'page.plugins.success.uninstalledAndDeleted', defaultMessage: 'Plugin uninstalled and database data removed' }) : formatMessage({ id: 'page.plugins.success.uninstalled', defaultMessage: 'Plugin uninstalled' }));
      setUninstallDialogOpen(false);
      setUninstallTarget(null);
      await refreshAfterMutation();
    } catch (error) {
      handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.uninstall', defaultMessage: 'Failed to uninstall plugin, please try again later' }));
    } finally {
      setMutationLoading(false);
    }
  };

  const handleUpload = async () => {
    if (!uploadFile) {
      message.warning(formatMessage({ id: 'page.plugins.error.selectPackage', defaultMessage: 'Please choose a plugin package first' }));
      return;
    }
    if (!uploadFile.name.toLowerCase().endsWith('.zip')) {
      message.warning(formatMessage({ id: 'page.plugins.error.zipOnly', defaultMessage: 'Only zip plugin packages are supported' }));
      return;
    }
    if (uploadFile.size > 50 * 1024 * 1024) {
      message.warning(formatMessage({ id: 'page.plugins.error.max50mb', defaultMessage: 'The plugin package cannot exceed 50MB' }));
      return;
    }
    setMutationLoading(true);
    try {
      await pluginService.upload(uploadFile, { autoRedirectOnUnauthorized: false });
      setUploadVisible(false);
      setUploadFile(null);
      message.success(formatMessage({ id: 'page.plugins.success.uploaded', defaultMessage: 'Plugin uploaded and validated' }));
      await loadOverview();
    } catch (error) {
      handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.upload', defaultMessage: 'Failed to upload plugin, please try again later' }));
    } finally {
      setMutationLoading(false);
    }
  };

  const handleOpenVersions = (plugin: PluginDefinition) => {
    setSelectedPlugin(plugin);
    setVersionDrawerOpen(true);
  };

  const handleOpenDetails = (plugin: PluginDefinition) => {
    setSelectedPlugin(plugin);
    setDetailDrawerOpen(true);
  };

  const handleOpenLogs = async (plugin: PluginDefinition) => {
    setSelectedPlugin(plugin);
    setLogDrawerOpen(true);
    setLogsLoading(true);
    try {
      setRuntimeLogs(await pluginService.runtimeLogs(plugin.pluginCode, { autoRedirectOnUnauthorized: false }));
    } catch (error) {
      handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.logs', defaultMessage: 'Failed to load plugin logs, please try again later' }));
    } finally {
      setLogsLoading(false);
    }
  };

  const currentAvailableMap = useMemo(() => buildAvailablePluginMap(availablePlugins), [availablePlugins]);

  const filteredDefinitions = useMemo(() => filterPluginDefinitions(definitions, searchKeyword), [definitions, searchKeyword]);

  const selectedPluginVersions = selectedPlugin ? versionMap[selectedPlugin.pluginCode] || [] : [];
  const selectedTenantPlugin = selectedPlugin ? currentAvailableMap.get(selectedPlugin.pluginCode) : undefined;
  const selectedActiveVersion = selectedPluginVersions.find((item) => item.isActive === 1) || selectedPluginVersions[0];
  const versionColumns = useMemo(
    () =>
      buildVersionColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        onInstall: (pluginCode, version) => void handleInstall(pluginCode, version),
        onActivate: (pluginCode, version) => void handleActivate(pluginCode, version),
        onDisable: (pluginCode) => void handleDisable(pluginCode),
        onRollback: (pluginCode, version) => void handleRollback(pluginCode, version),
      }),
    [handleActivate, handleDisable, handleInstall, handleRollback, responsive.isDesktop, responsive.isMobile],
  );
  return (
    <ManagementPage
      content={null}
      ghost
      title={formatMessage({ id: 'page.plugins.title', defaultMessage: 'Plugin management' })}
      style={{ height: '100%', minHeight: 0 }}
    >
      <ManagementPageBody>
        <Card bodyStyle={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
            <Input.Search
              allowClear
              placeholder={formatMessage({ id: 'page.plugins.searchPlaceholder', defaultMessage: 'Enter plugin code or name' })}
              value={searchKeyword}
              onChange={(event) => setSearchKeyword(event.target.value)}
              style={{ width: 320, maxWidth: '100%', flex: '0 1 320px' }}
            />
            <Space wrap>
              <Button icon={<SyncOutlined />} onClick={() => void loadOverview()} loading={loading || mutationLoading}>
                {formatMessage({ id: 'page.plugins.refresh', defaultMessage: 'Refresh' })}
              </Button>
              <Button icon={<CloudUploadOutlined />} type="primary" onClick={() => setUploadVisible(true)}>
                {formatMessage({ id: 'page.plugins.upload', defaultMessage: 'Upload plugin' })}
              </Button>
            </Space>
          </Space>

          <PluginCardsGrid
            loading={loading}
            definitions={filteredDefinitions}
            currentAvailableMap={currentAvailableMap}
            getPreferredEnableVersion={(pluginCode) => getPreferredEnableVersion(pluginCode, versionMap)}
            mutationLoading={mutationLoading}
            onToggleEnable={(pluginCode, enabled, versionLabel) =>
              void (enabled ? handleEnable(pluginCode, versionLabel) : handleDisable(pluginCode))
            }
            onOpenDetails={handleOpenDetails}
            onOpenVersions={handleOpenVersions}
            onOpenLogs={(plugin) => void handleOpenLogs(plugin)}
            onUninstall={handleUninstall}
          />
        </Card>
      </ManagementPageBody>

      <ManagementDrawer
        title={selectedPlugin ? `${selectedPlugin.pluginName} · ${formatMessage({ id: 'page.plugins.versionManagement', defaultMessage: 'Version management' })}` : formatMessage({ id: 'page.plugins.versionManagement', defaultMessage: 'Version management' })}
        open={versionDrawerOpen}
        onClose={() => setVersionDrawerOpen(false)}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions bordered column={responsive.isMobile ? 1 : 2} size="small">
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.pluginCode', defaultMessage: 'Plugin code' })}>{selectedPlugin?.pluginCode || '-'}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.currentEnabledVersion', defaultMessage: 'Current enabled version' })}>{selectedTenantPlugin?.version || selectedActiveVersion?.version || '-'}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.versionCount', defaultMessage: 'Version count' })}>{selectedPluginVersions.length}</Descriptions.Item>
          </Descriptions>
          <ManagementTable<PluginVersion>
            rowKey={(record) => `${record.pluginCode}-${record.version}`}
            loading={loading}
            dataSource={selectedPluginVersions}
            pagination={false}
            columns={versionColumns}
            isMobile={responsive.isMobile}
            search={false}
            toolBarRender={false}
          />
        </Space>
      </ManagementDrawer>

      <ManagementDrawer
        title={selectedPlugin ? `${selectedPlugin.pluginName} · ${formatMessage({ id: 'page.plugins.detail', defaultMessage: 'Details' })}` : formatMessage({ id: 'page.plugins.detail', defaultMessage: 'Plugin details' })}
        open={detailDrawerOpen}
        onClose={() => setDetailDrawerOpen(false)}
      >
        {selectedPlugin ? (
          <Descriptions {...detailDescriptionsProps}>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.pluginCode', defaultMessage: 'Plugin code' })}>{selectedPlugin.pluginCode}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.name', defaultMessage: 'Plugin name' })}>{selectedPlugin.pluginName}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.description', defaultMessage: 'Description' })}>{selectedPlugin.description || '-'}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.author', defaultMessage: 'Author' })}>{selectedPlugin.author || '-'}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.apiVersion', defaultMessage: 'API version' })}>{selectedPlugin.pluginApiVersion}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.status', defaultMessage: 'Status' })}>{selectedPlugin.status}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.currentVersion', defaultMessage: 'Current version' })}>{selectedTenantPlugin?.version || selectedActiveVersion?.version || '-'}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.enabled', defaultMessage: 'Enabled' })}>{selectedTenantPlugin ? formatMessage({ id: 'page.plugins.enabled.true', defaultMessage: 'Enabled' }) : formatMessage({ id: 'page.plugins.enabled.false', defaultMessage: 'Disabled' })}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.menuCount', defaultMessage: 'Menu count' })}>{selectedTenantPlugin?.menus?.length || 0}</Descriptions.Item>
            <Descriptions.Item label={formatMessage({ id: 'page.plugins.routeCount', defaultMessage: 'Route count' })}>{selectedTenantPlugin?.routes?.length || 0}</Descriptions.Item>
          </Descriptions>
        ) : null}
      </ManagementDrawer>

      <ManagementDrawer
        title={selectedPlugin ? `${selectedPlugin.pluginName} · ${formatMessage({ id: 'page.plugins.log', defaultMessage: 'Logs' })}` : formatMessage({ id: 'page.plugins.log', defaultMessage: 'Plugin logs' })}
        open={logDrawerOpen}
        onClose={() => setLogDrawerOpen(false)}
      >
        <ManagementTable<PluginRuntimeLog>
          rowKey="id"
          loading={logsLoading}
          dataSource={runtimeLogs}
          pagination={false}
          columns={logColumns}
          isMobile={responsive.isMobile}
          search={false}
          toolBarRender={false}
        />
      </ManagementDrawer>

      <Modal
        title={uninstallTarget ? formatMessage({ id: 'page.plugins.uninstallWithName', defaultMessage: 'Uninstall {name}' }, { name: uninstallTarget.pluginName }) : formatMessage({ id: 'page.plugins.uninstall', defaultMessage: 'Uninstall plugin' })}
        open={uninstallDialogOpen}
        onCancel={() => {
          if (mutationLoading) {
            return;
          }
          setUninstallDialogOpen(false);
          setUninstallTarget(null);
        }}
        okText={formatMessage({ id: 'page.plugins.confirm', defaultMessage: 'Confirm uninstall' })}
        cancelText={formatMessage({ id: 'page.plugins.cancel', defaultMessage: 'Cancel' })}
        confirmLoading={mutationLoading}
        onOk={() => void confirmUninstall()}
        destroyOnHidden
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Typography.Paragraph style={{ marginBottom: 0 }}>
            {formatMessage({ id: 'page.plugins.confirmUninstall', defaultMessage: 'You are about to uninstall {name}.' }, { name: uninstallTarget?.pluginName || uninstallTarget?.pluginCode || '-' })}
          </Typography.Paragraph>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {formatMessage({ id: 'page.plugins.uninstallDesc', defaultMessage: 'You can choose whether to delete the plugin-related database data as well. If selected, plugin runtime logs, platform bindings, version records, and plugin definitions will be removed.' })}
          </Typography.Paragraph>
          <Radio.Group
            value={removePluginData}
            onChange={(event) => setRemovePluginData(event.target.value)}
            style={{ width: '100%' }}
          >
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Radio
                value={false}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  width: '100%',
                  marginInlineStart: 0,
                  padding: '16px 20px',
                  borderRadius: 10,
                  border: `1px solid ${removePluginData ? token.colorBorderSecondary : token.colorPrimary}`,
                  background: removePluginData ? token.colorBgContainer : token.colorPrimaryBg,
                }}
              >
                {formatMessage({ id: 'page.plugins.onlyUninstall', defaultMessage: 'Only uninstall the plugin, do not delete database data' })}
              </Radio>
              <Radio
                value={true}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  width: '100%',
                  marginInlineStart: 0,
                  padding: '16px 20px',
                  borderRadius: 10,
                  border: `1px solid ${removePluginData ? token.colorError : token.colorBorderSecondary}`,
                  background: removePluginData ? token.colorErrorBg : token.colorBgContainer,
                }}
              >
                {formatMessage({ id: 'page.plugins.uninstallAndDeleteData', defaultMessage: 'Uninstall and delete database data' })}
              </Radio>
            </Space>
          </Radio.Group>
        </Space>
      </Modal>

      <Modal
        open={uploadVisible}
        title={formatMessage({ id: 'page.plugins.uploadPackage', defaultMessage: 'Upload plugin package' })}
        onCancel={() => setUploadVisible(false)}
        onOk={() => void handleUpload()}
        confirmLoading={mutationLoading}
        okText={formatMessage({ id: 'page.plugins.uploadConfirm', defaultMessage: 'Upload' })}
        cancelText={formatMessage({ id: 'page.plugins.cancelUpload', defaultMessage: 'Cancel' })}
      >
        <Upload
          beforeUpload={(file) => {
            setUploadFile(file);
            return false;
          }}
          maxCount={1}
          accept=".zip"
          onRemove={() => setUploadFile(null)}
        >
          <Button icon={<CloudUploadOutlined />}>{formatMessage({ id: 'page.plugins.chooseZip', defaultMessage: 'Choose zip plugin package' })}</Button>
        </Upload>
      </Modal>
    </ManagementPage>
  );
};

export default PluginsPage;
