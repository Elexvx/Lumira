import { BuildOutlined, CloudUploadOutlined, DeleteOutlined, FileSearchOutlined, PoweroffOutlined, SyncOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Col, Descriptions, Drawer, Empty, Input, Modal, Row, Space, Switch, Table, Tag, Typography, Upload, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { usePermission } from '@/hooks/usePermission';
import { pluginService } from '@/services/plugin';
import type { PluginDefinition, PluginRuntimeLog, PluginVersion, TenantPlugin } from '@/types/api';

const PluginsPage = () => {
  const { initialState, setInitialState } = useInitialStateModel();
  const { canAccess } = usePermission();
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
      onOk: async () => action(),
    });

  const getActiveVersion = (pluginCode: string) => {
    const versions = versionMap[pluginCode] || [];
    return versions.find((item) => item.isActive === 1) || versions[0];
  };

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
    const versionToUse = version || getActiveVersion(pluginCode)?.version;
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
      } finally {
        setMutationLoading(false);
      }
    });
  };

  const handleUninstall = async (pluginCode: string) => {
    showConfirm('卸载插件', `${pluginCode} 将从系统中移除，确认继续吗？`, async () => {
      setMutationLoading(true);
      try {
        await pluginService.uninstall(pluginCode, { autoRedirectOnUnauthorized: false });
        message.success('插件已卸载');
        await refreshAfterMutation();
      } finally {
        setMutationLoading(false);
      }
    });
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
    } finally {
      setLogsLoading(false);
    }
  };

  const currentAvailableMap = useMemo(
    () => new Map(availablePlugins.map((item) => [item.pluginCode, item])),
    [availablePlugins],
  );

  const filteredDefinitions = useMemo(() => {
    const keyword = searchKeyword.trim().toLowerCase();
    return definitions.filter((item) => {
      if (!keyword) {
        return true;
      }
      return item.pluginName.toLowerCase().includes(keyword) || item.pluginCode.toLowerCase().includes(keyword);
    });
  }, [definitions, searchKeyword]);

  const selectedPluginVersions = selectedPlugin ? versionMap[selectedPlugin.pluginCode] || [] : [];
  const selectedTenantPlugin = selectedPlugin ? currentAvailableMap.get(selectedPlugin.pluginCode) : undefined;
  const selectedActiveVersion = selectedPluginVersions.find((item) => item.isActive === 1) || selectedPluginVersions[0];

  return (
    <PageContainer
      className="saas-management-page"
      ghost
      title="插件管理"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
      extra={null}
    >
      <div className="saas-management-page-body">
        <Card style={{ marginBottom: 16 }}>
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
        </Card>

        <Row gutter={[16, 16]}>
          {filteredDefinitions.map((plugin) => {
            const activeVersion = getActiveVersion(plugin.pluginCode);
            const enabledPlugin = currentAvailableMap.get(plugin.pluginCode);
            const enabled = Boolean(enabledPlugin);
            const versionLabel = enabledPlugin?.version || activeVersion?.version;
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
                      onChange={(checked) => void (checked ? handleEnable(plugin.pluginCode, versionLabel) : handleDisable(plugin.pluginCode))}
                    />
                  }
                >
                  <Space direction="vertical" size={12} style={{ width: '100%' }}>
                    <Typography.Paragraph style={{ marginBottom: 0 }}>
                      {plugin.description || '暂无插件描述'}
                    </Typography.Paragraph>
                    <Descriptions column={1} size="small" bordered>
                      <Descriptions.Item label="插件编码">{plugin.pluginCode}</Descriptions.Item>
                      <Descriptions.Item label="API 版本">{plugin.pluginApiVersion}</Descriptions.Item>
                      <Descriptions.Item label="当前版本">{versionLabel || '-'}</Descriptions.Item>
                      <Descriptions.Item label="作者">{plugin.author || '-'}</Descriptions.Item>
                    </Descriptions>
                    <Space wrap>
                      <Button onClick={() => handleOpenDetails(plugin)}>详情</Button>
                      <Button onClick={() => handleOpenVersions(plugin)}>版本</Button>
                      <Button onClick={() => handleOpenLogs(plugin)} icon={<FileSearchOutlined />}>
                        日志
                      </Button>
                      <Button danger icon={<DeleteOutlined />} onClick={() => void handleUninstall(plugin.pluginCode)}>
                        卸载
                      </Button>
                    </Space>
                  </Space>
                </Card>
              </Col>
            );
          })}
        </Row>

        {!loading && filteredDefinitions.length === 0 ? (
          <Card style={{ marginTop: 16 }}>
            <Empty description="暂无插件定义" />
          </Card>
        ) : null}
      </div>

      <Drawer
        title={selectedPlugin ? `${selectedPlugin.pluginName} · 版本管理` : '版本管理'}
        open={versionDrawerOpen}
        onClose={() => setVersionDrawerOpen(false)}
        width={920}
        destroyOnClose
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="插件编码">{selectedPlugin?.pluginCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="当前启用版本">{selectedTenantPlugin?.version || selectedActiveVersion?.version || '-'}</Descriptions.Item>
            <Descriptions.Item label="版本数量">{selectedPluginVersions.length}</Descriptions.Item>
          </Descriptions>
          <Table<PluginVersion>
            rowKey={(record) => `${record.pluginCode}-${record.version}`}
            loading={loading}
            dataSource={selectedPluginVersions}
            pagination={false}
            columns={[
              { title: '版本', dataIndex: 'version' },
              { title: '安装状态', dataIndex: 'installStatus' },
              { title: '加载状态', dataIndex: 'loadStatus' },
              { title: '健康状态', dataIndex: 'healthStatus' },
              {
                title: '激活',
                dataIndex: 'isActive',
                render: (_, record) => <Tag color={record.isActive === 1 ? 'green' : 'default'}>{record.isActive === 1 ? '是' : '否'}</Tag>,
              },
              {
                title: '操作',
                render: (_, record) => (
                  <Space wrap>
                    <Button onClick={() => void handleInstall(record.pluginCode, record.version)}>安装</Button>
                    <Button onClick={() => void handleActivate(record.pluginCode, record.version)}>激活</Button>
                    <Button onClick={() => void handleEnable(record.pluginCode, record.version)}>启用</Button>
                    <Button onClick={() => void handleDisable(record.pluginCode)}>停用</Button>
                    <Button onClick={() => void handleRollback(record.pluginCode, record.version)}>回滚</Button>
                  </Space>
                ),
              },
            ]}
          />
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
          <Descriptions bordered column={2} size="small">
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
        <Table<PluginRuntimeLog>
          rowKey="id"
          loading={logsLoading}
          dataSource={runtimeLogs}
          pagination={false}
          columns={[
            { title: '时间', dataIndex: 'createdAt', width: 180 },
            { title: '操作类型', dataIndex: 'operationType', width: 120 },
            { title: '生命周期', dataIndex: 'lifecycleStatus', width: 120 },
            { title: '结果', dataIndex: 'resultStatus', width: 120 },
            { title: '详情', dataIndex: 'detailMessage' },
          ]}
        />
      </Drawer>

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
