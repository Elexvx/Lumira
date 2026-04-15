import { HolderOutlined, MinusOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Space, Tag, Typography } from 'antd';
import type { ProColumns, ProDescriptionsItemProps } from '@ant-design/pro-components';
import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import type { MenuRecord } from '@/types/api';
import type { MenuTreeRecord } from '@/pages/system/menus/treeUtils';

interface BuildMenuColumnsOptions {
  isDesktop: boolean;
  isMobile: boolean;
  canReorderMenus: boolean;
  expandedRowKeys: number[];
  expandableMenuIds: Set<number>;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onToggleExpand: (menuId: number) => void;
  onOpenDetail: (record: MenuRecord) => void;
  onOpenEdit: (record: MenuRecord) => void;
  onToggleStatus: (record: MenuRecord) => void;
}

export const buildMenuColumns = ({
  isDesktop,
  isMobile,
  canReorderMenus,
  expandedRowKeys,
  expandableMenuIds,
  buildRowActions,
  onToggleExpand,
  onOpenDetail,
  onOpenEdit,
  onToggleStatus,
}: BuildMenuColumnsOptions): ProColumns<MenuTreeRecord>[] => [
  {
    title: '拖拽',
    dataIndex: 'dragHandle',
    width: 88,
    hideInSearch: true,
    responsive: ['md', 'lg', 'xl', 'xxl'],
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
          <HolderOutlined style={{ color: '#8c8c8c', cursor: canReorderMenus ? 'grab' : 'not-allowed' }} />
        </Space>
      );
    },
  },
  {
    title: '菜单编码',
    dataIndex: 'menuCode',
    hideInSearch: true,
    responsive: ['lg', 'xl', 'xxl'],
  },
  {
    title: '菜单名称',
    dataIndex: 'menuName',
    search: true,
    render: (_, record) => <span style={{ paddingInlineStart: `${(record.level || 0) * 24}px` }}>{record.menuName}</span>,
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
    width: 220,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    ellipsis: true,
  },
  {
    title: '组件',
    dataIndex: 'component',
    hideInSearch: true,
    width: 300,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
  },
  {
    title: '权限标识',
    dataIndex: 'permissionKey',
    search: true,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) =>
      record.permissionKey ? <Typography.Text ellipsis={{ tooltip: record.permissionKey }}>{record.permissionKey}</Typography.Text> : '-',
  },
  {
    title: '排序',
    dataIndex: 'sortNo',
    hideInSearch: true,
    width: 88,
    responsive: ['md', 'lg', 'xl', 'xxl'],
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
    fixed: isDesktop ? 'right' : undefined,
    width: 180,
    render: (_, record) => (
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
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'status',
            label: record.status === 'ENABLED' ? '停用' : '启用',
            permission: 'system:menu:status',
            danger: record.status === 'ENABLED',
            onClick: () => onToggleStatus(record),
          },
        ])}
      />
    ),
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
