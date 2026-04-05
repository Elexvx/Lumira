import { useCallback, useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button, Card, Descriptions, Drawer, Form, Input, Select, Space, Tag, message } from 'antd';
import { configService } from '@/services/config';
import type { SystemConfigRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';

export default () => {
  const [queryForm] = Form.useForm();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedConfig, setSelectedConfig] = useState<SystemConfigRecord | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);

  const fetchConfigs = useCallback(
    async (params: { current?: number; pageSize?: number }) => {
      const result = await configService.list(
        {
          pageNo: params.current,
          pageSize: params.pageSize,
          ...(query || {}),
        },
        { autoRedirectOnUnauthorized: false },
      );
      return { data: result.records, success: true, total: result.total };
    },
    [query, reloadTick],
  );

  const columns = useMemo<ProColumns<SystemConfigRecord>[]>(
    () => [
      { title: '配置编码', dataIndex: 'configKey' },
      { title: '配置名称', dataIndex: 'configName' },
      { title: '配置值', dataIndex: 'configValue' },
      { title: '范围', dataIndex: 'configScope' },
      {
        title: '系统内置',
        dataIndex: 'isSystem',
        render: (_, record) => <Tag color={record.isSystem ? 'green' : 'default'}>{record.isSystem ? '是' : '否'}</Tag>,
      },
      {
        title: '操作',
        render: (_, record) => (
          <Space wrap>
            {canAccess('system:config:view') ? (
              <Button
                onClick={() => {
                  setSelectedConfig(record);
                  setDetailOpen(true);
                }}
              >
                详情
              </Button>
            ) : null}
            {canAccess('system:config:update') ? (
              <Button
                onClick={() => {
                  setSelectedConfig(record);
                  setEditingId(record.id);
                  setEditorOpen(true);
                }}
              >
                编辑
              </Button>
            ) : null}
          </Space>
        ),
      },
    ],
    [canAccess],
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
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      title="参数配置"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Card className="saas-query-panel">
          <Form form={queryForm} layout="vertical" onFinish={submitQuery} onReset={resetQuery}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
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
            </div>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button htmlType="reset">重置</Button>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={() => setReloadTick((value) => value + 1)}>刷新</Button>
            </Space>
          </Form>
        </Card>

        <Card className="saas-action-bar">
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space>
              {canAccess('system:config:update') ? (
                <Button type="primary" onClick={openCreate}>新增配置</Button>
              ) : null}
            </Space>
            <Button onClick={() => setReloadTick((value) => value + 1)}>刷新列表</Button>
          </Space>
        </Card>

        <Card className="saas-crud-table-card" bodyStyle={{ minHeight: 0 }}>
          <ProTable<SystemConfigRecord>
            rowKey="id"
            columns={columns}
            request={fetchConfigs}
            params={{ ...query, reloadTick }}
            search={false}
            options={false}
            toolBarRender={false}
            pagination={{ showSizeChanger: true }}
          />
        </Card>

        <Drawer
          className="saas-detail-drawer"
          title={editingId ? '编辑配置' : '新增配置'}
          open={editorOpen}
          onClose={() => setEditorOpen(false)}
          width={720}
          destroyOnClose
          extra={
            <Space>
              <Button onClick={() => setEditorOpen(false)}>取消</Button>
              <Button type="primary" onClick={saveConfig}>
                保存
              </Button>
            </Space>
          }
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
        </Drawer>

        <Drawer
          className="saas-detail-drawer"
          title={selectedConfig ? `配置详情 · ${selectedConfig.configName}` : '配置详情'}
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          width={720}
          destroyOnClose
        >
          {selectedConfig ? (
            <Descriptions
              bordered
              size="small"
              column={2}
              items={[
                { key: 'configKey', label: '配置编码', children: selectedConfig.configKey },
                { key: 'configName', label: '配置名称', children: selectedConfig.configName },
                { key: 'configScope', label: '范围', children: selectedConfig.configScope },
                { key: 'configValue', label: '配置值', children: selectedConfig.configValue },
                { key: 'isSystem', label: '系统内置', children: selectedConfig.isSystem ? '是' : '否' },
                { key: 'remark', label: '备注', children: selectedConfig.remark || '-' },
              ]}
            />
          ) : null}
        </Drawer>
      </div>
    </PageContainer>
  );
};
