import type { ProColumns } from '@ant-design/pro-components';
import { Switch } from 'antd';

type TemplateEnabledRecord = {
  id: number;
  enabled?: boolean;
};

export const buildTemplateEnabledColumn = <T extends TemplateEnabledRecord>(
  updateEnabled: (id: number, enabled: boolean) => Promise<unknown>,
  reload: () => void,
): ProColumns<T> => ({
  title: '启用',
  dataIndex: 'enabled',
  render: (_, record) => (
    <Switch
      checked={record.enabled}
      onChange={(checked) => {
        void updateEnabled(record.id, checked).then(reload);
      }}
    />
  ),
});
