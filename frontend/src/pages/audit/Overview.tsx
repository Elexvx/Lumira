import { useMemo, useState } from 'react';
import { Button, Card, DatePicker, Form, Input, Space, Tabs, Tag, Typography } from 'antd';
import { ManagementPageContainer } from '@/components/ManagementPageContainer';
import { QueryPanel } from '@/components/QueryPanel';
import { ActionBar } from '@/components/ActionBar';
import { DataTable } from '@/components/DataTable';
import { DetailDrawer } from '@/components/DetailDrawer';
import { auditService } from '@/services/audit';
import type { AuditLogRecord } from '@/types/api';

type AuditTabKey = 'login' | 'operation';

export default () => {
  const [form] = Form.useForm();
  const [activeTab, setActiveTab] = useState<AuditTabKey>('login');
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedRecord, setSelectedRecord] = useState<AuditLogRecord | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const submitQuery = async (values: { username?: string; tenantId?: number; range?: [unknown, unknown] }) => {
    const next: Record<string, unknown> = {
      username: values.username,
      tenantId: values.tenantId,
    };
    if (values.range?.length === 2) {
      const start = values.range[0] as { format?: (fmt: string) => string };
      const end = values.range[1] as { format?: (fmt: string) => string };
      if (start.format && end.format) {
        next.startTime = start.format('YYYY-MM-DD HH:mm:ss');
        next.endTime = end.format('YYYY-MM-DD HH:mm:ss');
      }
    }
    setQuery(next);
  };

  const resetQuery = () => {
    form.resetFields();
    setQuery({});
  };

  const columns = useMemo(
    () =>
      activeTab === 'login'
        ? [
            { title: '用户名', dataIndex: 'username', width: 140 },
            { title: '租户', dataIndex: 'tenantId', width: 100 },
            {
              title: '结果',
              dataIndex: 'logResult',
              width: 120,
              render: (value: string) => <Tag color={value === 'SUCCESS' ? 'green' : 'red'}>{value}</Tag>,
            },
            { title: '原因', dataIndex: 'failReason' },
            { title: '时间', dataIndex: 'createdAt', width: 180 },
            {
              title: '操作',
              key: 'actions',
              width: 120,
              render: (_: unknown, record: AuditLogRecord) => (
                <Button
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
            { title: '模块', dataIndex: 'moduleName', width: 140 },
            { title: '操作', dataIndex: 'actionName', width: 140 },
            { title: '类型', dataIndex: 'operationType', width: 120 },
            { title: '详情', dataIndex: 'detailMessage' },
            { title: '时间', dataIndex: 'createdAt', width: 180 },
            {
              title: '操作',
              key: 'actions',
              width: 120,
              render: (_: unknown, record: AuditLogRecord) => (
                <Button
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
          ],
    [activeTab],
  );

  const request = async (params: { current: number; pageSize: number }) => {
    const payload = {
      ...query,
      pageNo: params.current,
      pageSize: params.pageSize,
    };
    return activeTab === 'login'
      ? auditService.loginLogs(payload, { autoRedirectOnUnauthorized: false })
      : auditService.operationLogs(payload, { autoRedirectOnUnauthorized: false });
  };

  return (
    <ManagementPageContainer title="审计中心" description="查看登录日志和操作日志，并按用户、租户和时间范围筛选。">
      <QueryPanel
        form={form}
        onSearch={submitQuery}
        onReset={resetQuery}
        columns={4}
        collapseCount={4}
        actions={
          <Button onClick={() => setQuery({ ...query })} type="primary">
            刷新查询
          </Button>
        }
      >
        <Form.Item name="username" label="用户名">
          <Input allowClear placeholder="输入用户名" />
        </Form.Item>
        <Form.Item name="tenantId" label="租户ID">
          <Input allowClear placeholder="输入租户 ID" />
        </Form.Item>
        <Form.Item name="range" label="时间范围">
          <DatePicker.RangePicker showTime style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="keyword" label="关键字">
          <Input allowClear placeholder="可输入操作说明" />
        </Form.Item>
      </QueryPanel>

      <ActionBar
        left={
          <Tabs
            activeKey={activeTab}
            items={[
              { key: 'login', label: '登录日志' },
              { key: 'operation', label: '操作日志' },
            ]}
            onChange={(key) => setActiveTab(key as AuditTabKey)}
          />
        }
        right={
          <Space>
            <Typography.Text type="secondary">按账号、租户、时间范围筛选</Typography.Text>
          </Space>
        }
      />

      <Card bodyStyle={{ height: 460, minHeight: 0 }}>
        <DataTable<AuditLogRecord>
          rowKey="id"
          columns={columns}
          request={request}
          params={{ activeTab, ...query }}
          middleScroll
          emptyText="暂无审计日志"
        />
      </Card>

      <DetailDrawer
        title={selectedRecord ? `日志详情 · ${selectedRecord.username || selectedRecord.moduleName || selectedRecord.id}` : '日志详情'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        descriptionItems={
          selectedRecord
            ? [
                { key: 'username', label: '用户名', children: selectedRecord.username || '-' },
                { key: 'tenantId', label: '租户ID', children: selectedRecord.tenantId ?? '-' },
                { key: 'type', label: '类型', children: selectedRecord.logType || selectedRecord.operationType || '-' },
                { key: 'result', label: '结果', children: selectedRecord.logResult || '-' },
                { key: 'requestId', label: 'RequestId', children: selectedRecord.requestId || '-' },
                { key: 'traceId', label: 'TraceId', children: selectedRecord.traceId || '-' },
                { key: 'createdAt', label: '时间', children: selectedRecord.createdAt },
              ]
            : undefined
        }
      >
        {selectedRecord ? (
          <Card size="small" title="扩展信息">
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
              {selectedRecord.failReason || selectedRecord.detailMessage || '无更多详情'}
            </Typography.Paragraph>
          </Card>
        ) : null}
      </DetailDrawer>
    </ManagementPageContainer>
  );
};
