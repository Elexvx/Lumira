import { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { UploadOutlined } from '@ant-design/icons';
import { useRequest } from 'umi';
import { PermissionButton } from '@/components/PermissionButton';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { pluginService } from '@/services/plugin';
import type { PluginDefinition, PluginRuntimeLog, PluginVersion } from '@/types/api';

const { Paragraph, Text } = Typography;

const PluginsPage = () => {
  const { initialState, setInitialState } = useInitialStateModel();
  const [selectedPlugin, setSelectedPlugin] = useState<PluginDefinition>();
  const [selectedVersion, setSelectedVersion] = useState<string>();
  const [uploadFile, setUploadFile] = useState<File>();
  const [versionDrawerOpen, setVersionDrawerOpen] = useState(false);
  const [logDrawerOpen, setLogDrawerOpen] = useState(false);
  const [uploadVisible, setUploadVisible] = useState(false);

  const definitionQuery = useRequest(() => pluginService.definitions(), {
    refreshDeps: [initialState?.currentTenant?.tenantId],
  });

  const versionQuery = useRequest(
    () => (selectedPlugin ? pluginService.versions(selectedPlugin.pluginCode) : Promise.resolve([] as PluginVersion[])),
    {
      manual: false,
      refreshDeps: [selectedPlugin?.pluginCode],
    },
  );

  const logQuery = useRequest(
    () => (selectedPlugin ? pluginService.runtimeLogs(selectedPlugin.pluginCode) : Promise.resolve([] as PluginRuntimeLog[])),
    {
      manual: false,
      refreshDeps: [selectedPlugin?.pluginCode, logDrawerOpen],
    },
  );

  useEffect(() => {
    if (!selectedPlugin && definitionQuery.data?.length) {
      setSelectedPlugin(definitionQuery.data[0]);
    }
  }, [definitionQuery.data, selectedPlugin]);

  const definitionColumns = useMemo<ColumnsType<PluginDefinition>>(
    () => [
      {
        title: '插件编码',
        dataIndex: 'pluginCode',
      },
      {
        title: '名称',
        dataIndex: 'pluginName',
      },
      {
        title: '类型',
        dataIndex: 'pluginType',
      },
      {
        title: '作者',
        dataIndex: 'author',
      },
      {
        title: '状态',
        dataIndex: 'status',
        render: (value: string) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value}</Tag>,
      },
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => (
          <Space>
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
            <PermissionButton
              permission="plugin:management:install"
              onClick={() => handleInstall(record.pluginCode, record.version)}
            >
              安装
            </PermissionButton>
            <PermissionButton
              permission="plugin:management:upgrade"
              onClick={() => handleUpgrade(record.pluginCode, record.version)}
            >
              激活
            </PermissionButton>
            <PermissionButton
              permission="plugin:management:enable"
              onClick={() => handleEnable(record.pluginCode, record.version)}
            >
              启用
            </PermissionButton>
            <PermissionButton
              permission="plugin:management:disable"
              onClick={() => handleDisable(record.pluginCode)}
            >
              停用
            </PermissionButton>
            <PermissionButton
              permission="plugin:management:rollback"
              onClick={() => handleRollback(record.pluginCode, record.version)}
            >
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

  const handleInstall = async (pluginCode: string, version: string) => {
    await pluginService.install({ pluginCode, version });
    await versionQuery.refresh();
    await definitionQuery.refresh();
    message.success('插件安装完成');
  };

  const handleUpgrade = async (pluginCode: string, version: string) => {
    await pluginService.upgrade({ pluginCode, version });
    await versionQuery.refresh();
    await refreshBootstrap();
    message.success('插件激活版本已切换');
  };

  const handleEnable = async (pluginCode: string, version: string) => {
    const tenantId = initialState?.currentTenant?.tenantId;
    if (!tenantId) {
      message.error('当前未选择租户');
      return;
    }
    await pluginService.enable({ tenantId, pluginCode, version });
    await refreshBootstrap();
    message.success('插件已启用');
  };

  const handleDisable = async (pluginCode: string) => {
    const tenantId = initialState?.currentTenant?.tenantId;
    if (!tenantId) {
      message.error('当前未选择租户');
      return;
    }
    await pluginService.disable({ tenantId, pluginCode });
    await refreshBootstrap();
    message.success('插件已停用');
  };

  const handleRollback = async (pluginCode: string, version: string) => {
    await pluginService.rollback({ pluginCode, targetVersion: version });
    await versionQuery.refresh();
    await refreshBootstrap();
    message.success('插件已回滚');
  };

  const handleUpload = async () => {
    if (!uploadFile) {
      message.error('请先选择插件包');
      return;
    }
    const result = await pluginService.upload(uploadFile);
    setSelectedPlugin(
      definitionQuery.data?.find((item) => item.pluginCode === result.pluginCode) || {
        pluginCode: result.pluginCode,
        pluginName: result.pluginName,
        pluginType: 'PLUGIN',
        pluginApiVersion: '1.0.0',
        status: 'ENABLED',
        builtinFlag: 0,
        sortNo: 0,
      },
    );
    setSelectedVersion(result.version);
    setUploadVisible(false);
    setUploadFile(undefined);
    await definitionQuery.refresh();
    message.success('插件上传并完成校验');
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, height: 'calc(100vh - 112px)' }}>
      <Card
        bodyStyle={{ padding: 16 }}
        extra={
          <Space>
            <Text type="secondary">当前租户：{initialState?.currentTenant?.tenantName || '未选择'}</Text>
            <PermissionButton permission="plugin:management:upload" onClick={() => setUploadVisible(true)}>
              上传插件
            </PermissionButton>
          </Space>
        }
      >
        <Form layout="inline">
          <Form.Item label="插件名称">
            <Input.Search
              allowClear
              placeholder="按名称筛选"
              onSearch={(value) => {
                const keyword = value.trim().toLowerCase();
                const next = definitionQuery.data?.find((item) => item.pluginName.toLowerCase().includes(keyword));
                if (next) {
                  setSelectedPlugin(next);
                }
              }}
            />
          </Form.Item>
          <Form.Item label="插件编码">
            <Select
              allowClear
              style={{ width: 240 }}
              options={(definitionQuery.data || []).map((item) => ({
                label: item.pluginName,
                value: item.pluginCode,
              }))}
              value={selectedPlugin?.pluginCode}
              onChange={(value) => setSelectedPlugin(definitionQuery.data?.find((item) => item.pluginCode === value))}
            />
          </Form.Item>
        </Form>
      </Card>

      <Card bodyStyle={{ padding: 0, height: '100%' }}>
        <div style={{ height: '100%', overflow: 'auto' }}>
          <Table
            rowKey="pluginCode"
            loading={definitionQuery.loading}
            columns={definitionColumns}
            dataSource={definitionQuery.data || []}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无插件定义" /> }}
          />
        </div>
      </Card>

      <Drawer
        open={versionDrawerOpen}
        title={`版本列表 · ${selectedPlugin?.pluginName || ''}`}
        width={960}
        onClose={() => setVersionDrawerOpen(false)}
      >
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="插件编码">{selectedPlugin?.pluginCode}</Descriptions.Item>
          <Descriptions.Item label="API 版本">{selectedPlugin?.pluginApiVersion}</Descriptions.Item>
          <Descriptions.Item label="作者">{selectedPlugin?.author || '-'}</Descriptions.Item>
          <Descriptions.Item label="状态">{selectedPlugin?.status || '-'}</Descriptions.Item>
          <Descriptions.Item label="描述" span={2}>
            {selectedPlugin?.description || '-'}
          </Descriptions.Item>
        </Descriptions>
        <Table
          style={{ marginTop: 16 }}
          rowKey="version"
          loading={versionQuery.loading}
          columns={versionColumns}
          dataSource={versionQuery.data || []}
          pagination={false}
        />
        <Card title="最近一次校验结果" style={{ marginTop: 16 }}>
          <Paragraph style={{ marginBottom: 0 }}>
            {versionQuery.data?.find((item) => item.version === selectedVersion)?.validationReportJson || '请选择一个版本查看'}
          </Paragraph>
        </Card>
      </Drawer>

      <Drawer
        open={logDrawerOpen}
        title={`运行日志 · ${selectedPlugin?.pluginName || ''}`}
        width={900}
        onClose={() => setLogDrawerOpen(false)}
      >
        <Table
          rowKey="id"
          loading={logQuery.loading}
          pagination={false}
          dataSource={logQuery.data || []}
          columns={[
            { title: '时间', dataIndex: 'createdAt', width: 180 },
            { title: '操作', dataIndex: 'operationType', width: 120 },
            { title: '生命周期', dataIndex: 'lifecycleStatus', width: 140 },
            { title: '结果', dataIndex: 'resultStatus', width: 120 },
            { title: '详情', dataIndex: 'detailMessage' },
          ]}
        />
      </Drawer>

      <Modal
        open={uploadVisible}
        title="上传插件包"
        onCancel={() => setUploadVisible(false)}
        onOk={handleUpload}
        okText="开始上传"
      >
        <Upload
          maxCount={1}
          beforeUpload={(file) => {
            setUploadFile(file);
            return false;
          }}
          onRemove={() => {
            setUploadFile(undefined);
          }}
        >
          <Button icon={<UploadOutlined />}>选择 zip 插件包</Button>
        </Upload>
      </Modal>
    </div>
  );
};

export default PluginsPage;
