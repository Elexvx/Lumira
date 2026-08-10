import { Button, Card, Form, Result, Space, Steps, Typography } from 'antd';
import { useState } from 'react';
import { history } from '@umijs/max';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { createExpert, uploadExpertAvatar } from '@/services/expert/api';
import type { ExpertRecord } from '@/services/expert/types';
import { ExpertForm, normalizeExpertPayload } from '@/pages/expert/ExpertPage';
import type { ExpertFormValues } from '@/pages/expert/ExpertPage';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';

const ExpertApplicationPage = () => {
  const [form] = Form.useForm<ExpertFormValues>();
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [createdExpert, setCreatedExpert] = useState<ExpertRecord>();

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

  const submitApplication = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const created = await createExpert(normalizeExpertPayload(values));
      setCreatedExpert(created);
      message.success('专家申请已提交审批');
      setStep(2);
    } catch (error) {
      showErrorMessage(error, '专家申请提交失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ManagementPage title="专家申请" extra={<Button onClick={() => history.push('/competitions/register')}>返回赛事报名</Button>}>
      <ManagementPageBody>
        <Card className="competition-application-card competition-expert-application-card">
          <div className="competition-expert-application-content">
            <Steps
              current={step}
              items={[
                { title: '申请确认' },
                { title: '填写资料' },
                { title: '完成' },
              ]}
            />

            {step === 0 ? (
              <div className="competition-application-intro">
                <Typography.Title level={4}>专家申请</Typography.Title>
                <Typography.Paragraph>
                  请按专家库资料标准填写申请信息。提交后进入审批流程，审批通过后系统会自动创建账号并发送激活邮件。
                </Typography.Paragraph>
                <Button type="primary" onClick={() => setStep(1)}>
                  开始填写
                </Button>
              </div>
            ) : null}

            {step === 1 ? (
              <>
                <ExpertForm form={form} uploadingAvatar={uploadingAvatar} onAvatarUpload={handleAvatarUpload} />
                <Space className="competition-application-actions">
                  <Button onClick={() => setStep(0)}>上一步</Button>
                  <Button type="primary" loading={saving} onClick={() => void submitApplication()}>
                    提交申请
                  </Button>
                </Space>
              </>
            ) : null}

            {step === 2 ? (
              <Result
                status="success"
                title="专家申请已提交"
                subTitle={createdExpert ? `专家编码：${createdExpert.code}。审批通过后系统会发送账号激活邮件。` : undefined}
                extra={[
                  <Button key="back" onClick={() => history.push('/competitions/register')}>
                    返回赛事报名
                  </Button>,
                  <Button key="tasks" type="primary" onClick={() => history.push('/workflows/tasks')}>
                    查看审批进度
                  </Button>,
                ]}
              />
            ) : null}
          </div>
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default ExpertApplicationPage;
