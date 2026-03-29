import { useCallback, useMemo, useState } from 'react';
import { Button, Card, Form, Input, Modal, Select, Space, Tag, message } from 'antd';
import { ManagementPageContainer } from '@/components/ManagementPageContainer';
import { QueryPanel } from '@/components/QueryPanel';
import { ActionBar } from '@/components/ActionBar';
import { DataTable } from '@/components/DataTable';
import { DetailDrawer } from '@/components/DetailDrawer';
import { PermissionButton } from '@/components/PermissionButton';
import { configService } from '@/services/config';
import type { SystemConfigRecord } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';

export default () => {
  const [queryForm] = Form.useForm();
  const [editorForm] = Form.useForm();
  const { isMobile } = useResponsive();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedConfig, setSelectedConfig] = useState<SystemConfigRecord | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);
  const fetchConfigs = useCallback(
    async (params: { current: number; pageSize: number }) =>
      configService.list(
        {
          pageNo: params.current,
          pageSize: params.pageSize,
          ...(query || {}),
        },
        { autoRedirectOnUnauthorized: false },
      ),
    [query, reloadTick],
  );

  const columns = useMemo(
    () => [
      { title: '配置编码', dataIndex: 'configKey' },
      { title: '配置名称', dataIndex: 'configName' },
      { title: '配置值', dataIndex: 'configValue' },
      { title: '范围', dataIndex: 'configScope' },
      {
        title: '系统内置',
        dataIndex: 'isSystem',
        render: (value: number) => <Tag color={value ? 'green' : 'default'}>{value ? '是' : '否'}</Tag>,
      },
      {
        title: '操作',
        render: (_: unknown, record: SystemConfigRecord) => (
          <Space wrap>
            <PermissionButton
              permission="system:config:view"
              onClick={() => {
                setSelectedConfig(record);
                setDetailOpen(true);
              }}
            >
              详情
            </PermissionButton>
            <PermissionButton
              permission="system:config:update"
              onClick={() => {
                setSelectedConfig(record);
                setEditingId(record.id);
                setEditorOpen(true);
              }}
            >
              编辑
            </PermissionButton>
          </Space>
        ),
      },
    ],
    [],
  );

  const submitQuery = async (values: Record<string, unknown>) => setQuery(values);
  const resetQuery = () => {
    queryForm.resetFields();
    setQuery({});
  };

  const openCreate = () => {
    setSelectedConfig(null);
    setEditingId(null);
    editorForm.resetFields();
    editorForm.setFieldsValue({ configScope: 'PLATFORM' });
    setEditorOpen(true);
  };

  const saveConfig = async () => {
    const values = await editorForm.validateFields();
    if (editingId) {
      await configService.update(editingId, values, { autoRedirectOnUnauthorized: false });
      message.success('配置已更新');
    } else {
      await configService.create(values, { autoRedirectOnUnauthorized: false });
      message.success('配置已创建');
    }
    setEditorOpen(false);
    setReloadTick((value) => value + 1);
  };

  return (
    <ManagementPageContainer title="参数配置" description="支持平台级和租户级配置查询与编辑。">
      <QueryPanel
        form={queryForm}
        onSearch={submitQuery}
        onReset={resetQuery}
        columns={isMobile ? 1 : 3}
        collapseCount={3}
        actions={<Button onClick={() => setReloadTick((value) => value + 1)}>刷新</Button>}
      >
        <Form.Item name="configKey" label="配置编码">
          <Input allowClear placeholder="输入配置编码" />
        </Form.Item>
        <Form.Item name="configName" label="配置名称">
          <Input allowClear placeholder="输入配置名称" />
        </Form.Item>
        <Form.Item name="configScope" label="范围">
          <Select
            allowClear
            options={[
              { label: '平台级', value: 'PLATFORM' },
              { label: '租户级', value: 'TENANT' },
            ]}
          />
        </Form.Item>
      </QueryPanel>

      <ActionBar
        left={<PermissionButton permission="system:config:update" type="primary" onClick={openCreate}>新增配置</PermissionButton>}
        right={<Button onClick={() => setReloadTick((value) => value + 1)}>刷新列表</Button>}
      />

      <Card bodyStyle={{ height: 520, minHeight: 0 }}>
        <DataTable<SystemConfigRecord>
          rowKey="id"
          columns={columns}
          request={fetchConfigs}
          middleScroll
          emptyText="暂无配置"
        />
      </Card>

      <Modal
        open={editorOpen}
        title={editingId ? '编辑配置' : '新增配置'}
        onCancel={() => setEditorOpen(false)}
        onOk={saveConfig}
        destroyOnClose
      >
        <Form form={editorForm} layout="vertical" initialValues={{ configScope: 'PLATFORM' }}>
          <Form.Item name="configKey" label="配置编码" rules={[{ required: true, message: '请输入配置编码' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="configName" label="配置名称" rules={[{ required: true, message: '请输入配置名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="configValue" label="配置值" rules={[{ required: true, message: '请输入配置值' }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="configScope" label="范围">
            <Select options={[{ label: '平台级', value: 'PLATFORM' }, { label: '租户级', value: 'TENANT' }]} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <DetailDrawer
        title={selectedConfig ? `配置详情 · ${selectedConfig.configName}` : '配置详情'}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        descriptionItems={
          selectedConfig
            ? [
                { key: 'configKey', label: '配置编码', children: selectedConfig.configKey },
                { key: 'configName', label: '配置名称', children: selectedConfig.configName },
                { key: 'configScope', label: '范围', children: selectedConfig.configScope },
                { key: 'configValue', label: '配置值', children: selectedConfig.configValue },
                { key: 'isSystem', label: '系统内置', children: selectedConfig.isSystem ? '是' : '否' },
                { key: 'remark', label: '备注', children: selectedConfig.remark || '-' },
              ]
            : undefined
        }
      />
    </ManagementPageContainer>
  );
};
