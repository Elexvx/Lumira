import { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button, Card, Checkbox, Drawer, Form, Input, Select, Space, Tag, message } from 'antd';
import { useRequest } from 'umi';
import { iamService } from '@/services/iam';
import type { PermissionRecord, RoleDetail, RoleRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';
import { DetailDrawer } from '@/components/DetailDrawer';
import { useDetailState } from '@/hooks/useDetailState';
import { ROLE_TYPE_LABEL_MAP, ROLE_TYPE_OPTIONS } from '@/constants/role';

export default () => {
  const [queryForm] = Form.useForm();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedRole, setSelectedRole] = useState<RoleRecord | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);
  const detailState = useDetailState<RoleDetail>();

  const permissionQuery = useRequest(async () => ({
    data: await iamService.permissions({ autoRedirectOnUnauthorized: false }),
  }) as { data: PermissionRecord[] });
  const permissionOptions = useMemo(
    () =>
      (permissionQuery.data || []).map((item) => ({
        label: item.permissionName,
        value: item.permissionKey,
      })),
    [permissionQuery.data],
  );

  useEffect(() => {
    if (editorOpen && selectedRole?.id) {
      iamService.roleDetail(selectedRole.id, { autoRedirectOnUnauthorized: false }).then((detail) => {
        editorForm.setFieldsValue({ ...detail, permissionKeys: detail.permissionKeys || [] });
      });
    }
  }, [editorForm, editorOpen, selectedRole?.id]);

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
                  void detailState.load(() => iamService.roleDetail(record.id, { autoRedirectOnUnauthorized: false }));
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

  return (
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      title="角色管理"
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
                  options={ROLE_TYPE_OPTIONS as unknown as {label:string;value:string}[]}
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
              <Select options={ROLE_TYPE_OPTIONS as unknown as {label:string;value:string}[]} />
            </Form.Item>
            <Form.Item name="permissionKeys" label="权限">
              <Checkbox.Group options={permissionOptions} />
            </Form.Item>
          </Form>
        </Drawer>

        <DetailDrawer<RoleDetail>
          title={selectedRole ? `角色详情 · ${selectedRole.roleName}` : '角色详情'}
          open={detailState.open}
          onClose={detailState.close}
          status={detailState.status}
          errorMessage={detailState.errorMessage}
          dataSource={detailState.data}
          columns={[
            { title: '角色编码', dataIndex: 'roleCode' },
            { title: '角色名称', dataIndex: 'roleName' },
            { title: '角色类型', dataIndex: 'roleType', render: (_, entity) => ROLE_TYPE_LABEL_MAP[entity.roleType || ''] || entity.roleType },
            { title: '权限数', dataIndex: 'permissionCount' },
            { title: '用户数', dataIndex: 'userCount' },
          ]}
        >
          {detailState.data?.permissionKeys?.length ? <Space wrap>{detailState.data.permissionKeys.map((item) => <Tag key={item}>{item}</Tag>)}</Space> : null}
        </DetailDrawer>
      </div>
    </PageContainer>
  );
};
