import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useState } from 'react';
import { DataTable } from '@/features/table/DataTable';
import { useResponsive } from '@/hooks/useResponsive';
import { saveCompetitionSettingsModule } from '@/services/competition/api';
import type { CompetitionConfigItem, CompetitionSettingsRecord } from '@/services/competition/types';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import {
  buildCompetitionAwardSettingsItem,
  COMPETITION_AWARD_SETTINGS_ITEM_KEY,
  getCompetitionAwardSettings,
  MAIN_COMPETITION_AWARD_NAMES,
  normalizeCompetitionAwardSettings,
  type CompetitionAwardQuotaType,
  type CompetitionAwardSettings,
  type CompetitionAwardSpecialAward,
} from '@/pages/competition/utils/competitionAwardSettings';
import type { CompetitionSettingsPanelHandle } from './CompetitionSettingsPanelHandle';

type CompetitionAwardSettingsPanelProps = {
  competitionUuid: string;
  items: CompetitionConfigItem[];
  onSaved: (settings: CompetitionSettingsRecord) => void;
};

type AwardSettingsForm = CompetitionAwardSettings;

type SpecialAwardForm = Pick<CompetitionAwardSpecialAward, 'awardName' | 'quota' | 'quotaType'>;

const AWARD_QUOTA_TYPE_OPTIONS: Array<{ label: string; value: CompetitionAwardQuotaType }> = [
  { label: '固定数量', value: 'FIXED' },
  { label: '按比例', value: 'PERCENTAGE' },
];

const AWARD_FORM_LABEL_WIDTH = 104;
const AWARD_FORM_GAP = 12;
const AWARD_FORM_LABEL_TEXT_OFFSET = 12;
const AWARD_CONTROL_WIDTH = 208;
const AWARD_QUOTA_ADDON_CONTENT_WIDTH = 14;

const quotaAddon = (quotaType: CompetitionAwardQuotaType) => (
  <span style={{
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: AWARD_QUOTA_ADDON_CONTENT_WIDTH,
  }}>
    {quotaType === 'PERCENTAGE' ? '%' : '名'}
  </span>
);

const createSpecialAward = (): CompetitionAwardSpecialAward => ({
  id: `special-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  awardName: '',
  quota: 1,
  quotaType: 'FIXED',
});

const quotaRules = (quotaType: CompetitionAwardQuotaType, required = true) => [{
  required,
  type: 'number' as const,
  min: 1,
  max: quotaType === 'PERCENTAGE' ? 100 : 10000,
  message: quotaType === 'PERCENTAGE' ? '请输入 1–100 的整数比例' : '请输入 1–10000 的整数数量',
  validator: async (_: unknown, value: unknown) => {
    if (value == null && !required) return;
    if (!Number.isInteger(value)) throw new Error('请输入整数');
  },
}];

const CompetitionAwardSettingsPanel = forwardRef<CompetitionSettingsPanelHandle, CompetitionAwardSettingsPanelProps>(({
  competitionUuid,
  items,
  onSaved,
}, ref) => {
  const responsive = useResponsive();
  const [form] = Form.useForm<AwardSettingsForm>();
  const [specialAwardForm] = Form.useForm<SpecialAwardForm>();
  const [specialAwards, setSpecialAwards] = useState<CompetitionAwardSpecialAward[]>([]);
  const [specialAwardModalOpen, setSpecialAwardModalOpen] = useState(false);
  const [editingSpecialAwardId, setEditingSpecialAwardId] = useState<string | null>(null);
  const existingItem = items.find((item) => item.itemKey === COMPETITION_AWARD_SETTINGS_ITEM_KEY);

  useEffect(() => {
    const settings = getCompetitionAwardSettings(items);
    form.setFieldsValue(settings);
    setSpecialAwards(settings.specialAwards);
  }, [form, items]);

  const updateSpecialAwards = useCallback((nextAwards: CompetitionAwardSpecialAward[]) => {
    setSpecialAwards(nextAwards);
    form.setFieldValue('specialAwards', nextAwards);
  }, [form]);

  const save = useCallback(async () => {
    try {
      const values = await form.validateFields();
      const settings = normalizeCompetitionAwardSettings({ ...values, specialAwards });
      const awardNames = [
        ...MAIN_COMPETITION_AWARD_NAMES,
        ...(settings.excellenceEnabled ? ['优秀奖'] : []),
        ...settings.specialAwards.map((award) => award.awardName.trim()),
      ];
      if (awardNames.some((name) => !name) || new Set(awardNames).size !== awardNames.length) {
        form.setFields([{ name: ['specialAwards'], errors: ['奖项名称不能为空且不能重复'] }]);
        return false;
      }
      const saved = await saveCompetitionSettingsModule(
        competitionUuid,
        'awards',
        [buildCompetitionAwardSettingsItem(existingItem, settings)],
        API_OPTS.SILENT,
      );
      onSaved(saved);
      return true;
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return false;
      }
      showErrorMessage(error, '获奖设置保存失败');
      return false;
    }
  }, [competitionUuid, existingItem, form, onSaved, specialAwards]);

  const openSpecialAwardModal = useCallback((award?: CompetitionAwardSpecialAward) => {
    setEditingSpecialAwardId(award?.id || null);
    specialAwardForm.resetFields();
    specialAwardForm.setFieldsValue(award
      ? {
          awardName: award.awardName,
          quota: award.quota,
          quotaType: award.quotaType,
        }
      : {
          awardName: '',
          quota: 1,
          quotaType: 'FIXED',
        });
    setSpecialAwardModalOpen(true);
  }, [specialAwardForm]);

  const closeSpecialAwardModal = useCallback(() => {
    setSpecialAwardModalOpen(false);
    setEditingSpecialAwardId(null);
    specialAwardForm.resetFields();
  }, [specialAwardForm]);

  const saveSpecialAward = useCallback(async () => {
    try {
      const values = await specialAwardForm.validateFields();
      const awardName = values.awardName.trim();
      const isDuplicate = MAIN_COMPETITION_AWARD_NAMES.some((name) => name === awardName)
        || specialAwards.some((award) => award.id !== editingSpecialAwardId && award.awardName.trim() === awardName);
      if (isDuplicate) {
        specialAwardForm.setFields([{ name: 'awardName', errors: ['奖项名称不能重复'] }]);
        return;
      }
      if (!editingSpecialAwardId && specialAwards.length >= 20) {
        specialAwardForm.setFields([{ name: 'awardName', errors: ['专项奖项最多添加 20 个'] }]);
        return;
      }
      const nextAward: CompetitionAwardSpecialAward = {
        id: editingSpecialAwardId || createSpecialAward().id,
        awardName,
        quota: Number(values.quota),
        quotaType: values.quotaType,
      };
      const nextAwards = editingSpecialAwardId
        ? specialAwards.map((award) => award.id === editingSpecialAwardId ? nextAward : award)
        : [...specialAwards, nextAward];
      updateSpecialAwards(nextAwards);
      closeSpecialAwardModal();
    } catch {
      // Keep the modal open so the user can correct validation errors.
    }
  }, [closeSpecialAwardModal, editingSpecialAwardId, specialAwardForm, specialAwards, updateSpecialAwards]);

  const moveSpecialAward = useCallback((id: string, offset: -1 | 1) => {
    const currentIndex = specialAwards.findIndex((award) => award.id === id);
    const targetIndex = currentIndex + offset;
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= specialAwards.length) return;
    const nextAwards = [...specialAwards];
    [nextAwards[currentIndex], nextAwards[targetIndex]] = [nextAwards[targetIndex], nextAwards[currentIndex]];
    updateSpecialAwards(nextAwards);
  }, [specialAwards, updateSpecialAwards]);

  const removeSpecialAward = useCallback((id: string) => {
    updateSpecialAwards(specialAwards.filter((award) => award.id !== id));
  }, [specialAwards, updateSpecialAwards]);

  const specialAwardQuotaType = Form.useWatch('quotaType', specialAwardForm) || 'FIXED';

  const specialAwardColumns: ColumnsType<CompetitionAwardSpecialAward> = [
    {
      title: '序号',
      key: 'index',
      width: 80,
      align: 'center',
      render: (_value: unknown, _award: CompetitionAwardSpecialAward, index: number) => index + 1,
    },
    {
      title: '奖项名称',
      dataIndex: 'awardName',
      key: 'awardName',
      render: (awardName: string) => (
        <span style={{ display: 'block', whiteSpace: 'normal', overflowWrap: 'anywhere' }}>
          {awardName}
        </span>
      ),
    },
    {
      title: '计算方式',
      dataIndex: 'quotaType',
      key: 'quotaType',
      render: (quotaType: CompetitionAwardQuotaType) => quotaType === 'PERCENTAGE' ? '按比例' : '固定数量',
    },
    {
      title: '名额值',
      key: 'quota',
      render: (_value: unknown, award: CompetitionAwardSpecialAward) =>
        `${award.quota}${award.quotaType === 'PERCENTAGE' ? '%' : '名'}`,
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_value: unknown, award: CompetitionAwardSpecialAward, index: number) => (
        <Space size={2}>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openSpecialAwardModal(award)}
          >
            编辑
          </Button>
          <Button
            type="text"
            size="small"
            aria-label="上移专项奖项"
            icon={<ArrowUpOutlined />}
            disabled={index === 0}
            onClick={() => moveSpecialAward(award.id, -1)}
          />
          <Button
            type="text"
            size="small"
            aria-label="下移专项奖项"
            icon={<ArrowDownOutlined />}
            disabled={index === specialAwards.length - 1}
            onClick={() => moveSpecialAward(award.id, 1)}
          />
          <Button
            type="text"
            size="small"
            danger
            aria-label="删除专项奖项"
            icon={<DeleteOutlined />}
            onClick={() => removeSpecialAward(award.id)}
          />
        </Space>
      ),
    },
  ];

  useImperativeHandle(ref, () => ({
    saveNow: save,
  }), [save]);

  return (
    <Space orientation="vertical" size={18} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 0 }}>获奖设置</Typography.Title>

      <Form form={form} layout="vertical" colon={false}>
        <Typography.Title level={5} style={{ marginTop: 0 }}>主奖项</Typography.Title>
        <Form.Item
          name="mainQuotaType"
          label={<span style={{ fontWeight: 400 }}>计算方式</span>}
          layout="horizontal"
          labelAlign="left"
          labelCol={{ flex: `0 0 ${AWARD_FORM_LABEL_WIDTH + AWARD_FORM_GAP}px` }}
          rules={[{ required: true, message: '请选择主奖项计算方式' }]}
          style={{ marginTop: 12, marginBottom: 12 }}
        >
          <Select style={{ width: AWARD_CONTROL_WIDTH }} options={AWARD_QUOTA_TYPE_OPTIONS} />
        </Form.Item>
        <Space orientation="vertical" size={10} style={{ width: '100%' }}>
          {MAIN_COMPETITION_AWARD_NAMES.map((awardName, index) => (
            <div key={awardName} style={{ display: 'flex', alignItems: 'center', gap: AWARD_FORM_GAP }}>
              <Typography.Text style={{
                display: 'inline-block',
                width: AWARD_FORM_LABEL_WIDTH,
                flex: `0 0 ${AWARD_FORM_LABEL_WIDTH}px`,
                boxSizing: 'border-box',
                paddingLeft: AWARD_FORM_LABEL_TEXT_OFFSET,
              }}>
                {awardName}
              </Typography.Text>
              <Form.Item noStyle shouldUpdate={(previous, current) => previous.mainQuotaType !== current.mainQuotaType}>
                {({ getFieldValue }) => {
                  const quotaType = getFieldValue('mainQuotaType') as CompetitionAwardQuotaType;
                  return (
                    <Form.Item
                      name={['mainAwards', index, 'quota']}
                      rules={quotaRules(quotaType)}
                      style={{ marginBottom: 0 }}
                    >
                      <InputNumber
                        min={1}
                        max={quotaType === 'PERCENTAGE' ? 100 : 10000}
                        precision={0}
                        addonAfter={quotaAddon(quotaType)}
                        style={{ width: 170 }}
                      />
                    </Form.Item>
                  );
                }}
              </Form.Item>
            </div>
          ))}
        </Space>

        <div style={{ margin: '22px 0 18px', borderTop: '1px solid var(--ant-color-border)' }} />
        <div style={{
          display: 'flex',
          alignItems: 'center',
          marginBottom: 12,
        }}>
          <Typography.Title level={5} style={{ margin: 0 }}>子奖项</Typography.Title>
        </div>
        <div style={{ display: 'flex', width: '100%', marginBottom: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: AWARD_FORM_GAP }}>
            <Typography.Text style={{
              display: 'inline-block',
              width: AWARD_FORM_LABEL_WIDTH,
              flex: `0 0 ${AWARD_FORM_LABEL_WIDTH}px`,
              boxSizing: 'border-box',
              paddingLeft: AWARD_FORM_LABEL_TEXT_OFFSET,
            }}>
              设置优秀奖
            </Typography.Text>
            <Form.Item name="excellenceEnabled" valuePropName="checked" style={{ marginBottom: 0 }}>
              <Switch />
            </Form.Item>
          </div>
        </div>
        <div>
          <Form.Item noStyle shouldUpdate={(previous, current) =>
            previous.excellenceEnabled !== current.excellenceEnabled || previous.mainQuotaType !== current.mainQuotaType
          }>
            {({ getFieldValue }) => {
              const enabled = Boolean(getFieldValue('excellenceEnabled'));
              const quotaType = getFieldValue('mainQuotaType') as CompetitionAwardQuotaType;
              return enabled ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: AWARD_FORM_GAP }}>
                  <Typography.Text style={{
                    display: 'inline-block',
                    width: AWARD_FORM_LABEL_WIDTH,
                    flex: `0 0 ${AWARD_FORM_LABEL_WIDTH}px`,
                    boxSizing: 'border-box',
                    paddingLeft: AWARD_FORM_LABEL_TEXT_OFFSET,
                  }}>
                    优秀奖
                  </Typography.Text>
                  <Form.Item name="excellenceQuota" rules={quotaRules(quotaType)} style={{ marginBottom: 0 }}>
                    <InputNumber
                      min={1}
                      max={quotaType === 'PERCENTAGE' ? 100 : 10000}
                      precision={0}
                      addonAfter={quotaAddon(quotaType)}
                      style={{ width: 170 }}
                    />
                  </Form.Item>
                </div>
              ) : null;
            }}
          </Form.Item>
        </div>

        <div style={{ margin: '22px 0 18px', borderTop: '1px solid var(--ant-color-border)' }} />
        <Typography.Title level={5} style={{ marginTop: 0 }}>专项奖项</Typography.Title>
        <DataTable<CompetitionAwardSpecialAward>
          rowKey="id"
          isMobile={responsive.isMobile}
          size="middle"
          tableLayout="auto"
          pagination={false}
          dataSource={specialAwards}
          columns={specialAwardColumns}
          locale={{ emptyText: '暂无专项奖项' }}
        />
        <div style={{ marginTop: 12 }}>
          <Button
            icon={<PlusOutlined />}
            disabled={specialAwards.length >= 20}
            onClick={() => openSpecialAwardModal()}
          >
            新增专项奖项
          </Button>
        </div>
      </Form>

      <Modal
        title={editingSpecialAwardId ? '编辑专项奖项' : '新增专项奖项'}
        open={specialAwardModalOpen}
        width={520}
        className="competition-award-special-award-modal"
        destroyOnClose
        okText="确定"
        cancelText="取消"
        onCancel={closeSpecialAwardModal}
        onOk={saveSpecialAward}
      >
        <Form form={specialAwardForm} layout="vertical" colon={false}>
          <Form.Item
            name="awardName"
            label={<span style={{ fontWeight: 400 }}>奖项名称</span>}
            rules={[{ required: true, whitespace: true, message: '请输入专项奖项名称' }]}
            style={{ marginBottom: 16 }}
          >
            <Input placeholder="请输入专项奖项名称" />
          </Form.Item>
          <Form.Item
            name="quotaType"
            label={<span style={{ fontWeight: 400 }}>计算方式</span>}
            rules={[{ required: true, message: '请选择计算方式' }]}
            style={{ marginBottom: 16 }}
          >
            <Select options={AWARD_QUOTA_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="quota"
            className="competition-award-special-award-form__quota"
            label={<span style={{ fontWeight: 400 }}>名额值</span>}
            rules={quotaRules(specialAwardQuotaType)}
            style={{ marginBottom: 0 }}
          >
            <InputNumber
              min={1}
              max={specialAwardQuotaType === 'PERCENTAGE' ? 100 : 10000}
              precision={0}
              addonAfter={quotaAddon(specialAwardQuotaType)}
              style={{ width: '100%' }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
});

CompetitionAwardSettingsPanel.displayName = 'CompetitionAwardSettingsPanel';

export default CompetitionAwardSettingsPanel;
