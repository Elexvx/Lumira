import { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button, Card, Descriptions, Drawer, Form, Input, InputNumber, Select, Space, Tag, Typography, message } from 'antd';
import { useRequest } from 'umi';
import { dictService } from '@/services/dict';
import type { DictItemRecord, DictTypeRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';

export default () => {
  const [queryForm] = Form.useForm();
  const [typeForm] = Form.useForm();
  const [itemForm] = Form.useForm();
  const { canAccess } = usePermission();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedType, setSelectedType] = useState<DictTypeRecord | null>(null);
  const [selectedItem, setSelectedItem] = useState<DictItemRecord | null>(null);
  const [typeEditorOpen, setTypeEditorOpen] = useState(false);
  const [itemEditorOpen, setItemEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editingTypeId, setEditingTypeId] = useState<number | null>(null);
  const [editingItemId, setEditingItemId] = useState<number | null>(null);
  const [reloadTick, setReloadTick] = useState(0);

  const fetchTypes = useCallback(
    async (params: { current?: number; pageSize?: number }) => {
      const result = await dictService.types(
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

  const itemListQuery = useRequest(
    async () =>
      selectedType
        ? ({ data: await dictService.items(selectedType.id, { autoRedirectOnUnauthorized: false }) } as { data: DictItemRecord[] })
        : ({ data: [] as DictItemRecord[] } as { data: DictItemRecord[] }),
    { refreshDeps: [selectedType?.id, reloadTick] },
  );

  useEffect(() => {
    if (typeEditorOpen && selectedType) {
      typeForm.setFieldsValue(selectedType);
    }
  }, [selectedType, typeEditorOpen, typeForm]);

  useEffect(() => {
    if (itemEditorOpen && selectedItem) {
      itemForm.setFieldsValue(selectedItem);
    }
  }, [itemEditorOpen, itemForm, selectedItem]);

  const columns = useMemo<ProColumns<DictTypeRecord>[]>(
    () => [
      { title: '字典编码', dataIndex: 'dictCode' },
      { title: '字典名称', dataIndex: 'dictName' },
      {
        title: '系统内置',
        dataIndex: 'isSystem',
        render: (_, record) => <Tag color={record.isSystem ? 'green' : 'default'}>{record.isSystem ? '是' : '否'}</Tag>,
      },
      {
        title: '状态',
        dataIndex: 'status',
        render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
      },
      {
        title: '操作',
        render: (_, record) => (
          <Space wrap>
            {canAccess('system:dict:view') ? (
              <Button
                onClick={() => {
                  setSelectedType(record);
                  setDetailOpen(true);
                }}
              >
                详情
              </Button>
            ) : null}
            {canAccess('system:dict:update') ? (
              <Button
                onClick={() => {
                  setSelectedType(record);
                  setEditingTypeId(record.id);
                  setTypeEditorOpen(true);
                }}
              >
                编辑
              </Button>
            ) : null}
            {canAccess('system:dict:view') ? (
              <Button
                onClick={() => {
                  setSelectedType(record);
                  setDetailOpen(true);
                }}
              >
                字典项
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

  const openCreateType = () => {
    setSelectedType(null);
    setEditingTypeId(null);
    typeForm.resetFields();
    typeForm.setFieldsValue({ status: 'ENABLED' });
    setTypeEditorOpen(true);
  };

  const saveType = async () => {
    const values = await typeForm.validateFields();
    if (editingTypeId) {
      await dictService.updateType(editingTypeId, values, { autoRedirectOnUnauthorized: false });
      message.success('字典类型已更新');
    } else {
      await dictService.createType(values, { autoRedirectOnUnauthorized: false });
      message.success('字典类型已创建');
    }
    setTypeEditorOpen(false);
    setReloadTick((value) => value + 1);
  };

  const openCreateItem = () => {
    setSelectedItem(null);
    setEditingItemId(null);
    itemForm.resetFields();
    itemForm.setFieldsValue({ sortNo: 0, status: 'ENABLED' });
    setItemEditorOpen(true);
  };

  const saveItem = async () => {
    if (!selectedType) {
      return;
    }
    const values = await itemForm.validateFields();
    if (editingItemId) {
      await dictService.updateItem(selectedType.id, editingItemId, values, { autoRedirectOnUnauthorized: false });
      message.success('字典项已更新');
    } else {
      await dictService.createItem(selectedType.id, values, { autoRedirectOnUnauthorized: false });
      message.success('字典项已创建');
    }
    setItemEditorOpen(false);
    setReloadTick((value) => value + 1);
  };

  return (
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      breadcrumbRender={false}
      title="字典管理"
      subTitle="支持字典类型、字典项和基础查询。"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Card className="saas-query-panel">
          <Form form={queryForm} layout="vertical" onFinish={submitQuery} onReset={resetQuery}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
              <Form.Item name="dictCode" label="字典编码">
                <Input allowClear placeholder="输入字典编码" />
              </Form.Item>
              <Form.Item name="dictName" label="字典名称">
                <Input allowClear placeholder="输入字典名称" />
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
              <Button onClick={() => setReloadTick((value) => value + 1)}>刷新</Button>
            </Space>
          </Form>
        </Card>

        <Card className="saas-action-bar">
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space>
              {canAccess('system:dict:create') ? (
                <Button type="primary" onClick={openCreateType}>
                  新增字典类型
                </Button>
              ) : null}
            </Space>
            <Button onClick={() => setReloadTick((value) => value + 1)}>刷新列表</Button>
          </Space>
        </Card>

        <Card className="saas-crud-table-card" bodyStyle={{ minHeight: 0 }}>
          <ProTable<DictTypeRecord>
            rowKey="id"
            columns={columns}
            request={fetchTypes}
            params={{ ...query, reloadTick }}
            search={false}
            options={false}
            toolBarRender={false}
            pagination={{ showSizeChanger: true }}
          />
        </Card>

        <Drawer
          className="saas-detail-drawer"
          title={editingTypeId ? '编辑字典类型' : '新增字典类型'}
          open={typeEditorOpen}
          onClose={() => setTypeEditorOpen(false)}
          width={720}
          destroyOnClose
          extra={
            <Space>
              <Button onClick={() => setTypeEditorOpen(false)}>取消</Button>
              <Button type="primary" onClick={saveType}>
                保存
              </Button>
            </Space>
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
              <Select options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} />
            </Form.Item>
            <Form.Item name="remark" label="备注">
              <Input.TextArea rows={3} />
            </Form.Item>
          </Form>
        </Drawer>

        <Drawer
          className="saas-detail-drawer"
          title={selectedType ? `字典详情 · ${selectedType.dictName}` : '字典详情'}
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          width={900}
          destroyOnClose
        >
          {selectedType ? (
            <Space direction="vertical" style={{ width: '100%' }} size={16}>
              <Descriptions
                bordered
                size="small"
                column={2}
                items={[
                  { key: 'dictCode', label: '字典编码', children: selectedType.dictCode },
                  { key: 'dictName', label: '字典名称', children: selectedType.dictName },
                  { key: 'status', label: '状态', children: selectedType.status },
                  { key: 'isSystem', label: '系统内置', children: selectedType.isSystem ? '是' : '否' },
                  { key: 'remark', label: '备注', children: selectedType.remark || '-' },
                ]}
              />

              <Card
                className="saas-crud-info-card"
                title={
                  <Space>
                    <Typography.Text>字典项</Typography.Text>
                    {canAccess('system:dict:update') ? (
                      <Button size="small" onClick={openCreateItem}>
                        新增项
                      </Button>
                    ) : null}
                  </Space>
                }
              >
                <ProTable<DictItemRecord>
                  rowKey="id"
                  columns={[
                    { title: '标签', dataIndex: 'itemLabel' },
                    { title: '值', dataIndex: 'itemValue' },
                    { title: '排序', dataIndex: 'sortNo' },
                    {
                      title: '状态',
                      dataIndex: 'status',
                      render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
                    },
                    {
                      title: '操作',
                      render: (_, record) =>
                        canAccess('system:dict:update') ? (
                          <Button
                            onClick={() => {
                              setSelectedItem(record);
                              setEditingItemId(record.id);
                              setItemEditorOpen(true);
                            }}
                          >
                            编辑
                          </Button>
                        ) : null,
                    },
                  ]}
                  dataSource={itemListQuery.data || []}
                  loading={itemListQuery.loading}
                  search={false}
                  options={false}
                  toolBarRender={false}
                  pagination={false}
                />
              </Card>
            </Space>
          ) : null}
        </Drawer>

        <Drawer
          className="saas-detail-drawer"
          title={editingItemId ? '编辑字典项' : '新增字典项'}
          open={itemEditorOpen}
          onClose={() => setItemEditorOpen(false)}
          width={720}
          destroyOnClose
          extra={
            <Space>
              <Button onClick={() => setItemEditorOpen(false)}>取消</Button>
              <Button type="primary" onClick={saveItem}>
                保存
              </Button>
            </Space>
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
              <Select options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} />
            </Form.Item>
            <Form.Item name="remark" label="备注">
              <Input.TextArea rows={3} />
            </Form.Item>
          </Form>
        </Drawer>
      </div>
    </PageContainer>
  );
};
