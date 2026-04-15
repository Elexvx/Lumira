import type { ProColumns } from '@ant-design/pro-components';
import { Tag, Typography } from 'antd';
import { TableActionBar } from '@/features/table/TableActionBar';
import type { PluginRuntimeLog, PluginVersion } from '@/types/api';

interface BuildVersionColumnsOptions {
  isDesktop: boolean;
  isMobile: boolean;
  onInstall: (pluginCode: string, version: string) => void;
  onActivate: (pluginCode: string, version: string) => void;
  onDisable: (pluginCode: string) => void;
  onRollback: (pluginCode: string, version: string) => void;
}

export const buildVersionColumns = ({
  isDesktop,
  isMobile,
  onInstall,
  onActivate,
  onDisable,
  onRollback,
}: BuildVersionColumnsOptions): ProColumns<PluginVersion>[] => [
  { title: '版本', dataIndex: 'version' },
  { title: '安装状态', dataIndex: 'installStatus' },
  { title: '加载状态', dataIndex: 'loadStatus' },
  { title: '健康状态', dataIndex: 'healthStatus' },
  {
    title: '激活',
    dataIndex: 'isActive',
    render: (_, record) => <Tag color={record.isActive === 1 ? 'green' : 'default'}>{record.isActive === 1 ? '是' : '否'}</Tag>,
  },
  {
    title: '操作',
    fixed: isDesktop ? 'right' : undefined,
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={[
          { key: 'install', label: '安装', onClick: () => onInstall(record.pluginCode, record.version) },
          { key: 'activate', label: '激活', onClick: () => onActivate(record.pluginCode, record.version) },
          { key: 'disable', label: '停用', onClick: () => onDisable(record.pluginCode), danger: true },
          { key: 'rollback', label: '回滚', onClick: () => onRollback(record.pluginCode, record.version) },
        ]}
      />
    ),
  },
];

export const logColumns: ProColumns<PluginRuntimeLog>[] = [
  { title: '时间', dataIndex: 'createdAt', width: 180 },
  { title: '操作类型', dataIndex: 'operationType', width: 120 },
  { title: '生命周期', dataIndex: 'lifecycleStatus', width: 120 },
  { title: '结果', dataIndex: 'resultStatus', width: 120 },
  {
    title: '详情',
    dataIndex: 'detailMessage',
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) =>
      record.detailMessage ? (
        <Typography.Text copyable={{ text: record.detailMessage }} ellipsis={{ tooltip: record.detailMessage }}>
          {record.detailMessage}
        </Typography.Text>
      ) : (
        '-'
      ),
  },
];
