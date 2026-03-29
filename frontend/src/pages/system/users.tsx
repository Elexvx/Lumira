import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Descriptions, Form, Input, Modal, Select, Space, Switch, Tag, message } from 'antd';
import { useRequest } from 'umi';
import { ManagementPageContainer } from '@/components/ManagementPageContainer';
import { QueryPanel } from '@/components/QueryPanel';
import { ActionBar } from '@/components/ActionBar';
import { DataTable } from '@/components/DataTable';
import { DetailDrawer } from '@/components/DetailDrawer';
import { PermissionButton } from '@/components/PermissionButton';
import { userService } from '@/services/user';
import { iamService } from '@/services/iam';
import type { PagedResult, UserDetail, UserRecord } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';

export default () => {
  const [queryForm] = Form.useForm();
  const [editorForm] = Form.useForm();
  const { isMobile } = useResponsive();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserRecord | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [reloadTick, setReloadTick] = useState(0);
  const roleListQuery = useRequest(async () => ({
    data: await iamService.roles({ pageNo: 1, pageSize: 200 }, { autoRedirectOnUnauthorized: false }),
  }) as { data: PagedResult<import('@/types/api').RoleRecord> });
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
    async (params: { current: number; pageSize: number }) =>
      userService.list(
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
      { title: '用户名', dataIndex: 'username', width: 140 },
      { title: '昵称', dataIndex: 'nickname', width: 140 },
      { title: '姓名', dataIndex: 'realName', width: 140 },
      { title: '手机号', dataIndex: 'mobile', width: 140 },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: string) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value}</Tag>,
      },
      {
        title: '角色',
        dataIndex: 'roleNames',
        render: (value: string[]) => (value?.length ? value.join(', ') : '-'),
      },
      {
        title: '操作',
        width: 260,
        render: (_: unknown, record: UserRecord) => (
          <Space wrap>
            <PermissionButton
              permission="system:user:view"
              onClick={() => {
                setSelectedUser(record);
                setDetailOpen(true);
              }}
            >
              详情
            </PermissionButton>
            <PermissionButton
              permission="system:user:update"
              onClick={() => {
                setSelectedUser(record);
                setEditingId(record.id);
                setEditorOpen(true);
              }}
            >
              编辑
            </PermissionButton>
            <PermissionButton
              permission="system:user:status"
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
            </PermissionButton>
          </Space>
        ),
      },
    ],
    [],
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
    <ManagementPageContainer title="用户管理" description="支持查询、新增、编辑、查看详情和启停用户。">
      <QueryPanel
        form={queryForm}
        onSearch={submitQuery}
        onReset={resetQuery}
        columns={isMobile ? 1 : 3}
        collapseCount={3}
        actions={<Button onClick={() => setReloadTick((value) => value + 1)}>刷新</Button>}
      >
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
      </QueryPanel>

      <ActionBar
        left={
          <PermissionButton permission="system:user:create" type="primary" onClick={openCreate}>
            新增用户
          </PermissionButton>
        }
        right={<Button onClick={() => setReloadTick((value) => value + 1)}>刷新列表</Button>}
      />

      <Card bodyStyle={{ height: 520, minHeight: 0 }}>
        <DataTable<UserRecord>
          rowKey="id"
          columns={columns}
          request={fetchUsers}
          middleScroll
          emptyText="暂无用户数据"
        />
      </Card>

      <Modal
        open={editorOpen}
        title={editingId ? '编辑用户' : '新增用户'}
        onCancel={() => setEditorOpen(false)}
        onOk={saveUser}
        width={720}
        destroyOnClose
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
      </Modal>

      <DetailDrawer
        title={selectedUser ? `用户详情 · ${selectedUser.username}` : '用户详情'}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        loading={userDetailQuery.loading}
        descriptionItems={
          userDetailQuery.data
            ? [
                { key: 'username', label: '用户名', children: userDetailQuery.data.username },
                { key: 'nickname', label: '昵称', children: userDetailQuery.data.nickname || '-' },
                { key: 'realName', label: '姓名', children: userDetailQuery.data.realName || '-' },
                { key: 'mobile', label: '手机号', children: userDetailQuery.data.mobile || '-' },
                { key: 'email', label: '邮箱', children: userDetailQuery.data.email || '-' },
                { key: 'status', label: '状态', children: userDetailQuery.data.status },
              ]
            : undefined
        }
      >
        {userDetailQuery.data ? (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="角色">
              {(userDetailQuery.data.roleNames || []).length ? userDetailQuery.data.roleNames?.join(', ') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="租户">
              {(userDetailQuery.data.tenantNames || []).length ? userDetailQuery.data.tenantNames?.join(', ') : '-'}
            </Descriptions.Item>
          </Descriptions>
        ) : null}
      </DetailDrawer>
    </ManagementPageContainer>
  );
};
