import { TableActionBar } from '@/features/table/TableActionBar';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import type { TableActionItem } from '@/features/table/TableActionBar';
import type { ProColumns, ProDescriptionsItemProps } from '@ant-design/pro-components';
import { Tag, Typography } from 'antd';
import { maskIdCardNumber, maskMobile } from '@/utils/sensitive';
import type { UserDetail, UserRecord } from '@/types/api';

interface BuildUserColumnsOptions {
  isDesktop: boolean;
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenDetail: (record: UserRecord) => void;
  onOpenEdit: (record: UserRecord) => void;
  onToggleStatus: (record: UserRecord) => void;
  onDelete: (record: UserRecord) => void;
  isProtectedAdminAccount: (record?: Pick<UserRecord, 'id' | 'username'> | null) => boolean;
}

export const buildUserColumns = ({
  isDesktop,
  isMobile,
  buildRowActions,
  onOpenDetail,
  onOpenEdit,
  onToggleStatus,
  onDelete,
  isProtectedAdminAccount,
}: BuildUserColumnsOptions): ProColumns<UserRecord>[] => [
  {
    title: '用户名',
    dataIndex: 'username',
    search: true,
  },
  {
    title: '手机号',
    dataIndex: 'mobile',
    search: true,
    ellipsis: true,
    render: (_, record) => {
      const content = maskMobile(record.mobile) || '';
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
    title: '状态',
    dataIndex: 'status',
    valueEnum: {
      ENABLED: { text: '启用', status: 'Success' },
      DISABLED: { text: '禁用', status: 'Default' },
    },
    search: {
      transform: (value) => ({ status: value }),
    },
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
  },
  {
    title: '昵称',
    dataIndex: 'nickname',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
  },
  {
    title: '姓名',
    dataIndex: 'realName',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
  },
  {
    title: '角色',
    dataIndex: 'roleNames',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) => {
      const content = record.roleNames?.length ? record.roleNames.join(', ') : '';
      return content ? <Typography.Text ellipsis={{ tooltip: content }}>{content}</Typography.Text> : '-';
    },
  },
  {
    title: '操作',
    valueType: 'option',
    fixed: isDesktop ? 'right' : undefined,
    width: 220,
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildRowActions([
          {
            key: 'view',
            label: '详情',
            permission: 'system:user:view',
            onClick: () => onOpenDetail(record),
          },
          {
            key: 'edit',
            label: '编辑',
            permission: 'system:user:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'toggle',
            label: record.status === 'ENABLED' ? '禁用' : '启用',
            permission: 'system:user:status',
            hidden: isProtectedAdminAccount(record),
            danger: record.status === 'ENABLED',
            onClick: () => onToggleStatus(record),
          },
          {
            key: 'delete',
            label: '删除',
            permission: 'system:user:delete',
            hidden: isProtectedAdminAccount(record),
            danger: true,
            onClick: () => onDelete(record),
          },
        ])}
      />
    ),
  },
];

export const userDetailColumns: ProDescriptionsItemProps<UserDetail>[] = [
  { title: '用户名', dataIndex: 'username' },
  { title: '昵称', dataIndex: 'nickname', renderText: (value) => value || '-' },
  { title: '姓名', dataIndex: 'realName', renderText: (value) => value || '-' },
  { title: '手机号', dataIndex: 'mobile', renderText: (value) => maskMobile(value) || '-' },
  {
    title: '身份证号码',
    dataIndex: 'idCardNumber',
    renderText: (value) => maskIdCardNumber(value) || '-',
  },
  { title: '邮箱', dataIndex: 'email', renderText: (value) => value || '-' },
  { title: '头像地址', dataIndex: 'avatarUrl', renderText: (value) => value || '-' },
  { title: '出生年月', dataIndex: 'birthMonth', renderText: (value) => value || '-' },
  { title: '性别', dataIndex: 'gender', renderText: (value) => value || '-' },
  { title: '所在地区', dataIndex: 'region', renderText: (value) => value || '-' },
  { title: '可工作时间', dataIndex: 'availableTime', renderText: (value) => value || '-' },
  { title: '状态', dataIndex: 'status' },
  {
    title: '角色',
    dataIndex: 'roleNames',
    renderText: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-'),
  },
  { title: '创建时间', dataIndex: 'createdAt', renderText: (value) => value || '-' },
  { title: '更新时间', dataIndex: 'updatedAt', renderText: (value) => value || '-' },
];
