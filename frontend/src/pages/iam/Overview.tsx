import { useMemo, useRef, useState } from 'react';
import { PageContainer, ProDescriptions, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Card, Checkbox, Col, Drawer, Empty, Form, Input, Row, Space, Tag, Typography, message } from 'antd';
import { history, useRequest } from 'umi';
import { usePermission } from '@/hooks/usePermission';
import { iamService } from '@/services/iam';
import type { PermissionRecord, RoleDetail, RoleRecord } from '@/types/api';

const IamOverviewPage = () => {
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm();
  const { canAccess } = usePermission();
  const [selectedRole, setSelectedRole] = useState<RoleRecord | null>(null);
  const [roleDrawerOpen, setRoleDrawerOpen] = useState(false);
  const [permissionDrawerOpen, setPermissionDrawerOpen] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);

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

  const currentRolePermissions: string[] = roleDetailQuery.data?.permissionKeys || [];

  const roleColumns = useMemo<ProColumns<RoleRecord>[]>(
    () => [
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
        search: true,
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
        render: (_, record) => (
          <Space size={0}>
            {canAccess('system:role:view') ? (
              <Button
                type="link"
                size="small"
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
                type="link"
                size="small"
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

  return (
    <PageContainer title="权限中心">
      <ProTable<RoleRecord>
        actionRef={actionRef}
        rowKey="id"
        columns={roleColumns}
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
          <Button key="role" onClick={() => history.push('/system/roles')}>
            进入角色管理
          </Button>,
          <Button key="user" onClick={() => history.push('/system/users')}>
            进入用户管理
          </Button>,
          <Button key="refresh" type="primary" onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ]}
      />

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} xl={12}>
          <Card title="权限目录">
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
          <Card title="用户角色查看">
            <Typography.Paragraph type="secondary">
              用户角色关系请在用户管理页查看和维护，这里保留的是角色维度的权限收口视图。
            </Typography.Paragraph>
            <Button onClick={() => history.push('/system/users')}>前往用户管理</Button>
          </Card>
        </Col>
      </Row>

      <Drawer
        title={selectedRole ? `角色详情 · ${selectedRole.roleName}` : '角色详情'}
        open={roleDrawerOpen}
        onClose={() => setRoleDrawerOpen(false)}
        width={720}
        destroyOnClose
      >
        {selectedRole ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <ProDescriptions<RoleRecord>
              column={2}
              dataSource={selectedRole}
              columns={[
                { title: '角色编码', dataIndex: 'roleCode' },
                { title: '角色名称', dataIndex: 'roleName' },
                { title: '角色类型', dataIndex: 'roleType' },
                { title: '权限数量', dataIndex: 'permissionCount' },
                { title: '用户数量', dataIndex: 'userCount' },
              ]}
            />
            {currentRolePermissions.length ? (
              <Card size="small" title="当前权限">
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
    </PageContainer>
  );
};

export default IamOverviewPage;
