import { Button, Checkbox, Empty, Form, Input, Modal, Select, Space, Spin, Tag, Tree, Typography } from 'antd';
import { ProDescriptions } from '@ant-design/pro-components';
import { useMemo } from 'react';
import { ManagementDrawer, type ManagementDrawerAction } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useRoleManagementPageData } from '@/pages/system/roles/hooks/useRoleManagementPageData';
import { ROLE_TYPE_LABEL_MAP, ROLE_TYPE_OPTIONS } from '@/constants/role';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import type { TreeProps } from 'antd';
import type { NormalizedPermissionTreeRecord } from '@/pages/system/rolesPermissionTree/normalize';
import type { PermissionActionRecord, PermissionTreeRecord, RoleDataScope } from '@/types/api';
import './roles.css';

const DATA_SCOPE_OPTIONS: Array<{ label: string; value: 'ALL' | 'TENANT' | 'DEPT' | 'DEPT_AND_CHILD' | 'SELF' | 'CUSTOM' }> = [
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
const DEFAULT_DATA_SCOPES: RoleDataScope[] = [{ resourceCode: '*', scopeType: 'SELF' }];

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
}) => (
  <Modal
    title="默认注册角色"
    open={open}
    confirmLoading={saving}
    onOk={onSubmit}
    onCancel={onCancel}
    okButtonProps={{ disabled: !canSave }}
    okText="保存"
    cancelText="取消"
  >
    <Space direction="vertical" size={APP_SPACING.modalFooterGap.desktop} style={{ width: '100%' }}>
      <Typography.Text type="secondary">
        新用户通过注册或验证码自动创建后，会默认绑定该角色；后续仍可在用户管理中单独调整角色。
      </Typography.Text>
      <Select
        showSearch
        loading={loading}
        value={value}
        onChange={onChange}
        placeholder="请选择默认注册角色"
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
}) => {
  if (editorLoading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: 420 }}>
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
              <div className="role-editor-section__title">页面路由权限</div>
              <div className="role-editor-section__meta">先勾选可访问的页面，目录节点仅用于分组，再配置该页面下的按钮权限</div>
            </div>
            <Space>
              <Button size="small" onClick={onExpandToggle}>
                {expandedKeys.length ? '折叠全部' : '展开全部'}
              </Button>
              <Button size="small" onClick={onSelectAllPages}>
                {selectedPageCount === totalPageCount ? '全不选' : '全选'}
              </Button>
            </Space>
          </div>
          <div className="role-permission-tree">
            {permissionTreeLoading ? (
              <div style={{ display: 'grid', placeItems: 'center', minHeight: 320 }}>
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
              <Empty description="暂无可配置页面权限" style={{ padding: '48px 0' }} />
            )}
          </div>
        </section>

        <section className="role-editor-section role-action-panel">
          <div className="role-editor-section__header">
            <div>
              <div className="role-editor-section__title">页面动作权限</div>
              <div className="role-editor-section__meta">按钮权限仅在页面权限勾选后生效</div>
            </div>
          </div>

          {permissionTree.length ? (
            <>
              <div className="role-action-panel__page-name">
                {activePageNode?.pageName || '请从左侧选择页面'}
                {activePageNode?.routeMatched && activePageNode?.routePath ? (
                  <Tag style={{ marginInlineStart: 8 }} color="blue">
                    {activePageNode?.routePath}
                  </Tag>
                ) : activePageNode?.nodeType === 'PAGE' ? (
                  <Tag style={{ marginInlineStart: 8 }} color="red">
                    路由失配
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
                    <Empty description="该页面暂无子权限" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  </div>
                )
              ) : (
                <div className="role-action-panel__empty">
                  <Empty description="请从左侧页面权限树中选择一个页面" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                </div>
              )}
            </>
          ) : (
            <div className="role-action-panel__empty">
              <Empty description="请先在上方勾选一个页面" image={Empty.PRESENTED_IMAGE_SIMPLE} />
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
    title: roleActions.roleEditorMode === 'permissions' ? '分配角色权限' : roleCrud.drawer.editingId ? '编辑角色 / 分配权限' : '新增角色',
    onClose: roleActions.handleEditorClose,
    footerActions: [
      { key: 'cancel', label: '取消', onClick: roleActions.handleEditorClose },
      { key: 'save', label: '保存', type: 'primary' as const, loading: roleActions.saving, disabled: !roleActions.canSaveRole, onClick: () => void roleActions.saveRole() },
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
    title: roleActions.selectedRoleDetail ? `角色详情 · ${roleActions.selectedRoleDetail.roleName}` : '角色详情',
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
    <ManagementPage title="角色管理">
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
              label: '新增角色',
              onClick: roleCrud.drawer.openCreate,
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
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : detailDrawer.selectedRoleDetail ? (
          <>
            <ProDescriptions
              columns={[
                { title: '角色编码', dataIndex: 'roleCode' },
                { title: '角色名称', dataIndex: 'roleName' },
                {
                  title: '角色类型',
                  dataIndex: 'roleType',
                  renderText: (value) => ROLE_TYPE_LABEL_MAP[String(value)] || String(value || '-'),
                },
                { title: '默认访问页', dataIndex: 'defaultHomePath' },
                { title: '权限数', dataIndex: 'permissionCount' },
                { title: '用户数', dataIndex: 'userCount' },
              ]}
              dataSource={detailDrawer.selectedRoleDetail}
              column={detailDrawer.column}
            />
            <div style={{ marginTop: 16 }}>
              <Space wrap size={[8, 8]}>
                <Typography.Text strong>数据范围</Typography.Text>
                {(detailDrawer.selectedRoleDetail.dataScopes?.length ? detailDrawer.selectedRoleDetail.dataScopes : DEFAULT_DATA_SCOPES).map((scope) => (
                  <Tag key={`${scope.resourceCode}:${scope.scopeType}`} color="purple">
                    {scope.resourceCode === '*' ? '全局' : scope.resourceCode} · {DATA_SCOPE_LABELS[scope.scopeType] || scope.scopeType}
                  </Tag>
                ))}
              </Space>
            </div>
            <div style={{ marginTop: 16 }}>
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Typography.Text strong>当前权限</Typography.Text>
                {detailDrawer.permissionDetailTreeData?.length ? (
                  <div className="role-permission-tree role-permission-detail-tree">
                    <Tree blockNode defaultExpandAll selectable={false} showIcon={false} treeData={detailDrawer.permissionDetailTreeData} />
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
            if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(roleCode)) {
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
    <Form.Item name="defaultHomePath" label="默认访问页面" rules={[{ required: true, message: '请选择默认访问页面' }]}>
      <Select
        showSearch
        disabled={isPermissionOnlyEditor}
        classNames={{ popup: { root: 'role-default-home-select-popup' } }}
        listHeight={360}
        optionFilterProp="label"
        options={defaultHomeOptions}
        placeholder="请选择登录后的默认访问页面"
      />
    </Form.Item>
    <Form.Item name={['dataScopes', 0, 'resourceCode']} hidden initialValue="*" />
    <Form.Item name={['dataScopes', 0, 'scopeType']} label="数据范围" rules={[{ required: true, message: '请选择数据范围' }]}>
      <Select disabled={isPermissionOnlyEditor} options={DATA_SCOPE_OPTIONS} />
    </Form.Item>
  </>
);
