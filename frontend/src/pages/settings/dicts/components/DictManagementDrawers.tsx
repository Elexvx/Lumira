import { ProDescriptions, type ProDescriptionsProps } from '@ant-design/pro-components';
import { Form, Input, InputNumber, Select, Space, Spin } from 'antd';
import type { FormProps } from 'antd';
import type { ReactNode } from 'react';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementTable } from '@/features/management/ManagementTable';
import type { ManagementTableProps } from '@/features/management/ManagementTable';
import type { DictItemRecord, DictTypeRecord } from '@/types/api';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

const dictStatusOptions = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
];

const DictTypeForm = ({ formProps }: { formProps: FormProps }) => (
  <Form {...formProps}>
    <Form.Item name="dictCode" label="字典编码" rules={[{ required: true, message: '请输入字典编码' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="dictName" label="字典名称" rules={[{ required: true, message: '请输入字典名称' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="status" label="状态">
      <Select options={dictStatusOptions} />
    </Form.Item>
    <Form.Item name="remark" label="备注">
      <Input.TextArea rows={3} />
    </Form.Item>
  </Form>
);

const DictItemForm = ({ formProps }: { formProps: FormProps }) => (
  <Form {...formProps}>
    <Form.Item name="itemLabel" label="标签" rules={[{ required: true, message: '请输入标签' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="itemValue" label="值" rules={[{ required: true, message: '请输入值' }]}>
      <Input />
    </Form.Item>
    <Form.Item name="sortNo" label="排序">
      <InputNumber style={{ width: '100%' }} />
    </Form.Item>
    <Form.Item name="status" label="状态">
      <Select options={dictStatusOptions} />
    </Form.Item>
    <Form.Item name="remark" label="备注">
      <Input.TextArea rows={3} />
    </Form.Item>
  </Form>
);

const dictTypeDetailColumns: ProDescriptionsProps<DictTypeRecord>['columns'] = [
  { title: '字典编码', dataIndex: 'dictCode' },
  { title: '字典名称', dataIndex: 'dictName' },
  { title: '状态', dataIndex: 'status', renderText: (value) => (String(value) === 'ENABLED' ? '启用' : '停用') },
  {
    title: '系统内置',
    dataIndex: 'isSystem',
    renderText: (value) => (Number(value) !== 0 ? '是' : '否'),
  },
  { title: '备注', dataIndex: 'remark', renderText: (value) => value || '-' },
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
        { key: 'cancel', label: '取消', onClick: onCloseTypeDrawer },
        { key: 'save', label: '保存', type: 'primary', loading: typeDrawerSaving, disabled: !canSaveType, onClick: onSaveType },
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
                  label: '新增项',
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
        { key: 'cancel', label: '取消', onClick: onCloseItemDrawer },
        { key: 'save', label: '保存', type: 'primary', loading: itemDrawerSaving, disabled: !canSaveItem, onClick: onSaveItem },
      ]}
    >
      <DictItemForm formProps={itemFormProps} />
    </ManagementDrawer>
    </>
  );
};
