import * as AntIcons from '@ant-design/icons';
import { AppstoreOutlined, CloseOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Popover, Segmented, Tooltip } from 'antd';
import { createElement, useMemo, useState, type ComponentType, type ReactNode } from 'react';
import './MenuIconPicker.css';

type AntdIconComponent = ComponentType<Record<string, unknown>>;
type IconStyle = 'Outlined' | 'Filled' | 'TwoTone';

export interface MenuIconOption {
  label: string;
  value: string;
}

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

const ICON_STYLE_OPTIONS: Array<{ label: string; value: IconStyle }> = [
  { label: '线框风格', value: 'Outlined' },
  { label: '实底风格', value: 'Filled' },
  { label: '双色风格', value: 'TwoTone' },
];

const ICON_STYLE_SUFFIX_PATTERN = /(Outlined|Filled|TwoTone)$/;

const ALL_ICON_NAMES = Object.keys(AntIcons)
  .filter((name) => ICON_STYLE_SUFFIX_PATTERN.test(name))
  .sort((first, second) => first.localeCompare(second));

export const normalizeMenuIconName = (iconName?: string | null) =>
  (iconName || '')
    .trim()
    .replace(/(^\w)|-(\w)/g, (_, firstChar: string, hyphenChar: string) => (firstChar || hyphenChar).toUpperCase());

export const resolveMenuIcon = (iconName?: string | null, className?: string): ReactNode => {
  const normalizedIconName = normalizeMenuIconName(iconName);
  if (!normalizedIconName) {
    return undefined;
  }

  const iconComponents = AntIcons as unknown as Record<string, AntdIconComponent | undefined>;
  const iconComponent = iconComponents[normalizedIconName] || iconComponents[`${normalizedIconName}Outlined`];

  return iconComponent ? createElement(iconComponent, { className }) : undefined;
};

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

const normalizeOption = (option: MenuIconOption) => {
  const value = normalizeMenuIconName(option.value);
  return value ? { label: normalizeMenuIconName(option.label) || value, value } : null;
};

const buildPickerOptions = (options: MenuIconOption[]) => {
  const seen = new Set<string>();
  return options
    .map(normalizeOption)
    .filter((option): option is MenuIconOption => Boolean(option))
    .filter((option) => {
      if (seen.has(option.value)) {
        return false;
      }
      seen.add(option.value);
      return true;
    });
};

export const MenuIconPicker = ({ value, options = [], loading, disabled, onChange }: MenuIconPickerProps) => {
  const iconOptions = useMemo(() => buildPickerOptions(options), [options]);
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [activeStyle, setActiveStyle] = useState<IconStyle>('Outlined');
  const selectedIcon = normalizeMenuIconName(value);
  const selectedIconNode = resolveMenuIcon(selectedIcon) || <AppstoreOutlined className="saas-menu-icon-picker__placeholder" />;
  const optionValues = useMemo(() => new Set(iconOptions.map((option) => option.value)), [iconOptions]);
  const pickerIconNames = useMemo(
    () => {
      const configuredNames = iconOptions.map((option) => option.value);
      const mergedCatalogNames = [
        ...configuredNames,
        ...ALL_ICON_NAMES.filter((iconName) => !optionValues.has(iconName)),
      ];
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

  const overlay = (
    <div className="saas-menu-icon-picker__overlay">
      <Input
        allowClear
        className="saas-menu-icon-picker__search"
        suffix={<SearchOutlined />}
        placeholder="搜索"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
      />
      <Segmented
        block
        className="saas-menu-icon-picker__styles"
        options={ICON_STYLE_OPTIONS}
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
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到图标" />
      )}
    </div>
  );

  return (
    <div className="saas-menu-icon-picker">
      <div className="saas-menu-icon-picker__trigger">
        <Popover
          arrow
          content={overlay}
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
            title={selectedIcon || '选择菜单项图标'}
          />
        </Popover>
        <Button
          className="saas-menu-icon-picker__clear"
          disabled={disabled || !selectedIcon}
          icon={<CloseOutlined />}
          title="清空图标"
          onClick={() => onChange?.(undefined)}
        />
      </div>
    </div>
  );
};
