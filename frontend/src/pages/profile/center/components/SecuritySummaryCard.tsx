import { Card, Descriptions, Space, Tag } from 'antd';
import type { DescriptionsProps } from 'antd';
import type { TenantSummary } from '@/types/api';

interface SecuritySummaryCardProps {
  currentTenant: TenantSummary | null;
  permissionCount: number;
  roleNames: string[];
  descriptionsProps: DescriptionsProps;
}

export const SecuritySummaryCard = ({ currentTenant, permissionCount, roleNames, descriptionsProps }: SecuritySummaryCardProps) => (
  <Card title="租户与安全" style={{ width: '100%' }}>
    <Descriptions {...descriptionsProps}>
      <Descriptions.Item label="当前租户">{currentTenant?.tenantName || '未选择'}</Descriptions.Item>
      <Descriptions.Item label="租户编码">{currentTenant?.tenantCode || '-'}</Descriptions.Item>
      <Descriptions.Item label="权限数">{permissionCount}</Descriptions.Item>
      <Descriptions.Item label="角色摘要">
        <Space wrap>{roleNames.length ? roleNames.map((name) => <Tag key={name}>{name}</Tag>) : <Tag>暂无角色摘要</Tag>}</Space>
      </Descriptions.Item>
    </Descriptions>
  </Card>
);
