import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Modal, Select, Space, Tag, Typography, message } from 'antd';
import { useRequest } from 'umi';
import { ManagementPageContainer } from '@/components/ManagementPageContainer';
import { QueryPanel } from '@/components/QueryPanel';
import { ActionBar } from '@/components/ActionBar';
import { DataTable } from '@/components/DataTable';
import { DetailDrawer } from '@/components/DetailDrawer';
import { PermissionButton } from '@/components/PermissionButton';
import { dictService } from '@/services/dict';
import type { DictItemRecord, DictTypeRecord } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';

export default () => {
  const [typeForm] = Form.useForm();
  const [itemForm] = Form.useForm();
  const { isMobile } = useResponsive();
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
    async (params: { current: number; pageSize: number }) =>
      dictService.types(
        {
          pageNo: params.current,
          pageSize: params.pageSize,
          ...(query || {}),
        },
        { autoRedirectOnUnauthorized: false },
      ),
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

  const columns = useMemo(
    () => [
      { title: '字典编码', dataIndex: 'dictCode' },
      { title: '字典名称', dataIndex: 'dictName' },
      {
        title: '系统内置',
        dataIndex: 'isSystem',
        render: (value: number) => <Tag color={value ? 'green' : 'default'}>{value ? '是' : '否'}</Tag>,
      },
      {
        title: '状态',
        dataIndex: 'status',
        render: (value: string) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value}</Tag>,
      },
      {
        title: '操作',
        render: (_: unknown, record: DictTypeRecord) => (
          <Space wrap>
            <PermissionButton
              permission="system:dict:view"
              onClick={() => {
                setSelectedType(record);
                setDetailOpen(true);
              }}
            >
              详情
            </PermissionButton>
            <PermissionButton
              permission="system:dict:update"
              onClick={() => {
                setSelectedType(record);
                setEditingTypeId(record.id);
                setTypeEditorOpen(true);
              }}
            >
              编辑
            </PermissionButton>
            <PermissionButton
              permission="system:dict:view"
              onClick={() => {
                setSelectedType(record);
                setDetailOpen(true);
              }}
            >
              字典项
            </PermissionButton>
          </Space>
        ),
      },
    ],
    [],
  );

  const submitQuery = async (values: Record<string, unknown>) => setQuery(values);
  const resetQuery = () => {
    typeForm.resetFields();
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
    <ManagementPageContainer title="字典管理" description="支持字典类型、字典项和基础查询。">
      <QueryPanel
        form={typeForm}
        onSearch={submitQuery}
        onReset={resetQuery}
        columns={isMobile ? 1 : 3}
        collapseCount={3}
        actions={<Button onClick={() => setReloadTick((value) => value + 1)}>刷新</Button>}
      >
        <Form.Item name="dictCode" label="字典编码">
          <Input allowClear placeholder="输入字典编码" />
        </Form.Item>
        <Form.Item name="dictName" label="字典名称">
          <Input allowClear placeholder="输入字典名称" />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select allowClear options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} />
        </Form.Item>
      </QueryPanel>

      <ActionBar
        left={
          <PermissionButton permission="system:dict:create" type="primary" onClick={openCreateType}>
            新增字典类型
          </PermissionButton>
        }
        right={<Button onClick={() => setReloadTick((value) => value + 1)}>刷新列表</Button>}
      />

      <Card bodyStyle={{ height: 520, minHeight: 0 }}>
        <DataTable<DictTypeRecord>
          rowKey="id"
          columns={columns}
          request={fetchTypes}
          middleScroll
          emptyText="暂无字典类型"
        />
      </Card>

      <Modal
        open={typeEditorOpen}
        title={editingTypeId ? '编辑字典类型' : '新增字典类型'}
        onCancel={() => setTypeEditorOpen(false)}
        onOk={saveType}
        destroyOnClose
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
      </Modal>

      <DetailDrawer
        title={selectedType ? `字典详情 · ${selectedType.dictName}` : '字典详情'}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        descriptionItems={
          selectedType
            ? [
                { key: 'dictCode', label: '字典编码', children: selectedType.dictCode },
                { key: 'dictName', label: '字典名称', children: selectedType.dictName },
                { key: 'status', label: '状态', children: selectedType.status },
                { key: 'isSystem', label: '系统内置', children: selectedType.isSystem ? '是' : '否' },
                { key: 'remark', label: '备注', children: selectedType.remark || '-' },
              ]
            : undefined
        }
      >
        {selectedType ? (
          <Card
            title={
              <Space>
                <Typography.Text>字典项</Typography.Text>
                <Button size="small" onClick={openCreateItem}>
                  新增项
                </Button>
              </Space>
            }
          >
            <DataTable<DictItemRecord>
              rowKey="id"
              columns={[
                { title: '标签', dataIndex: 'itemLabel' },
                { title: '值', dataIndex: 'itemValue' },
                { title: '排序', dataIndex: 'sortNo' },
                {
                  title: '状态',
                  dataIndex: 'status',
                  render: (value: string) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value}</Tag>,
                },
                {
                  title: '操作',
                  render: (_: unknown, record: DictItemRecord) => (
                    <PermissionButton
                      permission="system:dict:update"
                      onClick={() => {
                        setSelectedItem(record);
                        setEditingItemId(record.id);
                        setItemEditorOpen(true);
                      }}
                    >
                      编辑
                    </PermissionButton>
                  ),
                },
              ]}
              dataSource={itemListQuery.data || []}
              pagination={false}
              middleScroll
              emptyText="暂无字典项"
            />
          </Card>
        ) : null}
      </DetailDrawer>

      <Modal
        open={itemEditorOpen}
        title={editingItemId ? '编辑字典项' : '新增字典项'}
        onCancel={() => setItemEditorOpen(false)}
        onOk={saveItem}
        destroyOnClose
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
      </Modal>
    </ManagementPageContainer>
  );
};
