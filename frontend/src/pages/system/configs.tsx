import { useRef, useState } from 'react';
import { PageContainer, ProDescriptions, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Drawer, Form, Input, Select, Space, Spin, Tag, message } from 'antd';
import { configService } from '@/services/config';
import type { SystemConfigRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';

const ConfigManagementPage = () => {
  const actionRef = useRef<ActionType>();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
  const [selectedConfig, setSelectedConfig] = useState<SystemConfigRecord | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const openCreate = () => {
    setSelectedConfig(null);
    setEditingId(null);
    editorForm.resetFields();
    editorForm.setFieldsValue({ configScope: 'PLATFORM' });
    setEditorOpen(true);
  };

  const openEdit = async (record: SystemConfigRecord) => {
    setSelectedConfig(record);
    setEditingId(record.id);
    setEditorOpen(true);
    const detail = await configService.detail(record.id, { autoRedirectOnUnauthorized: false });
    editorForm.setFieldsValue(detail);
  };

  const openDetail = async (record: SystemConfigRecord) => {
    setSelectedConfig(record);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const detail = await configService.detail(record.id, { autoRedirectOnUnauthorized: false });
      setSelectedConfig(detail);
    } finally {
      setDetailLoading(false);
    }
  };

  const saveConfig = async () => {
    setSaving(true);
    try {
      const values = await editorForm.validateFields();
      if (editingId) {
        await configService.update(editingId, values, { autoRedirectOnUnauthorized: false });
        message.success('配置已更新');
      } else {
        await configService.create(values, { autoRedirectOnUnauthorized: false });
        message.success('配置已创建');
      }
      setEditorOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<SystemConfigRecord>[] = [
    {
      title: '配置编码',
      dataIndex: 'configKey',
      search: true,
    },
    {
      title: '配置名称',
      dataIndex: 'configName',
      search: true,
    },
    {
      title: '配置值',
      dataIndex: 'configValue',
      hideInSearch: true,
    },
    {
      title: '范围',
      dataIndex: 'configScope',
      valueEnum: {
        PLATFORM: { text: '平台级' },
        TENANT: { text: '租户级' },
      },
      search: {
        transform: (value) => ({ configScope: value }),
      },
    },
    {
      title: '系统内置',
      dataIndex: 'isSystem',
      hideInSearch: true,
      render: (_, record) => <Tag color={record.isSystem ? 'green' : 'default'}>{record.isSystem ? '是' : '否'}</Tag>,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      hideInSearch: true,
      render: (_, record) => record.remark || '-',
    },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 160,
      render: (_, record) => (
        <Space size={0}>
          {canAccess('system:config:view') ? (
            <Button type="link" size="small" onClick={() => void openDetail(record)}>
              详情
            </Button>
          ) : null}
          {canAccess('system:config:update') ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              编辑
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <PageContainer
      title="参数配置"
      extra={
        <Space>
          {canAccess('system:config:update') ? (
            <Button type="primary" onClick={openCreate}>
              新增配置
            </Button>
          ) : null}
        </Space>
      }
    >
      <ProTable<SystemConfigRecord>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        search={{ labelWidth: 'auto' }}
        options={false}
        pagination={{ showSizeChanger: true }}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          const result = await configService.list(
            {
              pageNo: current,
              pageSize,
              ...rest,
            },
            { autoRedirectOnUnauthorized: false },
          );
          return {
            data: result.records,
            success: true,
            total: result.total,
          };
        }}
        toolBarRender={() => [
          canAccess('system:config:update') ? (
            <Button key="create" type="primary" onClick={openCreate}>
              新增配置
            </Button>
          ) : null,
          <Button key="refresh" onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ]}
      />

      <Drawer
        title={editingId ? '编辑配置' : '新增配置'}
        open={editorOpen}
        onClose={() => setEditorOpen(false)}
        width={720}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setEditorOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void saveConfig()}>
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
            <Select
              options={[
                { label: '平台级', value: 'PLATFORM' },
                { label: '租户级', value: 'TENANT' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={selectedConfig ? `配置详情 · ${selectedConfig.configName}` : '配置详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setSelectedConfig(null);
        }}
        width={720}
        destroyOnClose
      >
        {detailLoading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : selectedConfig ? (
          <ProDescriptions<SystemConfigRecord>
            column={2}
            dataSource={selectedConfig}
            columns={[
              { title: '配置编码', dataIndex: 'configKey' },
              { title: '配置名称', dataIndex: 'configName' },
              { title: '范围', dataIndex: 'configScope' },
              { title: '配置值', dataIndex: 'configValue' },
              { title: '系统内置', dataIndex: 'isSystem', renderText: (value) => (value ? '是' : '否') },
              { title: '备注', dataIndex: 'remark', renderText: (value) => value || '-' },
            ]}
          />
        ) : null}
      </Drawer>
    </PageContainer>
  );
};

export default ConfigManagementPage;
