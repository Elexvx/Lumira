import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  metadataBase: new URL(process.env.SITE_PUBLIC_URL || process.env.NEXT_PUBLIC_SITE_PUBLIC_URL || 'https://site-frontend-nine.vercel.app'),
  title: {
    default: 'Legendary Invention',
    template: '%s | Legendary Invention',
  },
  description: '简洁大气的独立官网前端，由统一后台管理页面、内容与提交入口。',
};

const themeInitScript = `
(() => {
  try {
    const key = 'legendary-site-theme';
    const stored = window.localStorage.getItem(key);
    const theme = stored === 'light' || stored === 'dark'
      ? stored
      : (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    document.documentElement.dataset.siteTheme = theme;
    document.documentElement.style.colorScheme = theme;
    const applyBodyTheme = () => {
      document.body.dataset.siteTheme = theme;
      document.body.style.backgroundColor = theme === 'dark' ? '#0a0f16' : '#ffffff';
      document.body.style.color = theme === 'dark' ? '#f5f7fb' : '#101418';
    };
    if (document.body) {
      applyBodyTheme();
    } else {
      document.addEventListener('DOMContentLoaded', applyBodyTheme, { once: true });
    }
  } catch {
    document.documentElement.dataset.siteTheme = 'light';
    document.documentElement.style.colorScheme = 'light';
  }
})();
`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
