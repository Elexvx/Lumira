import { ProDescriptions } from '@ant-design/pro-components';
import { Form, Input, Modal, Select, Space, Spin, Tag, Tree, Typography, message } from 'antd';
import type { TreeProps } from 'antd';
import { useMemo, useState } from 'react';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { buildTableRequest } from '@/features/table/proTable';
import { ROLE_TYPE_OPTIONS } from '@/constants/role';
import { buildRoleColumns, roleDetailColumns } from '@/pages/system/roles/columns';
import { RolePermissionEditor } from '@/pages/system/roles/components/RolePermissionEditor';
import { useRolePermissionEditor } from '@/pages/system/roles/hooks/useRolePermissionEditor';
import { buildRolePermissionDisplayGroups } from '@/pages/system/rolesPermissionTree';
import type { NormalizedPermissionTreeRecord } from '@/pages/system/rolesPermissionTree';
import { iamService } from '@/services/iam';
import type { RoleDataScope, RoleDetail, RoleRecord } from '@/types/api';
import { confirmAction } from '@/utils/confirm';
import './roles.css';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';


const ROLE_CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,63}$/;
const DEFAULT_DATA_SCOPES: RoleDataScope[] = [{ resourceCode: '*', scopeType: 'SELF' }];
const DATA_SCOPE_OPTIONS = [
  { label: '全部数据', value: 'ALL' },
  { label: '本租户数据', value: 'TENANT' },
  { label: '本部门数据', value: 'DEPT' },
  { label: '本部门及下级', value: 'DEPT_AND_CHILD' },
  { label: '仅本人数据', value: 'SELF' },
  { label: '自定义范围', value: 'CUSTOM' },
];
const DATA_SCOPE_LABELS = DATA_SCOPE_OPTIONS.reduce<Record<string, string>>((acc, item) => {
  acc[item.value] = item.label;
  return acc;
}, {});
type RoleEditorMode = 'create' | 'edit' | 'permissions';

const DEFAULT_HOME_PATH = '/dashboard/home';

const collectDefaultHomeOptions = (nodes: NormalizedPermissionTreeRecord[] = []) => {
  const options: { label: string; value: string }[] = [];
  const seen = new Set<string>();
  const walk = (items: NormalizedPermissionTreeRecord[]) => {
    items.forEach((item) => {
      if (item.nodeType === 'PAGE' && item.selectable && item.routePath && !seen.has(item.routePath)) {
        seen.add(item.routePath);
        options.push({ label: `${item.pageName}（${item.routePath}）`, value: item.routePath });
      }
      if (item.children?.length) {
        walk(item.children);
      }
    });
  };
  walk(nodes);
  return options;
};

const formatPermissionGroupLabel = (permissionGroup: string) =>
  (
    {
      audit: '审计',
      dashboard: '首页',
      iam: 'IAM',
      message: '消息',
      plugin: '插件',
      profile: '个人中心',
      system: '系统',
      tenant: '平台',
    } as Record<string, string>
  )[permissionGroup] || permissionGroup;

const RoleManagementPage = () => {
  const roleCrud = useCrudPageState<RoleRecord>();
  const [editorForm] = Form.useForm();
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const [selectedRoleDetail, setSelectedRoleDetail] = useState<RoleDetail | null>(null);
  const [editorDirty, setEditorDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [roleEditorMode, setRoleEditorMode] = useState<RoleEditorMode>('create');
  const [defaultRoleModalOpen, setDefaultRoleModalOpen] = useState(false);
  const [defaultRoleOptions, setDefaultRoleOptions] = useState<RoleRecord[]>([]);
  const [defaultRoleId, setDefaultRoleId] = useState<number | undefined>();
  const [defaultRoleLoading, setDefaultRoleLoading] = useState(false);
  const [defaultRoleSaving, setDefaultRoleSaving] = useState(false);
  const canSaveRole =
    roleEditorMode === 'permissions'
      ? actionPermission.can('system:role:permissions')
      : actionPermission.can(roleCrud.drawer.editingId ? 'system:role:update' : 'system:role:create');
  const canUpdateRoleSettings = actionPermission.can('system:role:update');
  const isPermissionOnlyEditor = roleEditorMode === 'permissions';
  const editorFormProps = useStandardFormProps({
    form: editorForm,
    initialValues: { roleType: 'CUSTOM', defaultHomePath: DEFAULT_HOME_PATH, permissionKeys: [], dataScopes: DEFAULT_DATA_SCOPES },
    onValuesChange: () => setEditorDirty(true),
    className: 'role-editor-form',
  });
  const detailProps = useDetailProDescriptionsProps<RoleDetail>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedRoleDetail || undefined,
  });
  const permissionEditor = useRolePermissionEditor({
    form: editorForm,
    editorOpen: roleCrud.drawer.open,
    onDirty: () => setEditorDirty(true),
  });
  const permissionDetailGroups = useMemo(
    () =>
      buildRolePermissionDisplayGroups(
        permissionEditor.pageTreeData,
        selectedRoleDetail?.permissionKeys || [],
        permissionEditor.permissionCatalogMap,
      ),
    [permissionEditor.pageTreeData, permissionEditor.permissionCatalogMap, selectedRoleDetail?.permissionKeys],
  );
  const defaultHomeOptions = useMemo(() => collectDefaultHomeOptions(permissionEditor.pageTreeData), [permissionEditor.pageTreeData]);

  const permissionDetailTreeData = useMemo<TreeProps['treeData']>(
    () =>
      permissionDetailGroups.map((group) => ({
        key: `group:${group.permissionGroup}`,
        title: (
          <div className="role-page-row role-permission-detail__group-row">
            <span className="role-page-row__name">分类：{formatPermissionGroupLabel(group.permissionGroup)}</span>
          </div>
        ),
        children: group.pages.map((page) => ({
          key: `page:${group.permissionGroup}:${page.pageKey}`,
          title: (
            <div className="role-permission-detail__page-tree-row">
              <div className="role-page-row">
                <span className="role-page-row__name">{page.pageName}</span>
                <span className="role-page-row__meta">
                  {page.routePath ? <span className="role-page-row__route">{page.routePath}</span> : null}
                  <Tag color="blue">{page.permissions.some((item) => item.isPagePermission) ? '页面' : '权限'}</Tag>
                </span>
              </div>
              <Space wrap size={[8, 8]} className="role-permission-detail__tags">
                {page.permissions.map((item) => (
                  <Tag key={item.permissionKey} color="green">
                    {item.permissionName}
                  </Tag>
                ))}
              </Space>
            </div>
          ),
        })),
      })),
    [permissionDetailGroups],
  );

  const handleRoleCodeBlur = () => {
    const currentRoleCode = editorForm.getFieldValue('roleCode');
    if (typeof currentRoleCode !== 'string') {
      return;
    }
    const trimmedRoleCode = currentRoleCode.trim();
    if (trimmedRoleCode !== currentRoleCode) {
      editorForm.setFieldsValue({ roleCode: trimmedRoleCode });
    }
  };

  const closeEditorDrawer = () => {
    roleCrud.drawer.close();
    setRoleEditorMode('create');
    permissionEditor.setEditorLoading(false);
    setEditorDirty(false);
    permissionEditor.resetEditorPermissionState();
  };

  const openCreate = () => {
    roleCrud.drawer.openCreate();
    setRoleEditorMode('create');
    permissionEditor.resetEditorPermissionState();
    setEditorDirty(false);
    permissionEditor.setEditorLoading(false);
    editorForm.resetFields();
    editorForm.setFieldsValue({ roleType: 'CUSTOM', defaultHomePath: DEFAULT_HOME_PATH, permissionKeys: [], dataScopes: DEFAULT_DATA_SCOPES });
  };

  const openDefaultRoleModal = async () => {
    setDefaultRoleModalOpen(true);
    setDefaultRoleLoading(true);
    try {
      const [defaultRole, rolePage] = await Promise.all([
        iamService.defaultRegistrationRole(API_OPTS.NO_REDIRECT),
        iamService.roles({ pageNo: 1, pageSize: 200 }, API_OPTS.NO_REDIRECT),
      ]);
      setDefaultRoleId(defaultRole.id);
      setDefaultRoleOptions(rolePage.records || []);
    } catch {
      message.error('默认注册角色加载失败，请稍后重试');
    } finally {
      setDefaultRoleLoading(false);
    }
  };

  const saveDefaultRole = async () => {
    if (!defaultRoleId) {
      message.warning('请选择默认注册角色');
      return;
    }
    setDefaultRoleSaving(true);
    try {
      await iamService.updateDefaultRegistrationRole({ roleId: defaultRoleId }, API_OPTS.NO_REDIRECT);
      message.success('默认注册角色已更新');
      setDefaultRoleModalOpen(false);
      roleCrud.reloadTable();
    } finally {
      setDefaultRoleSaving(false);
    }
  };

  const openEdit = async (record: RoleRecord, mode: RoleEditorMode = 'edit') => {
    roleCrud.drawer.openEdit(record, record.id);
    setRoleEditorMode(mode);
    permissionEditor.resetEditorPermissionState();
    permissionEditor.setEditorLoading(true);
    setEditorDirty(false);

    try {
      const detail = await iamService.roleDetail(record.id, API_OPTS.NO_REDIRECT);
      const permissionKeys = permissionEditor.sanitizePermissionKeys(detail.permissionKeys || []);
      editorForm.setFieldsValue({
        ...detail,
        defaultHomePath: detail.defaultHomePath || DEFAULT_HOME_PATH,
        permissionKeys,
        dataScopes: detail.dataScopes?.length ? detail.dataScopes : DEFAULT_DATA_SCOPES,
      });
      permissionEditor.syncActivePageByPermissionKeys(permissionKeys);
    } catch {
      message.error('加载角色信息失败，请稍后重试');
      roleCrud.drawer.close();
    } finally {
      permissionEditor.setEditorLoading(false);
    }
  };

  const openDetail = async (record: RoleRecord) => {
    roleCrud.detail.openDetail(record);
    roleCrud.detail.setLoading(true);
    try {
      const detail = await iamService.roleDetail(record.id, API_OPTS.NO_REDIRECT);
      setSelectedRoleDetail(detail);
    } finally {
      roleCrud.detail.setLoading(false);
    }
  };

  const saveRole = async () => {
    setSaving(true);
    try {
      const values = await editorForm.validateFields();
      const permissionKeys = permissionEditor.sanitizePermissionKeys(values.permissionKeys || []);
      if (roleCrud.drawer.editingId && roleEditorMode === 'permissions') {
        await iamService.updateRolePermissions(roleCrud.drawer.editingId, permissionKeys, API_OPTS.NO_REDIRECT);
        message.success('角色权限已更新');
        closeEditorDrawer();
        roleCrud.reloadTable();
        return;
      }
      const payload = {
        ...values,
        roleCode: typeof values.roleCode === 'string' ? values.roleCode.trim() : values.roleCode,
        defaultHomePath: typeof values.defaultHomePath === 'string' ? values.defaultHomePath.trim() : DEFAULT_HOME_PATH,
        permissionKeys,
        dataScopes: values.dataScopes?.length ? values.dataScopes : DEFAULT_DATA_SCOPES,
      };
      if (roleCrud.drawer.editingId) {
        await iamService.updateRole(roleCrud.drawer.editingId, payload, API_OPTS.NO_REDIRECT);
        message.success('角色已更新');
      } else {
        await iamService.createRole(payload, API_OPTS.NO_REDIRECT);
        message.success('角色已创建');
      }
      closeEditorDrawer();
      roleCrud.reloadTable();
    } finally {
      setSaving(false);
    }
  };

  const deleteRole = (record: RoleRecord) => {
    confirmAction({
      title: '删除角色',
      content: `确认删除角色「${record.roleName}」吗？删除后该角色的权限配置会一并移除。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await iamService.deleteRole(record.id, API_OPTS.NO_REDIRECT);
        message.success('角色已删除');
        roleCrud.reloadTable();
      },
    });
  };

  const handleEditorClose = () => {
    if (!editorDirty) {
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

  const columns = useMemo(
    () =>
      buildRoleColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        buildRowActions: actionPermission.buildTableActions,
        onOpenDetail: (record) => void openDetail(record),
        onOpenEdit: (record) => void openEdit(record),
        onOpenPermissions: (record) => void openEdit(record, 'permissions'),
        onDelete: deleteRole,
      }),
    [actionPermission.buildTableActions, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <ManagementPage title="角色管理">
      <ManagementTable<RoleRecord>
        actionRef={roleCrud.actionRef}
        rowKey="id"
        columns={columns}
        isMobile={responsive.isMobile}
        search={searchConfig}
        request={buildTableRequest((params) => iamService.roles(params, API_OPTS.NO_REDIRECT))}
        toolBarRender={() =>
          buildToolbarButtons([
            {
              key: 'create',
              permission: 'system:role:create',
              type: 'primary',
              label: '新增角色',
              onClick: openCreate,
            },
            {
              key: 'default-registration-role',
              permission: 'system:role:update',
              label: '默认注册角色',
              onClick: () => void openDefaultRoleModal(),
            },
            {
              key: 'refresh',
              label: '刷新',
              onClick: roleCrud.reloadTable,
            },
          ])
        }
      />

      <Modal
        title="默认注册角色"
        open={defaultRoleModalOpen}
        confirmLoading={defaultRoleSaving}
        onOk={() => void saveDefaultRole()}
        onCancel={() => setDefaultRoleModalOpen(false)}
        okButtonProps={{ disabled: !canUpdateRoleSettings }}
        okText="保存"
        cancelText="取消"
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Typography.Text type="secondary">
            新用户通过注册或验证码自动创建后，会默认绑定该角色；后续仍可在用户管理中单独调整角色。
          </Typography.Text>
          <Select
            showSearch
            loading={defaultRoleLoading}
            value={defaultRoleId}
            onChange={setDefaultRoleId}
            placeholder="请选择默认注册角色"
            optionFilterProp="label"
            style={{ width: '100%' }}
            options={defaultRoleOptions.map((role) => ({
              label: `${role.roleName}（${role.roleCode}）`,
              value: role.id,
            }))}
          />
        </Space>
      </Modal>

      <ManagementDrawer
        title={roleEditorMode === 'permissions' ? '分配角色权限' : roleCrud.drawer.editingId ? '编辑角色 / 分配权限' : '新增角色'}
        open={roleCrud.drawer.open}
        onClose={handleEditorClose}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: handleEditorClose },
          { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canSaveRole, onClick: () => void saveRole() },
        ]}
      >
        <Form {...editorFormProps}>
          <Form.Item
            name="roleCode"
            label="角色编码"
            rules={[
              {
                validator: (_, value) => {
                  const roleCode = typeof value === 'string' ? value.trim() : '';
                  if (!roleCode) {
                    return Promise.reject(new Error('请输入角色编码'));
                  }
                  if (roleCode.length > 64) {
                    return Promise.reject(new Error('角色编码长度不能超过64个字符'));
                  }
                  if (!ROLE_CODE_PATTERN.test(roleCode)) {
                    return Promise.reject(new Error('角色编码只能由字母、数字和下划线组成，且必须以字母开头'));
                  }
                  return Promise.resolve();
                },
              },
            ]}
          >
            <Input maxLength={64} disabled={isPermissionOnlyEditor} onBlur={handleRoleCodeBlur} />
          </Form.Item>
          <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input disabled={isPermissionOnlyEditor} />
          </Form.Item>
          <Form.Item name="roleType" label="角色类型" rules={[{ required: true, message: '请选择角色类型' }]}>
            <Select disabled={isPermissionOnlyEditor} options={ROLE_TYPE_OPTIONS as unknown as { label: string; value: string }[]} />
          </Form.Item>
          <Form.Item
            name="defaultHomePath"
            label="默认访问页面"
            rules={[{ required: true, message: '请选择默认访问页面' }]}
          >
            <Select
              showSearch
              disabled={isPermissionOnlyEditor}
              classNames={{ popup: { root: 'role-default-home-select-popup' } }}
              getPopupContainer={(trigger) => trigger.parentElement || document.body}
              listHeight={360}
              optionFilterProp="label"
              options={defaultHomeOptions}
              placeholder="请选择登录后的默认访问页面"
              styles={{ popup: { list: { maxHeight: 360, overflowY: 'auto' } } }}
            />
          </Form.Item>
          <Form.Item name={['dataScopes', 0, 'resourceCode']} hidden initialValue="*" />
          <Form.Item
            name={['dataScopes', 0, 'scopeType']}
            label="数据范围"
            rules={[{ required: true, message: '请选择数据范围' }]}
          >
            <Select disabled={isPermissionOnlyEditor} options={DATA_SCOPE_OPTIONS} />
          </Form.Item>
          <RolePermissionEditor
            permissionTree={permissionEditor.permissionTree}
            permissionTreeLoading={permissionEditor.permissionTreeLoading}
            editorLoading={permissionEditor.editorLoading}
            pageTreeData={permissionEditor.pageTreeData}
            selectedPageNodeKeys={permissionEditor.selectedPageNodeKeys}
            selectedPageCount={permissionEditor.selectedPageCount}
            totalPageCount={permissionEditor.totalPageCount}
            activePageKey={permissionEditor.activePageKey}
            activePageNode={permissionEditor.activePageNode}
            activePageActionPermissions={permissionEditor.activePageActionPermissions}
            activePageSelectedActionKeys={permissionEditor.activePageSelectedActionKeys}
            isActivePageSelected={permissionEditor.isActivePageSelected}
            expandedKeys={permissionEditor.expandedKeys}
            onExpandChange={permissionEditor.setExpandedKeys}
            onExpandToggle={permissionEditor.handleExpandToggle}
            onSelectAllPages={permissionEditor.handleSelectAllPages}
            onPageTreeCheck={permissionEditor.handlePageTreeCheck}
            onActivePageChange={permissionEditor.setActivePageKey}
            onActionPermissionsChange={permissionEditor.handleActionPermissionsChange}
          />
        </Form>
      </ManagementDrawer>

      <ManagementDrawer
        title={roleCrud.detail.currentRecord ? `角色详情 · ${roleCrud.detail.currentRecord.roleName}` : '角色详情'}
        open={roleCrud.detail.open}
        onClose={() => {
          roleCrud.detail.close();
          setSelectedRoleDetail(null);
        }}
      >
        {roleCrud.detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : selectedRoleDetail ? (
          <>
            <ProDescriptions<RoleDetail> {...detailProps} columns={roleDetailColumns} />
            <div style={{ marginTop: 16 }}>
              <Space wrap size={[8, 8]}>
                <Typography.Text strong>数据范围</Typography.Text>
                {(selectedRoleDetail.dataScopes?.length ? selectedRoleDetail.dataScopes : DEFAULT_DATA_SCOPES).map((scope) => (
                  <Tag key={`${scope.resourceCode}:${scope.scopeType}`} color="purple">
                    {scope.resourceCode === '*' ? '全局' : scope.resourceCode} · {DATA_SCOPE_LABELS[scope.scopeType] || scope.scopeType}
                  </Tag>
                ))}
              </Space>
            </div>
            <div style={{ marginTop: 16 }}>
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Typography.Text strong>当前权限</Typography.Text>
                {permissionDetailTreeData?.length ? (
                  <div className="role-permission-tree role-permission-detail-tree">
                    <Tree
                      blockNode
                      defaultExpandAll
                      selectable={false}
                      showIcon={false}
                      treeData={permissionDetailTreeData}
                    />
                  </div>
                ) : (
                  <Tag>暂无权限</Tag>
                )}
              </Space>
            </div>
          </>
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default RoleManagementPage;
