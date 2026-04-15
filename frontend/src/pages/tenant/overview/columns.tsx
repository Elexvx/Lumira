import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import type { ProColumns } from '@ant-design/pro-components';
import { Tag, Typography } from 'antd';
import { formatDateTime } from '@/pages/tenant/overview/utils';
import type { MyTenant, TenantPlugin, TenantSummary } from '@/types/api';

interface BuildTenantColumnsOptions {
  isDesktop: boolean;
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenDetail: (record: TenantSummary) => void;
  onOpenEdit: (record: TenantSummary) => void;
  onDelete: (record: TenantSummary) => void;
}

export const buildTenantColumns = ({
  isDesktop,
  isMobile,
  buildRowActions,
  onOpenDetail,
  onOpenEdit,
  onDelete,
}: BuildTenantColumnsOptions): ProColumns<TenantSummary>[] => [
  {
    title: '租户编码',
    dataIndex: 'tenantCode',
    search: true,
  },
  {
    title: '租户名称',
    dataIndex: 'tenantName',
    search: true,
  },
  {
    title: '简称',
    dataIndex: 'tenantShortName',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
  },
  {
    title: '状态',
    dataIndex: 'status',
    valueEnum: {
      ENABLED: { text: '启用', status: 'Success' },
      DISABLED: { text: '停用', status: 'Default' },
    },
    search: {
      transform: (value) => ({ status: value }),
    },
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    hideInSearch: true,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => formatDateTime(record.createdAt),
  },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    hideInSearch: true,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => formatDateTime(record.updatedAt),
  },
  {
    title: '操作',
    valueType: 'option',
    fixed: isDesktop ? 'right' : undefined,
    width: 200,
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildRowActions([
          {
            key: 'detail',
            label: '详情',
            permission: 'tenant:view',
            onClick: () => onOpenDetail(record),
          },
          {
            key: 'edit',
            label: '编辑',
            permission: 'tenant:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'delete',
            label: '删除',
            permission: 'tenant:delete',
            danger: true,
            onClick: () => onDelete(record),
          },
        ])}
      />
    ),
  },
];

export const pluginColumns: ProColumns<TenantPlugin>[] = [
  { title: '插件编码', dataIndex: 'pluginCode' },
  { title: '插件名称', dataIndex: 'pluginName' },
  { title: '版本', dataIndex: 'version' },
  {
    title: '共享依赖',
    dataIndex: 'sharedDeps',
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) => {
      const content = record.sharedDeps?.length ? record.sharedDeps.join(', ') : '';
      return content ? (
        <Typography.Text copyable={{ text: content }} ellipsis={{ tooltip: content }}>
          {content}
        </Typography.Text>
      ) : (
        '-'
      );
    },
  },
  {
    title: '菜单数',
    dataIndex: 'menus',
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => record.menus?.length ?? 0,
  },
];

export const myTenantColumns: ProColumns<MyTenant>[] = [
  { title: '租户编码', dataIndex: 'tenantCode' },
  { title: '租户名称', dataIndex: 'tenantName' },
  { title: '简称', dataIndex: 'tenantShortName', responsive: ['md', 'lg', 'xl', 'xxl'] },
  {
    title: '默认',
    dataIndex: 'isDefault',
    render: (_, record) => <Tag color={record.isDefault ? 'green' : 'default'}>{record.isDefault ? '是' : '否'}</Tag>,
  },
  {
    title: '状态',
    dataIndex: 'status',
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
  },
];
