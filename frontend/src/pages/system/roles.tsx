import { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button, Card, Checkbox, Descriptions, Drawer, Form, Input, Select, Space, Tag, message } from 'antd';
import { useRequest } from 'umi';
import { iamService } from '@/services/iam';
import type { PermissionRecord, RoleDetail, RoleRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';

export default () => {
  const [queryForm] = Form.useForm();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedRole, setSelectedRole] = useState<RoleRecord | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);

  const permissionQuery = useRequest(async () => ({
    data: await iamService.permissions({ autoRedirectOnUnauthorized: false }),
  }) as { data: PermissionRecord[] });
  const roleDetailQuery = useRequest(
    async () =>
      selectedRole
        ? ({ data: await iamService.roleDetail(selectedRole.id, { autoRedirectOnUnauthorized: false }) } as { data: RoleDetail | null })
        : ({ data: null } as { data: RoleDetail | null }),
    { refreshDeps: [selectedRole?.id, reloadTick] },
  );

  const permissionOptions = useMemo(
    () =>
      (permissionQuery.data || []).map((item) => ({
        label: item.permissionName,
        value: item.permissionKey,
      })),
    [permissionQuery.data],
  );

  useEffect(() => {
    if (editorOpen && roleDetailQuery.data) {
      editorForm.setFieldsValue({
        ...roleDetailQuery.data,
        permissionKeys: roleDetailQuery.data.permissionKeys || [],
      });
    }
  }, [editorForm, editorOpen, roleDetailQuery.data]);

  const columns = useMemo<ProColumns<RoleRecord>[]>(
    () => [
      { title: '角色编码', dataIndex: 'roleCode' },
      { title: '角色名称', dataIndex: 'roleName' },
      { title: '角色类型', dataIndex: 'roleType' },
      { title: '权限数', dataIndex: 'permissionCount', render: (_, record) => record.permissionCount ?? 0 },
      { title: '用户数', dataIndex: 'userCount', render: (_, record) => record.userCount ?? 0 },
      {
        title: '操作',
        render: (_, record) => (
          <Space wrap>
            {canAccess('system:role:view') ? (
              <Button
                onClick={() => {
                  setSelectedRole(record);
                  setDetailOpen(true);
                }}
              >
                详情
              </Button>
            ) : null}
            {canAccess('system:role:update') ? (
              <Button
                onClick={() => {
                  setSelectedRole(record);
                  setEditingId(record.id);
                  setEditorOpen(true);
                }}
              >
                编辑
              </Button>
            ) : null}
            {canAccess('system:role:permissions') ? (
              <Button
                onClick={() => {
                  setSelectedRole(record);
                  setEditingId(record.id);
                  setEditorOpen(true);
                }}
              >
                权限分配
              </Button>
            ) : null}
          </Space>
        ),
      },
    ],
    [canAccess],
  );

  const submitQuery = async (values: Record<string, unknown>) => {
    setQuery(values);
  };

  const resetQuery = () => {
    queryForm.resetFields();
    setQuery({});
  };

  const fetchRoles = useCallback(
    async (params: { current?: number; pageSize?: number }) => {
      const result = await iamService.roles(
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

  const openCreate = () => {
    setSelectedRole(null);
    setEditingId(null);
    editorForm.resetFields();
    editorForm.setFieldsValue({ roleType: 'CUSTOM', permissionKeys: [] });
    setEditorOpen(true);
  };

  const saveRole = async () => {
    const values = await editorForm.validateFields();
    const payload = {
      ...values,
      permissionKeys: values.permissionKeys || [],
    };
    if (editingId) {
      await iamService.updateRole(editingId, payload, { autoRedirectOnUnauthorized: false });
      message.success('角色已更新');
    } else {
      await iamService.createRole(payload, { autoRedirectOnUnauthorized: false });
      message.success('角色已创建');
    }
    setEditorOpen(false);
    setReloadTick((value) => value + 1);
  };

  const detail = roleDetailQuery.data as RoleDetail | undefined;

  return (
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      breadcrumbRender={false}
      title="角色管理"
      subTitle="支持角色查询、新增、编辑、查看与权限分配。"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Card className="saas-query-panel">
          <Form form={queryForm} layout="vertical" onFinish={submitQuery} onReset={resetQuery}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
              <Form.Item name="roleCode" label="角色编码">
                <Input allowClear placeholder="输入角色编码" />
              </Form.Item>
              <Form.Item name="roleName" label="角色名称">
                <Input allowClear placeholder="输入角色名称" />
              </Form.Item>
              <Form.Item name="roleType" label="角色类型">
                <Select
                  allowClear
                  options={[
                    { label: '系统角色', value: 'SYSTEM' },
                    { label: '自定义角色', value: 'CUSTOM' },
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
              {canAccess('system:role:create') ? (
                <Button type="primary" onClick={openCreate}>
                  新增角色
                </Button>
              ) : null}
            </Space>
            <Button onClick={() => setReloadTick((value) => value + 1)}>刷新列表</Button>
          </Space>
        </Card>

        <Card className="saas-crud-table-card" bodyStyle={{ minHeight: 0 }}>
          <ProTable<RoleRecord>
            rowKey="id"
            columns={columns}
            request={fetchRoles}
            params={{ ...query, reloadTick }}
            search={false}
            options={false}
            toolBarRender={false}
            pagination={{ showSizeChanger: true }}
          />
        </Card>

        <Drawer
          className="saas-detail-drawer"
          title={editingId ? '编辑角色 / 分配权限' : '新增角色'}
          open={editorOpen}
          onClose={() => setEditorOpen(false)}
          width={720}
          destroyOnClose
          extra={
            <Space>
              <Button onClick={() => setEditorOpen(false)}>取消</Button>
              <Button type="primary" onClick={saveRole}>
                保存
              </Button>
            </Space>
          }
        >
          <Form form={editorForm} layout="vertical" initialValues={{ roleType: 'CUSTOM', permissionKeys: [] }}>
            <Form.Item name="roleCode" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
              <Input />
            </Form.Item>
            <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
              <Input />
            </Form.Item>
            <Form.Item name="roleType" label="角色类型" rules={[{ required: true, message: '请选择角色类型' }]}>
              <Select options={[{ label: '系统角色', value: 'SYSTEM' }, { label: '自定义角色', value: 'CUSTOM' }]} />
            </Form.Item>
            <Form.Item name="permissionKeys" label="权限">
              <Checkbox.Group options={permissionOptions} />
            </Form.Item>
          </Form>
        </Drawer>

        <Drawer
          className="saas-detail-drawer"
          title={selectedRole ? `角色详情 · ${selectedRole.roleName}` : '角色详情'}
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          width={720}
          destroyOnClose
        >
          {detail ? (
            <Space direction="vertical" style={{ width: '100%' }} size={16}>
              <Descriptions
                bordered
                size="small"
                column={2}
                items={[
                  { key: 'roleCode', label: '角色编码', children: detail.roleCode },
                  { key: 'roleName', label: '角色名称', children: detail.roleName },
                  { key: 'roleType', label: '角色类型', children: detail.roleType },
                  { key: 'permissionCount', label: '权限数', children: detail.permissionCount ?? 0 },
                  { key: 'userCount', label: '用户数', children: detail.userCount ?? 0 },
                ]}
              />
              {detail.permissionKeys?.length ? (
                <Space wrap>
                  {detail.permissionKeys.map((item) => (
                    <Tag key={item} color="geekblue">
                      {item}
                    </Tag>
                  ))}
                </Space>
              ) : null}
            </Space>
          ) : null}
        </Drawer>
      </div>
    </PageContainer>
  );
};
