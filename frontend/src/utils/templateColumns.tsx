import { Switch } from 'antd';

type TemplateEnabledRecord = {
  id: number;
  enabled?: boolean;
};

export const buildTemplateEnabledColumn = <T extends TemplateEnabledRecord>(
  updateEnabled: (id: number, enabled: boolean) => Promise<unknown>,
  reload: () => void,
) => ({
  title: '启用',
  dataIndex: 'enabled',
  render: (value: boolean, record: T) => (
    <Switch
      checked={value}
      onChange={(checked) => {
        void updateEnabled(record.id, checked).then(reload);
      }}
    />
  ),
});
