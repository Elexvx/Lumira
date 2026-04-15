import dayjs from 'dayjs';
import { useEffect, useMemo, useRef, useState } from 'react';
import { PageContainer, ProDescriptions, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Col, DatePicker, Drawer, Form, Input, Row, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { useDetailFormProps, useDetailProDescriptionsProps } from '@/features/detail/config';
import { usePermissionActions } from '@/features/permissions/usePermissionActions';
import { TableActionBar } from '@/features/table/TableActionBar';
import { buildMobilePagination, buildTableRequest, buildTableScroll } from '@/features/table/proTable';
import { useResponsive } from '@/hooks/useResponsive';
import { userService } from '@/services/user';
import { iamService } from '@/services/iam';
import type { PagedResult, RoleRecord, UserDetail, UserRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';
import { confirmAction } from '@/utils/confirm';
import { trimString, validateOptionalChinaIdCard, validateOptionalChinaMobile } from '@/utils/validators';
import { maskIdCardNumber, maskMobile } from '@/utils/sensitive';

const GENDER_OPTIONS = [
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '其他', value: 'OTHER' },
];

const UserManagementPage = () => {
  const actionRef = useRef<ActionType>();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
  const { buildActions } = usePermissionActions();
  const responsive = useResponsive();
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserRecord | null>(null);
  const [selectedUserDetail, setSelectedUserDetail] = useState<UserDetail | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [roleOptions, setRoleOptions] = useState<{ label: string; value: number }[]>([]);
  const isProtectedAdminAccount = (record?: Pick<UserRecord, 'id' | 'username'> | null) =>
    Boolean(record && (record.id === 1001 || record.username?.toLowerCase() === 'admin'));
  const protectedAdminSelected = isProtectedAdminAccount(selectedUser);
  const editorFormProps = useDetailFormProps({
    form: editorForm,
    initialValues: { status: 'ENABLED', roleIds: [] },
  });
  const detailProps = useDetailProDescriptionsProps<UserDetail>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedUserDetail || undefined,
  });

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
        birthMonth: detail.birthMonth ? dayjs(detail.birthMonth, 'YYYY-MM') : null,
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
        birthMonth: values.birthMonth ? values.birthMonth.format('YYYY-MM') : '',
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

  const updateUserStatus = async (record: UserRecord, status: 'ENABLED' | 'DISABLED') => {
    await userService.changeStatus(record.id, { status }, { autoRedirectOnUnauthorized: false });
    message.success('状态已更新');
    actionRef.current?.reload();
  };

  const handleStatusToggle = (record: UserRecord) => {
    if (record.status !== 'ENABLED') {
      void updateUserStatus(record, 'ENABLED');
      return;
    }

    confirmAction({
      title: '禁用用户',
      content: `确认禁用用户「${record.username}」吗？禁用后该账号将无法继续登录。`,
      okText: '确认禁用',
      okButtonProps: { danger: true },
      onOk: async () => {
        await updateUserStatus(record, 'DISABLED');
      },
    });
  };

  const columns: ProColumns<UserRecord>[] = useMemo(
    () => [
    {
      title: '用户名',
      dataIndex: 'username',
      search: true,
    },
    {
      title: '手机号',
      dataIndex: 'mobile',
      search: true,
      ellipsis: true,
      render: (_, record) => {
        const content = maskMobile(record.mobile) || '';
        return content ? (
          <Typography.Text copyable={{ text: content }} ellipsis={{ tooltip: content }}>
            {content}
          </Typography.Text>
        ) : (
          '-'
        );
      },
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
      responsive: ['md', 'lg', 'xl', 'xxl'],
    },
    {
      title: '姓名',
      dataIndex: 'realName',
      hideInSearch: true,
      responsive: ['md', 'lg', 'xl', 'xxl'],
    },
    {
      title: '角色',
      dataIndex: 'roleNames',
      hideInSearch: true,
      responsive: ['lg', 'xl', 'xxl'],
      ellipsis: true,
      render: (_, record) => {
        const content = record.roleNames?.length ? record.roleNames.join(', ') : '';
        return content ? (
          <Typography.Text ellipsis={{ tooltip: content }}>{content}</Typography.Text>
        ) : (
          '-'
        );
      },
    },
    {
      title: '操作',
      valueType: 'option',
      fixed: responsive.isDesktop ? 'right' : undefined,
      width: 180,
      render: (_, record) => (
        <TableActionBar
          isMobile={responsive.isMobile}
          items={buildActions([
            {
              key: 'view',
              label: '详情',
              permission: 'system:user:view',
              onClick: () => void openDetail(record),
            },
            {
              key: 'edit',
              label: '编辑',
              permission: 'system:user:update',
              onClick: () => void openEdit(record),
            },
            {
              key: 'toggle',
              label: record.status === 'ENABLED' ? '禁用' : '启用',
              permission: 'system:user:status',
              hidden: isProtectedAdminAccount(record),
              danger: record.status === 'ENABLED',
              onClick: () => void handleStatusToggle(record),
            },
          ])}
        />
      ),
    },
    ],
    [buildActions, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <PageContainer title="用户管理" className="saas-management-page">
      <div className="saas-table-wrap">
        <ProTable<UserRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
          options={false}
          pagination={buildMobilePagination({ showSizeChanger: true }, responsive.isMobile)}
          scroll={buildTableScroll(columns, responsive.isMobile)}
          request={buildTableRequest((params) => userService.list(params, { autoRedirectOnUnauthorized: false }))}
          toolBarRender={() => [
            canAccess('system:user:create') ? (
              <Button key="create" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={openCreate}>
                新增用户
              </Button>
            ) : null,
            <Button key="refresh" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => actionRef.current?.reload()}>
              刷新
            </Button>,
          ]}
        />
      </div>

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
        <Form {...editorFormProps}>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]} normalize={trimString}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                name="password"
                label={editingId ? '重置密码（可选）' : '初始密码'}
                rules={!editingId ? [{ required: true, message: '请输入密码' }] : undefined}
              >
                <Input.Password placeholder="输入密码" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="mobile" label="手机号" rules={[{ validator: validateOptionalChinaMobile }]} normalize={trimString}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="idCardNumber" label="身份证号码" rules={[{ validator: validateOptionalChinaIdCard }]} normalize={trimString}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="nickname" label="昵称" normalize={trimString}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="realName" label="姓名" normalize={trimString}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '请输入有效邮箱地址' }]} normalize={trimString}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="avatarUrl" label="头像地址" normalize={trimString}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="birthMonth" label="出生年月">
                <DatePicker picker="month" placeholder="请选择出生年月" format="YYYY年MM月" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="gender" label="性别">
                <Select allowClear options={GENDER_OPTIONS} placeholder="请选择性别" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="region" label="所在地区" normalize={trimString}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="status" label="状态">
                <Select
                  disabled={protectedAdminSelected}
                  options={
                    protectedAdminSelected
                      ? [{ label: '启用', value: 'ENABLED' }]
                      : [
                          { label: '启用', value: 'ENABLED' },
                          { label: '禁用', value: 'DISABLED' },
                        ]
                  }
                />
              </Form.Item>
            </Col>
            <Col xs={24}>
              <Form.Item name="availableTime" label="可工作时间" normalize={trimString}>
                <Input.TextArea rows={2} placeholder="请输入可工作时间，如：周一至周五 09:00-18:00" />
              </Form.Item>
            </Col>
            <Col xs={24}>
              <Form.Item name="roleIds" label="角色">
                <Select mode="multiple" options={roleOptions} />
              </Form.Item>
            </Col>
          </Row>
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
              {...detailProps}
              columns={[
                { title: '用户名', dataIndex: 'username' },
              { title: '昵称', dataIndex: 'nickname', renderText: (value) => value || '-' },
              { title: '姓名', dataIndex: 'realName', renderText: (value) => value || '-' },
              { title: '手机号', dataIndex: 'mobile', renderText: (value) => maskMobile(value) || '-' },
              {
                title: '身份证号码',
                dataIndex: 'idCardNumber',
                renderText: (value) => maskIdCardNumber(value) || '-',
              },
              { title: '邮箱', dataIndex: 'email', renderText: (value) => value || '-' },
              { title: '头像地址', dataIndex: 'avatarUrl', renderText: (value) => value || '-' },
              { title: '出生年月', dataIndex: 'birthMonth', renderText: (value) => value || '-' },
              { title: '性别', dataIndex: 'gender', renderText: (value) => value || '-' },
              { title: '所在地区', dataIndex: 'region', renderText: (value) => value || '-' },
              { title: '可工作时间', dataIndex: 'availableTime', renderText: (value) => value || '-' },
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
              { title: '创建时间', dataIndex: 'createdAt', renderText: (value) => value || '-' },
              { title: '更新时间', dataIndex: 'updatedAt', renderText: (value) => value || '-' },
            ]}
          />
        ) : null}
      </Drawer>
    </PageContainer>
  );
};

export default UserManagementPage;
