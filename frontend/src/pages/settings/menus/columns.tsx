import { HolderOutlined, MinusOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Space, Tag, Typography } from 'antd';
import type { ProColumns, ProDescriptionsItemProps } from '@ant-design/pro-components';
import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import type { MenuRecord } from '@/types/api';
import type { MenuTreeRecord } from '@/pages/settings/menus/treeUtils';

interface BuildMenuColumnsOptions {
  isMobile: boolean;
  canReorderMenus: boolean;
  expandedRowKeys: number[];
  expandableMenuIds: Set<number>;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  isReadonlyMenu: (record: MenuRecord) => boolean;
  onToggleExpand: (menuId: number) => void;
  onOpenDetail: (record: MenuRecord) => void;
  onOpenEdit: (record: MenuRecord) => void;
  onToggleStatus: (record: MenuRecord) => void;
  onDelete: (record: MenuRecord) => void;
}

export const buildMenuColumns = ({
  isMobile,
  canReorderMenus,
  expandedRowKeys,
  expandableMenuIds,
  buildRowActions,
  isReadonlyMenu,
  onToggleExpand,
  onOpenDetail,
  onOpenEdit,
  onToggleStatus,
  onDelete,
}: BuildMenuColumnsOptions): ProColumns<MenuTreeRecord>[] => [
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
          <HolderOutlined style={{ color: '#8c8c8c', cursor: canReorderMenus && !readonly ? 'grab' : 'not-allowed' }} />
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
      <Typography.Text
        className="saas-menu-tree-cell"
        ellipsis={{ tooltip: record.menuName }}
        style={{ paddingInlineStart: `${(record.level || 0) * 24}px` }}
      >
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

export const menuDetailColumns: ProDescriptionsItemProps<MenuRecord>[] = [
  { title: '菜单名称', dataIndex: 'menuName' },
  { title: '菜单类型', dataIndex: 'menuType' },
  { title: '路由', dataIndex: 'path', renderText: (value) => value || '-' },
  { title: '组件', dataIndex: 'component', renderText: (value) => value || '-' },
  { title: '权限标识', dataIndex: 'permissionKey', renderText: (value) => value || '-' },
  { title: '状态', dataIndex: 'status' },
];
