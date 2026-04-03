import { useEffect, useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { UploadOutlined } from '@ant-design/icons';
import { Button, Card, Descriptions, Drawer, Form, Input, Modal, Select, Space, Tag, Upload, message } from 'antd';
import { useRequest } from 'umi';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { usePermission } from '@/hooks/usePermission';
import { pluginService } from '@/services/plugin';
import type { PluginDefinition, PluginRuntimeLog, PluginVersion, TenantPlugin } from '@/types/api';

const PluginsPage = () => {
  const [queryForm] = Form.useForm();
  const { initialState, setInitialState } = useInitialStateModel();
  const { canAccess } = usePermission();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedPlugin, setSelectedPlugin] = useState<PluginDefinition | null>(null);
  const [versionDrawerOpen, setVersionDrawerOpen] = useState(false);
  const [logDrawerOpen, setLogDrawerOpen] = useState(false);
  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [uploadFile, setUploadFile] = useState<File | null>(null);

  const definitionQuery = useRequest(async () => ({
    data: await pluginService.definitions({ autoRedirectOnUnauthorized: false }),
  }) as { data: PluginDefinition[] }, {
    refreshDeps: [initialState?.currentTenant?.tenantId],
  });

  const versionQuery = useRequest(
    async () =>
      selectedPlugin
        ? ({ data: await pluginService.versions(selectedPlugin.pluginCode, { autoRedirectOnUnauthorized: false }) } as { data: PluginVersion[] })
        : ({ data: [] as PluginVersion[] } as { data: PluginVersion[] }),
    { refreshDeps: [selectedPlugin?.pluginCode] },
  );

  const logQuery = useRequest(
    async () =>
      selectedPlugin
        ? ({ data: await pluginService.runtimeLogs(selectedPlugin.pluginCode, { autoRedirectOnUnauthorized: false }) } as { data: PluginRuntimeLog[] })
        : ({ data: [] as PluginRuntimeLog[] } as { data: PluginRuntimeLog[] }),
    { refreshDeps: [selectedPlugin?.pluginCode, logDrawerOpen] },
  );

  const availableQuery = useRequest(async () => ({
    data: await pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
  }) as { data: TenantPlugin[] }, {
    refreshDeps: [initialState?.currentTenant?.tenantId],
  });

  const definitionList = definitionQuery.data || [];
  const versionList = versionQuery.data || [];
  const runtimeLogList = logQuery.data || [];
  const currentAvailable = availableQuery.data || initialState?.availablePlugins || [];

  useEffect(() => {
    if (!selectedPlugin && definitionList.length > 0) {
      setSelectedPlugin(definitionList[0]);
    }
  }, [definitionList, selectedPlugin]);

  const filteredDefinitions = useMemo(() => {
    const keyword = String(query.keyword || '').trim().toLowerCase();
    const pluginCode = String(query.pluginCode || '').trim().toLowerCase();
    const status = String(query.status || '').trim().toLowerCase();
    return definitionList.filter((item) => {
      const matchesKeyword = !keyword || item.pluginName.toLowerCase().includes(keyword) || item.pluginCode.toLowerCase().includes(keyword);
      const matchesCode = !pluginCode || item.pluginCode.toLowerCase().includes(pluginCode);
      const matchesStatus = !status || item.status.toLowerCase() === status;
      return matchesKeyword && matchesCode && matchesStatus;
    });
  }, [definitionList, query]);

  const refreshBootstrap = async () => {
    const [menuTree, availablePlugins] = await Promise.all([
      pluginService.currentMenus({ autoRedirectOnUnauthorized: false }),
      pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
    ]);
    setInitialState((prev) =>
      prev
        ? {
            ...prev,
            menuTree,
            availablePlugins,
            securitySettings: prev.securitySettings,
          }
        : prev,
    );
  };

  const confirm = (title: string, content: string, action: () => Promise<void>) =>
    Modal.confirm({
      title,
      content,
      okText: '确认',
      cancelText: '取消',
      onOk: action,
    });

  const confirmInstall = (pluginCode: string, version: string) =>
    confirm('安装插件版本', `${pluginCode} @ ${version}`, async () => {
      await pluginService.install({ pluginCode, version }, { autoRedirectOnUnauthorized: false });
      await versionQuery.refresh();
      await definitionQuery.refresh();
      message.success('插件安装完成');
    });

  const confirmActivate = (pluginCode: string, version: string) =>
    confirm('激活插件版本', `${pluginCode} @ ${version}`, async () => {
      await pluginService.upgrade({ pluginCode, version }, { autoRedirectOnUnauthorized: false });
      await versionQuery.refresh();
      await refreshBootstrap();
      message.success('插件激活版本已切换');
    });

  const confirmEnable = (pluginCode: string, version: string) => {
    const tenantId = initialState?.currentTenant?.tenantId;
    if (!tenantId) {
      message.error('当前未选择租户');
      return;
    }
    confirm('启用插件', `${pluginCode} @ ${version}`, async () => {
      await pluginService.enable({ tenantId, pluginCode, version }, { autoRedirectOnUnauthorized: false });
      await refreshBootstrap();
      message.success('插件已启用');
    });
  };

  const confirmDisable = (pluginCode: string) => {
    const tenantId = initialState?.currentTenant?.tenantId;
    if (!tenantId) {
      message.error('当前未选择租户');
      return;
    }
    confirm('停用插件', pluginCode, async () => {
      await pluginService.disable({ tenantId, pluginCode }, { autoRedirectOnUnauthorized: false });
      await refreshBootstrap();
      message.success('插件已停用');
    });
  };

  const confirmRollback = (pluginCode: string, version: string) =>
    confirm('回滚插件版本', `${pluginCode} -> ${version}`, async () => {
      await pluginService.rollback({ pluginCode, targetVersion: version }, { autoRedirectOnUnauthorized: false });
      await versionQuery.refresh();
      await refreshBootstrap();
      message.success('插件已回滚');
    });

  const handleUpload = async () => {
    if (!uploadFile) {
      message.error('请先选择插件包');
      return;
    }
    if (!uploadFile.name.toLowerCase().endsWith('.zip')) {
      message.error('仅支持 zip 插件包');
      return;
    }
    if (uploadFile.size > 50 * 1024 * 1024) {
      message.error('插件包不能超过 50MB');
      return;
    }
    await pluginService.upload(uploadFile, { autoRedirectOnUnauthorized: false });
    setUploadVisible(false);
    setUploadFile(null);
    await definitionQuery.refresh();
    message.success('插件上传并完成校验');
  };

  const selectedPluginDetail = useMemo(() => {
    if (!selectedPlugin) {
      return null;
    }
    const activeVersion = versionList.find((item) => item.isActive === 1) || versionList[0];
    const tenantPlugin = currentAvailable.find((item) => item.pluginCode === selectedPlugin.pluginCode);
    return {
      pluginCode: selectedPlugin.pluginCode,
      pluginName: selectedPlugin.pluginName,
      version: tenantPlugin?.version || activeVersion?.version || '-',
      author: selectedPlugin.author || '-',
      pluginApiVersion: selectedPlugin.pluginApiVersion || '-',
      status: selectedPlugin.status,
      healthStatus: activeVersion?.healthStatus || 'UNKNOWN',
      installStatus: activeVersion?.installStatus || '-',
      loadStatus: activeVersion?.loadStatus || '-',
      dependencyInfo: tenantPlugin?.sharedDeps?.length ? tenantPlugin.sharedDeps.join(', ') : '-',
      tenantEnabled: tenantPlugin ? '已启用' : '未启用',
      menuCount: tenantPlugin?.menus?.length || 0,
    };
  }, [currentAvailable, selectedPlugin, versionList]);

  const definitionColumns = useMemo<ProColumns<PluginDefinition>[]>(
    () => [
      { title: '插件编码', dataIndex: 'pluginCode' },
      { title: '名称', dataIndex: 'pluginName' },
      { title: '作者', dataIndex: 'author' },
      { title: 'API 版本', dataIndex: 'pluginApiVersion' },
      {
        title: '状态',
        dataIndex: 'status',
        render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
      },
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => (
          <Space wrap>
            {canAccess('plugin:management:view') ? (
              <Button
                onClick={() => {
                  setSelectedPlugin(record);
                  setDetailDrawerOpen(true);
                }}
              >
                详情
              </Button>
            ) : null}
            {canAccess('plugin:management:view') ? (
              <Button
                onClick={() => {
                  setSelectedPlugin(record);
                  setVersionDrawerOpen(true);
                }}
              >
                版本
              </Button>
            ) : null}
            {canAccess('plugin:management:logs') ? (
              <Button
                onClick={() => {
                  setSelectedPlugin(record);
                  setLogDrawerOpen(true);
                }}
              >
                日志
              </Button>
            ) : null}
          </Space>
        ),
      },
    ],
    [canAccess],
  );

  const versionColumns = useMemo<ProColumns<PluginVersion>[]>(
    () => [
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
        key: 'actions',
        render: (_, record) => (
          <Space wrap>
            {canAccess('plugin:management:install') ? (
              <Button onClick={() => confirmInstall(record.pluginCode, record.version)}>安装</Button>
            ) : null}
            {canAccess('plugin:management:upgrade') ? (
              <Button onClick={() => confirmActivate(record.pluginCode, record.version)}>激活</Button>
            ) : null}
            {canAccess('plugin:management:enable') ? (
              <Button onClick={() => confirmEnable(record.pluginCode, record.version)}>启用</Button>
            ) : null}
            {canAccess('plugin:management:disable') ? (
              <Button onClick={() => confirmDisable(record.pluginCode)}>停用</Button>
            ) : null}
            {canAccess('plugin:management:rollback') ? (
              <Button onClick={() => confirmRollback(record.pluginCode, record.version)}>回滚</Button>
            ) : null}
          </Space>
        ),
      },
    ],
    [canAccess, initialState?.currentTenant?.tenantId],
  );

  return (
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      breadcrumbRender={false}
      title="插件管理"
      subTitle="插件列表、版本、日志和运行态统一纳入页面规范。"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Card className="saas-query-panel">
          <Form
            form={queryForm}
            layout="vertical"
            onFinish={(values) => setQuery(values)}
            onReset={() => {
              queryForm.resetFields();
              setQuery({});
            }}
          >
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
              <Form.Item name="keyword" label="关键词">
                <Input allowClear placeholder="插件名称或编码" />
              </Form.Item>
              <Form.Item name="pluginCode" label="插件编码">
                <Input allowClear placeholder="输入插件编码" />
              </Form.Item>
              <Form.Item name="status" label="状态">
                <Select allowClear options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} />
              </Form.Item>
            </div>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button htmlType="reset">重置</Button>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={() => definitionQuery.refresh()}>刷新</Button>
              {canAccess('plugin:management:upload') ? (
                <Button type="primary" onClick={() => setUploadVisible(true)}>
                  上传插件
                </Button>
              ) : null}
            </Space>
          </Form>
        </Card>

        <Card className="saas-action-bar">
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space>
              <Tag color="blue">当前租户：{initialState?.currentTenant?.tenantName || '未选择'}</Tag>
            </Space>
            <Button onClick={() => definitionQuery.refresh()}>重新加载</Button>
          </Space>
        </Card>

        <Card className="saas-crud-table-card" bodyStyle={{ minHeight: 0 }}>
          <ProTable<PluginDefinition>
            rowKey="pluginCode"
            columns={definitionColumns}
            dataSource={filteredDefinitions}
            loading={definitionQuery.loading}
            search={false}
            options={false}
            toolBarRender={false}
            pagination={false}
          />
        </Card>

        <Drawer
          className="saas-detail-drawer"
          title={selectedPlugin ? `插件详情 · ${selectedPlugin.pluginName}` : '插件详情'}
          open={detailDrawerOpen}
          onClose={() => setDetailDrawerOpen(false)}
          width={720}
          destroyOnClose
        >
          {selectedPluginDetail ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Descriptions
                bordered
                size="small"
                column={2}
                items={[
                  { key: 'pluginCode', label: '插件编码', children: selectedPluginDetail.pluginCode },
                  { key: 'pluginName', label: '插件名称', children: selectedPluginDetail.pluginName },
                  { key: 'version', label: '当前版本', children: selectedPluginDetail.version },
                  { key: 'author', label: '作者', children: selectedPluginDetail.author },
                  { key: 'pluginApiVersion', label: 'API 版本', children: selectedPluginDetail.pluginApiVersion },
                  { key: 'status', label: '状态', children: selectedPluginDetail.status },
                  { key: 'healthStatus', label: '健康状态', children: selectedPluginDetail.healthStatus },
                  { key: 'tenantEnabled', label: '租户启用', children: selectedPluginDetail.tenantEnabled },
                ]}
              />
              <Card className="saas-crud-info-card" size="small" title="依赖和菜单">
                <Space direction="vertical" size={8}>
                  <div>依赖信息：{selectedPluginDetail.dependencyInfo}</div>
                  <div>菜单数量：{selectedPluginDetail.menuCount}</div>
                  <div>安装状态：{selectedPluginDetail.installStatus}</div>
                  <div>加载状态：{selectedPluginDetail.loadStatus}</div>
                </Space>
              </Card>
            </Space>
          ) : null}
        </Drawer>

        <Drawer
          className="saas-detail-drawer"
          title={selectedPlugin ? `版本列表 · ${selectedPlugin.pluginName}` : '版本列表'}
          open={versionDrawerOpen}
          onClose={() => setVersionDrawerOpen(false)}
          width={980}
          destroyOnClose
        >
          <Card loading={versionQuery.loading} bordered={false} bodyStyle={{ padding: 0 }}>
            <ProTable<PluginVersion>
              rowKey="version"
              columns={versionColumns}
              dataSource={versionList}
              search={false}
              options={false}
              toolBarRender={false}
              pagination={false}
            />
          </Card>
        </Drawer>

        <Drawer
          className="saas-detail-drawer"
          title={selectedPlugin ? `运行日志 · ${selectedPlugin.pluginName}` : '运行日志'}
          open={logDrawerOpen}
          onClose={() => setLogDrawerOpen(false)}
          width={980}
          destroyOnClose
        >
          <Card loading={logQuery.loading} bordered={false} bodyStyle={{ padding: 0 }}>
            <ProTable<PluginRuntimeLog>
              rowKey="id"
              columns={[
                { title: '时间', dataIndex: 'createdAt', width: 180 },
                { title: '操作', dataIndex: 'operationType', width: 120 },
                { title: '生命周期', dataIndex: 'lifecycleStatus', width: 140 },
                { title: '结果', dataIndex: 'resultStatus', width: 120 },
                { title: '详情', dataIndex: 'detailMessage' },
              ]}
              dataSource={runtimeLogList}
              search={false}
              options={false}
              toolBarRender={false}
              pagination={false}
            />
          </Card>
        </Drawer>

        <Drawer
          className="saas-detail-drawer"
          title="上传插件包"
          open={uploadVisible}
          onClose={() => setUploadVisible(false)}
          width={720}
          destroyOnClose
          extra={
            <Space>
              <Button onClick={() => setUploadVisible(false)}>取消</Button>
              <Button type="primary" onClick={handleUpload}>
                开始上传
              </Button>
            </Space>
          }
        >
          <Upload
            maxCount={1}
            beforeUpload={(file) => {
              if (!file.name.toLowerCase().endsWith('.zip')) {
                message.error('仅支持 zip 插件包');
                return Upload.LIST_IGNORE;
              }
              setUploadFile(file as unknown as File);
              return false;
            }}
            onRemove={() => {
              setUploadFile(null);
            }}
          >
            <Button icon={<UploadOutlined />}>选择 zip 插件包</Button>
          </Upload>
        </Drawer>
      </div>
    </PageContainer>
  );
};

export default PluginsPage;
