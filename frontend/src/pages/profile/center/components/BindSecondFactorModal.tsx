import { Button, Alert, Card, Descriptions, Empty, Form, Input, Modal, QRCode, Result, Space, Tag, Typography, Divider } from 'antd';
import { StepsForm } from '@ant-design/pro-components';
import type { DescriptionsProps } from 'antd';
import type { SecondFactorChallenge, SecondFactorProviderStatus } from '@/types/api';

interface BindAlertState {
  type: 'info' | 'warning' | 'error';
  message: string;
}

interface BindSecondFactorModalProps {
  open: boolean;
  bindingProvider: SecondFactorProviderStatus | null;
  bindingChallenge: SecondFactorChallenge | null;
  bindingCompleted: boolean;
  bindingIsSms: boolean;
  bindingLoading: boolean;
  bindingSubmitting: boolean;
  bindingAlert?: BindAlertState;
  singleColumnDescriptionsProps: DescriptionsProps;
  onCancel: () => void;
  onRetry: () => void;
  onFinish: () => void;
  onVerify: (values: { verificationCode?: string }) => Promise<boolean>;
}

const BindStepSubmitter = ({
  bindingSubmitting,
  bindingLoading,
  hasChallenge,
  showRetry,
  onCancel,
  onRetry,
}: {
  bindingSubmitting: boolean;
  bindingLoading: boolean;
  hasChallenge: boolean;
  showRetry?: boolean;
  onCancel: () => void;
  onRetry: () => void;
}) => ({
  render: (props: { step: number; onPre?: () => void; onSubmit?: () => void }) => (
    <Space size={8} wrap>
      <Button onClick={onCancel} disabled={bindingSubmitting}>
        取消
      </Button>
      {props.step > 0 ? (
        <Button onClick={props.onPre} disabled={bindingLoading || bindingSubmitting}>
          上一步
        </Button>
      ) : null}
      {showRetry ? (
        <Button onClick={onRetry} disabled={bindingLoading || bindingSubmitting || !hasChallenge}>
          重新发送验证码
        </Button>
      ) : null}
      <Button
        type="primary"
        loading={bindingLoading || bindingSubmitting}
        disabled={bindingLoading || bindingSubmitting || !hasChallenge}
        onClick={props.onSubmit}
      >
        {props.step === 0 ? '下一步' : '确认绑定'}
      </Button>
    </Space>
  ),
});

export const BindSecondFactorModal = ({
  open,
  bindingProvider,
  bindingChallenge,
  bindingCompleted,
  bindingIsSms,
  bindingLoading,
  bindingSubmitting,
  bindingAlert,
  singleColumnDescriptionsProps,
  onCancel,
  onRetry,
  onFinish,
  onVerify,
}: BindSecondFactorModalProps) => (
  <Modal
    title={
      bindingProvider
        ? `${bindingProvider.pluginName || bindingProvider.pluginCode} · ${bindingIsSms ? '短信验证码绑定' : '2FA 绑定'}`
        : '二次验证绑定'
    }
    open={open}
    onCancel={onCancel}
    footer={null}
    width={780}
    destroyOnClose
    maskClosable={false}
  >
    {bindingCompleted && bindingChallenge ? (
      bindingIsSms ? (
        <Result
          status="success"
          title="短信验证码绑定已完成"
          subTitle="后续登录或验证时会向该手机号发送短信验证码。"
          extra={[
            <Button key="close" type="primary" onClick={onFinish}>
              完成
            </Button>,
          ]}
          style={{ padding: 0 }}
        />
      ) : (
        <Result
          status="success"
          title="绑定已完成"
          subTitle="请妥善保存以下恢复码，用于设备丢失或验证码不可用时找回账号。"
          extra={[
            <Button key="close" type="primary" onClick={onFinish}>
              完成
            </Button>,
          ]}
          style={{ padding: 0 }}
        >
          <Card size="small" title="恢复码">
            <Space wrap>
              {(bindingChallenge.recoveryCodes || []).length ? (
                bindingChallenge.recoveryCodes!.map((code) => (
                  <Tag key={code} color="gold">
                    {code}
                  </Tag>
                ))
              ) : (
                <Typography.Text type="secondary">暂无恢复码</Typography.Text>
              )}
            </Space>
            <Divider />
            <Typography.Paragraph style={{ marginBottom: 0 }} type="secondary" copyable={{ text: (bindingChallenge.recoveryCodes || []).join('\n') }}>
              点击复制全部恢复码
            </Typography.Paragraph>
          </Card>
        </Result>
      )
    ) : bindingIsSms ? (
      <StepsForm
        submitter={BindStepSubmitter({
          bindingSubmitting,
          bindingLoading,
          hasChallenge: Boolean(bindingChallenge),
          showRetry: true,
          onCancel,
          onRetry,
        })}
        stepsProps={{ responsive: false }}
        formProps={{ layout: 'vertical' }}
        onFinish={onVerify}
        stepsFormRender={(formDom, submitterDom) => (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {bindingAlert ? <Alert showIcon type={bindingAlert.type} message={bindingAlert.message} /> : null}
            {formDom}
            {submitterDom}
          </Space>
        )}
      >
        <StepsForm.StepForm name="sms-verify" title="接收验证码">
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Alert
              showIcon
              type="info"
              message="短信验证码已发送"
              description={
                bindingChallenge?.maskedContact
                  ? `验证码已发送至 ${bindingChallenge.maskedContact}，请输入收到的 6 位短信验证码完成绑定。`
                  : '验证码已发送至手机号，请输入收到的 6 位短信验证码完成绑定。'
              }
            />
            {bindingLoading ? (
              <Card loading />
            ) : bindingChallenge ? (
              <Descriptions {...singleColumnDescriptionsProps}>
                <Descriptions.Item label="插件">{bindingChallenge.pluginName || bindingChallenge.pluginCode || '-'}</Descriptions.Item>
                <Descriptions.Item label="验证方式">{bindingChallenge.factorName || '短信验证码'}</Descriptions.Item>
                <Descriptions.Item label="绑定标识">{bindingChallenge.maskedContact || '-'}</Descriptions.Item>
                <Descriptions.Item label="提示信息">{bindingChallenge.promptMessage || '请输入收到的短信验证码'}</Descriptions.Item>
              </Descriptions>
            ) : (
              <Empty
                description={
                  <Space direction="vertical" size={8}>
                    <span>绑定信息尚未加载，请重试</span>
                    <Button type="primary" onClick={onRetry} disabled={!bindingProvider}>
                      重新获取绑定信息
                    </Button>
                  </Space>
                }
              />
            )}
          </Space>
        </StepsForm.StepForm>
        <StepsForm.StepForm name="sms-input" title="输入短信验证码">
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Alert
              showIcon
              type="info"
              message="输入短信验证码"
              description="请填写手机收到的 6 位短信验证码，校验成功后即完成绑定。"
            />
            <Form.Item
              name="verificationCode"
              rules={[
                { required: true, message: '请输入短信验证码' },
                { pattern: /^\d{6}$/, message: '验证码必须为 6 位数字' },
              ]}
            >
              <Input size="large" maxLength={6} inputMode="numeric" autoComplete="one-time-code" placeholder="请输入 6 位短信验证码" />
            </Form.Item>
          </Space>
        </StepsForm.StepForm>
      </StepsForm>
    ) : (
      <StepsForm
        submitter={BindStepSubmitter({
          bindingSubmitting,
          bindingLoading,
          hasChallenge: Boolean(bindingChallenge),
          onCancel,
          onRetry,
        })}
        stepsProps={{ responsive: false }}
        formProps={{ layout: 'vertical' }}
        onFinish={onVerify}
        stepsFormRender={(formDom, submitterDom) => (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {bindingAlert ? <Alert showIcon type={bindingAlert.type} message={bindingAlert.message} /> : null}
            {formDom}
            {submitterDom}
          </Space>
        )}
      >
        <StepsForm.StepForm name="bind-preview" title="扫描二维码">
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Alert
              showIcon
              type="info"
              message="扫码绑定"
              description={
                bindingProvider?.bound
                  ? '当前已绑定，重新绑定会生成新的密钥并覆盖旧绑定，请确认后继续。'
                  : '请使用支持 TOTP 的认证器扫描二维码。也可以手动输入密钥完成绑定。'
              }
            />
            {bindingLoading ? (
              <Card loading />
            ) : bindingChallenge ? (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <div className="saas-profile-2fa-binding__qr">
                  <QRCode value={bindingChallenge.setupUri || bindingChallenge.setupSecret || ''} size={188} bordered />
                </div>
                <Descriptions {...singleColumnDescriptionsProps}>
                  <Descriptions.Item label="插件">{bindingChallenge.pluginName || bindingChallenge.pluginCode || '-'}</Descriptions.Item>
                  <Descriptions.Item label="绑定标识">{bindingChallenge.maskedContact || '-'}</Descriptions.Item>
                  <Descriptions.Item label="手动密钥">
                    <Typography.Text copyable={{ text: bindingChallenge.setupSecret || '' }}>
                      {bindingChallenge.setupSecret || '-'}
                    </Typography.Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="绑定地址">
                    <Typography.Paragraph style={{ marginBottom: 0 }} copyable={{ text: bindingChallenge.setupUri || '' }}>
                      {bindingChallenge.setupUri || '-'}
                    </Typography.Paragraph>
                  </Descriptions.Item>
                </Descriptions>
                <Typography.Text type="secondary">下一步将要求你输入认证器中的首个 6 位验证码，确认成功后才算绑定完成。</Typography.Text>
              </Space>
            ) : (
              <Empty
                description={
                  <Space direction="vertical" size={8}>
                    <span>绑定信息尚未加载，请重试</span>
                    <Button type="primary" onClick={onRetry} disabled={!bindingProvider}>
                      重新获取绑定信息
                    </Button>
                  </Space>
                }
              />
            )}
          </Space>
        </StepsForm.StepForm>
        <StepsForm.StepForm name="bind-verify" title="验证首个验证码">
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Alert
              showIcon
              type="info"
              message="验证首个验证码"
              description="请在认证器中查看当前 6 位验证码并输入，系统会用它确认二维码已经成功绑定。"
            />
            <Form.Item
              name="verificationCode"
              rules={[
                { required: true, message: '请输入首个验证码' },
                { pattern: /^\d{6}$/, message: '验证码必须为 6 位数字' },
              ]}
            >
              <Input size="large" maxLength={6} inputMode="numeric" autoComplete="one-time-code" placeholder="请输入 6 位验证码" />
            </Form.Item>
          </Space>
        </StepsForm.StepForm>
      </StepsForm>
    )}
  </Modal>
);
