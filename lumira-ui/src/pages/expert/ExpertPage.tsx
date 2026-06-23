import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Form, Input, InputNumber, Modal, Select, Space, Tag, Typography } from 'antd';
import type { FormInstance } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { history, useLocation } from '@umijs/max';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useResponsive } from '@/hooks/useResponsive';
import { createExpert, deleteExpert, listExperts, updateExpert } from '@/services/expert/api';
import type { ExpertRecord, ExpertStatus, ExpertUpsertPayload } from '@/services/expert/types';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';
import './ExpertPage.css';

type ExpertFormValues = ExpertUpsertPayload;

const statusOptions: Array<{ label: string; value: ExpertStatus }> = [
  { label: '启用', value: 'active' },
  { label: '停用', value: 'inactive' },
];

const statusText: Record<ExpertStatus, string> = {
  active: '启用',
  inactive: '停用',
};

const statusColor: Record<ExpertStatus, string> = {
  active: 'green',
  inactive: 'default',
};

const trimOptional = (value?: string) => {
  const trimmed = value?.trim();
  return trimmed || undefined;
};

const normalizePayload = (values: ExpertFormValues): ExpertUpsertPayload => ({
  code: trimOptional(values.code),
  name: values.name.trim(),
  title: trimOptional(values.title),
  organization: trimOptional(values.organization),
  position: trimOptional(values.position),
  expertise: values.expertise.trim(),
  phone: trimOptional(values.phone),
  email: trimOptional(values.email),
  avatarUrl: trimOptional(values.avatarUrl),
  bio: trimOptional(values.bio),
  tags: trimOptional(values.tags),
  status: values.status || 'active',
  sort: values.sort ?? 100,
});

const splitTags = (tags?: string | null) =>
  (tags || '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);

const ExpertForm = ({ form }: { form: FormInstance<ExpertFormValues> }) => (
  <Form<ExpertFormValues>
    form={form}
    layout="vertical"
    initialValues={{
      status: 'active',
      sort: 100,
    }}
  >
    <Form.Item name="name" label="专家姓名" rules={[{ required: true, message: '请输入专家姓名' }]}>
      <Input maxLength={64} />
    </Form.Item>
    <Space size="middle" style={{ width: '100%' }} align="start">
      <Form.Item name="title" label="专家头衔" style={{ flex: 1 }}>
        <Input maxLength={128} placeholder="例如 教授 / 高级工程师" />
      </Form.Item>
      <Form.Item name="position" label="职务" style={{ flex: 1 }}>
        <Input maxLength={128} />
      </Form.Item>
    </Space>
    <Form.Item name="organization" label="所属机构">
      <Input maxLength={128} />
    </Form.Item>
    <Form.Item name="expertise" label="专业领域" rules={[{ required: true, message: '请输入专业领域' }]}>
      <Input maxLength={255} placeholder="例如 人工智能、产业投资、智能制造" />
    </Form.Item>
    <Space size="middle" style={{ width: '100%' }} align="start">
      <Form.Item name="phone" label="联系电话" style={{ flex: 1 }}>
        <Input maxLength={64} />
      </Form.Item>
      <Form.Item name="email" label="邮箱" style={{ flex: 1 }} rules={[{ type: 'email', message: '请输入有效邮箱' }]}>
        <Input maxLength={128} />
      </Form.Item>
    </Space>
    <Form.Item name="avatarUrl" label="头像 URL">
      <Input maxLength={512} />
    </Form.Item>
    <Form.Item name="bio" label="专家简介">
      <Input.TextArea rows={4} maxLength={1000} />
    </Form.Item>
    <Form.Item name="tags" label="标签">
      <Input maxLength={1000} placeholder="多个标签用英文逗号分隔" />
    </Form.Item>
    <Space size="middle" style={{ width: '100%' }} align="start">
      <Form.Item name="status" label="状态" rules={[{ required: true }]} style={{ flex: 1 }}>
        <Select options={statusOptions} />
      </Form.Item>
      <Form.Item name="sort" label="排序" style={{ flex: 1 }}>
        <InputNumber min={0} max={9999} style={{ width: '100%' }} />
      </Form.Item>
    </Space>
    <Form.Item name="code" label="专家编码">
      <Input maxLength={64} placeholder="不填时自动生成" />
    </Form.Item>
  </Form>
);

const ExpertManagementView = () => {
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [form] = Form.useForm<ExpertFormValues>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<ExpertRecord>();
  const [saving, setSaving] = useState(false);

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingRecord(undefined);
  };

  const openCreateDrawer = () => {
    setEditingRecord(undefined);
    form.resetFields();
    form.setFieldsValue({ status: 'active', sort: 100 });
    setDrawerOpen(true);
  };

  const openEditDrawer = (record: ExpertRecord) => {
    setEditingRecord(record);
    form.resetFields();
    form.setFieldsValue({
      ...record,
      title: record.title || undefined,
      organization: record.organization || undefined,
      position: record.position || undefined,
      phone: record.phone || undefined,
      email: record.email || undefined,
      avatarUrl: record.avatarUrl || undefined,
      bio: record.bio || undefined,
      tags: record.tags || undefined,
    });
    setDrawerOpen(true);
  };

  const saveExpert = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editingRecord) {
        await updateExpert(editingRecord.id, normalizePayload(values));
        message.success('专家已更新');
      } else {
        await createExpert(normalizePayload(values));
        message.success('专家已新增');
      }
      closeDrawer();
      actionRef.current?.reload();
    } catch (error) {
      showErrorMessage(error, '专家保存失败');
    } finally {
      setSaving(false);
    }
  };

  const columns = useMemo<ProColumns<ExpertRecord>[]>(
    () => [
      {
        title: '专家',
        dataIndex: 'keyword',
        render: (_, record) => (
          <Space className="expert-name-cell" direction="vertical" size={0}>
            <Typography.Text strong>{record.name}</Typography.Text>
            <span className="expert-name-cell__meta">{record.code}</span>
          </Space>
        ),
      },
      {
        title: '头衔',
        dataIndex: 'title',
        search: false,
        render: (value) => value || '-',
      },
      {
        title: '所属机构',
        dataIndex: 'organization',
        search: false,
        ellipsis: true,
        render: (value) => value || '-',
      },
      {
        title: '职务',
        dataIndex: 'position',
        search: false,
        render: (value) => value || '-',
      },
      {
        title: '专业领域',
        dataIndex: 'expertise',
        search: false,
        ellipsis: true,
      },
      {
        title: '标签',
        dataIndex: 'tags',
        search: false,
        render: (_, record) => (
          <Space className="expert-tags" size={[4, 4]} wrap>
            {splitTags(record.tags).slice(0, 4).map((tag) => (
              <Tag key={tag} color="blue">
                {tag}
              </Tag>
            ))}
            {!splitTags(record.tags).length ? '-' : null}
          </Space>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueType: 'select',
        valueEnum: {
          active: { text: '启用' },
          inactive: { text: '停用' },
        },
        width: 100,
        render: (_, record) => <Tag color={statusColor[record.status]}>{statusText[record.status]}</Tag>,
      },
      {
        title: '排序',
        dataIndex: 'sort',
        search: false,
        width: 80,
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        search: false,
        width: 172,
        render: (value) => value || '-',
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 148,
        align: 'right',
        className: 'saas-table-action-column',
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={actionPermission.buildTableActions([
              {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined />,
                permission: 'expert:update',
                onClick: () => openEditDrawer(record),
              },
              {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined />,
                permission: 'expert:delete',
                danger: true,
                onClick: () => {
                  Modal.confirm({
                    title: '确认删除该专家？',
                    content: `删除后专家「${record.name}」不会再出现在专家库中。`,
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await deleteExpert(record.id);
                      message.success('专家已删除');
                      actionRef.current?.reload();
                    },
                  });
                },
              },
            ])}
          />
        ),
      },
    ],
    [actionPermission, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <ManagementPage title="专家库">
      <ManagementPageBody className="expert-page">
        <ManagementTable<ExpertRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          scroll={{ x: 1180 }}
          request={async (params) => {
            const response = await listExperts({
              keyword: typeof params.keyword === 'string' ? params.keyword : undefined,
              status: params.status as ExpertStatus | undefined,
              pageNo: params.current,
              pageSize: params.pageSize,
            });
            return {
              data: response.records,
              total: response.total,
              success: true,
            };
          }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          toolBarRender={() =>
            actionPermission.buildToolbarActions([
              {
                permission: 'expert:create',
                value: (
                  <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreateDrawer}>
                    新增专家
                  </Button>
                ),
              },
            ])
          }
        />
      </ManagementPageBody>

      <ManagementDrawer
        title={editingRecord ? '编辑专家' : '新增专家'}
        open={drawerOpen}
        onClose={closeDrawer}
        destroyOnHidden
        footerActions={[
          { key: 'cancel', label: '取消', onClick: closeDrawer },
          {
            key: 'save',
            label: '保存',
            type: 'primary',
            loading: saving,
            onClick: () => void saveExpert(),
          },
        ]}
      >
        <ExpertForm form={form} />
      </ManagementDrawer>
    </ManagementPage>
  );
};

const ExpertPage = () => {
  const location = useLocation();

  useEffect(() => {
    if (location.pathname === '/experts') {
      history.replace('/experts/management');
    }
  }, [location.pathname]);

  return <ExpertManagementView />;
};

export default ExpertPage;
