import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Checkbox, Col, Descriptions, Form, Input, Modal, Row, Space, Tag, Typography, message } from 'antd';
import { history, useRequest } from 'umi';
import { ManagementPageContainer } from '@/components/ManagementPageContainer';
import { QueryPanel } from '@/components/QueryPanel';
import { ActionBar } from '@/components/ActionBar';
import { DataTable } from '@/components/DataTable';
import { DetailDrawer } from '@/components/DetailDrawer';
import { PermissionButton } from '@/components/PermissionButton';
import { EmptyState } from '@/components/EmptyState';
import { iamService } from '@/services/iam';
import type { RoleDetail, RoleRecord } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';
import type { PermissionRecord } from '@/types/api';

export default () => {
  const [form] = Form.useForm();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedRole, setSelectedRole] = useState<RoleRecord | null>(null);
  const [roleDrawerOpen, setRoleDrawerOpen] = useState(false);
  const [permissionDrawerOpen, setPermissionDrawerOpen] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);
  const { isMobile } = useResponsive();
  const permissionQuery = useRequest(async () => ({
    data: await iamService.permissions({ autoRedirectOnUnauthorized: false }),
  }) as { data: PermissionRecord[] });
  const roleDetailQuery = useRequest(
    async () =>
      selectedRole
        ? ({
            data: await iamService.roleDetail(selectedRole.id, { autoRedirectOnUnauthorized: false }),
          } as { data: RoleRecord | null })
        : ({ data: null } as { data: RoleRecord | null }),
    {
      refreshDeps: [selectedRole?.id, reloadTick],
    },
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
        title: item.permissionKey,
      })),
    [permissionQuery.data],
  );

  const submitQuery = async (values: Record<string, unknown>) => {
    setQuery(values);
  };

  const resetQuery = () => {
    form.resetFields();
    setQuery({});
  };

  const roleColumns = useMemo(
    () => [
      { title: '角色编码', dataIndex: 'roleCode' },
      { title: '角色名称', dataIndex: 'roleName' },
      { title: '角色类型', dataIndex: 'roleType' },
      {
        title: '权限数',
        dataIndex: 'permissionCount',
        render: (value: number) => value ?? 0,
      },
      {
        title: '用户数',
        dataIndex: 'userCount',
        render: (value: number) => value ?? 0,
      },
      {
        title: '操作',
        key: 'actions',
        render: (_: unknown, record: RoleRecord) => (
          <Space wrap>
            <PermissionButton
              permission="system:role:view"
              onClick={() => {
                setSelectedRole(record);
                setRoleDrawerOpen(true);
              }}
            >
              详情
            </PermissionButton>
            <PermissionButton
              permission="system:role:permissions"
              onClick={() => {
                setSelectedRole(record);
                setPermissionDrawerOpen(true);
              }}
            >
              分配权限
            </PermissionButton>
          </Space>
        ),
      },
    ],
    [],
  );

  const currentRolePermissions: string[] = (roleDetailQuery.data as RoleDetail | null | undefined)?.permissionKeys || [];

  return (
    <ManagementPageContainer title="权限中心" description="查看角色、权限和角色权限分配。">
      <QueryPanel
        form={form}
        onSearch={submitQuery}
        onReset={resetQuery}
        columns={isMobile ? 1 : 3}
        collapseCount={3}
        actions={
          <>
            <Button onClick={() => history.push('/system/roles')}>进入角色管理</Button>
            <Button onClick={() => history.push('/system/users')}>进入用户管理</Button>
          </>
        }
      >
        <Form.Item name="roleCode" label="角色编码">
          <Input allowClear placeholder="输入角色编码" />
        </Form.Item>
        <Form.Item name="roleName" label="角色名称">
          <Input allowClear placeholder="输入角色名称" />
        </Form.Item>
        <Form.Item name="roleType" label="角色类型">
          <Input allowClear placeholder="输入角色类型" />
        </Form.Item>
      </QueryPanel>

      <ActionBar
        left={<Typography.Text strong>角色列表</Typography.Text>}
        right={<Typography.Text type="secondary">权限列表已接入统一权限快照</Typography.Text>}
      />

      <Card bodyStyle={{ height: 420, minHeight: 0 }}>
        <DataTable<RoleRecord>
          rowKey="id"
          columns={roleColumns}
          request={fetchRoles}
          middleScroll
          emptyText="暂无角色数据"
        />
      </Card>

      <Row gutter={[16, 16]}>
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
              <EmptyState description="暂无权限目录" />
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

      <DetailDrawer
        title={selectedRole ? `角色详情 · ${selectedRole.roleName}` : '角色详情'}
        open={roleDrawerOpen}
        onClose={() => setRoleDrawerOpen(false)}
        descriptionItems={
          selectedRole
            ? [
                { key: 'roleCode', label: '角色编码', children: selectedRole.roleCode },
                { key: 'roleName', label: '角色名称', children: selectedRole.roleName },
                { key: 'roleType', label: '角色类型', children: selectedRole.roleType },
                { key: 'permissionCount', label: '权限数量', children: selectedRole.permissionCount ?? 0 },
                { key: 'userCount', label: '用户数量', children: selectedRole.userCount ?? 0 },
              ]
            : undefined
        }
      >
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
          <EmptyState description="该角色暂无权限" />
        )}
      </DetailDrawer>

      <DetailDrawer
        title={selectedRole ? `分配权限 · ${selectedRole.roleName}` : '分配权限'}
        open={permissionDrawerOpen}
        onClose={() => setPermissionDrawerOpen(false)}
        loading={roleDetailQuery.loading || permissionQuery.loading}
        footer={
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
      </DetailDrawer>
    </ManagementPageContainer>
  );
};
