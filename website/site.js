const DEFAULT_BRANDING = {
  websiteName: "宏翔商道",
  websiteFaviconUrl: "",
  websiteLogoUrl: "",
  companyName: "宏翔商道",
  copyrightStartYear: new Date().getFullYear(),
  footerIcp: "",
  footerCopyright: "",
};

const normalizeText = (value, fallback) => {
  if (typeof value !== "string") {
    return fallback;
  }
  const trimmed = value.trim();
  return trimmed || fallback;
};

const normalizeUploadUrl = (url) => {
  if (typeof url !== "string" || !url.trim()) {
    return "";
  }
  const trimmed = url.trim();
  if (/^(https?:)?\/\//i.test(trimmed) || trimmed.startsWith("data:")) {
    return trimmed;
  }
  return trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
};

const normalizeBranding = (settings = {}) => {
  const websiteName = normalizeText(settings.websiteName, DEFAULT_BRANDING.websiteName);
  return {
    websiteName,
    websiteFaviconUrl: normalizeUploadUrl(settings.websiteFaviconUrl),
    websiteLogoUrl: normalizeUploadUrl(settings.websiteLogoUrl),
    companyName: normalizeText(settings.companyName, websiteName),
    copyrightStartYear: Number.isFinite(Number(settings.copyrightStartYear))
      ? Number(settings.copyrightStartYear)
      : DEFAULT_BRANDING.copyrightStartYear,
    footerIcp: normalizeText(settings.footerIcp, ""),
    footerCopyright: normalizeText(settings.footerCopyright, ""),
  };
};

const buildCopyrightText = (settings) => {
  if (settings.footerCopyright) {
    return settings.footerCopyright;
  }
  const currentYear = new Date().getFullYear();
  const startYear = settings.copyrightStartYear || currentYear;
  const yearLabel = startYear < currentYear ? `${startYear}-${currentYear}` : String(currentYear);
  return `Copyright © ${yearLabel} ${settings.companyName} All Rights Reserved`;
};

const setText = (selector, text) => {
  document.querySelectorAll(selector).forEach((element) => {
    element.textContent = text;
  });
};

const applyLogo = (settings) => {
  const mark = document.querySelector("[data-brand-mark]");
  if (!mark) {
    return;
  }
  if (settings.websiteLogoUrl) {
    mark.textContent = "";
    mark.style.backgroundImage = `url("${settings.websiteLogoUrl}")`;
    mark.style.backgroundSize = "cover";
    mark.style.backgroundPosition = "center";
  } else {
    mark.textContent = settings.websiteName.slice(0, 1);
  }
};

const applyFavicon = (url) => {
  const href = normalizeUploadUrl(url);
  if (!href) {
    return;
  }
  let icon = document.querySelector('link[rel="icon"]');
  if (!icon) {
    icon = document.createElement("link");
    icon.rel = "icon";
    document.head.appendChild(icon);
  }
  icon.href = href;
};

const applyBranding = (settings) => {
  document.title = `${settings.websiteName} 官网`;
  setText("[data-brand-name]", settings.websiteName);
  setText("[data-hero-brand]", settings.websiteName);
  setText("[data-footer-company]", settings.companyName);
  setText("[data-footer-copy]", buildCopyrightText(settings));
  setText("[data-footer-icp]", settings.footerIcp);
  applyLogo(settings);
  applyFavicon(settings.websiteFaviconUrl);
};

const loadBranding = async () => {
  try {
    const response = await fetch("/api/v1/public/branding-settings", {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(`Branding request failed: ${response.status}`);
    }
    const payload = await response.json();
    return normalizeBranding(payload?.data);
  } catch (error) {
    console.info("Using local website branding fallback.", error);
    return normalizeBranding(DEFAULT_BRANDING);
  }
};

applyBranding(await loadBranding());
