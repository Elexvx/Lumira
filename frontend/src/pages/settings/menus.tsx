import { ProDescriptions } from '@ant-design/pro-components';
import { Button, Form, Space, Spin, Tabs, Tag, message } from 'antd';
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
  getStoredSettingRouteOrder,
  persistSettingRouteOrder,
  resetSettingRouteOrder,
} from '@/navigation/settingsRouteOrder';

interface MenuDragState {
  draggedId: number;
  targetId: number;
  position: MenuDropPosition;
}

const isBuiltinMenu = (record: Pick<MenuRecord, 'builtin' | 'id'>) => Boolean(record.builtin || record.id < 0);

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
  name: string;
  path: string;
  icon?: string;
  access?: string;
  sortNo: number;
  manageMode: string;
}

const buildSettingsRouteRecords = (routeOrder: string[]): SettingsRouteRecord[] => routeOrder.map((path, index) => {
  const meta = backendRouteMeta.find((item) => item.path === path);
  return {
    id: path,
    name: meta ? formatRouteName(meta.name) : path,
    path,
    icon: meta?.icon,
    access: meta?.access,
    sortNo: index + 1,
    manageMode: '平台内置',
  };
});

const moveArrayItem = <T,>(items: T[], fromIndex: number, toIndex: number) => {
  const nextItems = [...items];
  const [movedItem] = nextItems.splice(fromIndex, 1);
  nextItems.splice(toIndex, 0, movedItem);
  return nextItems;
};

const SettingsRoutesTab = () => {
  const { setInitialState } = useInitialStateModel();
  const responsive = useResponsive();
  const [routeOrder, setRouteOrder] = useState(() => getStoredSettingRouteOrder());
  const records = useMemo(() => buildSettingsRouteRecords(routeOrder), [routeOrder]);
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

  return (
    <ManagementTable<SettingsRouteRecord>
      rowKey="id"
      dataSource={records}
      pagination={false}
      scroll={{ x: 1180 }}
      tableLayout="fixed"
      isMobile={responsive.isMobile}
      search={false}
      toolBarRender={() =>
        canResetOrder ? (
          [<Button key="reset" onClick={resetOrder}>恢复默认顺序</Button>]
        ) : []
      }
      columns={[
        {
          title: '显示名称',
          dataIndex: 'name',
          width: 180,
          ellipsis: true,
        },
        {
          title: '路由路径',
          dataIndex: 'path',
          width: 240,
          ellipsis: true,
        },
        {
          title: '访问权限',
          dataIndex: 'access',
          width: 220,
          ellipsis: true,
        },
        {
          title: '图标',
          dataIndex: 'icon',
          width: 160,
          render: (value) => value || '-',
        },
        {
          title: '顺序',
          dataIndex: 'sortNo',
          width: 88,
        },
        {
          title: '管理方式',
          dataIndex: 'manageMode',
          width: 140,
          render: (value) => <Tag color="blue">{value}</Tag>,
        },
        {
          title: '操作',
          valueType: 'option',
          width: 150,
          fixed: 'right',
          render: (_, record, index) => (
            <Space>
              <Button type="link" disabled={index === 0} onClick={() => moveRoute(record, -1)}>
                上移
              </Button>
              <Button type="link" disabled={index === records.length - 1} onClick={() => moveRoute(record, 1)}>
                下移
              </Button>
            </Space>
          ),
        },
      ]}
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
      } catch {
        // keep silent: global request interceptor already handles feedback
      }
    })();
  }, [loadMenus]);

  useEffect(() => {
    menuCrud.reloadTable();
  }, [expandedRowKeys, menuCrud.reloadTable, menuTree]);

  const flatMenus = useMemo(() => flattenMenus(menuTree), [menuTree]);
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
                  const visibleMenus = buildMenuTableData(menuTree, expandedRowKeys, params);
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
