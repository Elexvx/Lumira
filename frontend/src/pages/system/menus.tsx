import { HolderOutlined, MinusOutlined, PlusOutlined } from '@ant-design/icons';
import { PageContainer, ProDescriptions, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Drawer, Form, Input, InputNumber, Select, Space, Spin, Tag, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState, type DragEvent } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { iamService } from '@/services/iam';
import type { MenuNode, MenuRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';
import { confirmAction } from '@/utils/confirm';

type MenuDropPosition = 'before' | 'inside' | 'after';

interface MenuDragState {
  draggedId: number;
  targetId: number;
  position: MenuDropPosition;
}

type MenuTreeRecord = MenuRecord & { level?: number };

const toRuntimeMenuNodes = (menus: MenuRecord[]): MenuNode[] =>
  menus.map((menu) => ({
    id: menu.id,
    tenantId: menu.tenantId,
    parentId: menu.parentId ?? undefined,
    menuCode: menu.menuCode,
    name: menu.menuName,
    path: menu.path ?? '',
    component: menu.component ?? undefined,
    icon: menu.icon ?? undefined,
    permissionKey: menu.permissionKey ?? undefined,
    sortNo: menu.sortNo,
    children: menu.children?.length ? toRuntimeMenuNodes(menu.children) : undefined,
  }));

const filterMenus = (menus: MenuRecord[], keyword: string, menuCode: string, permissionKey: string, level = 0): MenuTreeRecord[] => {
  const normalizedKeyword = keyword.trim().toLowerCase();
  const normalizedMenuCode = menuCode.trim().toLowerCase();
  const normalizedPermissionKey = permissionKey.trim().toLowerCase();

  return menus
    .map((menu) => {
      const matched =
        (!normalizedKeyword || menu.menuName.toLowerCase().includes(normalizedKeyword)) &&
        (!normalizedMenuCode || menu.menuCode.toLowerCase().includes(normalizedMenuCode)) &&
        (!normalizedPermissionKey || (menu.permissionKey || '').toLowerCase().includes(normalizedPermissionKey));
      const children = menu.children ? filterMenus(menu.children, keyword, menuCode, permissionKey, level + 1) : [];
      if (matched || children.length) {
        return {
          ...menu,
          level,
          children: children.length ? children : undefined,
        };
      }
      return null;
    })
    .filter(Boolean) as MenuRecord[];
};

const flattenMenus = (menus: MenuRecord[], level = 0, result: Array<MenuRecord & { level: number }> = []) => {
  menus.forEach((menu) => {
    const { children: _children, ...rest } = menu;
    result.push({ ...rest, level });
    if (menu.children?.length) {
      flattenMenus(menu.children, level + 1, result);
    }
  });
  return result;
};

const flattenVisibleMenus = (
  menus: MenuTreeRecord[],
  expandedRowKeys: number[],
  includeAllChildren = false,
  level = 0,
  result: MenuTreeRecord[] = [],
) => {
  menus.forEach((menu) => {
    const { children: _children, ...rest } = menu;
    const currentMenu = { ...rest, level };
    result.push(currentMenu);

    if (menu.children?.length && (includeAllChildren || expandedRowKeys.includes(menu.id))) {
      flattenVisibleMenus(menu.children as MenuTreeRecord[], expandedRowKeys, includeAllChildren, level + 1, result);
    }
  });

  return result;
};

const sortMenuTree = (menus: MenuRecord[]): MenuRecord[] =>
  [...menus]
    .sort((left, right) => (left.sortNo ?? 0) - (right.sortNo ?? 0) || left.id - right.id)
    .map((menu) => ({
      ...menu,
      children: menu.children?.length ? sortMenuTree(menu.children) : undefined,
    }));

const normalizeMenuTreeOrder = (menus: MenuRecord[], parentId = 0): MenuRecord[] =>
  menus.map((menu, index) => ({
    ...menu,
    parentId,
    sortNo: index,
    children: menu.children?.length ? normalizeMenuTreeOrder(menu.children, menu.id) : undefined,
  }));

const flattenMenuOrder = (menus: MenuRecord[], parentId = 0, result: Array<{ id: number; parentId?: number | null; sortNo: number }> = []) => {
  menus.forEach((menu, index) => {
    result.push({
      id: menu.id,
      parentId,
      sortNo: index,
    });
    if (menu.children?.length) {
      flattenMenuOrder(menu.children, menu.id, result);
    }
  });
  return result;
};

const extractMenuNode = (menus: MenuRecord[], menuId: number): { menus: MenuRecord[]; node?: MenuRecord } => {
  const nextMenus: MenuRecord[] = [];
  let extractedNode: MenuRecord | undefined;

  for (const menu of menus) {
    if (menu.id === menuId) {
      extractedNode = menu;
      continue;
    }

    if (menu.children?.length) {
      const childResult = extractMenuNode(menu.children, menuId);
      if (childResult.node) {
        extractedNode = childResult.node;
        nextMenus.push({
          ...menu,
          children: childResult.menus.length ? childResult.menus : undefined,
        });
        continue;
      }
    }

    nextMenus.push(menu);
  }

  return { menus: nextMenus, node: extractedNode };
};

const insertMenuNode = (
  menus: MenuRecord[],
  targetId: number,
  node: MenuRecord,
  position: MenuDropPosition,
): { menus: MenuRecord[]; inserted: boolean } => {
  const nextMenus: MenuRecord[] = [];
  let inserted = false;

  for (const menu of menus) {
    if (position === 'before' && menu.id === targetId) {
      nextMenus.push(node, menu);
      inserted = true;
      continue;
    }

    if (position === 'after' && menu.id === targetId) {
      nextMenus.push(menu, node);
      inserted = true;
      continue;
    }

    if (position === 'inside' && menu.id === targetId) {
      if (menu.menuType === 'BUTTON') {
        nextMenus.push(menu);
        continue;
      }
      nextMenus.push({
        ...menu,
        children: [...(menu.children || []), node],
      });
      inserted = true;
      continue;
    }

    if (menu.children?.length) {
      const childResult = insertMenuNode(menu.children, targetId, node, position);
      if (childResult.inserted) {
        nextMenus.push({
          ...menu,
          children: childResult.menus,
        });
        inserted = true;
        continue;
      }
    }

    nextMenus.push(menu);
  }

  return { menus: nextMenus, inserted };
};

const moveMenuNode = (menus: MenuRecord[], draggedId: number, targetId: number, position: MenuDropPosition) => {
  if (draggedId === targetId) {
    return null;
  }

  const extracted = extractMenuNode(menus, draggedId);
  if (!extracted.node) {
    return null;
  }

  const inserted = insertMenuNode(extracted.menus, targetId, extracted.node, position);
  if (!inserted.inserted) {
    return null;
  }

  return normalizeMenuTreeOrder(inserted.menus);
};

const getDropPosition = (event: DragEvent<HTMLTableRowElement>, record: MenuRecord): MenuDropPosition => {
  const row = event.currentTarget;
  const bounds = row.getBoundingClientRect();
  const offsetY = event.clientY - bounds.top;
  const ratio = bounds.height <= 0 ? 0.5 : offsetY / bounds.height;

  if (ratio <= 0.25) {
    return 'before';
  }
  if (ratio >= 0.75) {
    return 'after';
  }

  return record.menuType === 'BUTTON' ? 'after' : 'inside';
};

const MenuManagementPage = () => {
  const actionRef = useRef<ActionType>();
  const [editorForm] = Form.useForm();
  const { canAccess } = usePermission();
  const { setInitialState } = useInitialStateModel();
  const [selectedMenu, setSelectedMenu] = useState<MenuRecord | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [menuTree, setMenuTree] = useState<MenuRecord[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dragState, setDragState] = useState<MenuDragState | null>(null);
  const [reordering, setReordering] = useState(false);
  const [expandedRowKeys, setExpandedRowKeys] = useState<number[]>([]);
  const expandableMenuIds = useMemo(() => {
    const ids = new Set<number>();
    const collectIds = (menus: MenuRecord[]) => {
      menus.forEach((menu) => {
        if (menu.children?.length) {
          ids.add(menu.id);
          collectIds(menu.children);
        }
      });
    };
    collectIds(menuTree);
    return ids;
  }, [menuTree]);

  const loadMenus = useCallback(async () => {
    const result = await iamService.menus({ autoRedirectOnUnauthorized: false });
    const sortedResult = sortMenuTree(result);
    setMenuTree(sortedResult);
    setInitialState((prev) =>
      prev
        ? {
            ...prev,
            menuTree: toRuntimeMenuNodes(sortedResult),
            menuVersion: (prev.menuVersion ?? 0) + 1,
          }
        : prev,
    );
    setExpandedRowKeys((currentKeys) => {
      const validKeys = new Set<number>();
      const collectIds = (menus: MenuRecord[]) => {
        menus.forEach((menu) => {
          validKeys.add(menu.id);
          if (menu.children?.length) {
            collectIds(menu.children);
          }
        });
      };
      collectIds(sortedResult);
      return currentKeys.filter((key) => validKeys.has(key));
    });
  }, [setInitialState]);

  useEffect(() => {
    void (async () => {
      try {
        await loadMenus();
        actionRef.current?.reload();
      } catch {
      }
    })();
  }, [loadMenus]);

  useEffect(() => {
    actionRef.current?.reload();
  }, [expandedRowKeys]);

  const flatMenus = useMemo(() => flattenMenus(menuTree), [menuTree]);

  const saveMenuOrder = useCallback(
    async (nextTree: MenuRecord[]) => {
      const normalizedTree = normalizeMenuTreeOrder(nextTree);
      const previousTree = menuTree;
      setMenuTree(normalizedTree);
      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              menuTree: toRuntimeMenuNodes(normalizedTree),
              menuVersion: (prev.menuVersion ?? 0) + 1,
            }
          : prev,
      );
      setReordering(true);
      try {
        await iamService.reorderMenus(
          {
            items: flattenMenuOrder(normalizedTree),
          },
          { autoRedirectOnUnauthorized: false },
        );
        message.success('菜单顺序已更新');
        actionRef.current?.reload();
      } catch (error) {
        setMenuTree(previousTree);
        setInitialState((prev) =>
          prev
            ? {
                ...prev,
                menuTree: toRuntimeMenuNodes(previousTree),
                menuVersion: (prev.menuVersion ?? 0) + 1,
              }
            : prev,
        );
        throw error;
      } finally {
        setReordering(false);
        setDragState(null);
      }
    },
    [menuTree, setInitialState],
  );

  const openCreate = () => {
    setSelectedMenu(null);
    setEditingId(null);
    editorForm.resetFields();
    editorForm.setFieldsValue({ menuType: 'MENU', status: 'ENABLED', sortNo: 0 });
    setEditorOpen(true);
  };

  const openEdit = async (record: MenuRecord) => {
    setSelectedMenu(record);
    setEditingId(record.id);
    setEditorOpen(true);
    const detail = await iamService.menuDetail(record.id, { autoRedirectOnUnauthorized: false });
    editorForm.setFieldsValue({
      ...detail,
      parentId: detail.parentId ?? undefined,
    });
  };

  const openDetail = async (record: MenuRecord) => {
    setSelectedMenu(record);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const detail = await iamService.menuDetail(record.id, { autoRedirectOnUnauthorized: false });
      setSelectedMenu(detail);
    } finally {
      setDetailLoading(false);
    }
  };

  const saveMenu = async () => {
    setSaving(true);
    try {
      const values = await editorForm.validateFields();
      if (editingId) {
        await iamService.updateMenu(editingId, values, { autoRedirectOnUnauthorized: false });
        message.success('菜单已更新');
      } else {
        await iamService.createMenu(values, { autoRedirectOnUnauthorized: false });
        message.success('菜单已创建');
      }
      setEditorOpen(false);
      await loadMenus();
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const handleRowDragStart = (record: MenuRecord) => (event: DragEvent<HTMLTableRowElement>) => {
    if (!canAccess('system:menu:update') || reordering) {
      event.preventDefault();
      return;
    }
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', String(record.id));
    setDragState({
      draggedId: record.id,
      targetId: record.id,
      position: 'inside',
    });
  };

  const handleRowDragOver = (record: MenuRecord) => (event: DragEvent<HTMLTableRowElement>) => {
    if (!dragState || dragState.draggedId === record.id || !canAccess('system:menu:update') || reordering) {
      return;
    }
    event.preventDefault();
    const position = getDropPosition(event, record);
    setDragState((current) => {
      if (!current || current.draggedId !== dragState.draggedId || current.targetId === record.id && current.position === position) {
        return current;
      }
      return {
        draggedId: current.draggedId,
        targetId: record.id,
        position,
      };
    });
  };

  const handleRowDrop = (record: MenuRecord) => async (event: DragEvent<HTMLTableRowElement>) => {
    event.preventDefault();
    if (!dragState || dragState.draggedId === record.id || !canAccess('system:menu:update') || reordering) {
      setDragState(null);
      return;
    }

    const position = getDropPosition(event, record);
    const nextTree = moveMenuNode(menuTree, dragState.draggedId, record.id, position);
    setDragState(null);

    if (!nextTree) {
      return;
    }

    try {
      await saveMenuOrder(nextTree);
    } catch {
      // Revert is handled inside saveMenuOrder; keep the UI responsive.
    }
  };

  const handleRowDragEnd = () => {
    setDragState(null);
  };

  const updateMenuStatus = async (record: MenuRecord, status: 'ENABLED' | 'DISABLED') => {
    await iamService.changeMenuStatus(record.id, status, { autoRedirectOnUnauthorized: false });
    message.success('状态已更新');
    await loadMenus();
    actionRef.current?.reload();
  };

  const handleStatusToggle = (record: MenuRecord) => {
    if (record.status !== 'ENABLED') {
      void updateMenuStatus(record, 'ENABLED');
      return;
    }

    confirmAction({
      title: '停用菜单',
      content: `确认停用菜单「${record.menuName}」吗？停用后该菜单将不再在前台展示。`,
      okText: '确认停用',
      okButtonProps: { danger: true },
      onOk: async () => {
        await updateMenuStatus(record, 'DISABLED');
      },
    });
  };

  const columns: ProColumns<MenuTreeRecord>[] = [
    {
      title: '拖拽',
      dataIndex: 'dragHandle',
      width: 88,
      hideInSearch: true,
      render: (_, record) => {
        const hasChildren = expandableMenuIds.has(record.id);
        const expanded = expandedRowKeys.includes(record.id);

        return (
          <Space size={8}>
            <Button
              type="text"
              size="small"
              aria-label={hasChildren ? (expanded ? '折叠行' : '展开行') : undefined}
              icon={hasChildren ? (expanded ? <MinusOutlined /> : <PlusOutlined />) : null}
              disabled={!hasChildren}
              onClick={(event) => {
                event.stopPropagation();
                if (!hasChildren) {
                  return;
                }
                setExpandedRowKeys((currentKeys) =>
                  expanded ? currentKeys.filter((key) => key !== record.id) : [...currentKeys, record.id],
                );
              }}
              style={{
                width: 24,
                height: 24,
                padding: 0,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            />
            <HolderOutlined style={{ color: '#8c8c8c', cursor: 'grab' }} />
          </Space>
        );
      },
    },
    {
      title: '菜单编码',
      dataIndex: 'menuCode',
      hideInSearch: true,
      hideInTable: true,
    },
    {
      title: '菜单名称',
      dataIndex: 'menuName',
      search: true,
      render: (_, record) => (
        <span style={{ paddingInlineStart: `${(record.level || 0) * 24}px` }}>{record.menuName}</span>
      ),
    },
    {
      title: '菜单类型',
      dataIndex: 'menuType',
      valueEnum: {
        CATALOG: { text: '目录' },
        MENU: { text: '菜单' },
        BUTTON: { text: '按钮' },
      },
    },
    {
      title: '路由',
      dataIndex: 'path',
      hideInSearch: true,
    },
    {
      title: '组件',
      dataIndex: 'component',
      hideInSearch: true,
      width: 260,
      ellipsis: true,
    },
    {
      title: '权限标识',
      dataIndex: 'permissionKey',
      search: true,
      render: (_, record) => record.permissionKey || '-',
    },
    {
      title: '排序',
      dataIndex: 'sortNo',
      hideInSearch: true,
      width: 88,
      render: (_, record) => record.sortNo ?? 0,
    },
    {
      title: '状态',
      dataIndex: 'status',
      hideInSearch: true,
      render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
    },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 180,
      render: (_, record) => (
        <Space size={0}>
          {canAccess('system:menu:view') ? (
            <Button type="link" size="small" onClick={() => void openDetail(record)}>
              详情
            </Button>
          ) : null}
          {canAccess('system:menu:update') ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {canAccess('system:menu:status') ? (
            <Button
              type="link"
              size="small"
              danger={record.status === 'ENABLED'}
              onClick={() => void handleStatusToggle(record)}
            >
              {record.status === 'ENABLED' ? '停用' : '启用'}
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <PageContainer
      title="菜单管理"
    >
      <ProTable<MenuRecord & { level?: number }>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        search={{ labelWidth: 'auto' }}
        options={false}
        pagination={false}
        scroll={{ x: 1500 }}
        onRow={(record) => ({
          draggable: canAccess('system:menu:update') && !reordering,
          onDragStart: handleRowDragStart(record),
          onDragOver: handleRowDragOver(record),
          onDrop: handleRowDrop(record),
          onDragEnd: handleRowDragEnd,
          style: {
            cursor: canAccess('system:menu:update') && !reordering ? 'grab' : undefined,
            userSelect: 'none',
            opacity: dragState?.draggedId === record.id ? 0.35 : 1,
            backgroundColor:
              dragState?.targetId === record.id && dragState.position === 'inside'
                ? 'rgba(22, 119, 255, 0.08)'
                : undefined,
            boxShadow:
              dragState?.targetId === record.id && dragState.position === 'before'
                ? 'inset 0 2px 0 #1677ff'
                : dragState?.targetId === record.id && dragState.position === 'after'
                  ? 'inset 0 -2px 0 #1677ff'
                  : undefined,
          },
        })}
        request={async (params) => {
          const keyword = String(params.menuName || params.keyword || '');
          const menuCode = String(params.menuCode || '');
          const permissionKey = String(params.permissionKey || '');
          const filtered = filterMenus(menuTree, keyword, menuCode, permissionKey);
          const hasSearch = Boolean(keyword.trim() || menuCode.trim() || permissionKey.trim());
          const visibleMenus = hasSearch
            ? flattenMenus(filtered)
            : flattenVisibleMenus(filtered, expandedRowKeys);
          return {
            data: visibleMenus,
            success: true,
            total: visibleMenus.length,
          };
        }}
        toolBarRender={() => [
          canAccess('system:menu:create') ? (
            <Button key="create" type="primary" onClick={openCreate}>
              新增菜单
            </Button>
          ) : null,
          <Button
            key="refresh"
            onClick={async () => {
              await loadMenus();
              actionRef.current?.reload();
            }}
          >
            刷新
          </Button>,
        ]}
      />

      <Drawer
        title={editingId ? '编辑菜单' : '新增菜单'}
        open={editorOpen}
        onClose={() => setEditorOpen(false)}
        width={720}
        destroyOnClose
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={() => setEditorOpen(false)}>取消</Button>
              <Button type="primary" loading={saving} onClick={() => void saveMenu()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={editorForm} layout="vertical" initialValues={{ menuType: 'MENU', status: 'ENABLED', sortNo: 0 }}>
          <Form.Item name="parentId" label="上级菜单">
            <Select
              allowClear
              options={flatMenus.map((menu) => ({
                label: `${'　'.repeat(menu.level || 0)}${menu.menuName}`,
                value: menu.id,
              }))}
            />
          </Form.Item>
          <Form.Item name="menuCode" label="菜单编码" rules={[{ required: true, message: '请输入菜单编码' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="menuName" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="menuType" label="菜单类型" rules={[{ required: true, message: '请选择菜单类型' }]}>
            <Select
              options={[
                { label: '目录', value: 'CATALOG' },
                { label: '菜单', value: 'MENU' },
                { label: '按钮', value: 'BUTTON' },
              ]}
            />
          </Form.Item>
          <Form.Item name="path" label="路由">
            <Input />
          </Form.Item>
          <Form.Item name="component" label="组件">
            <Input />
          </Form.Item>
          <Form.Item name="icon" label="图标">
            <Input />
          </Form.Item>
          <Form.Item name="sortNo" label="排序">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="permissionKey" label="权限标识">
            <Input />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { label: '启用', value: 'ENABLED' },
                { label: '停用', value: 'DISABLED' },
              ]}
            />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={selectedMenu ? `菜单详情 · ${selectedMenu.menuName}` : '菜单详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setSelectedMenu(null);
        }}
        width={720}
        destroyOnClose
      >
        {detailLoading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : selectedMenu ? (
          <ProDescriptions<MenuRecord>
            column={2}
            dataSource={selectedMenu}
            columns={[
              { title: '菜单名称', dataIndex: 'menuName' },
              { title: '菜单类型', dataIndex: 'menuType' },
              { title: '路由', dataIndex: 'path', renderText: (value) => value || '-' },
              { title: '组件', dataIndex: 'component', renderText: (value) => value || '-' },
              { title: '权限标识', dataIndex: 'permissionKey', renderText: (value) => value || '-' },
              { title: '状态', dataIndex: 'status' },
            ]}
          />
        ) : null}
      </Drawer>
    </PageContainer>
  );
};

export default MenuManagementPage;
