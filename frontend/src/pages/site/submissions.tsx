import { Button, Drawer, Form, Input, Select, Space, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { siteService, type SiteSubmission } from '@/services/site';
import './site.css';

const SubmissionsPage = () => {
  const [records, setRecords] = useState<SiteSubmission[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [detail, setDetail] = useState<SiteSubmission | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<{ status: string; reviewRemark?: string }>();
  const actionPermission = useActionPermission();
  const canReview = actionPermission.can('site:submission:review');

  const load = async (nextPage = pageNo) => {
    setLoading(true);
    try {
      const result = await siteService.submissions({ pageNo: nextPage, pageSize: 10 });
      setRecords(result.records);
      setTotal(result.total);
      setPageNo(nextPage);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(1); }, []);

  const open = (record: SiteSubmission) => {
    setDetail(record);
    form.setFieldsValue({ status: record.status, reviewRemark: record.reviewRemark });
  };

  const save = async () => {
    if (!detail) return;
    await siteService.reviewSubmission(detail.id, await form.validateFields());
    message.success('审核状态已更新');
    setDetail(null);
    await load();
  };

  return (
    <div className="site-admin-page">
      <div className="site-admin-header">
        <div>
          <h1 className="site-admin-title">提交记录</h1>
          <p className="site-admin-desc">查看官网表单提交内容并完成审核归档。</p>
        </div>
      </div>
      <div className="site-admin-card">
        <Table
          rowKey="id"
          loading={loading}
          dataSource={records}
          pagination={{ current: pageNo, total, pageSize: 10, onChange: load }}
          columns={[
            { title: '表单', dataIndex: 'formName' },
            { title: '提交 IP', dataIndex: 'submitterIp', width: 150 },
            { title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color={value === 'APPROVED' ? 'green' : value === 'REJECTED' ? 'red' : 'blue'}>{value}</Tag> },
            { title: '提交时间', dataIndex: 'createdAt', width: 180 },
            { title: '操作', width: 100, render: (_, record) => <Button type="link" onClick={() => open(record)}>查看</Button> },
          ]}
        />
      </div>
      <Drawer title="提交详情" open={Boolean(detail)} width={STANDARD_DRAWER_WIDTH} onClose={() => setDetail(null)} extra={canReview ? <Button type="primary" onClick={save}>保存审核</Button> : null}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Input.TextArea className="site-admin-json" rows={12} value={detail?.dataJson} readOnly />
          <Form form={form} layout="vertical" disabled={!canReview}>
            <Form.Item name="status" label="审核状态" rules={[{ required: true }]}>
              <Select options={[{ value: 'PENDING', label: '待处理' }, { value: 'APPROVED', label: '通过' }, { value: 'REJECTED', label: '驳回' }, { value: 'ARCHIVED', label: '归档' }]} />
            </Form.Item>
            <Form.Item name="reviewRemark" label="审核说明">
              <Input.TextArea rows={4} />
            </Form.Item>
          </Form>
        </Space>
      </Drawer>
    </div>
  );
};

export default SubmissionsPage;
