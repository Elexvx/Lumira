import { Button, Checkbox, Empty, Form, Input, Modal, Select, Space, Spin, Tag, Tree, Typography } from 'antd';
import { ProDescriptions } from '@ant-design/pro-components';
import { useMemo } from 'react';
import { ManagementDrawer, type ManagementDrawerAction } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useRoleManagementPageData } from '@/pages/system/roles/hooks/useRoleManagementPageData';
import { ROLE_TYPE_LABEL_MAP, ROLE_TYPE_OPTIONS } from '@/constants/role';
import { useDictOptions, type DictOption } from '@/hooks/useDictOptions';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import type { TreeProps } from 'antd';
import type { NormalizedPermissionTreeRecord } from '@/pages/system/rolesPermissionTree/normalize';
import type { PermissionActionRecord, PermissionTreeRecord, RoleDataScope } from '@/types/api';
import './roles.css';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

const COMPETITION_REGISTRATION_SCOPE_RESOURCE = 'competition:registration';
const ACTIVITY_REGISTRATION_SCOPE_RESOURCE = 'activity:registration';

const DATA_SCOPE_OPTIONS: Array<{ label: string; value: 'ALL' | 'DEPT' | 'DEPT_AND_CHILD' | 'SELF' | 'CUSTOM' }> = [
  { label: t('ui.system.roles.allData'), value: 'ALL' },
  { label: t('ui.system.roles.currentDepartmentData'), value: 'DEPT' },
  { label: t('ui.system.roles.currentDepartmentAndDescendants'), value: 'DEPT_AND_CHILD' },
  { label: t('ui.system.roles.myDataOnly'), value: 'SELF' },
  { label: t('ui.system.roles.customScope'), value: 'CUSTOM' },
];
const REGISTRATION_DATA_SCOPE_OPTIONS: Array<{ label: string; value: 'ALL' | 'SELF' }> = [
  { label: t('ui.system.roles.allData'), value: 'ALL' },
  { label: t('ui.system.roles.myDataOnly'), value: 'SELF' },
];
const DATA_SCOPE_LABELS = DATA_SCOPE_OPTIONS.reduce<Record<string, string>>((acc, item) => {
  acc[item.value] = item.label;
  return acc;
}, {});
const DEFAULT_DATA_SCOPES: RoleDataScope[] = [{ resourceCode: '*', scopeType: 'SELF' }];
const ROLE_TYPE_DICT_FALLBACK_OPTIONS = ROLE_TYPE_OPTIONS as unknown as DictOption[];

const formatDataScopeResource = (resourceCode: string) => {
  if (resourceCode === '*') {
    return t('ui.system.roles.global');
  }
  if (resourceCode === COMPETITION_REGISTRATION_SCOPE_RESOURCE) {
    return t('ui.system.roles.competitionRegistrations');
  }
  if (resourceCode === ACTIVITY_REGISTRATION_SCOPE_RESOURCE) {
    return t('ui.system.roles.activityRegistrations');
  }
  return resourceCode;
};

const formatPermissionGroupLabel = (permissionGroup: string) =>
  (
    {
      audit: t('ui.system.roles.audit'),
      dashboard: t('ui.system.roles.dashboard'),
      iam: 'IAM',
      message: t('ui.system.roles.messages'),
      plugin: t('ui.system.roles.plugins'),
      profile: t('ui.system.roles.profile'),
      system: t('ui.system.roles.system'),
      platform: t('ui.system.roles.platform'),
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
      title={t('ui.system.roles.defaultRegistrationRole')}
      open={open}
      confirmLoading={saving}
      onOk={onSubmit}
      onCancel={onCancel}
      okButtonProps={{ disabled: !canSave }}
      okText={t('ui.system.roles.save')}
      cancelText={t('ui.system.roles.cancel')}
    >
      <Space direction="vertical" size={modalFooterGap} style={{ width: '100%' }}>
        <Typography.Text type="secondary">{t('ui.system.roles.newUsersCreatedThroughRegistrationOrVerificationCode')}</Typography.Text>
        <Select
          showSearch
          loading={loading}
          value={value}
          onChange={onChange}
          placeholder={t('ui.system.roles.selectDefaultRegistrationRole')}
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
              <div className="role-editor-section__title">{t('ui.system.roles.pageRoutePermissions')}</div>
              <div className="role-editor-section__meta">{t('ui.system.roles.firstSelectAccessiblePagesDirectoryNodesAreFor')}</div>
            </div>
            <Space>
              <Button size="small" onClick={onExpandToggle}>
                {expandedKeys.length ? t('ui.system.roles.collapseAll') : t('ui.system.roles.expandAll')}
              </Button>
              <Button size="small" onClick={onSelectAllPages}>
                {selectedPageCount === totalPageCount ? t('ui.system.roles.deselectAll') : t('ui.system.roles.selectAll')}
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
              <Empty description={t('ui.system.roles.noPagePermissionsAvailable')} style={{ padding: 'var(--saas-spacing-48) 0' }} />
            )}
          </div>
        </section>

        <section className="role-editor-section role-action-panel">
          <div className="role-editor-section__header">
            <div>
              <div className="role-editor-section__title">{t('ui.system.roles.pageActionPermissions')}</div>
              <div className="role-editor-section__meta">{t('ui.system.roles.buttonPermissionsTakeEffectOnlyAfterPagePermissions')}</div>
            </div>
          </div>

          {permissionTree.length ? (
            <>
              <div className="role-action-panel__page-name">
                {activePageNode?.pageName || t('ui.system.roles.selectAPageFromTheLeft')}
                {activePageNode?.routeMatched && activePageNode?.routePath ? (
                  <Tag style={{ marginInlineStart: tagWrapGap[0] }} color="blue">
                    {activePageNode?.routePath}
                  </Tag>
                ) : activePageNode?.nodeType === 'PAGE' ? (
                  <Tag style={{ marginInlineStart: tagWrapGap[0] }} color="red">
                    {t('ui.system.roles.routeMismatch')}
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
                    <Empty description={t('ui.system.roles.noChildPermissionsOnThisPage')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  </div>
                )
              ) : (
                <div className="role-action-panel__empty">
                  <Empty description={t('ui.system.roles.selectAPageFromTheLeftPermissionTree')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                </div>
              )}
            </>
          ) : (
            <div className="role-action-panel__empty">
              <Empty description={t('ui.system.roles.pleaseSelectAPageAboveFirst')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
            </div>
          )}
        </section>
      </div>
    </>
  );
};

const RoleManagementPage = () => {
  const { options: roleTypeOptions } = useDictOptions('sys_role_type', ROLE_TYPE_DICT_FALLBACK_OPTIONS);
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
            <span className="role-page-row__name">{t('ui.system.roles.group')}: {formatPermissionGroupLabel(group.permissionGroup)}</span>
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
                  <Tag color="blue">{page.permissions.some((item) => item.isPagePermission) ? t('ui.system.roles.page') : t('ui.system.roles.permission')}</Tag>
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
    title: roleActions.roleEditorMode === 'permissions' ? t('ui.system.roles.assignRolePermissions') : roleCrud.drawer.editingId ? t('ui.system.roles.editRoleAssignPermissions') : t('ui.system.roles.addRole'),
    onClose: roleActions.handleEditorClose,
    footerActions: [
      { key: 'cancel', label: t('ui.system.roles.cancel'), onClick: roleActions.handleEditorClose },
      { key: 'save', label: t('ui.system.roles.save'), type: 'primary' as const, loading: roleActions.saving, disabled: !roleActions.canSaveRole, onClick: () => void roleActions.saveRole() },
    ] as ManagementDrawerAction[],
    formProps: roleActions.editorFormProps,
    isPermissionOnlyEditor: roleActions.isPermissionOnlyEditor,
    handleRoleCodeBlur: roleActions.handleRoleCodeBlur,
    defaultHomeOptions,
    roleTypeOptions,
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
    title: roleActions.selectedRoleDetail ? `${t('ui.system.roles.roleDetails')} · ${roleActions.selectedRoleDetail.roleName}` : t('ui.system.roles.roleDetails'),
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
    <ManagementPage title={t('ui.system.roles.roleManagement')}>
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
                label: t('ui.system.roles.addRole'),
                onClick: roleActions.openCreate,
              },
              {
                key: 'default-registration-role',
                permission: 'system:role:update',
                label: t('ui.system.roles.defaultRegistrationRole'),
                onClick: () => void openDefaultRoleModal(),
              },
              {
                key: 'refresh',
                label: t('ui.system.roles.refresh'),
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
            roleTypeOptions={editorDrawer.roleTypeOptions}
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
                { title: t('ui.system.roles.roleCode'), dataIndex: 'roleCode' },
                { title: t('ui.system.roles.roleName'), dataIndex: 'roleName' },
                {
                  title: t('ui.system.roles.roleType'),
                  dataIndex: 'roleType',
                  renderText: (value) => ROLE_TYPE_LABEL_MAP[String(value)] || String(value || '-'),
                },
                { title: t('ui.system.roles.defaultHomePage'), dataIndex: 'defaultHomePath' },
                { title: t('ui.system.roles.permissionCount'), dataIndex: 'permissionCount' },
                { title: t('ui.system.roles.userCount'), dataIndex: 'userCount' },
              ]}
              dataSource={detailDrawer.selectedRoleDetail}
              column={detailDrawer.column}
            />
            <div style={{ marginTop: resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile) }}>
              <Space wrap size={resolveResponsiveValue(APP_SPACING.tagWrapGap, responsive.isMobile)}>
                <Typography.Text strong>{t('ui.system.roles.dataScope')}</Typography.Text>
                {(detailDrawer.selectedRoleDetail.dataScopes?.length ? detailDrawer.selectedRoleDetail.dataScopes : DEFAULT_DATA_SCOPES).map((scope) => (
                  <Tag key={`${scope.resourceCode}:${scope.scopeType}`} color="purple">
                    {formatDataScopeResource(scope.resourceCode)} · {DATA_SCOPE_LABELS[scope.scopeType] || scope.scopeType}
                  </Tag>
                ))}
              </Space>
            </div>
            <div style={{ marginTop: resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile) }}>
              <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.tagWrapGap, responsive.isMobile)} style={{ width: '100%' }}>
                <Typography.Text strong>{t('ui.system.roles.currentPermissions')}</Typography.Text>
                {detailDrawer.permissionDetailTreeData?.length ? (
                  <div className="role-permission-tree role-permission-detail-tree">
                    <Tree blockNode defaultExpandAll selectable={false} showIcon={false} treeData={detailDrawer.permissionDetailTreeData} />
                  </div>
                ) : (
                  <Tag>{t('ui.system.roles.noPermissions')}</Tag>
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
  roleTypeOptions,
}: {
  isPermissionOnlyEditor: boolean;
  handleRoleCodeBlur: () => void;
  defaultHomeOptions: Array<{ label: string; value: string }>;
  roleTypeOptions: DictOption[];
}) => (
  <>
    <Form.Item
      name="roleCode"
      label={t('ui.system.roles.roleCode')}
      rules={[
        {
          validator: (_, value) => {
            const roleCode = typeof value === 'string' ? value.trim() : '';
            if (!roleCode) {
              return Promise.reject(new Error(t('ui.system.roles.pleaseEnterTheRoleCode')));
            }
            if (roleCode.length > 64) {
              return Promise.reject(new Error(t('ui.system.roles.roleCodeCannotExceed64Characters')));
            }
            if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(roleCode)) {
              return Promise.reject(new Error(t('ui.system.roles.roleCodeCanContainOnlyLettersNumbersAnd')));
            }
            return Promise.resolve();
          },
        },
      ]}
    >
      <Input maxLength={64} disabled={isPermissionOnlyEditor} onBlur={handleRoleCodeBlur} />
    </Form.Item>
    <Form.Item name="roleName" label={t('ui.system.roles.roleName')} rules={[{ required: true, message: t('ui.system.roles.pleaseEnterTheRoleName') }]}>
      <Input disabled={isPermissionOnlyEditor} />
    </Form.Item>
    <Form.Item name="roleType" label={t('ui.system.roles.roleType')} rules={[{ required: true, message: t('ui.system.roles.pleaseSelectARoleType') }]}>
      <Select disabled={isPermissionOnlyEditor} options={roleTypeOptions} />
    </Form.Item>
    <Form.Item name="defaultHomePath" label={t('ui.system.roles.defaultHomePage.a026a47d')} rules={[{ required: true, message: t('ui.system.roles.pleaseSelectADefaultHomePage') }]}>
      <Select
        showSearch
        disabled={isPermissionOnlyEditor}
        classNames={{ popup: { root: 'role-default-home-select-popup' } }}
        listHeight={360}
        optionFilterProp="label"
        options={defaultHomeOptions}
        placeholder={t('ui.system.roles.selectTheDefaultPageAfterLogin')}
      />
    </Form.Item>
    <Form.Item name={['dataScopes', 0, 'resourceCode']} hidden initialValue="*" />
    <Form.Item name={['dataScopes', 0, 'scopeType']} label={t('ui.system.roles.dataScope')} rules={[{ required: true, message: t('ui.system.roles.pleaseSelectADataScope') }]}>
      <Select disabled={isPermissionOnlyEditor} options={DATA_SCOPE_OPTIONS} />
    </Form.Item>
    <Form.Item name={['dataScopes', 1, 'resourceCode']} hidden initialValue={COMPETITION_REGISTRATION_SCOPE_RESOURCE} />
    <Form.Item
      name={['dataScopes', 1, 'scopeType']}
      label={t('ui.system.roles.competitionRegistrationDataScope')}
      rules={[{ required: true, message: t('ui.system.roles.pleaseSelectTheCompetitionRegistrationDataScope') }]}
      extra={t('ui.system.roles.controlsWhichCompetitionRegistrationRecordsThisRoleCan')}
    >
      <Select disabled={isPermissionOnlyEditor} options={REGISTRATION_DATA_SCOPE_OPTIONS} />
    </Form.Item>
    <Form.Item name={['dataScopes', 2, 'resourceCode']} hidden initialValue={ACTIVITY_REGISTRATION_SCOPE_RESOURCE} />
    <Form.Item
      name={['dataScopes', 2, 'scopeType']}
      label={t('ui.system.roles.activityRegistrationDataScope')}
      rules={[{ required: true, message: t('ui.system.roles.pleaseSelectTheActivityRegistrationDataScope') }]}
      extra={t('ui.system.roles.controlsWhichActivityRegistrationRecordsThisRoleCan')}
    >
      <Select disabled={isPermissionOnlyEditor} options={REGISTRATION_DATA_SCOPE_OPTIONS} />
    </Form.Item>
  </>
);
