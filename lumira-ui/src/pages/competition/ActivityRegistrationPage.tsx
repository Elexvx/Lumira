import { PlusOutlined } from '@ant-design/icons';
import { Button, Card, Descriptions, Form, Input, Result, Select, Space, Steps, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { history, useLocation, useModel } from '@umijs/max';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { listPublicActivities } from '@/services/activity/api';
import type { PublicActivityRecord } from '@/services/activity/types';
import type { RoleDataScope } from '@/types/api';
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

type ActivityApplicationRecord = ActivityRegistrationValues & {
  id: string;
  applicationNo: string;
  activityTitle: string;
  status: 'SUBMITTED';
  submittedAt: string;
  ownerUserId?: number | null;
  ownerUsername?: string | null;
};

const activityRegistrationModeQueryKey = 'mode';
const activityRegistrationStepQueryKey = 'step';
const activityRegistrationModeValue = 'wizard';
const activityRegistrationMaxStep = 3;
const ACTIVITY_REGISTRATION_SCOPE_RESOURCE = 'activity:registration';
const buildActivityRegistrationStorageKey = (userId?: number | null) => `lumira.activityRegistration.records.${userId ?? 'guest'}`;

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

const readActivityApplicationRecords = (storageKey: string): ActivityApplicationRecord[] => {
  try {
    const value = window.localStorage.getItem(storageKey);
    const parsed = value ? JSON.parse(value) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const writeActivityApplicationRecords = (storageKey: string, records: ActivityApplicationRecord[]) => {
  window.localStorage.setItem(storageKey, JSON.stringify(records));
};

const formatDateTime = (value?: string) => (value ? value.replace('T', ' ').slice(0, 19) : '-');

const createLocalTimestamp = () => {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 19);
};

const canViewAllActivityApplications = (dataScopes?: RoleDataScope[]) => {
  const matchedScopes = (dataScopes || []).filter(
    (scope) => scope.resourceCode === '*' || scope.resourceCode === ACTIVITY_REGISTRATION_SCOPE_RESOURCE,
  );
  return matchedScopes.some((scope) => scope.scopeType === 'ALL');
};

const readVisibleActivityApplicationRecords = (storageKey: string, canViewAll: boolean): ActivityApplicationRecord[] => {
  if (!canViewAll) {
    return readActivityApplicationRecords(storageKey);
  }

  const records: ActivityApplicationRecord[] = [];
  for (let index = 0; index < window.localStorage.length; index += 1) {
    const key = window.localStorage.key(index);
    if (!key || !key.startsWith('lumira.activityRegistration.records.')) {
      continue;
    }
    records.push(...readActivityApplicationRecords(key));
  }
  return records.sort((left, right) => String(right.submittedAt || '').localeCompare(String(left.submittedAt || '')));
};

const ActivityRegistrationPage = () => {
  const { initialState } = useModel('@@initialState');
  const location = useLocation();
  const actionPermission = useActionPermission();
  const [form] = Form.useForm<ActivityRegistrationValues>();
  const [viewMode, setViewMode] = useState<'list' | 'wizard'>('list');
  const [step, setStep] = useState(0);
  const [activities, setActivities] = useState<PublicActivityRecord[]>([]);
  const [records, setRecords] = useState<ActivityApplicationRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [applicationNo, setApplicationNo] = useState<string>();
  const [selectedActivityId, setSelectedActivityId] = useState<number>();
  const activityRegistrationStorageKey = useMemo(
    () => buildActivityRegistrationStorageKey(initialState?.currentUser?.userId),
    [initialState?.currentUser?.userId],
  );
  const canViewAllRecords = useMemo(
    () => canViewAllActivityApplications(initialState?.currentUser?.dataScopes),
    [initialState?.currentUser?.dataScopes],
  );
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

  useEffect(() => {
    setRecords(readVisibleActivityApplicationRecords(activityRegistrationStorageKey, canViewAllRecords));
  }, [activityRegistrationStorageKey, canViewAllRecords]);

  useEffect(() => {
    const loadActivities = async () => {
      setLoading(true);
      try {
        const response = await listPublicActivities({ status: 'published', pageNo: 1, pageSize: 100 });
        setActivities(response.records);
      } catch (error) {
        showErrorMessage(error, '活动列表加载失败');
      } finally {
        setLoading(false);
      }
    };

    void loadActivities();
  }, []);

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
    const no = `ACT-${Date.now().toString(36).toUpperCase()}`;
    const nextRecord: ActivityApplicationRecord = {
      ...values,
      id: no,
      applicationNo: no,
      activityTitle: selectedActivity?.title || '-',
      status: 'SUBMITTED',
      submittedAt: createLocalTimestamp(),
      ownerUserId: initialState?.currentUser?.userId,
      ownerUsername: initialState?.currentUser?.username,
    };
    const nextRecords = [nextRecord, ...records];
    writeActivityApplicationRecords(
      activityRegistrationStorageKey,
      canViewAllRecords ? [nextRecord, ...readActivityApplicationRecords(activityRegistrationStorageKey)] : nextRecords,
    );
    setRecords(readVisibleActivityApplicationRecords(activityRegistrationStorageKey, canViewAllRecords));
    setApplicationNo(no);
    message.success('活动报名已提交');
    setWizardStep(3);
  };

  const previous = () => setWizardStep(step - 1);

  const columns: ColumnsType<ActivityApplicationRecord> = [
    {
      title: '报名编号',
      dataIndex: 'applicationNo',
      render: (value: string) => <Typography.Text strong>{value}</Typography.Text>,
    },
    {
      title: '活动',
      dataIndex: 'activityTitle',
    },
    {
      title: '报名人',
      dataIndex: 'name',
      render: (value?: string) => value || '-',
    },
    {
      title: '手机号',
      dataIndex: 'mobile',
      render: (value?: string) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: () => <Tag color="success">已提交</Tag>,
    },
    {
      title: '提交时间',
      dataIndex: 'submittedAt',
      render: formatDateTime,
    },
  ];

  if (viewMode === 'list') {
    return (
      <ManagementPage title="活动报名" extra={<Button onClick={() => history.push('/activities/management')}>返回活动管理</Button>}>
        <ManagementPageBody>
          <Card>
            <Table<ActivityApplicationRecord>
              rowKey="id"
              columns={columns}
              dataSource={records}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              title={() => (
                <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Typography.Title level={5} style={{ margin: 0 }}>
                    活动报名记录
                  </Typography.Title>
                  {canCreateActivityRegistration ? (
                    <Button type="primary" icon={<PlusOutlined />} onClick={startNewRegistration}>
                      新增报名
                    </Button>
                  ) : null}
                </Space>
              )}
            />
          </Card>
        </ManagementPageBody>
      </ManagementPage>
    );
  }

  return (
    <ManagementPage title="活动报名" extra={<Button onClick={showList}>返回报名记录</Button>}>
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
