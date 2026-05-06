import { Card, Descriptions, Space, Tag } from 'antd';
import type { DescriptionsProps } from 'antd';

interface SecuritySummaryCardProps {
  permissionCount: number;
  roleNames: string[];
  descriptionsProps: DescriptionsProps;
}

export const SecuritySummaryCard = ({ permissionCount, roleNames, descriptionsProps }: SecuritySummaryCardProps) => (
  <Card title="账号与安全" style={{ width: '100%' }}>
    <Descriptions {...descriptionsProps}>
      <Descriptions.Item label="权限数">{permissionCount}</Descriptions.Item>
      <Descriptions.Item label="角色摘要">
        <Space wrap>{roleNames.length ? roleNames.map((name) => <Tag key={name}>{name}</Tag>) : <Tag>暂无角色摘要</Tag>}</Space>
      </Descriptions.Item>
    </Descriptions>
  </Card>
);
