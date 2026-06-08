import * as AntIcons from '@ant-design/icons';
import { AppstoreOutlined, CloseOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Popover, Segmented, Tooltip } from 'antd';
import { createElement, useMemo, useState } from 'react';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import './MenuIconPicker.css';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type IconStyle = 'Outlined' | 'Filled' | 'TwoTone';
type AntdIconComponent = React.ComponentType<Record<string, unknown>>;

const ICON_STYLE_SUFFIX_PATTERN = /(Outlined|Filled|TwoTone)$/;

export interface MenuIconOption {
  label: string;
  value: string;
}

const normalizeMenuIconName = (iconName?: string | null) =>
  (iconName || '')
    .trim()
    .replace(/(^\w)|-(\w)/g, (_, firstChar: string, hyphenChar: string) => (firstChar || hyphenChar).toUpperCase());

const ALL_ICON_NAMES = Object.keys(AntIcons)
  .filter((name) => ICON_STYLE_SUFFIX_PATTERN.test(name))
  .sort((first, second) => first.localeCompare(second));

const getAllMenuIconNames = () => ALL_ICON_NAMES;

const resolveMenuIcon = (iconName?: string | null, className?: string) => {
  const normalizedIconName = normalizeMenuIconName(iconName);
  if (!normalizedIconName) {
    return undefined;
  }

  const iconComponents = AntIcons as unknown as Record<string, AntdIconComponent | undefined>;
  const iconComponent = iconComponents[normalizedIconName] || iconComponents[`${normalizedIconName}Outlined`];

  return iconComponent ? createElement(iconComponent, { className }) : undefined;
};

const normalizeMenuIconOption = (option: MenuIconOption) => {
  const value = normalizeMenuIconName(option.value);
  return value ? { label: normalizeMenuIconName(option.label) || value, value } : null;
};

const buildPickerOptions = (options: MenuIconOption[]) => {
  const seen = new Set<string>();
  return options
    .map(normalizeMenuIconOption)
    .filter((option): option is MenuIconOption => Boolean(option))
    .filter((option) => {
      if (seen.has(option.value)) {
        return false;
      }
      seen.add(option.value);
      return true;
    });
};

const ICON_STYLE_OPTIONS: Array<{ label: string; value: IconStyle }> = [
  { label: t('线框风格', 'Outlined'), value: 'Outlined' },
  { label: t('实底风格', 'Filled'), value: 'Filled' },
  { label: t('双色风格', 'TwoTone'), value: 'TwoTone' },
];

interface MenuIconPickerProps {
  value?: string;
  options?: MenuIconOption[];
  loading?: boolean;
  disabled?: boolean;
  onChange?: (value?: string) => void;
}

interface MenuIconPreviewProps {
  icon?: string | null;
  showName?: boolean;
}

export const MenuIconPreview = ({ icon, showName = true }: MenuIconPreviewProps) => {
  const iconName = normalizeMenuIconName(icon);
  const renderedIcon = resolveMenuIcon(iconName, 'saas-menu-icon-preview__icon');

  if (!iconName) {
    return <span>-</span>;
  }

  return (
    <span className="saas-menu-icon-preview">
      {renderedIcon}
      {showName ? <span className="saas-menu-icon-preview__name">{iconName}</span> : null}
    </span>
  );
};

export const MenuIconPicker = ({ value, options = [], loading, disabled, onChange }: MenuIconPickerProps) => {
  const iconOptions = useMemo(() => buildPickerOptions(options), [options]);
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [activeStyle, setActiveStyle] = useState<IconStyle>('Outlined');
  const selectedIcon = normalizeMenuIconName(value);
  const selectedIconNode =
    resolveMenuIcon(selectedIcon) || createElement(AppstoreOutlined, { className: 'saas-menu-icon-picker__placeholder' });
  const optionValues = useMemo(() => new Set(iconOptions.map((option) => option.value)), [iconOptions]);
  const pickerIconNames = useMemo(
    () => {
      const configuredNames = iconOptions.map((option) => option.value);
      const mergedCatalogNames = [...configuredNames, ...getAllMenuIconNames().filter((iconName) => !optionValues.has(iconName))];
      const mergedNames = selectedIcon && !mergedCatalogNames.includes(selectedIcon)
        ? [selectedIcon, ...mergedCatalogNames]
        : mergedCatalogNames;
      const normalizedKeyword = keyword.trim().toLowerCase();

      return mergedNames
        .filter((iconName) => iconName.endsWith(activeStyle))
        .filter((iconName) => !normalizedKeyword || iconName.toLowerCase().includes(normalizedKeyword));
    },
    [activeStyle, iconOptions, keyword, optionValues, selectedIcon],
  );

  const pickerContent = (
    <div className="saas-menu-icon-picker__overlay">
      <Input
        allowClear
        className="saas-menu-icon-picker__search"
        size="small"
        suffix={<SearchOutlined />}
        placeholder={t('搜索', 'Search')}
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
      />
      <Segmented
        block
        className="saas-menu-icon-picker__styles"
        options={ICON_STYLE_OPTIONS}
        size="small"
        value={activeStyle}
        onChange={(nextValue) => setActiveStyle(nextValue as IconStyle)}
      />
      {pickerIconNames.length ? (
        <div className="saas-menu-icon-picker__grid">
          {pickerIconNames.map((iconName) => (
            <Tooltip key={iconName} title={iconName}>
              <button
                type="button"
                className={`saas-menu-icon-picker__option${iconName === selectedIcon ? ' saas-menu-icon-picker__option--active' : ''}`}
                aria-label={iconName}
                onClick={() => {
                  onChange?.(iconName);
                  setOpen(false);
                }}
              >
                {resolveMenuIcon(iconName)}
              </button>
            </Tooltip>
          ))}
        </div>
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('未找到图标', 'No icons found')} />
      )}
    </div>
  );

  return (
    <div className="saas-menu-icon-picker">
      <div className="saas-menu-icon-picker__trigger">
        <Popover
          arrow
          content={pickerContent}
          open={open}
          placement="bottomLeft"
          rootClassName="saas-menu-icon-picker-popover"
          trigger="click"
          onOpenChange={(nextOpen) => {
            if (!disabled) {
              setOpen(nextOpen);
            }
          }}
        >
          <Button
            className="saas-menu-icon-picker__button"
            disabled={disabled}
            icon={selectedIconNode}
            loading={loading}
            title={selectedIcon || t('选择菜单项图标', 'Choose a menu icon')}
          />
        </Popover>
        <Button
          className="saas-menu-icon-picker__clear"
          disabled={disabled || !selectedIcon}
          icon={<CloseOutlined />}
          title={t('清空图标', 'Clear icon')}
          onClick={() => onChange?.(undefined)}
        />
      </div>
    </div>
  );
};
