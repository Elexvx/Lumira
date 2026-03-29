import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, Modal, Select, Space, Tag, Upload, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { UploadOutlined } from '@ant-design/icons';
import { useRequest } from 'umi';
import { ManagementPageContainer } from '@/components/ManagementPageContainer';
import { QueryPanel } from '@/components/QueryPanel';
import { ActionBar } from '@/components/ActionBar';
import { DataTable } from '@/components/DataTable';
import { DetailDrawer } from '@/components/DetailDrawer';
import { PermissionButton } from '@/components/PermissionButton';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { pluginService } from '@/services/plugin';
import type { PluginDefinition, PluginRuntimeLog, PluginVersion } from '@/types/api';

const PluginsPage = () => {
  const [queryForm] = Form.useForm();
  const { initialState, setInitialState } = useInitialStateModel();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedPlugin, setSelectedPlugin] = useState<PluginDefinition | null>(null);
  const [selectedVersion, setSelectedVersion] = useState<string | null>(null);
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
  }) as { data: import('@/types/api').TenantPlugin[] }, {
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

  const definitionColumns = useMemo<ColumnsType<PluginDefinition>>(
    () => [
      { title: '插件编码', dataIndex: 'pluginCode' },
      { title: '名称', dataIndex: 'pluginName' },
      { title: '作者', dataIndex: 'author' },
      { title: 'API 版本', dataIndex: 'pluginApiVersion' },
      {
        title: '状态',
        dataIndex: 'status',
        render: (value: string) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value}</Tag>,
      },
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => (
          <Space wrap>
            <PermissionButton
              permission="plugin:management:view"
              onClick={() => {
                setSelectedPlugin(record);
                setSelectedVersion(null);
                setDetailDrawerOpen(true);
              }}
            >
              详情
            </PermissionButton>
            <PermissionButton
              permission="plugin:management:view"
              onClick={() => {
                setSelectedPlugin(record);
                setVersionDrawerOpen(true);
              }}
            >
              版本
            </PermissionButton>
            <PermissionButton
              permission="plugin:management:logs"
              onClick={() => {
                setSelectedPlugin(record);
                setLogDrawerOpen(true);
              }}
            >
              日志
            </PermissionButton>
          </Space>
        ),
      },
    ],
    [],
  );

  const versionColumns = useMemo<ColumnsType<PluginVersion>>(
    () => [
      { title: '版本', dataIndex: 'version' },
      { title: '安装状态', dataIndex: 'installStatus' },
      { title: '加载状态', dataIndex: 'loadStatus' },
      { title: '健康状态', dataIndex: 'healthStatus' },
      {
        title: '激活',
        dataIndex: 'isActive',
        render: (value: number) => <Tag color={value === 1 ? 'green' : 'default'}>{value === 1 ? '是' : '否'}</Tag>,
      },
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => (
          <Space wrap>
            <PermissionButton permission="plugin:management:install" onClick={() => confirmInstall(record.pluginCode, record.version)}>
              安装
            </PermissionButton>
            <PermissionButton permission="plugin:management:upgrade" onClick={() => confirmActivate(record.pluginCode, record.version)}>
              激活
            </PermissionButton>
            <PermissionButton permission="plugin:management:enable" onClick={() => confirmEnable(record.pluginCode, record.version)}>
              启用
            </PermissionButton>
            <PermissionButton permission="plugin:management:disable" onClick={() => confirmDisable(record.pluginCode)}>
              停用
            </PermissionButton>
            <PermissionButton permission="plugin:management:rollback" onClick={() => confirmRollback(record.pluginCode, record.version)}>
              回滚
            </PermissionButton>
          </Space>
        ),
      },
    ],
    [initialState?.currentTenant?.tenantId],
  );

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

  return (
    <ManagementPageContainer title="插件管理" description="插件列表、版本、日志和运行态统一纳入页面规范。">
      <QueryPanel
        form={queryForm}
        onSearch={(values) => setQuery(values)}
        onReset={() => {
          queryForm.resetFields();
          setQuery({});
        }}
        columns={3}
        collapseCount={3}
        actions={
          <>
            <Button onClick={() => definitionQuery.refresh()}>刷新</Button>
            <PermissionButton permission="plugin:management:upload" type="primary" onClick={() => setUploadVisible(true)}>
              上传插件
            </PermissionButton>
          </>
        }
      >
        <Form.Item name="keyword" label="关键词">
          <Input allowClear placeholder="插件名称或编码" />
        </Form.Item>
        <Form.Item name="pluginCode" label="插件编码">
          <Input allowClear placeholder="输入插件编码" />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select allowClear options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} />
        </Form.Item>
      </QueryPanel>

      <ActionBar
        left={<Space><Tag color="blue">当前租户：{initialState?.currentTenant?.tenantName || '未选择'}</Tag></Space>}
        right={<Button onClick={() => definitionQuery.refresh()}>重新加载</Button>}
      />

      <Card bodyStyle={{ height: 520, minHeight: 0 }}>
        <DataTable<PluginDefinition>
          rowKey="pluginCode"
          columns={definitionColumns}
          dataSource={filteredDefinitions}
          pagination={false}
          loading={definitionQuery.loading}
          middleScroll
          emptyText="暂无插件定义"
        />
      </Card>

      <DetailDrawer
        title={selectedPlugin ? `插件详情 · ${selectedPlugin.pluginName}` : '插件详情'}
        open={detailDrawerOpen}
        onClose={() => setDetailDrawerOpen(false)}
        descriptionItems={
          selectedPluginDetail
            ? [
                { key: 'pluginCode', label: '插件编码', children: selectedPluginDetail.pluginCode },
                { key: 'pluginName', label: '插件名称', children: selectedPluginDetail.pluginName },
                { key: 'version', label: '当前版本', children: selectedPluginDetail.version },
                { key: 'author', label: '作者', children: selectedPluginDetail.author },
                { key: 'pluginApiVersion', label: 'API 版本', children: selectedPluginDetail.pluginApiVersion },
                { key: 'status', label: '状态', children: selectedPluginDetail.status },
                { key: 'healthStatus', label: '健康状态', children: selectedPluginDetail.healthStatus },
                { key: 'tenantEnabled', label: '租户启用', children: selectedPluginDetail.tenantEnabled },
              ]
            : undefined
        }
      >
        {selectedPluginDetail ? (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Card size="small" title="依赖和菜单">
              <Space direction="vertical" size={8}>
                <div>依赖信息：{selectedPluginDetail.dependencyInfo}</div>
                <div>菜单数量：{selectedPluginDetail.menuCount}</div>
                <div>安装状态：{selectedPluginDetail.installStatus}</div>
                <div>加载状态：{selectedPluginDetail.loadStatus}</div>
              </Space>
            </Card>
          </Space>
        ) : null}
      </DetailDrawer>

      <DetailDrawer
        title={selectedPlugin ? `版本列表 · ${selectedPlugin.pluginName}` : '版本列表'}
        open={versionDrawerOpen}
        onClose={() => setVersionDrawerOpen(false)}
        width={960}
        loading={versionQuery.loading}
      >
        <DataTable<PluginVersion>
          rowKey="version"
          columns={versionColumns}
          dataSource={versionList}
          pagination={false}
          middleScroll
          emptyText="暂无版本数据"
        />
      </DetailDrawer>

      <DetailDrawer
        title={selectedPlugin ? `运行日志 · ${selectedPlugin.pluginName}` : '运行日志'}
        open={logDrawerOpen}
        onClose={() => setLogDrawerOpen(false)}
        width={960}
        loading={logQuery.loading}
      >
        <DataTable<PluginRuntimeLog>
          rowKey="id"
          columns={[
            { title: '时间', dataIndex: 'createdAt', width: 180 },
            { title: '操作', dataIndex: 'operationType', width: 120 },
            { title: '生命周期', dataIndex: 'lifecycleStatus', width: 140 },
            { title: '结果', dataIndex: 'resultStatus', width: 120 },
            { title: '详情', dataIndex: 'detailMessage' },
          ]}
          dataSource={runtimeLogList}
          pagination={false}
          middleScroll
          emptyText="暂无插件日志"
        />
      </DetailDrawer>

      <Modal
        open={uploadVisible}
        title="上传插件包"
        onCancel={() => setUploadVisible(false)}
        onOk={handleUpload}
        okText="开始上传"
        destroyOnClose
      >
        <Upload
          maxCount={1}
          beforeUpload={(file) => {
            if (!file.name.toLowerCase().endsWith('.zip')) {
              message.error('仅支持 zip 插件包');
              return Upload.LIST_IGNORE;
            }
            setUploadFile(file);
            return false;
          }}
          onRemove={() => {
            setUploadFile(null);
          }}
        >
          <Button icon={<UploadOutlined />}>选择 zip 插件包</Button>
        </Upload>
      </Modal>
    </ManagementPageContainer>
  );
};

export default PluginsPage;
