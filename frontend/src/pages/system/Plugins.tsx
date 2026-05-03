import { CloudUploadOutlined, SyncOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Descriptions, Drawer, Input, Modal, Radio, Space, Table, Typography, Upload, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { buildTableScroll } from '@/features/table/proTable';
import { useResponsive } from '@/hooks/useResponsive';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { ApiRequestError } from '@/services/common/request';
import { buildVersionColumns, logColumns } from '@/pages/system/plugins/columns';
import { PluginCardsGrid } from '@/pages/system/plugins/components/PluginCardsGrid';
import { buildAvailablePluginMap, filterPluginDefinitions, getPreferredEnableVersion } from '@/pages/system/plugins/utils';
import { pluginService } from '@/services/plugin';
import type { PluginDefinition, PluginRuntimeLog, PluginVersion, TenantPlugin } from '@/types/api';

const PluginsPage = () => {
  const { initialState, setInitialState } = useInitialStateModel();
  const responsive = useResponsive();
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
      const [definitionList, tenantPlugins] = await Promise.all([
        pluginService.definitions({ autoRedirectOnUnauthorized: false }),
        pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
      ]);
      const versionResults = await Promise.allSettled(
        definitionList.map(async (plugin) => ({
          pluginCode: plugin.pluginCode,
          versions: await pluginService.versions(plugin.pluginCode, { autoRedirectOnUnauthorized: false }),
        })),
      );
      const nextVersionMap: Record<string, PluginVersion[]> = {};
      versionResults.forEach((result) => {
        if (result.status === 'fulfilled') {
          nextVersionMap[result.value.pluginCode] = result.value.versions;
        }
      });
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
      handlePluginPageError(error, '加载插件信息失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadOverview();
  }, [initialState?.currentTenant?.tenantId]);

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
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await action();
        } catch (error) {
          handlePluginPageError(error, '操作失败，请稍后重试');
        }
      },
    });

  const refreshAfterMutation = async () => {
    try {
      await loadOverview();
    } catch {
      message.warning('插件已更新，但列表刷新失败，请手动刷新页面');
    }
    try {
      await refreshBootstrap();
    } catch {
      message.warning('插件已更新，但菜单刷新失败，请手动刷新页面');
    }
  };

  const handleInstall = async (pluginCode: string, version: string) => {
    showConfirm('安装插件版本', `${pluginCode} @ ${version}`, async () => {
      setMutationLoading(true);
      try {
        await pluginService.install({ pluginCode, version }, { autoRedirectOnUnauthorized: false });
        message.success('插件安装完成');
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, '安装插件失败，请稍后重试');
      } finally {
        setMutationLoading(false);
      }
    });
  };

  const handleActivate = async (pluginCode: string, version: string) => {
    showConfirm('激活插件版本', `${pluginCode} @ ${version}`, async () => {
      setMutationLoading(true);
      try {
        await pluginService.upgrade({ pluginCode, version }, { autoRedirectOnUnauthorized: false });
        message.success('插件激活版本已切换');
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, '激活插件失败，请稍后重试');
      } finally {
        setMutationLoading(false);
      }
    });
  };

  const handleEnable = async (pluginCode: string, version?: string) => {
    const tenantId = initialState?.currentTenant?.tenantId;
    if (!tenantId) {
      message.warning('当前未选择租户');
      return;
    }
    const versionToUse = version || getPreferredEnableVersion(pluginCode, versionMap)?.version;
    if (!versionToUse) {
      message.warning('请先安装可用版本');
      setSelectedPlugin(definitions.find((item) => item.pluginCode === pluginCode) || null);
      setVersionDrawerOpen(true);
      return;
    }
    showConfirm('启用插件', `${pluginCode} @ ${versionToUse}`, async () => {
      setMutationLoading(true);
      try {
        await pluginService.enable({ tenantId, pluginCode, version: versionToUse }, { autoRedirectOnUnauthorized: false });
        message.success('插件已启用');
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, '启用插件失败，请稍后重试');
      } finally {
        setMutationLoading(false);
      }
    });
  };

  const handleDisable = async (pluginCode: string) => {
    const tenantId = initialState?.currentTenant?.tenantId;
    if (!tenantId) {
      message.warning('当前未选择租户');
      return;
    }
    showConfirm('停用插件', pluginCode, async () => {
      setMutationLoading(true);
      try {
        await pluginService.disable({ tenantId, pluginCode }, { autoRedirectOnUnauthorized: false });
        message.success('插件已停用');
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, '停用插件失败，请稍后重试');
      } finally {
        setMutationLoading(false);
      }
    });
  };

  const handleRollback = async (pluginCode: string, version: string) => {
    showConfirm('回滚插件版本', `${pluginCode} -> ${version}`, async () => {
      setMutationLoading(true);
      try {
        await pluginService.rollback({ pluginCode, targetVersion: version }, { autoRedirectOnUnauthorized: false });
        message.success('插件已回滚');
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, '回滚插件失败，请稍后重试');
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
      message.success(removePluginData ? '插件已卸载，并已删除数据库数据' : '插件已卸载');
      setUninstallDialogOpen(false);
      setUninstallTarget(null);
      await refreshAfterMutation();
    } catch (error) {
      handlePluginPageError(error, '卸载插件失败，请稍后重试');
    } finally {
      setMutationLoading(false);
    }
  };

  const handleUpload = async () => {
    if (!uploadFile) {
      message.warning('请先选择插件包');
      return;
    }
    if (!uploadFile.name.toLowerCase().endsWith('.zip')) {
      message.warning('仅支持 zip 插件包');
      return;
    }
    if (uploadFile.size > 50 * 1024 * 1024) {
      message.warning('插件包不能超过 50MB');
      return;
    }
    setMutationLoading(true);
    try {
      await pluginService.upload(uploadFile, { autoRedirectOnUnauthorized: false });
      setUploadVisible(false);
      setUploadFile(null);
      message.success('插件上传并完成校验');
      await loadOverview();
    } catch (error) {
      handlePluginPageError(error, '上传插件失败，请稍后重试');
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
      handlePluginPageError(error, '加载插件日志失败，请稍后重试');
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
  const responsiveVersionColumns = versionColumns as ColumnsType<PluginVersion>;
  const responsiveLogColumns = logColumns as ColumnsType<PluginRuntimeLog>;

  return (
    <PageContainer
      className="saas-management-page"
      content={null}
      ghost
      title="插件管理"
      style={{ height: '100%', minHeight: 0 }}
    >
      <div className="saas-management-page-body">
        <Card bodyStyle={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
            <Input.Search
              allowClear
              placeholder="输入插件编码或名称"
              value={searchKeyword}
              onChange={(event) => setSearchKeyword(event.target.value)}
              style={{ width: 320, maxWidth: '100%', flex: '0 1 320px' }}
            />
            <Space wrap>
              <Button icon={<SyncOutlined />} onClick={() => void loadOverview()} loading={loading || mutationLoading}>
                刷新
              </Button>
              <Button icon={<CloudUploadOutlined />} type="primary" onClick={() => setUploadVisible(true)}>
                上传插件
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
      </div>

      <Drawer
        title={selectedPlugin ? `${selectedPlugin.pluginName} · 版本管理` : '版本管理'}
        open={versionDrawerOpen}
        onClose={() => setVersionDrawerOpen(false)}
        width={920}
        destroyOnClose
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions bordered column={responsive.isMobile ? 1 : 2} size="small">
            <Descriptions.Item label="插件编码">{selectedPlugin?.pluginCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="当前启用版本">{selectedTenantPlugin?.version || selectedActiveVersion?.version || '-'}</Descriptions.Item>
            <Descriptions.Item label="版本数量">{selectedPluginVersions.length}</Descriptions.Item>
          </Descriptions>
          <div className="saas-table-wrap">
            <Table<PluginVersion>
              rowKey={(record) => `${record.pluginCode}-${record.version}`}
              loading={loading}
              dataSource={selectedPluginVersions}
              pagination={false}
              scroll={buildTableScroll(versionColumns, responsive.isMobile)}
              columns={responsiveVersionColumns}
            />
          </div>
        </Space>
      </Drawer>

      <Drawer
        title={selectedPlugin ? `${selectedPlugin.pluginName} · 详情` : '插件详情'}
        open={detailDrawerOpen}
        onClose={() => setDetailDrawerOpen(false)}
        width={760}
        destroyOnClose
      >
        {selectedPlugin ? (
          <Descriptions {...detailDescriptionsProps}>
            <Descriptions.Item label="插件编码">{selectedPlugin.pluginCode}</Descriptions.Item>
            <Descriptions.Item label="插件名称">{selectedPlugin.pluginName}</Descriptions.Item>
            <Descriptions.Item label="描述">{selectedPlugin.description || '-'}</Descriptions.Item>
            <Descriptions.Item label="作者">{selectedPlugin.author || '-'}</Descriptions.Item>
            <Descriptions.Item label="API 版本">{selectedPlugin.pluginApiVersion}</Descriptions.Item>
            <Descriptions.Item label="状态">{selectedPlugin.status}</Descriptions.Item>
            <Descriptions.Item label="当前版本">{selectedTenantPlugin?.version || selectedActiveVersion?.version || '-'}</Descriptions.Item>
            <Descriptions.Item label="是否启用">{selectedTenantPlugin ? '已启用' : '未启用'}</Descriptions.Item>
            <Descriptions.Item label="菜单数">{selectedTenantPlugin?.menus?.length || 0}</Descriptions.Item>
            <Descriptions.Item label="路由数">{selectedTenantPlugin?.routes?.length || 0}</Descriptions.Item>
          </Descriptions>
        ) : null}
      </Drawer>

      <Drawer
        title={selectedPlugin ? `${selectedPlugin.pluginName} · 日志` : '插件日志'}
        open={logDrawerOpen}
        onClose={() => setLogDrawerOpen(false)}
        width={920}
        destroyOnClose
      >
        <div className="saas-table-wrap">
          <Table<PluginRuntimeLog>
            rowKey="id"
            loading={logsLoading}
            dataSource={runtimeLogs}
            pagination={false}
            scroll={buildTableScroll(logColumns, responsive.isMobile)}
            columns={responsiveLogColumns}
          />
        </div>
      </Drawer>

      <Modal
        title={uninstallTarget ? `卸载 ${uninstallTarget.pluginName}` : '卸载插件'}
        open={uninstallDialogOpen}
        onCancel={() => {
          if (mutationLoading) {
            return;
          }
          setUninstallDialogOpen(false);
          setUninstallTarget(null);
        }}
        okText="确认卸载"
        cancelText="取消"
        confirmLoading={mutationLoading}
        onOk={() => void confirmUninstall()}
        destroyOnClose
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Typography.Paragraph style={{ marginBottom: 0 }}>
            确认后将卸载插件 <Typography.Text strong>{uninstallTarget?.pluginName || uninstallTarget?.pluginCode || '-'}</Typography.Text>。
          </Typography.Paragraph>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            你可以选择是否同时删除插件相关数据库数据。选择删除后，会清理插件运行日志、租户关联、版本记录和插件定义等数据。
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
                  border: `1px solid ${removePluginData ? '#f0f0f0' : '#1677ff'}`,
                  background: removePluginData ? '#fff' : '#f5faff',
                }}
              >
                仅卸载插件，不删除数据库数据
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
                  border: `1px solid ${removePluginData ? '#ff4d4f' : '#f0f0f0'}`,
                  background: removePluginData ? '#fff2f0' : '#fff',
                }}
              >
                卸载并删除数据库数据
              </Radio>
            </Space>
          </Radio.Group>
        </Space>
      </Modal>

      <Modal
        open={uploadVisible}
        title="上传插件包"
        onCancel={() => setUploadVisible(false)}
        onOk={() => void handleUpload()}
        confirmLoading={mutationLoading}
        okText="上传"
        cancelText="取消"
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
          <Button icon={<CloudUploadOutlined />}>选择 zip 插件包</Button>
        </Upload>
      </Modal>
    </PageContainer>
  );
};

export default PluginsPage;
