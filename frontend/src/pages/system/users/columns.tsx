import { TableActionBar } from '@/features/table/TableActionBar';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import type { TableActionItem } from '@/features/table/TableActionBar';
import type { ProColumns, ProDescriptionsItemProps } from '@ant-design/pro-components';
import { Tag, Typography } from 'antd';
import { maskEmail, maskIdCardNumber, maskMobile } from '@/utils/sensitive';
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
    title: '用户ID',
    dataIndex: 'id',
    search: {
      transform: (value) => ({ userId: value ? Number(value) : undefined }),
    },
    width: 96,
  },
  {
    title: '用户编号',
    dataIndex: 'userNo',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
  },
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
    title: '邮箱',
    dataIndex: 'email',
    search: true,
    ellipsis: true,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => {
      const content = maskEmail(record.email) || '';
      return content ? <Typography.Text ellipsis={{ tooltip: content }}>{content}</Typography.Text> : '-';
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
    title: '来源',
    dataIndex: 'source',
    valueEnum: {
      LEGACY_SYS_USER: { text: '旧系统迁移' },
      PASSWORD: { text: '账号密码' },
      SMS: { text: '短信注册' },
      EMAIL: { text: '邮箱注册' },
      WECHAT: { text: '微信' },
      ADMIN_CREATE: { text: '后台创建' },
      SYSTEM: { text: '系统' },
    },
    responsive: ['lg', 'xl', 'xxl'],
  },
  {
    title: '注册时间',
    dataIndex: 'registeredAt',
    valueType: 'dateRange',
    search: {
      transform: (value) => ({
        registeredStart: value?.[0],
        registeredEnd: value?.[1],
      }),
    },
    responsive: ['lg', 'xl', 'xxl'],
    renderText: (value) => value || '-',
  },
  {
    title: '最近登录',
    dataIndex: 'lastLoginAt',
    valueType: 'dateRange',
    search: {
      transform: (value) => ({
        lastLoginStart: value?.[0],
        lastLoginEnd: value?.[1],
      }),
    },
    responsive: ['xl', 'xxl'],
    renderText: (value) => value || '-',
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
  { title: '用户ID', dataIndex: 'id' },
  { title: '用户编号', dataIndex: 'userNo', renderText: (value) => value || '-' },
  { title: '用户名', dataIndex: 'username' },
  { title: '昵称', dataIndex: 'nickname', renderText: (value) => value || '-' },
  { title: '姓名', dataIndex: 'realName', renderText: (value) => value || '-' },
  { title: '手机号', dataIndex: 'mobile', renderText: (value) => maskMobile(value) || '-' },
  {
    title: '身份证号码',
    dataIndex: 'idCardNumber',
    renderText: (value) => maskIdCardNumber(value) || '-',
  },
  { title: '邮箱', dataIndex: 'email', renderText: (value) => maskEmail(value) || '-' },
  { title: '头像地址', dataIndex: 'avatarUrl', renderText: (value) => value || '-' },
  { title: '出生年月', dataIndex: 'birthMonth', renderText: (value) => value || '-' },
  { title: '性别', dataIndex: 'gender', renderText: (value) => value || '-' },
  { title: '所在地区', dataIndex: 'region', renderText: (value) => value || '-' },
  { title: '可工作时间', dataIndex: 'availableTime', renderText: (value) => value || '-' },
  { title: '状态', dataIndex: 'status' },
  { title: '来源', dataIndex: 'source', renderText: (value) => value || '-' },
  { title: '注册时间', dataIndex: 'registeredAt', renderText: (value) => value || '-' },
  { title: '最近登录', dataIndex: 'lastLoginAt', renderText: (value) => value || '-' },
  {
    title: '角色',
    dataIndex: 'roleNames',
    renderText: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-'),
  },
  { title: '创建时间', dataIndex: 'createdAt', renderText: (value) => value || '-' },
  { title: '更新时间', dataIndex: 'updatedAt', renderText: (value) => value || '-' },
];
