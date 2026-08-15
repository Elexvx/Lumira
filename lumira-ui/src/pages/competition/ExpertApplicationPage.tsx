import {
  Alert,
  Button,
  Card,
  DatePicker,
  Form,
  Image,
  Input,
  InputNumber,
  Result,
  Select,
  Space,
  Spin,
  Steps,
  Typography,
  Upload,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { history, useLocation } from '@umijs/max';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import {
  getCompetitionSettings,
  listCompetitions,
} from '@/services/competition/api';
import type { CompetitionRecord, CompetitionSettingsRecord } from '@/services/competition/types';
import { createExpert } from '@/services/expert/api';
import type { ExpertRecord } from '@/services/expert/types';
import { request } from '@/services/common/request';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { validateRegistrationFieldValue } from './utils/registrationFieldValidation';
import {
  buildExpertApplicationPayload,
  parseExpertApplicationFieldOptions,
  parseExpertApplicationFields,
  type ExpertApplicationField,
  type ExpertApplicationFormValues,
} from './utils/expertApplicationFields';

const parseCompetitionUuid = (search: string) => new URLSearchParams(search).get('competitionUuid') || '';

const ExpertApplicationImageInput = ({
  value,
  onChange,
}: {
  value?: string;
  onChange?: (value?: string) => void;
}) => {
  const [uploading, setUploading] = useState(false);

  return (
    <Space align="start" size="middle" wrap>
      {value ? <Image width={96} height={96} src={normalizeUploadUrl(value)} alt="已上传头像" /> : null}
      <Space orientation="vertical" size={4}>
        <Upload
          accept="image/*"
          showUploadList={false}
          disabled={uploading}
          beforeUpload={async (file) => {
            if (!file.type.startsWith('image/')) {
              message.warning('请上传图片文件');
              return Upload.LIST_IGNORE;
            }
            setUploading(true);
            try {
              const data = new FormData();
              data.append('file', file);
              const uploadedUrl = await request<string>('/v1/system/uploads/image', {
                method: 'POST',
                headers: {},
                data,
              });
              onChange?.(uploadedUrl);
            } catch (error) {
              showErrorMessage(error, '图片上传失败');
            } finally {
              setUploading(false);
            }
            return Upload.LIST_IGNORE;
          }}
        >
          <Button loading={uploading}>{value ? '更换图片' : '上传图片'}</Button>
        </Upload>
        {value ? <Button type="link" onClick={() => onChange?.(undefined)}>移除</Button> : null}
      </Space>
    </Space>
  );
};

const renderExpertApplicationInput = (field: ExpertApplicationField) => {
  const placeholder = field.placeholder || `请输入${field.title}`;
  switch (field.fieldType) {
    case 'TEXTAREA':
      return <Input.TextArea rows={4} placeholder={placeholder} />;
    case 'NUMBER':
      return <InputNumber style={{ width: '100%' }} placeholder={placeholder} />;
    case 'DATE':
      return <DatePicker style={{ width: '100%' }} placeholder={placeholder} />;
    case 'IMAGE':
      return <ExpertApplicationImageInput />;
    case 'SELECT':
    case 'ROLE':
      return <Select options={parseExpertApplicationFieldOptions(field.options)} placeholder={placeholder} showSearch optionFilterProp="label" />;
    case 'MULTI_SELECT':
      return <Select mode="multiple" options={parseExpertApplicationFieldOptions(field.options)} placeholder={placeholder} showSearch optionFilterProp="label" />;
    case 'MOBILE':
      return <Input inputMode="numeric" maxLength={11} placeholder={placeholder} />;
    case 'EMAIL':
      return <Input maxLength={128} placeholder={placeholder} />;
    default:
      return <Input placeholder={placeholder} />;
  }
};

const buildExpertApplicationFieldRules = (field: ExpertApplicationField) => [
  ...(field.required ? [{ required: true, message: `请输入${field.title}` }] : []),
  {
    validator: async (_: unknown, value: unknown) => {
      const validationError = validateRegistrationFieldValue(
        field.fieldType,
        field.validationRule,
        field.title,
        value,
        'EXPERT_FIELD',
        field.itemKey,
      );
      if (validationError) {
        throw new Error(validationError);
      }
    },
  },
];

const ExpertApplicationPage = () => {
  const location = useLocation();
  const [form] = Form.useForm<ExpertApplicationFormValues>();
  const initialCompetitionUuid = parseCompetitionUuid(location.search);
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);
  const [loadingCompetitions, setLoadingCompetitions] = useState(true);
  const [loadingSettings, setLoadingSettings] = useState(false);
  const [competitions, setCompetitions] = useState<CompetitionRecord[]>([]);
  const [selectedCompetitionUuid, setSelectedCompetitionUuid] = useState(initialCompetitionUuid);
  const [settings, setSettings] = useState<CompetitionSettingsRecord>();
  const [createdExpert, setCreatedExpert] = useState<ExpertRecord>();

  useEffect(() => {
    let mounted = true;
    setLoadingCompetitions(true);
    listCompetitions({ status: 'published', pageNo: 1, pageSize: 100 })
      .then((result) => {
        if (mounted) {
          setCompetitions(result.records || []);
        }
      })
      .catch((error) => {
        if (mounted) {
          showErrorMessage(error, '可申请赛事加载失败');
        }
      })
      .finally(() => {
        if (mounted) {
          setLoadingCompetitions(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedCompetitionUuid) {
      setSettings(undefined);
      return;
    }
    let mounted = true;
    setLoadingSettings(true);
    getCompetitionSettings(selectedCompetitionUuid)
      .then((result) => {
        if (mounted) {
          setSettings(result);
          form.resetFields();
          setStep(0);
        }
      })
      .catch((error) => {
        if (mounted) {
          setSettings(undefined);
          showErrorMessage(error, '赛事专家要求加载失败');
        }
      })
      .finally(() => {
        if (mounted) {
          setLoadingSettings(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, [form, selectedCompetitionUuid]);

  const fields = useMemo(
    () => parseExpertApplicationFields(settings?.fields || []),
    [settings?.fields],
  );
  const selectedCompetition = settings?.competition
    || competitions.find((competition) => competition.uuid === selectedCompetitionUuid);

  const startFilling = () => {
    if (!selectedCompetitionUuid || !settings) {
      message.warning('请先选择要申请的赛事');
      return;
    }
    setStep(1);
  };

  const submitApplication = async () => {
    if (!selectedCompetitionUuid) {
      message.warning('请先选择要申请的赛事');
      return;
    }
    const values = await form.validateFields();
    setSaving(true);
    try {
      const created = await createExpert(buildExpertApplicationPayload(fields, values, selectedCompetitionUuid));
      setCreatedExpert(created);
      message.success('专家申请已提交审批');
      setStep(2);
    } catch (error) {
      showErrorMessage(error, '专家申请提交失败');
    } finally {
      setSaving(false);
    }
  };

  const renderedFields = fields.map((field, index) => {
    const previousField = fields[index - 1];
    const groupHeading = field.groupLabel && field.groupLabel !== previousField?.groupLabel
      ? <Typography.Title level={5} style={{ marginTop: index ? 24 : 0 }}>{field.groupLabel}</Typography.Title>
      : null;
    return (
      <div key={field.itemKey}>
        {groupHeading}
        <Form.Item
          name={field.itemKey}
          label={field.title}
          rules={buildExpertApplicationFieldRules(field)}
          extra={field.description}
        >
          {renderExpertApplicationInput(field)}
        </Form.Item>
      </div>
    );
  });

  return (
    <ManagementPage
      title="专家申请"
      extra={<Button onClick={() => history.push('/competitions/register')}>返回赛事报名</Button>}
    >
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
                <Typography.Title level={4}>选择申请赛事</Typography.Title>
                <Typography.Paragraph>
                  专家要求由赛事管理独立配置。选择赛事后，系统会按该赛事的专家信息表单收集申请资料。
                </Typography.Paragraph>
                <div className="competition-expert-application-selection">
                  <Select
                    showSearch
                    optionFilterProp="label"
                    loading={loadingCompetitions || loadingSettings}
                    placeholder="请选择要申请的赛事"
                    value={selectedCompetitionUuid || undefined}
                    options={competitions
                      .filter((competition) => competition.uuid)
                      .map((competition) => ({ label: competition.title, value: competition.uuid }))}
                    onChange={setSelectedCompetitionUuid}
                    style={{ width: '100%' }}
                  />
                  {loadingSettings ? <Spin size="small" /> : null}
                  {selectedCompetition ? (
                    <Typography.Paragraph
                      className="competition-expert-application-selection__description"
                      type="secondary"
                    >
                      当前赛事：{selectedCompetition.title}，已配置 {fields.length} 项专家信息。
                    </Typography.Paragraph>
                  ) : null}
                  {!loadingSettings && selectedCompetitionUuid && !settings ? (
                    <Alert showIcon type="error" title="无法读取该赛事的专家申请要求，请刷新后重试。" />
                  ) : null}
                  <Button type="primary" disabled={!settings || loadingSettings} onClick={startFilling}>
                    开始填写
                  </Button>
                </div>
              </div>
            ) : null}

            <Form
              form={form}
              layout="vertical"
              className="expert-form-grid"
              style={{ display: step === 1 ? undefined : 'none' }}
            >
              {step === 1 ? renderedFields : null}
            </Form>
            {step === 1 ? (
              <Space className="competition-application-actions">
                <Button onClick={() => setStep(0)}>上一步</Button>
                <Button type="primary" loading={saving} onClick={() => void submitApplication()}>
                  提交申请
                </Button>
              </Space>
            ) : null}

            {step === 2 ? (
              <Result
                status="success"
                title="专家申请已提交"
                subTitle={createdExpert
                  ? `申请赛事：${selectedCompetition?.title || '当前赛事'}。专家编码：${createdExpert.code}，审批通过后系统会发送账号激活邮件。`
                  : undefined}
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
