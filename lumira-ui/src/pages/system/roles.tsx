import { Button, Checkbox, Empty, Form, Input, Modal, Select, Space, Spin, Tag, Tree, Typography } from 'antd';
import { ProDescriptions } from '@ant-design/pro-components';
import { useMemo } from 'react';
import { ManagementDrawer, type ManagementDrawerAction } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useRoleManagementPageData } from '@/pages/system/roles/hooks/useRoleManagementPageData';
import { ROLE_TYPE_LABEL_MAP, ROLE_TYPE_OPTIONS } from '@/constants/role';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import type { TreeProps } from 'antd';
import type { NormalizedPermissionTreeRecord } from '@/pages/system/rolesPermissionTree/normalize';
import type { PermissionActionRecord, PermissionTreeRecord, RoleDataScope } from '@/types/api';
import './roles.css';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const DATA_SCOPE_OPTIONS: Array<{ label: string; value: 'ALL' | 'TENANT' | 'DEPT' | 'DEPT_AND_CHILD' | 'SELF' | 'CUSTOM' }> = [
  { label: t('全部数据', 'All data'), value: 'ALL' },
  { label: t('本租户数据', 'Current tenant data'), value: 'TENANT' },
  { label: t('本部门数据', 'Current department data'), value: 'DEPT' },
  { label: t('本部门及下级', 'Current department and descendants'), value: 'DEPT_AND_CHILD' },
  { label: t('仅本人数据', 'My data only'), value: 'SELF' },
  { label: t('自定义范围', 'Custom scope'), value: 'CUSTOM' },
];
const DATA_SCOPE_LABELS = DATA_SCOPE_OPTIONS.reduce<Record<string, string>>((acc, item) => {
  acc[item.value] = item.label;
  return acc;
}, {});
const DEFAULT_DATA_SCOPES: RoleDataScope[] = [{ resourceCode: '*', scopeType: 'SELF' }];

const formatPermissionGroupLabel = (permissionGroup: string) =>
  (
    {
      audit: t('审计', 'Audit'),
      dashboard: t('首页', 'Dashboard'),
      iam: 'IAM',
      message: t('消息', 'Messages'),
      plugin: t('插件', 'Plugins'),
      profile: t('个人中心', 'Profile'),
      system: t('系统', 'System'),
      tenant: t('平台', 'Platform'),
    } as Record<string, string>
  )[permissionGroup] || permissionGroup;

const DefaultRegistrationRoleModal = ({
  open,
  loading,
  saving,
  canSave,
  value,
  options,
  onChange,
  onSubmit,
  onCancel,
  isMobile,
}: {
  open: boolean;
  loading: boolean;
  saving: boolean;
  canSave: boolean;
  value?: number;
  options: Array<{ id: number; roleName: string; roleCode: string }>;
  onChange: (roleId?: number) => void;
  onSubmit: () => void;
  onCancel: () => void;
  isMobile: boolean;
}) => {
  const modalFooterGap = resolveResponsiveValue(APP_SPACING.modalFooterGap, isMobile);

  return (
    <Modal
      title={t('默认注册角色', 'Default registration role')}
      open={open}
      confirmLoading={saving}
      onOk={onSubmit}
      onCancel={onCancel}
      okButtonProps={{ disabled: !canSave }}
      okText={t('保存', 'Save')}
      cancelText={t('取消', 'Cancel')}
    >
      <Space direction="vertical" size={modalFooterGap} style={{ width: '100%' }}>
        <Typography.Text type="secondary">{t('新用户通过注册或验证码自动创建后，会默认绑定该角色；后续仍可在用户管理中单独调整角色。', 'New users created through registration or verification code will be bound to this role by default; you can adjust roles later in user management.')}</Typography.Text>
        <Select
          showSearch
          loading={loading}
          value={value}
          onChange={onChange}
          placeholder={t('请选择默认注册角色', 'Select default registration role')}
          optionFilterProp="label"
          style={{ width: '100%' }}
          options={options.map((role) => ({
            label: `${role.roleName}（${role.roleCode}）`,
            value: role.id,
          }))}
        />
      </Space>
    </Modal>
  );
};

const RolePermissionEditor = ({
  permissionTree,
  permissionTreeLoading,
  editorLoading,
  pageTreeData,
  selectedPageNodeKeys,
  selectedPageCount,
  totalPageCount,
  activePageKey,
  activePageNode,
  activePageActionPermissions,
  activePageSelectedActionKeys,
  isActivePageSelected,
  expandedKeys,
  onExpandChange,
  onExpandToggle,
  onSelectAllPages,
  onPageTreeCheck,
  onActivePageChange,
  onActionPermissionsChange,
  isMobile,
}: {
  permissionTree: PermissionTreeRecord[];
  permissionTreeLoading: boolean;
  editorLoading: boolean;
  pageTreeData: NormalizedPermissionTreeRecord[];
  selectedPageNodeKeys: string[];
  selectedPageCount: number;
  totalPageCount: number;
  activePageKey: string | null;
  activePageNode: NormalizedPermissionTreeRecord | null;
  activePageActionPermissions: PermissionActionRecord[];
  activePageSelectedActionKeys: string[];
  isActivePageSelected: boolean;
  expandedKeys: string[];
  onExpandChange: (keys: string[]) => void;
  onExpandToggle: () => void;
  onSelectAllPages: () => void;
  onPageTreeCheck: (keys: string[]) => void;
  onActivePageChange: (pageKey: string | null) => void;
  onActionPermissionsChange: (keys: string[]) => void;
  isMobile: boolean;
}) => {
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);

  if (editorLoading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: 'var(--saas-spacing-420)' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <>
      <Form.Item name="permissionKeys" hidden>
        <Input />
      </Form.Item>
      <div className="role-editor-grid">
        <section className="role-editor-section">
          <div className="role-editor-section__header">
            <div>
              <div className="role-editor-section__title">{t('页面路由权限', 'Page route permissions')}</div>
              <div className="role-editor-section__meta">{t('先勾选可访问的页面，目录节点仅用于分组，再配置该页面下的按钮权限', 'First select accessible pages. Directory nodes are for grouping only, then configure button permissions.')}</div>
            </div>
            <Space>
              <Button size="small" onClick={onExpandToggle}>
                {expandedKeys.length ? t('折叠全部', 'Collapse all') : t('展开全部', 'Expand all')}
              </Button>
              <Button size="small" onClick={onSelectAllPages}>
                {selectedPageCount === totalPageCount ? t('全不选', 'Deselect all') : t('全选', 'Select all')}
              </Button>
            </Space>
          </div>
          <div className="role-permission-tree">
            {permissionTreeLoading ? (
              <div style={{ display: 'grid', placeItems: 'center', minHeight: 'var(--saas-spacing-320)' }}>
                <Spin />
              </div>
            ) : pageTreeData.length ? (
              <Tree
                checkable
                blockNode
                selectable
                treeData={pageTreeData}
                checkedKeys={selectedPageNodeKeys}
                selectedKeys={activePageKey ? [activePageKey] : []}
                expandedKeys={expandedKeys}
                onExpand={(nextExpandedKeys) => onExpandChange(nextExpandedKeys.map(String))}
                onCheck={(checkedKeys: Parameters<NonNullable<TreeProps['onCheck']>>[0], info) => {
                  const nextCheckedKeys = Array.isArray(checkedKeys) ? checkedKeys.map(String) : [];
                  onPageTreeCheck(nextCheckedKeys);
                  if ((info.node as NormalizedPermissionTreeRecord).selectable && (info.node as NormalizedPermissionTreeRecord).pageKey) {
                    onActivePageChange((info.node as NormalizedPermissionTreeRecord).pageKey || null);
                  }
                }}
                onSelect={(_, info) => {
                  if ((info.node as NormalizedPermissionTreeRecord).selectable && (info.node as NormalizedPermissionTreeRecord).pageKey) {
                    onActivePageChange((info.node as NormalizedPermissionTreeRecord).pageKey || null);
                  }
                }}
              />
            ) : (
              <Empty description={t('暂无可配置页面权限', 'No page permissions available')} style={{ padding: 'var(--saas-spacing-48) 0' }} />
            )}
          </div>
        </section>

        <section className="role-editor-section role-action-panel">
          <div className="role-editor-section__header">
            <div>
              <div className="role-editor-section__title">{t('页面动作权限', 'Page action permissions')}</div>
              <div className="role-editor-section__meta">{t('按钮权限仅在页面权限勾选后生效', 'Button permissions take effect only after page permissions are selected')}</div>
            </div>
          </div>

          {permissionTree.length ? (
            <>
              <div className="role-action-panel__page-name">
                {activePageNode?.pageName || t('请从左侧选择页面', 'Select a page from the left')}
                {activePageNode?.routeMatched && activePageNode?.routePath ? (
                  <Tag style={{ marginInlineStart: tagWrapGap[0] }} color="blue">
                    {activePageNode?.routePath}
                  </Tag>
                ) : activePageNode?.nodeType === 'PAGE' ? (
                  <Tag style={{ marginInlineStart: tagWrapGap[0] }} color="red">
                    {t('路由失配', 'Route mismatch')}
                  </Tag>
                ) : null}
              </div>
              {activePageNode ? (
                activePageActionPermissions.length ? (
                  <Checkbox.Group
                    value={activePageSelectedActionKeys}
                    onChange={(checkedValues) => onActionPermissionsChange(checkedValues.map(String))}
                    className="role-action-grid"
                    disabled={!isActivePageSelected}
                    options={activePageActionPermissions.map((item) => ({
                      label: item.permissionName,
                      value: item.permissionKey,
                    }))}
                  />
                ) : (
                  <div className="role-action-panel__empty">
                    <Empty description={t('该页面暂无子权限', 'No child permissions on this page')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  </div>
                )
              ) : (
                <div className="role-action-panel__empty">
                  <Empty description={t('请从左侧页面权限树中选择一个页面', 'Select a page from the left permission tree')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                </div>
              )}
            </>
          ) : (
            <div className="role-action-panel__empty">
              <Empty description={t('请先在上方勾选一个页面', 'Please select a page above first')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
            </div>
          )}
        </section>
      </div>
    </>
  );
};

const RoleManagementPage = () => {
  const {
    roleCrud,
    searchConfig,
    buildToolbarButtons,
    responsive,
    columns,
    tableRequest,
    defaultRoleModal,
    openDefaultRoleModal,
    permissionEditor,
    roleActions,
    permissionDetailGroups,
    defaultHomeOptions,
  } = useRoleManagementPageData();
  const permissionDetailTreeData = useMemo<TreeProps['treeData']>(
    () =>
      permissionDetailGroups.map((group) => ({
        key: `group:${group.permissionGroup}`,
        title: (
          <div className="role-page-row role-permission-detail__group-row">
            <span className="role-page-row__name">{t('分类', 'Group')}: {formatPermissionGroupLabel(group.permissionGroup)}</span>
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
                  <Tag color="blue">{page.permissions.some((item) => item.isPagePermission) ? t('页面', 'Page') : t('权限', 'Permission')}</Tag>
                </span>
              </div>
              <Space wrap size={resolveResponsiveValue(APP_SPACING.tagWrapGap, responsive.isMobile)} className="role-permission-detail__tags">
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
    [permissionDetailGroups, responsive.isMobile],
  );
  const editorDrawer = {
    open: roleCrud.drawer.open,
    title: roleActions.roleEditorMode === 'permissions' ? t('分配角色权限', 'Assign role permissions') : roleCrud.drawer.editingId ? t('编辑角色 / 分配权限', 'Edit role / Assign permissions') : t('新增角色', 'Add role'),
    onClose: roleActions.handleEditorClose,
    footerActions: [
      { key: 'cancel', label: t('取消', 'Cancel'), onClick: roleActions.handleEditorClose },
      { key: 'save', label: t('保存', 'Save'), type: 'primary' as const, loading: roleActions.saving, disabled: !roleActions.canSaveRole, onClick: () => void roleActions.saveRole() },
    ] as ManagementDrawerAction[],
    formProps: roleActions.editorFormProps,
    isPermissionOnlyEditor: roleActions.isPermissionOnlyEditor,
    handleRoleCodeBlur: roleActions.handleRoleCodeBlur,
    defaultHomeOptions,
    permissionEditor,
  };
  const detailDrawer: {
    open: boolean;
    title: string;
    onClose: () => void;
    loading: boolean;
    selectedRoleDetail: typeof roleActions.selectedRoleDetail;
    column: 1 | 2;
    permissionDetailTreeData: typeof permissionDetailTreeData;
  } = {
    open: roleCrud.detail.open,
    title: roleActions.selectedRoleDetail ? `${t('角色详情', 'Role details')} · ${roleActions.selectedRoleDetail.roleName}` : t('角色详情', 'Role details'),
    onClose: () => {
      roleCrud.detail.close();
      roleActions.setSelectedRoleDetail(null);
    },
    loading: roleCrud.detail.loading,
    selectedRoleDetail: roleActions.selectedRoleDetail,
    column: responsive.isMobile ? 1 : 2,
    permissionDetailTreeData,
  };

  return (
    <ManagementPage title={t('角色管理', 'Role management')}>
      <ManagementPageBody>
        <ManagementTable
          actionRef={roleCrud.actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          search={searchConfig}
          request={tableRequest}
          toolBarRender={() =>
            buildToolbarButtons([
              {
                key: 'create',
                permission: 'system:role:create',
                type: 'primary',
                label: t('新增角色', 'Add role'),
                onClick: roleCrud.drawer.openCreate,
              },
              {
                key: 'default-registration-role',
                permission: 'system:role:update',
                label: t('默认注册角色', 'Default registration role'),
                onClick: () => void openDefaultRoleModal(),
              },
              {
                key: 'refresh',
                label: t('刷新', 'Refresh'),
                onClick: roleCrud.reloadTable,
              },
            ])
          }
        />
      </ManagementPageBody>

      <DefaultRegistrationRoleModal
        open={defaultRoleModal.open}
        loading={defaultRoleModal.loading}
        saving={defaultRoleModal.saving}
        canSave={defaultRoleModal.canSave}
        value={defaultRoleModal.value}
        options={defaultRoleModal.options}
        onChange={defaultRoleModal.onChange}
        onSubmit={defaultRoleModal.onSubmit}
        onCancel={defaultRoleModal.onCancel}
        isMobile={responsive.isMobile}
      />

      <ManagementDrawer title={editorDrawer.title} open={editorDrawer.open} onClose={editorDrawer.onClose} footerActions={editorDrawer.footerActions}>
        <Form {...editorDrawer.formProps}>
          <RoleEditorBasicFields
            isPermissionOnlyEditor={editorDrawer.isPermissionOnlyEditor}
            handleRoleCodeBlur={editorDrawer.handleRoleCodeBlur}
            defaultHomeOptions={editorDrawer.defaultHomeOptions}
          />
          <RolePermissionEditor
            permissionTree={editorDrawer.permissionEditor.permissionTree}
            permissionTreeLoading={editorDrawer.permissionEditor.permissionTreeLoading}
            editorLoading={editorDrawer.permissionEditor.editorLoading}
            pageTreeData={editorDrawer.permissionEditor.pageTreeData}
            selectedPageNodeKeys={editorDrawer.permissionEditor.selectedPageNodeKeys}
            selectedPageCount={editorDrawer.permissionEditor.selectedPageCount}
            totalPageCount={editorDrawer.permissionEditor.totalPageCount}
            activePageKey={editorDrawer.permissionEditor.activePageKey}
            activePageNode={editorDrawer.permissionEditor.activePageNode}
            activePageActionPermissions={editorDrawer.permissionEditor.activePageActionPermissions}
            activePageSelectedActionKeys={editorDrawer.permissionEditor.activePageSelectedActionKeys}
            isActivePageSelected={editorDrawer.permissionEditor.isActivePageSelected}
            expandedKeys={editorDrawer.permissionEditor.expandedKeys}
            isMobile={responsive.isMobile}
            onExpandChange={editorDrawer.permissionEditor.setExpandedKeys}
            onExpandToggle={editorDrawer.permissionEditor.handleExpandToggle}
            onSelectAllPages={editorDrawer.permissionEditor.handleSelectAllPages}
            onPageTreeCheck={editorDrawer.permissionEditor.handlePageTreeCheck}
            onActivePageChange={editorDrawer.permissionEditor.setActivePageKey}
            onActionPermissionsChange={editorDrawer.permissionEditor.handleActionPermissionsChange}
          />
        </Form>
      </ManagementDrawer>

      <ManagementDrawer title={detailDrawer.title} open={detailDrawer.open} onClose={detailDrawer.onClose}>
        {detailDrawer.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 'var(--saas-spacing-240)' }}>
            <Spin />
          </div>
        ) : detailDrawer.selectedRoleDetail ? (
          <>
            <ProDescriptions
              columns={[
                { title: t('角色编码', 'Role code'), dataIndex: 'roleCode' },
                { title: t('角色名称', 'Role name'), dataIndex: 'roleName' },
                {
                  title: t('角色类型', 'Role type'),
                  dataIndex: 'roleType',
                  renderText: (value) => ROLE_TYPE_LABEL_MAP[String(value)] || String(value || '-'),
                },
                { title: t('默认访问页', 'Default home page'), dataIndex: 'defaultHomePath' },
                { title: t('权限数', 'Permission count'), dataIndex: 'permissionCount' },
                { title: t('用户数', 'User count'), dataIndex: 'userCount' },
              ]}
              dataSource={detailDrawer.selectedRoleDetail}
              column={detailDrawer.column}
            />
            <div style={{ marginTop: resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile) }}>
              <Space wrap size={resolveResponsiveValue(APP_SPACING.tagWrapGap, responsive.isMobile)}>
                <Typography.Text strong>{t('数据范围', 'Data scope')}</Typography.Text>
                {(detailDrawer.selectedRoleDetail.dataScopes?.length ? detailDrawer.selectedRoleDetail.dataScopes : DEFAULT_DATA_SCOPES).map((scope) => (
                  <Tag key={`${scope.resourceCode}:${scope.scopeType}`} color="purple">
                    {scope.resourceCode === '*' ? t('全局', 'Global') : scope.resourceCode} · {DATA_SCOPE_LABELS[scope.scopeType] || scope.scopeType}
                  </Tag>
                ))}
              </Space>
            </div>
            <div style={{ marginTop: resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile) }}>
              <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.tagWrapGap, responsive.isMobile)} style={{ width: '100%' }}>
                <Typography.Text strong>{t('当前权限', 'Current permissions')}</Typography.Text>
                {detailDrawer.permissionDetailTreeData?.length ? (
                  <div className="role-permission-tree role-permission-detail-tree">
                    <Tree blockNode defaultExpandAll selectable={false} showIcon={false} treeData={detailDrawer.permissionDetailTreeData} />
                  </div>
                ) : (
                  <Tag>{t('暂无权限', 'No permissions')}</Tag>
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
const RoleEditorBasicFields = ({
  isPermissionOnlyEditor,
  handleRoleCodeBlur,
  defaultHomeOptions,
}: {
  isPermissionOnlyEditor: boolean;
  handleRoleCodeBlur: () => void;
  defaultHomeOptions: Array<{ label: string; value: string }>;
}) => (
  <>
    <Form.Item
      name="roleCode"
      label={t('角色编码', 'Role code')}
      rules={[
        {
          validator: (_, value) => {
            const roleCode = typeof value === 'string' ? value.trim() : '';
            if (!roleCode) {
              return Promise.reject(new Error(t('请输入角色编码', 'Please enter the role code')));
            }
            if (roleCode.length > 64) {
              return Promise.reject(new Error(t('角色编码长度不能超过64个字符', 'Role code cannot exceed 64 characters')));
            }
            if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(roleCode)) {
              return Promise.reject(new Error(t('角色编码只能由字母、数字和下划线组成，且必须以字母开头', 'Role code can contain only letters, numbers and underscores, and must start with a letter')));
            }
            return Promise.resolve();
          },
        },
      ]}
    >
      <Input maxLength={64} disabled={isPermissionOnlyEditor} onBlur={handleRoleCodeBlur} />
    </Form.Item>
    <Form.Item name="roleName" label={t('角色名称', 'Role name')} rules={[{ required: true, message: t('请输入角色名称', 'Please enter the role name') }]}>
      <Input disabled={isPermissionOnlyEditor} />
    </Form.Item>
    <Form.Item name="roleType" label={t('角色类型', 'Role type')} rules={[{ required: true, message: t('请选择角色类型', 'Please select a role type') }]}>
      <Select disabled={isPermissionOnlyEditor} options={ROLE_TYPE_OPTIONS as unknown as { label: string; value: string }[]} />
    </Form.Item>
    <Form.Item name="defaultHomePath" label={t('默认访问页面', 'Default home page')} rules={[{ required: true, message: t('请选择默认访问页面', 'Please select a default home page') }]}>
      <Select
        showSearch
        disabled={isPermissionOnlyEditor}
        classNames={{ popup: { root: 'role-default-home-select-popup' } }}
        listHeight={360}
        optionFilterProp="label"
        options={defaultHomeOptions}
        placeholder={t('请选择登录后的默认访问页面', 'Select the default page after login')}
      />
    </Form.Item>
    <Form.Item name={['dataScopes', 0, 'resourceCode']} hidden initialValue="*" />
    <Form.Item name={['dataScopes', 0, 'scopeType']} label={t('数据范围', 'Data scope')} rules={[{ required: true, message: t('请选择数据范围', 'Please select a data scope') }]}>
      <Select disabled={isPermissionOnlyEditor} options={DATA_SCOPE_OPTIONS} />
    </Form.Item>
  </>
);
