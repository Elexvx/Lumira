import { Empty } from 'antd';

export const EmptyState = ({ description = '功能建设中' }: { description?: string }) => <Empty description={description} />;
