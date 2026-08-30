import { ProDescriptions, type ActionType, type ProDescriptionsProps } from '@ant-design/pro-components';
import { Alert, Form, Input, InputNumber, Select, Space, Spin, Typography, Upload } from 'antd';
import type { FormProps } from 'antd';
import type { MutableRefObject, ReactNode } from 'react';
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

type DictionaryImportPreview = {
  fileSha256: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  errors?: Array<{ rowNumber: number; message: string }>;
};

const DictTypeForm = ({
  formProps,
  allowImport,
  importPreview,
  importPreviewLoading,
  onImportFileSelected,
}: {
  formProps: FormProps;
  allowImport: boolean;
  importPreview?: DictionaryImportPreview;
  importPreviewLoading: boolean;
  onImportFileSelected: (file?: File) => void;
}) => (
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
    <Form.Item name="structureType" label="字典结构" initialValue="FLAT">
      <Select options={[{ label: '普通字典', value: 'FLAT' }, { label: '层级字典', value: 'TREE' }]} />
    </Form.Item>
    <Form.Item name="remark" label={t('ui.settings.dicts.dictmanagementdrawers.remark')}>
      <Input.TextArea rows={3} />
    </Form.Item>
    {allowImport ? (
      <Form.Item label="同时导入文件" extra="支持 UTF-8 制表符 TXT、XLS、XLSX；最大 20 MB、20000 行。">
        <Upload.Dragger
          accept=".txt,.xls,.xlsx"
          beforeUpload={(file) => { onImportFileSelected(file); return false; }}
          maxCount={1}
          onRemove={() => { onImportFileSelected(undefined); }}
        >
          <p>点击或拖入字典文件，系统会先校验预览</p>
        </Upload.Dragger>
        {importPreviewLoading ? <Spin size="small" /> : null}
        {importPreview ? (
          <Alert
            style={{ marginTop: 12 }}
            type={importPreview.invalidRows ? 'error' : 'success'}
            showIcon
            message={`共 ${importPreview.totalRows} 行，有效 ${importPreview.validRows} 行，错误 ${importPreview.invalidRows} 行`}
            description={importPreview.errors?.length ? (
              <Space orientation="vertical" size={2}>
                {importPreview.errors.slice(0, 5).map((error) => (
                  <Typography.Text key={`${error.rowNumber}-${error.message}`} type="danger">
                    第 {error.rowNumber} 行：{error.message}
                  </Typography.Text>
                ))}
              </Space>
            ) : '校验通过，保存后将原子创建字典及全部字典项。'}
          />
        ) : null}
      </Form.Item>
    ) : null}
  </Form>
);

const DictItemForm = ({ formProps, treeMode }: { formProps: FormProps; treeMode: boolean }) => (
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
    {treeMode ? (
      <>
        <Form.Item name="parentItemValue" label="父级字典值" extra="根节点留空；子节点填写父级 itemValue。">
          <Input />
        </Form.Item>
        <Form.Item name="levelNo" label="层级" rules={[{ required: true, message: '请输入层级' }]}>
          <InputNumber min={1} max={9} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="leaf" label="是否末级" rules={[{ required: true, message: '请选择是否末级' }]}>
          <Select options={[{ label: '是', value: true }, { label: '否', value: false }]} />
        </Form.Item>
      </>
    ) : null}
    <Form.Item name="remark" label={t('ui.settings.dicts.dictmanagementdrawers.remark')}>
      <Input.TextArea rows={3} />
    </Form.Item>
  </Form>
);

const dictTypeDetailColumns: ProDescriptionsProps<DictTypeRecord>['columns'] = [
  { title: t('ui.settings.dicts.dictmanagementdrawers.dictionaryCode'), dataIndex: 'dictCode' },
  { title: t('ui.settings.dicts.dictmanagementdrawers.dictionaryName'), dataIndex: 'dictName' },
  { title: '字典结构', dataIndex: 'structureType', renderText: (value) => (String(value) === 'TREE' ? '层级字典' : '普通字典') },
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
  allowTypeImport: boolean;
  typeFormProps: FormProps;
  importPreview?: DictionaryImportPreview;
  importPreviewLoading: boolean;
  onImportFileSelected: (file?: File) => void;
  onCloseTypeDrawer: () => void;
  onSaveType: () => void;
  typeDetailDrawerOpen: boolean;
  typeDetailDrawerTitle: string;
  typeDetailLoading: boolean;
  typeDetail: DictTypeRecord | null;
  detailProps: ProDescriptionsProps<DictTypeRecord>;
  itemColumns: ManagementTableProps<DictItemRecord>['columns'];
  itemRows: DictItemRecord[];
  itemTableActionRef: MutableRefObject<ActionType | null>;
  itemTableRequest?: ManagementTableProps<DictItemRecord>['request'];
  treeMode: boolean;
  onTreeExpand: (expanded: boolean, record: DictItemRecord) => void;
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
  allowTypeImport,
  typeFormProps,
  importPreview,
  importPreviewLoading,
  onImportFileSelected,
  onCloseTypeDrawer,
  onSaveType,
  typeDetailDrawerOpen,
  typeDetailDrawerTitle,
  typeDetailLoading,
  typeDetail,
  detailProps,
  itemColumns,
  itemRows,
  itemTableActionRef,
  itemTableRequest,
  treeMode,
  onTreeExpand,
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
      <DictTypeForm
        formProps={typeFormProps}
        allowImport={allowTypeImport}
        importPreview={importPreview}
        importPreviewLoading={importPreviewLoading}
        onImportFileSelected={onImportFileSelected}
      />
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
            actionRef={itemTableActionRef}
            rowKey="id"
            columns={itemColumns}
            isMobile={isMobile}
            request={treeMode ? undefined : itemTableRequest}
            dataSource={treeMode ? itemRows : undefined}
            search={treeMode ? false : searchConfig}
            pagination={treeMode ? false : { pageSize: 20, showSizeChanger: true }}
            expandable={treeMode ? {
              onExpand: onTreeExpand,
              rowExpandable: (record) => !record.leaf,
            } : undefined}
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
      <DictItemForm formProps={itemFormProps} treeMode={treeMode} />
    </ManagementDrawer>
    </>
  );
};
