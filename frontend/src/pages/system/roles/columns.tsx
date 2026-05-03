import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import { ROLE_TYPE_LABEL_MAP, ROLE_TYPE_OPTIONS } from '@/constants/role';
import type { ProColumns, ProDescriptionsItemProps } from '@ant-design/pro-components';
import type { RoleDetail, RoleRecord } from '@/types/api';

interface BuildRoleColumnsOptions {
  isDesktop: boolean;
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenDetail: (record: RoleRecord) => void;
  onOpenEdit: (record: RoleRecord) => void;
}

const roleTypeValueEnum = ROLE_TYPE_OPTIONS.reduce<Record<string, { text: string }>>((acc, item) => {
  acc[String(item.value)] = { text: item.label };
  return acc;
}, {});

export const buildRoleColumns = ({
  isDesktop,
  isMobile,
  buildRowActions,
  onOpenDetail,
  onOpenEdit,
}: BuildRoleColumnsOptions): ProColumns<RoleRecord>[] => [
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
    valueEnum: roleTypeValueEnum,
    search: {
      transform: (value) => ({ roleType: value }),
    },
  },
  {
    title: '权限数',
    dataIndex: 'permissionCount',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => record.permissionCount ?? 0,
  },
  {
    title: '用户数',
    dataIndex: 'userCount',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => record.userCount ?? 0,
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
            permission: 'system:role:view',
            onClick: () => onOpenDetail(record),
          },
          {
            key: 'edit',
            label: '编辑',
            permission: 'system:role:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'permission',
            label: '权限分配',
            permission: 'system:role:permissions',
            onClick: () => onOpenEdit(record),
          },
        ])}
      />
    ),
  },
];

export const roleDetailColumns: ProDescriptionsItemProps<RoleDetail>[] = [
  { title: '角色编码', dataIndex: 'roleCode' },
  { title: '角色名称', dataIndex: 'roleName' },
  {
    title: '角色类型',
    dataIndex: 'roleType',
    renderText: (value) => ROLE_TYPE_LABEL_MAP[String(value)] || String(value || '-'),
  },
  { title: '权限数', dataIndex: 'permissionCount' },
  { title: '用户数', dataIndex: 'userCount' },
];
