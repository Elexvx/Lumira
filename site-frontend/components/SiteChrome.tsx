import { LogIn } from 'lucide-react';
import { publicAssetUrl, type NavigationItem, type SiteSettings } from '@/lib/api';

function navigationTarget(item: NavigationItem) {
  if (item.linkType === 'EXTERNAL') return item.linkTarget;
  return item.linkTarget || '/';
}

export function SiteHeader({ site, navigation }: { site: SiteSettings; navigation: NavigationItem[] }) {
  const logoUrl = publicAssetUrl(site.logoUrl);

  return (
    <header className="site-header">
      <div className="site-header__brand">
        <a className="brand" href="/">
          {logoUrl ? <img src={logoUrl} alt="" className="brand-logo" /> : <span className="brand-mark" />}
          <span>{site.name || 'Legendary Invention'}</span>
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
        <a className="site-login-button" href="/user/login">
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
      <span>Independent public site powered by CMS.</span>
    </footer>
  );
}
