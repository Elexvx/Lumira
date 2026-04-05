import { useMemo, useRef, useState } from 'react';
import { PageContainer, ProDescriptions, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Button, Drawer, InputNumber, Space, Tag, DatePicker } from 'antd';
import { auditService } from '@/services/audit';
import type { AuditLogRecord } from '@/types/api';

type AuditLogType = 'login' | 'operation';
type AuditRecord = AuditLogRecord;

const renderStatusTag = (label?: string | null) => {
  if (!label) {
    return '-';
  }
  const normalized = label.toUpperCase();
  const map: Record<string, { color: string; text: string }> = {
    SUCCESS: { color: 'green', text: '成功' },
    FAIL: { color: 'red', text: '失败' },
    FAILED: { color: 'red', text: '失败' },
    ERROR: { color: 'red', text: '异常' },
    RUNNING: { color: 'blue', text: '运行中' },
    ENABLED: { color: 'green', text: '已启用' },
    DISABLED: { color: 'default', text: '已禁用' },
  };
  const current = map[normalized];
  return <Tag color={current?.color || 'blue'}>{current?.text || label}</Tag>;
};

const AuditOverviewPage = () => {
  const actionRef = useRef<ActionType>();
  const [logType, setLogType] = useState<AuditLogType>('login');
  const [selectedRecord, setSelectedRecord] = useState<AuditRecord | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const columns = useMemo<ProColumns<AuditRecord>[]>(() => {
    return logType === 'login'
      ? [
          { title: '用户名', dataIndex: 'username', hideInSearch: false },
          {
            title: '租户 ID',
            dataIndex: 'tenantId',
            hideInSearch: false,
            renderFormItem: () => <InputNumber min={1} style={{ width: '100%' }} controls={false} />,
          },
          {
            title: '时间范围',
            dataIndex: 'range',
            hideInTable: true,
            renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
            search: {
              transform: (value) => {
                if (!Array.isArray(value) || value.length !== 2) {
                  return {};
                }
                const [start, end] = value as unknown as [{ format: (format: string) => string }, { format: (format: string) => string }];
                return {
                  startTime: start?.format?.('YYYY-MM-DD HH:mm:ss'),
                  endTime: end?.format?.('YYYY-MM-DD HH:mm:ss'),
                };
              },
            },
          },
          {
            title: '结果',
            dataIndex: 'logResult',
            valueEnum: {
              SUCCESS: { text: '成功' },
              FAIL: { text: '失败' },
              FAILED: { text: '失败' },
              ERROR: { text: '异常' },
            },
            render: (_, record) => renderStatusTag(record.logResult),
          },
          { title: '原因', dataIndex: 'failReason', hideInSearch: true },
          { title: '时间', dataIndex: 'createdAt', hideInSearch: true },
          {
            title: '操作',
            valueType: 'option',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
              <Button
                type="link"
                size="small"
                onClick={() => {
                  setSelectedRecord(record);
                  setDrawerOpen(true);
                }}
              >
                详情
              </Button>
            ),
          },
        ]
      : [
          { title: '用户名', dataIndex: 'username', hideInSearch: false },
          {
            title: '租户 ID',
            dataIndex: 'tenantId',
            hideInSearch: false,
            renderFormItem: () => <InputNumber min={1} style={{ width: '100%' }} controls={false} />,
          },
          {
            title: '时间范围',
            dataIndex: 'range',
            hideInTable: true,
            renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
            search: {
              transform: (value) => {
                if (!Array.isArray(value) || value.length !== 2) {
                  return {};
                }
                const [start, end] = value as unknown as [{ format: (format: string) => string }, { format: (format: string) => string }];
                return {
                  startTime: start?.format?.('YYYY-MM-DD HH:mm:ss'),
                  endTime: end?.format?.('YYYY-MM-DD HH:mm:ss'),
                };
              },
            },
          },
          { title: '模块', dataIndex: 'moduleName', hideInSearch: true },
          { title: '操作', dataIndex: 'actionName', hideInSearch: true },
          { title: '类型', dataIndex: 'operationType', hideInSearch: true },
          { title: '详情', dataIndex: 'detailMessage', hideInSearch: true },
          { title: '时间', dataIndex: 'createdAt', hideInSearch: true },
          {
            title: '操作',
            valueType: 'option',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
              <Button
                type="link"
                size="small"
                onClick={() => {
                  setSelectedRecord(record);
                  setDrawerOpen(true);
                }}
              >
                详情
              </Button>
            ),
          },
        ];
  }, [logType]);

  return (
    <PageContainer
      title="审计中心"
      tabList={[
        { tab: '登录日志', key: 'login' },
        { tab: '操作日志', key: 'operation' },
      ]}
      tabActiveKey={logType}
      onTabChange={(key) => {
        setLogType(key as AuditLogType);
        actionRef.current?.reload();
      }}
    >
      <ProTable<AuditRecord>
        key={logType}
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        search={{ labelWidth: 'auto' }}
        options={false}
        pagination={{ showSizeChanger: true, pageSize: logType === 'login' ? 10 : 20 }}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          const payload = {
            pageNo: current,
            pageSize,
            ...rest,
          };
          const result =
            logType === 'login'
              ? await auditService.loginLogs(payload, { autoRedirectOnUnauthorized: false })
              : await auditService.operationLogs(payload, { autoRedirectOnUnauthorized: false });
          return {
            data: result.records,
            success: true,
            total: result.total,
          };
        }}
        toolBarRender={() => [
          <Button key="refresh" type="primary" onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ]}
      />

      <Drawer
        title={selectedRecord ? `日志详情 · ${selectedRecord.username || selectedRecord.moduleName || selectedRecord.id}` : '日志详情'}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setSelectedRecord(null);
        }}
        width={720}
        destroyOnClose
      >
        {selectedRecord ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <ProDescriptions<AuditRecord>
              column={2}
              dataSource={selectedRecord}
              columns={[
                { title: '用户名', dataIndex: 'username', renderText: (value) => value || '-' },
                { title: '租户 ID', dataIndex: 'tenantId', renderText: (value) => value ?? '-' },
                { title: '类型', dataIndex: 'logType', renderText: (value) => value || selectedRecord.operationType || '-' },
                { title: '结果', dataIndex: 'logResult', renderText: (value) => value || '-' },
                { title: 'RequestId', dataIndex: 'requestId', renderText: (value) => value || '-' },
                { title: 'TraceId', dataIndex: 'traceId', renderText: (value) => value || '-' },
                { title: '时间', dataIndex: 'createdAt' },
              ]}
            />
            <ProDescriptions<AuditRecord>
              column={1}
              dataSource={selectedRecord}
              columns={[
                {
                  title: '扩展信息',
                  dataIndex: 'detailMessage',
                  renderText: (_, entity) => entity.failReason || entity.detailMessage || '无更多详情',
                },
              ]}
            />
          </Space>
        ) : null}
      </Drawer>
    </PageContainer>
  );
};

export default AuditOverviewPage;
