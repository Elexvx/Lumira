import { Button, Form, Input, Popconfirm, Select, Space, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { siteService, type SitePage } from '@/services/site';
import SiteAdminPage, { SiteAdminDrawer, SiteAdminTable } from './SiteAdminPage';
import './site.css';

const pageTypeOptions = [
  { value: 'CUSTOM', label: '自定义页面' },
  { value: 'LANDING', label: '落地页' },
  { value: 'ARTICLE_LIST', label: '内容列表' },
];

const PageManagement = () => {
  const [records, setRecords] = useState<SitePage[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [editing, setEditing] = useState<Partial<SitePage> | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<Partial<SitePage>>();
  const actionPermission = useActionPermission();

  const load = async (nextPage = pageNo) => {
    setLoading(true);
    try {
      const result = await siteService.pages({ pageNo: nextPage, pageSize: 10 }, { autoRedirectOnUnauthorized: false });
      setRecords(result.records || []);
      setTotal(result.total);
      setPageNo(nextPage);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(1); }, []);

  const open = (record?: SitePage) => {
    const next = record || {
      title: '',
      slug: '',
      pageType: 'CUSTOM',
      blocksJson: '[]',
      seoJson: '{}',
    };
    setEditing(next);
    form.setFieldsValue(next);
  };

  const save = async () => {
    const values = await form.validateFields();
    const payload = {
      ...values,
      pageType: values.pageType || 'CUSTOM',
      blocksJson: values.blocksJson || '[]',
    };
    if (editing?.id) {
      await siteService.updatePage(editing.id, payload, { autoRedirectOnUnauthorized: false });
    } else {
      await siteService.createPage(payload, { autoRedirectOnUnauthorized: false });
    }
    message.success('页面已保存');
    setEditing(null);
    await load();
  };

  return (
    <SiteAdminPage
      title="页面管理"
      extra={actionPermission.can('site:page:create') ? <Button type="primary" icon={<PlusOutlined />} onClick={() => open()}>新增页面</Button> : null}
    >
      <div className="site-admin-card">
        <SiteAdminTable<SitePage>
          rowKey="id"
          loading={loading}
          dataSource={records}
          pagination={{ current: pageNo, total, pageSize: 10, onChange: load }}
          columns={[
            { title: '页面标题', dataIndex: 'title', ellipsis: true },
            { title: '访问路径', dataIndex: 'slug', ellipsis: true },
            { title: '页面类型', dataIndex: 'pageType', width: 140 },
            { title: '草稿版本', dataIndex: 'currentDraftVersion', width: 110, search: false },
            { title: '发布版本', dataIndex: 'currentPublishedVersion', width: 110, search: false },
            { title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color={value === 'PUBLISHED' ? 'green' : 'blue'}>{value}</Tag> },
            {
              title: '操作',
              width: 280,
              render: (_, record) => (
                <Space>
                  {actionPermission.can('site:page:update') ? <Button type="link" onClick={() => open(record)}>编辑</Button> : null}
                  {actionPermission.can('site:page:publish') ? <Button type="link" onClick={async () => { await siteService.publishPage(record.id, { autoRedirectOnUnauthorized: false }); await load(); }}>发布</Button> : null}
                  {actionPermission.can('site:page:publish') ? <Button type="link" onClick={async () => { await siteService.offlinePage(record.id, { autoRedirectOnUnauthorized: false }); await load(); }}>下线</Button> : null}
                  {actionPermission.can('site:page:update') ? (
                    <Popconfirm title="确认删除？" onConfirm={async () => { await siteService.deletePage(record.id, { autoRedirectOnUnauthorized: false }); await load(); }}>
                      <Button type="link" danger>删除</Button>
                    </Popconfirm>
                  ) : null}
                </Space>
              ),
            },
          ]}
        />
      </div>
      <SiteAdminDrawer title={editing?.id ? '编辑页面' : '新增页面'} open={Boolean(editing)} onClose={() => setEditing(null)} extra={<Button type="primary" onClick={save}>保存</Button>}>
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="页面标题" rules={[{ required: true, message: '请输入页面标题' }]}><Input /></Form.Item>
          <Form.Item name="slug" label="访问路径" rules={[{ required: true, message: '请输入访问路径' }]}><Input placeholder="/about" /></Form.Item>
          <Form.Item name="pageType" label="页面类型"><Select options={pageTypeOptions} /></Form.Item>
          <Form.Item name="blocksJson" label="页面区块 JSON" rules={[{ required: true, message: '请输入页面区块 JSON' }]}><Input.TextArea className="site-admin-json" rows={10} /></Form.Item>
          <Form.Item name="seoJson" label="SEO JSON"><Input.TextArea className="site-admin-json" rows={4} /></Form.Item>
        </Form>
      </SiteAdminDrawer>
    </SiteAdminPage>
  );
};

export default PageManagement;
