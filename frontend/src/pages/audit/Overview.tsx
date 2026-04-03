import { useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { ColumnHeightOutlined, DownOutlined, ReloadOutlined, SettingOutlined, UpOutlined } from '@ant-design/icons';
import { Breadcrumb, Button, Card, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Select, Space, Typography } from 'antd';
import { auditService } from '@/services/audit';
import type { AuditLogRecord } from '@/types/api';
import './Overview.less';

type AuditLogType = 'login' | 'operation';
type AuditQueryValues = {
  username?: string;
  tenantId?: number | null;
  logType?: AuditLogType;
  range?: [unknown, unknown];
};

const renderStatusPill = (label?: string | null) => {
  if (!label) {
    return '-';
  }

  const normalizedLabel = label.toUpperCase();
  const colorMap: Record<string, string> = {
    SUCCESS: '#52c41a',
    FAIL: '#ff4d4f',
    FAILED: '#ff4d4f',
    ERROR: '#ff4d4f',
    RUNNING: '#1677ff',
    ENABLED: '#52c41a',
    DISABLED: '#d9d9d9',
    ONLINE: '#52c41a',
    OFFLINE: '#d9d9d9',
  };
  const displayMap: Record<string, string> = {
    SUCCESS: '成功',
    FAIL: '失败',
    FAILED: '失败',
    ERROR: '异常',
    RUNNING: '运行中',
    ENABLED: '已启用',
    DISABLED: '已禁用',
    ONLINE: '已上线',
    OFFLINE: '已关闭',
  };
  const color = colorMap[normalizedLabel] || '#1677ff';
  const displayLabel = displayMap[normalizedLabel] || label;

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <span
        style={{
          width: 8,
          height: 8,
          borderRadius: '50%',
          backgroundColor: color,
          flexShrink: 0,
        }}
      />
      <span>{displayLabel}</span>
    </span>
  );
};

export default () => {
  const [form] = Form.useForm();
  const [logType, setLogType] = useState<AuditLogType>('login');
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [advancedVisible, setAdvancedVisible] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<AuditLogRecord | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const submitQuery = async (values: AuditQueryValues) => {
    const next: Record<string, unknown> = {};
    const nextLogType = values.logType || logType;
    if (values.username) {
      next.username = values.username.trim();
    }
    if (typeof values.tenantId === 'number' && Number.isFinite(values.tenantId)) {
      next.tenantId = values.tenantId;
    }
    if (values.range?.length === 2) {
      const start = values.range[0] as { format?: (fmt: string) => string };
      const end = values.range[1] as { format?: (fmt: string) => string };
      if (start.format && end.format) {
        next.startTime = start.format('YYYY-MM-DD HH:mm:ss');
        next.endTime = end.format('YYYY-MM-DD HH:mm:ss');
      }
    }
    setLogType(nextLogType);
    setQuery(next);
  };

  const resetQuery = () => {
    form.resetFields();
    setQuery({});
    setLogType('login');
    setAdvancedVisible(false);
  };

  const columns = useMemo<ProColumns<AuditLogRecord>[]>(
    () =>
      logType === 'login'
        ? [
            { title: '用户名', dataIndex: 'username', width: 140 },
            { title: '租户', dataIndex: 'tenantId', width: 100 },
            {
              title: '结果',
              dataIndex: 'logResult',
              width: 120,
              render: (_, record) => renderStatusPill(record.logResult),
            },
            { title: '原因', dataIndex: 'failReason' },
            { title: '时间', dataIndex: 'createdAt', width: 180 },
            {
              title: '操作',
              key: 'actions',
              width: 120,
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
            { title: '模块', dataIndex: 'moduleName', width: 140 },
            { title: '操作', dataIndex: 'actionName', width: 140 },
            { title: '类型', dataIndex: 'operationType', width: 120 },
            { title: '详情', dataIndex: 'detailMessage' },
            { title: '时间', dataIndex: 'createdAt', width: 180 },
            {
              title: '操作',
              key: 'actions',
              width: 120,
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
            ],
    [logType],
  );

  const refreshList = () => {
    setQuery((current) => ({ ...current }));
  };

  return (
    <PageContainer
      className="saas-management-page saas-crud-page saas-audit-page"
      ghost
      breadcrumbRender={false}
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <div className="saas-audit-page-header">
          <Breadcrumb
            items={[
              { title: '列表页' },
              { title: '审计中心' },
            ]}
          />
          <Typography.Title level={2} className="saas-audit-page-title">
            审计中心
          </Typography.Title>
        </div>

        <Card className="saas-query-panel">
          <Form
            form={form}
            layout="vertical"
            onFinish={submitQuery}
            onReset={resetQuery}
          >
            <div className="saas-audit-query-row">
              <div className="saas-audit-query-field">
                <Form.Item name="username" label="用户名">
                  <Input allowClear placeholder="请输入用户名" />
                </Form.Item>
              </div>
              <div className="saas-audit-query-field">
                <Form.Item name="range" label="时间范围">
                  <DatePicker.RangePicker showTime style={{ width: '100%' }} />
                </Form.Item>
              </div>
              <div className="saas-audit-query-actions">
                <Space size={12} align="center">
                  <Button htmlType="reset">重置</Button>
                  <Button type="primary" htmlType="submit">
                    查询
                  </Button>
                  <Button
                    type="link"
                    onClick={() => setAdvancedVisible((current) => !current)}
                    icon={advancedVisible ? <UpOutlined /> : <DownOutlined />}
                  >
                    {advancedVisible ? '收起' : '展开'}
                  </Button>
                </Space>
              </div>
            </div>

            {advancedVisible ? (
              <div className="saas-audit-query-row saas-audit-query-row--advanced">
                <div className="saas-audit-query-field">
                  <Form.Item name="tenantId" label="租户ID">
                    <InputNumber min={1} placeholder="请输入租户 ID" style={{ width: '100%' }} controls={false} />
                  </Form.Item>
                </div>
                <div className="saas-audit-query-field">
                  <Form.Item name="logType" label="日志类型" initialValue={logType}>
                    <Select
                      options={[
                        { label: '登录日志', value: 'login' },
                        { label: '操作日志', value: 'operation' },
                      ]}
                    />
                  </Form.Item>
                </div>
                <div className="saas-audit-query-actions" />
              </div>
            ) : null}
          </Form>
        </Card>

        <Card
          className="saas-crud-table-card saas-audit-table-card"
          title={<span className="saas-audit-table-title">审计日志</span>}
          extra={
            <Space size={8} align="center">
              <Button type="primary" icon={<ReloadOutlined />} onClick={refreshList}>
                刷新
              </Button>
              <Button type="text" icon={<ColumnHeightOutlined />} aria-label="密度" />
              <Button type="text" icon={<SettingOutlined />} aria-label="设置" />
            </Space>
          }
          bodyStyle={{ minHeight: 0 }}
        >
          <ProTable<AuditLogRecord>
            rowKey="id"
            columns={columns}
            params={{ logType, ...query }}
            rowSelection={{}}
            request={async (params) => {
              const payload = {
                ...query,
                pageNo: params.current,
                pageSize: params.pageSize,
              };
              const result = logType === 'login'
                ? await auditService.loginLogs(payload, { autoRedirectOnUnauthorized: false })
                : await auditService.operationLogs(payload, { autoRedirectOnUnauthorized: false });
              return { data: result.records, success: true, total: result.total };
            }}
            search={false}
            options={false}
            toolBarRender={false}
            pagination={{ showSizeChanger: true, pageSize: 20 }}
          />
        </Card>

        <Drawer
          className="saas-detail-drawer"
          title={selectedRecord ? `日志详情 · ${selectedRecord.username || selectedRecord.moduleName || selectedRecord.id}` : '日志详情'}
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          width={720}
          destroyOnClose
        >
          {selectedRecord ? (
            <Space direction="vertical" style={{ width: '100%' }} size={16}>
              <Descriptions
                bordered
                size="small"
                column={2}
                items={[
                  { key: 'username', label: '用户名', children: selectedRecord.username || '-' },
                  { key: 'tenantId', label: '租户ID', children: selectedRecord.tenantId ?? '-' },
                  { key: 'type', label: '类型', children: selectedRecord.logType || selectedRecord.operationType || '-' },
                  { key: 'result', label: '结果', children: selectedRecord.logResult || '-' },
                  { key: 'requestId', label: 'RequestId', children: selectedRecord.requestId || '-' },
                  { key: 'traceId', label: 'TraceId', children: selectedRecord.traceId || '-' },
                  { key: 'createdAt', label: '时间', children: selectedRecord.createdAt },
                ]}
              />
              <Card className="saas-crud-info-card" size="small" title="扩展信息">
                <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                  {selectedRecord.failReason || selectedRecord.detailMessage || '无更多详情'}
                </Typography.Paragraph>
              </Card>
            </Space>
          ) : null}
        </Drawer>
      </div>
    </PageContainer>
  );
};
