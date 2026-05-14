import { Button, Drawer, Form, Input, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { siteService, type SiteContent } from '@/services/site';
import './site.css';

const ContentManagement = () => {
  const [records, setRecords] = useState<SiteContent[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [editing, setEditing] = useState<Partial<SiteContent> | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<Partial<SiteContent>>();
  const actionPermission = useActionPermission();

  const load = async (nextPage = pageNo) => {
    setLoading(true);
    try {
      const result = await siteService.contents({ pageNo: nextPage, pageSize: 10 });
      setRecords(result.records);
      setTotal(result.total);
      setPageNo(nextPage);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(1); }, []);

  const open = (record?: SiteContent) => {
    const next = record || { title: '', slug: '', bodyType: 'RICH_TEXT' };
    setEditing(next);
    form.setFieldsValue(next);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing?.id) await siteService.updateContent(editing.id, values);
    else await siteService.createContent(values);
    message.success('内容已保存');
    setEditing(null);
    await load();
  };

  return (
    <div className="site-admin-page">
      <div className="site-admin-header">
        <div>
          <h1 className="site-admin-title">内容管理</h1>
          <p className="site-admin-desc">维护新闻、公告、文章等官网内容。</p>
        </div>
        {actionPermission.can('site:content:create') ? <Button type="primary" icon={<PlusOutlined />} onClick={() => open()}>新增内容</Button> : null}
      </div>
      <div className="site-admin-card">
        <Table
          rowKey="id"
          loading={loading}
          dataSource={records}
          pagination={{ current: pageNo, total, pageSize: 10, onChange: load }}
          columns={[
            { title: '标题', dataIndex: 'title' },
            { title: '路径', dataIndex: 'slug' },
            { title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color={value === 'PUBLISHED' ? 'green' : 'blue'}>{value}</Tag> },
            {
              title: '操作',
              width: 260,
              render: (_, record) => (
                <Space>
                  {actionPermission.can('site:content:update') ? <Button type="link" onClick={() => open(record)}>编辑</Button> : null}
                  {actionPermission.can('site:content:publish') ? <Button type="link" onClick={async () => { await siteService.publishContent(record.id); await load(); }}>发布</Button> : null}
                  {actionPermission.can('site:content:publish') ? <Button type="link" onClick={async () => { await siteService.offlineContent(record.id); await load(); }}>下线</Button> : null}
                  {actionPermission.can('site:content:update') ? <Popconfirm title="确认删除？" onConfirm={async () => { await siteService.deleteContent(record.id); await load(); }}>
                    <Button type="link" danger>删除</Button>
                  </Popconfirm> : null}
                </Space>
              ),
            },
          ]}
        />
      </div>
      <Drawer title={editing?.id ? '编辑内容' : '新增内容'} open={Boolean(editing)} width={720} onClose={() => setEditing(null)} extra={<Button type="primary" onClick={save}>保存</Button>}>
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="slug" label="访问路径" rules={[{ required: true }]}><Input placeholder="/news/example" /></Form.Item>
          <Form.Item name="summary" label="摘要"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item name="coverFileId" label="封面文件 ID"><Input type="number" /></Form.Item>
          <Form.Item name="bodyType" label="正文类型"><Select options={[{ value: 'RICH_TEXT', label: '富文本' }, { value: 'JSON', label: '结构化 JSON' }]} /></Form.Item>
          <Form.Item name="bodyText" label="正文"><Input.TextArea rows={10} /></Form.Item>
          <Form.Item name="seoJson" label="SEO JSON"><Input.TextArea className="site-admin-json" rows={4} /></Form.Item>
        </Form>
      </Drawer>
    </div>
  );
};

export default ContentManagement;
