import { Space, Tag, Typography } from 'antd';
import type { AuditLogRecord, MyTenant } from '@/types/api';

export const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', { hour12: false });
};

export const buildSwitchHistoryItems = (records: AuditLogRecord[], tenantById: Map<number, MyTenant>) =>
  records.slice(0, 5).map((record) => {
    const result = record.logResult || record.loginResult || 'UNKNOWN';
    const isSuccess = result === 'SUCCESS';
    const tenant = record.tenantId ? tenantById.get(record.tenantId) : undefined;
    const tenantLabel = tenant
      ? `${tenant.tenantName}${tenant.tenantCode ? `（${tenant.tenantCode}）` : ''}`
      : record.tenantId
        ? `租户 #${record.tenantId}`
        : '未知租户';

    return {
      key: record.id,
      color: isSuccess ? 'green' : 'red',
      children: (
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          <Space size={8} wrap>
            <Typography.Text strong>{record.username || '未知用户'}</Typography.Text>
            <Tag color={isSuccess ? 'green' : 'red'}>{isSuccess ? '成功' : '失败'}</Tag>
          </Space>
          <Typography.Text>{`切换至 ${tenantLabel}`}</Typography.Text>
          <Typography.Text type="secondary">
            {record.failReason || record.detailMessage || '租户切换操作'} · {formatDateTime(record.createdAt)}
          </Typography.Text>
        </Space>
      ),
    };
  });
