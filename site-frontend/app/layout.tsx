import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Legendary Invention',
  description: '简洁大气的独立官网前端',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
