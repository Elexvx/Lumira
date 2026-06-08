import { ProDescriptions, type ProDescriptionsProps } from '@ant-design/pro-components';
import { Form, Input, InputNumber, Select, Space, Spin } from 'antd';
import type { FormProps } from 'antd';
import type { ReactNode } from 'react';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementTable } from '@/features/management/ManagementTable';
import type { ManagementTableProps } from '@/features/management/ManagementTable';
import type { DictItemRecord, DictTypeRecord } from '@/types/api';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const dictStatusOptions = [
  { label: t('启用', 'Enabled'), value: 'ENABLED' },
  { label: t('停用', 'Disabled'), value: 'DISABLED' },
];

const DictTypeForm = ({ formProps }: { formProps: FormProps }) => (
  <Form {...formProps}>
    <Form.Item name="dictCode" label={t('字典编码', 'Dictionary code')} rules={[{ required: true, message: t('请输入字典编码', 'Please enter the dictionary code') }]}>
      <Input />
    </Form.Item>
    <Form.Item name="dictName" label={t('字典名称', 'Dictionary name')} rules={[{ required: true, message: t('请输入字典名称', 'Please enter the dictionary name') }]}>
      <Input />
    </Form.Item>
    <Form.Item name="status" label={t('状态', 'Status')}>
      <Select options={dictStatusOptions} />
    </Form.Item>
    <Form.Item name="remark" label={t('备注', 'Remark')}>
      <Input.TextArea rows={3} />
    </Form.Item>
  </Form>
);

const DictItemForm = ({ formProps }: { formProps: FormProps }) => (
  <Form {...formProps}>
    <Form.Item name="itemLabel" label={t('标签', 'Label')} rules={[{ required: true, message: t('请输入标签', 'Please enter the label') }]}>
      <Input />
    </Form.Item>
    <Form.Item name="itemValue" label={t('值', 'Value')} rules={[{ required: true, message: t('请输入值', 'Please enter the value') }]}>
      <Input />
    </Form.Item>
    <Form.Item name="sortNo" label={t('排序', 'Sort')}>
      <InputNumber style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item name="status" label={t('状态', 'Status')}>
      <Select options={dictStatusOptions} />
    </Form.Item>
    <Form.Item name="remark" label={t('备注', 'Remark')}>
      <Input.TextArea rows={3} />
    </Form.Item>
  </Form>
);

const dictTypeDetailColumns: ProDescriptionsProps<DictTypeRecord>['columns'] = [
  { title: t('字典编码', 'Dictionary code'), dataIndex: 'dictCode' },
  { title: t('字典名称', 'Dictionary name'), dataIndex: 'dictName' },
  { title: t('状态', 'Status'), dataIndex: 'status', renderText: (value) => (String(value) === 'ENABLED' ? t('启用', 'Enabled') : t('停用', 'Disabled')) },
  {
    title: t('系统内置', 'System built-in'),
    dataIndex: 'isSystem',
    renderText: (value) => (Number(value) !== 0 ? t('是', 'Yes') : t('否', 'No')),
  },
  { title: t('备注', 'Remark'), dataIndex: 'remark', renderText: (value) => value || '-' },
];

type DictManagementDrawersProps = {
  typeDrawerOpen: boolean;
  typeDrawerTitle: string;
  typeDrawerSaving: boolean;
  canSaveType: boolean;
  typeFormProps: FormProps;
  onCloseTypeDrawer: () => void;
  onSaveType: () => void;
  typeDetailDrawerOpen: boolean;
  typeDetailDrawerTitle: string;
  typeDetailLoading: boolean;
  typeDetail: DictTypeRecord | null;
  detailProps: ProDescriptionsProps<DictTypeRecord>;
  itemColumns: ManagementTableProps<DictItemRecord>['columns'];
  itemRows: DictItemRecord[];
  isMobile: boolean;
  searchConfig: ManagementTableProps<DictTypeRecord>['search'];
  buildToolbarButtons: (items: Array<{ key: string; permission?: string; type?: 'primary' | 'default' | 'dashed' | 'link'; label: string; onClick: () => void }>) => ReactNode[];
  onCreateItem: () => void;
  onCloseDetailDrawer: () => void;
  itemDrawerOpen: boolean;
  itemDrawerTitle: string;
  itemDrawerSaving: boolean;
  canSaveItem: boolean;
  itemFormProps: FormProps;
  onCloseItemDrawer: () => void;
  onSaveItem: () => void;
};

export const DictManagementDrawers = ({
  typeDrawerOpen,
  typeDrawerTitle,
  typeDrawerSaving,
  canSaveType,
  typeFormProps,
  onCloseTypeDrawer,
  onSaveType,
  typeDetailDrawerOpen,
  typeDetailDrawerTitle,
  typeDetailLoading,
  typeDetail,
  detailProps,
  itemColumns,
  itemRows,
  isMobile,
  searchConfig,
  buildToolbarButtons,
  onCreateItem,
  onCloseDetailDrawer,
  itemDrawerOpen,
  itemDrawerTitle,
  itemDrawerSaving,
  canSaveItem,
  itemFormProps,
  onCloseItemDrawer,
  onSaveItem,
}: DictManagementDrawersProps) => {
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);

  return (
    <>
    <ManagementDrawer
      title={typeDrawerTitle}
      open={typeDrawerOpen}
      onClose={onCloseTypeDrawer}
      footerActions={[
        { key: 'cancel', label: t('取消', 'Cancel'), onClick: onCloseTypeDrawer },
        { key: 'save', label: t('保存', 'Save'), type: 'primary', loading: typeDrawerSaving, disabled: !canSaveType, onClick: onSaveType },
      ]}
    >
      <DictTypeForm formProps={typeFormProps} />
    </ManagementDrawer>

    <ManagementDrawer title={typeDetailDrawerTitle} open={typeDetailDrawerOpen} onClose={onCloseDetailDrawer}>
      {typeDetailLoading ? (
        <div style={{ display: 'grid', placeItems: 'center', minHeight: 'var(--saas-spacing-240)' }}>
          <Spin />
        </div>
      ) : typeDetail ? (
        <Space direction="vertical" style={{ width: '100%' }} size={sectionGap}>
          <ProDescriptions<DictTypeRecord> {...detailProps} columns={dictTypeDetailColumns} />
          <ManagementTable<DictItemRecord>
            rowKey="id"
            columns={itemColumns}
            isMobile={isMobile}
            dataSource={itemRows}
            search={searchConfig}
            pagination={false}
            toolBarRender={() =>
              buildToolbarButtons([
                {
                  key: 'create-item',
                  permission: 'system:dict:update',
                  type: 'primary',
                  label: t('新增项', 'Add item'),
                  onClick: onCreateItem,
                },
              ])
            }
          />
        </Space>
      ) : null}
    </ManagementDrawer>

    <ManagementDrawer
      title={itemDrawerTitle}
      open={itemDrawerOpen}
      onClose={onCloseItemDrawer}
      footerActions={[
        { key: 'cancel', label: t('取消', 'Cancel'), onClick: onCloseItemDrawer },
        { key: 'save', label: t('保存', 'Save'), type: 'primary', loading: itemDrawerSaving, disabled: !canSaveItem, onClick: onSaveItem },
      ]}
    >
      <DictItemForm formProps={itemFormProps} />
    </ManagementDrawer>
    </>
  );
};
