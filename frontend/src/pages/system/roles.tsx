import { useEffect, useMemo, useRef, useState } from 'react';
import { PageContainer, ProDescriptions, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Checkbox, Drawer, Empty, Form, Input, Modal, Select, Space, Spin, Tag, Tree, Typography, message } from 'antd';
import { iamService } from '@/services/iam';
import type { PermissionTreeRecord, RoleDetail, RoleRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';
import { ROLE_TYPE_LABEL_MAP, ROLE_TYPE_OPTIONS } from '@/constants/role';
import './roles.less';

interface PermissionPageOption {
  label: string;
  value: string;
}

const hasRoutePath = (routePath?: string) => Boolean(routePath);

const buildPermissionTreeData = (nodes: PermissionTreeRecord[]): Array<PermissionTreeRecord & { key: string; title: JSX.Element }> =>
  nodes.map((node) => ({
    ...node,
    key: node.pageKey,
    disableCheckbox: !node.selectable,
    title: (
      <div className="role-page-row">
        <span className="role-page-row__name">{node.pageName}</span>
        {hasRoutePath(node.routePath) ? <span className="role-page-row__route">{node.routePath}</span> : null}
      </div>
    ),
    children: node.children?.length ? buildPermissionTreeData(node.children) : undefined,
  }));

const collectSelectablePages = (nodes: PermissionTreeRecord[], result: PermissionTreeRecord[] = []) => {
  nodes.forEach((node) => {
    if (node.selectable && node.permissionKey) {
      result.push(node);
    }
    if (node.children?.length) {
      collectSelectablePages(node.children, result);
    }
  });
  return result;
};

const collectSelectablePageMap = (nodes: PermissionTreeRecord[], result = new Map<string, PermissionTreeRecord>()) => {
  nodes.forEach((node) => {
    if (node.selectable && node.permissionKey) {
      result.set(node.permissionKey, node);
    }
    if (node.children?.length) {
      collectSelectablePageMap(node.children, result);
    }
  });
  return result;
};

const collectActionPermissionPageMap = (nodes: PermissionTreeRecord[], result = new Map<string, string>()) => {
  nodes.forEach((node) => {
    if (node.selectable && node.permissionKey) {
      node.actionPermissions?.forEach((action) => {
        if (action.permissionKey) {
          result.set(action.permissionKey, node.permissionKey as string);
        }
      });
    }
    if (node.children?.length) {
      collectActionPermissionPageMap(node.children, result);
    }
  });
  return result;
};

const collectExpandableKeys = (nodes: PermissionTreeRecord[], result: string[] = []) => {
  nodes.forEach((node) => {
    if (node.children?.length) {
      result.push(node.pageKey);
      collectExpandableKeys(node.children, result);
    }
  });
  return result;
};

const normalizePermissionKeysByPages = (
  currentPermissionKeys: string[],
  nextPageKeys: string[],
  allPageKeys: Set<string>,
  actionPermissionPageMap: Map<string, string>,
) => {
  const nextPageKeySet = new Set(nextPageKeys);
  const nextPermissionKeys = new Set<string>();

  currentPermissionKeys.forEach((permissionKey) => {
    if (nextPageKeySet.has(permissionKey)) {
      nextPermissionKeys.add(permissionKey);
      return;
    }

    const pageKey = actionPermissionPageMap.get(permissionKey);
    if (pageKey) {
      if (nextPageKeySet.has(pageKey)) {
        nextPermissionKeys.add(permissionKey);
      }
      return;
    }

    if (!allPageKeys.has(permissionKey)) {
      nextPermissionKeys.add(permissionKey);
    }
  });

  nextPageKeys.forEach((permissionKey) => {
    nextPermissionKeys.add(permissionKey);
  });

  return Array.from(nextPermissionKeys);
};

const RoleManagementPage = () => {
  const actionRef = useRef<ActionType>();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
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
        setExpandedKeys(collectExpandableKeys(result));
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

  const selectablePages = useMemo(() => collectSelectablePages(permissionTree), [permissionTree]);
  const selectablePageMap = useMemo(() => collectSelectablePageMap(permissionTree), [permissionTree]);
  const actionPermissionPageMap = useMemo(() => collectActionPermissionPageMap(permissionTree), [permissionTree]);
  const selectablePageKeys = useMemo(() => new Set(selectablePages.map((item) => item.permissionKey).filter(Boolean) as string[]), [selectablePages]);

  const pageTreeData = useMemo(() => buildPermissionTreeData(permissionTree), [permissionTree]);

  const selectedPageKeys = useMemo(
    () => watchedPermissionKeys.filter((permissionKey) => selectablePageKeys.has(permissionKey)),
    [selectablePageKeys, watchedPermissionKeys],
  );

  const selectedPageOptions = useMemo<PermissionPageOption[]>(
    () =>
      selectedPageKeys
        .map((permissionKey) => selectablePageMap.get(permissionKey))
        .filter((page): page is PermissionTreeRecord => Boolean(page))
        .map((page) => ({
          label: page.pageName,
          value: page.permissionKey as string,
        })),
    [selectablePageMap, selectedPageKeys],
  );

  const activePageNode = useMemo(
    () => (activePageKey ? selectablePageMap.get(activePageKey) || null : null),
    [activePageKey, selectablePageMap],
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

  useEffect(() => {
    if (!editorOpen) {
      return;
    }

    if (selectedPageKeys.length === 0) {
      setActivePageKey(null);
      return;
    }

    if (!activePageKey || !selectedPageKeys.includes(activePageKey)) {
      setActivePageKey(selectedPageKeys[0]);
    }
  }, [activePageKey, editorOpen, selectedPageKeys]);

  const applyPermissionKeys = (nextPermissionKeys: string[]) => {
    editorForm.setFieldsValue({ permissionKeys: nextPermissionKeys });
    setEditorDirty(true);
  };

  const closeEditorDrawer = () => {
    setEditorOpen(false);
    setEditorLoading(false);
    setEditorDirty(false);
    setActivePageKey(null);
  };

  const openCreate = () => {
    editorRequestSeq.current += 1;
    setSelectedRole(null);
    setEditingId(null);
    setActivePageKey(null);
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

    try {
      const detail = await iamService.roleDetail(record.id, { autoRedirectOnUnauthorized: false });
      if (editorRequestSeq.current !== requestSeq) {
        return;
      }

      editorForm.setFieldsValue({
        ...detail,
        permissionKeys: detail.permissionKeys || [],
      });

      const initialPageKey = detail.permissionKeys?.find((permissionKey) => selectablePageKeys.has(permissionKey)) || null;
      setActivePageKey(initialPageKey);
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
    const nextPageKeys = checkedKeys.filter((permissionKey) => selectablePageKeys.has(permissionKey));
    const nextPermissionKeys = normalizePermissionKeysByPages(
      watchedPermissionKeys,
      nextPageKeys,
      selectablePageKeys,
      actionPermissionPageMap,
    );
    applyPermissionKeys(nextPermissionKeys);

    if (!nextPageKeys.length) {
      setActivePageKey(null);
      return;
    }

    if (!activePageKey || !nextPageKeys.includes(activePageKey)) {
      setActivePageKey(nextPageKeys[0]);
    }
  };

  const handleSelectAllPages = () => {
    const allPageKeys = selectablePages.map((item) => item.permissionKey).filter(Boolean) as string[];
    const nextPageKeys = selectedPageKeys.length === allPageKeys.length ? [] : allPageKeys;
    const nextPermissionKeys = normalizePermissionKeysByPages(
      watchedPermissionKeys,
      nextPageKeys,
      selectablePageKeys,
      actionPermissionPageMap,
    );
    applyPermissionKeys(nextPermissionKeys);
  };

  const handleExpandToggle = () => {
    if (expandedKeys.length === 0) {
      setExpandedKeys(collectExpandableKeys(permissionTree));
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

  const columns: ProColumns<RoleRecord>[] = [
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
      width: 180,
      render: (_, record) => (
        <Space size={0}>
          {canAccess('system:role:view') ? (
            <Button type="link" size="small" onClick={() => void openDetail(record)}>
              详情
            </Button>
          ) : null}
          {canAccess('system:role:update') ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {canAccess('system:role:permissions') ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              权限分配
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <PageContainer title="角色管理">
      <ProTable<RoleRecord>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
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
          canAccess('system:role:create') ? (
            <Button key="create" type="primary" onClick={openCreate}>
              新增角色
            </Button>
          ) : null,
          <Button key="refresh" onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ]}
      />

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
                    <div className="role-editor-section__meta">先勾选可访问的页面，再配置该页面下的按钮权限</div>
                  </div>
                  <Space>
                    <Button size="small" onClick={handleExpandToggle}>
                      {expandedKeys.length ? '折叠全部' : '展开全部'}
                    </Button>
                    <Button size="small" onClick={handleSelectAllPages}>
                      {selectedPageKeys.length === selectablePages.length ? '全不选' : '全选'}
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
                      showLine
                      virtual
                      height={360}
                      treeData={pageTreeData}
                      checkedKeys={selectedPageKeys}
                      expandedKeys={expandedKeys}
                      onExpand={(nextExpandedKeys) => setExpandedKeys(nextExpandedKeys.map(String))}
                      onCheck={(checkedKeys) => {
                        const nextCheckedKeys = Array.isArray(checkedKeys) ? checkedKeys.map(String) : [];
                        handlePageTreeCheck(nextCheckedKeys);
                      }}
                      onSelect={(_, info) => {
                        if ((info.node as PermissionTreeRecord).selectable && (info.node as PermissionTreeRecord).permissionKey) {
                          setActivePageKey((info.node as PermissionTreeRecord).permissionKey as string);
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

                {selectedPageOptions.length ? (
                  <>
                    <div className="role-action-toolbar">
                      <Typography.Text type="secondary">当前页面</Typography.Text>
                      <Select
                        value={activePageKey || undefined}
                        options={selectedPageOptions}
                        placeholder="请选择一个已勾选页面"
                        onChange={(value) => setActivePageKey(value)}
                        style={{ flex: 1 }}
                      />
                    </div>
                    <div className="role-action-panel__page-name">
                      {activePageNode?.pageName || '请选择一个页面'}
                      {hasRoutePath(activePageNode?.routePath) ? (
                        <Tag style={{ marginInlineStart: 8 }} color="blue">
                          {activePageNode?.routePath}
                        </Tag>
                      ) : null}
                    </div>
                    {activePageActionPermissions.length ? (
                      <Checkbox.Group
                        value={activePageSelectedActionKeys}
                        onChange={(checkedValues) => handleActionPermissionsChange(checkedValues.map(String))}
                        className="role-action-grid"
                        options={activePageActionPermissions.map((item) => ({
                          label: item.permissionName,
                          value: item.permissionKey,
                        }))}
                      />
                    ) : (
                      <div className="role-action-panel__empty">
                        <Empty description="该页面暂无字权限" image={Empty.PRESENTED_IMAGE_SIMPLE} />
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
              column={2}
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
