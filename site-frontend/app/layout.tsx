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

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
