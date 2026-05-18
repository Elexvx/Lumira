'use client';

import { Moon, Sun } from 'lucide-react';
import { useEffect, useState } from 'react';

type SiteTheme = 'light' | 'dark';

const STORAGE_KEY = 'legendary-site-theme';

function resolveInitialTheme(): SiteTheme {
  if (typeof window === 'undefined') {
    return 'light';
  }
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function applyTheme(theme: SiteTheme) {
  document.documentElement.dataset.siteTheme = theme;
  document.documentElement.style.colorScheme = theme;
  document.body.dataset.siteTheme = theme;
  document.body.style.backgroundColor = theme === 'dark' ? '#0a0f16' : '#ffffff';
  document.body.style.color = theme === 'dark' ? '#f5f7fb' : '#101418';
}

export function SiteThemeToggle() {
  const [theme, setTheme] = useState<SiteTheme>('light');

  useEffect(() => {
    const initialTheme = resolveInitialTheme();
    setTheme(initialTheme);
    applyTheme(initialTheme);
  }, []);

  const nextTheme = theme === 'dark' ? 'light' : 'dark';

  return (
    <button
      type="button"
      className="site-theme-toggle"
      aria-label={theme === 'dark' ? '切换到日间模式' : '切换到夜间模式'}
      aria-pressed={theme === 'dark'}
      title={theme === 'dark' ? '日间模式' : '夜间模式'}
      onClick={() => {
        setTheme(nextTheme);
        window.localStorage.setItem(STORAGE_KEY, nextTheme);
        applyTheme(nextTheme);
      }}
    >
      {theme === 'dark' ? <Sun aria-hidden="true" size={17} strokeWidth={2.4} /> : <Moon aria-hidden="true" size={17} strokeWidth={2.4} />}
    </button>
  );
}
