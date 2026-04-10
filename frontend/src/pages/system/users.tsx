import { useEffect, useRef, useState } from 'react';
import { PageContainer, ProDescriptions, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Drawer, Form, Input, Select, Space, Spin, Tag, message } from 'antd';
import { userService } from '@/services/user';
import { iamService } from '@/services/iam';
import type { PagedResult, RoleRecord, UserDetail, UserRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';

const UserManagementPage = () => {
  const actionRef = useRef<ActionType>();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserRecord | null>(null);
  const [selectedUserDetail, setSelectedUserDetail] = useState<UserDetail | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [roleOptions, setRoleOptions] = useState<{ label: string; value: number }[]>([]);

  useEffect(() => {
    let active = true;
    void iamService.roles({ pageNo: 1, pageSize: 200 }, { autoRedirectOnUnauthorized: false }).then((result) => {
      if (!active) {
        return;
      }
      setRoleOptions(
        (result.records || []).map((role) => ({
          label: role.roleName,
          value: role.id,
        })),
      );
    });
    return () => {
      active = false;
    };
  }, []);

  const openCreate = () => {
    setSelectedUser(null);
    setEditingId(null);
    editorForm.resetFields();
    editorForm.setFieldsValue({ status: 'ENABLED', roleIds: [] });
    setEditorOpen(true);
  };

  const openEdit = async (record: UserRecord) => {
    setSelectedUser(record);
    setEditingId(record.id);
    setEditorOpen(true);
    try {
      const detail = await userService.detail(record.id, { autoRedirectOnUnauthorized: false });
      editorForm.setFieldsValue({
        ...detail,
        roleIds: detail.roleIds || [],
      });
    } catch {
      setEditorOpen(false);
      setEditingId(null);
    }
  };

  const openDetail = async (record: UserRecord) => {
    setSelectedUser(record);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const detail = await userService.detail(record.id, { autoRedirectOnUnauthorized: false });
      setSelectedUserDetail(detail);
    } catch {
      setDetailOpen(false);
      setSelectedUserDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const saveUser = async () => {
    setSaving(true);
    try {
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
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<UserRecord>[] = [
    {
      title: '用户名',
      dataIndex: 'username',
      search: true,
    },
    {
      title: '手机号',
      dataIndex: 'mobile',
      search: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueEnum: {
        ENABLED: { text: '启用', status: 'Success' },
        DISABLED: { text: '禁用', status: 'Default' },
      },
      search: {
        transform: (value) => ({ status: value }),
      },
      render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
    },
    {
      title: '昵称',
      dataIndex: 'nickname',
      hideInSearch: true,
    },
    {
      title: '姓名',
      dataIndex: 'realName',
      hideInSearch: true,
    },
    {
      title: '角色',
      dataIndex: 'roleNames',
      hideInSearch: true,
      render: (_, record) => (record.roleNames?.length ? record.roleNames.join(', ') : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 180,
      render: (_, record) => (
        <Space size={0}>
          {canAccess('system:user:view') ? (
            <Button type="link" size="small" onClick={() => void openDetail(record)}>
              详情
            </Button>
          ) : null}
          {canAccess('system:user:update') ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {canAccess('system:user:status') ? (
            <Button
              type="link"
              size="small"
              danger={record.status === 'ENABLED'}
              onClick={async () => {
                await userService.changeStatus(
                  record.id,
                  { status: record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED' },
                  { autoRedirectOnUnauthorized: false },
                );
                message.success('状态已更新');
                actionRef.current?.reload();
              }}
            >
              {record.status === 'ENABLED' ? '禁用' : '启用'}
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <PageContainer title="用户管理">
      <ProTable<UserRecord>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        search={{ labelWidth: 'auto' }}
        options={false}
        pagination={{ showSizeChanger: true }}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          const result = await userService.list(
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
          canAccess('system:user:create') ? (
            <Button key="create" type="primary" onClick={openCreate}>
              新增用户
            </Button>
          ) : null,
          <Button key="refresh" onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ]}
      />

      <Drawer
        title={editingId ? '编辑用户' : '新增用户'}
        open={editorOpen}
        onClose={() => setEditorOpen(false)}
        width={720}
        destroyOnClose
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={() => setEditorOpen(false)}>取消</Button>
              <Button type="primary" loading={saving} onClick={() => void saveUser()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={editorForm} layout="vertical" initialValues={{ status: 'ENABLED', roleIds: [] }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="password"
            label={editingId ? '重置密码（可选）' : '初始密码'}
            rules={!editingId ? [{ required: true, message: '请输入密码' }] : undefined}
          >
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
            <Select
              options={[
                { label: '启用', value: 'ENABLED' },
                { label: '禁用', value: 'DISABLED' },
              ]}
            />
          </Form.Item>
          <Form.Item name="roleIds" label="角色">
            <Select mode="multiple" options={roleOptions} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={selectedUser ? `用户详情 · ${selectedUser.username}` : '用户详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setSelectedUserDetail(null);
        }}
        width={720}
        destroyOnClose
      >
        {detailLoading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : selectedUserDetail ? (
          <ProDescriptions<UserDetail>
            column={2}
            dataSource={selectedUserDetail}
            columns={[
              { title: '用户名', dataIndex: 'username' },
              { title: '昵称', dataIndex: 'nickname', renderText: (value) => value || '-' },
              { title: '姓名', dataIndex: 'realName', renderText: (value) => value || '-' },
              { title: '手机号', dataIndex: 'mobile', renderText: (value) => value || '-' },
              { title: '邮箱', dataIndex: 'email', renderText: (value) => value || '-' },
              { title: '状态', dataIndex: 'status' },
              {
                title: '角色',
                dataIndex: 'roleNames',
                renderText: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-'),
              },
              {
                title: '租户',
                dataIndex: 'tenantNames',
                renderText: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-'),
              },
            ]}
          />
        ) : null}
      </Drawer>
    </PageContainer>
  );
};

export default UserManagementPage;
