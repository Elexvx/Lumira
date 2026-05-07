import { ProDescriptions } from '@ant-design/pro-components';
import dayjs from 'dayjs';
import { Form, Spin, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { buildTableRequest } from '@/features/table/proTable';
import { buildUserColumns, userDetailColumns } from '@/pages/system/users/columns';
import { UserEditorForm } from '@/pages/system/users/components/UserEditorForm';
import { isProtectedAdminAccount } from '@/pages/system/users/constants';
import { iamService } from '@/services/iam';
import { userService } from '@/services/user';
import type { UserDetail, UserRecord } from '@/types/api';
import { confirmAction } from '@/utils/confirm';

const UserManagementPage = () => {
  const { actionRef, drawer, detail, reloadTable } = useCrudPageState<UserRecord>();
  const [editorForm] = Form.useForm();
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const [selectedUserDetail, setSelectedUserDetail] = useState<UserDetail | null>(null);
  const [saving, setSaving] = useState(false);
  const [roleOptions, setRoleOptions] = useState<{ label: string; value: number }[]>([]);
  const protectedAdminSelected = isProtectedAdminAccount(drawer.currentRecord);
  const editorFormProps = useStandardFormProps({
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
    drawer.openCreate();
    editorForm.resetFields();
    editorForm.setFieldsValue({ status: 'ENABLED', roleIds: [] });
  };

  const openEdit = async (record: UserRecord) => {
    drawer.openEdit(record, record.id);
    try {
      const detailResult = await userService.detail(record.id, { autoRedirectOnUnauthorized: false });
      editorForm.setFieldsValue({
        ...detailResult,
        birthMonth: detailResult.birthMonth ? dayjs(detailResult.birthMonth, 'YYYY-MM') : null,
        roleIds: detailResult.roleIds || [],
      });
    } catch {
      drawer.reset();
    }
  };

  const openDetail = async (record: UserRecord) => {
    detail.openDetail(record);
    detail.setLoading(true);
    try {
      const detailResult = await userService.detail(record.id, { autoRedirectOnUnauthorized: false });
      setSelectedUserDetail(detailResult);
    } catch {
      detail.setOpen(false);
      setSelectedUserDetail(null);
    } finally {
      detail.setLoading(false);
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

      if (drawer.editingId) {
        await userService.update(drawer.editingId, payload, { autoRedirectOnUnauthorized: false });
        message.success('用户已更新');
      } else {
        await userService.create(payload, { autoRedirectOnUnauthorized: false });
        message.success('用户已创建');
      }

      drawer.close();
      reloadTable();
    } finally {
      setSaving(false);
    }
  };

  const updateUserStatus = async (record: UserRecord, status: 'ENABLED' | 'DISABLED') => {
    await userService.changeStatus(record.id, { status }, { autoRedirectOnUnauthorized: false });
    message.success('状态已更新');
    reloadTable();
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

  const columns = useMemo(
    () =>
      buildUserColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        buildRowActions: actionPermission.buildTableActions,
        onOpenDetail: (record) => void openDetail(record),
        onOpenEdit: (record) => void openEdit(record),
        onToggleStatus: (record) => void handleStatusToggle(record),
        isProtectedAdminAccount,
      }),
    [actionPermission.buildTableActions, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <ManagementPage title="用户管理">
      <ManagementTable<UserRecord>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        isMobile={responsive.isMobile}
        search={searchConfig}
        request={buildTableRequest((params) => userService.list(params, { autoRedirectOnUnauthorized: false }))}
        toolBarRender={() =>
          buildToolbarButtons([
            {
              key: 'create',
              permission: 'system:user:create',
              type: 'primary',
              label: '新增用户',
              onClick: openCreate,
            },
            {
              key: 'refresh',
              label: '刷新',
              onClick: reloadTable,
            },
          ])
        }
      />

      <ManagementDrawer
        title={drawer.editingId ? '编辑用户' : '新增用户'}
        open={drawer.open}
        onClose={drawer.close}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: drawer.close },
          { key: 'save', label: '保存', type: 'primary', loading: saving, onClick: () => void saveUser() },
        ]}
      >
        <UserEditorForm
          formProps={editorFormProps}
          editingId={drawer.editingId}
          roleOptions={roleOptions}
          protectedAdminSelected={protectedAdminSelected}
        />
      </ManagementDrawer>

      <ManagementDrawer
        title={detail.currentRecord ? `用户详情 · ${detail.currentRecord.username}` : '用户详情'}
        open={detail.open}
        onClose={() => {
          detail.close();
          setSelectedUserDetail(null);
        }}
      >
        {detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : selectedUserDetail ? (
          <ProDescriptions<UserDetail> {...detailProps} columns={userDetailColumns} />
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default UserManagementPage;
