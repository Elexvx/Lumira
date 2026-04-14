import { useEffect, useMemo, useRef, useState } from 'react';
import { PageContainer, ProDescriptions, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Checkbox, Drawer, Empty, Form, Input, Modal, Select, Space, Spin, Tag, Tree, message } from 'antd';
import { iamService } from '@/services/iam';
import type { PermissionTreeRecord, RoleDetail, RoleRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';
import { buildResponsivePagination, buildResponsiveScroll, normalizeResponsiveColumns, ResponsiveActions, useResponsiveTable } from '@/components/ResponsiveTable';
import { ROLE_TYPE_LABEL_MAP, ROLE_TYPE_OPTIONS } from '@/constants/role';
import {
  buildPermissionTreeData,
  collectActionPermissionPageMap,
  collectExpandableKeys,
  collectPermissionKeyToPageKeyMap,
  collectSelectablePageNodeMap,
  collectSelectablePages,
  normalizePermissionKeysByPages,
  normalizePermissionTree,
  type NormalizedPermissionTreeRecord,
} from './rolesPermissionTree';
import './roles.less';

const RoleManagementPage = () => {
  const actionRef = useRef<ActionType>();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
  const responsive = useResponsiveTable();
  const [selectedRole, setSelectedRole] = useState<RoleRecord | null>(null);
  const [selectedRoleDetail, setSelectedRoleDetail] = useState<RoleDetail | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [editorLoading, setEditorLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editorDirty, setEditorDirty] = useState(false);
  const [permissionTree, setPermissionTree] = useState<PermissionTreeRecord[]>([]);
  const [permissionTreeLoading, setPermissionTreeLoading] = useState(true);
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [activePageKey, setActivePageKey] = useState<string | null>(null);
  const editorRequestSeq = useRef(0);

  const watchedPermissionKeys = Form.useWatch<string[]>('permissionKeys', editorForm) ?? [];

  useEffect(() => {
    let active = true;
    setPermissionTreeLoading(true);
    void iamService
      .permissionTree({ autoRedirectOnUnauthorized: false })
      .then((result: PermissionTreeRecord[]) => {
        if (!active) {
          return;
        }
        setPermissionTree(result);
        setExpandedKeys([]);
      })
      .catch(() => {
        if (active) {
          message.error('加载权限树失败，请稍后重试');
        }
      })
      .finally(() => {
        if (active) {
          setPermissionTreeLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const normalizedPermissionTree = useMemo(() => normalizePermissionTree(permissionTree), [permissionTree]);
  const selectablePages = useMemo(() => collectSelectablePages(normalizedPermissionTree), [normalizedPermissionTree]);
  const selectablePageNodeMap = useMemo(() => collectSelectablePageNodeMap(normalizedPermissionTree), [normalizedPermissionTree]);
  const permissionKeyToPageKeyMap = useMemo(() => collectPermissionKeyToPageKeyMap(normalizedPermissionTree), [normalizedPermissionTree]);
  const actionPermissionPageMap = useMemo(() => collectActionPermissionPageMap(normalizedPermissionTree), [normalizedPermissionTree]);
  const selectablePermissionKeys = useMemo(() => new Set(selectablePages.map((item) => item.permissionKey).filter(Boolean) as string[]), [selectablePages]);
  const pageTreeData = useMemo(() => buildPermissionTreeData(normalizedPermissionTree), [normalizedPermissionTree]);

  const selectedPagePermissionKeys = useMemo(
    () => watchedPermissionKeys.filter((permissionKey) => selectablePermissionKeys.has(permissionKey)),
    [selectablePermissionKeys, watchedPermissionKeys],
  );

  const selectedPageNodeKeys = useMemo(
    () =>
      Array.from(
        new Set(
          selectedPagePermissionKeys.flatMap((permissionKey) => permissionKeyToPageKeyMap.get(permissionKey) || []),
        ),
      ),
    [permissionKeyToPageKeyMap, selectedPagePermissionKeys],
  );

  const activePageNode = useMemo(
    () => (activePageKey ? selectablePageNodeMap.get(activePageKey) || null : null),
    [activePageKey, selectablePageNodeMap],
  );

  const activePageActionPermissions = activePageNode?.actionPermissions ?? [];
  const activePageActionPermissionKeys = useMemo(
    () => new Set(activePageActionPermissions.map((item) => item.permissionKey).filter(Boolean) as string[]),
    [activePageActionPermissions],
  );
  const activePageSelectedActionKeys = useMemo(
    () => watchedPermissionKeys.filter((permissionKey) => activePageActionPermissionKeys.has(permissionKey)),
    [activePageActionPermissionKeys, watchedPermissionKeys],
  );
  const isActivePageSelected = Boolean(activePageNode?.permissionKey && selectedPagePermissionKeys.includes(activePageNode.permissionKey));

  useEffect(() => {
    if (!editorOpen) {
      return;
    }

    if (!activePageKey) {
      setActivePageKey(selectedPageNodeKeys[0] || selectablePages[0]?.pageKey || null);
    }
  }, [activePageKey, editorOpen, selectablePages, selectedPageNodeKeys]);

  const applyPermissionKeys = (nextPermissionKeys: string[]) => {
    editorForm.setFieldsValue({ permissionKeys: nextPermissionKeys });
    setEditorDirty(true);
  };

  const closeEditorDrawer = () => {
    setEditorOpen(false);
    setEditorLoading(false);
    setEditorDirty(false);
    setActivePageKey(null);
    setExpandedKeys([]);
  };

  const openCreate = () => {
    editorRequestSeq.current += 1;
    setSelectedRole(null);
    setEditingId(null);
    setActivePageKey(null);
    setExpandedKeys([]);
    setEditorDirty(false);
    setEditorLoading(false);
    editorForm.resetFields();
    editorForm.setFieldsValue({ roleType: 'CUSTOM', permissionKeys: [] });
    setEditorOpen(true);
  };

  const openEdit = async (record: RoleRecord) => {
    const requestSeq = ++editorRequestSeq.current;
    setSelectedRole(record);
    setEditingId(record.id);
    setEditorOpen(true);
    setEditorLoading(true);
    setEditorDirty(false);
    setActivePageKey(null);
    setExpandedKeys([]);

    try {
      const detail = await iamService.roleDetail(record.id, { autoRedirectOnUnauthorized: false });
      if (editorRequestSeq.current !== requestSeq) {
        return;
      }

      editorForm.setFieldsValue({
        ...detail,
        permissionKeys: detail.permissionKeys || [],
      });

      const initialPermissionKey = detail.permissionKeys?.find((permissionKey) => selectablePermissionKeys.has(permissionKey)) || null;
      setActivePageKey(initialPermissionKey ? permissionKeyToPageKeyMap.get(initialPermissionKey)?.[0] || null : null);
    } catch (error) {
      if (editorRequestSeq.current === requestSeq) {
        message.error('加载角色信息失败，请稍后重试');
        setEditorOpen(false);
      }
    } finally {
      if (editorRequestSeq.current === requestSeq) {
        setEditorLoading(false);
      }
    }
  };

  const openDetail = async (record: RoleRecord) => {
    setSelectedRole(record);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const detail = await iamService.roleDetail(record.id, { autoRedirectOnUnauthorized: false });
      setSelectedRoleDetail(detail);
    } finally {
      setDetailLoading(false);
    }
  };

  const saveRole = async () => {
    setSaving(true);
    try {
      const values = await editorForm.validateFields();
      const payload = {
        ...values,
        permissionKeys: values.permissionKeys || [],
      };
      if (editingId) {
        await iamService.updateRole(editingId, payload, { autoRedirectOnUnauthorized: false });
        message.success('角色已更新');
      } else {
        await iamService.createRole(payload, { autoRedirectOnUnauthorized: false });
        message.success('角色已创建');
      }
      closeEditorDrawer();
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
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

  const handlePageTreeCheck = (checkedKeys: string[]) => {
    const nextPageNodeKeys = checkedKeys.filter((pageKey) => selectablePageNodeMap.has(pageKey));
    const nextPagePermissionKeys = nextPageNodeKeys
      .map((pageKey) => selectablePageNodeMap.get(pageKey)?.permissionKey)
      .filter((permissionKey): permissionKey is string => Boolean(permissionKey));
    const nextPermissionKeys = normalizePermissionKeysByPages(
      watchedPermissionKeys,
      nextPagePermissionKeys,
      selectablePermissionKeys,
      actionPermissionPageMap,
    );
    applyPermissionKeys(nextPermissionKeys);

    if (!nextPageNodeKeys.length) {
      setActivePageKey(null);
      return;
    }

    if (!activePageKey || !nextPageNodeKeys.includes(activePageKey)) {
      setActivePageKey(nextPageNodeKeys[0]);
    }
  };

  const handleSelectAllPages = () => {
    const allPagePermissionKeys = selectablePages.map((item) => item.permissionKey).filter(Boolean) as string[];
    const nextPagePermissionKeys = selectedPagePermissionKeys.length === allPagePermissionKeys.length ? [] : allPagePermissionKeys;
    const nextPermissionKeys = normalizePermissionKeysByPages(
      watchedPermissionKeys,
      nextPagePermissionKeys,
      selectablePermissionKeys,
      actionPermissionPageMap,
    );
    applyPermissionKeys(nextPermissionKeys);
    setActivePageKey(nextPagePermissionKeys[0] ? permissionKeyToPageKeyMap.get(nextPagePermissionKeys[0])?.[0] || null : null);
  };

  const handleExpandToggle = () => {
    if (expandedKeys.length === 0) {
      setExpandedKeys(collectExpandableKeys(normalizedPermissionTree));
      return;
    }
    setExpandedKeys([]);
  };

  const handleActionPermissionsChange = (nextActionKeys: string[]) => {
    if (!activePageNode?.permissionKey) {
      return;
    }

    const nextPermissionKeys = new Set<string>(watchedPermissionKeys);
    activePageActionPermissions.forEach((action) => {
      if (action.permissionKey) {
        nextPermissionKeys.delete(action.permissionKey);
      }
    });

    nextPermissionKeys.add(activePageNode.permissionKey);
    nextActionKeys.forEach((permissionKey) => {
      nextPermissionKeys.add(permissionKey);
    });

    applyPermissionKeys(Array.from(nextPermissionKeys));
  };

  const columns: ProColumns<RoleRecord>[] = useMemo(
    () => [
      {
        title: '角色编码',
        dataIndex: 'roleCode',
        search: true,
        importance: 1,
      },
      {
        title: '角色名称',
        dataIndex: 'roleName',
        search: true,
        importance: 1,
      },
      {
        title: '角色类型',
        dataIndex: 'roleType',
        importance: 1,
        valueEnum: ROLE_TYPE_OPTIONS.reduce<Record<string, { text: string }>>((acc, item) => {
          acc[String(item.value)] = { text: item.label };
          return acc;
        }, {}),
        search: {
          transform: (value) => ({ roleType: value }),
        },
      },
      {
        title: '权限数',
        dataIndex: 'permissionCount',
        hideInSearch: true,
        importance: 2,
        responsiveLevel: ['tablet', 'desktop'],
        render: (_, record) => record.permissionCount ?? 0,
      },
      {
        title: '用户数',
        dataIndex: 'userCount',
        hideInSearch: true,
        importance: 2,
        responsiveLevel: ['tablet', 'desktop'],
        render: (_, record) => record.userCount ?? 0,
      },
      {
        title: '操作',
        valueType: 'option',
        importance: 0,
        desktopFixed: 'right',
        width: 180,
        render: (_, record) => (
          <ResponsiveActions
            level={responsive.level}
            items={[
              {
                key: 'detail',
                label: '详情',
                hidden: !canAccess('system:role:view'),
                onClick: () => void openDetail(record),
              },
              {
                key: 'edit',
                label: '编辑',
                hidden: !canAccess('system:role:update'),
                onClick: () => void openEdit(record),
              },
              {
                key: 'permission',
                label: '权限分配',
                hidden: !canAccess('system:role:permissions'),
                onClick: () => void openEdit(record),
              },
            ]}
          />
        ),
      },
    ],
    [canAccess, responsive.level],
  );
  const responsiveColumns = useMemo(() => normalizeResponsiveColumns(columns, responsive.level), [columns, responsive.level]);

  return (
    <PageContainer title="角色管理" className="saas-management-page">
      <div className="saas-table-wrap">
        <ProTable<RoleRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={responsiveColumns}
          search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
          options={false}
          pagination={buildResponsivePagination({ showSizeChanger: true }, responsive)}
          scroll={buildResponsiveScroll(responsiveColumns, responsive)}
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
            canAccess('system:role:create') ? (
              <Button key="create" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={openCreate}>
                新增角色
              </Button>
            ) : null,
            <Button key="refresh" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => actionRef.current?.reload()}>
              刷新
            </Button>,
          ]}
        />
      </div>

      <Drawer
        title={editingId ? '编辑角色 / 分配权限' : '新增角色'}
        open={editorOpen}
        onClose={handleEditorClose}
        width="min(1040px, 96vw)"
        destroyOnClose
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={handleEditorClose}>取消</Button>
              <Button type="primary" loading={saving} onClick={() => void saveRole()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        {editorLoading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 420 }}>
            <Spin size="large" />
          </div>
        ) : (
          <Form
            form={editorForm}
            layout="vertical"
            initialValues={{ roleType: 'CUSTOM', permissionKeys: [] }}
            onValuesChange={() => setEditorDirty(true)}
            className="role-editor-form"
          >
            <Form.Item name="roleCode" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
              <Input />
            </Form.Item>
            <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
              <Input />
            </Form.Item>
            <Form.Item name="roleType" label="角色类型" rules={[{ required: true, message: '请选择角色类型' }]}>
              <Select options={ROLE_TYPE_OPTIONS as unknown as { label: string; value: string }[]} />
            </Form.Item>
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
                    <Button size="small" onClick={handleExpandToggle}>
                      {expandedKeys.length ? '折叠全部' : '展开全部'}
                    </Button>
                    <Button size="small" onClick={handleSelectAllPages}>
                      {selectedPageNodeKeys.length === selectablePages.length ? '全不选' : '全选'}
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
                      virtual
                      height={360}
                      treeData={pageTreeData}
                      checkedKeys={selectedPageNodeKeys}
                      selectedKeys={activePageKey ? [activePageKey] : []}
                      expandedKeys={expandedKeys}
                      onExpand={(nextExpandedKeys) => setExpandedKeys(nextExpandedKeys.map(String))}
                      onCheck={(checkedKeys, info) => {
                        const nextCheckedKeys = Array.isArray(checkedKeys) ? checkedKeys.map(String) : [];
                        handlePageTreeCheck(nextCheckedKeys);
                        if ((info.node as NormalizedPermissionTreeRecord).selectable && (info.node as NormalizedPermissionTreeRecord).pageKey) {
                          setActivePageKey((info.node as NormalizedPermissionTreeRecord).pageKey);
                        }
                      }}
                      onSelect={(_, info) => {
                        if ((info.node as NormalizedPermissionTreeRecord).selectable && (info.node as NormalizedPermissionTreeRecord).pageKey) {
                          setActivePageKey((info.node as NormalizedPermissionTreeRecord).pageKey);
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

                {selectablePages.length ? (
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
                          onChange={(checkedValues) => handleActionPermissionsChange(checkedValues.map(String))}
                          className="role-action-grid"
                          disabled={!isActivePageSelected}
                          options={activePageActionPermissions.map((item) => ({
                            label: item.permissionName,
                            value: item.permissionKey,
                          }))}
                        />
                      ) : (
                        <div className="role-action-panel__empty">
                          <Empty description="该页面暂无字权限" image={Empty.PRESENTED_IMAGE_SIMPLE} />
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
          </Form>
        )}
      </Drawer>

      <Drawer
        title={selectedRole ? `角色详情 · ${selectedRole.roleName}` : '角色详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setSelectedRoleDetail(null);
        }}
        width={720}
        destroyOnClose
      >
        {detailLoading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : selectedRoleDetail ? (
          <>
            <ProDescriptions<RoleDetail>
              column={responsive.isMobile ? 1 : 2}
              dataSource={selectedRoleDetail}
              columns={[
                { title: '角色编码', dataIndex: 'roleCode' },
                { title: '角色名称', dataIndex: 'roleName' },
                {
                  title: '角色类型',
                  dataIndex: 'roleType',
                  renderText: (value) => ROLE_TYPE_LABEL_MAP[String(value)] || String(value || '-'),
                },
                { title: '权限数', dataIndex: 'permissionCount' },
                { title: '用户数', dataIndex: 'userCount' },
              ]}
            />
            <div style={{ marginTop: 16 }}>
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <div>当前权限</div>
                {selectedRoleDetail.permissionKeys?.length ? (
                  <Space wrap>
                    {selectedRoleDetail.permissionKeys.map((item) => (
                      <Tag key={item} color="geekblue">
                        {item}
                      </Tag>
                    ))}
                  </Space>
                ) : (
                  <Tag>暂无权限</Tag>
                )}
              </Space>
            </div>
          </>
        ) : null}
      </Drawer>
    </PageContainer>
  );
};

export default RoleManagementPage;
