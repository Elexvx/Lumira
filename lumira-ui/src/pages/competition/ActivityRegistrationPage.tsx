import { Button, Card, Descriptions, Form, Input, Result, Select, Space, Steps, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { history } from '@umijs/max';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { listActivities } from '@/services/activity/api';
import type { ActivityRecord } from '@/services/activity/types';
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

const ActivityRegistrationPage = () => {
  const [form] = Form.useForm<ActivityRegistrationValues>();
  const [step, setStep] = useState(0);
  const [activities, setActivities] = useState<ActivityRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [applicationNo, setApplicationNo] = useState<string>();
  const selectedActivityId = Form.useWatch('activityId', form);

  useEffect(() => {
    const loadActivities = async () => {
      setLoading(true);
      try {
        const response = await listActivities({ status: 'published', pageNo: 1, pageSize: 100 });
        setActivities(response.records);
      } catch (error) {
        showErrorMessage(error, '活动列表加载失败');
      } finally {
        setLoading(false);
      }
    };

    void loadActivities();
  }, []);

  const selectedActivity = useMemo(
    () => activities.find((activity) => activity.id === selectedActivityId),
    [activities, selectedActivityId],
  );

  const next = async () => {
    if (step === 0) {
      await form.validateFields(['activityId']);
      setStep(1);
      return;
    }

    if (step === 1) {
      await form.validateFields(['name', 'mobile', 'email', 'organization', 'position', 'remark']);
      setStep(2);
      return;
    }

    const no = `ACT-${Date.now().toString(36).toUpperCase()}`;
    setApplicationNo(no);
    message.success('活动报名已提交');
    setStep(3);
  };

  const previous = () => setStep((current) => Math.max(0, current - 1));

  return (
    <ManagementPage title="活动报名" extra={<Button onClick={() => history.push('/competitions/register')}>返回赛事报名</Button>}>
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
              extra={<Button type="primary" onClick={() => history.push('/competitions/register')}>返回赛事报名</Button>}
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
                <Button type="primary" onClick={() => void next()}>
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
