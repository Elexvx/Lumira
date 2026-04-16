import { PageContainer, ProDescriptions, ProTable } from '@ant-design/pro-components';
import { Button, Drawer, Form, Space, Spin, message } from 'antd';
import { useCallback, useEffect, useMemo, useState, type DragEvent } from 'react';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { buildTableScroll } from '@/features/table/proTable';
import { useResponsive } from '@/hooks/useResponsive';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { buildMenuColumns, menuDetailColumns } from '@/pages/system/menus/columns';
import { MenuEditorForm, buildParentMenuOptions } from '@/pages/system/menus/components/MenuEditorForm';
import {
  flattenMenuOrder,
  flattenMenus,
  getDropPosition,
  moveMenuNode,
  normalizeMenuTreeOrder,
  sortMenuTree,
  toRuntimeMenuNodes,
  type MenuDropPosition,
} from '@/pages/system/menus/treeUtils';
import { buildMenuTableData } from '@/pages/system/menus/tableData';
import { iamService } from '@/services/iam';
import type { MenuRecord } from '@/types/api';
import { confirmAction } from '@/utils/confirm';

interface MenuDragState {
  draggedId: number;
  targetId: number;
  position: MenuDropPosition;
}

const MenuManagementPage = () => {
  const menuCrud = useCrudPageState<MenuRecord>();
  const [editorForm] = Form.useForm();
  const actionPermission = useActionPermission();
  const responsive = useResponsive();
  const { setInitialState } = useInitialStateModel();
  const [menuTree, setMenuTree] = useState<MenuRecord[]>([]);
  const [saving, setSaving] = useState(false);
  const [dragState, setDragState] = useState<MenuDragState | null>(null);
  const [reordering, setReordering] = useState(false);
  const [expandedRowKeys, setExpandedRowKeys] = useState<number[]>([]);
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
        menuCrud.reloadTable();
      } catch {
        // keep silent: global request interceptor already handles feedback
      }
    })();
  }, [loadMenus]);

  useEffect(() => {
    menuCrud.reloadTable();
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
    menuCrud.drawer.openEdit(record, record.id);
    const detail = await iamService.menuDetail(record.id, { autoRedirectOnUnauthorized: false });
    editorForm.setFieldsValue({
      ...detail,
      parentId: detail.parentId ?? undefined,
    });
  };

  const openDetail = async (record: MenuRecord) => {
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
    if (!canReorderMenus) {
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
    if (!dragState || dragState.draggedId === record.id || !canReorderMenus) {
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
    if (!dragState || dragState.draggedId === record.id || !canReorderMenus) {
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

  const columns = useMemo(
    () =>
      buildMenuColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        canReorderMenus,
        expandedRowKeys,
        expandableMenuIds,
        buildRowActions: actionPermission.buildTableActions,
        onToggleExpand: (menuId) =>
          setExpandedRowKeys((currentKeys) =>
            currentKeys.includes(menuId) ? currentKeys.filter((key) => key !== menuId) : [...currentKeys, menuId],
          ),
        onOpenDetail: (record) => void openDetail(record),
        onOpenEdit: (record) => void openEdit(record),
        onToggleStatus: (record) => void handleStatusToggle(record),
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
    <PageContainer title="菜单管理" className="saas-management-page">
      <div className="saas-table-wrap saas-wide-table">
        <ProTable<MenuRecord & { level?: number }>
          actionRef={menuCrud.actionRef}
          rowKey="id"
          columns={columns}
          search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
          options={false}
          pagination={false}
          scroll={buildTableScroll(columns, responsive.isMobile, { wide: true })}
          onRow={(record) => ({
            draggable: canReorderMenus,
            onDragStart: handleRowDragStart(record),
            onDragOver: handleRowDragOver(record),
            onDrop: handleRowDrop(record),
            onDragEnd: handleRowDragEnd,
            style: {
              cursor: canReorderMenus ? 'grab' : undefined,
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
            actionPermission.buildToolbarActions([
              {
                permission: 'system:menu:create',
                value: (
                  <Button key="create" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={openCreate}>
                    新增菜单
                  </Button>
                ),
              },
              {
                value: (
                  <Button
                    key="refresh"
                    size={responsive.isMobile ? 'small' : 'middle'}
                    onClick={async () => {
                      await loadMenus();
                      menuCrud.reloadTable();
                    }}
                  >
                    刷新
                  </Button>
                ),
              },
            ])
          }
        />
      </div>

      <Drawer
        title={menuCrud.drawer.editingId ? '编辑菜单' : '新增菜单'}
        open={menuCrud.drawer.open}
        onClose={menuCrud.drawer.close}
        width={720}
        destroyOnClose
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={menuCrud.drawer.close}>取消</Button>
              <Button type="primary" loading={saving} onClick={() => void saveMenu()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <MenuEditorForm formProps={editorFormProps} parentOptions={buildParentMenuOptions(flatMenus)} />
      </Drawer>

      <Drawer
        title={menuCrud.detail.currentRecord ? `菜单详情 · ${menuCrud.detail.currentRecord.menuName}` : '菜单详情'}
        open={menuCrud.detail.open}
        onClose={menuCrud.detail.close}
        width={720}
        destroyOnClose
      >
        {menuCrud.detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : menuCrud.detail.currentRecord ? (
          <ProDescriptions<MenuRecord> {...detailProps} columns={menuDetailColumns} />
        ) : null}
      </Drawer>
    </PageContainer>
  );
};

export default MenuManagementPage;
