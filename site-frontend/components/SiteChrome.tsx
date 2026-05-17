import { publicAssetUrl, type NavigationItem, type SiteSettings } from '@/lib/api';

function navigationTarget(item: NavigationItem) {
  if (item.linkType === 'EXTERNAL') return item.linkTarget;
  return item.linkTarget || '/';
}

export function SiteHeader({ site, navigation }: { site: SiteSettings; navigation: NavigationItem[] }) {
  const logoUrl = publicAssetUrl(site.logoUrl);
  return (
    <header className="site-header">
      <a className="brand" href="/">
        {logoUrl ? <img src={logoUrl} alt="" className="brand-logo" /> : <span className="brand-mark" />}
        {site.name || 'Legendary Invention'}
      </a>
      <nav aria-label="主导航">
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
