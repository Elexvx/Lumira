import { CheckOutlined, CloseOutlined, FileSearchOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Input, Modal, Space, Tag, Timeline, Typography } from 'antd';
import { useRef, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { StandardDrawer } from '@/features/management/StandardDrawer';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useResponsive } from '@/hooks/useResponsive';
import { approveWorkflowTask, listMyWorkflowTasks, listWorkflowLogs, rejectWorkflowTask } from '@/services/workflow/api';
import type { WorkflowActionLog, WorkflowTask } from '@/services/workflow/types';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';

const businessText: Record<string, string> = {
  EXPERT_APPLICATION: '专家申请',
  COMPETITION_APPROVAL: '赛事审批',
};

const WorkflowTasksPage = () => {
  const responsive = useResponsive();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [logs, setLogs] = useState<WorkflowActionLog[]>([]);
  const [logOpen, setLogOpen] = useState(false);

  const handleAction = (record: WorkflowTask, action: 'approve' | 'reject') => {
    let comment = '';
    Modal.confirm({
      title: action === 'approve' ? '通过审批' : '驳回审批',
      content: <Input.TextArea rows={4} maxLength={500} placeholder="审批意见" onChange={(event) => (comment = event.target.value)} />,
      okButtonProps: { danger: action === 'reject' },
      onOk: async () => {
        try {
          if (action === 'approve') {
            await approveWorkflowTask(record.id, comment);
            message.success('审批已通过');
          } else {
            await rejectWorkflowTask(record.id, comment);
            message.success('审批已驳回');
          }
          actionRef.current?.reload();
        } catch (error) {
          showErrorMessage(error, '审批处理失败');
          throw error;
        }
      },
    });
  };

  const openLogs = async (record: WorkflowTask) => {
    try {
      setLogs(await listWorkflowLogs(record.instanceId));
      setLogOpen(true);
    } catch (error) {
      showErrorMessage(error, '审批轨迹加载失败');
    }
  };

  const columns: ProColumns<WorkflowTask>[] = [
    {
      title: '业务',
      dataIndex: 'businessTitle',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.businessTitle || '-'}</Typography.Text>
          <Typography.Text type="secondary">{businessText[record.businessType] || record.businessType}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '节点',
      dataIndex: 'nodeName',
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      width: 120,
      render: (_, record) => <Tag color={record.status === 'PENDING' ? 'blue' : 'default'}>{record.status}</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      search: false,
      width: 180,
      render: (value) => value || '-',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: responsive.isDesktop ? 'right' : undefined,
      render: (_, record) => (
        <TableActionBar
          isMobile={responsive.isMobile}
          items={[
            { key: 'approve', label: '通过', icon: <CheckOutlined />, onClick: () => handleAction(record, 'approve') },
            { key: 'reject', label: '驳回', icon: <CloseOutlined />, danger: true, onClick: () => handleAction(record, 'reject') },
            { key: 'logs', label: '轨迹', icon: <FileSearchOutlined />, onClick: () => void openLogs(record) },
          ]}
        />
      ),
    },
  ];

  return (
    <ManagementPage title="我的审批">
      <ManagementPageBody>
        <ManagementTable<WorkflowTask>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          scroll={{ x: 980 }}
          request={async (params) => {
            const response = await listMyWorkflowTasks({ status: 'PENDING', pageNo: params.current, pageSize: params.pageSize });
            return { data: response.records, total: response.total, success: true };
          }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
        <StandardDrawer title="审批轨迹" open={logOpen} onClose={() => setLogOpen(false)}>
          <Timeline
            items={logs.map((log) => ({
              children: (
                <Space direction="vertical" size={2}>
                  <Typography.Text strong>{log.actionType}</Typography.Text>
                  <Typography.Text type="secondary">{log.operatorUsername || '-'} / {log.createdAt || '-'}</Typography.Text>
                  {log.comment ? <Typography.Paragraph>{log.comment}</Typography.Paragraph> : null}
                </Space>
              ),
            }))}
          />
        </StandardDrawer>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default WorkflowTasksPage;
