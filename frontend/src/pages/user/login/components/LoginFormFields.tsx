import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { ProFormCheckbox, ProFormText } from '@ant-design/pro-components';
import { Alert, Button, Form, Image, Input, Skeleton, Space, Typography } from 'antd';
import { getCaptchaValueFromEvent, shouldBlockCaptchaKey } from '@/pages/user/login/captchaInput';
import type { CaptchaChallenge, LoginResponse } from '@/types/api';

interface LoginFormFieldsProps {
  pendingSecondFactorLogin: LoginResponse | null;
  pendingSecondFactorPrompt: string;
  securityCaptchaEnabled: boolean;
  captchaChallenge: CaptchaChallenge | null;
  captchaLoading: boolean;
  captchaImageLoadFailed: boolean;
  loginEncryptionLoading: boolean;
  onRefreshCaptcha: () => void;
  onCaptchaImageError: () => void;
}

export const LoginFormFields = ({
  pendingSecondFactorLogin,
  pendingSecondFactorPrompt,
  securityCaptchaEnabled,
  captchaChallenge,
  captchaLoading,
  captchaImageLoadFailed,
  loginEncryptionLoading,
  onRefreshCaptcha,
  onCaptchaImageError,
}: LoginFormFieldsProps) => (
  <>
    <ProFormText
      name="username"
      fieldProps={{
        prefix: <UserOutlined className="saas-login-page__field-icon" />,
        size: 'large',
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
        size: 'large',
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
        <ProFormText
          name="verificationCode"
          fieldProps={{
            size: 'large',
            autoComplete: 'one-time-code',
            inputMode: 'numeric',
          }}
          placeholder="请输入验证码"
          rules={[{ required: true, message: '请输入验证码' }]}
        />
      </>
    ) : null}
    {loginEncryptionLoading ? (
      <Typography.Text type="secondary">
        <Space size={8}>
          <Skeleton.Avatar active size="small" shape="circle" />
          正在加载登录加密信息...
        </Space>
      </Typography.Text>
    ) : null}
    {!pendingSecondFactorLogin && securityCaptchaEnabled ? (
      <div className="saas-login-page__captcha-row">
        <Form.Item
          key={captchaChallenge?.captchaId || 'captcha-code'}
          name="captchaCode"
          rules={[{ required: true, message: '请输入验证码' }]}
          getValueFromEvent={getCaptchaValueFromEvent}
          className="saas-login-page__captcha-input"
        >
          <Input
            size="large"
            autoComplete="off"
            autoCapitalize="off"
            autoCorrect="off"
            lang="en"
            spellCheck={false}
            inputMode="text"
            maxLength={8}
            placeholder="请输入验证码"
            aria-label="验证码"
            className="saas-login-page__captcha-native-input"
            onCompositionStart={(event) => event.preventDefault()}
            onKeyDown={(event) => {
              if (shouldBlockCaptchaKey(event)) {
                event.preventDefault();
              }
            }}
          />
        </Form.Item>
        <Button
          size="large"
          aria-label="刷新验证码"
          title="点击刷新验证码"
          onClick={onRefreshCaptcha}
          className="saas-login-page__captcha-image-button"
        >
          {captchaLoading ? (
            <Skeleton.Image active className="saas-login-page__captcha-skeleton" />
          ) : captchaImageLoadFailed ? (
            <Typography.Text type="secondary">点击重试</Typography.Text>
          ) : captchaChallenge?.imageUrl ? (
            <Image
              src={captchaChallenge.imageUrl}
              alt="验证码"
              preview={false}
              onError={onCaptchaImageError}
              className="saas-login-page__captcha-image"
            />
          ) : (
            <Typography.Text type="secondary">点击刷新</Typography.Text>
          )}
        </Button>
      </div>
    ) : null}
    <div className="saas-login-page__actions">
      <ProFormCheckbox noStyle name="remember">
        保持登录状态
      </ProFormCheckbox>
    </div>
  </>
);
