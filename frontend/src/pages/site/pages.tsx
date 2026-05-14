import { Button, Drawer, Form, Input, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { defaultBlocksJson, siteService, type SitePage } from '@/services/site';
import './site.css';

const PageManagement = () => {
  const [records, setRecords] = useState<SitePage[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<Partial<SitePage> | null>(null);
  const [form] = Form.useForm<Partial<SitePage>>();
  const actionPermission = useActionPermission();

  const load = async (nextPage = pageNo) => {
    setLoading(true);
    try {
      const result = await siteService.pages({ pageNo: nextPage, pageSize: 10 });
      setRecords(result.records);
      setTotal(result.total);
      setPageNo(nextPage);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(1);
  }, []);

  const open = (record?: SitePage) => {
    const next = record || { title: '', slug: '/', pageType: 'CUSTOM', blocksJson: defaultBlocksJson };
    setEditing(next);
    form.setFieldsValue(next);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing?.id) await siteService.updatePage(editing.id, values);
    else await siteService.createPage(values);
    message.success('页面已保存');
    setEditing(null);
    await load();
  };

  const publish = async (record: SitePage) => {
    await siteService.publishPage(record.id);
    message.success('页面已发布');
    await load();
  };

  return (
    <div className="site-admin-page">
      <div className="site-admin-header">
        <div>
          <h1 className="site-admin-title">页面管理</h1>
          <p className="site-admin-desc">以区块 JSON 管理官网页面草稿、发布和下线。</p>
        </div>
        {actionPermission.can('site:page:create') ? <Button type="primary" icon={<PlusOutlined />} onClick={() => open()}>新增页面</Button> : null}
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
            { title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color={value === 'PUBLISHED' ? 'green' : value === 'OFFLINE' ? 'default' : 'blue'}>{value}</Tag> },
            { title: '更新时间', dataIndex: 'updatedAt', width: 180 },
            {
              title: '操作',
              width: 260,
              render: (_, record) => (
                <Space>
                  {actionPermission.can('site:page:update') ? <Button type="link" onClick={() => open(record)}>编辑</Button> : null}
                  {actionPermission.can('site:page:publish') ? <Button type="link" onClick={() => publish(record)}>发布</Button> : null}
                  {actionPermission.can('site:page:publish') ? <Button type="link" onClick={async () => { await siteService.offlinePage(record.id); await load(); }}>下线</Button> : null}
                  {actionPermission.can('site:page:update') ? <Popconfirm title="确认删除？" onConfirm={async () => { await siteService.deletePage(record.id); await load(); }}>
                    <Button type="link" danger>删除</Button>
                  </Popconfirm> : null}
                </Space>
              ),
            },
          ]}
        />
      </div>
      <Drawer title={editing?.id ? '编辑页面' : '新增页面'} open={Boolean(editing)} width={760} onClose={() => setEditing(null)} extra={<Button type="primary" onClick={save}>保存</Button>}>
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="slug" label="访问路径" rules={[{ required: true }]}><Input placeholder="/about" /></Form.Item>
          <Form.Item name="pageType" label="页面类型"><Select options={[{ value: 'HOME', label: '首页' }, { value: 'CUSTOM', label: '自定义' }]} /></Form.Item>
          <Form.Item name="seoJson" label="SEO JSON"><Input.TextArea className="site-admin-json" rows={4} /></Form.Item>
          <Form.Item name="blocksJson" label="区块 JSON" rules={[{ required: true }]}><Input.TextArea className="site-admin-json" rows={14} /></Form.Item>
        </Form>
      </Drawer>
    </div>
  );
};

export default PageManagement;
