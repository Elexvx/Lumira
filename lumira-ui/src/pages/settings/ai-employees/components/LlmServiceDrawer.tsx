import { Alert, Col, Form, Input, InputNumber, Row, Select, Space, Switch, Typography, type FormInstance } from 'antd';
import { SyncOutlined } from '@ant-design/icons';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import type { AiLlmServiceRecord, AiLlmServiceTestResult } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { LLM_SERVICE_DRAWER_WIDTH_BY_BREAKPOINT } from '@/constants/ui';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

export type LlmFormValues = {
  provider?: string;
  code?: string;
  title?: string;
  baseUrl?: string;
  apiKey?: string;
  defaultModel?: string;
  enabled?: boolean;
  timeoutMs?: number;
  temperature?: number;
  maxTokens?: number;
};

interface LlmServiceDrawerProps {
  open: boolean;
  title: string;
  form: FormInstance<LlmFormValues>;
  selectedService: AiLlmServiceRecord | null;
  llmTestResult: AiLlmServiceTestResult | null;
  llmTesting: boolean;
  llmSaving: boolean;
  canSaveLlmService: boolean;
  canRunTest: boolean;
  providerOptions: Array<{ label: string; value: string }>;
  onClose: () => void;
  onProviderChange: (provider: string) => void;
  onSave: () => void;
  onTest: () => void;
  onValuesChange: () => void;
}

export const LlmServiceDrawer = ({
  open,
  title,
  form,
  selectedService,
  llmTestResult,
  llmTesting,
  llmSaving,
  canSaveLlmService,
  canRunTest,
  providerOptions,
  onClose,
  onProviderChange,
  onSave,
  onTest,
  onValuesChange,
}: LlmServiceDrawerProps) => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const rowGutter = resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile);
  const microGap = resolveResponsiveValue(APP_SPACING.microGap, isMobile);

  return (
    <ManagementDrawer
      title={title}
      open={open}
      onClose={onClose}
      width={resolveResponsiveValue(LLM_SERVICE_DRAWER_WIDTH_BY_BREAKPOINT, isMobile)}
      footerActions={[
        {
          key: 'test',
          label: (
            <Space size={microGap}>
              <SyncOutlined />
              {t('测试连接', 'Test connection')}
            </Space>
          ),
          loading: llmTesting,
          disabled: llmSaving || !canRunTest,
          onClick: onTest,
        },
        { key: 'cancel', label: t('取消', 'Cancel'), onClick: onClose },
        { key: 'save', label: t('保存', 'Save'), type: 'primary', loading: llmSaving, disabled: !canSaveLlmService, onClick: onSave },
      ]}
    >
      <Form layout="vertical" form={form} onValuesChange={onValuesChange}>
      <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
          <Row gutter={rowGutter}>
            <Col xs={24} md={12}>
              <Form.Item label={t('LLM 类型', 'LLM type')} name="provider" rules={[{ required: true, message: t('请选择 LLM 类型', 'Please select an LLM type') }]}>
                <Select options={providerOptions} placeholder={t('请选择供应商类型', 'Please select a provider type')} onChange={onProviderChange} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label={t('唯一标识', 'Code')} name="code" rules={[{ required: true, message: t('请输入唯一标识', 'Please enter the code') }]}>
                <Input placeholder={t('例如：default-chat', 'e.g. default-chat')} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={rowGutter}>
            <Col xs={24} md={12}>
              <Form.Item label={t('标题', 'Title')} name="title" rules={[{ required: true, message: t('请输入标题', 'Please enter the title') }]}>
                <Input placeholder={t('例如：默认对话模型', 'e.g. Default chat model')} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label={t('默认模型', 'Default model')} name="defaultModel">
                <Input placeholder={t('例如：qwen-plus / qwen-plus-latest / deepseek-v4-flash', 'e.g. qwen-plus / qwen-plus-latest / deepseek-v4-flash')} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="Base URL" name="baseUrl">
            <Input placeholder={t('阿里云百炼：https://dashscope.aliyuncs.com/compatible-mode/v1', 'Alibaba Cloud Bailian: https://dashscope.aliyuncs.com/compatible-mode/v1')} />
          </Form.Item>
          <Form.Item label="API Key" name="apiKey">
            <Input.Password
              placeholder={selectedService?.apiKeyConfigured ? t('留空则使用已保存 API Key', 'Leave blank to use the saved API Key') : t('请输入 API Key', 'Please enter the API Key')}
              autoComplete="off"
            />
          </Form.Item>
          {llmTestResult ? (
            <Alert
              showIcon
              type={llmTestResult.success ? 'success' : 'error'}
              message={llmTestResult.success ? t('测试通过', 'Test passed') : t('测试失败', 'Test failed')}
              description={
                <Space direction="vertical" size={microGap}>
                  <Typography.Text>
                    {llmTestResult.message || (llmTestResult.success ? t('当前 LLM 服务可正常响应', 'The LLM service is responding normally') : t('请检查 Base URL、模型和 API Key', 'Please check the Base URL, model, and API Key'))}
                  </Typography.Text>
                  {llmTestResult.success ? (
                    <Typography.Text type="secondary">
                      {[
                        llmTestResult.model ? `${t('模型', 'Model')}: ${llmTestResult.model}` : null,
                        llmTestResult.latencyMs != null ? `${t('耗时', 'Latency')}: ${llmTestResult.latencyMs} ms` : null,
                        llmTestResult.replyText ? `${t('响应', 'Response')}: ${llmTestResult.replyText}` : null,
                      ]
                        .filter(Boolean)
                        .join(' | ')}
                    </Typography.Text>
                  ) : null}
                </Space>
              }
            />
          ) : null}
          <Row gutter={rowGutter}>
            <Col xs={24} md={8}>
              <Form.Item label={t('超时时间（毫秒）', 'Timeout (ms)')} name="timeoutMs">
                <InputNumber min={1000} step={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Temperature" name="temperature">
                <InputNumber min={0} max={2} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Max Tokens" name="maxTokens">
                <InputNumber min={1} step={128} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label={t('启用状态', 'Enabled status')} name="enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Space>
      </Form>
    </ManagementDrawer>
  );
};
