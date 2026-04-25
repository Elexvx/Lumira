import { LockOutlined, MailOutlined, MobileOutlined, UserOutlined } from '@ant-design/icons';
import { Form, Input, Space, Tabs, Typography, Checkbox, Button, Alert, Image, Skeleton } from 'antd';
import type { LoginCodeChallenge, LoginResponse, AgreementSettings } from '@/types/api';
import { getCaptchaValueFromEvent, shouldBlockCaptchaKey } from '@/pages/user/login/captchaInput';

export type LoginMode = 'password' | 'sms' | 'email';

export interface LoginFormValues {
  passwordAccount?: string;
  passwordPassword?: string;
  smsAccount?: string;
  smsVerificationCode?: string;
  emailAccount?: string;
  emailVerificationCode?: string;
  remember?: boolean;
  captchaCode?: string;
  agreementAccepted?: boolean;
  verificationCode?: string;
}

interface LoginFormFieldsProps {
  activeLoginMode: LoginMode;
  availableLoginModes: LoginMode[];
  agreementSettings: AgreementSettings;
  pendingSecondFactorLogin: LoginResponse | null;
  pendingSecondFactorPrompt: string;
  securityCaptchaEnabled: boolean;
  captchaChallenge: { captchaId: string; imageUrl?: string | null } | null;
  captchaLoading: boolean;
  captchaImageLoadFailed: boolean;
  loginEncryptionLoading: boolean;
  sendingLoginType: LoginMode | null;
  loginCodeChallenges: Partial<Record<Exclude<LoginMode, 'password'>, LoginCodeChallenge | null>>;
  onModeChange: (mode: LoginMode) => void;
  onSendLoginCode: (mode: Exclude<LoginMode, 'password'>) => void;
  onRefreshCaptcha: () => void;
  onCaptchaImageError: () => void;
  onOpenAgreementPreview: (previewKind: 'user' | 'privacy') => void;
}

const MODE_META: Record<LoginMode, { label: string; subtitle: string }> = {
  password: { label: '账号密码', subtitle: '账号密码登录' },
  sms: { label: '短信验证码', subtitle: '短信验证码登录' },
  email: { label: '邮箱验证码', subtitle: '邮箱验证码登录' },
};

const getAccountPlaceholder = (mode: Exclude<LoginMode, 'password'>) => (mode === 'sms' ? '请输入手机号' : '请输入邮箱');

export const LoginFormFields = ({
  activeLoginMode,
  availableLoginModes,
  agreementSettings,
  pendingSecondFactorLogin,
  pendingSecondFactorPrompt,
  securityCaptchaEnabled,
  captchaChallenge,
  captchaLoading,
  captchaImageLoadFailed,
  loginEncryptionLoading,
  sendingLoginType,
  loginCodeChallenges,
  onModeChange,
  onSendLoginCode,
  onRefreshCaptcha,
  onCaptchaImageError,
  onOpenAgreementPreview,
}: LoginFormFieldsProps) => {
  const form = Form.useFormInstance<LoginFormValues>();
  const smsAccount = Form.useWatch('smsAccount', form);
  const emailAccount = Form.useWatch('emailAccount', form);
  const hasAgreement = Boolean(agreementSettings.userAgreementMarkdown || agreementSettings.privacyAgreementMarkdown);
  const showTabs = availableLoginModes.length > 1 || availableLoginModes[0] !== 'password';
  const pendingChallenge = activeLoginMode === 'sms' ? loginCodeChallenges.sms : activeLoginMode === 'email' ? loginCodeChallenges.email : null;

  if (pendingSecondFactorLogin) {
    return (
      <>
        <Alert showIcon type="info" message={pendingSecondFactorPrompt} />
        <Form.Item
          name="verificationCode"
          rules={[{ required: true, message: '请输入验证码' }]}
        >
          <Input
            size="large"
            autoComplete="one-time-code"
            inputMode="numeric"
            placeholder="请输入验证码"
          />
        </Form.Item>
      </>
    );
  }

  const renderPasswordTab = () => (
    <>
      <Form.Item name="passwordAccount" rules={[{ required: true, message: '请输入账号' }]}>
        <Input
          size="large"
          prefix={<UserOutlined className="saas-login-page__field-icon" />}
          autoComplete="username"
          placeholder="请输入账号"
        />
      </Form.Item>
      <Form.Item
        name="passwordPassword"
        rules={[
          { required: true, message: '请输入密码' },
          { min: 6, message: '密码长度不能少于 6 位' },
        ]}
      >
        <Input.Password
          size="large"
          prefix={<LockOutlined className="saas-login-page__field-icon" />}
          autoComplete="current-password"
          placeholder="请输入密码"
        />
      </Form.Item>
      {loginEncryptionLoading ? (
        <Typography.Text type="secondary">
          <Space size={8}>
            <Skeleton.Avatar active size="small" shape="circle" />
            正在加载登录加密信息...
          </Space>
        </Typography.Text>
      ) : null}
      {securityCaptchaEnabled ? (
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
    </>
  );

  const renderCodeTab = (mode: Exclude<LoginMode, 'password'>) => {
    const accountValue = mode === 'sms' ? smsAccount : emailAccount;
    const challenge = pendingChallenge;
    return (
      <>
        <Form.Item
          name={mode === 'sms' ? 'smsAccount' : 'emailAccount'}
          rules={[
            { required: true, message: mode === 'sms' ? '请输入手机号' : '请输入邮箱' },
            ...(mode === 'email' ? [{ type: 'email' as const, message: '请输入有效邮箱地址' }] : []),
          ]}
        >
          <Input
            size="large"
            prefix={mode === 'sms' ? <MobileOutlined className="saas-login-page__field-icon" /> : <MailOutlined className="saas-login-page__field-icon" />}
            autoComplete={mode === 'sms' ? 'tel' : 'email'}
            placeholder={getAccountPlaceholder(mode)}
          />
        </Form.Item>
        <div className="saas-login-page__code-row">
          <Form.Item
            name={mode === 'sms' ? 'smsVerificationCode' : 'emailVerificationCode'}
            rules={[{ required: true, message: '请输入验证码' }]}
            className="saas-login-page__code-input"
          >
            <Input
              size="large"
              autoComplete="one-time-code"
              inputMode="numeric"
              placeholder="请输入验证码"
            />
          </Form.Item>
          <Button
            size="large"
            onClick={() => onSendLoginCode(mode)}
            loading={sendingLoginType === mode}
            className="saas-login-page__send-code-button"
            disabled={!accountValue}
          >
            {challenge?.challengeId ? '重新发送' : '发送验证码'}
          </Button>
        </div>
        {challenge?.promptMessage ? (
          <Alert showIcon type="info" message={challenge.promptMessage} className="saas-login-page__code-alert" />
        ) : null}
        {challenge?.maskedContact ? (
          <Typography.Text type="secondary">验证码将发送到 {challenge.maskedContact}</Typography.Text>
        ) : null}
        {challenge?.debugCode ? (
          <Typography.Text type="secondary" className="saas-login-page__debug-code">
            调试验证码：{challenge.debugCode}
          </Typography.Text>
        ) : null}
      </>
    );
  };

  const tabItems = availableLoginModes.map((mode) => ({
    key: mode,
    label: MODE_META[mode].label,
    children: mode === 'password' ? renderPasswordTab() : renderCodeTab(mode),
  }));

  return (
    <>
      {showTabs ? (
        <Tabs
          activeKey={activeLoginMode}
          items={tabItems}
          onChange={(key) => onModeChange(key as LoginMode)}
          className="saas-login-page__tabs"
          destroyInactiveTabPane
        />
      ) : (
        renderPasswordTab()
      )}
      {!pendingSecondFactorLogin ? (
        <div className="saas-login-page__agreement">
          {hasAgreement ? (
            <Form.Item
              name="agreementAccepted"
              valuePropName="checked"
              rules={[
                {
                  validator: async (_, value) => {
                    if (hasAgreement && !value) {
                      throw new Error('请先同意条款后再登录');
                    }
                  },
                },
              ]}
            >
              <Checkbox>
                我已阅读并同意
                <Button type="link" size="small" onClick={() => onOpenAgreementPreview('user')}>
                  用户协议
                </Button>
                和
                <Button type="link" size="small" onClick={() => onOpenAgreementPreview('privacy')}>
                  隐私政策
                </Button>
              </Checkbox>
            </Form.Item>
          ) : null}
        </div>
      ) : null}
      <div className="saas-login-page__actions">
        <Form.Item noStyle name="remember" valuePropName="checked">
          <Checkbox>保持登录状态</Checkbox>
        </Form.Item>
      </div>
    </>
  );
};
