import { HolderOutlined, MinusOutlined, PlusOutlined } from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import { Button, Form, Input, InputNumber, Select, Space, Spin, Tabs, Tag, Typography, message, theme } from 'antd';
import type { FormProps } from 'antd';
import { useCallback, useEffect, useMemo, useState, type DragEvent } from 'react';
import { formatMessage } from '@umijs/max';
import { storage } from '@/cache/storage';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementDrawerAction } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementTable } from '@/features/management/ManagementTable';
import { confirmAction } from '@/utils/confirm';
import { API_OPTS } from '@/utils/errorMessage';
import { request } from '@/services/common/request';
import type { MenuMutationPayload } from '@/services/iam/types';
import { MenuIconPicker, MenuIconPreview } from '@/pages/settings/menus/components/MenuIconPicker';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { backendRouteMeta } from '@/routes/meta';
import { resolveBuiltinMessage } from '@/i18n/messages';
import type { ProDescriptionsItemProps, ProColumns } from '@ant-design/pro-components';
import type { MenuNode, MenuRecord } from '@/types/api';
import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import { isMainMenuHiddenSettingPath } from '@/navigation/settingsNavigationRuntime';

const MENU_TYPE_LABELS = {
  CATALOG: '目录',
  MENU: '菜单',
  TAB: '页签',
  BUTTON: '按钮',
} as const;

const MENU_TYPE_OPTIONS = [
  { label: '目录', value: 'CATALOG' },
  { label: '菜单', value: 'MENU' },
  { label: '页签', value: 'TAB' },
  { label: '按钮', value: 'BUTTON' },
];

const MENU_STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
];

const isBuiltinMenu = (record: Pick<MenuRecord, 'id'>) => record.id < 0;

const buildParentMenuOptions = (menus: Array<MenuRecord & { level: number }>) =>
  menus.map((menu) => ({
    label: `${'　'.repeat(menu.level || 0)}${menu.menuName}`,
    value: menu.id,
  }));

type SettingsRouteRecord = {
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
};

type SettingsRouteEditorValues = {
  icon?: string;
  sortNo?: number;
};

const DEFAULT_SETTING_ROUTE_ORDER = [
  '/settings/tenants',
  '/settings/menus',
  '/settings/dicts',
  '/settings/profile-fields',
  '/settings/personalization',
  '/settings/security',
  '/settings/verification',
  '/settings/notifications',
  '/settings/ai-employees',
  '/settings/plugins',
  '/settings/files/all',
  '/settings/localization',
  '/settings/monitoring',
  '/settings/api-docs',
  '/settings/audit',
];

const SETTING_ROUTE_ORDER_KEY = 'settings_route_order';
const SETTING_ROUTE_ICON_KEY = 'settings_route_icons';

const getStoredSettingRouteOrder = () => {
  const storedOrder = storage.get<string[]>(SETTING_ROUTE_ORDER_KEY) || [];
  const storedPathSet = new Set(storedOrder);
  return [
    ...storedOrder.filter((path) => DEFAULT_SETTING_ROUTE_ORDER.includes(path)),
    ...DEFAULT_SETTING_ROUTE_ORDER.filter((path) => !storedPathSet.has(path)),
  ];
};

const persistSettingRouteOrder = (order: string[]) => {
  const validPathSet = new Set(DEFAULT_SETTING_ROUTE_ORDER);
  storage.set(
    SETTING_ROUTE_ORDER_KEY,
    order.filter((path, index, array) => validPathSet.has(path) && array.indexOf(path) === index),
  );
};

const resetSettingRouteOrder = () => {
  storage.remove(SETTING_ROUTE_ORDER_KEY);
};

const getStoredSettingRouteIcons = () => {
  const validPathSet = new Set(DEFAULT_SETTING_ROUTE_ORDER);
  const storedIcons = storage.get<Record<string, string>>(SETTING_ROUTE_ICON_KEY) || {};
  return Object.fromEntries(
    Object.entries(storedIcons)
      .map(([path, icon]) => [path, icon.trim()])
      .filter(([path, icon]) => validPathSet.has(path) && Boolean(icon)),
  );
};

const persistSettingRouteIcons = (icons: Record<string, string>) => {
  const validPathSet = new Set(DEFAULT_SETTING_ROUTE_ORDER);
  storage.set(
    SETTING_ROUTE_ICON_KEY,
    Object.fromEntries(
      Object.entries(icons)
        .map(([path, icon]) => [path, icon.trim()])
        .filter(([path, icon]) => validPathSet.has(path) && Boolean(icon)),
    ),
  );
};

const formatRouteName = (name: string) =>
  resolveBuiltinMessage(
    name,
    formatMessage({
      id: name,
      defaultMessage: name,
    }),
  );

const buildSettingsRouteRecords = (routeOrder: string[], routeIcons: Record<string, string>): SettingsRouteRecord[] =>
  routeOrder.map((path, index) => {
    const meta = backendRouteMeta.find((item) => item.path === path);
    const customIcon = routeIcons[path];
    const defaultIcon = meta?.icon;
    return {
      id: path,
      menuCode: `settings:${path.replace(/^\/settings\/?/, '').replace(/\//g, ':') || 'root'}`,
      menuName: meta ? formatRouteName(meta.name) : path,
      menuType: 'TAB',
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

type MenuTreeRecord = MenuRecord & { level?: number };

type MenuDragState = {
  draggedId: number;
  targetId: number;
  position: 'before' | 'inside' | 'after';
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
  position: 'before' | 'inside' | 'after',
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

const moveMenuNode = (menus: MenuRecord[], draggedId: number, targetId: number, position: 'before' | 'inside' | 'after') => {
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
  menus: Array<MenuRecord & { level?: number }>,
  expandedRowKeys: number[],
  includeAllChildren = false,
  level = 0,
  result: Array<MenuRecord & { level?: number }> = [],
) => {
  menus.forEach((menu) => {
    const { children: _children, ...rest } = menu;
    const currentMenu = { ...rest, level };
    result.push(currentMenu);

    if (menu.children?.length && (includeAllChildren || expandedRowKeys.includes(menu.id))) {
      flattenVisibleMenus(menu.children as Array<MenuRecord & { level?: number }>, expandedRowKeys, includeAllChildren, level + 1, result);
    }
  });

  return result;
};

const filterMenus = (menus: MenuRecord[], keyword: string, menuCode: string, permissionKey: string, level = 0): Array<MenuRecord & { level?: number }> => {
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
    .filter(Boolean) as Array<MenuRecord & { level?: number }>;
};

const buildMainRouteMenuTree = (menus: MenuRecord[]): MenuRecord[] =>
  menus
    .filter((menu) => !isMainMenuHiddenSettingPath(menu.path ?? undefined))
    .map((menu) => ({
      ...menu,
      children: menu.children?.length ? buildMainRouteMenuTree(menu.children) : undefined,
    }));

const getDropPosition = (event: DragEvent<HTMLTableRowElement>, record: MenuRecord) => {
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

const buildMenuColumns = ({
  isMobile,
  canReorderMenus,
  expandedRowKeys,
  expandableMenuIds,
  dragHandleColor,
  buildRowActions,
  isReadonlyMenu,
  onToggleExpand,
  onOpenDetail,
  onOpenEdit,
  onToggleStatus,
  onDelete,
}: {
  isMobile: boolean;
  canReorderMenus: boolean;
  expandedRowKeys: number[];
  expandableMenuIds: Set<number>;
  dragHandleColor: string;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  isReadonlyMenu: (record: MenuRecord) => boolean;
  onToggleExpand: (menuId: number) => void;
  onOpenDetail: (record: MenuRecord) => void;
  onOpenEdit: (record: MenuRecord) => void;
  onToggleStatus: (record: MenuRecord) => void;
  onDelete: (record: MenuRecord) => void;
}): ProColumns<MenuTreeRecord>[] => [
  {
    title: '拖拽',
    dataIndex: 'dragHandle',
    width: 96,
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => {
      const hasChildren = expandableMenuIds.has(record.id);
      const expanded = expandedRowKeys.includes(record.id);
      const readonly = isReadonlyMenu(record);

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
              onToggleExpand(record.id);
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
          <HolderOutlined style={{ color: dragHandleColor, cursor: canReorderMenus && !readonly ? 'grab' : 'not-allowed' }} />
        </Space>
      );
    },
  },
  {
    title: '菜单编码',
    dataIndex: 'menuCode',
    search: false,
    width: 180,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) =>
      record.menuCode ? <Typography.Text ellipsis={{ tooltip: record.menuCode }}>{record.menuCode}</Typography.Text> : '-',
  },
  {
    title: '菜单名称',
    dataIndex: 'menuName',
    width: 260,
    search: true,
    ellipsis: true,
    render: (_, record) => (
      <Typography.Text className="saas-menu-tree-cell" ellipsis={{ tooltip: record.menuName }} style={{ paddingInlineStart: `${(record.level || 0) * 24}px` }}>
        {record.menuName}
      </Typography.Text>
    ),
  },
  {
    title: '菜单类型',
    dataIndex: 'menuType',
    width: 120,
    valueEnum: {
      CATALOG: { text: '目录' },
      MENU: { text: '菜单' },
      TAB: { text: '页签' },
      BUTTON: { text: '按钮' },
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
    render: (_, record) => <MenuIconPreview icon={record.icon} />,
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
    render: (_, record) => record.sortNo ?? 0,
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 120,
    search: false,
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
  },
  {
    title: '操作',
    valueType: 'option',
    fixed: 'right',
    width: 180,
    render: (_, record) => {
      const readonly = isReadonlyMenu(record);
      return (
        <TableActionBar
          isMobile={isMobile}
          items={buildRowActions([
            {
              key: 'detail',
              label: '详情',
              permission: 'system:menu:view',
              onClick: () => onOpenDetail(record),
            },
            {
              key: 'edit',
              label: '编辑',
              permission: 'system:menu:update',
              disabled: readonly,
              onClick: () => onOpenEdit(record),
            },
            {
              key: 'status',
              label: record.status === 'ENABLED' ? '停用' : '启用',
              permission: 'system:menu:status',
              danger: record.status === 'ENABLED',
              disabled: readonly,
              onClick: () => onToggleStatus(record),
            },
            {
              key: 'delete',
              label: '删除',
              permission: 'system:menu:delete',
              danger: true,
              disabled: readonly || Boolean(record.children?.length),
              onClick: () => onDelete(record),
            },
          ])}
        />
      );
    },
  },
];

type UseMenuTreeManagementParams = {
  isMobile: boolean;
  tokenColorTextTertiary: string;
  tokenColorPrimary: string;
  tokenColorPrimaryBg: string;
  reloadTable: () => void;
  onOpenCreate: () => void;
  onOpenDetail: (record: MenuRecord) => void;
  onOpenEdit: (record: MenuRecord) => void;
};

const useMenuTreeManagement = ({
  isMobile,
  tokenColorTextTertiary,
  tokenColorPrimary,
  tokenColorPrimaryBg,
  reloadTable,
  onOpenCreate,
  onOpenDetail,
  onOpenEdit,
}: UseMenuTreeManagementParams) => {
  const { actionPermission, buildToolbarButtons } = usePagePermissionActions();
  const { setInitialState } = useInitialStateModel();
  const [menuTree, setMenuTree] = useState<MenuRecord[]>([]);
  const [activeTab, setActiveTab] = useState('main');
  const [expandedRowKeys, setExpandedRowKeys] = useState<number[]>([]);
  const mainRouteMenuTree = useMemo(() => buildMainRouteMenuTree(menuTree), [menuTree]);
  const flatMenus = useMemo(() => flattenMenus(mainRouteMenuTree), [mainRouteMenuTree]);
  const editableFlatMenus = useMemo(() => flatMenus.filter((menu) => !isBuiltinMenu(menu)), [flatMenus]);
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
    const result = await request<MenuRecord[]>('/v1/system/menus', {
      method: 'GET',
      ...API_OPTS.NO_REDIRECT,
    });
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
  }, [setExpandedRowKeys, setInitialState, setMenuTree]);

  const updateMenuStatus = useCallback(
    async (record: MenuRecord, status: 'ENABLED' | 'DISABLED') => {
      await request<boolean>(`/v1/system/menus/${record.id}/status`, {
        method: 'PATCH',
        data: { status },
        ...API_OPTS.NO_REDIRECT,
      });
      message.success('状态已更新');
      await loadMenus();
      reloadTable();
    },
    [loadMenus, reloadTable],
  );

  const deleteMenu = useCallback(
    (record: MenuRecord) => {
      confirmAction({
        title: '删除菜单',
        content: `确认删除菜单「${record.menuName}」吗？删除后权限树和运行菜单将不再出现该项。`,
        okText: '确认删除',
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/system/menus/${record.id}`, {
            method: 'DELETE',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success('菜单已删除');
          await loadMenus();
          reloadTable();
        },
      });
    },
    [loadMenus, reloadTable],
  );

  const handleStatusToggle = useCallback(
    (record: MenuRecord) => {
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
    },
    [updateMenuStatus],
  );

  const menuTableRequest = useCallback(
    async (params: Record<string, unknown>) => {
      const keyword = String(params.menuName || params.keyword || '');
      const menuCode = String(params.menuCode || '');
      const permissionKey = String(params.permissionKey || '');
      const filtered = filterMenus(mainRouteMenuTree, keyword, menuCode, permissionKey);
      const hasSearch = Boolean(keyword.trim() || menuCode.trim() || permissionKey.trim());
      const visibleMenus = hasSearch ? flattenMenus(filtered as MenuRecord[]) : flattenVisibleMenus(filtered, expandedRowKeys);
      return {
        data: visibleMenus,
        success: true,
        total: visibleMenus.length,
      };
    },
    [expandedRowKeys, mainRouteMenuTree],
  );

  useEffect(() => {
    reloadTable();
  }, [expandedRowKeys, menuTree, reloadTable]);

  const [dragState, setDragState] = useState<MenuDragState | null>(null);
  const [reordering, setReordering] = useState(false);
  const canReorderMenus = actionPermission.can('system:menu:update') && !reordering;

  const persistMenuOrder = useCallback(
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
        await request<boolean>('/v1/system/menus/reorder', {
          method: 'PUT',
          data: {
            items: flattenMenuOrder(normalizedTree).filter((item) => item.id > 0),
          },
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('菜单顺序已更新');
        reloadTable();
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
      }
    },
    [menuTree, reloadTable, setInitialState, setMenuTree],
  );

  const handleRowDragStart = useCallback(
    (record: MenuRecord) => (event: DragEvent<HTMLTableRowElement>) => {
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
    },
    [canReorderMenus, setDragState],
  );

  const handleRowDragOver = useCallback(
    (record: MenuRecord) => (event: DragEvent<HTMLTableRowElement>) => {
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
    },
    [canReorderMenus, dragState, setDragState],
  );

  const handleRowDrop = useCallback(
    (record: MenuRecord) => async (event: DragEvent<HTMLTableRowElement>) => {
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
        await persistMenuOrder(nextTree);
      } catch {
        // Revert is handled inside saveMenuOrder; keep the UI responsive.
      }
    },
    [canReorderMenus, dragState, menuTree, persistMenuOrder, setDragState],
  );

  const handleRowDragEnd = useCallback(() => {
    setDragState(null);
  }, [setDragState]);

  const toolbarActions = useMemo(
    () =>
      buildToolbarButtons([
        {
          key: 'create',
          permission: 'system:menu:create',
          type: 'primary',
          label: '新增菜单',
          onClick: onOpenCreate,
        },
        {
          key: 'refresh',
          label: '刷新',
          onClick: async () => {
            await loadMenus();
            reloadTable();
          },
        },
      ]),
    [buildToolbarButtons, loadMenus, onOpenCreate, reloadTable],
  );
  const columns = useMemo(
    () =>
      buildMenuColumns({
        isMobile,
        canReorderMenus,
        expandedRowKeys,
        expandableMenuIds,
        dragHandleColor: tokenColorTextTertiary,
        buildRowActions: actionPermission.buildTableActions,
        isReadonlyMenu: isBuiltinMenu,
        onToggleExpand: (menuId) =>
          setExpandedRowKeys((currentKeys) =>
            currentKeys.includes(menuId) ? currentKeys.filter((key) => key !== menuId) : [...currentKeys, menuId],
          ),
        onOpenDetail: (record) => void onOpenDetail(record),
        onOpenEdit: (record) => void onOpenEdit(record),
        onToggleStatus: (record) => void handleStatusToggle(record),
        onDelete: (record) => void deleteMenu(record),
      }),
    [
      actionPermission.buildTableActions,
      canReorderMenus,
      expandedRowKeys,
      expandableMenuIds,
      isMobile,
      onOpenDetail,
      onOpenEdit,
      deleteMenu,
      handleStatusToggle,
      setExpandedRowKeys,
      tokenColorTextTertiary,
    ],
  );
  const getRowProps = useMemo(
    () => (record: MenuRecord) => ({
      draggable: canReorderMenus && !isBuiltinMenu(record),
      onDragStart: handleRowDragStart(record),
      onDragOver: handleRowDragOver(record),
      onDrop: handleRowDrop(record),
      onDragEnd: handleRowDragEnd,
      style: {
        cursor: canReorderMenus && !isBuiltinMenu(record) ? 'grab' : undefined,
        userSelect: 'none' as const,
        opacity: dragState?.draggedId === record.id ? 0.35 : 1,
        backgroundColor:
          dragState?.targetId === record.id && dragState.position === 'inside'
            ? tokenColorPrimaryBg
            : undefined,
        boxShadow:
          dragState?.targetId === record.id && dragState.position === 'before'
            ? `inset 0 2px 0 ${tokenColorPrimary}`
            : dragState?.targetId === record.id && dragState.position === 'after'
              ? `inset 0 -2px 0 ${tokenColorPrimary}`
              : undefined,
      },
    }),
    [canReorderMenus, dragState, handleRowDragEnd, handleRowDragOver, handleRowDragStart, handleRowDrop, tokenColorPrimary, tokenColorPrimaryBg],
  );

  return {
    catalogPack: {
      activeTab,
      setActiveTab,
      menuTree,
      mainRouteMenuTree,
      flatMenus,
      editableFlatMenus,
      expandableMenuIds,
      expandedRowKeys,
      setExpandedRowKeys,
      loadMenus,
      updateMenuStatus,
      deleteMenu,
      menuTableRequest,
    },
    reorderPack: {
      canReorderMenus,
      reordering,
      dragState,
      handleRowDragStart,
      handleRowDragOver,
      handleRowDrop,
      handleRowDragEnd,
    },
    tablePack: {
      toolbarActions,
      columns,
      getRowProps,
    },
  };
};

const SettingsRoutesTab = () => {
  const responsive = useResponsive();
  const [routeEditorForm] = Form.useForm<SettingsRouteEditorValues>();
  const [routeOrder, setRouteOrder] = useState(() => getStoredSettingRouteOrder());
  const [routeIcons, setRouteIcons] = useState(() => getStoredSettingRouteIcons());
  const [editingRoute, setEditingRoute] = useState<SettingsRouteRecord | null>(null);
  const { setInitialState } = useInitialStateModel();

  const records = useMemo(() => buildSettingsRouteRecords(routeOrder, routeIcons), [routeIcons, routeOrder]);
  const canResetOrder = routeOrder.join('|') !== DEFAULT_SETTING_ROUTE_ORDER.join('|');
  const routeEditorFormProps = useStandardFormProps({
    form: routeEditorForm,
  });

  const closeRouteEditor = useCallback(() => {
    setEditingRoute(null);
    routeEditorForm.resetFields();
  }, [routeEditorForm]);

  const openEditRoute = useCallback(
    (record: SettingsRouteRecord) => {
      setEditingRoute(record);
      routeEditorForm.setFieldsValue({
        icon: record.icon,
        sortNo: record.sortNo,
      });
    },
    [routeEditorForm],
  );

  const refreshSettingsNavigation = useCallback(() => {
    setInitialState((prev) =>
      prev
        ? {
            ...prev,
            menuVersion: (prev.menuVersion ?? 0) + 1,
          }
        : prev,
    );
  }, [setInitialState]);

  const updateRouteOrder = useCallback(
    (nextOrder: string[]) => {
      persistSettingRouteOrder(nextOrder);
      setRouteOrder(nextOrder);
      refreshSettingsNavigation();
      message.success('设置页路由顺序已更新');
    },
    [refreshSettingsNavigation],
  );

  const moveRoute = useCallback(
    (record: SettingsRouteRecord, direction: -1 | 1) => {
      const currentIndex = routeOrder.indexOf(record.path);
      const nextIndex = currentIndex + direction;
      if (currentIndex < 0 || nextIndex < 0 || nextIndex >= routeOrder.length) {
        return;
      }
      updateRouteOrder(moveArrayItem(routeOrder, currentIndex, nextIndex));
    },
    [routeOrder, updateRouteOrder],
  );

  const resetOrder = useCallback(() => {
    resetSettingRouteOrder();
    setRouteOrder(DEFAULT_SETTING_ROUTE_ORDER);
    refreshSettingsNavigation();
    message.success('设置页路由顺序已恢复默认');
  }, [refreshSettingsNavigation]);

  const resetRouteIcon = useCallback(() => {
    if (!editingRoute) {
      return;
    }
    const record = editingRoute;
    const { [record.path]: _removed, ...nextIcons } = routeIcons;
    persistSettingRouteIcons(nextIcons);
    const normalizedIcons = getStoredSettingRouteIcons();
    setRouteIcons(normalizedIcons);
    routeEditorForm.setFieldValue('icon', record.defaultIcon);
    setEditingRoute((current) => (current ? { ...current, icon: record.defaultIcon, customIcon: undefined } : current));
    refreshSettingsNavigation();
    message.success('设置页路由图标已恢复默认');
  }, [editingRoute, refreshSettingsNavigation, routeEditorForm, routeIcons]);

  const saveRouteEditor = useCallback(async () => {
    if (!editingRoute) {
      return;
    }
    const values = await routeEditorForm.validateFields();
    const nextIcon = (values.icon || '').trim();
    const nextIcons = { ...routeIcons };
    if (nextIcon && nextIcon !== editingRoute.defaultIcon) {
      nextIcons[editingRoute.path] = nextIcon;
    } else {
      delete nextIcons[editingRoute.path];
    }

    const currentIndex = routeOrder.indexOf(editingRoute.path);
    const nextSortNo = Math.min(Math.max(Math.trunc(Number(values.sortNo) || editingRoute.sortNo), 1), routeOrder.length);
    const nextIndex = nextSortNo - 1;
    const nextOrder = currentIndex >= 0 && currentIndex !== nextIndex ? moveArrayItem(routeOrder, currentIndex, nextIndex) : routeOrder;

    persistSettingRouteIcons(nextIcons);
    persistSettingRouteOrder(nextOrder);
    setRouteIcons(getStoredSettingRouteIcons());
    setRouteOrder(nextOrder);
    refreshSettingsNavigation();
    closeRouteEditor();
    message.success('设置页路由已更新');
  }, [closeRouteEditor, editingRoute, refreshSettingsNavigation, routeIcons, routeOrder, routeEditorForm]);

  const footerActions: ManagementDrawerAction[] = [
    { key: 'cancel', label: '取消', onClick: closeRouteEditor },
    { key: 'reset-icon', label: '默认图标', disabled: !editingRoute?.customIcon, onClick: resetRouteIcon },
    { key: 'save', label: '保存', type: 'primary', onClick: () => void saveRouteEditor() },
  ];
  const { token } = theme.useToken();
  const columns: ProColumns<SettingsRouteRecord>[] = [
    {
      title: '拖拽',
      dataIndex: 'dragHandle',
      width: 96,
      search: false,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      render: () => <HolderOutlined style={{ color: token.colorTextTertiary, cursor: 'default' }} />,
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
        TAB: { text: '页签' },
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
      render: (_, record) => <MenuIconPreview icon={record.icon} />,
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
      width: 144,
      fixed: 'right',
      render: (_, record, index) => (
        <Space size={4}>
          <Button type="link" onClick={() => openEditRoute(record)}>
            编辑
          </Button>
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
    <>
      <ManagementTable<SettingsRouteRecord>
        rowKey="id"
        dataSource={records}
        pagination={false}
        scroll={{ x: 'max-content' }}
        tableLayout="fixed"
        isMobile={responsive.isMobile}
        search={false}
        toolBarRender={() => (canResetOrder ? [<Button key="reset" onClick={resetOrder}>恢复默认顺序</Button>] : [])}
        columns={columns}
      />
      <ManagementDrawer
        title={editingRoute ? `编辑设置页路由 · ${editingRoute.menuName}` : '编辑设置页路由'}
        open={Boolean(editingRoute)}
        onClose={closeRouteEditor}
        footerActions={footerActions}
      >
        <Form {...routeEditorFormProps}>
          <Form.Item label="菜单编码">
            <Input disabled value={editingRoute?.menuCode} />
          </Form.Item>
          <Form.Item label="菜单名称">
            <Input disabled value={editingRoute?.menuName} />
          </Form.Item>
          <Form.Item label="菜单类型">
            <Select disabled value="TAB" options={[{ label: '页签', value: 'TAB' }]} />
          </Form.Item>
          <Form.Item label="路由">
            <Input disabled value={editingRoute?.path} />
          </Form.Item>
          <Form.Item label="组件">
            <Input disabled value={editingRoute?.component} />
          </Form.Item>
          <Form.Item name="icon" label="菜单项图标" extra="图标来自 Ant Design 图标库；清空后使用默认图标。">
            <MenuIconPicker />
          </Form.Item>
          <Form.Item name="sortNo" label="排序" rules={[{ type: 'number', min: 1, max: records.length, message: `请输入 1-${records.length} 之间的排序` }]}>
            <InputNumber min={1} max={records.length} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="权限标识">
            <Input disabled value={editingRoute?.permissionKey || '-'} />
          </Form.Item>
          <Form.Item label="状态">
            <Select disabled value="ENABLED" options={[{ label: '启用', value: 'ENABLED' }]} />
          </Form.Item>
        </Form>
      </ManagementDrawer>
    </>
  );
};

const menuDetailColumns: ProDescriptionsItemProps<MenuRecord>[] = [
  { title: '菜单名称', dataIndex: 'menuName' },
  { title: '菜单类型', dataIndex: 'menuType', renderText: (value) => MENU_TYPE_LABELS[value as keyof typeof MENU_TYPE_LABELS] || value },
  { title: '路由', dataIndex: 'path', renderText: (value) => value || '-' },
  { title: '图标', dataIndex: 'icon', renderText: (value) => value || '-' },
  { title: '组件', dataIndex: 'component', renderText: (value) => value || '-' },
  { title: '权限标识', dataIndex: 'permissionKey', renderText: (value) => value || '-' },
  { title: '状态', dataIndex: 'status' },
];

const MenuEditorForm = ({ formProps, parentOptions }: { formProps: FormProps; parentOptions: Array<{ label: string; value: number }> }) => (
  <Form {...formProps}>
    <Form.Item name="parentId" label="上级菜单">
      <Select allowClear options={parentOptions} />
    </Form.Item>
    <Form.Item name="menuCode" label="菜单编码" rules={[{ required: true, message: '请输入菜单编码' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="menuName" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="menuType" label="菜单类型" rules={[{ required: true, message: '请选择菜单类型' }]}>
      <Select options={MENU_TYPE_OPTIONS} />
    </Form.Item>
    <Form.Item name="path" label="路由">
      <Input />
    </Form.Item>
    <Form.Item name="component" label="组件">
      <Input />
    </Form.Item>
    <Form.Item name="icon" label="菜单项图标" extra="图标来自 Ant Design 图标库，保存时记录原始图标名。">
      <MenuIconPicker />
    </Form.Item>
    <Form.Item name="sortNo" label="排序">
      <InputNumber style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item name="permissionKey" label="权限标识">
      <Input />
    </Form.Item>
    <Form.Item name="status" label="状态">
      <Select options={MENU_STATUS_OPTIONS} />
    </Form.Item>
  </Form>
);

const MenuManagementPage = () => {
  const { token } = theme.useToken();
  const { actionPermission, responsive, searchConfig } = usePagePermissionActions();
  const { actionRef, drawer, detail, reloadTable } = useCrudPageState<MenuRecord>();
  const [editorForm] = Form.useForm<MenuMutationPayload>();
  const editorFormProps = useStandardFormProps({
    form: editorForm,
    initialValues: { menuCode: '', menuName: '', menuType: 'MENU', status: 'ENABLED', sortNo: 0 },
  });
  const detailProps = useDetailProDescriptionsProps<MenuRecord>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: detail.currentRecord || undefined,
  });
  const [saving, setSaving] = useState(false);
  const openCreate = useCallback(() => {
    drawer.openCreate();
    editorForm.resetFields();
    editorForm.setFieldsValue({ menuType: 'MENU', status: 'ENABLED', sortNo: 0 });
  }, [drawer, editorForm]);
  const openEdit = useCallback(
    async (record: MenuRecord) => {
      if (isBuiltinMenu(record)) {
        message.warning('内置菜单不支持编辑');
        return;
      }
      drawer.openEdit(record, record.id);
      const detailResult = await request<MenuRecord>(`/v1/system/menus/${record.id}`, {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      });
      editorForm.setFieldsValue({
        parentId: detailResult.parentId ?? undefined,
        menuCode: detailResult.menuCode,
        menuName: detailResult.menuName,
        menuType: detailResult.menuType,
        path: detailResult.path ?? undefined,
        component: detailResult.component ?? undefined,
        icon: detailResult.icon ?? undefined,
        sortNo: detailResult.sortNo ?? undefined,
        permissionKey: detailResult.permissionKey ?? undefined,
        status: detailResult.status,
      });
    },
    [drawer, editorForm],
  );
  const openDetail = useCallback(
    async (record: MenuRecord) => {
      if (isBuiltinMenu(record)) {
        detail.openDetail(record);
        detail.setLoading(false);
        detail.setCurrentRecord(record);
        return;
      }
      detail.openDetail(record);
      detail.setLoading(true);
      try {
        const detailResult = await request<MenuRecord>(`/v1/system/menus/${record.id}`, {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        });
        detail.setCurrentRecord(detailResult);
      } finally {
        detail.setLoading(false);
      }
    },
    [detail],
  );
  const { catalogPack, tablePack } = useMenuTreeManagement({
    isMobile: responsive.isMobile,
    tokenColorTextTertiary: token.colorTextTertiary,
    tokenColorPrimary: token.colorPrimary,
    tokenColorPrimaryBg: token.colorPrimaryBg,
    reloadTable,
    onOpenCreate: openCreate,
    onOpenDetail: openDetail,
    onOpenEdit: openEdit,
  });
  const columns = tablePack.columns;
  const toolbarActions = tablePack.toolbarActions;
  const getRowProps = tablePack.getRowProps;
  const menuTableRequest = catalogPack.menuTableRequest;
  const canSaveMenu = actionPermission.can(drawer.editingId ? 'system:menu:update' : 'system:menu:create');
  const saveMenu = useCallback(async () => {
    setSaving(true);
    const editingId = drawer.editingId;
    try {
      const values = await editorForm.validateFields();
      if (editingId) {
        await request<MenuRecord>(`/v1/system/menus/${editingId}`, {
          method: 'PUT',
          data: values,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('菜单已更新');
      } else {
        await request<MenuRecord>('/v1/system/menus', {
          method: 'POST',
          data: values,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('菜单已创建');
      }
      drawer.close();
      await catalogPack.loadMenus();
      reloadTable();
    } finally {
      setSaving(false);
    }
  }, [catalogPack, drawer, editorForm, reloadTable]);
  const menuEditorProps = {
    open: drawer.open,
    title: drawer.editingId ? '编辑菜单' : '新增菜单',
    onClose: drawer.close,
    footerActions: [
      { key: 'cancel', label: '取消', onClick: drawer.close },
      { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canSaveMenu, onClick: () => void saveMenu() },
    ] as ManagementDrawerAction[],
    formProps: editorFormProps,
    parentOptions: buildParentMenuOptions(catalogPack.editableFlatMenus),
  };
  const menuDetailDrawerProps = {
    open: detail.open,
    title: detail.currentRecord ? `菜单详情 · ${detail.currentRecord.menuName}` : '菜单详情',
    onClose: detail.close,
    loading: detail.loading,
    detailProps,
    currentRecord: detail.currentRecord,
  };
  const activeTab = catalogPack.activeTab;
  const setActiveTab = catalogPack.setActiveTab;

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
              <ManagementTable
                actionRef={actionRef}
                rowKey="id"
                columns={columns}
                isMobile={responsive.isMobile}
                search={searchConfig}
                pagination={false}
                scroll={{ x: 'max-content' }}
                tableLayout="fixed"
                onRow={getRowProps}
                request={menuTableRequest}
                toolBarRender={() => toolbarActions}
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

      <ManagementDrawer title={menuEditorProps.title} open={menuEditorProps.open} onClose={menuEditorProps.onClose} footerActions={menuEditorProps.footerActions}>
        <MenuEditorForm formProps={menuEditorProps.formProps} parentOptions={menuEditorProps.parentOptions} />
      </ManagementDrawer>

      <ManagementDrawer title={menuDetailDrawerProps.title} open={menuDetailDrawerProps.open} onClose={menuDetailDrawerProps.onClose}>
        {menuDetailDrawerProps.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : menuDetailDrawerProps.currentRecord ? (
          <ProDescriptions columns={menuDetailColumns} {...menuDetailDrawerProps.detailProps} />
        ) : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default MenuManagementPage;
