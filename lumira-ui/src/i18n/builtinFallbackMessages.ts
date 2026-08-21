import { normalizeLocale, resolvePreferredLocale } from './locale';

const LOGIN_FALLBACK_MESSAGES = {
  'zh-CN': {
    'app.locale.switch': '切换语言',
    'page.login.welcomeTitle': '欢迎登录 {websiteName}',
    'page.login.title': '登录',
    'page.login.modeTabs': '登录方式',
    'page.login.passwordAccount': '密码登录',
    'page.login.smsCode': '短信验证码登录',
    'page.login.emailCode': '邮箱验证码登录',
    'page.login.passkeyShort': '通行密钥',
    'page.login.passkey': '使用通行密钥登录',
    'page.login.qr.wechatTitle': '微信扫码登录',
    'page.login.qr.wechatActionHint': '点击二维码前往微信授权登录',
    'page.login.qr.wechatUnavailable': '微信登录暂未启用',
    'page.login.qr.wechatLoadFailed': '微信二维码加载失败，请改用跳转登录',
    'page.login.qr.wechatHint': '请使用微信扫码登录',
    'page.login.wechat': '微信登录',
    'page.login.error.pleaseEnterAccount': '请输入账号、手机号或邮箱',
    'page.login.error.pleaseEnterPassword': '请输入密码',
    'page.login.error.pleaseEnterMobile': '请输入手机号',
    'page.login.error.pleaseEnterEmail': '请输入邮箱',
    'page.login.error.pleaseEnterCaptcha': '请输入验证码',
    'page.login.error.accountLength': '账号长度不能超过 128 个字符',
    'page.login.error.passwordLength': '密码长度不能少于 6 位',
    'page.login.error.invalidAccountCharacters': '账号包含不支持的字符',
    'page.login.error.invalidCodeCharacters': '验证码只能包含字母和数字',
    'page.login.error.invalidMobile': '请输入有效手机号',
    'page.login.error.invalidEmail': '请输入有效邮箱地址',
    'page.login.error.pleaseCompleteSliderCaptcha': '请先完成滑块验证',
    'page.login.code.countdown': '{seconds} 秒后重发',
    'page.login.code.refresh': '重新发送',
    'page.login.code.send': '发送验证码',
    'page.login.captcha.refresh': '刷新验证码',
    'page.login.captcha.refreshText': '点击刷新',
    'page.login.captcha.retry': '点击重试',
    'page.login.captcha.alt': '验证码',
    'page.login.captcha.sliderTitle': '拖动验证',
    'page.login.captcha.sliderVerified': '已验证',
    'page.login.captcha.startSlider': '验证',
    'page.login.remember': '记住我',
    'page.login.forgotPassword': '忘记密码',
    'page.login.submit.login': '登录',
    'page.login.submit.verify': '验证并登录',
    'page.login.noAccount': '没有账号？',
    'page.login.joinUs': '注册账号',
    'page.login.registerAndLogin': '注册并登录',
    'page.login.backToLogin': '返回登录',
    'page.login.registrationUnavailable': '暂时无法注册，请联系管理员配置注册与验证码服务',
    'mockSms.modal.title': '模拟短信验证码',
    'mockSms.modal.close': '关闭',
    'mockSms.modal.debugOnly': '仅用于本地调试，不会发送真实短信',
    'mockSms.modal.code': '验证码',
    'mockSms.modal.copy': '复制验证码',
    'mockSms.modal.copySuccess': '验证码已复制',
    'mockSms.modal.copyFailed': '复制失败，请手动复制验证码',
    'page.login.otherMethods': '其他方式登录',
    'page.login.qq': 'QQ 登录',
    'page.login.weibo': '微博登录',
    'page.login.agreement.accept': '我已阅读并同意',
    'page.login.autoRegisterNoticePrefix': '未注册手机号验证后自动登录，注册即代表同意',
    'page.login.agreement.user': '用户协议',
    'page.login.agreement.userPlain': '用户协议',
    'page.login.agreement.and': '和',
    'page.login.agreement.privacy': '隐私政策',
    'page.login.agreement.privacyGuide': '隐私协议',
  },
  'en-US': {
    'app.locale.switch': 'Switch language',
    'page.login.welcomeTitle': 'Welcome to {websiteName}',
    'page.login.title': 'Log in',
    'page.login.modeTabs': 'Login method',
    'page.login.passwordAccount': 'Password login',
    'page.login.smsCode': 'SMS code login',
    'page.login.emailCode': 'Email code login',
    'page.login.passkeyShort': 'Passkey',
    'page.login.passkey': 'Log in with a passkey',
    'page.login.qr.wechatTitle': 'WeChat QR login',
    'page.login.qr.wechatActionHint': 'Select the QR code to continue with WeChat',
    'page.login.qr.wechatUnavailable': 'WeChat login is not enabled',
    'page.login.qr.wechatLoadFailed': 'Failed to load the WeChat QR code. Use redirect login instead.',
    'page.login.qr.wechatHint': 'Scan with WeChat to log in',
    'page.login.wechat': 'WeChat login',
    'page.login.error.pleaseEnterAccount': 'Please enter your account, mobile number, or email',
    'page.login.error.pleaseEnterPassword': 'Please enter your password',
    'page.login.error.pleaseEnterMobile': 'Please enter your mobile number',
    'page.login.error.pleaseEnterEmail': 'Please enter your email',
    'page.login.error.pleaseEnterCaptcha': 'Please enter the verification code',
    'page.login.error.accountLength': 'Account cannot exceed 128 characters',
    'page.login.error.passwordLength': 'Password must be at least 6 characters',
    'page.login.error.invalidAccountCharacters': 'The account contains unsupported characters',
    'page.login.error.invalidCodeCharacters': 'Verification code can only contain letters and numbers',
    'page.login.error.invalidMobile': 'Please enter a valid mobile number',
    'page.login.error.invalidEmail': 'Please enter a valid email address',
    'page.login.error.pleaseCompleteSliderCaptcha': 'Please complete the slider captcha first',
    'page.login.code.countdown': 'Resend in {seconds}s',
    'page.login.code.refresh': 'Resend',
    'page.login.code.send': 'Send code',
    'page.login.captcha.refresh': 'Refresh captcha',
    'page.login.captcha.refreshText': 'Click to refresh',
    'page.login.captcha.retry': 'Click to retry',
    'page.login.captcha.alt': 'Captcha',
    'page.login.captcha.sliderTitle': 'Drag to verify',
    'page.login.captcha.sliderVerified': 'Verified',
    'page.login.captcha.startSlider': 'Verify',
    'page.login.remember': 'Remember me',
    'page.login.forgotPassword': 'Forgot password',
    'page.login.submit.login': 'Log in',
    'page.login.submit.verify': 'Verify and log in',
    'page.login.noAccount': 'No account?',
    'page.login.joinUs': 'Create account',
    'page.login.registerAndLogin': 'Create account and log in',
    'page.login.backToLogin': 'Back to login',
    'page.login.registrationUnavailable': 'Registration is unavailable. Ask an administrator to configure registration and verification.',
    'mockSms.modal.title': 'Mock SMS verification code',
    'mockSms.modal.close': 'Close',
    'mockSms.modal.debugOnly': 'For local debugging only. No real SMS message was sent.',
    'mockSms.modal.code': 'Verification code',
    'mockSms.modal.copy': 'Copy code',
    'mockSms.modal.copySuccess': 'Verification code copied',
    'mockSms.modal.copyFailed': 'Copy failed. Please copy the code manually.',
    'page.login.otherMethods': 'Other login methods',
    'page.login.qq': 'QQ login',
    'page.login.weibo': 'Weibo login',
    'page.login.agreement.accept': 'I have read and agree to the',
    'page.login.autoRegisterNoticePrefix': 'Unregistered mobile numbers will be registered after verification. By continuing, you agree to the',
    'page.login.agreement.user': 'User Agreement',
    'page.login.agreement.userPlain': 'User Agreement',
    'page.login.agreement.and': 'and',
    'page.login.agreement.privacy': 'Privacy Policy',
    'page.login.agreement.privacyGuide': 'Privacy Policy',
  },
} as const;

const ROLE_SIMULATION_FALLBACK_MESSAGES = {
  'zh-CN': {
    'nav.user.role.exitSimulation': '退出角色模拟',
    'nav.user.role.exitSuccess': '已退出角色模拟',
  },
  'en-US': {
    'nav.user.role.exitSimulation': 'Exit role simulation',
    'nav.user.role.exitSuccess': 'Exited role simulation',
  },
} as const;

const USER_MENU_FALLBACK_MESSAGES = {
  'zh-CN': {
    'nav.user.menu': '用户菜单',
    'nav.user.profile': '个人资料',
    'nav.user.changePassword': '修改密码',
    'nav.user.switchRole': '切换角色',
    'nav.user.role.currentTag': '当前',
    'nav.user.role.exitSimulation': '退出角色模拟',
    'nav.user.role.exitSuccess': '已退出角色模拟',
    'nav.user.role.switchSuccess': '角色切换成功',
    'nav.user.role.switchSuccessWithName': '已切换至 {roleName}',
    'auth.logout': '退出登录',
  },
  'en-US': {
    'nav.user.menu': 'User menu',
    'nav.user.profile': 'Profile',
    'nav.user.changePassword': 'Change password',
    'nav.user.switchRole': 'Switch role',
    'nav.user.role.currentTag': 'Current',
    'nav.user.role.exitSimulation': 'Exit role simulation',
    'nav.user.role.exitSuccess': 'Exited role simulation',
    'nav.user.role.switchSuccess': 'Role switched',
    'nav.user.role.switchSuccessWithName': 'Switched to {roleName}',
    'auth.logout': 'Log out',
  },
} as const;

const USER_EDITOR_FALLBACK_MESSAGES = {
  'zh-CN': {
    'ui.system.users.systemFields': '系统信息',
    'ui.system.users.customFields': '个人资料',
    'ui.system.users.noCustomFields': '暂无启用的个人资料字段',
    'ui.system.users.customFieldsHint': '个人资料字段可在字段管理中增加、修改或停用。',
    'ui.system.users.customFieldRequired': '请输入{fieldLabel}',
  },
  'en-US': {
    'ui.system.users.systemFields': 'System information',
    'ui.system.users.customFields': 'Personal information',
    'ui.system.users.noCustomFields': 'No enabled personal profile fields',
    'ui.system.users.customFieldsHint': 'Personal profile fields are managed in Profile field settings.',
    'ui.system.users.customFieldRequired': 'Please enter {fieldLabel}',
  },
} as const;

type LoginFallbackLocale = keyof typeof LOGIN_FALLBACK_MESSAGES;

const currentLocale = () => {
  if (typeof document !== 'undefined') {
    return normalizeLocale(document.documentElement.lang);
  }
  return resolvePreferredLocale();
};

export const resolveBuiltinFallbackMessage = (id?: string | null, localeCode = currentLocale()) => {
  if (!id) {
    return undefined;
  }
  const locale: LoginFallbackLocale = normalizeLocale(localeCode).startsWith('zh') ? 'zh-CN' : 'en-US';
  return LOGIN_FALLBACK_MESSAGES[locale][id as keyof (typeof LOGIN_FALLBACK_MESSAGES)[typeof locale]]
    || ROLE_SIMULATION_FALLBACK_MESSAGES[locale][id as keyof (typeof ROLE_SIMULATION_FALLBACK_MESSAGES)[typeof locale]]
    || USER_MENU_FALLBACK_MESSAGES[locale][id as keyof (typeof USER_MENU_FALLBACK_MESSAGES)[typeof locale]]
    || USER_EDITOR_FALLBACK_MESSAGES[locale][id as keyof (typeof USER_EDITOR_FALLBACK_MESSAGES)[typeof locale]];
};

const containsCjk = (value: string) => /[\u3400-\u9fff]/.test(value);

export const shouldUseBuiltinFallback = (
  id: string,
  databaseValue: string,
  localeCode = currentLocale(),
) => {
  const fallback = resolveBuiltinFallbackMessage(id, localeCode);
  if (!fallback) {
    return false;
  }

  const chineseLocale = normalizeLocale(localeCode).startsWith('zh');
  return chineseLocale
    ? containsCjk(fallback) && !containsCjk(databaseValue)
    : !containsCjk(fallback) && containsCjk(databaseValue);
};
