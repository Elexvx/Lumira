import {
  AlertOutlined,
  BellOutlined,
  CheckOutlined,
  CloudSyncOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { DataTable } from '@/features/table/DataTable';
import { useResponsive } from '@/hooks/useResponsive';
import { request } from '@/services/common/request';
import { useAccess, useModel } from '@umijs/max';
import {
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Result,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';

type ChannelType =
  | 'WECOM_WEBHOOK' | 'WECOM_APP' | 'FEISHU_WEBHOOK' | 'FEISHU_APP'
  | 'DINGTALK_WEBHOOK' | 'DINGTALK_APP' | 'EMAIL_SYSTEM_SMTP' | 'EMAIL_CUSTOM_SMTP';

interface CatalogSignal {
  key: string; name: string; sourceType: 'PROMETHEUS' | 'BUSINESS_EVENT'; unit: string;
  comparators: string[]; description: string;
}
interface ChannelRecord {
  id: number; name: string; type: ChannelType; enabled: boolean; config: Record<string, unknown>;
  secretConfigured: boolean; lastTestStatus?: string; lastTestError?: string; lastTestAt?: string;
  version: number; updatedAt: string;
}
interface ContactMember {
  id?: number; channelId: number; channelName?: string; channelType?: ChannelType;
  memberType: string; targetIdentifier: string; displayName?: string; enabled: boolean;
}
interface ContactGroup {
  id: number; name: string; enabled: boolean; members: ContactMember[]; version: number; updatedAt: string;
}
interface RuleRecord {
  id: number; name: string; sourceType: string; signalKey: string; comparator: string; threshold: number;
  windowSeconds: number; pendingSeconds: number; severity: string; contactGroupId: number;
  contactGroupName: string; enabled: boolean; labels: Record<string, string>; evaluationError?: string;
  lastEvaluatedAt?: string; version: number;
}
interface AlertInstance {
  id: number; ruleId: number; ruleName: string; severity: string; status: string; lastValue?: number;
  startedAt: string; firingAt?: string; resolvedAt?: string; acknowledgedAt?: string;
  acknowledgedBy?: number; evaluationError?: string; version: number;
}
interface SilenceRecord {
  id: number; name: string; ruleId?: number; ruleName?: string; startsAt: string; endsAt: string;
  reason: string; enabled: boolean; version: number; updatedAt: string;
}
interface DeliveryRecord {
  id: number; instanceId: number; eventType: string; channelName: string; channelType: ChannelType;
  recipient: string; status: string; attempts: number; lastError?: string; nextAttemptAt?: string;
  sentAt?: string; createdAt: string;
}
interface DirectoryMapping {
  id: number; channelId: number; userId: number; userUuid: string; providerUserId: string;
  providerDisplayName?: string; matchSource: string; status: string; manualOverride: boolean; syncedAt?: string;
}
interface HealthRecord {
  pluginEnabled: boolean; workerStatus: string; workerHeartbeatAt?: string; enabledRules: number;
  firingAlerts: number; pendingDeliveries: number; deadLetters: number; lastEvaluationError?: string;
}

type ChannelFormValue = Omit<ChannelRecord, 'id' | 'lastTestStatus' | 'lastTestError' | 'lastTestAt' | 'updatedAt' | 'secretConfigured'>;
type RuleFormValue = Omit<RuleRecord, 'id' | 'contactGroupName' | 'evaluationError' | 'lastEvaluatedAt'>;
type GroupFormValue = Omit<ContactGroup, 'id' | 'updatedAt'>;
type SilenceFormValue = Omit<SilenceRecord, 'id' | 'ruleName' | 'updatedAt' | 'startsAt' | 'endsAt'> & { startsAt: Dayjs; endsAt: Dayjs };

const CHANNEL_LABELS: Record<ChannelType, string> = {
  WECOM_WEBHOOK: '企业微信群机器人', WECOM_APP: '企业微信应用机器人',
  FEISHU_WEBHOOK: '飞书群机器人', FEISHU_APP: '飞书企业应用',
  DINGTALK_WEBHOOK: '钉钉群机器人', DINGTALK_APP: '钉钉企业应用',
  EMAIL_SYSTEM_SMTP: '系统 SMTP', EMAIL_CUSTOM_SMTP: '独立 SMTP',
};
const CHANNEL_OPTIONS = Object.entries(CHANNEL_LABELS).map(([value, label]) => ({ value, label }));
const STATUS_COLORS: Record<string, string> = {
  FIRING: 'red', PENDING: 'orange', RESOLVED: 'green', SENT: 'green', SUCCESS: 'green',
  FAILED: 'red', DEAD_LETTER: 'volcano', RETRY: 'orange', PAUSED: 'default', SENDING: 'blue',
  MATCHED: 'green', AMBIGUOUS: 'orange', UNMATCHED: 'default', HEALTHY: 'green', STALE: 'red', NEVER_SEEN: 'default',
};

const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-';
const statusTag = (status?: string) => status ? <Tag color={STATUS_COLORS[status] || 'blue'}>{status}</Tag> : '-';

const ChannelConfigFields = ({ type }: { type?: ChannelType }) => {
  if (!type) return null;
  const rateLimit = <Form.Item name={['config', 'rateLimitPerMinute']} label="每分钟发送上限" initialValue={60}>
    <InputNumber min={1} max={600} style={{ width: '100%' }} />
  </Form.Item>;
  if (type.endsWith('_WEBHOOK')) return <>
    <Form.Item name={['config', 'webhookUrl']} label="机器人 Webhook" rules={[{ required: true }]}>
      <Input.Password placeholder="仅允许对应平台官方 HTTPS 地址" autoComplete="new-password" />
    </Form.Item>
    <Form.Item name={['config', 'signSecret']} label="签名密钥（可选）">
      <Input.Password placeholder="留空表示平台未启用签名；****** 表示保留原值" autoComplete="new-password" />
    </Form.Item>
    {rateLimit}
  </>;
  if (type === 'WECOM_APP') return <>
    <Form.Item name={['config', 'corpId']} label="Corp ID" rules={[{ required: true }]}><Input /></Form.Item>
    <Form.Item name={['config', 'agentId']} label="Agent ID" rules={[{ required: true }]}><Input /></Form.Item>
    <Form.Item name={['config', 'secret']} label="应用 Secret" rules={[{ required: true }]}><Input.Password autoComplete="new-password" /></Form.Item>
    <Form.Item name={['config', 'testRecipient']} label="测试接收人 userId"><Input /></Form.Item>
    {rateLimit}
  </>;
  if (type === 'FEISHU_APP') return <>
    <Form.Item name={['config', 'appId']} label="App ID" rules={[{ required: true }]}><Input /></Form.Item>
    <Form.Item name={['config', 'appSecret']} label="App Secret" rules={[{ required: true }]}><Input.Password autoComplete="new-password" /></Form.Item>
    <Form.Item name={['config', 'receiveIdType']} label="接收 ID 类型" initialValue="user_id">
      <Select options={[{ value: 'user_id' }, { value: 'open_id' }, { value: 'union_id' }, { value: 'chat_id' }]} />
    </Form.Item>
    <Form.Item name={['config', 'testRecipient']} label="测试接收人 ID"><Input /></Form.Item>
    {rateLimit}
  </>;
  if (type === 'DINGTALK_APP') return <>
    <Form.Item name={['config', 'clientId']} label="Client ID" rules={[{ required: true }]}><Input /></Form.Item>
    <Form.Item name={['config', 'clientSecret']} label="Client Secret" rules={[{ required: true }]}><Input.Password autoComplete="new-password" /></Form.Item>
    <Form.Item name={['config', 'robotCode']} label="Robot Code" rules={[{ required: true }]}><Input /></Form.Item>
    <Form.Item name={['config', 'testRecipient']} label="测试群 openConversationId"><Input /></Form.Item>
    {rateLimit}
  </>;
  if (type === 'EMAIL_SYSTEM_SMTP') return <>
    <Form.Item name={['config', 'from']} label="发件地址" rules={[{ required: true, type: 'email' }]}><Input /></Form.Item>
    <Form.Item name={['config', 'testRecipient']} label="测试收件地址" rules={[{ type: 'email' }]}><Input /></Form.Item>
    {rateLimit}
  </>;
  return <>
    <Form.Item name={['config', 'host']} label="SMTP 主机" rules={[{ required: true }]}><Input /></Form.Item>
    <Form.Item name={['config', 'port']} label="端口" rules={[{ required: true }]}><InputNumber min={1} max={65535} style={{ width: '100%' }} /></Form.Item>
    <Form.Item name={['config', 'username']} label="用户名" rules={[{ required: true }]}><Input /></Form.Item>
    <Form.Item name={['config', 'password']} label="密码" rules={[{ required: true }]}><Input.Password autoComplete="new-password" /></Form.Item>
    <Form.Item name={['config', 'from']} label="发件地址" rules={[{ required: true, type: 'email' }]}><Input /></Form.Item>
    <Form.Item name={['config', 'testRecipient']} label="测试收件地址" rules={[{ type: 'email' }]}><Input /></Form.Item>
    <Space>
      <Form.Item name={['config', 'ssl']} label="SSL" valuePropName="checked"><Switch /></Form.Item>
      <Form.Item name={['config', 'startTls']} label="STARTTLS" valuePropName="checked"><Switch /></Form.Item>
    </Space>
    {rateLimit}
  </>;
};

const AlertingPage = () => {
  const { message } = App.useApp();
  const responsive = useResponsive();
  const access = useAccess();
  const { initialState } = useModel('@@initialState');
  const enabledInBootstrap = initialState?.availablePlugins?.some((item) => item.pluginCode === 'builtin-alerting');
  const canManage = access.hasPermission?.('plugin:alerting:manage') ?? false;
  const canManageChannels = access.hasPermission?.('plugin:alerting:channel-manage') ?? false;
  const canAck = access.hasPermission?.('plugin:alerting:ack') ?? false;
  const canSilence = access.hasPermission?.('plugin:alerting:silence') ?? false;
  const canSyncDirectory = access.hasPermission?.('plugin:alerting:directory-sync') ?? false;

  const [loading, setLoading] = useState(false);
  const [health, setHealth] = useState<HealthRecord>();
  const [catalog, setCatalog] = useState<CatalogSignal[]>([]);
  const [channels, setChannels] = useState<ChannelRecord[]>([]);
  const [groups, setGroups] = useState<ContactGroup[]>([]);
  const [rules, setRules] = useState<RuleRecord[]>([]);
  const [instances, setInstances] = useState<AlertInstance[]>([]);
  const [silences, setSilences] = useState<SilenceRecord[]>([]);
  const [deliveries, setDeliveries] = useState<DeliveryRecord[]>([]);
  const [mappings, setMappings] = useState<DirectoryMapping[]>([]);

  const [channelOpen, setChannelOpen] = useState(false);
  const [channelEditing, setChannelEditing] = useState<ChannelRecord>();
  const [groupOpen, setGroupOpen] = useState(false);
  const [groupEditing, setGroupEditing] = useState<ContactGroup>();
  const [ruleOpen, setRuleOpen] = useState(false);
  const [ruleEditing, setRuleEditing] = useState<RuleRecord>();
  const [silenceOpen, setSilenceOpen] = useState(false);
  const [silenceEditing, setSilenceEditing] = useState<SilenceRecord>();
  const [mappingOpen, setMappingOpen] = useState(false);
  const [channelForm] = Form.useForm<ChannelFormValue>();
  const [groupForm] = Form.useForm<GroupFormValue>();
  const [ruleForm] = Form.useForm<RuleFormValue>();
  const [silenceForm] = Form.useForm<SilenceFormValue>();
  const [mappingForm] = Form.useForm();
  const selectedChannelType = Form.useWatch('type', channelForm) as ChannelType | undefined;
  const selectedSignalKey = Form.useWatch('signalKey', ruleForm) as string | undefined;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [nextHealth, nextCatalog, nextChannels, nextGroups, nextRules, nextInstances, nextSilences, nextDeliveries, nextMappings] = await Promise.all([
        request<HealthRecord>('/v2/alerting/health'), request<CatalogSignal[]>('/v2/alerting/catalog'),
        request<ChannelRecord[]>('/v2/alerting/channels'), request<ContactGroup[]>('/v2/alerting/contact-groups'),
        request<RuleRecord[]>('/v2/alerting/rules'), request<AlertInstance[]>('/v2/alerting/instances'),
        request<SilenceRecord[]>('/v2/alerting/silences'), request<DeliveryRecord[]>('/v2/alerting/deliveries'),
        request<DirectoryMapping[]>('/v2/alerting/directory/mappings'),
      ]);
      setHealth(nextHealth); setCatalog(nextCatalog); setChannels(nextChannels); setGroups(nextGroups);
      setRules(nextRules); setInstances(nextInstances); setSilences(nextSilences);
      setDeliveries(nextDeliveries); setMappings(nextMappings);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '告警中心加载失败');
    } finally { setLoading(false); }
  }, [message]);

  useEffect(() => { if (enabledInBootstrap) void load(); }, [enabledInBootstrap, load]);

  const signal = useMemo(() => catalog.find((item) => item.key === selectedSignalKey), [catalog, selectedSignalKey]);
  useEffect(() => { if (signal) ruleForm.setFieldValue('sourceType', signal.sourceType); }, [ruleForm, signal]);

  if (!enabledInBootstrap) return <Result status="warning" title="内置告警插件尚未启用" subTitle="请由平台管理员在插件管理中心启用。启用前后台工作器保持空闲，不会发送任何通知。" />;

  const saveChannel = async () => {
    const value = await channelForm.validateFields();
    const path = channelEditing ? `/v2/alerting/channels/${channelEditing.id}` : '/v2/alerting/channels';
    await request(path, { method: channelEditing ? 'PUT' : 'POST', data: { ...value, version: channelEditing?.version } });
    message.success('渠道已保存'); setChannelOpen(false); await load();
  };
  const saveGroup = async () => {
    const value = await groupForm.validateFields();
    const path = groupEditing ? `/v2/alerting/contact-groups/${groupEditing.id}` : '/v2/alerting/contact-groups';
    await request(path, { method: groupEditing ? 'PUT' : 'POST', data: { ...value, version: groupEditing?.version } });
    message.success('联系人组已保存'); setGroupOpen(false); await load();
  };
  const saveRule = async () => {
    const value = await ruleForm.validateFields();
    const path = ruleEditing ? `/v2/alerting/rules/${ruleEditing.id}` : '/v2/alerting/rules';
    await request(path, { method: ruleEditing ? 'PUT' : 'POST', data: { ...value, labels: {}, version: ruleEditing?.version } });
    message.success('规则已保存'); setRuleOpen(false); await load();
  };
  const saveSilence = async () => {
    const value = await silenceForm.validateFields();
    const data = { ...value, startsAt: value.startsAt.format('YYYY-MM-DDTHH:mm:ss'), endsAt: value.endsAt.format('YYYY-MM-DDTHH:mm:ss'), version: silenceEditing?.version };
    const path = silenceEditing ? `/v2/alerting/silences/${silenceEditing.id}` : '/v2/alerting/silences';
    await request(path, { method: silenceEditing ? 'PUT' : 'POST', data });
    message.success('静默已保存'); setSilenceOpen(false); await load();
  };

  const overview = <Space direction="vertical" size="large" style={{ width: '100%' }}>
    <Row gutter={[16, 16]}>
      <Col xs={12} lg={6}><Card><Statistic title="正在告警" value={health?.firingAlerts || 0} prefix={<AlertOutlined />} valueStyle={{ color: health?.firingAlerts ? '#cf1322' : undefined }} /></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title="已启用规则" value={health?.enabledRules || 0} prefix={<BellOutlined />} /></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title="待投递" value={health?.pendingDeliveries || 0} prefix={<SendOutlined />} /></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title="死信" value={health?.deadLetters || 0} prefix={<StopOutlined />} valueStyle={{ color: health?.deadLetters ? '#cf1322' : undefined }} /></Card></Col>
    </Row>
    <Card title="后台告警服务">
      <Descriptions column={{ xs: 1, md: 3 }}>
        <Descriptions.Item label="插件">{health?.pluginEnabled ? statusTag('HEALTHY') : statusTag('PAUSED')}</Descriptions.Item>
        <Descriptions.Item label="工作器">{statusTag(health?.workerStatus)}</Descriptions.Item>
        <Descriptions.Item label="最近心跳">{formatTime(health?.workerHeartbeatAt)}</Descriptions.Item>
        <Descriptions.Item label="最近评估错误" span={3}>{health?.lastEvaluationError || '无'}</Descriptions.Item>
      </Descriptions>
    </Card>
    <Card title="告警实例">
      <DataTable rowKey="id" isMobile={responsive.isMobile} loading={loading} dataSource={instances} pagination={{ pageSize: 10 }} columns={[
        { title: '规则', dataIndex: 'ruleName' }, { title: '级别', dataIndex: 'severity', render: statusTag },
        { title: '状态', dataIndex: 'status', render: statusTag }, { title: '当前值', dataIndex: 'lastValue', render: (v) => v ?? '-' },
        { title: '触发时间', dataIndex: 'firingAt', render: formatTime }, { title: '确认时间', dataIndex: 'acknowledgedAt', render: formatTime },
        { title: '操作', render: (_, row: AlertInstance) => row.status === 'FIRING' && !row.acknowledgedAt && canAck
          ? <Button size="small" icon={<CheckOutlined />} onClick={async () => { await request(`/v2/alerting/instances/${row.id}/ack?version=${row.version}`, { method: 'POST' }); message.success('告警已确认'); await load(); }}>确认</Button> : '-' },
      ]} />
    </Card>
  </Space>;

  const ruleTab = <Card extra={canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setRuleEditing(undefined); ruleForm.resetFields(); ruleForm.setFieldsValue({ enabled: false, windowSeconds: 300, pendingSeconds: 300, severity: 'WARNING', comparator: 'GT' } as RuleFormValue); setRuleOpen(true); }}>新建规则</Button>}>
    <DataTable rowKey="id" isMobile={responsive.isMobile} loading={loading} dataSource={rules} columns={[
      { title: '规则', dataIndex: 'name' }, { title: '信号', dataIndex: 'signalKey' }, { title: '条件', render: (_, r: RuleRecord) => `${r.comparator} ${r.threshold}` },
      { title: '级别', dataIndex: 'severity', render: statusTag }, { title: '联系人组', dataIndex: 'contactGroupName' },
      { title: '状态', dataIndex: 'enabled', render: (v) => v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag> },
      { title: '评估健康', render: (_, r: RuleRecord) => r.evaluationError ? <Typography.Text type="danger">{r.evaluationError}</Typography.Text> : formatTime(r.lastEvaluatedAt) },
      { title: '操作', render: (_, r: RuleRecord) => canManage && <Space>
        <Button size="small" icon={<EditOutlined />} onClick={() => { setRuleEditing(r); ruleForm.setFieldsValue({ ...r }); setRuleOpen(true); }}>编辑</Button>
        <Popconfirm title="删除这条规则？" onConfirm={async () => { await request(`/v2/alerting/rules/${r.id}`, { method: 'DELETE' }); await load(); }}><Button size="small" danger icon={<DeleteOutlined />} /></Popconfirm>
      </Space> },
    ]} />
  </Card>;

  const channelTab = <Card extra={canManageChannels && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setChannelEditing(undefined); channelForm.resetFields(); channelForm.setFieldsValue({ enabled: false, config: {} } as ChannelFormValue); setChannelOpen(true); }}>新建渠道实例</Button>}>
    <Typography.Paragraph type="secondary">每条记录都是独立配置、独立启停的渠道实例；同一平台可配置多个机器人或 SMTP。</Typography.Paragraph>
    <DataTable rowKey="id" isMobile={responsive.isMobile} loading={loading} dataSource={channels} columns={[
      { title: '名称', dataIndex: 'name' }, { title: '类型', dataIndex: 'type', render: (v: ChannelType) => CHANNEL_LABELS[v] || v },
      { title: '状态', dataIndex: 'enabled', render: (v) => v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag> },
      { title: '凭据', dataIndex: 'secretConfigured', render: (v) => v ? <Tag color="blue">已加密配置</Tag> : <Tag>未配置</Tag> },
      { title: '最近测试', render: (_, r: ChannelRecord) => <Space>{statusTag(r.lastTestStatus)}<span>{formatTime(r.lastTestAt)}</span></Space> },
      { title: '操作', render: (_, r: ChannelRecord) => canManageChannels && <Space>
        <Button size="small" icon={<EditOutlined />} onClick={() => { setChannelEditing(r); channelForm.setFieldsValue({ ...r }); setChannelOpen(true); }}>编辑</Button>
        <Button size="small" icon={<SendOutlined />} onClick={async () => { const result = await request<{ success: boolean; error?: string }>(`/v2/alerting/channels/${r.id}/test`, { method: 'POST' }); result.success ? message.success('测试消息已提交') : message.error(result.error || '测试失败'); await load(); }}>测试</Button>
        <Popconfirm title="删除该渠道实例？" onConfirm={async () => { await request(`/v2/alerting/channels/${r.id}`, { method: 'DELETE' }); await load(); }}><Button size="small" danger icon={<DeleteOutlined />} /></Popconfirm>
      </Space> },
    ]} />
  </Card>;

  const groupTab = <Card extra={canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setGroupEditing(undefined); groupForm.resetFields(); groupForm.setFieldsValue({ enabled: true, members: [{ enabled: true }] } as GroupFormValue); setGroupOpen(true); }}>新建联系人组</Button>}>
    <DataTable rowKey="id" isMobile={responsive.isMobile} dataSource={groups} columns={[
      { title: '名称', dataIndex: 'name' }, { title: '成员', render: (_, r: ContactGroup) => r.members.map((m) => m.displayName || m.targetIdentifier).join('、') },
      { title: '渠道数', render: (_, r: ContactGroup) => new Set(r.members.map((m) => m.channelId)).size },
      { title: '状态', dataIndex: 'enabled', render: (v) => v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag> },
      { title: '操作', render: (_, r: ContactGroup) => canManage && <Space><Button size="small" icon={<EditOutlined />} onClick={() => { setGroupEditing(r); groupForm.setFieldsValue({ ...r }); setGroupOpen(true); }}>编辑</Button><Popconfirm title="删除联系人组？" onConfirm={async () => { await request(`/v2/alerting/contact-groups/${r.id}`, { method: 'DELETE' }); await load(); }}><Button size="small" danger icon={<DeleteOutlined />} /></Popconfirm></Space> },
    ]} />
  </Card>;

  const silenceTab = <Card extra={canSilence && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setSilenceEditing(undefined); silenceForm.resetFields(); silenceForm.setFieldsValue({ enabled: true, startsAt: dayjs(), endsAt: dayjs().add(2, 'hour') } as SilenceFormValue); setSilenceOpen(true); }}>新建静默</Button>}>
    <DataTable rowKey="id" isMobile={responsive.isMobile} dataSource={silences} columns={[
      { title: '名称', dataIndex: 'name' }, { title: '规则', dataIndex: 'ruleName', render: (v) => v || '全部规则' },
      { title: '开始', dataIndex: 'startsAt', render: formatTime }, { title: '结束', dataIndex: 'endsAt', render: formatTime },
      { title: '原因', dataIndex: 'reason' }, { title: '状态', dataIndex: 'enabled', render: (v) => v ? <Tag color="orange">有效</Tag> : <Tag>停用</Tag> },
      { title: '操作', render: (_, r: SilenceRecord) => canSilence && <Space><Button size="small" icon={<EditOutlined />} onClick={() => { setSilenceEditing(r); silenceForm.setFieldsValue({ ...r, startsAt: dayjs(r.startsAt), endsAt: dayjs(r.endsAt) }); setSilenceOpen(true); }}>编辑</Button><Popconfirm title="删除静默？" onConfirm={async () => { await request(`/v2/alerting/silences/${r.id}`, { method: 'DELETE' }); await load(); }}><Button size="small" danger icon={<DeleteOutlined />} /></Popconfirm></Space> },
    ]} />
  </Card>;

  const deliveryTab = <Card><DataTable rowKey="id" isMobile={responsive.isMobile} dataSource={deliveries} columns={[
    { title: '事件', dataIndex: 'eventType', render: statusTag }, { title: '渠道', dataIndex: 'channelName' },
    { title: '接收目标', dataIndex: 'recipient', ellipsis: true }, { title: '状态', dataIndex: 'status', render: statusTag },
    { title: '尝试次数', dataIndex: 'attempts' }, { title: '错误', dataIndex: 'lastError', ellipsis: true, render: (v) => v || '-' },
    { title: '创建时间', dataIndex: 'createdAt', render: formatTime },
    { title: '操作', render: (_, r: DeliveryRecord) => canManage && ['FAILED', 'DEAD_LETTER'].includes(r.status)
      ? <Button size="small" onClick={async () => { await request(`/v2/alerting/deliveries/${r.id}/retry`, { method: 'POST' }); message.success('已重新入队'); await load(); }}>重试</Button> : '-' },
  ]} /></Card>;

  const appChannels = channels.filter((channel) => channel.type.endsWith('_APP'));
  const directoryTab = <Card extra={canSyncDirectory && <Button icon={<PlusOutlined />} onClick={() => { mappingForm.resetFields(); setMappingOpen(true); }}>手工映射</Button>}>
    <Space wrap style={{ marginBottom: 16 }}>{appChannels.map((channel) => <Button key={channel.id} icon={<CloudSyncOutlined />} disabled={!canSyncDirectory} onClick={async () => { const result = await request<Record<string, number>>(`/v2/alerting/directory/channels/${channel.id}/sync`, { method: 'POST' }); message.success(`同步完成：匹配 ${result.matched || 0}，歧义 ${result.ambiguous || 0}，未匹配 ${result.unmatched || 0}`); await load(); }}>同步 {channel.name}</Button>)}</Space>
    <DataTable rowKey="id" isMobile={responsive.isMobile} dataSource={mappings} columns={[
      { title: '渠道', dataIndex: 'channelId', render: (v) => channels.find((c) => c.id === v)?.name || v },
      { title: 'Lumira UID', dataIndex: 'userUuid' }, { title: '平台用户 ID', dataIndex: 'providerUserId', render: (v) => v || '-' },
      { title: '平台名称', dataIndex: 'providerDisplayName', render: (v) => v || '-' }, { title: '匹配依据', dataIndex: 'matchSource' },
      { title: '状态', dataIndex: 'status', render: statusTag }, { title: '手工覆盖', dataIndex: 'manualOverride', render: (v) => v ? '是' : '否' },
      { title: '同步时间', dataIndex: 'syncedAt', render: formatTime },
    ]} />
  </Card>;

  return <PageContainer title="告警中心" subTitle="规则评估、告警生命周期与多渠道可靠投递" extra={<Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>刷新</Button>}>
    <Tabs items={[
      { key: 'overview', label: '概览与告警', children: overview }, { key: 'rules', label: '触发规则', children: ruleTab },
      { key: 'channels', label: '告警渠道', children: channelTab }, { key: 'groups', label: '联系人组', children: groupTab },
      { key: 'silences', label: '静默与维护', children: silenceTab }, { key: 'deliveries', label: '投递日志', children: deliveryTab },
      { key: 'directory', label: '企业目录映射', children: directoryTab },
    ]} />

    <Modal open={channelOpen} title={channelEditing ? '编辑渠道实例' : '新建渠道实例'} width={640} onCancel={() => setChannelOpen(false)} onOk={() => void saveChannel()} destroyOnHidden>
      <Form form={channelForm} layout="vertical">
        <Form.Item name="name" label="实例名称" rules={[{ required: true }]}><Input placeholder="例如：运维群-企业微信" /></Form.Item>
        <Form.Item name="type" label="渠道类型" rules={[{ required: true }]}><Select options={CHANNEL_OPTIONS} disabled={Boolean(channelEditing)} /></Form.Item>
        <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
        <ChannelConfigFields type={selectedChannelType} />
      </Form>
    </Modal>

    <Modal open={groupOpen} title={groupEditing ? '编辑联系人组' : '新建联系人组'} width={780} onCancel={() => setGroupOpen(false)} onOk={() => void saveGroup()} destroyOnHidden>
      <Form form={groupForm} layout="vertical">
        <Form.Item name="name" label="组名称" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
        <Form.List name="members">{(fields, { add, remove }) => <Space direction="vertical" style={{ width: '100%' }}>
          {fields.map((field) => <Card key={field.key} size="small" extra={<Button danger type="text" icon={<DeleteOutlined />} onClick={() => remove(field.name)} />}>
            <Row gutter={12}>
              <Col span={8}><Form.Item name={[field.name, 'channelId']} label="渠道实例" rules={[{ required: true }]}><Select options={channels.map((c) => ({ value: c.id, label: c.name }))} /></Form.Item></Col>
              <Col span={6}><Form.Item name={[field.name, 'memberType']} label="成员类型" rules={[{ required: true }]}><Select options={['USER', 'EMAIL', 'EXTERNAL_USER', 'CHAT', 'WEBHOOK'].map((value) => ({ value }))} /></Form.Item></Col>
              <Col span={10}><Form.Item name={[field.name, 'targetIdentifier']} label="目标标识" rules={[{ required: true }]}><Input placeholder="用户用 user:<UID>；Webhook 用 webhook" /></Form.Item></Col>
              <Col span={18}><Form.Item name={[field.name, 'displayName']} label="显示名称"><Input /></Form.Item></Col>
              <Col span={6}><Form.Item name={[field.name, 'enabled']} label="启用" valuePropName="checked"><Switch /></Form.Item></Col>
            </Row>
          </Card>)}
          <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ enabled: true })}>添加成员</Button>
        </Space>}</Form.List>
      </Form>
    </Modal>

    <Modal open={ruleOpen} title={ruleEditing ? '编辑告警规则' : '新建告警规则'} width={680} onCancel={() => setRuleOpen(false)} onOk={() => void saveRule()} destroyOnHidden>
      <Form form={ruleForm} layout="vertical">
        <Form.Item name="name" label="规则名称" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="signalKey" label="受控信号" rules={[{ required: true }]}><Select showSearch options={catalog.map((item) => ({ value: item.key, label: `${item.name}（${item.unit}）` }))} /></Form.Item>
        <Form.Item name="sourceType" hidden><Input /></Form.Item>
        {signal && <Typography.Paragraph type="secondary">{signal.description}</Typography.Paragraph>}
        <Row gutter={12}>
          <Col span={8}><Form.Item name="comparator" label="比较符" rules={[{ required: true }]}><Select options={(signal?.comparators || ['GT', 'GTE', 'LT', 'LTE', 'EQ', 'NE']).map((value) => ({ value }))} /></Form.Item></Col>
          <Col span={8}><Form.Item name="threshold" label={`阈值${signal ? `（${signal.unit}）` : ''}`} rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item name="severity" label="级别" rules={[{ required: true }]}><Select options={['INFO', 'WARNING', 'CRITICAL'].map((value) => ({ value }))} /></Form.Item></Col>
          <Col span={8}><Form.Item name="windowSeconds" label="统计窗口（秒）" rules={[{ required: true }]}><InputNumber min={1} max={86400} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item name="pendingSeconds" label="持续时间（秒）" rules={[{ required: true }]}><InputNumber min={0} max={86400} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item name="contactGroupId" label="联系人组" rules={[{ required: true }]}><Select options={groups.filter((g) => g.enabled).map((g) => ({ value: g.id, label: g.name }))} /></Form.Item></Col>
        </Row>
        <Form.Item name="enabled" label="启用规则" valuePropName="checked"><Switch /></Form.Item>
      </Form>
    </Modal>

    <Modal open={silenceOpen} title={silenceEditing ? '编辑静默' : '新建静默'} onCancel={() => setSilenceOpen(false)} onOk={() => void saveSilence()} destroyOnHidden>
      <Form form={silenceForm} layout="vertical">
        <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="ruleId" label="规则（留空为全部）"><Select allowClear options={rules.map((r) => ({ value: r.id, label: r.name }))} /></Form.Item>
        <Form.Item name="startsAt" label="开始时间" rules={[{ required: true }]}><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
        <Form.Item name="endsAt" label="结束时间" rules={[{ required: true }]}><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
        <Form.Item name="reason" label="原因" rules={[{ required: true }]}><Input.TextArea rows={3} /></Form.Item>
        <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
      </Form>
    </Modal>

    <Modal open={mappingOpen} title="手工覆盖企业目录映射" onCancel={() => setMappingOpen(false)} onOk={async () => { const value = await mappingForm.validateFields(); await request('/v2/alerting/directory/mappings', { method: 'POST', data: value }); message.success('手工映射已保存'); setMappingOpen(false); await load(); }} destroyOnHidden>
      <Form form={mappingForm} layout="vertical">
        <Form.Item name="channelId" label="企业应用渠道" rules={[{ required: true }]}><Select options={appChannels.map((c) => ({ value: c.id, label: c.name }))} /></Form.Item>
        <Form.Item name="userId" label="Lumira 用户数字 ID" rules={[{ required: true }]}><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
        <Form.Item name="userUuid" label="Lumira UID" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="providerUserId" label="平台用户 ID" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="providerDisplayName" label="平台显示名称"><Input /></Form.Item>
      </Form>
    </Modal>
  </PageContainer>;
};

export default AlertingPage;
