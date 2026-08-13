import { DeleteOutlined, EditOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Avatar, Button, Form, Input, InputNumber, Modal, Select, Space, Tag, Typography, Upload } from 'antd';
import type { FormInstance } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { history, useLocation } from '@umijs/max';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useResponsive } from '@/hooks/useResponsive';
import { useDictOptions } from '@/hooks/useDictOptions';
import { createExpert, deleteExpert, listExperts, updateExpert, uploadExpertAvatar } from '@/services/expert/api';
import type { ExpertApprovalStatus, ExpertRecord, ExpertStatus, ExpertUpsertPayload } from '@/services/expert/types';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { trimString, validateOptionalChinaIdCard, validateOptionalChinaMobile } from '@/utils/validators';
import './ExpertPage.css';

export type ExpertFormValues = Omit<ExpertUpsertPayload, 'expertise' | 'tags'> & {
  expertise?: string[];
  tags?: string[];
};
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

const approvalStatusText: Record<ExpertApprovalStatus, string> = {
  PENDING: '待审批',
  RUNNING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
};

const approvalStatusColor: Record<ExpertApprovalStatus, string> = {
  PENDING: 'blue',
  RUNNING: 'processing',
  APPROVED: 'green',
  REJECTED: 'red',
};

const expertTitleFallbackOptions = [
  { label: '教授', value: '教授' },
  { label: '副教授', value: '副教授' },
  { label: '研究员', value: '研究员' },
  { label: '高级工程师', value: '高级工程师' },
  { label: '行业专家', value: '行业专家' },
];

const expertPositionFallbackOptions = [
  { label: '主任', value: '主任' },
  { label: '院长', value: '院长' },
  { label: '总工程师', value: '总工程师' },
  { label: '技术负责人', value: '技术负责人' },
  { label: '投资合伙人', value: '投资合伙人' },
];

const expertExpertiseFallbackOptions = [
  { label: '人工智能', value: '人工智能' },
  { label: '智能制造', value: '智能制造' },
  { label: '产业投资', value: '产业投资' },
  { label: '数字经济', value: '数字经济' },
  { label: '科技成果转化', value: '科技成果转化' },
];

const expertTagFallbackOptions = [
  { label: '评审专家', value: '评审专家' },
  { label: '导师', value: '导师' },
  { label: '产业资源', value: '产业资源' },
  { label: '投融资', value: '投融资' },
  { label: '技术顾问', value: '技术顾问' },
];

const EXPERT_NAME_PATTERN = /^[\u4e00-\u9fa5A-Za-z·\s]{2,64}$/;
const OPTIONAL_PHONE_PATTERN = /^(?:1[3-9]\d{9}|0\d{2,3}-?\d{7,8}(?:-\d{1,6})?)$/;

const trimOptional = (value?: string) => {
  const trimmed = value?.trim();
  return trimmed || undefined;
};

const joinOptions = (values?: string[]) => values?.map((value) => value.trim()).filter(Boolean).join(',') || undefined;

export const normalizeExpertPayload = (values: ExpertFormValues): ExpertUpsertPayload => ({
  code: trimOptional(values.code),
  name: values.name.trim(),
  title: trimOptional(values.title),
  organization: trimOptional(values.organization),
  position: trimOptional(values.position),
  expertise: joinOptions(values.expertise) || '',
  phone: trimOptional(values.phone),
  mobile: trimOptional(values.mobile),
  idCardNumber: trimOptional(values.idCardNumber),
  email: trimOptional(values.email),
  avatarUrl: trimOptional(values.avatarUrl),
  bio: trimOptional(values.bio),
  tags: joinOptions(values.tags),
  status: values.status || 'active',
  sort: values.sort ?? 100,
});

const splitTags = (tags?: string | null) =>
  (tags || '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);

const validateOptionalPhone = async (_: unknown, value?: string) => {
  const normalizedValue = value?.trim();
  if (!normalizedValue || OPTIONAL_PHONE_PATTERN.test(normalizedValue)) {
    return;
  }
  throw new Error('请输入有效联系电话');
};

export const ExpertForm = ({
  form,
  uploadingAvatar,
  onAvatarUpload,
}: {
  form: FormInstance<ExpertFormValues>;
  uploadingAvatar: boolean;
  onAvatarUpload: (file: File) => Promise<void>;
}) => {
  const { options: titleOptions, loading: titleLoading } = useDictOptions('aiadc_expert_title', expertTitleFallbackOptions);
  const { options: positionOptions, loading: positionLoading } = useDictOptions('aiadc_expert_position', expertPositionFallbackOptions);
  const { options: expertiseOptions, loading: expertiseLoading } = useDictOptions('aiadc_expert_expertise', expertExpertiseFallbackOptions);
  const { options: tagOptions, loading: tagLoading } = useDictOptions('aiadc_expert_tag', expertTagFallbackOptions);

  return (
    <Form<ExpertFormValues>
      form={form}
      layout="vertical"
      initialValues={{
        status: 'active',
        sort: 100,
      }}
    >
      <div className="expert-form-grid">
        <Form.Item
          name="name"
          label="专家姓名"
          className="expert-form-grid__full"
          normalize={trimString}
          rules={[
            { required: true, message: '请输入专家姓名' },
            { pattern: EXPERT_NAME_PATTERN, message: '专家姓名只能包含中文、英文字母、空格和间隔号' },
          ]}
        >
          <Input maxLength={64} placeholder="例如 张三" />
        </Form.Item>

        <Form.Item name="title" label="专家头衔" normalize={trimString}>
          <Select allowClear showSearch loading={titleLoading} options={titleOptions} placeholder="请选择专家头衔" optionFilterProp="label" />
        </Form.Item>

        <Form.Item name="position" label="职务" normalize={trimString}>
          <Select allowClear showSearch loading={positionLoading} options={positionOptions} placeholder="请选择职务" optionFilterProp="label" />
        </Form.Item>

        <Form.Item name="organization" label="所属机构" className="expert-form-grid__full" normalize={trimString}>
          <Input maxLength={128} />
        </Form.Item>

        <Form.Item name="expertise" label="专业领域" className="expert-form-grid__full" rules={[{ required: true, message: '请选择专业领域' }]}>
          <Select
            mode="multiple"
            allowClear
            showSearch
            loading={expertiseLoading}
            options={expertiseOptions}
            placeholder="请选择专业领域"
            optionFilterProp="label"
          />
        </Form.Item>

        <Form.Item name="phone" label="联系电话" rules={[{ validator: validateOptionalPhone }]} normalize={trimString}>
          <Input maxLength={64} placeholder="座机或手机号" />
        </Form.Item>

        <Form.Item name="mobile" label="手机号码" rules={[{ validator: validateOptionalChinaMobile }]} normalize={trimString}>
          <Input maxLength={32} placeholder="11 位手机号" />
        </Form.Item>

        <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '请输入有效邮箱' }]} normalize={trimString}>
          <Input maxLength={128} />
        </Form.Item>

        <Form.Item name="idCardNumber" label="身份证号码" rules={[{ validator: validateOptionalChinaIdCard }]} normalize={trimString}>
          <Input maxLength={32} />
        </Form.Item>

        <Form.Item name="avatarUrl" hidden>
          <Input />
        </Form.Item>

        <Form.Item label="头像" className="expert-form-grid__full">
          <Form.Item noStyle shouldUpdate={(previous, next) => previous.avatarUrl !== next.avatarUrl}>
            {({ getFieldValue }) => {
              const avatarUrl = getFieldValue('avatarUrl');
              return (
                <Space align="center" size="middle" wrap>
                  <Avatar size={64} src={avatarUrl ? normalizeUploadUrl(avatarUrl) : undefined}>
                    {form.getFieldValue('name')?.slice(0, 1) || '专'}
                  </Avatar>
                  <Upload
                    accept="image/*"
                    showUploadList={false}
                    beforeUpload={async (file) => {
                      if (!file.type.startsWith('image/')) {
                        message.warning('请上传图片文件');
                        return Upload.LIST_IGNORE;
                      }
                      await onAvatarUpload(file);
                      return Upload.LIST_IGNORE;
                    }}
                  >
                    <Button icon={<UploadOutlined />} loading={uploadingAvatar}>
                      上传头像
                    </Button>
                  </Upload>
                  {avatarUrl ? (
                    <Button type="link" danger onClick={() => form.setFieldValue('avatarUrl', undefined)}>
                      清除
                    </Button>
                  ) : null}
                </Space>
              );
            }}
          </Form.Item>
        </Form.Item>

        <Form.Item name="bio" label="专家简介" className="expert-form-grid__full" normalize={trimString}>
          <Input.TextArea rows={4} maxLength={1000} showCount />
        </Form.Item>

        <Form.Item name="tags" label="标签" className="expert-form-grid__full">
          <Select mode="multiple" allowClear showSearch loading={tagLoading} options={tagOptions} placeholder="请选择标签" optionFilterProp="label" />
        </Form.Item>

        <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
          <Select options={statusOptions} />
        </Form.Item>

        <Form.Item name="sort" label="排序">
          <InputNumber min={0} max={9999} style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item name="code" label="专家编码" className="expert-form-grid__full" normalize={trimString}>
          <Input maxLength={64} placeholder="不填时自动生成" />
        </Form.Item>
      </div>
    </Form>
  );
};

const ExpertManagementView = () => {
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [form] = Form.useForm<ExpertFormValues>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<ExpertRecord>();
  const [saving, setSaving] = useState(false);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);

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

  const openEditDrawer = useCallback((record: ExpertRecord) => {
    setEditingRecord(record);
    form.resetFields();
    form.setFieldsValue({
      ...record,
      expertise: splitTags(record.expertise),
      title: record.title || undefined,
      organization: record.organization || undefined,
      position: record.position || undefined,
      phone: record.phone || undefined,
      mobile: record.mobile || undefined,
      idCardNumber: record.idCardNumber || undefined,
      email: record.email || undefined,
      avatarUrl: record.avatarUrl || undefined,
      bio: record.bio || undefined,
      tags: splitTags(record.tags),
    });
    setDrawerOpen(true);
  }, [form]);

  const handleAvatarUpload = async (file: File) => {
    setUploadingAvatar(true);
    try {
      const avatarUrl = await uploadExpertAvatar(file);
      form.setFieldValue('avatarUrl', avatarUrl);
      message.success('头像已上传');
    } catch (error) {
      showErrorMessage(error, '头像上传失败');
    } finally {
      setUploadingAvatar(false);
    }
  };

  const saveExpert = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editingRecord) {
        await updateExpert(editingRecord.id, normalizeExpertPayload(values));
        message.success('专家已更新');
      } else {
        const created = await createExpert(normalizeExpertPayload(values));
        message.success('专家已新增');
        if (created.initialPassword) {
          const username = `expert_${created.code.replace(/[^A-Za-z0-9_-]/g, '_')}`;
          Modal.info({
            title: '专家账号已生成',
            content: (
              <Space direction="vertical" size={8}>
                <Typography.Text>登录账号：{username}</Typography.Text>
                <Typography.Text copyable strong>
                  初始密码：{created.initialPassword}
                </Typography.Text>
              </Space>
            ),
          });
        }
      }
      closeDrawer();
      actionRef.current?.reload();
    } catch (error) {
      showErrorMessage(error, '专家保存失败');
    } finally {
      setSaving(false);
    }
  };

  const columns = useMemo<ProColumns<ExpertRecord>[]>(() => {
    const baseColumns: ProColumns<ExpertRecord>[] = [
      {
        title: '专家',
        dataIndex: 'keyword',
        fieldProps: {
          placeholder: '输入专家姓名/编码/机构/领域',
        },
        render: (_, record) => (
          <Space className="expert-name-cell" direction="vertical" size={0}>
            <Typography.Text strong>{record.name}</Typography.Text>
            <span className="expert-name-cell__meta">{record.code}</span>
          </Space>
        ),
      },
      {
        title: '赛事查询',
        dataIndex: 'competitionKeyword',
        hideInTable: true,
        fieldProps: {
          placeholder: '输入赛事名称/编码/主办方',
        },
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
        title: '审批',
        dataIndex: 'approvalStatus',
        valueType: 'select',
        valueEnum: {
          PENDING: { text: '待审批' },
          RUNNING: { text: '审批中' },
          APPROVED: { text: '已通过' },
          REJECTED: { text: '已驳回' },
        },
        width: 120,
        render: (_, record) => {
          const approvalStatus = (record.approvalStatus || 'APPROVED') as ExpertApprovalStatus;
          return <Tag color={approvalStatusColor[approvalStatus]}>{approvalStatusText[approvalStatus]}</Tag>;
        },
      },
      {
        title: '账号',
        dataIndex: 'accountStatus',
        search: false,
        width: 120,
        render: (_, record) => (record.userId ? <Tag color="green">{record.accountStatus || 'ENABLED'}</Tag> : <Tag>未生成</Tag>),
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
    ];

    return [
      ...baseColumns,
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
    ];
  }, [actionPermission, openEditDrawer, responsive.isDesktop, responsive.isMobile]);

  return (
    <ManagementPage title="专家管理">
      <ManagementPageBody className="expert-page">
        <ManagementTable<ExpertRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          scroll={{ x: 1180 }}
          request={async (params) => {
            const response = await listExperts({
              keyword:
                typeof params.keyword === 'string'
                  ? params.keyword
                  : typeof params.competitionKeyword === 'string'
                    ? params.competitionKeyword
                    : undefined,
              status: params.status as ExpertStatus | undefined,
              approvalStatus: params.approvalStatus as ExpertApprovalStatus | undefined,
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
        <ExpertForm form={form} uploadingAvatar={uploadingAvatar} onAvatarUpload={handleAvatarUpload} />
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
