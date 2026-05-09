import { PageContainer, ProCard } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Empty, Space, Table, Tag, Typography } from 'antd';
import { taskService } from '@/services/task';
import type { TaskRecord } from '@/types/api';

const columns = [
  { title: '任务', dataIndex: 'title', render: (_: unknown, record: TaskRecord) => <Space direction="vertical" size={0}><Typography.Text strong>{record.title}</Typography.Text><Typography.Text type="secondary">{record.description || record.businessTitle}</Typography.Text></Space> },
  { title: '类型', dataIndex: 'taskType', width: 120, render: (value: string) => <Tag color="blue">{value}</Tag> },
  { title: '业务类型', dataIndex: 'businessType', width: 160 },
  { title: '状态', dataIndex: 'status', width: 120, render: (value: string) => <Tag color={value === 'PENDING' ? 'orange' : 'green'}>{value}</Tag> },
  { title: '创建时间', dataIndex: 'createTime', width: 190 },
];

const TasksPage = () => {
  const pendingQuery = useQuery({ queryKey: ['tasks', 'pending'], queryFn: () => taskService.myPending({ pageNo: 1, pageSize: 50 }) });
  const handledQuery = useQuery({ queryKey: ['tasks', 'handled'], queryFn: () => taskService.myHandled({ pageNo: 1, pageSize: 50 }) });

  return (
    <PageContainer title="任务中心">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <ProCard title="我的待办" variant="outlined">
          <Table<TaskRecord>
            rowKey="id"
            columns={columns}
            dataSource={pendingQuery.data?.records || []}
            loading={pendingQuery.isLoading}
            pagination={false}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待办任务" /> }}
          />
        </ProCard>
        <ProCard title="我已处理" variant="outlined">
          <Table<TaskRecord>
            rowKey="id"
            columns={columns}
            dataSource={handledQuery.data?.records || []}
            loading={handledQuery.isLoading}
            pagination={false}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已处理任务" /> }}
          />
        </ProCard>
      </Space>
    </PageContainer>
  );
};

export default TasksPage;
