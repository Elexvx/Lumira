import { useCallback, useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button, Card, Checkbox, Col, Descriptions, Drawer, Empty, Form, Input, Row, Space, Tag, Typography, message } from 'antd';
import { history, useRequest } from 'umi';
import { usePermission } from '@/hooks/usePermission';
import { iamService } from '@/services/iam';
import type { PermissionRecord, RoleDetail, RoleRecord } from '@/types/api';

export default () => {
  const [form] = Form.useForm();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedRole, setSelectedRole] = useState<RoleRecord | null>(null);
  const [roleDrawerOpen, setRoleDrawerOpen] = useState(false);
  const [permissionDrawerOpen, setPermissionDrawerOpen] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);
  const { canAccess } = usePermission();

  const permissionQuery = useRequest(async () => ({
    data: await iamService.permissions({ autoRedirectOnUnauthorized: false }),
  }) as { data: PermissionRecord[] });
  const roleDetailQuery = useRequest(
    async () =>
      selectedRole
        ? ({
            data: await iamService.roleDetail(selectedRole.id, { autoRedirectOnUnauthorized: false }),
          } as { data: RoleDetail | null })
        : ({ data: null } as { data: RoleDetail | null }),
    {
      refreshDeps: [selectedRole?.id, reloadTick],
    },
  );

  const permissionOptions = useMemo(
    () =>
      (permissionQuery.data || []).map((item) => ({
        label: item.permissionName,
        value: item.permissionKey,
        title: item.permissionKey,
      })),
    [permissionQuery.data],
  );

  const roleColumns = useMemo<ProColumns<RoleRecord>[]>(
    () => [
      { title: '角色编码', dataIndex: 'roleCode' },
      { title: '角色名称', dataIndex: 'roleName' },
      { title: '角色类型', dataIndex: 'roleType' },
      {
        title: '权限数',
        dataIndex: 'permissionCount',
        render: (_, record) => record.permissionCount ?? 0,
      },
      {
        title: '用户数',
        dataIndex: 'userCount',
        render: (_, record) => record.userCount ?? 0,
      },
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => (
          <Space wrap>
            {canAccess('system:role:view') ? (
              <Button
                onClick={() => {
                  setSelectedRole(record);
                  setRoleDrawerOpen(true);
                }}
              >
                详情
              </Button>
            ) : null}
            {canAccess('system:role:permissions') ? (
              <Button
                onClick={() => {
                  setSelectedRole(record);
                  setPermissionDrawerOpen(true);
                }}
              >
                分配权限
              </Button>
            ) : null}
          </Space>
        ),
      },
    ],
    [canAccess],
  );

  const currentRolePermissions: string[] = roleDetailQuery.data?.permissionKeys || [];

  const submitQuery = async (values: Record<string, unknown>) => {
    setQuery(values);
  };

  const resetQuery = () => {
    form.resetFields();
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
      return {
        data: result.records,
        success: true,
        total: result.total,
      };
    },
    [query, reloadTick],
  );

  return (
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      breadcrumbRender={false}
      title="权限中心"
      subTitle="查看角色、权限和角色权限分配。"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Card className="saas-query-panel">
          <Form form={form} layout="vertical" onFinish={submitQuery} onReset={resetQuery}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
              <Form.Item name="roleCode" label="角色编码">
                <Input allowClear placeholder="输入角色编码" />
              </Form.Item>
              <Form.Item name="roleName" label="角色名称">
                <Input allowClear placeholder="输入角色名称" />
              </Form.Item>
              <Form.Item name="roleType" label="角色类型">
                <Input allowClear placeholder="输入角色类型" />
              </Form.Item>
            </div>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button htmlType="reset">重置</Button>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={() => history.push('/system/roles')}>进入角色管理</Button>
              <Button onClick={() => history.push('/system/users')}>进入用户管理</Button>
            </Space>
          </Form>
        </Card>

        <Card className="saas-action-bar">
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Typography.Text strong>角色列表</Typography.Text>
            <Typography.Text type="secondary">权限列表已接入统一权限快照</Typography.Text>
          </Space>
        </Card>

        <Card className="saas-crud-table-card" bodyStyle={{ minHeight: 0 }}>
          <ProTable<RoleRecord>
            rowKey="id"
            columns={roleColumns}
            request={fetchRoles}
            params={{ ...query, reloadTick }}
            search={false}
            options={false}
            toolBarRender={false}
            pagination={{ showSizeChanger: true }}
          />
        </Card>

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={12}>
            <Card className="saas-crud-info-card" title="权限目录">
              {permissionQuery.data?.length ? (
                <Space wrap>
                  {permissionQuery.data.map((item) => (
                    <Tag key={item.permissionKey} color="blue">
                      {item.permissionName}
                    </Tag>
                  ))}
                </Space>
              ) : (
                <Empty description="暂无权限目录" />
              )}
            </Card>
          </Col>
          <Col xs={24} xl={12}>
            <Card className="saas-crud-info-card" title="用户角色查看">
              <Typography.Paragraph type="secondary">
                用户角色关系请在用户管理页查看和维护，这里保留的是角色维度的权限收口视图。
              </Typography.Paragraph>
              <Button onClick={() => history.push('/system/users')}>前往用户管理</Button>
            </Card>
          </Col>
        </Row>

        <Drawer
          className="saas-detail-drawer"
          title={selectedRole ? `角色详情 · ${selectedRole.roleName}` : '角色详情'}
          open={roleDrawerOpen}
          onClose={() => setRoleDrawerOpen(false)}
          width={720}
          destroyOnClose
        >
          {selectedRole ? (
            <Space direction="vertical" style={{ width: '100%' }} size={16}>
              <Descriptions
                bordered
                size="small"
                column={2}
                items={[
                  { key: 'roleCode', label: '角色编码', children: selectedRole.roleCode },
                  { key: 'roleName', label: '角色名称', children: selectedRole.roleName },
                  { key: 'roleType', label: '角色类型', children: selectedRole.roleType },
                  { key: 'permissionCount', label: '权限数量', children: selectedRole.permissionCount ?? 0 },
                  { key: 'userCount', label: '用户数量', children: selectedRole.userCount ?? 0 },
                ]}
              />
              {currentRolePermissions.length ? (
                <Card className="saas-crud-info-card" size="small" title="当前权限">
                  <Space wrap>
                    {currentRolePermissions.map((permissionKey: string) => (
                      <Tag key={permissionKey} color="geekblue">
                        {permissionKey}
                      </Tag>
                    ))}
                  </Space>
                </Card>
              ) : (
                <Empty description="该角色暂无权限" />
              )}
            </Space>
          ) : null}
        </Drawer>

        <Drawer
          className="saas-detail-drawer"
          title={selectedRole ? `分配权限 · ${selectedRole.roleName}` : '分配权限'}
          open={permissionDrawerOpen}
          onClose={() => setPermissionDrawerOpen(false)}
          width={720}
          destroyOnClose
          extra={
            <Space>
              <Button onClick={() => setPermissionDrawerOpen(false)}>取消</Button>
              <Button
                type="primary"
                onClick={async () => {
                  if (!selectedRole) {
                    return;
                  }
                  const nextPermissions = permissionOptions.map((item) => item.value as string);
                  await iamService.updateRolePermissions(selectedRole.id, nextPermissions, {
                    autoRedirectOnUnauthorized: false,
                  });
                  message.success('权限已同步');
                  setPermissionDrawerOpen(false);
                  setReloadTick((value) => value + 1);
                  await roleDetailQuery.refresh();
                }}
              >
                一键同步全部权限
              </Button>
            </Space>
          }
        >
          <Typography.Paragraph type="secondary">
            当前仅提供最小闭环：可查看权限目录，也可以一键同步全部权限。后续可在此扩展更精细的角色权限勾选交互。
          </Typography.Paragraph>
          <Form
            layout="vertical"
            onFinish={async (values: { permissionKeys?: string[] }) => {
              if (!selectedRole) {
                return;
              }
              await iamService.updateRolePermissions(selectedRole.id, values.permissionKeys || [], {
                autoRedirectOnUnauthorized: false,
              });
              message.success('权限已保存');
              setPermissionDrawerOpen(false);
              setReloadTick((value) => value + 1);
              await roleDetailQuery.refresh();
            }}
            initialValues={{ permissionKeys: currentRolePermissions }}
          >
            <Form.Item name="permissionKeys" label="权限列表">
              <Checkbox.Group options={permissionOptions} />
            </Form.Item>
            <Button type="primary" htmlType="submit">
              保存权限
            </Button>
          </Form>
        </Drawer>
      </div>
    </PageContainer>
  );
};
