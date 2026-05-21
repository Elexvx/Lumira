import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import type { ProColumns, ProDescriptionsItemProps } from '@ant-design/pro-components';
import { Tag, Typography } from 'antd';
import { renderStatusLabel } from '@/pages/settings/dicts/constants';
import type { DictItemRecord, DictTypeRecord } from '@/types/api';

interface BuildDictTypeColumnsOptions {
  isDesktop: boolean;
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenDetail: (record: DictTypeRecord) => void;
  onOpenEdit: (record: DictTypeRecord) => void;
  onDelete: (record: DictTypeRecord) => void;
}

export const buildDictTypeColumns = ({
  isDesktop,
  isMobile,
  buildRowActions,
  onOpenDetail,
  onOpenEdit,
  onDelete,
}: BuildDictTypeColumnsOptions): ProColumns<DictTypeRecord>[] => [
  {
    title: '字典编码',
    dataIndex: 'dictCode',
    search: true,
  },
  {
    title: '字典名称',
    dataIndex: 'dictName',
    search: true,
  },
  {
    title: '状态',
    dataIndex: 'status',
    valueEnum: {
      ENABLED: { text: '启用', status: 'Success' },
      DISABLED: { text: '停用', status: 'Default' },
    },
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{renderStatusLabel(record.status)}</Tag>,
  },
  {
    title: '系统内置',
    dataIndex: 'isSystem',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => <Tag color={record.isSystem ? 'green' : 'default'}>{record.isSystem ? '是' : '否'}</Tag>,
  },
  {
    title: '备注',
    dataIndex: 'remark',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) =>
      record.remark ? (
        <Typography.Text copyable={{ text: record.remark }} ellipsis={{ tooltip: record.remark }}>
          {record.remark}
        </Typography.Text>
      ) : (
        '-'
      ),
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
            key: 'view',
            label: '详情',
            permission: 'system:dict:view',
            onClick: () => onOpenDetail(record),
          },
          {
            key: 'edit',
            label: '编辑',
            permission: 'system:dict:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'delete',
            label: '删除',
            permission: 'system:dict:delete',
            danger: true,
            disabled: Boolean(record.isSystem),
            onClick: () => onDelete(record),
          },
        ])}
      />
    ),
  },
];

interface BuildDictItemColumnsOptions {
  isDesktop: boolean;
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenEdit: (record: DictItemRecord) => void;
  onDelete: (record: DictItemRecord) => void;
}

export const buildDictItemColumns = ({
  isDesktop,
  isMobile,
  buildRowActions,
  onOpenEdit,
  onDelete,
}: BuildDictItemColumnsOptions): ProColumns<DictItemRecord>[] => [
  { title: '标签', dataIndex: 'itemLabel' },
  { title: '值', dataIndex: 'itemValue' },
  { title: '排序', dataIndex: 'sortNo', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  {
    title: '状态',
    dataIndex: 'status',
    search: false,
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{renderStatusLabel(record.status)}</Tag>,
  },
  {
    title: '备注',
    dataIndex: 'remark',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) =>
      record.remark ? (
        <Typography.Text copyable={{ text: record.remark }} ellipsis={{ tooltip: record.remark }}>
          {record.remark}
        </Typography.Text>
      ) : (
        '-'
      ),
  },
  {
    title: '操作',
    valueType: 'option',
    fixed: isDesktop ? 'right' : undefined,
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildRowActions([
          {
            key: 'edit',
            label: '编辑',
            permission: 'system:dict:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'delete',
            label: '删除',
            permission: 'system:dict:delete',
            danger: true,
            onClick: () => onDelete(record),
          },
        ])}
      />
    ),
  },
];

export const dictTypeDetailColumns: ProDescriptionsItemProps<DictTypeRecord>[] = [
  { title: '字典编码', dataIndex: 'dictCode' },
  { title: '字典名称', dataIndex: 'dictName' },
  { title: '状态', dataIndex: 'status', renderText: (value) => renderStatusLabel(String(value)) },
  {
    title: '系统内置',
    dataIndex: 'isSystem',
    renderText: (value) => (value ? '是' : '否'),
  },
  { title: '备注', dataIndex: 'remark', renderText: (value) => value || '-' },
];
