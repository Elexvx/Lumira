import { Alert, Col, Form, Input, InputNumber, Row, Select, Space, Switch, Typography, type FormInstance } from 'antd';
import { SyncOutlined } from '@ant-design/icons';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import type { AiLlmServiceRecord, AiLlmServiceTestResult } from '@/types/api';

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
  return (
    <ManagementDrawer
      title={title}
      open={open}
      onClose={onClose}
      width={700}
      footerActions={[
        {
          key: 'test',
          label: (
            <Space size={4}>
              <SyncOutlined />
              测试连接
            </Space>
          ),
          loading: llmTesting,
          disabled: llmSaving || !canRunTest,
          onClick: onTest,
        },
        { key: 'cancel', label: '取消', onClick: onClose },
        { key: 'save', label: '保存', type: 'primary', loading: llmSaving, disabled: !canSaveLlmService, onClick: onSave },
      ]}
    >
      <Form layout="vertical" form={form} onValuesChange={onValuesChange}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item label="LLM 类型" name="provider" rules={[{ required: true, message: '请选择 LLM 类型' }]}>
                <Select options={providerOptions} placeholder="请选择供应商类型" onChange={onProviderChange} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="唯一标识" name="code" rules={[{ required: true, message: '请输入唯一标识' }]}>
                <Input placeholder="例如：default-chat" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item label="标题" name="title" rules={[{ required: true, message: '请输入标题' }]}>
                <Input placeholder="例如：默认对话模型" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="默认模型" name="defaultModel">
                <Input placeholder="例如：qwen-plus / qwen-plus-latest / deepseek-v4-flash" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="Base URL" name="baseUrl">
            <Input placeholder="阿里云百炼：https://dashscope.aliyuncs.com/compatible-mode/v1" />
          </Form.Item>
          <Form.Item label="API Key" name="apiKey">
            <Input.Password
              placeholder={selectedService?.apiKeyConfigured ? '留空则使用已保存 API Key' : '请输入 API Key'}
              autoComplete="off"
            />
          </Form.Item>
          {llmTestResult ? (
            <Alert
              showIcon
              type={llmTestResult.success ? 'success' : 'error'}
              message={llmTestResult.success ? '测试通过' : '测试失败'}
              description={
                <Space direction="vertical" size={4}>
                  <Typography.Text>
                    {llmTestResult.message || (llmTestResult.success ? '当前 LLM 服务可正常响应' : '请检查 Base URL、模型和 API Key')}
                  </Typography.Text>
                  {llmTestResult.success ? (
                    <Typography.Text type="secondary">
                      {[
                        llmTestResult.model ? `模型：${llmTestResult.model}` : null,
                        llmTestResult.latencyMs != null ? `耗时：${llmTestResult.latencyMs} ms` : null,
                        llmTestResult.replyText ? `响应：${llmTestResult.replyText}` : null,
                      ]
                        .filter(Boolean)
                        .join(' ｜ ')}
                    </Typography.Text>
                  ) : null}
                </Space>
              }
            />
          ) : null}
          <Row gutter={16}>
            <Col xs={24} md={8}>
              <Form.Item label="超时时间（毫秒）" name="timeoutMs">
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
          <Form.Item label="启用状态" name="enabled" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
        </Space>
      </Form>
    </ManagementDrawer>
  );
};
