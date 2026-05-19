import { Button, Form, Input, Popconfirm, Select, Space, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { siteService, type SiteForm } from '@/services/site';
import SiteAdminPage, { SiteAdminDrawer, SiteAdminTable } from './SiteAdminPage';
import './site.css';

const FormManagement = () => {
  const [records, setRecords] = useState<SiteForm[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [editing, setEditing] = useState<Partial<SiteForm> | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<Partial<SiteForm>>();
  const actionPermission = useActionPermission();

  const load = async (nextPage = pageNo) => {
    setLoading(true);
    try {
      const result = await siteService.forms({ pageNo: nextPage, pageSize: 10 }, { autoRedirectOnUnauthorized: false });
      setRecords(result.records || []);
      setTotal(result.total);
      setPageNo(nextPage);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(1); }, []);

  const open = (record?: SiteForm) => {
    const next = record || { code: '', name: '', submitPolicy: 'PUBLIC', schemaJson: '[]', notificationJson: '{}', status: 'ENABLED' };
    setEditing(next);
    form.setFieldsValue(next);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing?.id) await siteService.updateForm(editing.id, values, { autoRedirectOnUnauthorized: false });
    else await siteService.createForm(values, { autoRedirectOnUnauthorized: false });
    message.success('表单已保存');
    setEditing(null);
    await load();
  };

  return (
    <SiteAdminPage
      title="表单管理"
      extra={actionPermission.can('site:form:create') ? <Button type="primary" icon={<PlusOutlined />} onClick={() => open()}>新增表单</Button> : null}
    >
      <div className="site-admin-card">
        <SiteAdminTable<SiteForm>
          rowKey="id"
          loading={loading}
          dataSource={records}
          pagination={{ current: pageNo, total, pageSize: 10, onChange: load }}
          columns={[
            { title: '表单编码', dataIndex: 'code' },
            { title: '表单名称', dataIndex: 'name' },
            { title: '提交策略', dataIndex: 'submitPolicy', width: 120 },
            { title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value}</Tag> },
            {
              title: '操作',
              width: 180,
              render: (_, record) => (
                <Space>
                  {actionPermission.can('site:form:update') ? <Button type="link" onClick={() => open(record)}>编辑</Button> : null}
                  {actionPermission.can('site:form:delete') ? (
                    <Popconfirm title="确认删除？" onConfirm={async () => { await siteService.deleteForm(record.id, { autoRedirectOnUnauthorized: false }); await load(); }}>
                      <Button type="link" danger>删除</Button>
                    </Popconfirm>
                  ) : null}
                </Space>
              ),
            },
          ]}
        />
      </div>
      <SiteAdminDrawer title={editing?.id ? '编辑表单' : '新增表单'} open={Boolean(editing)} onClose={() => setEditing(null)} extra={<Button type="primary" onClick={save}>保存</Button>}>
        <Form form={form} layout="vertical">
          <Form.Item name="code" label="表单编码" rules={[{ required: true, message: '请输入表单编码' }]}><Input /></Form.Item>
          <Form.Item name="name" label="表单名称" rules={[{ required: true, message: '请输入表单名称' }]}><Input /></Form.Item>
          <Form.Item name="submitPolicy" label="提交策略"><Select options={[{ value: 'PUBLIC', label: '公开提交' }, { value: 'LOGIN_REQUIRED', label: '登录后提交' }]} /></Form.Item>
          <Form.Item name="schemaJson" label="字段结构 JSON" rules={[{ required: true, message: '请输入字段结构 JSON' }]}><Input.TextArea className="site-admin-json" rows={10} /></Form.Item>
          <Form.Item name="notificationJson" label="通知配置 JSON"><Input.TextArea className="site-admin-json" rows={4} /></Form.Item>
          <Form.Item name="status" label="状态"><Select options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]} /></Form.Item>
        </Form>
      </SiteAdminDrawer>
    </SiteAdminPage>
  );
};

export default FormManagement;
