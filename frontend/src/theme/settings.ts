import { buildStorageKey, storage } from '../cache/storage';

export type ThemePreference = 'system' | 'light' | 'dark' | 'compact';

const THEME_PREFERENCE_KEY = 'theme_preference';
export const THEME_PREFERENCE_STORAGE_KEY = buildStorageKey(THEME_PREFERENCE_KEY);
const LIGHT_BACKGROUND_COLOR = '#ffffff';
const DARK_BACKGROUND_COLOR = '#09090b';

export const DEFAULT_THEME_PREFERENCE: ThemePreference = 'system';

const isValidThemePreference = (value: unknown): value is ThemePreference => {
  return value === 'system' || value === 'light' || value === 'dark' || value === 'compact';
};

export const normalizeThemePreference = (value?: string | null): ThemePreference => {
  if (isValidThemePreference(value)) {
    return value;
  }

  return DEFAULT_THEME_PREFERENCE;
};

export const getStoredThemePreference = (): ThemePreference | null => storage.get<ThemePreference>(THEME_PREFERENCE_KEY);

export const persistThemePreference = (value: ThemePreference) => {
  storage.set(THEME_PREFERENCE_KEY, normalizeThemePreference(value));
};

export const resolveThemeColorMode = (themePreference: ThemePreference, systemDarkMode: boolean) => {
  return themePreference === 'dark' || (themePreference === 'system' && systemDarkMode) ? 'dark' : 'light';
};

export const applyThemePreferenceToDocument = (
  root: HTMLElement,
  themePreference: ThemePreference,
  systemDarkMode: boolean,
  body?: HTMLElement | null,
) => {
  const resolvedColorMode = resolveThemeColorMode(themePreference, systemDarkMode);
  const backgroundColor = resolvedColorMode === 'dark' ? DARK_BACKGROUND_COLOR : LIGHT_BACKGROUND_COLOR;

  root.dataset.theme = resolvedColorMode;
  root.dataset.themePreference = themePreference;
  root.dataset.themeDensity = themePreference === 'compact' ? 'compact' : 'normal';
  root.style.colorScheme = resolvedColorMode;
  root.style.backgroundColor = backgroundColor;

  if (body) {
    body.style.backgroundColor = backgroundColor;
  }

  return resolvedColorMode;
};

export const createThemePreferenceBootstrapScript = () => {
  const storageKey = JSON.stringify(THEME_PREFERENCE_STORAGE_KEY);
  const defaultPreference = JSON.stringify(DEFAULT_THEME_PREFERENCE);

  return `(function(){try{var storageKey=${storageKey};var preference=${defaultPreference};var raw=localStorage.getItem(storageKey);if(raw!==null){try{preference=JSON.parse(raw);}catch(_error){preference=${defaultPreference};}}if(preference!=="system"&&preference!=="light"&&preference!=="dark"&&preference!=="compact"){preference=${defaultPreference};}var systemDark=false;if(preference==="system"&&typeof window.matchMedia==="function"){systemDark=window.matchMedia("(prefers-color-scheme: dark)").matches;}var resolved=preference==="dark"||(preference==="system"&&systemDark)?"dark":"light";var root=document.documentElement;var backgroundColor=resolved==="dark"?${JSON.stringify(DARK_BACKGROUND_COLOR)}:${JSON.stringify(LIGHT_BACKGROUND_COLOR)};root.dataset.theme=resolved;root.dataset.themePreference=preference;root.dataset.themeDensity=preference==="compact"?"compact":"normal";root.style.colorScheme=resolved;root.style.backgroundColor=backgroundColor;if(document.body){document.body.style.backgroundColor=backgroundColor;}}catch(_error){}})();`;
};
