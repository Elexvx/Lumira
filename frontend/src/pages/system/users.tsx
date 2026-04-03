import { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button, Card, Descriptions, Drawer, Form, Input, Select, Space, Tag, message } from 'antd';
import { useRequest } from 'umi';
import { userService } from '@/services/user';
import { iamService } from '@/services/iam';
import type { PagedResult, RoleRecord, UserDetail, UserRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';

export default () => {
  const [queryForm] = Form.useForm();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserRecord | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [reloadTick, setReloadTick] = useState(0);

  const roleListQuery = useRequest(async () => ({
    data: await iamService.roles({ pageNo: 1, pageSize: 200 }, { autoRedirectOnUnauthorized: false }),
  }) as { data: PagedResult<RoleRecord> });
  const userDetailQuery = useRequest(
    async () =>
      selectedUser
        ? ({ data: await userService.detail(selectedUser.id, { autoRedirectOnUnauthorized: false }) } as { data: UserDetail | null })
        : ({ data: null } as { data: UserDetail | null }),
    {
      refreshDeps: [selectedUser?.id, reloadTick],
    },
  );

  useEffect(() => {
    if ((detailOpen || editorOpen) && userDetailQuery.data) {
      editorForm.setFieldsValue({
        ...userDetailQuery.data,
        roleIds: userDetailQuery.data.roleIds || [],
      });
    }
  }, [detailOpen, editorOpen, editorForm, userDetailQuery.data]);

  const roleOptions = useMemo(
    () =>
      (roleListQuery.data?.records || []).map((role) => ({
        label: role.roleName,
        value: role.id,
      })),
    [roleListQuery.data?.records],
  );

  const fetchUsers = useCallback(
    async (params: { current?: number; pageSize?: number }) => {
      const result = await userService.list(
        {
          pageNo: params.current,
          pageSize: params.pageSize,
          ...(query || {}),
        },
        { autoRedirectOnUnauthorized: false },
      );
      return {
        data: result.records,
        success: true,
        total: result.total,
      };
    },
    [query, reloadTick],
  );

  const columns = useMemo<ProColumns<UserRecord>[]>(
    () => [
      { title: '用户名', dataIndex: 'username', width: 140 },
      { title: '昵称', dataIndex: 'nickname', width: 140 },
      { title: '姓名', dataIndex: 'realName', width: 140 },
      { title: '手机号', dataIndex: 'mobile', width: 140 },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
      },
      {
        title: '角色',
        dataIndex: 'roleNames',
        render: (_, record) => (record.roleNames?.length ? record.roleNames.join(', ') : '-'),
      },
      {
        title: '操作',
        width: 260,
        render: (_, record) => (
          <Space wrap>
            {canAccess('system:user:view') ? (
              <Button
                onClick={() => {
                  setSelectedUser(record);
                  setDetailOpen(true);
                }}
              >
                详情
              </Button>
            ) : null}
            {canAccess('system:user:update') ? (
              <Button
                onClick={() => {
                  setSelectedUser(record);
                  setEditingId(record.id);
                  setEditorOpen(true);
                }}
              >
                编辑
              </Button>
            ) : null}
            {canAccess('system:user:status') ? (
              <Button
                onClick={async () => {
                  await userService.changeStatus(
                    record.id,
                    { status: record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED' },
                    { autoRedirectOnUnauthorized: false },
                  );
                  message.success('状态已更新');
                  setReloadTick((value) => value + 1);
                }}
              >
                {record.status === 'ENABLED' ? '禁用' : '启用'}
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

  const openCreate = () => {
    setSelectedUser(null);
    setEditingId(null);
    editorForm.resetFields();
    editorForm.setFieldsValue({ status: 'ENABLED', roleIds: [] });
    setEditorOpen(true);
  };

  const saveUser = async () => {
    const values = await editorForm.validateFields();
    const payload = {
      ...values,
      roleIds: values.roleIds || [],
    };
    if (editingId) {
      await userService.update(editingId, payload, { autoRedirectOnUnauthorized: false });
      message.success('用户已更新');
    } else {
      await userService.create(payload, { autoRedirectOnUnauthorized: false });
      message.success('用户已创建');
    }
    setEditorOpen(false);
    setReloadTick((value) => value + 1);
  };

  return (
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      breadcrumbRender={false}
      title="用户管理"
      subTitle="支持查询、新增、编辑、查看详情和启停用户。"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Card className="saas-query-panel">
          <Form form={queryForm} layout="vertical" onFinish={submitQuery} onReset={resetQuery}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
              <Form.Item name="username" label="用户名">
                <Input allowClear placeholder="输入用户名" />
              </Form.Item>
              <Form.Item name="mobile" label="手机号">
                <Input allowClear placeholder="输入手机号" />
              </Form.Item>
              <Form.Item name="status" label="状态">
                <Select
                  allowClear
                  options={[
                    { label: '启用', value: 'ENABLED' },
                    { label: '禁用', value: 'DISABLED' },
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
              {canAccess('system:user:create') ? (
                <Button type="primary" onClick={openCreate}>
                  新增用户
                </Button>
              ) : null}
            </Space>
            <Button onClick={() => setReloadTick((value) => value + 1)}>刷新列表</Button>
          </Space>
        </Card>

        <Card className="saas-crud-table-card" bodyStyle={{ minHeight: 0 }}>
          <ProTable<UserRecord>
            rowKey="id"
            columns={columns}
            request={fetchUsers}
            params={{ ...query, reloadTick }}
            search={false}
            options={false}
            toolBarRender={false}
            pagination={{ showSizeChanger: true }}
          />
        </Card>

        <Drawer
          className="saas-detail-drawer"
          title={editingId ? '编辑用户' : '新增用户'}
          open={editorOpen}
          onClose={() => setEditorOpen(false)}
          width={720}
          destroyOnClose
          extra={
            <Space>
              <Button onClick={() => setEditorOpen(false)}>取消</Button>
              <Button type="primary" onClick={saveUser}>
                保存
              </Button>
            </Space>
          }
        >
          <Form form={editorForm} layout="vertical" initialValues={{ status: 'ENABLED' }}>
            <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input />
            </Form.Item>
            <Form.Item name="password" label={editingId ? '重置密码（可选）' : '初始密码'} rules={!editingId ? [{ required: true, message: '请输入密码' }] : undefined}>
              <Input.Password placeholder="输入密码" />
            </Form.Item>
            <Form.Item name="mobile" label="手机号">
              <Input />
            </Form.Item>
            <Form.Item name="nickname" label="昵称">
              <Input />
            </Form.Item>
            <Form.Item name="realName" label="姓名">
              <Input />
            </Form.Item>
            <Form.Item name="email" label="邮箱">
              <Input />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select options={[{ label: '启用', value: 'ENABLED' }, { label: '禁用', value: 'DISABLED' }]} />
            </Form.Item>
            <Form.Item name="roleIds" label="角色">
              <Select mode="multiple" options={roleOptions} />
            </Form.Item>
          </Form>
        </Drawer>

        <Drawer
          className="saas-detail-drawer"
          title={selectedUser ? `用户详情 · ${selectedUser.username}` : '用户详情'}
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          width={720}
          destroyOnClose
        >
          <Card loading={userDetailQuery.loading} bordered={false} bodyStyle={{ padding: 0 }}>
            {userDetailQuery.data ? (
              <Space direction="vertical" style={{ width: '100%' }} size={16}>
                <Descriptions
                  bordered
                  size="small"
                  column={2}
                  items={[
                    { key: 'username', label: '用户名', children: userDetailQuery.data.username },
                    { key: 'nickname', label: '昵称', children: userDetailQuery.data.nickname || '-' },
                    { key: 'realName', label: '姓名', children: userDetailQuery.data.realName || '-' },
                    { key: 'mobile', label: '手机号', children: userDetailQuery.data.mobile || '-' },
                    { key: 'email', label: '邮箱', children: userDetailQuery.data.email || '-' },
                    { key: 'status', label: '状态', children: userDetailQuery.data.status },
                  ]}
                />
                <Descriptions column={2} bordered size="small">
                  <Descriptions.Item label="角色">
                    {(userDetailQuery.data.roleNames || []).length ? userDetailQuery.data.roleNames?.join(', ') : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="租户">
                    {(userDetailQuery.data.tenantNames || []).length ? userDetailQuery.data.tenantNames?.join(', ') : '-'}
                  </Descriptions.Item>
                </Descriptions>
              </Space>
            ) : null}
          </Card>
        </Drawer>
      </div>
    </PageContainer>
  );
};
