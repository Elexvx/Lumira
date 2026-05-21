import { Button, Form, Input, InputNumber, Popconfirm, Select, Space, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { siteService, type SiteCategory, type SiteContent } from '@/services/site';
import SiteAdminPage, { SiteAdminDrawer, SiteAdminTable } from './SiteAdminPage';
import './site.css';

const ContentManagement = () => {
  const [records, setRecords] = useState<SiteContent[]>([]);
  const [categories, setCategories] = useState<SiteCategory[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [editing, setEditing] = useState<Partial<SiteContent> | null>(null);
  const [categoryEditing, setCategoryEditing] = useState<Partial<SiteCategory> | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<Partial<SiteContent>>();
  const [categoryForm] = Form.useForm<Partial<SiteCategory>>();
  const actionPermission = useActionPermission();

  const load = async (nextPage = pageNo) => {
    setLoading(true);
    try {
      const [result, nextCategories] = await Promise.all([
        siteService.contents({ pageNo: nextPage, pageSize: 10 }),
        siteService.categories(),
      ]);
      setRecords(result.records);
      setCategories(nextCategories);
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

  const openCategory = (record?: SiteCategory) => {
    const next = record || { code: '', name: '', sortOrder: 0, status: 'ENABLED' };
    setCategoryEditing(next);
    categoryForm.setFieldsValue(next);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing?.id) await siteService.updateContent(editing.id, values);
    else await siteService.createContent(values);
    message.success('内容已保存');
    setEditing(null);
    await load();
  };

  const saveCategory = async () => {
    const values = await categoryForm.validateFields();
    if (categoryEditing?.id) await siteService.updateCategory(categoryEditing.id, values);
    else await siteService.createCategory(values);
    message.success('分类已保存');
    setCategoryEditing(null);
    await load();
  };

  return (
    <SiteAdminPage
      title="内容管理"
      extra={actionPermission.can('site:content:create') ? <Button type="primary" icon={<PlusOutlined />} onClick={() => open()}>新增内容</Button> : null}
    >
      <div className="site-admin-card">
        <SiteAdminTable<SiteCategory>
          rowKey="id"
          dataSource={categories}
          pagination={false}
          title={() => (
            <Space>
              <span>内容分类</span>
              {actionPermission.can('site:content:create') ? <Button icon={<PlusOutlined />} onClick={() => openCategory()}>新增分类</Button> : null}
            </Space>
          )}
          columns={[
            { title: '名称', dataIndex: 'name' },
            { title: '编码', dataIndex: 'code' },
            { title: '排序', dataIndex: 'sortOrder', width: 100 },
            { title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value}</Tag> },
            {
              title: '操作',
              width: 150,
              render: (_, record) => (
                <Space>
                  {actionPermission.can('site:content:update') ? <Button type="link" onClick={() => openCategory(record)}>编辑</Button> : null}
                  {actionPermission.can('site:content:update') ? <Popconfirm title="确认删除？" onConfirm={async () => { await siteService.deleteCategory(record.id); await load(); }}>
                    <Button type="link" danger>删除</Button>
                  </Popconfirm> : null}
                </Space>
              ),
            },
          ]}
        />
      </div>
      <div className="site-admin-card">
        <SiteAdminTable<SiteContent>
          rowKey="id"
          loading={loading}
          dataSource={records}
          pagination={{ current: pageNo, total, pageSize: 10, onChange: load }}
          columns={[
            { title: '标题', dataIndex: 'title' },
            { title: '分类', dataIndex: 'categoryId', width: 140, render: (value) => categories.find((item) => item.id === value)?.name || '-' },
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
      <SiteAdminDrawer title={editing?.id ? '编辑内容' : '新增内容'} open={Boolean(editing)} onClose={() => setEditing(null)} extra={<Button type="primary" onClick={save}>保存</Button>}>
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="slug" label="访问路径" rules={[{ required: true }]}><Input placeholder="/news/example" /></Form.Item>
          <Form.Item name="categoryId" label="分类">
            <Select allowClear options={categories.map((item) => ({ value: item.id, label: item.name }))} />
          </Form.Item>
          <Form.Item name="summary" label="摘要"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item name="coverFileId" label="封面文件 ID"><Input type="number" /></Form.Item>
          <Form.Item name="bodyType" label="正文类型"><Select options={[{ value: 'RICH_TEXT', label: '富文本' }, { value: 'JSON', label: '结构化 JSON' }]} /></Form.Item>
          <Form.Item name="bodyText" label="正文"><Input.TextArea rows={10} /></Form.Item>
          <Form.Item name="seoJson" label="SEO JSON"><Input.TextArea className="site-admin-json" rows={4} /></Form.Item>
        </Form>
      </SiteAdminDrawer>
      <SiteAdminDrawer title={categoryEditing?.id ? '编辑分类' : '新增分类'} open={Boolean(categoryEditing)} onClose={() => setCategoryEditing(null)} extra={<Button type="primary" onClick={saveCategory}>保存</Button>}>
        <Form form={categoryForm} layout="vertical">
          <Form.Item name="name" label="分类名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="code" label="分类编码" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="sortOrder" label="排序"><InputNumber style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="status" label="状态"><Select options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]} /></Form.Item>
        </Form>
      </SiteAdminDrawer>
    </SiteAdminPage>
  );
};

export default ContentManagement;
