import { HolderOutlined } from '@ant-design/icons';
import { ProDescriptions, type ProColumns } from '@ant-design/pro-components';
import { Button, Form, Input, Popover, Space, Spin, Tabs, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useState, type DragEvent } from 'react';
import { formatMessage } from '@umijs/max';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { buildMenuColumns, menuDetailColumns } from '@/pages/settings/menus/columns';
import { MenuEditorForm, buildParentMenuOptions } from '@/pages/settings/menus/components/MenuEditorForm';
import {
  flattenMenuOrder,
  flattenMenus,
  getDropPosition,
  moveMenuNode,
  normalizeMenuTreeOrder,
  sortMenuTree,
  toRuntimeMenuNodes,
  type MenuDropPosition,
} from '@/pages/settings/menus/treeUtils';
import { buildMenuTableData } from '@/pages/settings/menus/tableData';
import { iamService } from '@/services/iam';
import { backendRouteMeta } from '@/routes/meta';
import type { MenuRecord } from '@/types/api';
import { confirmAction } from '@/utils/confirm';
import { resolveBuiltinMessage } from '@/i18n/messages';
import {
  DEFAULT_SETTING_ROUTE_ORDER,
  getStoredSettingRouteIcons,
  getStoredSettingRouteOrder,
  persistSettingRouteIcons,
  persistSettingRouteOrder,
  resetSettingRouteOrder,
} from '@/navigation/settingsRouteOrder';
import { isMainMenuHiddenSettingPath } from '@/navigation/settingsNavigation';

interface MenuDragState {
  draggedId: number;
  targetId: number;
  position: MenuDropPosition;
}

const isBuiltinMenu = (record: Pick<MenuRecord, 'id'>) => record.id < 0;

const formatRouteName = (name: string) =>
  resolveBuiltinMessage(
    name,
    formatMessage({
      id: name,
      defaultMessage: name,
    }),
  );

interface SettingsRouteRecord {
  id: string;
  menuCode: string;
  menuName: string;
  menuType: string;
  path: string;
  icon?: string;
  defaultIcon?: string;
  customIcon?: string;
  permissionKey?: string;
  component?: string;
  sortNo: number;
  status: string;
}

const buildSettingsRouteRecords = (routeOrder: string[], routeIcons: Record<string, string>): SettingsRouteRecord[] => routeOrder.map((path, index) => {
  const meta = backendRouteMeta.find((item) => item.path === path);
  const customIcon = routeIcons[path];
  const defaultIcon = meta?.icon;
  return {
    id: path,
    menuCode: `settings:${path.replace(/^\/settings\/?/, '').replace(/\//g, ':') || 'root'}`,
    menuName: meta ? formatRouteName(meta.name) : path,
    menuType: 'MENU',
    path,
    icon: customIcon || defaultIcon,
    defaultIcon,
    customIcon,
    permissionKey: meta?.access,
    component: meta?.path ? `@/pages${meta.path}` : '-',
    sortNo: index + 1,
    status: 'ENABLED',
  };
});

const moveArrayItem = <T,>(items: T[], fromIndex: number, toIndex: number) => {
  const nextItems = [...items];
  const [movedItem] = nextItems.splice(fromIndex, 1);
  nextItems.splice(toIndex, 0, movedItem);
  return nextItems;
};

const buildMainRouteMenuTree = (menus: MenuRecord[]): MenuRecord[] =>
  menus
    .filter((menu) => !isMainMenuHiddenSettingPath(menu.path ?? undefined))
    .map((menu) => ({
      ...menu,
      children: menu.children?.length ? buildMainRouteMenuTree(menu.children) : undefined,
    }));

const SettingsRoutesTab = () => {
  const { setInitialState } = useInitialStateModel();
  const responsive = useResponsive();
  const [routeOrder, setRouteOrder] = useState(() => getStoredSettingRouteOrder());
  const [routeIcons, setRouteIcons] = useState(() => getStoredSettingRouteIcons());
  const [editingRouteIcons, setEditingRouteIcons] = useState<Record<string, string>>(() => getStoredSettingRouteIcons());
  const records = useMemo(() => buildSettingsRouteRecords(routeOrder, routeIcons), [routeOrder, routeIcons]);
  const canResetOrder = routeOrder.join('|') !== DEFAULT_SETTING_ROUTE_ORDER.join('|');

  const refreshSettingsNavigation = () => {
    setInitialState((prev) =>
      prev
        ? {
            ...prev,
            menuVersion: (prev.menuVersion ?? 0) + 1,
          }
        : prev,
    );
  };

  const updateRouteOrder = (nextOrder: string[]) => {
    persistSettingRouteOrder(nextOrder);
    setRouteOrder(nextOrder);
    refreshSettingsNavigation();
    message.success('设置页路由顺序已更新');
  };

  const updateRouteIcon = (record: SettingsRouteRecord) => {
    const nextIcon = (editingRouteIcons[record.path] || '').trim();
    const nextIcons = {
      ...routeIcons,
      [record.path]: nextIcon,
    };
    persistSettingRouteIcons(nextIcons);
    const normalizedIcons = getStoredSettingRouteIcons();
    setRouteIcons(normalizedIcons);
    setEditingRouteIcons(normalizedIcons);
    refreshSettingsNavigation();
    message.success(nextIcon ? '设置页路由图标已更新' : '设置页路由图标已恢复默认');
  };

  const resetRouteIcon = (record: SettingsRouteRecord) => {
    const { [record.path]: _removed, ...nextIcons } = routeIcons;
    persistSettingRouteIcons(nextIcons);
    const normalizedIcons = getStoredSettingRouteIcons();
    setRouteIcons(normalizedIcons);
    setEditingRouteIcons(normalizedIcons);
    refreshSettingsNavigation();
    message.success('设置页路由图标已恢复默认');
  };

  const moveRoute = (record: SettingsRouteRecord, direction: -1 | 1) => {
    const currentIndex = routeOrder.indexOf(record.path);
    const nextIndex = currentIndex + direction;
    if (currentIndex < 0 || nextIndex < 0 || nextIndex >= routeOrder.length) {
      return;
    }
    updateRouteOrder(moveArrayItem(routeOrder, currentIndex, nextIndex));
  };

  const resetOrder = () => {
    resetSettingRouteOrder();
    setRouteOrder(DEFAULT_SETTING_ROUTE_ORDER);
    refreshSettingsNavigation();
    message.success('设置页路由顺序已恢复默认');
  };

  const columns: ProColumns<SettingsRouteRecord>[] = [
    {
      title: '拖拽',
      dataIndex: 'dragHandle',
      width: 96,
      search: false,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      render: () => <HolderOutlined style={{ color: '#8c8c8c', cursor: 'default' }} />,
    },
    {
      title: '菜单编码',
      dataIndex: 'menuCode',
      search: false,
      width: 180,
      responsive: ['lg', 'xl', 'xxl'],
      ellipsis: true,
      render: (_, record) => <Typography.Text ellipsis={{ tooltip: record.menuCode }}>{record.menuCode}</Typography.Text>,
    },
    {
      title: '菜单名称',
      dataIndex: 'menuName',
      width: 260,
      search: true,
      ellipsis: true,
      render: (_, record) => (
        <Typography.Text className="saas-menu-tree-cell" ellipsis={{ tooltip: record.menuName }}>
          {record.menuName}
        </Typography.Text>
      ),
    },
    {
      title: '菜单类型',
      dataIndex: 'menuType',
      width: 120,
      valueEnum: {
        MENU: { text: '菜单' },
      },
    },
    {
      title: '路由',
      dataIndex: 'path',
      search: false,
      width: 220,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      ellipsis: true,
    },
    {
      title: '图标',
      dataIndex: 'icon',
      search: false,
      width: 180,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      ellipsis: true,
      render: (_, record) => record.icon || '-',
    },
    {
      title: '组件',
      dataIndex: 'component',
      search: false,
      width: 300,
      responsive: ['lg', 'xl', 'xxl'],
      ellipsis: true,
    },
    {
      title: '权限标识',
      dataIndex: 'permissionKey',
      width: 220,
      search: true,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      ellipsis: true,
      render: (_, record) =>
        record.permissionKey ? <Typography.Text ellipsis={{ tooltip: record.permissionKey }}>{record.permissionKey}</Typography.Text> : '-',
    },
    {
      title: '排序',
      dataIndex: 'sortNo',
      search: false,
      width: 88,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      render: (_, record) => record.sortNo,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      search: false,
      render: (_, record) => <Tag color="green">{record.status}</Tag>,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: (_, record, index) => (
        <Space>
          <Popover
            trigger="click"
            title="设置图标"
            content={(
              <Space.Compact>
                <Input
                  allowClear
                  value={editingRouteIcons[record.path] ?? ''}
                  placeholder={record.defaultIcon || '如：SettingOutlined'}
                  style={{ width: 180 }}
                  onChange={(event) =>
                    setEditingRouteIcons((current) => ({
                      ...current,
                      [record.path]: event.target.value,
                    }))
                  }
                  onPressEnter={() => updateRouteIcon(record)}
                />
                <Button onClick={() => updateRouteIcon(record)}>保存</Button>
                {record.customIcon ? <Button onClick={() => resetRouteIcon(record)}>默认</Button> : null}
              </Space.Compact>
            )}
          >
            <Button type="link">图标</Button>
          </Popover>
          <Button type="link" disabled={index === 0} onClick={() => moveRoute(record, -1)}>
            上移
          </Button>
          <Button type="link" disabled={index === records.length - 1} onClick={() => moveRoute(record, 1)}>
            下移
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <ManagementTable<SettingsRouteRecord>
      rowKey="id"
      dataSource={records}
      pagination={false}
      scroll={{ x: 1780 }}
      tableLayout="fixed"
      isMobile={responsive.isMobile}
      search={false}
      toolBarRender={() =>
        canResetOrder ? (
          [<Button key="reset" onClick={resetOrder}>恢复默认顺序</Button>]
        ) : []
      }
      columns={columns}
    />
  );
};

const MenuManagementPage = () => {
  const menuCrud = useCrudPageState<MenuRecord>();
  const [editorForm] = Form.useForm();
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const { setInitialState } = useInitialStateModel();
  const [menuTree, setMenuTree] = useState<MenuRecord[]>([]);
  const [saving, setSaving] = useState(false);
  const [dragState, setDragState] = useState<MenuDragState | null>(null);
  const [reordering, setReordering] = useState(false);
  const [activeTab, setActiveTab] = useState('main');
  const [expandedRowKeys, setExpandedRowKeys] = useState<number[]>([]);
  const canSaveMenu = actionPermission.can(menuCrud.drawer.editingId ? 'system:menu:update' : 'system:menu:create');
  const editorFormProps = useStandardFormProps({
    form: editorForm,
    initialValues: { menuType: 'MENU', status: 'ENABLED', sortNo: 0 },
  });
  const detailProps = useDetailProDescriptionsProps<MenuRecord>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: menuCrud.detail.currentRecord || undefined,
  });
  const canReorderMenus = actionPermission.can('system:menu:update') && !reordering;
  const mainRouteMenuTree = useMemo(() => buildMainRouteMenuTree(menuTree), [menuTree]);
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
    collectIds(mainRouteMenuTree);
    return ids;
  }, [mainRouteMenuTree]);

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
      } catch {
        // keep silent: global request interceptor already handles feedback
      }
    })();
  }, [loadMenus]);

  useEffect(() => {
    menuCrud.reloadTable();
  }, [expandedRowKeys, menuCrud.reloadTable, menuTree]);

  const flatMenus = useMemo(() => flattenMenus(mainRouteMenuTree), [mainRouteMenuTree]);
  const editableFlatMenus = useMemo(() => flatMenus.filter((menu) => !isBuiltinMenu(menu)), [flatMenus]);

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
            items: flattenMenuOrder(normalizedTree).filter((item) => item.id > 0),
          },
          { autoRedirectOnUnauthorized: false },
        );
        message.success('菜单顺序已更新');
        menuCrud.reloadTable();
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
    menuCrud.drawer.openCreate();
    editorForm.resetFields();
    editorForm.setFieldsValue({ menuType: 'MENU', status: 'ENABLED', sortNo: 0 });
  };

  const openEdit = async (record: MenuRecord) => {
    if (isBuiltinMenu(record)) {
      message.warning('内置菜单不支持编辑');
      return;
    }
    menuCrud.drawer.openEdit(record, record.id);
    const detail = await iamService.menuDetail(record.id, { autoRedirectOnUnauthorized: false });
    editorForm.setFieldsValue({
      ...detail,
      parentId: detail.parentId ?? undefined,
    });
  };

  const openDetail = async (record: MenuRecord) => {
    if (isBuiltinMenu(record)) {
      menuCrud.detail.openDetail(record);
      menuCrud.detail.setLoading(false);
      menuCrud.detail.setCurrentRecord(record);
      return;
    }
    menuCrud.detail.openDetail(record);
    menuCrud.detail.setLoading(true);
    try {
      const detail = await iamService.menuDetail(record.id, { autoRedirectOnUnauthorized: false });
      menuCrud.detail.setCurrentRecord(detail);
    } finally {
      menuCrud.detail.setLoading(false);
    }
  };

  const saveMenu = async () => {
    setSaving(true);
    try {
      const values = await editorForm.validateFields();
      if (menuCrud.drawer.editingId) {
        await iamService.updateMenu(menuCrud.drawer.editingId, values, { autoRedirectOnUnauthorized: false });
        message.success('菜单已更新');
      } else {
        await iamService.createMenu(values, { autoRedirectOnUnauthorized: false });
        message.success('菜单已创建');
      }
      menuCrud.drawer.close();
      await loadMenus();
      menuCrud.reloadTable();
    } finally {
      setSaving(false);
    }
  };

  const handleRowDragStart = (record: MenuRecord) => (event: DragEvent<HTMLTableRowElement>) => {
    if (!canReorderMenus || isBuiltinMenu(record)) {
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
    if (!dragState || dragState.draggedId === record.id || !canReorderMenus || isBuiltinMenu(record)) {
      return;
    }
    event.preventDefault();
    const position = getDropPosition(event, record);
    setDragState((current) => {
      if (!current || current.draggedId !== dragState.draggedId || (current.targetId === record.id && current.position === position)) {
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
    if (!dragState || dragState.draggedId === record.id || !canReorderMenus || isBuiltinMenu(record)) {
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
    if (isBuiltinMenu(record)) {
      message.warning('内置菜单不支持修改状态');
      return;
    }
    await iamService.changeMenuStatus(record.id, status, { autoRedirectOnUnauthorized: false });
    message.success('状态已更新');
    await loadMenus();
    menuCrud.reloadTable();
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

  const deleteMenu = (record: MenuRecord) => {
    if (isBuiltinMenu(record)) {
      message.warning('内置菜单不支持删除');
      return;
    }
    confirmAction({
      title: '删除菜单',
      content: `确认删除菜单「${record.menuName}」吗？删除后权限树和运行菜单将不再出现该项。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await iamService.deleteMenu(record.id, { autoRedirectOnUnauthorized: false });
        message.success('菜单已删除');
        await loadMenus();
        menuCrud.reloadTable();
      },
    });
  };

  const columns = useMemo(
    () =>
      buildMenuColumns({
        isMobile: responsive.isMobile,
        canReorderMenus,
        expandedRowKeys,
        expandableMenuIds,
        buildRowActions: actionPermission.buildTableActions,
        isReadonlyMenu: isBuiltinMenu,
        onToggleExpand: (menuId) =>
          setExpandedRowKeys((currentKeys) =>
            currentKeys.includes(menuId) ? currentKeys.filter((key) => key !== menuId) : [...currentKeys, menuId],
          ),
        onOpenDetail: (record) => void openDetail(record),
        onOpenEdit: (record) => void openEdit(record),
        onToggleStatus: (record) => void handleStatusToggle(record),
        onDelete: deleteMenu,
      }),
    [
      actionPermission.buildTableActions,
      canReorderMenus,
      expandedRowKeys,
      expandableMenuIds,
      responsive.isDesktop,
      responsive.isMobile,
    ],
  );

  return (
    <ManagementPage title="菜单管理">
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'main',
            label: '主路由菜单',
            children: (
              <ManagementTable<MenuRecord & { level?: number }>
                actionRef={menuCrud.actionRef}
                rowKey="id"
                columns={columns}
                isMobile={responsive.isMobile}
                search={searchConfig}
                pagination={false}
                scroll={{ x: 1780 }}
                tableLayout="fixed"
                onRow={(record) => ({
                  draggable: canReorderMenus && !isBuiltinMenu(record),
                  onDragStart: handleRowDragStart(record),
                  onDragOver: handleRowDragOver(record),
                  onDrop: handleRowDrop(record),
                  onDragEnd: handleRowDragEnd,
                  style: {
                    cursor: canReorderMenus && !isBuiltinMenu(record) ? 'grab' : undefined,
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
                  const visibleMenus = buildMenuTableData(mainRouteMenuTree, expandedRowKeys, params);
                  return {
                    data: visibleMenus,
                    success: true,
                    total: visibleMenus.length,
                  };
                }}
                toolBarRender={() =>
                  buildToolbarButtons([
                    {
                      key: 'create',
                      permission: 'system:menu:create',
                      type: 'primary',
                      label: '新增菜单',
                      onClick: openCreate,
                    },
                    {
                      key: 'refresh',
                      label: '刷新',
                      onClick: async () => {
                        await loadMenus();
                        menuCrud.reloadTable();
                      },
                    },
                  ])
                }
              />
            ),
          },
          {
            key: 'settings',
            label: '设置页路由',
            children: <SettingsRoutesTab />,
          },
        ]}
      />

      <ManagementDrawer
        title={menuCrud.drawer.editingId ? '编辑菜单' : '新增菜单'}
        open={menuCrud.drawer.open}
        onClose={menuCrud.drawer.close}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: menuCrud.drawer.close },
          { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canSaveMenu, onClick: () => void saveMenu() },
        ]}
      >
        <MenuEditorForm formProps={editorFormProps} parentOptions={buildParentMenuOptions(editableFlatMenus)} />
      </ManagementDrawer>

      <ManagementDrawer
        title={menuCrud.detail.currentRecord ? `菜单详情 · ${menuCrud.detail.currentRecord.menuName}` : '菜单详情'}
        open={menuCrud.detail.open}
        onClose={menuCrud.detail.close}
      >
        {menuCrud.detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : menuCrud.detail.currentRecord ? (
          <ProDescriptions<MenuRecord> {...detailProps} columns={menuDetailColumns} />
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default MenuManagementPage;
