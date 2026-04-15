import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { ProFormCheckbox, ProFormText } from '@ant-design/pro-components';
import { Alert, Button, Form, Input, Select, Spin, Typography } from 'antd';
import type { CaptchaChallenge, LoginResponse } from '@/types/api';

interface LoginFormFieldsProps {
  pendingSecondFactorLogin: LoginResponse | null;
  pendingSecondFactorOptions: NonNullable<LoginResponse['secondFactorOptions']>;
  pendingSecondFactorOption: NonNullable<LoginResponse['secondFactorOptions']>[number] | null;
  pendingSecondFactorPrompt: string;
  selectedSecondFactorChallengeId: string | null;
  securityCaptchaEnabled: boolean;
  captchaChallenge: CaptchaChallenge | null;
  captchaLoading: boolean;
  captchaImageLoadFailed: boolean;
  loginEncryptionLoading: boolean;
  onSecondFactorChange: (challengeId: string) => void;
  onResetSecondFactorFlow: () => void;
  onRefreshCaptcha: () => void;
  onCaptchaImageError: () => void;
}

export const LoginFormFields = ({
  pendingSecondFactorLogin,
  pendingSecondFactorOptions,
  pendingSecondFactorOption,
  pendingSecondFactorPrompt,
  selectedSecondFactorChallengeId,
  securityCaptchaEnabled,
  captchaChallenge,
  captchaLoading,
  captchaImageLoadFailed,
  loginEncryptionLoading,
  onSecondFactorChange,
  onResetSecondFactorFlow,
  onRefreshCaptcha,
  onCaptchaImageError,
}: LoginFormFieldsProps) => (
  <>
    <ProFormText
      name="username"
      fieldProps={{
        prefix: <UserOutlined className="saas-login-page__field-icon" />,
        autoComplete: 'username',
        disabled: Boolean(pendingSecondFactorLogin),
      }}
      placeholder="请输入账号"
      rules={[{ required: true, message: '请输入账号' }]}
    />
    <ProFormText.Password
      name="password"
      fieldProps={{
        prefix: <LockOutlined className="saas-login-page__field-icon" />,
        autoComplete: 'current-password',
        disabled: Boolean(pendingSecondFactorLogin),
      }}
      placeholder="请输入密码"
      rules={[
        { required: true, message: '请输入密码' },
        { min: 6, message: '密码长度不能少于 6 位' },
      ]}
    />
    {pendingSecondFactorLogin ? (
      <>
        <Alert showIcon type="info" message={pendingSecondFactorPrompt} />
        {pendingSecondFactorOptions.length > 1 ? (
          <Select
            value={selectedSecondFactorChallengeId || pendingSecondFactorOption?.challengeId}
            onChange={onSecondFactorChange}
            options={pendingSecondFactorOptions.map((option) => ({
              value: option.challengeId,
              label: option.promptMessage || `${option.factorName} · ${option.pluginName}`,
            }))}
            placeholder="请选择二次验证方式"
            style={{ width: '100%', marginBottom: 12 }}
          />
        ) : null}
        <ProFormText
          name="verificationCode"
          fieldProps={{
            autoComplete: 'one-time-code',
            inputMode: 'numeric',
          }}
          placeholder="请输入验证码"
          rules={[{ required: true, message: '请输入验证码' }]}
        />
        <Button type="link" onClick={onResetSecondFactorFlow} style={{ padding: 0, height: 'auto' }}>
          返回重新登录
        </Button>
      </>
    ) : null}
    {loginEncryptionLoading ? <Typography.Text type="secondary">正在加载登录加密信息...</Typography.Text> : null}
    {!pendingSecondFactorLogin && securityCaptchaEnabled ? (
      <div className="saas-login-page__captcha-section">
        <div className="saas-login-page__captcha-input">
          <Form.Item key={captchaChallenge?.captchaId || 'captcha-code'} name="captchaCode" rules={[{ required: true, message: '请输入验证码' }]}>
            <Input size="large" autoComplete="off" spellCheck={false} maxLength={8} placeholder="请输入验证码" aria-label="验证码" />
          </Form.Item>
        </div>
        <button type="button" className="saas-login-page__captcha-media" title="刷新验证码" aria-label="刷新验证码" onClick={onRefreshCaptcha}>
          <span className="saas-login-page__captcha-image">
            {captchaLoading ? (
              <span className="saas-login-page__captcha-loading">
                <Spin size="small" />
              </span>
            ) : captchaImageLoadFailed ? (
              <Typography.Text className="saas-login-page__captcha-placeholder">图片加载失败，点击重试</Typography.Text>
            ) : captchaChallenge?.imageUrl ? (
              <img src={captchaChallenge.imageUrl} alt="验证码" onError={onCaptchaImageError} />
            ) : (
              <Typography.Text className="saas-login-page__captcha-placeholder">点击刷新验证码</Typography.Text>
            )}
          </span>
        </button>
      </div>
    ) : null}
    <div className="saas-login-page__actions">
      <ProFormCheckbox noStyle name="remember">
        保持登录状态
      </ProFormCheckbox>
    </div>
  </>
);
