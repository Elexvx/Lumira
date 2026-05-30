import fs from 'fs/promises';
import path from 'path';

const verificationPath = path.join(process.cwd(), 'frontend/src/pages/settings/verification.tsx');
const tabsPath = path.join(process.cwd(), 'frontend/src/pages/settings/components/verification/ConfigTabs.tsx');

async function main() {
  const content = await fs.readFile(verificationPath, 'utf-8');
  
  // Find start of renderSmsTab
  const renderSmsTabRegex = /const renderSmsTab = \(\) => \([\s\S]*?(?=\n  const renderEmailTab)/;
  const smsMatch = content.match(renderSmsTabRegex);
  
  const renderEmailTabRegex = /const renderEmailTab = \(\) => \([\s\S]*?(?=\n  const renderWechatTab)/;
  const emailMatch = content.match(renderEmailTabRegex);
  
  const renderWechatTabRegex = /const renderWechatTab = \(\) => \([\s\S]*?(?=\n  const renderPasskeyTab)/;
  const wechatMatch = content.match(renderWechatTabRegex);
  
  const renderPasskeyTabRegex = /const renderPasskeyTab = \(\) => \([\s\S]*?(?=\n  const renderBasicConfig)/;
  const passkeyMatch = content.match(renderPasskeyTabRegex);
  
  if (!smsMatch || !emailMatch || !wechatMatch || !passkeyMatch) {
    console.log("Could not find all tabs");
    return;
  }

  const tabsContent = `import { Button, Card, Form, Input, InputNumber, Select, Space, Switch, Typography, theme } from 'antd';
import type { FormProps } from 'antd';
import {
  SMS_PROVIDER_OPTIONS,
  SMS_PROVIDER_SCHEMAS,
  SMS_ACCESS_KEY_SECRET_MASK,
  SMTP_PASSWORD_MASK,
  WECHAT_APP_SECRET_MASK,
} from './config';
import type { SmsProviderCode } from './config';

interface SmsTabProps {
  smsFormProps: FormProps;
  canManageSettings: boolean;
  smsConfigEnabled: boolean;
  handleSmsProviderChange: (value: SmsProviderCode) => void;
  smsAccessKeySecretConfigured: boolean;
  providerDrafts: any;
  provider: SmsProviderCode;
}

export const SmsConfigTab = ({
  smsFormProps,
  canManageSettings,
  smsConfigEnabled,
  handleSmsProviderChange,
  smsAccessKeySecretConfigured,
  providerDrafts,
  provider
}: SmsTabProps) => {
  const providerSchema = SMS_PROVIDER_SCHEMAS[provider] ?? SMS_PROVIDER_SCHEMAS.aliyun;
  return ${smsMatch[0].replace('const renderSmsTab = () => ', '').trim()}
};

interface EmailTabProps {
  smtpFormProps: FormProps;
  smtpTestFormProps: FormProps;
  canManageSettings: boolean;
  emailConfigEnabled: boolean;
  smtpSettingsQuery: any;
  verificationSettingsQuery: any;
  smtpPasswordConfigured: boolean;
  testingSmtpSettings: boolean;
  handleTestSmtp: () => void;
}

export const EmailConfigTab = ({
  smtpFormProps,
  smtpTestFormProps,
  canManageSettings,
  emailConfigEnabled,
  smtpSettingsQuery,
  verificationSettingsQuery,
  smtpPasswordConfigured,
  testingSmtpSettings,
  handleTestSmtp
}: EmailTabProps) => {
  return ${emailMatch[0].replace('const renderEmailTab = () => ', '').trim()}
};

interface WechatTabProps {
  wechatFormProps: FormProps;
  canManageSettings: boolean;
  wechatAppSecretConfigured: boolean;
}

export const WechatConfigTab = ({
  wechatFormProps,
  canManageSettings,
  wechatAppSecretConfigured
}: WechatTabProps) => {
  return ${wechatMatch[0].replace('const renderWechatTab = () => ', '').trim()}
};

interface PasskeyTabProps {
  passkeyFormProps: FormProps;
  canManageSettings: boolean;
  passkeyConfigEnabled: boolean;
}

export const PasskeyConfigTab = ({
  passkeyFormProps,
  canManageSettings,
  passkeyConfigEnabled
}: PasskeyTabProps) => {
  const { token } = theme.useToken();
  return ${passkeyMatch[0].replace('const renderPasskeyTab = () => ', '').trim()}
};
`;

  await fs.writeFile(tabsPath, tabsContent, 'utf-8');
  console.log("Created ConfigTabs.tsx");
  
  let newVerificationContent = content
    .replace(renderSmsTabRegex, '')
    .replace(renderEmailTabRegex, '')
    .replace(renderWechatTabRegex, '')
    .replace(renderPasskeyTabRegex, '');
    
  // Inject imports in verification
  if (!newVerificationContent.includes('SmsConfigTab')) {
    newVerificationContent = newVerificationContent.replace(
      "import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';",
      "import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';\nimport { SmsConfigTab, EmailConfigTab, WechatConfigTab, PasskeyConfigTab } from './components/verification/ConfigTabs';"
    );
  }
  
  // Replace references
  newVerificationContent = newVerificationContent.replace(
    /return renderSmsTab\(\);/g,
    `return <SmsConfigTab 
      smsFormProps={smsFormProps}
      canManageSettings={canManageSettings}
      smsConfigEnabled={smsConfigEnabled}
      handleSmsProviderChange={handleSmsProviderChange}
      smsAccessKeySecretConfigured={smsAccessKeySecretConfigured}
      providerDrafts={providerDrafts}
      provider={smsSettingsForm.getFieldValue('provider') || 'aliyun'}
    />;`
  );
  
  newVerificationContent = newVerificationContent.replace(
    /return renderEmailTab\(\);/g,
    `return <EmailConfigTab 
      smtpFormProps={smtpFormProps}
      smtpTestFormProps={{ form: smtpTestForm, onFinish: handleTestSmtp }}
      canManageSettings={canManageSettings}
      emailConfigEnabled={emailConfigEnabled}
      smtpSettingsQuery={smtpSettingsQuery}
      verificationSettingsQuery={verificationSettingsQuery}
      smtpPasswordConfigured={smtpPasswordConfigured}
      testingSmtpSettings={testingSmtpSettings}
      handleTestSmtp={handleTestSmtp}
    />;`
  );
  
  newVerificationContent = newVerificationContent.replace(
    /return renderWechatTab\(\);/g,
    `return <WechatConfigTab 
      wechatFormProps={wechatFormProps}
      canManageSettings={canManageSettings}
      wechatAppSecretConfigured={wechatAppSecretConfigured}
    />;`
  );
  
  newVerificationContent = newVerificationContent.replace(
    /return renderPasskeyTab\(\);/g,
    `return <PasskeyConfigTab 
      passkeyFormProps={passkeyFormProps}
      canManageSettings={canManageSettings}
      passkeyConfigEnabled={passkeyConfigEnabled}
    />;`
  );

  await fs.writeFile(verificationPath, newVerificationContent, 'utf-8');
  console.log("Updated verification.tsx");
}

main().catch(console.error);
