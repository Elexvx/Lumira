import { ProDescriptions, type ProDescriptionsProps } from '@ant-design/pro-components';
import { Form, Input, InputNumber, Select, Space, Spin } from 'antd';
import type { FormProps } from 'antd';
import type { ReactNode } from 'react';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementTable } from '@/features/management/ManagementTable';
import type { ManagementTableProps } from '@/features/management/ManagementTable';
import type { DictItemRecord, DictTypeRecord } from '@/types/api';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

const dictStatusOptions = [
  { label: t('ui.settings.dicts.dictmanagementdrawers.enabled'), value: 'ENABLED' },
  { label: t('ui.settings.dicts.dictmanagementdrawers.disabled'), value: 'DISABLED' },
];

const DictTypeForm = ({ formProps }: { formProps: FormProps }) => (
  <Form {...formProps}>
    <Form.Item name="dictCode" label={t('ui.settings.dicts.dictmanagementdrawers.dictionaryCode')} rules={[{ required: true, message: t('ui.settings.dicts.dictmanagementdrawers.pleaseEnterTheDictionaryCode') }]}>
      <Input />
    </Form.Item>
    <Form.Item name="dictName" label={t('ui.settings.dicts.dictmanagementdrawers.dictionaryName')} rules={[{ required: true, message: t('ui.settings.dicts.dictmanagementdrawers.pleaseEnterTheDictionaryName') }]}>
      <Input />
    </Form.Item>
    <Form.Item name="status" label={t('ui.settings.dicts.dictmanagementdrawers.status')}>
      <Select options={dictStatusOptions} />
    </Form.Item>
    <Form.Item name="remark" label={t('ui.settings.dicts.dictmanagementdrawers.remark')}>
      <Input.TextArea rows={3} />
    </Form.Item>
  </Form>
);

const DictItemForm = ({ formProps }: { formProps: FormProps }) => (
  <Form {...formProps}>
    <Form.Item name="itemLabel" label={t('ui.settings.dicts.dictmanagementdrawers.label')} rules={[{ required: true, message: t('ui.settings.dicts.dictmanagementdrawers.pleaseEnterTheLabel') }]}>
      <Input />
    </Form.Item>
    <Form.Item name="itemValue" label={t('ui.settings.dicts.dictmanagementdrawers.value')} rules={[{ required: true, message: t('ui.settings.dicts.dictmanagementdrawers.pleaseEnterTheValue') }]}>
      <Input />
    </Form.Item>
    <Form.Item name="sortNo" label={t('ui.settings.dicts.dictmanagementdrawers.sort')}>
      <InputNumber style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item name="status" label={t('ui.settings.dicts.dictmanagementdrawers.status')}>
      <Select options={dictStatusOptions} />
    </Form.Item>
    <Form.Item name="remark" label={t('ui.settings.dicts.dictmanagementdrawers.remark')}>
      <Input.TextArea rows={3} />
    </Form.Item>
  </Form>
);

const dictTypeDetailColumns: ProDescriptionsProps<DictTypeRecord>['columns'] = [
  { title: t('ui.settings.dicts.dictmanagementdrawers.dictionaryCode'), dataIndex: 'dictCode' },
  { title: t('ui.settings.dicts.dictmanagementdrawers.dictionaryName'), dataIndex: 'dictName' },
  { title: t('ui.settings.dicts.dictmanagementdrawers.status'), dataIndex: 'status', renderText: (value) => (String(value) === 'ENABLED' ? t('ui.settings.dicts.dictmanagementdrawers.enabled') : t('ui.settings.dicts.dictmanagementdrawers.disabled')) },
  {
    title: t('ui.settings.dicts.dictmanagementdrawers.systemBuiltIn'),
    dataIndex: 'isSystem',
    renderText: (value) => (Number(value) !== 0 ? t('ui.settings.dicts.dictmanagementdrawers.yes') : t('ui.settings.dicts.dictmanagementdrawers.no')),
  },
  { title: t('ui.settings.dicts.dictmanagementdrawers.remark'), dataIndex: 'remark', renderText: (value) => value || '-' },
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
        { key: 'cancel', label: t('ui.settings.dicts.dictmanagementdrawers.cancel'), onClick: onCloseTypeDrawer },
        { key: 'save', label: t('ui.settings.dicts.dictmanagementdrawers.save'), type: 'primary', loading: typeDrawerSaving, disabled: !canSaveType, onClick: onSaveType },
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
                  label: t('ui.settings.dicts.dictmanagementdrawers.addItem'),
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
        { key: 'cancel', label: t('ui.settings.dicts.dictmanagementdrawers.cancel'), onClick: onCloseItemDrawer },
        { key: 'save', label: t('ui.settings.dicts.dictmanagementdrawers.save'), type: 'primary', loading: itemDrawerSaving, disabled: !canSaveItem, onClick: onSaveItem },
      ]}
    >
      <DictItemForm formProps={itemFormProps} />
    </ManagementDrawer>
    </>
  );
};
