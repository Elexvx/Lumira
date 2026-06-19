import { Alert, Avatar, Button, Card, Checkbox, Col, Form, Input, Radio, Row, Select, Space, Tag, Tabs, Typography } from 'antd';
import type { FormInstance } from 'antd';
import type { ReactNode } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import type { AiEmployeeCapabilityRecord } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

export type AvatarOption = {
  key: string;
  label: string;
  color: string;
  icon: ReactNode;
};

export type EmployeeFormValues = {
  username?: string;
  nickname?: string;
  position?: string;
  avatarKey?: string;
  description?: string;
  greeting?: string;
  systemPrompt?: string;
  defaultLlmServiceId?: number;
};

interface EmployeeDrawerProps {
  open: boolean;
  title: string;
  form: FormInstance<EmployeeFormValues>;
  employeePromptTemplate: string;
  avatarOptions: AvatarOption[];
  llmServiceOptions: Array<{ label: string; value: number }>;
  knowledgeBaseOptions: Array<{ label: string; value: number }>;
  employeeKnowledgeBaseIds: number[];
  employeeCapabilities: AiEmployeeCapabilityRecord[];
  employeeCapabilityModes: Record<string, AiEmployeeCapabilityRecord['permissionMode']>;
  editingId?: number | null;
  saving: boolean;
  canSave: boolean;
  onClose: () => void;
  onSave: () => void;
  onKnowledgeBaseIdsChange: (values: number[]) => void;
  onCapabilityModeChange: (capabilityCode: string, checked: boolean, readOnly: boolean) => void;
}

const DEFAULT_AVATAR_KEY = 'avatar-purple-01';

const groupCapabilities = (capabilities: AiEmployeeCapabilityRecord[]) =>
  capabilities.reduce<Record<string, AiEmployeeCapabilityRecord[]>>((result, capability) => {
    const groupName = capability.category || t('其他能力', 'Other capabilities');
    result[groupName] = [...(result[groupName] || []), capability];
    return result;
  }, {});

export const EmployeeDrawer = ({
  open,
  title,
  form,
  employeePromptTemplate,
  avatarOptions,
  llmServiceOptions,
  knowledgeBaseOptions,
  employeeKnowledgeBaseIds,
  employeeCapabilities,
  employeeCapabilityModes,
  editingId,
  saving,
  canSave,
  onClose,
  onSave,
  onKnowledgeBaseIdsChange,
  onCapabilityModeChange,
}: EmployeeDrawerProps) => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const rowGutter = resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile);
  const microGap = resolveResponsiveValue(APP_SPACING.microGap, isMobile);
  const mediumGap = resolveResponsiveValue(APP_SPACING.modalFooterGap, isMobile);
  const groupedCapabilities = groupCapabilities(employeeCapabilities);

  return (
    <ManagementDrawer
      title={title}
      open={open}
      onClose={onClose}
      width={STANDARD_DRAWER_WIDTH}
      footerActions={[
        { key: 'cancel', label: t('取消', 'Cancel'), onClick: onClose },
        { key: 'save', label: t('保存', 'Save'), type: 'primary', loading: saving, disabled: !canSave, onClick: onSave },
      ]}
      >
      <Form layout="vertical" form={form} initialValues={{ avatarKey: DEFAULT_AVATAR_KEY, systemPrompt: employeePromptTemplate }}>
        <Tabs
          defaultActiveKey="basic"
          items={[
            {
              key: 'basic',
              label: t('员工资料', 'Profile'),
              children: (
                <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                  <Row gutter={rowGutter}>
                    <Col xs={24} md={12}>
                      <Form.Item
                        label={t('用户名', 'Username')}
                        name="username"
                        rules={[
                          { required: true, message: t('请输入用户名', 'Please enter a username') },
                          { pattern: /^[a-z][a-zA-Z0-9-]*$/, message: t('用户名需为 lowerCamelCase 或短横线格式', 'The username must be in lowerCamelCase or hyphen format') },
                        ]}
                      >
                        <Input placeholder={t('例如：aiAssistant', 'e.g. aiAssistant')} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label={t('昵称', 'Nickname')} name="nickname" rules={[{ required: true, message: t('请输入昵称', 'Please enter a nickname') }]}>
                        <Input placeholder={t('例如：小助手', 'e.g. Assistant')} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={rowGutter}>
                    <Col xs={24} md={12}>
                      <Form.Item label={t('职位', 'Position')} name="position">
                        <Input placeholder={t('例如：智能客服', 'e.g. AI support agent')} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label={t('默认 LLM 服务', 'Default LLM service')} name="defaultLlmServiceId">
                        <Select allowClear options={llmServiceOptions} placeholder={t('请选择默认模型服务（可选）', 'Select a default model service (optional)')} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Form.Item label={t('头像', 'Avatar')} name="avatarKey">
                    <Radio.Group>
                      <Space wrap>
                        {avatarOptions.map((option) => (
                          <Radio key={option.key} value={option.key}>
                            <Space direction="vertical" align="center" size={0}>
                              <Avatar style={{ backgroundColor: option.color }} icon={option.icon} />
                            </Space>
                          </Radio>
                        ))}
                      </Space>
                    </Radio.Group>
                  </Form.Item>
                  <Form.Item label={t('简介', 'Description')} name="description">
                    <Input.TextArea rows={3} placeholder={t('简单说明这个 AI 员工的职责与边界', 'Briefly describe this AI employee’s responsibilities and boundaries')} />
                  </Form.Item>
                  <Form.Item label={t('问候语', 'Greeting')} name="greeting">
                    <Input.TextArea rows={2} placeholder={t('用户打开对话时展示的欢迎语', 'Welcome message shown when the user opens a conversation')} />
                  </Form.Item>
                </Space>
              ),
            },
            {
              key: 'prompt',
              label: t('人物设定', 'Persona'),
              children: (
                <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                  <Alert type="info" showIcon message={t('AI 模型的系统提示词，决定了‘我’是谁，遵循哪些要求来工作和完成任务。', 'The system prompt defines who this AI is and the rules it follows to work and complete tasks.')} />
                  <Space wrap>
                    <Button
                      onClick={() => {
                        form.setFieldValue('systemPrompt', '');
                      }}
                    >
                      {t('清空', 'Clear')}
                    </Button>
                    <Button
                      onClick={() => {
                        form.setFieldValue('systemPrompt', employeePromptTemplate);
                      }}
                    >
                      {t('恢复默认模板', 'Restore default template')}
                    </Button>
                  </Space>
                  <Form.Item name="systemPrompt" label="systemPrompt">
                    <Input.TextArea rows={12} placeholder={t('请输入系统提示词', 'Please enter the system prompt')} />
                  </Form.Item>
                </Space>
              ),
            },
            {
              key: 'security',
              label: t('能力边界', 'Capability boundaries'),
              children: (
                <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                  <Alert
                    type="info"
                    showIcon
                    message={t('能力边界按每个 AI 员工独立配置；查看类能力只允许访问或禁用，增删改启停类能力只允许允许或禁用，写入动作仍会二次确认。', 'Capability boundaries are configured per AI employee. View-only capabilities can only be allowed or disabled; create/update/delete/enable/disable capabilities can only be allowed or disabled; write actions still require confirmation.')}
                  />
                  <Form.Item label={t('绑定知识库', 'Bound knowledge bases')}>
                    <Select
                      mode="multiple"
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      placeholder={t('选择该 AI 员工回答时可引用的知识库', 'Select knowledge bases this AI employee may reference when answering')}
                      options={knowledgeBaseOptions}
                      value={employeeKnowledgeBaseIds}
                      onChange={(values) => onKnowledgeBaseIdsChange(values.map(Number))}
                    />
                  </Form.Item>
                  {editingId ? (
                    Object.entries(groupedCapabilities).map(([groupName, items]) => (
                      <Card key={groupName} size="small" title={groupName}>
                        <Space direction="vertical" size={mediumGap} style={{ width: '100%' }}>
                          {items.map((capability) => {
                            const checked = (employeeCapabilityModes[capability.capabilityCode] || capability.permissionMode) !== 'deny';
                            return (
                              <div key={capability.capabilityCode} style={{ display: 'flex', justifyContent: 'space-between', gap: mediumGap }}>
                                <Space direction="vertical" size={microGap}>
                                  <Space wrap>
                                    <Typography.Text strong>{capability.capabilityName}</Typography.Text>
                                    <Tag>{capability.capabilityCode}</Tag>
                                    {capability.riskLevel ? <Tag color={capability.riskLevel === 'HIGH' ? 'red' : capability.riskLevel === 'MEDIUM' ? 'orange' : 'green'}>{capability.riskLevel}</Tag> : null}
                                    {capability.needConfirm ? <Tag color="volcano">{t('二次确认', 'Confirm before action')}</Tag> : null}
                                  </Space>
                                  <Typography.Text type="secondary">{capability.description || t('暂无描述', 'No description')}</Typography.Text>
                                </Space>
                                <Checkbox
                                  checked={checked}
                                  onChange={(event) => {
                                    onCapabilityModeChange(capability.capabilityCode, event.target.checked, Boolean(capability.readOnly));
                                  }}
                                >
                                  {capability.readOnly ? t('可查看', 'View only') : t('可操作', 'Can operate')}
                                </Checkbox>
                              </div>
                            );
                          })}
                        </Space>
                      </Card>
                    ))
                  ) : (
                    <Alert type="warning" showIcon message={t('请先创建 AI 员工，保存后再编辑它的能力边界。', 'Please create the AI employee first, then save before editing its capability boundaries.')} />
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Form>
    </ManagementDrawer>
  );
};
