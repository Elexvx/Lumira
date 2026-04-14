import { useMemo, useRef, useState } from 'react';
import { PageContainer, ProDescriptions, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Drawer, Form, Input, InputNumber, Select, Space, Spin, Tag, message } from 'antd';
import { dictService } from '@/services/dict';
import type { DictItemRecord, DictTypeRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';
import { buildResponsivePagination, buildResponsiveScroll, normalizeResponsiveColumns, ResponsiveActions, ResponsiveText, useResponsiveTable } from '@/components/ResponsiveTable';

const statusLabelMap: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
};

const renderStatusLabel = (status?: string | null) => statusLabelMap[status || ''] || status || '-';

const DictManagementPage = () => {
  const actionRef = useRef<ActionType>();
  const [typeForm] = Form.useForm();
  const [itemForm] = Form.useForm();
  const { canAccess } = usePermission();
  const responsive = useResponsiveTable();
  const [selectedType, setSelectedType] = useState<DictTypeRecord | null>(null);
  const [selectedItem, setSelectedItem] = useState<DictItemRecord | null>(null);
  const [typeEditorOpen, setTypeEditorOpen] = useState(false);
  const [itemEditorOpen, setItemEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editingTypeId, setEditingTypeId] = useState<number | null>(null);
  const [editingItemId, setEditingItemId] = useState<number | null>(null);
  const [typeDetail, setTypeDetail] = useState<DictTypeRecord | null>(null);
  const [items, setItems] = useState<DictItemRecord[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const openCreateType = () => {
    setSelectedType(null);
    setEditingTypeId(null);
    typeForm.resetFields();
    typeForm.setFieldsValue({ status: 'ENABLED' });
    setTypeEditorOpen(true);
  };

  const openEditType = async (record: DictTypeRecord) => {
    setSelectedType(record);
    setEditingTypeId(record.id);
    setTypeEditorOpen(true);
    const detail = await dictService.typeDetail(record.id, { autoRedirectOnUnauthorized: false });
    typeForm.setFieldsValue(detail);
  };

  const openDetail = async (record: DictTypeRecord) => {
    setSelectedType(record);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const [detail, dictItems] = await Promise.all([
        dictService.typeDetail(record.id, { autoRedirectOnUnauthorized: false }),
        dictService.items(record.id, { autoRedirectOnUnauthorized: false }),
      ]);
      setTypeDetail(detail);
      setItems(dictItems);
    } finally {
      setDetailLoading(false);
    }
  };

  const saveType = async () => {
    setSaving(true);
    try {
      const values = await typeForm.validateFields();
      if (editingTypeId) {
        await dictService.updateType(editingTypeId, values, { autoRedirectOnUnauthorized: false });
        message.success('字典类型已更新');
      } else {
        await dictService.createType(values, { autoRedirectOnUnauthorized: false });
        message.success('字典类型已创建');
      }
      setTypeEditorOpen(false);
      actionRef.current?.reload?.();
    } finally {
      setSaving(false);
    }
  };

  const openCreateItem = () => {
    setSelectedItem(null);
    setEditingItemId(null);
    itemForm.resetFields();
    itemForm.setFieldsValue({ sortNo: 0, status: 'ENABLED' });
    setItemEditorOpen(true);
  };

  const openEditItem = (record: DictItemRecord) => {
    setSelectedItem(record);
    setEditingItemId(record.id);
    itemForm.setFieldsValue(record);
    setItemEditorOpen(true);
  };

  const saveItem = async () => {
    if (!selectedType) {
      return;
    }
    setSaving(true);
    try {
      const values = await itemForm.validateFields();
      if (editingItemId) {
        await dictService.updateItem(selectedType.id, editingItemId, values, { autoRedirectOnUnauthorized: false });
        message.success('字典项已更新');
      } else {
        await dictService.createItem(selectedType.id, values, { autoRedirectOnUnauthorized: false });
        message.success('字典项已创建');
      }
      setItemEditorOpen(false);
      if (selectedType) {
        const dictItems = await dictService.items(selectedType.id, { autoRedirectOnUnauthorized: false });
        setItems(dictItems);
      }
      actionRef.current?.reload?.();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<DictTypeRecord>[] = useMemo(
    () => [
    {
      title: '字典编码',
      dataIndex: 'dictCode',
      search: true,
      importance: 1,
    },
    {
      title: '字典名称',
      dataIndex: 'dictName',
      search: true,
      importance: 1,
    },
    {
      title: '状态',
      dataIndex: 'status',
      importance: 1,
      valueEnum: {
        ENABLED: { text: '启用', status: 'Success' },
        DISABLED: { text: '停用', status: 'Default' },
      },
      render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{renderStatusLabel(record.status)}</Tag>,
    },
    {
      title: '系统内置',
      dataIndex: 'isSystem',
      hideInSearch: true,
      importance: 2,
      responsiveLevel: ['tablet', 'desktop'],
      render: (_, record) => <Tag color={record.isSystem ? 'green' : 'default'}>{record.isSystem ? '是' : '否'}</Tag>,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      hideInSearch: true,
      importance: 3,
      responsiveLevel: 'desktop',
      ellipsisText: true,
      render: (_, record) => <ResponsiveText value={record.remark || '-'} copyable={Boolean(record.remark)} />,
    },
    {
      title: '操作',
      valueType: 'option',
      importance: 0,
      desktopFixed: 'right',
      width: 180,
      render: (_, record) => (
        <ResponsiveActions
          level={responsive.level}
          items={[
            {
              key: 'view',
              label: '详情',
              hidden: !canAccess('system:dict:view'),
              onClick: () => void openDetail(record),
            },
            {
              key: 'edit',
              label: '编辑',
              hidden: !canAccess('system:dict:update'),
              onClick: () => void openEditType(record),
            },
          ]}
        />
      ),
    },
    ],
    [canAccess, responsive.level],
  );

  const responsiveColumns = useMemo(() => normalizeResponsiveColumns(columns, responsive.level), [columns, responsive.level]);
  const dictItemColumns = useMemo<ProColumns<DictItemRecord>[]>(
    () => [
      { title: '标签', dataIndex: 'itemLabel', importance: 1 },
      { title: '值', dataIndex: 'itemValue', importance: 1 },
      { title: '排序', dataIndex: 'sortNo', hideInSearch: true, importance: 2, responsiveLevel: ['tablet', 'desktop'] },
      {
        title: '状态',
        dataIndex: 'status',
        hideInSearch: true,
        importance: 1,
        render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{renderStatusLabel(record.status)}</Tag>,
      },
      {
        title: '备注',
        dataIndex: 'remark',
        hideInSearch: true,
        importance: 3,
        responsiveLevel: 'desktop',
        ellipsisText: true,
        render: (_, record) => <ResponsiveText value={record.remark || '-'} copyable={Boolean(record.remark)} />,
      },
      {
        title: '操作',
        valueType: 'option',
        importance: 0,
        desktopFixed: 'right',
        render: (_, record) => (
          <ResponsiveActions
            level={responsive.level}
            items={[
              {
                key: 'edit',
                label: '编辑',
                hidden: !canAccess('system:dict:update'),
                onClick: () => openEditItem(record),
              },
            ]}
          />
        ),
      },
    ],
    [canAccess, responsive.level],
  );

  return (
    <PageContainer title="字典管理" className="saas-management-page">
      <div className="saas-table-wrap">
        <ProTable<DictTypeRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={responsiveColumns}
          search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
          options={false}
          pagination={buildResponsivePagination({ showSizeChanger: true }, responsive)}
          scroll={buildResponsiveScroll(responsiveColumns, responsive)}
          request={async (params) => {
            const { current, pageSize, ...rest } = params;
            const result = await dictService.types(
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
            canAccess('system:dict:create') ? (
            <Button key="create" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={openCreateType}>
                新增字典类型
              </Button>
            ) : null,
            <Button key="refresh" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => actionRef.current?.reload?.()}>
              刷新
            </Button>,
          ]}
        />
      </div>

      <Drawer
        title={editingTypeId ? '编辑字典类型' : '新增字典类型'}
        open={typeEditorOpen}
        onClose={() => setTypeEditorOpen(false)}
        width={720}
        destroyOnClose
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={() => setTypeEditorOpen(false)}>取消</Button>
              <Button type="primary" loading={saving} onClick={() => void saveType()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={typeForm} layout="vertical" initialValues={{ status: 'ENABLED' }}>
          <Form.Item name="dictCode" label="字典编码" rules={[{ required: true, message: '请输入字典编码' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="dictName" label="字典名称" rules={[{ required: true, message: '请输入字典名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { label: '启用', value: 'ENABLED' },
                { label: '停用', value: 'DISABLED' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={selectedType ? `字典详情 · ${selectedType.dictName}` : '字典详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setTypeDetail(null);
          setItems([]);
        }}
        width={900}
        destroyOnClose
      >
        {detailLoading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : typeDetail ? (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <ProDescriptions<DictTypeRecord>
              column={responsive.isMobile ? 1 : 2}
              dataSource={typeDetail}
              columns={[
                { title: '字典编码', dataIndex: 'dictCode' },
                { title: '字典名称', dataIndex: 'dictName' },
                { title: '状态', dataIndex: 'status', renderText: (value) => renderStatusLabel(String(value)) },
                {
                  title: '系统内置',
                  dataIndex: 'isSystem',
                  renderText: (value) => (value ? '是' : '否'),
                },
                { title: '备注', dataIndex: 'remark', renderText: (value) => value || '-' },
              ]}
            />

            <div className="saas-table-wrap">
              <ProTable<DictItemRecord>
                rowKey="id"
                columns={normalizeResponsiveColumns(dictItemColumns, responsive.level)}
                dataSource={items}
                search={false}
                options={false}
                pagination={false}
                scroll={buildResponsiveScroll(dictItemColumns, responsive)}
                toolBarRender={() => [
                  canAccess('system:dict:update') ? (
                    <Button key="create" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={openCreateItem}>
                      新增项
                    </Button>
                  ) : null,
                ]}
              />
            </div>
          </Space>
        ) : null}
      </Drawer>

      <Drawer
        title={editingItemId ? '编辑字典项' : '新增字典项'}
        open={itemEditorOpen}
        onClose={() => setItemEditorOpen(false)}
        width={720}
        destroyOnClose
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={() => setItemEditorOpen(false)}>取消</Button>
              <Button type="primary" loading={saving} onClick={() => void saveItem()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={itemForm} layout="vertical" initialValues={{ sortNo: 0, status: 'ENABLED' }}>
          <Form.Item name="itemLabel" label="标签" rules={[{ required: true, message: '请输入标签' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="itemValue" label="值" rules={[{ required: true, message: '请输入值' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="sortNo" label="排序">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { label: '启用', value: 'ENABLED' },
                { label: '停用', value: 'DISABLED' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Drawer>
    </PageContainer>
  );
};

export default DictManagementPage;
