import { ProTable } from '@ant-design/pro-components';

export const DataTable = () => <ProTable rowKey="id" columns={[]} request={async () => ({ data: [], success: true })} />;
