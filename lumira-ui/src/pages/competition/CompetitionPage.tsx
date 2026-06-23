import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Switch, Tag, Typography } from 'antd';
import type { FormInstance } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { useEffect, useMemo, useRef, useState } from 'react';
import { history, useLocation } from '@umijs/max';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useResponsive } from '@/hooks/useResponsive';
import { createCompetition, deleteCompetition, listCompetitions, updateCompetition } from '@/services/competition/api';
import type { CompetitionLocale, CompetitionRecord, CompetitionStatus, CompetitionUpsertPayload } from '@/services/competition/types';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';
import './CompetitionPage.css';

type CompetitionFormValues = Omit<CompetitionUpsertPayload, 'registrationStart' | 'registrationEnd' | 'competitionStart' | 'competitionEnd'> & {
  registrationRange?: [Dayjs, Dayjs] | [string, string];
  competitionRange?: [Dayjs, Dayjs] | [string, string];
};

const localeOptions: Array<{ label: string; value: CompetitionLocale }> = [
  { label: '中文', value: 'zh' },
  { label: 'English', value: 'en' },
];

const statusOptions: Array<{ label: string; value: CompetitionStatus }> = [
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' },
  { label: '已归档', value: 'archived' },
];

const statusText: Record<CompetitionStatus, string> = {
  draft: '草稿',
  published: '已发布',
  archived: '已归档',
};

const statusColor: Record<CompetitionStatus, string> = {
  draft: 'default',
  published: 'green',
  archived: 'blue',
};

const trimOptional = (value?: string) => {
  const trimmed = value?.trim();
  return trimmed || undefined;
};

const parseDateTime = (value?: string | null) => {
  if (!value) {
    return undefined;
  }
  const parsed = dayjs(value.replace(/\./g, '-'));
  return parsed.isValid() ? parsed : undefined;
};

const parseRange = (start?: string | null, end?: string | null): [Dayjs, Dayjs] | undefined => {
  const parsedStart = parseDateTime(start);
  const parsedEnd = parseDateTime(end);
  return parsedStart && parsedEnd ? [parsedStart, parsedEnd] : undefined;
};

const formatRangeValue = (value?: Dayjs | string) => {
  if (!value || typeof value === 'string') {
    return undefined;
  }
  return value.format('YYYY.MM.DD HH:mm');
};

const normalizePayload = (values: CompetitionFormValues): CompetitionUpsertPayload => {
  const [registrationStart, registrationEnd] = values.registrationRange || [];
  const [competitionStart, competitionEnd] = values.competitionRange || [];
  const { registrationRange: _registrationRange, competitionRange: _competitionRange, ...payloadValues } = values;
  return {
    ...payloadValues,
    code: trimOptional(values.code),
    title: values.title.trim(),
    category: values.category.trim(),
    level: trimOptional(values.level),
    organizer: trimOptional(values.organizer),
    registrationStart: formatRangeValue(registrationStart),
    registrationEnd: formatRangeValue(registrationEnd),
    competitionStart: formatRangeValue(competitionStart) || '',
    competitionEnd: formatRangeValue(competitionEnd),
    location: values.location.trim(),
    description: trimOptional(values.description),
    imageUrl: trimOptional(values.imageUrl),
    tags: trimOptional(values.tags),
    status: values.status || 'draft',
    featured: Boolean(values.featured),
    sort: values.sort ?? 100,
  };
};

const parseFeaturedFilter = (value: unknown) => {
  if (typeof value === 'boolean') {
    return value;
  }
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  return undefined;
};

const splitTags = (tags?: string | null) =>
  (tags || '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);

const CompetitionForm = ({ form }: { form: FormInstance<CompetitionFormValues> }) => (
  <Form<CompetitionFormValues>
    form={form}
    layout="vertical"
    initialValues={{
      locale: 'zh',
      status: 'draft',
      sort: 100,
      featured: false,
    }}
  >
    <Form.Item name="title" label="赛事名称" rules={[{ required: true, message: '请输入赛事名称' }]}>
      <Input maxLength={128} />
    </Form.Item>
    <Space size="middle" style={{ width: '100%' }} align="start">
      <Form.Item name="category" label="赛事分组" rules={[{ required: true, message: '请输入赛事分组' }]} style={{ flex: 1 }}>
        <Input maxLength={64} placeholder="例如 创新赛 / 应用赛 / 专项赛" />
      </Form.Item>
      <Form.Item name="level" label="赛事级别" style={{ flex: 1 }}>
        <Input maxLength={64} placeholder="例如 校级 / 省级 / 全国" />
      </Form.Item>
    </Space>
    <Form.Item name="organizer" label="主办方">
      <Input maxLength={128} />
    </Form.Item>
    <Form.Item name="registrationRange" label="报名时间">
      <DatePicker.RangePicker showTime format="YYYY.MM.DD HH:mm" minuteStep={15} style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item
      name="competitionRange"
      label="赛事时间"
      rules={[
        { required: true, message: '请选择赛事时间' },
        {
          validator: (_, value: CompetitionFormValues['competitionRange']) =>
            Array.isArray(value) && value.length === 2 ? Promise.resolve() : Promise.reject(new Error('请选择开始和结束时间')),
        },
      ]}
    >
      <DatePicker.RangePicker showTime format="YYYY.MM.DD HH:mm" minuteStep={15} style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item name="location" label="赛事地点" rules={[{ required: true, message: '请输入赛事地点' }]}>
      <Input maxLength={255} />
    </Form.Item>
    <Form.Item name="description" label="赛事说明">
      <Input.TextArea rows={4} maxLength={1000} />
    </Form.Item>
    <Form.Item name="imageUrl" label="封面 URL">
      <Input maxLength={512} />
    </Form.Item>
    <Form.Item name="tags" label="标签">
      <Input maxLength={1000} placeholder="多个标签用英文逗号分隔" />
    </Form.Item>
    <Space size="middle" style={{ width: '100%' }} align="start">
      <Form.Item name="locale" label="语言" rules={[{ required: true }]} style={{ flex: 1 }}>
        <Select options={localeOptions} />
      </Form.Item>
      <Form.Item name="status" label="状态" rules={[{ required: true }]} style={{ flex: 1 }}>
        <Select options={statusOptions} />
      </Form.Item>
      <Form.Item name="sort" label="排序" style={{ flex: 1 }}>
        <InputNumber min={0} max={9999} style={{ width: '100%' }} />
      </Form.Item>
    </Space>
    <Form.Item name="featured" label="推荐赛事" valuePropName="checked">
      <Switch checkedChildren="是" unCheckedChildren="否" />
    </Form.Item>
    <Form.Item name="code" label="赛事编码">
      <Input maxLength={64} placeholder="不填时自动生成" />
    </Form.Item>
  </Form>
);

const CompetitionPage = () => {
  const location = useLocation();
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [form] = Form.useForm<CompetitionFormValues>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<CompetitionRecord>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (location.pathname === '/competitions') {
      history.replace('/competitions/management');
    }
  }, [location.pathname]);

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingRecord(undefined);
  };

  const openCreateDrawer = () => {
    setEditingRecord(undefined);
    form.resetFields();
    form.setFieldsValue({ locale: 'zh', status: 'draft', sort: 100, featured: false });
    setDrawerOpen(true);
  };

  const openEditDrawer = (record: CompetitionRecord) => {
    setEditingRecord(record);
    form.resetFields();
    form.setFieldsValue({
      ...record,
      level: record.level || undefined,
      organizer: record.organizer || undefined,
      registrationRange: parseRange(record.registrationStart, record.registrationEnd),
      competitionRange: parseRange(record.competitionStart, record.competitionEnd),
      description: record.description || undefined,
      imageUrl: record.imageUrl || undefined,
      tags: record.tags || undefined,
      featured: Boolean(record.featured),
    });
    setDrawerOpen(true);
  };

  const saveCompetition = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editingRecord) {
        await updateCompetition(editingRecord.id, normalizePayload(values));
        message.success('赛事已更新');
      } else {
        await createCompetition(normalizePayload(values));
        message.success('赛事已新增');
      }
      closeDrawer();
      actionRef.current?.reload();
    } catch (error) {
      showErrorMessage(error, '赛事保存失败');
    } finally {
      setSaving(false);
    }
  };

  const columns = useMemo<ProColumns<CompetitionRecord>[]>(
    () => [
      {
        title: '赛事',
        dataIndex: 'keyword',
        render: (_, record) => (
          <Space className="competition-name-cell" direction="vertical" size={0}>
            <Typography.Text strong>{record.title}</Typography.Text>
            <span className="competition-name-cell__meta">{record.code}</span>
          </Space>
        ),
      },
      {
        title: '分组',
        dataIndex: 'category',
        render: (value) => (value ? <Tag color="blue">{String(value)}</Tag> : '-'),
      },
      {
        title: '级别',
        dataIndex: 'level',
        search: false,
        render: (value) => value || '-',
      },
      {
        title: '主办方',
        dataIndex: 'organizer',
        search: false,
        ellipsis: true,
        render: (value) => value || '-',
      },
      {
        title: '语言',
        dataIndex: 'locale',
        valueType: 'select',
        valueEnum: {
          zh: { text: '中文' },
          en: { text: 'English' },
        },
        width: 96,
      },
      {
        title: '赛事时间',
        dataIndex: 'competitionStart',
        search: false,
        width: 220,
        render: (_, record) => `${record.competitionStart || '-'}${record.competitionEnd ? ` - ${record.competitionEnd}` : ''}`,
      },
      {
        title: '地点',
        dataIndex: 'location',
        search: false,
        ellipsis: true,
      },
      {
        title: '标签',
        dataIndex: 'tags',
        search: false,
        render: (_, record) => (
          <Space className="competition-tags" size={[4, 4]} wrap>
            {splitTags(record.tags).slice(0, 4).map((tag) => (
              <Tag key={tag} color="geekblue">
                {tag}
              </Tag>
            ))}
            {!splitTags(record.tags).length ? '-' : null}
          </Space>
        ),
      },
      {
        title: '推荐',
        dataIndex: 'featured',
        valueType: 'select',
        valueEnum: {
          true: { text: '是' },
          false: { text: '否' },
        },
        width: 90,
        render: (_, record) => (record.featured ? <Tag color="gold">推荐</Tag> : <Tag>普通</Tag>),
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueType: 'select',
        valueEnum: {
          draft: { text: '草稿' },
          published: { text: '已发布' },
          archived: { text: '已归档' },
        },
        width: 110,
        render: (_, record) => <Tag color={statusColor[record.status]}>{statusText[record.status]}</Tag>,
      },
      {
        title: '排序',
        dataIndex: 'sort',
        search: false,
        width: 80,
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
                permission: 'aiadc:competition:update',
                onClick: () => openEditDrawer(record),
              },
              {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined />,
                permission: 'aiadc:competition:delete',
                danger: true,
                onClick: () => {
                  Modal.confirm({
                    title: '确认删除该赛事？',
                    content: `删除后赛事「${record.title}」不会再出现在赛事列表中。`,
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await deleteCompetition(record.id);
                      message.success('赛事已删除');
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
    <ManagementPage title="赛事管理">
      <ManagementPageBody>
        <ManagementTable<CompetitionRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          scroll={{ x: 1420 }}
          request={async (params) => {
            const response = await listCompetitions({
              keyword: typeof params.keyword === 'string' ? params.keyword : undefined,
              category: typeof params.category === 'string' ? params.category : undefined,
              locale: params.locale as CompetitionLocale | undefined,
              status: params.status as CompetitionStatus | undefined,
              featured: parseFeaturedFilter(params.featured),
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
                permission: 'aiadc:competition:create',
                value: (
                  <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreateDrawer}>
                    新增赛事
                  </Button>
                ),
              },
            ])
          }
        />
      </ManagementPageBody>

      <ManagementDrawer
        title={editingRecord ? '编辑赛事' : '新增赛事'}
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
            onClick: () => void saveCompetition(),
          },
        ]}
      >
        <CompetitionForm form={form} />
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default CompetitionPage;
