import { ApartmentOutlined, ReloadOutlined } from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import { Button, Card, Empty, Form, Input, Spin, Tree, message } from 'antd';
import type { DataNode } from 'antd/es/tree';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
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
import type { DepartmentRecord, UserDetail, UserRecord } from '@/types/api';
import { confirmAction } from '@/utils/confirm';
import './users.css';

const ALL_DEPARTMENTS_KEY = 'all';

const departmentTreeKey = (id: number) => `dept-${id}`;

const parseDepartmentTreeKey = (key: unknown) => {
  const value = String(key);
  if (value === ALL_DEPARTMENTS_KEY) {
    return null;
  }
  if (!value.startsWith('dept-')) {
    return null;
  }
  const id = Number(value.slice(5));
  return Number.isFinite(id) ? id : null;
};

const flattenDepartments = (departments: DepartmentRecord[], depth = 0): { label: string; value: number }[] =>
  departments.flatMap((department) => [
    { label: `${'　'.repeat(depth)}${department.deptName}`, value: department.id },
    ...flattenDepartments(department.children || [], depth + 1),
  ]);

const flattenDepartmentIds = (department: DepartmentRecord): number[] => [
  department.id,
  ...(department.children || []).flatMap((child) => flattenDepartmentIds(child)),
];

const departmentTitleMatches = (department: DepartmentRecord, keyword: string) =>
  !keyword ||
  department.deptName.toLowerCase().includes(keyword) ||
  department.deptCode.toLowerCase().includes(keyword);

const buildDepartmentTreeNodes = (items: DepartmentRecord[], keyword: string): DataNode[] =>
  items
    .map((department) => {
      const children = buildDepartmentTreeNodes(department.children || [], keyword);
      const matched = departmentTitleMatches(department, keyword);
      if (keyword && !matched && children.length === 0) {
        return null;
      }
      return {
        key: departmentTreeKey(department.id),
        title: (
          <span className="saas-user-department-tree__node">
            <span className="saas-user-department-tree__name">{department.deptName}</span>
            <span className="saas-user-department-tree__count">{department.userCount ?? 0}</span>
          </span>
        ),
        children,
      };
    })
    .filter(Boolean) as DataNode[];

const UserManagementPage = () => {
  const { actionRef, drawer, detail, reloadTable } = useCrudPageState<UserRecord>();
  const [editorForm] = Form.useForm();
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const [selectedUserDetail, setSelectedUserDetail] = useState<UserDetail | null>(null);
  const [saving, setSaving] = useState(false);
  const [roleOptions, setRoleOptions] = useState<{ label: string; value: number }[]>([]);
  const [roleOptionsLoaded, setRoleOptionsLoaded] = useState(false);
  const [departmentOptions, setDepartmentOptions] = useState<{ label: string; value: number }[]>([]);
  const [departmentOptionsLoaded, setDepartmentOptionsLoaded] = useState(false);
  const [departments, setDepartments] = useState<DepartmentRecord[]>([]);
  const [departmentLoading, setDepartmentLoading] = useState(false);
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | null>(null);
  const [expandedDepartmentKeys, setExpandedDepartmentKeys] = useState<string[]>([ALL_DEPARTMENTS_KEY]);
  const [departmentKeyword, setDepartmentKeyword] = useState('');
  const protectedAdminSelected = isProtectedAdminAccount(drawer.currentRecord);
  const canSaveUser = actionPermission.can(drawer.editingId ? 'system:user:update' : 'system:user:create');
  const editorFormProps = useStandardFormProps({
    form: editorForm,
    initialValues: { status: 'ENABLED', roleIds: [], deptIds: [] },
  });
  const detailProps = useDetailProDescriptionsProps<UserDetail>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedUserDetail || undefined,
  });

  const loadDepartments = useCallback(async () => {
    setDepartmentLoading(true);
    try {
      const result = await iamService.departments({ autoRedirectOnUnauthorized: false });
      setDepartments(result);
      setDepartmentOptions(flattenDepartments(result));
      setDepartmentOptionsLoaded(true);
    } finally {
      setDepartmentLoading(false);
    }
  }, []);

  const ensureRoleOptionsLoaded = async () => {
    if (roleOptionsLoaded) {
      return;
    }
    const result = await iamService.roles({ pageNo: 1, pageSize: 200 }, { autoRedirectOnUnauthorized: false });
    setRoleOptions(
      (result.records || []).map((role) => ({
        label: role.roleName,
        value: role.id,
      })),
    );
    setRoleOptionsLoaded(true);
  };

  const ensureDepartmentOptionsLoaded = async () => {
    if (departmentOptionsLoaded) {
      return;
    }
    await loadDepartments();
  };

  const allDepartmentIds = useMemo(() => departments.flatMap((department) => flattenDepartmentIds(department)), [departments]);
  const normalizedDepartmentKeyword = departmentKeyword.trim().toLowerCase();
  const departmentTreeData = useMemo<DataNode[]>(
    () => [
      {
        key: ALL_DEPARTMENTS_KEY,
        title: (
          <span className="saas-user-department-tree__node">
            <span className="saas-user-department-tree__name">全部部门</span>
          </span>
        ),
        children: buildDepartmentTreeNodes(departments, normalizedDepartmentKeyword),
      },
    ],
    [departments, normalizedDepartmentKeyword],
  );
  const selectedDepartmentKey = selectedDepartmentId ? departmentTreeKey(selectedDepartmentId) : ALL_DEPARTMENTS_KEY;

  useEffect(() => {
    void loadDepartments();
  }, [loadDepartments]);

  useEffect(() => {
    setExpandedDepartmentKeys(
      normalizedDepartmentKeyword ? [ALL_DEPARTMENTS_KEY, ...allDepartmentIds.map(departmentTreeKey)] : [ALL_DEPARTMENTS_KEY],
    );
  }, [allDepartmentIds, normalizedDepartmentKeyword]);

  useEffect(() => {
    reloadTable();
  }, [reloadTable, selectedDepartmentId]);

  const openCreate = async () => {
    drawer.openCreate();
    editorForm.resetFields();
    editorForm.setFieldsValue({ status: 'ENABLED', roleIds: [], deptIds: [] });
    try {
      await Promise.all([ensureRoleOptionsLoaded(), ensureDepartmentOptionsLoaded()]);
    } catch {
      drawer.reset();
    }
  };

  const openEdit = async (record: UserRecord) => {
    drawer.openEdit(record, record.id);
    try {
      const [detailResult] = await Promise.all([
        userService.detail(record.id, { autoRedirectOnUnauthorized: false }),
        ensureRoleOptionsLoaded(),
        ensureDepartmentOptionsLoaded(),
      ]);
      editorForm.setFieldsValue({
        ...detailResult,
        birthMonth: detailResult.birthMonth ? dayjs(detailResult.birthMonth, 'YYYY-MM') : null,
        roleIds: detailResult.roleIds || [],
        deptIds: detailResult.deptIds || [],
        primaryDeptId: detailResult.primaryDeptId || null,
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
        deptIds: values.deptIds || [],
        primaryDeptId: values.primaryDeptId || values.deptIds?.[0] || null,
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
      await loadDepartments();
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

  const handleDelete = (record: UserRecord) => {
    confirmAction({
      title: '删除用户',
      content: `确认删除用户「${record.username}」吗？删除后该用户将从当前租户移除，已有会话会被下线。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await userService.delete(record.id, { autoRedirectOnUnauthorized: false });
        message.success('用户已删除');
        reloadTable();
        await loadDepartments();
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
        onDelete: (record) => void handleDelete(record),
        isProtectedAdminAccount,
      }),
    [actionPermission.buildTableActions, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <ManagementPage title="用户管理" className="saas-user-management-page">
      <div className="saas-user-management-layout">
        <Card
          className="saas-user-department-card"
          title={
            <span className="saas-user-department-card__title">
              <ApartmentOutlined />
              组织部门
            </span>
          }
          extra={
            <Button
              aria-label="刷新部门"
              type="text"
              icon={<ReloadOutlined />}
              loading={departmentLoading}
              onClick={() => void loadDepartments()}
            />
          }
        >
          <Input.Search
            allowClear
            className="saas-user-department-card__search"
            placeholder="搜索部门"
            value={departmentKeyword}
            onChange={(event) => setDepartmentKeyword(event.target.value)}
          />
          {departmentLoading && departments.length === 0 ? (
            <div className="saas-user-department-card__loading">
              <Spin />
            </div>
          ) : departmentTreeData[0]?.children?.length || !normalizedDepartmentKeyword ? (
            <Tree
              blockNode
              className="saas-user-department-tree"
              treeData={departmentTreeData}
              selectedKeys={[selectedDepartmentKey]}
              expandedKeys={expandedDepartmentKeys}
              onExpand={(keys) => setExpandedDepartmentKeys(keys.map(String))}
              onSelect={(_, info) => {
                setSelectedDepartmentId(parseDepartmentTreeKey(info.node.key));
              }}
            />
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无匹配部门" />
          )}
        </Card>

        <div className="saas-user-management-main">
          <ManagementTable<UserRecord>
            actionRef={actionRef}
            rowKey="id"
            columns={columns}
            isMobile={responsive.isMobile}
            search={searchConfig}
            request={buildTableRequest((params) =>
              userService.list(
                {
                  ...params,
                  deptId: selectedDepartmentId || undefined,
                },
                { autoRedirectOnUnauthorized: false },
              ),
            )}
            toolBarRender={() =>
              buildToolbarButtons([
                {
                  key: 'create',
                  permission: 'system:user:create',
                  type: 'primary',
                  label: '新增用户',
                  onClick: () => void openCreate(),
                },
                {
                  key: 'refresh',
                  label: '刷新',
                  onClick: reloadTable,
                },
              ])
            }
          />
        </div>
      </div>

      <ManagementDrawer
        title={drawer.editingId ? '编辑用户' : '新增用户'}
        open={drawer.open}
        onClose={drawer.close}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: drawer.close },
          { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canSaveUser, onClick: () => void saveUser() },
        ]}
      >
        <UserEditorForm
          formProps={editorFormProps}
          editingId={drawer.editingId}
          roleOptions={roleOptions}
          departmentOptions={departmentOptions}
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
