import { LogIn } from 'lucide-react';
import { publicAssetUrl, type NavigationItem, type SiteSettings } from '@/lib/api';

function navigationTarget(item: NavigationItem) {
  if (item.linkType === 'EXTERNAL') return item.linkTarget;
  return item.linkTarget || '/';
}

function normalizeLoginRoute(value?: string | null) {
  const route = value?.trim();
  if (!route) return '/user/login';
  if (/^(https?:)?\/\//i.test(route) || route.startsWith('/')) return route;
  return `/${route}`;
}

export function SiteHeader({ site, navigation }: { site: SiteSettings; navigation: NavigationItem[] }) {
  const logoUrl = publicAssetUrl(site.logoUrl);
  const siteName = site.name || 'Legendary Invention';
  const loginRoute = normalizeLoginRoute(site.loginRoute);

  return (
    <header className="site-header">
      <div className="site-header__brand">
        <a className={logoUrl ? 'brand brand--logo-only' : 'brand'} href="/" aria-label={siteName}>
          {logoUrl ? (
            <img src={logoUrl} alt={siteName} className="brand-logo" />
          ) : (
            <>
              <span className="brand-mark" />
              <span>{siteName}</span>
            </>
          )}
        </a>
      </div>
      <nav className="site-header__nav" aria-label="主导航">
        {navigation.map((item) => (
          <a
            key={item.id}
            href={navigationTarget(item) || '/'}
            rel={item.openType === 'BLANK' ? 'noreferrer' : undefined}
            target={item.openType === 'BLANK' ? '_blank' : undefined}
          >
            {item.title}
          </a>
        ))}
      </nav>
      <div className="site-header__actions">
        <a className="site-login-button" href={loginRoute}>
          <LogIn aria-hidden="true" size={16} strokeWidth={2.4} />
          登录
        </a>
      </div>
    </header>
  );
}

export function SiteFooter({ site }: { site: SiteSettings }) {
  return (
    <footer>
      <span>{site.name || 'Legendary Invention'}</span>
    </footer>
  );
}
