import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Checkbox, Form, Input, Modal, Select, Space, Tag, message } from 'antd';
import { useRequest } from 'umi';
import { ManagementPageContainer } from '@/components/ManagementPageContainer';
import { QueryPanel } from '@/components/QueryPanel';
import { ActionBar } from '@/components/ActionBar';
import { DataTable } from '@/components/DataTable';
import { DetailDrawer } from '@/components/DetailDrawer';
import { PermissionButton } from '@/components/PermissionButton';
import { iamService } from '@/services/iam';
import type { PermissionRecord, RoleDetail, RoleRecord } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';

export default () => {
  const [queryForm] = Form.useForm();
  const [editorForm] = Form.useForm();
  const { isMobile } = useResponsive();
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
  const fetchRoles = useCallback(
    async (params: { current: number; pageSize: number }) =>
      iamService.roles(
        {
          pageNo: params.current,
          pageSize: params.pageSize,
          ...(query || {}),
        },
        { autoRedirectOnUnauthorized: false },
      ),
    [query, reloadTick],
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

  const columns = useMemo(
    () => [
      { title: '角色编码', dataIndex: 'roleCode' },
      { title: '角色名称', dataIndex: 'roleName' },
      { title: '角色类型', dataIndex: 'roleType' },
      { title: '权限数', dataIndex: 'permissionCount', render: (value: number) => value ?? 0 },
      { title: '用户数', dataIndex: 'userCount', render: (value: number) => value ?? 0 },
      {
        title: '操作',
        render: (_: unknown, record: RoleRecord) => (
          <Space wrap>
            <PermissionButton
              permission="system:role:view"
              onClick={() => {
                setSelectedRole(record);
                setDetailOpen(true);
              }}
            >
              详情
            </PermissionButton>
            <PermissionButton
              permission="system:role:update"
              onClick={() => {
                setSelectedRole(record);
                setEditingId(record.id);
                setEditorOpen(true);
              }}
            >
              编辑
            </PermissionButton>
            <PermissionButton
              permission="system:role:permissions"
              onClick={() => {
                setSelectedRole(record);
                setEditingId(record.id);
                setEditorOpen(true);
              }}
            >
              权限分配
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
    <ManagementPageContainer title="角色管理" description="支持角色查询、新增、编辑、查看与权限分配。">
      <QueryPanel
        form={queryForm}
        onSearch={submitQuery}
        onReset={resetQuery}
        columns={isMobile ? 1 : 3}
        collapseCount={3}
        actions={<Button onClick={() => setReloadTick((value) => value + 1)}>刷新</Button>}
      >
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
      </QueryPanel>

      <ActionBar
        left={
          <PermissionButton permission="system:role:create" type="primary" onClick={openCreate}>
            新增角色
          </PermissionButton>
        }
        right={<Button onClick={() => setReloadTick((value) => value + 1)}>刷新列表</Button>}
      />

      <Card bodyStyle={{ height: 520, minHeight: 0 }}>
        <DataTable<RoleRecord>
          rowKey="id"
          columns={columns}
          request={fetchRoles}
          middleScroll
          emptyText="暂无角色数据"
        />
      </Card>

      <Modal
        open={editorOpen}
        title={editingId ? '编辑角色 / 分配权限' : '新增角色'}
        onCancel={() => setEditorOpen(false)}
        onOk={saveRole}
        width={720}
        destroyOnClose
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
      </Modal>

      <DetailDrawer
        title={selectedRole ? `角色详情 · ${selectedRole.roleName}` : '角色详情'}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        loading={roleDetailQuery.loading}
        descriptionItems={
          detail
            ? [
                { key: 'roleCode', label: '角色编码', children: detail.roleCode },
                { key: 'roleName', label: '角色名称', children: detail.roleName },
                { key: 'roleType', label: '角色类型', children: detail.roleType },
                { key: 'permissionCount', label: '权限数', children: detail.permissionCount ?? 0 },
                { key: 'userCount', label: '用户数', children: detail.userCount ?? 0 },
              ]
            : undefined
        }
      >
        {detail?.permissionKeys?.length ? (
          <Space wrap>
            {detail.permissionKeys.map((item) => (
              <Tag key={item} color="geekblue">
                {item}
              </Tag>
            ))}
          </Space>
        ) : null}
      </DetailDrawer>
    </ManagementPageContainer>
  );
};
