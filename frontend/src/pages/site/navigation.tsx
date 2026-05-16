import { Button, Drawer, Form, Input, InputNumber, Popconfirm, Select, Space, Table, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { siteService, type SiteNavigation } from '@/services/site';
import SiteAdminPage from './SiteAdminPage';
import './site.css';

const NavigationPage = () => {
  const [records, setRecords] = useState<SiteNavigation[]>([]);
  const [editing, setEditing] = useState<Partial<SiteNavigation> | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<Partial<SiteNavigation>>();
  const actionPermission = useActionPermission();

  const load = async () => {
    setLoading(true);
    try {
      setRecords(await siteService.navigation());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const open = (record?: SiteNavigation) => {
    const next = record || { linkType: 'PAGE', openType: 'SELF', status: 'VISIBLE', sortOrder: 0 };
    setEditing(next);
    form.setFieldsValue(next);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing?.id) await siteService.updateNavigation(editing.id, values);
    else await siteService.createNavigation(values);
    message.success('导航已保存');
    setEditing(null);
    await load();
  };

  return (
    <SiteAdminPage
      title="导航管理"
      description="维护官网头部导航、外链和排序。"
      extra={actionPermission.can('site:navigation:create') ? <Button type="primary" icon={<PlusOutlined />} onClick={() => open()}>
          新增导航
        </Button> : null}
    >
      <div className="site-admin-card">
        <Table
          rowKey="id"
          loading={loading}
          dataSource={records}
          pagination={false}
          columns={[
            { title: '标题', dataIndex: 'title' },
            { title: '链接类型', dataIndex: 'linkType', width: 120 },
            { title: '链接目标', dataIndex: 'linkTarget' },
            { title: '排序', dataIndex: 'sortOrder', width: 90 },
            { title: '状态', dataIndex: 'status', width: 100 },
            {
              title: '操作',
              width: 160,
              render: (_, record) => (
                <Space>
                  {actionPermission.can('site:navigation:update') ? <Button type="link" onClick={() => open(record)}>编辑</Button> : null}
                  {actionPermission.can('site:navigation:delete') ? <Popconfirm title="确认删除？" onConfirm={async () => { await siteService.deleteNavigation(record.id); await load(); }}>
                    <Button type="link" danger>删除</Button>
                  </Popconfirm> : null}
                </Space>
              ),
            },
          ]}
        />
      </div>
      <Drawer title={editing?.id ? '编辑导航' : '新增导航'} open={Boolean(editing)} width={STANDARD_DRAWER_WIDTH} onClose={() => setEditing(null)} extra={<Button type="primary" onClick={save}>保存</Button>}>
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="linkType" label="链接类型"><Select options={[{ value: 'PAGE', label: '页面' }, { value: 'URL', label: '外链' }]} /></Form.Item>
          <Form.Item name="linkTarget" label="链接目标" rules={[{ required: true }]}><Input placeholder="/about" /></Form.Item>
          <Form.Item name="openType" label="打开方式"><Select options={[{ value: 'SELF', label: '当前窗口' }, { value: 'BLANK', label: '新窗口' }]} /></Form.Item>
          <Form.Item name="sortOrder" label="排序"><InputNumber style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="status" label="状态"><Select options={[{ value: 'VISIBLE', label: '显示' }, { value: 'HIDDEN', label: '隐藏' }]} /></Form.Item>
        </Form>
      </Drawer>
    </SiteAdminPage>
  );
};

export default NavigationPage;
