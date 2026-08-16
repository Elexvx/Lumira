import { Form, InputNumber, Space, Tag, Typography } from 'antd';
import { forwardRef, useCallback, useEffect, useImperativeHandle } from 'react';
import { saveCompetitionSettingsModule } from '@/services/competition/api';
import type { CompetitionConfigItem, CompetitionSettingsRecord } from '@/services/competition/types';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import {
  buildCompetitionAwardSettingsItem,
  COMPETITION_AWARD_SETTINGS_ITEM_KEY,
  DEFAULT_COMPETITION_AWARD_RULES,
  getCompetitionAwardRules,
  toCompetitionAwardRankRules,
  type CompetitionAwardRuleSetting,
} from '@/pages/competition/utils/competitionAwardSettings';
import type { CompetitionSettingsPanelHandle } from './CompetitionSettingsPanelHandle';

type CompetitionAwardSettingsPanelProps = {
  competitionUuid: string;
  items: CompetitionConfigItem[];
  onSaved: (settings: CompetitionSettingsRecord) => void;
};

type AwardSettingsForm = {
  rules: CompetitionAwardRuleSetting[];
};

const CompetitionAwardSettingsPanel = forwardRef<CompetitionSettingsPanelHandle, CompetitionAwardSettingsPanelProps>(({
  competitionUuid,
  items,
  onSaved,
}, ref) => {
  const [form] = Form.useForm<AwardSettingsForm>();
  const existingItem = items.find((item) => item.itemKey === COMPETITION_AWARD_SETTINGS_ITEM_KEY);

  useEffect(() => {
    form.setFieldsValue({ rules: getCompetitionAwardRules(items) });
  }, [form, items]);

  const save = useCallback(async () => {
    try {
      const values = await form.validateFields();
      const rules = values.rules.map((rule) => ({
        awardName: rule.awardName.trim(),
        quota: Number(rule.quota),
      }));
      const saved = await saveCompetitionSettingsModule(
        competitionUuid,
        'awards',
        [buildCompetitionAwardSettingsItem(existingItem, rules)],
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
  }, [competitionUuid, existingItem, form, onSaved]);

  useImperativeHandle(ref, () => ({
    saveNow: save,
  }), [save]);

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <div>
        <Typography.Title level={4} style={{ marginTop: 0 }}>获奖设置</Typography.Title>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          发布评审排行后，系统按以下四档名额从第 1 名开始连续换算名次，并在评审页面生成获奖名单。
        </Typography.Paragraph>
      </div>
      <Form form={form} layout="vertical">
        <Space orientation="vertical" size={12} style={{ width: '100%' }}>
          <Form.List name="rules">
            {(fields) => (
              fields.map((field, index) => (
                <Space key={field.key} align="baseline" wrap style={{ width: '100%' }}>
                  <Form.Item
                    name={[field.name, 'awardName']}
                    hidden
                    rules={[{ required: true, whitespace: true, message: '奖项名称不能为空' }]}
                  >
                    <input type="hidden" />
                  </Form.Item>
                  <Form.Item label={index === 0 ? '奖项' : undefined}>
                    <Typography.Text strong style={{ display: 'inline-block', minWidth: 88 }}>
                      {DEFAULT_COMPETITION_AWARD_RULES[index]?.awardName || '奖项'}
                    </Typography.Text>
                  </Form.Item>
                  <Form.Item
                    label={index === 0 ? '获奖名额' : undefined}
                    name={[field.name, 'quota']}
                    rules={[{ required: true, type: 'number', min: 1, max: 10000, message: '请输入 1–10000 的名额' }]}
                  >
                    <InputNumber min={1} max={10000} precision={0} addonAfter="名" />
                  </Form.Item>
                  <Form.Item noStyle shouldUpdate={(previous, current) => previous.rules !== current.rules}>
                    {({ getFieldValue }) => {
                      const rules = getFieldValue('rules') as CompetitionAwardRuleSetting[] | undefined;
                      const rankRule = toCompetitionAwardRankRules(rules || DEFAULT_COMPETITION_AWARD_RULES)[index];
                      return rankRule ? <Tag color="blue">第 {rankRule.minRank}–{rankRule.maxRank} 名</Tag> : null;
                    }}
                  </Form.Item>
                </Space>
              ))
            )}
          </Form.List>
        </Space>
      </Form>
    </Space>
  );
});

CompetitionAwardSettingsPanel.displayName = 'CompetitionAwardSettingsPanel';

export default CompetitionAwardSettingsPanel;
