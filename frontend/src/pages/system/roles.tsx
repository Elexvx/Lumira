import { useEffect, useRef, useState } from 'react';
import { PageContainer, ProDescriptions, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Checkbox, Drawer, Form, Input, Modal, Select, Space, Spin, Tag, message } from 'antd';
import { iamService } from '@/services/iam';
import type { PermissionRecord, RoleDetail, RoleRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';
import { ROLE_TYPE_LABEL_MAP, ROLE_TYPE_OPTIONS } from '@/constants/role';

const RoleManagementPage = () => {
  const actionRef = useRef<ActionType>();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
  const [selectedRole, setSelectedRole] = useState<RoleRecord | null>(null);
  const [selectedRoleDetail, setSelectedRoleDetail] = useState<RoleDetail | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [permissionOptions, setPermissionOptions] = useState<{ label: string; value: string }[]>([]);

  useEffect(() => {
    let active = true;
    void iamService.permissions({ autoRedirectOnUnauthorized: false }).then((result: PermissionRecord[]) => {
      if (!active) {
        return;
      }
      setPermissionOptions(
        result.map((item) => ({
          label: item.permissionName,
          value: item.permissionKey,
        })),
      );
    });
    return () => {
      active = false;
    };
  }, []);

  const openCreate = () => {
    setSelectedRole(null);
    setEditingId(null);
    editorForm.resetFields();
    editorForm.setFieldsValue({ roleType: 'CUSTOM', permissionKeys: [] });
    setEditorOpen(true);
  };

  const openEdit = async (record: RoleRecord) => {
    setSelectedRole(record);
    setEditingId(record.id);
    setEditorOpen(true);
    const detail = await iamService.roleDetail(record.id, { autoRedirectOnUnauthorized: false });
    editorForm.setFieldsValue({
      ...detail,
      permissionKeys: detail.permissionKeys || [],
    });
  };

  const openDetail = async (record: RoleRecord) => {
    setSelectedRole(record);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const detail = await iamService.roleDetail(record.id, { autoRedirectOnUnauthorized: false });
      setSelectedRoleDetail(detail);
    } finally {
      setDetailLoading(false);
    }
  };

  const saveRole = async () => {
    setSaving(true);
    try {
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
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const closeEditorDrawer = () => {
    setEditorOpen(false);
  };

  const handleEditorClose = () => {
    if (!editorForm.isFieldsTouched(true)) {
      closeEditorDrawer();
      return;
    }

    Modal.confirm({
      title: '提示',
      content: '关闭抽屉将丢失未保存的内容，是否确认关闭？',
      okText: '继续编辑',
      cancelText: '确认关闭',
      centered: true,
      onOk: () => Promise.resolve(),
      onCancel: closeEditorDrawer,
    });
  };

  const columns: ProColumns<RoleRecord>[] = [
    {
      title: '角色编码',
      dataIndex: 'roleCode',
      search: true,
    },
    {
      title: '角色名称',
      dataIndex: 'roleName',
      search: true,
    },
    {
      title: '角色类型',
      dataIndex: 'roleType',
      valueEnum: ROLE_TYPE_OPTIONS.reduce<Record<string, { text: string }>>((acc, item) => {
        acc[String(item.value)] = { text: item.label };
        return acc;
      }, {}),
      search: {
        transform: (value) => ({ roleType: value }),
      },
    },
    {
      title: '权限数',
      dataIndex: 'permissionCount',
      hideInSearch: true,
      render: (_, record) => record.permissionCount ?? 0,
    },
    {
      title: '用户数',
      dataIndex: 'userCount',
      hideInSearch: true,
      render: (_, record) => record.userCount ?? 0,
    },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 180,
      render: (_, record) => (
        <Space size={0}>
          {canAccess('system:role:view') ? (
            <Button type="link" size="small" onClick={() => void openDetail(record)}>
              详情
            </Button>
          ) : null}
          {canAccess('system:role:update') ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {canAccess('system:role:permissions') ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              权限分配
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  const currentPermissions = selectedRoleDetail?.permissionKeys || [];

  return (
    <PageContainer
      title="角色管理"
      extra={
        <Space>
          {canAccess('system:role:create') ? (
            <Button type="primary" onClick={openCreate}>
              新增角色
            </Button>
          ) : null}
        </Space>
      }
    >
      <ProTable<RoleRecord>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        search={{ labelWidth: 'auto' }}
        options={false}
        pagination={{ showSizeChanger: true }}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          const result = await iamService.roles(
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
          canAccess('system:role:create') ? (
            <Button key="create" type="primary" onClick={openCreate}>
              新增角色
            </Button>
          ) : null,
          <Button key="refresh" onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ]}
      />

      <Drawer
        title={editingId ? '编辑角色 / 分配权限' : '新增角色'}
        open={editorOpen}
        onClose={handleEditorClose}
        width={720}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={handleEditorClose}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void saveRole()}>
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
            <Select options={ROLE_TYPE_OPTIONS as unknown as { label: string; value: string }[]} />
          </Form.Item>
          <Form.Item name="permissionKeys" label="权限">
            <Checkbox.Group options={permissionOptions} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={selectedRole ? `角色详情 · ${selectedRole.roleName}` : '角色详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setSelectedRoleDetail(null);
        }}
        width={720}
        destroyOnClose
      >
        {detailLoading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : selectedRoleDetail ? (
          <>
            <ProDescriptions<RoleDetail>
              column={2}
              dataSource={selectedRoleDetail}
              columns={[
                { title: '角色编码', dataIndex: 'roleCode' },
                { title: '角色名称', dataIndex: 'roleName' },
                {
                  title: '角色类型',
                  dataIndex: 'roleType',
                  renderText: (value) => ROLE_TYPE_LABEL_MAP[String(value)] || String(value || '-'),
                },
                { title: '权限数', dataIndex: 'permissionCount' },
                { title: '用户数', dataIndex: 'userCount' },
              ]}
            />
            <div style={{ marginTop: 16 }}>
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <div>当前权限</div>
                {currentPermissions.length ? (
                  <Space wrap>
                    {currentPermissions.map((item) => (
                      <Tag key={item} color="geekblue">
                        {item}
                      </Tag>
                    ))}
                  </Space>
                ) : (
                  <Tag>暂无权限</Tag>
                )}
              </Space>
            </div>
          </>
        ) : null}
      </Drawer>
    </PageContainer>
  );
};

export default RoleManagementPage;
