import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Card, Descriptions, Form, Input, Result, Select, Space, Steps, Tag, Typography } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { history, useLocation } from '@umijs/max';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { useResponsive } from '@/hooks/useResponsive';
import { createActivityRegistration, listActivityRegistrations, listPublicActivities } from '@/services/activity/api';
import type { ActivityRegistrationRecord as ActivityApplicationRecord, PublicActivityRecord } from '@/services/activity/types';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';

type ActivityRegistrationValues = {
  activityId?: number;
  name?: string;
  mobile?: string;
  email?: string;
  organization?: string;
  position?: string;
  remark?: string;
};

type ActivityRegistrationFilterValues = {
  applicationNo?: string;
  activityTitle?: string;
  name?: string;
  mobile?: string;
  status?: ActivityApplicationRecord['status'];
};

const activityRegistrationModeQueryKey = 'mode';
const activityRegistrationStepQueryKey = 'step';
const activityRegistrationModeValue = 'wizard';
const activityRegistrationMaxStep = 3;

const parseActivityRegistrationStepFromSearch = (search: string) => {
  const params = new URLSearchParams(search);
  if (params.get(activityRegistrationModeQueryKey) !== activityRegistrationModeValue) {
    return undefined;
  }
  const stepValue = Number(params.get(activityRegistrationStepQueryKey));
  if (!Number.isInteger(stepValue) || stepValue < 1) {
    return 0;
  }
  return Math.min(stepValue - 1, activityRegistrationMaxStep);
};

const createActivityRegistrationSearch = (stepIndex: number) => {
  const params = new URLSearchParams();
  params.set(activityRegistrationModeQueryKey, activityRegistrationModeValue);
  params.set(activityRegistrationStepQueryKey, String(Math.min(Math.max(stepIndex, 0), activityRegistrationMaxStep) + 1));
  return `?${params.toString()}`;
};

const activityRegistrationBreadcrumb = {
  items: [{ title: '活动报名' }],
};

const formatDateTime = (value?: string) => (value ? value.replace('T', ' ').slice(0, 19) : '-');

const activityRegistrationColumns: ProColumns<ActivityApplicationRecord>[] = [
  {
    title: '报名编号',
    dataIndex: 'applicationNo',
    width: 190,
    fieldProps: {
      placeholder: 'Registration No.',
    },
    render: (_, record) => (
      <Typography.Text strong ellipsis={{ tooltip: record.applicationNo }}>
        {record.applicationNo}
      </Typography.Text>
    ),
  },
  {
    title: '活动',
    dataIndex: 'activityTitle',
    fieldProps: {
      placeholder: '请输入活动名称',
    },
    ellipsis: true,
  },
  {
    title: '报名人',
    dataIndex: 'name',
    width: 130,
    fieldProps: {
      placeholder: '请输入报名人',
    },
    render: (_, record) => record.name || '-',
  },
  {
    title: '手机号',
    dataIndex: 'mobile',
    width: 150,
    fieldProps: {
      placeholder: '请输入手机号',
    },
    render: (_, record) => record.mobile || '-',
  },
  {
    title: '状态',
    dataIndex: 'status',
    valueType: 'select',
    valueEnum: {
      SUBMITTED: { text: '已提交' },
    },
    width: 110,
    render: () => <Tag color="success">已提交</Tag>,
  },
  {
    title: '提交时间',
    dataIndex: 'submittedAt',
    width: 172,
    render: (_, record) => formatDateTime(record.submittedAt),
  },
];

const normalizeFilterValue = (value?: string) => value?.trim().toLocaleLowerCase() || '';

const filterActivityRegistrations = (
  records: ActivityApplicationRecord[],
  filters: ActivityRegistrationFilterValues,
) => {
  const applicationNo = normalizeFilterValue(filters.applicationNo);
  const activityTitle = normalizeFilterValue(filters.activityTitle);
  const name = normalizeFilterValue(filters.name);
  const mobile = normalizeFilterValue(filters.mobile);

  return records.filter((record) => (
    (!applicationNo || record.applicationNo.toLocaleLowerCase().includes(applicationNo))
      && (!activityTitle || record.activityTitle.toLocaleLowerCase().includes(activityTitle))
      && (!name || record.name.toLocaleLowerCase().includes(name))
      && (!mobile || record.mobile?.toLocaleLowerCase().includes(mobile))
      && (!filters.status || record.status === filters.status)
  ));
};

const ActivityRegistrationPage = () => {
  const location = useLocation();
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [form] = Form.useForm<ActivityRegistrationValues>();
  const [viewMode, setViewMode] = useState<'list' | 'wizard'>('list');
  const [step, setStep] = useState(0);
  const [activities, setActivities] = useState<PublicActivityRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [applicationNo, setApplicationNo] = useState<string>();
  const [selectedActivityId, setSelectedActivityId] = useState<number>();
  const canCreateActivityRegistration = actionPermission.can('aiadc:activity:create');

  const selectedActivity = useMemo(
    () => activities.find((activity) => activity.id === selectedActivityId),
    [activities, selectedActivityId],
  );

  const setWizardStep = useCallback((nextStep: number, replace = true) => {
    const normalizedStep = Math.min(Math.max(nextStep, 0), activityRegistrationMaxStep);
    setStep(normalizedStep);
    setViewMode('wizard');
    const navigate = replace ? history.replace : history.push;
    navigate({
      pathname: location.pathname,
      search: createActivityRegistrationSearch(normalizedStep),
    });
  }, [location.pathname]);

  const showList = useCallback(() => {
    setViewMode('list');
    history.replace({ pathname: location.pathname, search: '' });
  }, [location.pathname]);

  const startNewRegistration = useCallback(() => {
    form.resetFields();
    setApplicationNo(undefined);
    setSelectedActivityId(undefined);
    setWizardStep(0, false);
  }, [form, setWizardStep]);

  const loadPage = useCallback(async () => {
    setLoading(true);
    try {
      const activityResponse = await listPublicActivities({ status: 'published', pageNo: 1, pageSize: 100 });
      setActivities(activityResponse.records);
    } catch (error) {
      showErrorMessage(error, '活动列表加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadPage();
  }, [loadPage]);

  const activityRegistrationTableRequest = useMemo(
    () => buildTableRequest<ActivityApplicationRecord>(async (params) => {
      const registrationResponse = await listActivityRegistrations();
      const filteredRecords = filterActivityRegistrations(registrationResponse, {
        applicationNo: typeof params.applicationNo === 'string' ? params.applicationNo : undefined,
        activityTitle: typeof params.activityTitle === 'string' ? params.activityTitle : undefined,
        name: typeof params.name === 'string' ? params.name : undefined,
        mobile: typeof params.mobile === 'string' ? params.mobile : undefined,
        status: params.status === 'SUBMITTED' ? 'SUBMITTED' : undefined,
      });
      const pageNo = Math.max(1, Number(params.pageNo) || 1);
      const pageSize = Math.max(1, Number(params.pageSize) || 10);
      const start = (pageNo - 1) * pageSize;
      return {
        records: filteredRecords.slice(start, start + pageSize),
        total: filteredRecords.length,
      };
    }),
    [],
  );

  useEffect(() => {
    const requestedStep = parseActivityRegistrationStepFromSearch(location.search);
    if (requestedStep === undefined) {
      setViewMode('list');
      return;
    }
    setViewMode('wizard');
    setStep(requestedStep);
  }, [location.search]);

  const next = async () => {
    if (!canCreateActivityRegistration) {
      message.error('当前账号没有活动报名提交权限');
      return;
    }
    if (step === 0) {
      await form.validateFields(['activityId']);
      setSelectedActivityId(form.getFieldValue('activityId'));
      setWizardStep(1);
      return;
    }

    if (step === 1) {
      await form.validateFields(['name', 'mobile', 'email', 'organization', 'position', 'remark']);
      setWizardStep(2);
      return;
    }

    const values = form.getFieldsValue(true);
    const created = await createActivityRegistration({
      activityId: values.activityId!, name: values.name!, mobile: values.mobile, email: values.email,
      organization: values.organization, position: values.position, remark: values.remark,
    });
    setApplicationNo(created.applicationNo);
    message.success('活动报名已提交');
    setWizardStep(3);
  };

  const previous = () => setWizardStep(step - 1);

  if (viewMode === 'list') {
    return (
      <ManagementPage title="活动报名" breadcrumb={activityRegistrationBreadcrumb}>
        <ManagementPageBody>
          <ManagementTable<ActivityApplicationRecord>
            actionRef={actionRef}
            rowKey="id"
            columns={activityRegistrationColumns}
            isMobile={responsive.isMobile}
            autoContentWidth
            scroll={{ x: 'max-content' }}
            tableLayout="auto"
            request={activityRegistrationTableRequest}
            pagination={{ pageSize: 10, showSizeChanger: true }}
            toolBarRender={() => [
              <Button key="refresh" icon={<ReloadOutlined />} onClick={() => actionRef.current?.reload()}>
                刷新
              </Button>,
              ...(canCreateActivityRegistration ? [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={startNewRegistration}>
                  新增报名
                </Button>,
              ] : []),
            ]}
          />
        </ManagementPageBody>
      </ManagementPage>
    );
  }

  return (
    <ManagementPage
      title="活动报名"
      breadcrumb={activityRegistrationBreadcrumb}
      extra={<Button onClick={showList}>返回报名记录</Button>}
    >
      <ManagementPageBody>
        <Card className="competition-application-card">
          <Steps
            current={step}
            items={[
              { title: '选择活动' },
              { title: '填写信息' },
              { title: '确认提交' },
              { title: '完成' },
            ]}
          />

          {step === 3 ? (
            <Result
              status="success"
              title="活动报名已提交"
              subTitle={applicationNo ? `报名编号：${applicationNo}` : undefined}
              extra={
                <Space>
                  <Button onClick={showList}>返回报名记录</Button>
                  {canCreateActivityRegistration ? (
                    <Button type="primary" onClick={startNewRegistration}>
                      新增报名
                    </Button>
                  ) : null}
                </Space>
              }
            />
          ) : (
            <Form form={form} layout="vertical" className="competition-application-form">
              {step === 0 ? (
                <>
                  <Form.Item name="activityId" label="活动" rules={[{ required: true, message: '请选择活动' }]}>
                    <Select
                      loading={loading}
                      options={activities.map((activity) => ({
                        label: activity.title,
                        value: activity.id,
                      }))}
                      placeholder="请选择要报名的活动"
                    />
                  </Form.Item>
                  {selectedActivity ? (
                    <Descriptions column={1} size="small" bordered>
                      <Descriptions.Item label="时间">{[selectedActivity.activityDate, selectedActivity.activityTime].filter(Boolean).join(' ') || '-'}</Descriptions.Item>
                      <Descriptions.Item label="地点">{selectedActivity.location || '-'}</Descriptions.Item>
                      <Descriptions.Item label="简介">{selectedActivity.description || selectedActivity.subtitle || '-'}</Descriptions.Item>
                    </Descriptions>
                  ) : (
                    <Typography.Text type="secondary">请选择一个活动继续报名。</Typography.Text>
                  )}
                </>
              ) : null}

              {step === 1 ? (
                <div className="competition-application-grid">
                  <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
                    <Input maxLength={64} />
                  </Form.Item>
                  <Form.Item name="mobile" label="手机号" rules={[{ required: true, message: '请输入手机号' }]}>
                    <Input maxLength={32} />
                  </Form.Item>
                  <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '请输入有效邮箱' }]}>
                    <Input maxLength={128} />
                  </Form.Item>
                  <Form.Item name="organization" label="单位">
                    <Input maxLength={128} />
                  </Form.Item>
                  <Form.Item name="position" label="职务">
                    <Input maxLength={64} />
                  </Form.Item>
                  <Form.Item name="remark" label="备注" className="competition-application-grid__full">
                    <Input.TextArea rows={4} maxLength={500} showCount />
                  </Form.Item>
                </div>
              ) : null}

              {step === 2 ? (
                <Descriptions column={1} bordered>
                  <Descriptions.Item label="活动">{selectedActivity?.title || '-'}</Descriptions.Item>
                  <Descriptions.Item label="姓名">{form.getFieldValue('name') || '-'}</Descriptions.Item>
                  <Descriptions.Item label="手机号">{form.getFieldValue('mobile') || '-'}</Descriptions.Item>
                  <Descriptions.Item label="邮箱">{form.getFieldValue('email') || '-'}</Descriptions.Item>
                  <Descriptions.Item label="单位">{form.getFieldValue('organization') || '-'}</Descriptions.Item>
                  <Descriptions.Item label="职务">{form.getFieldValue('position') || '-'}</Descriptions.Item>
                  <Descriptions.Item label="备注">{form.getFieldValue('remark') || '-'}</Descriptions.Item>
                </Descriptions>
              ) : null}

              <Space className="competition-application-actions">
                {step > 0 ? <Button onClick={previous}>上一步</Button> : null}
                <Button type="primary" disabled={!canCreateActivityRegistration} onClick={() => void next()}>
                  {step === 2 ? '提交报名' : '下一步'}
                </Button>
              </Space>
            </Form>
          )}
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default ActivityRegistrationPage;
