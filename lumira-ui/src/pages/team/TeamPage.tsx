import { useEffect, useMemo, useState } from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import { history, useLocation, useParams } from '@umijs/max';
import {
  Avatar,
  Button,
  Card,
  Descriptions,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  QRCode,
  Result,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
} from 'antd';
import type { FormInstance, SelectProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CopyOutlined,
  DeleteOutlined,
  LinkOutlined,
  PlusOutlined,
  SearchOutlined,
  TeamOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useDictOptions } from '@/hooks/useDictOptions';
import { useResponsive } from '@/hooks/useResponsive';
import { message } from '@/theme/antdFeedbackBridge';
import { request } from '@/services/common/request';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import type { ProfileFieldSetting } from '@/types/api';
import {
  adminDeleteTeam,
  adminUpdateTeam,
  approveTeamJoinRequest,
  createTeam,
  createTeamInvite,
  createTeamMember,
  deleteTeam,
  disableTeamInvite,
  getTeam,
  joinTeamByCode,
  joinTeamByToken,
  listAllTeams,
  leaveTeam,
  listTeamInvites,
  listTeamJoinRequests,
  listTeamMembers,
  previewTeamInvite,
  rejectTeamJoinRequest,
  removeTeamMember,
  transferTeamOwner,
  updateTeam,
  updateTeamMemberRole,
} from '@/services/team/api';
import type {
  TeamDraftMemberPayload,
  TeamInviteRecord,
  TeamJoinRequestRecord,
  TeamMemberRecord,
  TeamRecord,
  TeamRole,
  TeamUpsertPayload,
} from '@/services/team/types';
import { normalizeTeamCreatePayload, pruneBlankDraftMembers } from './teamPayload';
import './team.css';

const roleOptions: Exclude<TeamRole, 'OWNER'>[] = ['ADMIN', 'MANAGER', 'MEMBER'];
type TeamDictOption = NonNullable<SelectProps['options']>[number];
type TeamEditablePayload = Pick<TeamUpsertPayload, 'teamName' | 'avatarUrl' | 'description'>;

const TEAM_TYPE_DICT_CODE = 'team_type';
const TEAM_VISIBILITY_DICT_CODE = 'team_visibility';
const TEAM_JOIN_MODE_DICT_CODE = 'team_join_mode';

const fallbackTeamTypeOptions: TeamDictOption[] = [
  { value: 'GENERAL', label: '通用团队' },
  { value: 'DEV', label: '开发团队' },
  { value: 'COMPETITION', label: '竞赛团队' },
  { value: 'CLUB', label: '社团组织' },
  { value: 'OTHER', label: '其他' },
];
const fallbackVisibilityOptions: TeamDictOption[] = [
  { value: 'PRIVATE', label: '私有' },
  { value: 'PUBLIC', label: '公开' },
];
const fallbackJoinModeOptions: TeamDictOption[] = [
  { value: 'INVITE_ONLY', label: '仅邀请' },
  { value: 'APPLY', label: '申请加入' },
  { value: 'OPEN', label: '开放加入' },
];

const roleColor: Record<string, string> = {
  OWNER: 'gold',
  ADMIN: 'blue',
  MANAGER: 'cyan',
  MEMBER: 'default',
};

const roleLabel: Record<string, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  MANAGER: '协作者',
  MEMBER: '成员',
};

const statusLabel: Record<string, string> = {
  ACTIVE: '正常',
  REMOVED: '已移除',
  DELETED: '已删除',
};

const useTeamDictOptions = () => {
  const { options: teamTypeOptions } = useDictOptions(TEAM_TYPE_DICT_CODE, fallbackTeamTypeOptions);
  const { options: visibilityOptions } = useDictOptions(TEAM_VISIBILITY_DICT_CODE, fallbackVisibilityOptions);
  const { options: joinModeOptions } = useDictOptions(TEAM_JOIN_MODE_DICT_CODE, fallbackJoinModeOptions);

  return { teamTypeOptions, visibilityOptions, joinModeOptions };
};

const useTeamMemberFieldSettings = () => {
  const [fields, setFields] = useState<ProfileFieldSetting[]>([]);

  useEffect(() => {
    void request<ProfileFieldSetting[]>('/v1/system/profile-field-settings?pageKey=TEAM_MEMBER', {
      method: 'GET',
      ...API_OPTS.NO_REDIRECT,
    }).then((records) => setFields((records || []).filter((item) => item.visible && item.custom)));
  }, []);

  return fields;
};

const renderTeamMemberExtraFieldInput = (field: ProfileFieldSetting) => {
  const placeholder = field.placeholder || field.fieldLabel || undefined;
  switch ((field.fieldType || 'TEXT').toUpperCase()) {
    case 'NUMBER':
      return <InputNumber min={0} style={{ width: '100%' }} placeholder={placeholder} />;
    case 'TEXTAREA':
      return <Input.TextArea rows={2} placeholder={placeholder} />;
    default:
      return <Input placeholder={placeholder} />;
  }
};

const optionLabel = (options: TeamDictOption[], value?: string | null) => {
  const option = options.find((item) => item.value === value);
  return String(option?.label ?? value ?? '-');
};

const fullInviteUrl = (invite?: TeamInviteRecord) => {
  if (!invite?.inviteUrl) {
    return '';
  }
  if (invite.inviteUrl.startsWith('http')) {
    return invite.inviteUrl;
  }
  return `${window.location.origin}${invite.inviteUrl}`;
};

const teamToFormValues = (team: TeamRecord): TeamEditablePayload => ({
  teamName: team.teamName,
  avatarUrl: team.avatarUrl || undefined,
  description: team.description || undefined,
});

const uploadTeamAvatarImage = async (file: File) => {
  if (!file.type.startsWith('image/')) {
    message.error('请上传图片文件');
    return undefined;
  }
  if (file.size > 20 * 1024 * 1024) {
    message.error('图片过大，请上传不超过 20MB 的文件');
    return undefined;
  }

  const formData = new FormData();
  formData.append('file', file);
  const uploadedUrl = await request<string>('/v1/system/uploads/image', {
    method: 'POST',
    headers: {},
    data: formData,
    ...API_OPTS.NO_REDIRECT,
  });
  return normalizeUploadUrl(uploadedUrl);
};

const TeamShell = ({ title, actions, children }: { title: string; actions?: React.ReactNode; children: React.ReactNode }) => (
  <ManagementPage title={title} extra={actions ? <Space wrap>{actions}</Space> : undefined}>
    <ManagementPageBody className="team-page">{children}</ManagementPageBody>
  </ManagementPage>
);

const TeamForm = ({
  form,
  initialValues,
  onFinish,
  teamTypeOptions = fallbackTeamTypeOptions,
  visibilityOptions = fallbackVisibilityOptions,
  joinModeOptions = fallbackJoinModeOptions,
}: {
  form?: FormInstance<TeamUpsertPayload>;
  initialValues?: Partial<TeamRecord>;
  onFinish: (values: TeamUpsertPayload) => Promise<void>;
  teamTypeOptions?: TeamDictOption[];
  visibilityOptions?: TeamDictOption[];
  joinModeOptions?: TeamDictOption[];
}) => {
  const [internalForm] = Form.useForm<TeamUpsertPayload>();
  const formInstance = form ?? internalForm;

  return (
    <Form
      form={formInstance}
      layout="vertical"
      className="team-form"
      initialValues={{
        teamType: 'GENERAL',
        visibility: 'PRIVATE',
        joinMode: 'INVITE_ONLY',
        ...initialValues,
      }}
      onFinish={onFinish}
    >
      <Form.Item name="teamName" label="团队名称" rules={[{ required: true, message: '请输入团队名称' }]}>
        <Input maxLength={128} />
      </Form.Item>
      <Form.Item name="teamType" label="团队类型">
        <Select options={teamTypeOptions} />
      </Form.Item>
      <Form.Item name="visibility" label="可见性">
        <Select options={visibilityOptions} />
      </Form.Item>
      <Form.Item name="joinMode" label="加入方式">
        <Select options={joinModeOptions} />
      </Form.Item>
      <Form.Item name="avatarUrl" label="头像 URL">
        <Input maxLength={512} />
      </Form.Item>
      <Form.Item name="description" label="简介">
        <Input.TextArea rows={4} maxLength={1000} />
      </Form.Item>
      {form ? null : (
        <Button type="primary" htmlType="submit">
          保存
        </Button>
      )}
    </Form>
  );
};

const TeamMemberTable = ({
  members,
  loading,
  actions,
}: {
  members: TeamMemberRecord[];
  loading?: boolean;
  actions?: (record: TeamMemberRecord) => React.ReactNode;
}) => {
  const columns: ColumnsType<TeamMemberRecord> = [
    {
      title: '成员姓名',
      dataIndex: 'memberName',
      render: (_, record) => record.memberName || record.memberAlias || (record.userId ? `用户 ${record.userId}` : '-'),
    },
    { title: '工号', dataIndex: 'employeeNo', render: (value) => value || '-' },
    { title: '所属部门', dataIndex: 'departmentName', render: (value) => value || '-' },
    { title: '角色', dataIndex: 'role', render: (role) => <Tag color={roleColor[role]}>{roleLabel[role] || role}</Tag> },
    { title: '来源', dataIndex: 'memberSource', render: (value) => (value === 'DRAFT' ? '表单填写' : '平台注册') },
    { title: '状态', dataIndex: 'status', render: (value) => statusLabel[value] || value || '-' },
    { title: '备注', dataIndex: 'remark', render: (value) => value || '-' },
  ];
  const actionColumn: ColumnsType<TeamMemberRecord>[number] | undefined = actions
    ? {
        title: '操作',
        width: 220,
        fixed: 'right',
        render: (_, record) => actions(record),
      }
    : undefined;

  return (
    <Table
      rowKey="id"
      columns={actionColumn ? [...columns, actionColumn] : columns}
      dataSource={members}
      loading={loading}
      pagination={false}
      scroll={actionColumn ? { x: 1080 } : undefined}
    />
  );
};

const TeamListPage = () => {
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const { teamTypeOptions, visibilityOptions, joinModeOptions } = useTeamDictOptions();
  const [teamForm] = Form.useForm<TeamUpsertPayload>();
  const [teams, setTeams] = useState<TeamRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingTeam, setEditingTeam] = useState<TeamRecord>();

  const load = async () => {
    setLoading(true);
    try {
      setTeams(await listAllTeams());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const openEditDrawer = (record: TeamRecord) => {
    setEditingTeam(record);
    teamForm.resetFields();
    teamForm.setFieldsValue({
      ...record,
      avatarUrl: record.avatarUrl || '',
      description: record.description || '',
    });
    setDrawerOpen(true);
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingTeam(undefined);
  };

  const saveTeam = async () => {
    if (!editingTeam) {
      return;
    }
    const values = await teamForm.validateFields();
    setSaving(true);
    try {
      await adminUpdateTeam(editingTeam.id, values);
      message.success('团队已更新');
      closeDrawer();
      await load();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<TeamRecord>[] = [
    {
      title: '团队',
      dataIndex: 'teamName',
      width: 360,
      minWidth: 320,
      className: 'team-list-name-column',
      render: (_, record) => (
        <Space className="team-list-name-cell">
          <Avatar size={32} src={normalizeUploadUrl(record.avatarUrl) || undefined} icon={<TeamOutlined />} />
          <div className="team-list-name-cell__text">
            <Typography.Text className="team-list-name-cell__title" strong ellipsis={{ tooltip: record.teamName }}>
              {record.teamName}
            </Typography.Text>
            <Typography.Text className="team-list-name-cell__code" type="secondary" ellipsis={{ tooltip: record.teamCode }}>
              {record.teamCode}
            </Typography.Text>
          </div>
        </Space>
      ),
    },
    { title: '类型', dataIndex: 'teamType', width: 140, render: (value) => <Tag>{optionLabel(teamTypeOptions, String(value))}</Tag> },
    { title: '可见性', dataIndex: 'visibility', width: 120, render: (value) => optionLabel(visibilityOptions, String(value)) },
    { title: '加入方式', dataIndex: 'joinMode', width: 140, render: (value) => optionLabel(joinModeOptions, String(value)) },
    { title: '成员数', dataIndex: 'memberCount', width: 100 },
    { title: '状态', dataIndex: 'status', width: 110, render: (value) => <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>{statusLabel[String(value)] || String(value)}</Tag> },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value) => value || '-' },
    {
      title: '操作',
      fixed: 'right',
      valueType: 'option',
      width: responsive.isMobile ? 120 : 184,
      align: 'right',
      className: 'saas-table-action-column',
      render: (_, record) => (
        <TableActionBar
          isMobile={responsive.isMobile}
          inlineCount={2}
          items={actionPermission.buildTableActions([
            { key: 'detail', label: '详情', permission: 'team:view', onClick: () => history.push(`/team/${record.id}`) },
            { key: 'members', label: '成员', permission: 'team:member:view', onClick: () => history.push(`/team/${record.id}/members`) },
            { key: 'invites', label: '邀请', permission: 'team:member:invite', onClick: () => history.push(`/team/${record.id}/invites`) },
            {
              key: 'edit',
              label: '编辑',
              permission: 'team:update',
              onClick: () => openEditDrawer(record),
            },
            {
              key: 'delete',
              label: '删除',
              icon: <DeleteOutlined />,
              permission: 'team:delete',
              danger: true,
              onClick: () => {
                Modal.confirm({
                  title: '确认删除该团队？',
                  content: '删除后会同步移除成员、邀请和加入申请。',
                  okButtonProps: { danger: true },
                  onOk: async () => {
                    await adminDeleteTeam(record.id);
                    message.success('团队已删除');
                    await load();
                  },
                });
              },
            },
          ])}
        />
      ),
    },
  ];

  return (
    <ManagementPage title="团队管理">
      <ManagementPageBody>
        <ManagementTable<TeamRecord>
          rowKey="id"
          columns={columns}
          dataSource={teams}
          loading={loading}
          isMobile={responsive.isMobile}
          autoContentWidth
          search={false}
          scroll={{ x: 'max-content' }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          locale={{ emptyText: <Empty description="暂无团队" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
          onRefresh={() => void load()}
          toolBarRender={() =>
            actionPermission.buildToolbarActions([
              {
                value: (
                  <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => history.push('/team/create')}>
                    新增团队
                  </Button>
                ),
                permission: 'team:create',
              },
            ])
          }
        />
      </ManagementPageBody>
      <ManagementDrawer
        title="编辑团队"
        open={drawerOpen}
        onClose={closeDrawer}
        destroyOnHidden
        footerActions={[
          { key: 'cancel', label: '取消', onClick: closeDrawer },
          { key: 'save', label: '保存', type: 'primary', loading: saving, onClick: () => void saveTeam() },
        ]}
      >
        <TeamForm
          form={teamForm}
          initialValues={editingTeam}
          teamTypeOptions={teamTypeOptions}
          visibilityOptions={visibilityOptions}
          joinModeOptions={joinModeOptions}
          onFinish={async () => saveTeam()}
        />
      </ManagementDrawer>
    </ManagementPage>
  );
};

const TeamSearchPage = () => {
  const { teamTypeOptions, visibilityOptions, joinModeOptions } = useTeamDictOptions();
  const [teams, setTeams] = useState<TeamRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [activeType, setActiveType] = useState('ALL');
  const [visibility, setVisibility] = useState('ALL');
  const [joinMode, setJoinMode] = useState('ALL');
  const [status, setStatus] = useState('ALL');

  const load = async () => {
    setLoading(true);
    try {
      setTeams(await listAllTeams());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const typeTabs = useMemo(
    () => [
      { key: 'ALL', label: '全部' },
      ...teamTypeOptions.slice(0, 6).map((item) => ({
        key: String(item.value),
        label: String(item.label),
      })),
    ],
    [teamTypeOptions],
  );

  const filteredTeams = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return teams.filter((team) => {
      const matchesKeyword =
        !normalizedKeyword ||
        [team.teamName, team.teamCode, team.description]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(normalizedKeyword));
      const matchesType = activeType === 'ALL' || team.teamType === activeType;
      const matchesVisibility = visibility === 'ALL' || team.visibility === visibility;
      const matchesJoinMode = joinMode === 'ALL' || team.joinMode === joinMode;
      const matchesStatus = status === 'ALL' || team.status === status;
      return matchesKeyword && matchesType && matchesVisibility && matchesJoinMode && matchesStatus;
    });
  }, [activeType, joinMode, keyword, status, teams, visibility]);

  return (
    <ManagementPage title="团队">
      <ManagementPageBody className="team-search-page">
        <div className="team-search-hero">
          <Input.Search
            className="team-search-hero__input"
            size="large"
            allowClear
            enterButton="搜索"
            prefix={<SearchOutlined />}
            placeholder="请输入团队名称、编码或简介"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={setKeyword}
          />
        </div>

        <Card className="team-search-filter-card">
          <div className="team-search-filter-row">
            <Typography.Text strong>所属类别:</Typography.Text>
            <Space wrap size={[24, 8]}>
              {typeTabs.map((item) => (
                <Button key={item.key} type={activeType === item.key ? 'link' : 'text'} onClick={() => setActiveType(item.key)}>
                  {item.label}
                </Button>
              ))}
            </Space>
          </div>
          <Divider />
          <div className="team-search-filter-row team-search-filter-row--split">
            <Space wrap size={[12, 8]}>
              <Typography.Text strong>其它选项:</Typography.Text>
              <Typography.Text>可见性:</Typography.Text>
              <Select
                value={visibility}
                className="team-search-filter-row__small"
                options={[{ value: 'ALL', label: '不限' }, ...visibilityOptions]}
                onChange={setVisibility}
              />
            </Space>
            <Space wrap size={[12, 8]}>
              <Typography.Text>加入方式:</Typography.Text>
              <Select
                value={joinMode}
                className="team-search-filter-row__small"
                options={[{ value: 'ALL', label: '不限' }, ...joinModeOptions]}
                onChange={setJoinMode}
              />
            </Space>
            <Space wrap size={[12, 8]}>
              <Typography.Text>状态:</Typography.Text>
              <Select
                value={status}
                className="team-search-filter-row__small"
                options={[
                  { value: 'ALL', label: '不限' },
                  { value: 'ACTIVE', label: '正常' },
                  { value: 'REMOVED', label: '已移除' },
                  { value: 'DELETED', label: '已删除' },
                ]}
                onChange={setStatus}
              />
            </Space>
          </div>
        </Card>

        <Card className="team-search-results" loading={loading}>
          {filteredTeams.length ? (
            filteredTeams.map((team, index) => (
              <div key={team.id}>
                <article className="team-search-result">
                  <Avatar size={46} src={normalizeUploadUrl(team.avatarUrl) || undefined} icon={<TeamOutlined />} />
                  <div className="team-search-result__body">
                    <Typography.Title level={4}>{team.teamName}</Typography.Title>
                    <Space wrap size={[8, 8]}>
                      <Tag>{optionLabel(teamTypeOptions, team.teamType)}</Tag>
                      <Tag>{optionLabel(visibilityOptions, team.visibility)}</Tag>
                      <Tag>{optionLabel(joinModeOptions, team.joinMode)}</Tag>
                      <Tag color={team.status === 'ACTIVE' ? 'green' : 'default'}>{statusLabel[team.status] || team.status}</Tag>
                    </Space>
                    <Typography.Paragraph ellipsis={{ rows: 2 }} className="team-search-result__description">
                      {team.description || '暂无团队简介，团队管理员可以在团队管理中补充说明。'}
                    </Typography.Paragraph>
                    <Space wrap className="team-search-result__meta">
                      <Typography.Link onClick={() => history.push(`/team/${team.id}`)}>{team.teamCode}</Typography.Link>
                      <Typography.Text type="secondary">{team.updatedAt || team.createdAt || '-'}</Typography.Text>
                    </Space>
                  </div>
                </article>
                {index < filteredTeams.length - 1 ? <Divider /> : null}
              </div>
            ))
          ) : (
            <Empty description="暂无匹配团队" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

const CreateTeamPage = () => {
  const actionPermission = useActionPermission();
  const { teamTypeOptions, visibilityOptions, joinModeOptions } = useTeamDictOptions();
  const customTeamMemberFields = useTeamMemberFieldSettings();
  const [form] = Form.useForm<TeamUpsertPayload>();
  const avatarUrlValue = Form.useWatch('avatarUrl', form);
  const [saving, setSaving] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);

  if (!actionPermission.can('team:create')) {
    return (
      <TeamShell title="创建团队" actions={<Button onClick={() => history.push('/team/management')}>返回</Button>}>
        <Result status="403" title="403" />
      </TeamShell>
    );
  }

  const submit = async () => {
    form.setFieldValue('initialMembers', pruneBlankDraftMembers(form.getFieldValue('initialMembers')));
    const values = await form.validateFields();
    const payload = normalizeTeamCreatePayload(values);

    setSaving(true);
    try {
      const team = await createTeam(payload);
      message.success('团队已创建');
      history.push(`/team/${team.id}`);
    } finally {
      setSaving(false);
    }
  };

  const uploadCreateTeamAvatar = async (file: File) => {
    setAvatarUploading(true);
    try {
      const uploadedUrl = await uploadTeamAvatarImage(file);
      if (uploadedUrl) {
        form.setFieldValue('avatarUrl', uploadedUrl);
        message.success('头像已上传');
      }
    } catch (error) {
      showErrorMessage(error, '头像上传失败，请稍后重试');
    } finally {
      setAvatarUploading(false);
    }
  };

  return (
    <TeamShell title="创建团队">
      <div className="team-advanced-page">
        <div className="team-advanced-page__heading">
          <Typography.Text type="secondary">团队 / 创建团队</Typography.Text>
        </div>
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          teamType: 'GENERAL',
          visibility: 'PRIVATE',
          joinMode: 'INVITE_ONLY',
          initialMembers: [
            { memberName: '', employeeNo: '', departmentName: '', role: 'MEMBER', remark: '', extraValues: {} },
          ],
        }}
      >
        <Card className="team-section-card" title="团队管理">
          <div className="team-form-grid">
            <Form.Item name="teamName" label="团队名称" rules={[{ required: true, message: '请输入团队名称' }]}>
              <Input placeholder="请输入团队名称" maxLength={128} />
            </Form.Item>
            <Form.Item name="teamType" label="团队类型">
              <Select placeholder="请选择团队类型" options={teamTypeOptions} />
            </Form.Item>
            <Form.Item name="avatarUrl" hidden>
              <Input />
            </Form.Item>
            <Form.Item label="团队头像">
              <div className="team-avatar-field">
                <Avatar size={48} src={normalizeUploadUrl(avatarUrlValue) || undefined} icon={<TeamOutlined />} />
                <Upload
                  accept="image/*"
                  showUploadList={false}
                  disabled={avatarUploading}
                  beforeUpload={async (file) => {
                    await uploadCreateTeamAvatar(file);
                    return Upload.LIST_IGNORE;
                  }}
                >
                  <Button icon={<UploadOutlined />} loading={avatarUploading}>
                    上传图片
                  </Button>
                </Upload>
                {avatarUrlValue ? (
                  <Button type="link" onClick={() => form.setFieldValue('avatarUrl', undefined)}>
                    移除
                  </Button>
                ) : null}
              </div>
            </Form.Item>
            <Form.Item name="description" label="团队简介" className="team-form-grid__wide">
              <Input.TextArea placeholder="请输入团队简介" rows={3} maxLength={1000} />
            </Form.Item>
          </div>
        </Card>

        <Card className="team-section-card" title="加入设置">
          <div className="team-form-grid">
            <Form.Item name="visibility" label="可见性">
              <Select placeholder="请选择可见性" options={visibilityOptions} />
            </Form.Item>
            <Form.Item name="joinMode" label="加入方式">
              <Select placeholder="请选择加入方式" options={joinModeOptions} />
            </Form.Item>
          </div>
        </Card>

        <Card className="team-section-card" title="成员管理">
          <Form.List name="initialMembers">
            {(fields, { add, remove }) => (
              <div className="team-member-form-table">
                <Table
                  rowKey="key"
                  pagination={false}
                  dataSource={fields}
                  columns={[
                    {
                      title: '成员姓名',
                      render: (_, field) => (
                        <Form.Item
                          name={[field.name, 'memberName']}
                          rules={[{ required: true, message: '请输入成员姓名' }]}
                          className="team-table-form-item"
                        >
                          <Input placeholder="请输入" maxLength={128} />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '工号',
                      render: (_, field) => (
                        <Form.Item name={[field.name, 'employeeNo']} className="team-table-form-item">
                          <Input placeholder="请输入" maxLength={64} />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '所属部门',
                      render: (_, field) => (
                        <Form.Item name={[field.name, 'departmentName']} className="team-table-form-item">
                          <Input placeholder="请输入" maxLength={128} />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '角色',
                      width: 150,
                      render: (_, field) => (
                        <Form.Item name={[field.name, 'role']} className="team-table-form-item">
                          <Select options={roleOptions.map((role) => ({ value: role, label: roleLabel[role] }))} />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '备注',
                      render: (_, field) => (
                        <Form.Item name={[field.name, 'remark']} className="team-table-form-item">
                          <Input placeholder="请输入" maxLength={512} />
                        </Form.Item>
                      ),
                    },
                    ...customTeamMemberFields.map((customField) => ({
                      title: customField.fieldLabel,
                      render: (_: unknown, field: { name: number }) => (
                        <Form.Item
                          name={[field.name, 'extraValues', customField.fieldKey]}
                          className="team-table-form-item"
                          rules={[{ required: Boolean(customField.required), message: `Please enter ${customField.fieldLabel}` }]}
                        >
                          {renderTeamMemberExtraFieldInput(customField)}
                        </Form.Item>
                      ),
                    })),
                    {
                      title: '操作',
                      width: 96,
                      render: (_, field) => (
                        <Button danger type="link" disabled={fields.length <= 1} onClick={() => remove(field.name)}>
                          删除
                        </Button>
                      ),
                    },
                  ]}
                />
                <Button
                  block
                  className="team-member-add-row"
                  icon={<PlusOutlined />}
                  onClick={() => add({ memberName: '', employeeNo: '', departmentName: '', role: 'MEMBER', remark: '', extraValues: {} })}
                >
                  添加一行数据
                </Button>
              </div>
            )}
          </Form.List>
        </Card>
      </Form>
      <div className="team-advanced-footer">
        <Button onClick={() => form.resetFields()}>重置</Button>
        <Button type="primary" loading={saving} onClick={() => void submit()}>
          提交
        </Button>
      </div>
      </div>
    </TeamShell>
  );
};

const useTeamId = () => {
  const params = useParams<{ teamId?: string }>();
  return Number(params.teamId || 0);
};

const TeamDetailPage = () => {
  const teamId = useTeamId();
  const actionPermission = useActionPermission();
  const { teamTypeOptions, visibilityOptions, joinModeOptions } = useTeamDictOptions();
  const [detailForm] = Form.useForm<TeamEditablePayload>();
  const avatarUrlValue = Form.useWatch('avatarUrl', detailForm);
  const [team, setTeam] = useState<TeamRecord>();
  const [members, setMembers] = useState<TeamMemberRecord[]>([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);

  const load = async () => {
    const nextTeam = await getTeam(teamId);
    setTeam(nextTeam);
    detailForm.setFieldsValue(teamToFormValues(nextTeam));
    if (actionPermission.can('team:member:view')) {
      setMembersLoading(true);
      try {
        setMembers(await listTeamMembers(teamId));
      } finally {
        setMembersLoading(false);
      }
    }
  };

  useEffect(() => {
    void load();
  }, [teamId]);

  const cancelEdit = () => {
    if (team) {
      detailForm.setFieldsValue(teamToFormValues(team));
    }
    setEditing(false);
  };

  const saveEdit = async () => {
    const values = await detailForm.validateFields();
    setSaving(true);
    try {
      const updated = await updateTeam(teamId, values);
      setTeam(updated);
      detailForm.setFieldsValue(teamToFormValues(updated));
      setEditing(false);
      message.success('团队已更新');
    } finally {
      setSaving(false);
    }
  };

  const uploadTeamAvatar = async (file: File) => {
    if (!editing) {
      return;
    }
    setAvatarUploading(true);
    try {
      const uploadedUrl = await uploadTeamAvatarImage(file);
      if (uploadedUrl) {
        detailForm.setFieldValue('avatarUrl', uploadedUrl);
        message.success('头像已上传');
      }
    } catch (error) {
      showErrorMessage(error, '头像上传失败，请稍后重试');
    } finally {
      setAvatarUploading(false);
    }
  };

  if (!team) {
    return <TeamShell title="团队详情"><Card loading /></TeamShell>;
  }

  const computedMemberCount = members.length || team.memberCount || 0;

  return (
    <TeamShell
      title={team.teamName}
      actions={
        <>
          <Button onClick={() => history.push('/team/management')}>团队管理</Button>
        </>
      }
    >
      <Card className="team-detail-card">
        <Form form={detailForm} layout="vertical" className="team-detail-form">
          <section className="team-detail-section">
            <div className="team-detail-section__header">
              <Typography.Title level={4}>团队信息</Typography.Title>
              <Space wrap>
                {editing ? (
                  <>
                    <Button onClick={cancelEdit} disabled={saving}>取消</Button>
                    <Button type="primary" loading={saving} onClick={() => void saveEdit()}>保存</Button>
                  </>
                ) : (
                  <>
                    {actionPermission.can('team:update') ? (
                      <Button type="primary" onClick={() => setEditing(true)}>编辑</Button>
                    ) : null}
                    {team.myRole === 'OWNER' && actionPermission.can('team:delete') ? (
                      <Popconfirm title="确认解散团队？" onConfirm={async () => { await deleteTeam(teamId); message.success('团队已删除'); history.push('/team/management'); }}>
                        <Button danger icon={<DeleteOutlined />}>解散</Button>
                      </Popconfirm>
                    ) : null}
                    {team.myRole !== 'OWNER' ? (
                      <Popconfirm title="确认退出团队？" onConfirm={async () => { await leaveTeam(teamId); message.success('已退出团队'); history.push('/team/management'); }}>
                        <Button danger>退出</Button>
                      </Popconfirm>
                    ) : null}
                  </>
                )}
              </Space>
            </div>
            <div className="team-detail-form-grid">
              <Form.Item name="teamName" label="团队名称" rules={[{ required: true, message: '请输入团队名称' }]}>
                <Input readOnly={!editing} maxLength={128} />
              </Form.Item>
              <Form.Item label="团队编码">
                <Input value={team.teamCode} readOnly />
              </Form.Item>
              <Form.Item label="团队类型">
                <Input value={optionLabel(teamTypeOptions, team.teamType)} readOnly />
              </Form.Item>
              <Form.Item label="成员数">
                <Input value={String(computedMemberCount)} readOnly />
              </Form.Item>
              <Form.Item label="我的角色">
                <Input value={roleLabel[team.myRole || ''] || team.myRole || '-'} readOnly />
              </Form.Item>
              <Form.Item label="状态">
                <Input value={statusLabel[team.status] || team.status} readOnly />
              </Form.Item>
              <Form.Item name="avatarUrl" hidden>
                <Input />
              </Form.Item>
              <Form.Item label="团队头像">
                <div className="team-avatar-field">
                  <Avatar size={64} src={normalizeUploadUrl(avatarUrlValue || team.avatarUrl) || undefined} icon={<TeamOutlined />} />
                  {editing ? (
                    <Upload
                      accept="image/*"
                      showUploadList={false}
                      disabled={avatarUploading}
                      beforeUpload={async (file) => {
                        await uploadTeamAvatar(file);
                        return Upload.LIST_IGNORE;
                      }}
                    >
                      <Button icon={<UploadOutlined />} loading={avatarUploading}>
                        上传图片
                      </Button>
                    </Upload>
                  ) : (
                    <Typography.Text type="secondary">{avatarUrlValue || team.avatarUrl ? '已上传头像' : '未上传头像'}</Typography.Text>
                  )}
                </div>
              </Form.Item>
              <Form.Item name="description" label="简介" className="team-detail-form-grid__wide">
                <Input.TextArea readOnly={!editing} rows={3} maxLength={1000} />
              </Form.Item>
            </div>
          </section>

          <Divider />

          <section className="team-detail-section">
            <Typography.Title level={4}>加入配置</Typography.Title>
            <div className="team-detail-form-grid">
              <Form.Item label="可见性">
                <Input value={optionLabel(visibilityOptions, team.visibility)} readOnly />
              </Form.Item>
              <Form.Item label="加入方式">
                <Input value={optionLabel(joinModeOptions, team.joinMode)} readOnly />
              </Form.Item>
              <Form.Item label="创建时间">
                <Input value={team.createdAt || '-'} readOnly />
              </Form.Item>
              <Form.Item label="更新时间">
                <Input value={team.updatedAt || '-'} readOnly />
              </Form.Item>
            </div>
          </section>
        </Form>

        <Divider />

        <section className="team-detail-section">
          <div className="team-detail-section__header">
            <Typography.Title level={4}>成员列表</Typography.Title>
            {actionPermission.can('team:member:invite') ? (
              <Button onClick={() => history.push(`/team/${teamId}/invites`)}>邀请</Button>
            ) : null}
          </div>
          <TeamMemberTable members={members} loading={membersLoading} />
        </section>
      </Card>
    </TeamShell>
  );
};

const MembersPage = () => {
  const teamId = useTeamId();
  const actionPermission = useActionPermission();
  const customTeamMemberFields = useTeamMemberFieldSettings();
  const [members, setMembers] = useState<TeamMemberRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<TeamDraftMemberPayload>();
  const load = async () => {
    setLoading(true);
    try {
      setMembers(await listTeamMembers(teamId));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, [teamId]);

  const submitMember = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await createTeamMember(teamId, values);
      message.success('成员已添加');
      setCreateOpen(false);
      form.resetFields();
      await load();
    } finally {
      setSaving(false);
    }
  };

  return (
    <TeamShell
      title="成员管理"
      actions={
        <>
          <Button onClick={() => history.push(`/team/${teamId}`)}>返回详情</Button>
          <Button type="primary" icon={<PlusOutlined />} disabled={!actionPermission.can('team:member:invite')} onClick={() => setCreateOpen(true)}>
            添加成员
          </Button>
        </>
      }
    >
      <Card>
        <TeamMemberTable
          members={members}
          loading={loading}
          actions={(record) => (
            <Space wrap>
              {record.role !== 'OWNER' ? (
                <Select
                  size="small"
                  value={record.role}
                  disabled={!actionPermission.can('team:member:role-update') || record.memberSource === 'DRAFT'}
                  style={{ width: 120 }}
                  options={roleOptions.map((role) => ({ value: role, label: roleLabel[role] }))}
                  onChange={async (role) => {
                    await updateTeamMemberRole(teamId, record.id, role);
                    await load();
                  }}
                />
              ) : null}
              {record.role !== 'OWNER' ? (
                <Popconfirm title="移除该成员？" onConfirm={async () => { await removeTeamMember(teamId, record.id); await load(); }}>
                  <Button size="small" danger disabled={!actionPermission.can('team:member:remove')}>移除</Button>
                </Popconfirm>
              ) : null}
              {record.role !== 'OWNER' && record.memberSource !== 'DRAFT' ? (
                <Popconfirm title="转让 OWNER 给该成员？" onConfirm={async () => { await transferTeamOwner(teamId, record.id); await load(); }}>
                  <Button size="small" disabled={!actionPermission.can('team:member:role-update')}>转让</Button>
                </Popconfirm>
              ) : null}
            </Space>
          )}
        />
      </Card>
      <Modal
        title="添加成员"
        open={createOpen}
        confirmLoading={saving}
        onOk={() => void submitMember()}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" initialValues={{ role: 'MEMBER', extraValues: {} }}>
          <Form.Item name="memberName" label="成员姓名" rules={[{ required: true, message: '请输入成员姓名' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="employeeNo" label="工号">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="departmentName" label="所属部门">
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="role" label="角色">
            <Select options={roleOptions.map((role) => ({ value: role, label: roleLabel[role] }))} />
          </Form.Item>
          {customTeamMemberFields.map((field) => (
            <Form.Item
              key={field.fieldKey}
              name={['extraValues', field.fieldKey]}
              label={field.fieldLabel}
              rules={[{ required: Boolean(field.required), message: `Please enter ${field.fieldLabel}` }]}
            >
              {renderTeamMemberExtraFieldInput(field)}
            </Form.Item>
          ))}
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} maxLength={512} />
          </Form.Item>
        </Form>
      </Modal>
    </TeamShell>
  );
};

const InvitesPage = () => {
  const teamId = useTeamId();
  const actionPermission = useActionPermission();
  const [invites, setInvites] = useState<TeamInviteRecord[]>([]);
  const [latest, setLatest] = useState<TeamInviteRecord>();
  const [form] = Form.useForm();
  const load = async () => setInvites(await listTeamInvites(teamId));
  useEffect(() => {
    void load();
  }, [teamId]);

  const columns: ColumnsType<TeamInviteRecord> = [
    { title: '邀请码', dataIndex: 'inviteCode', render: (value) => value || '-' },
    { title: '加入角色', dataIndex: 'roleOnJoin', render: (role) => roleLabel[role] || role },
    { title: '审批', dataIndex: 'needApproval', render: (value) => (value ? '需要' : '不需要') },
    { title: '使用次数', render: (_, record) => `${record.usedCount}/${record.maxUses ?? '不限'}` },
    { title: '状态', dataIndex: 'status' },
    {
      title: '操作',
      render: (_, record) => (
        <Popconfirm title="禁用该邀请？" onConfirm={async () => { await disableTeamInvite(teamId, record.id); await load(); }}>
          <Button size="small" disabled={!actionPermission.can('team:member:invite')}>禁用</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <TeamShell title="邀请成员" actions={<Button onClick={() => history.push(`/team/${teamId}`)}>返回详情</Button>}>
      <div className="team-invite-layout">
        <Card title="生成邀请">
          <Form
            form={form}
            layout="vertical"
            initialValues={{ roleOnJoin: 'MEMBER', needApproval: false }}
            onFinish={async (values) => {
              const invite = await createTeamInvite(teamId, values);
              setLatest(invite);
              await load();
              message.success('邀请已生成，明文 token 只展示一次');
            }}
          >
            <Form.Item name="inviteCode" label="邀请码">
              <Input placeholder="至少 8 位，可留空" maxLength={64} />
            </Form.Item>
            <Form.Item name="roleOnJoin" label="加入后角色">
              <Select options={roleOptions.map((role) => ({ value: role, label: roleLabel[role] }))} />
            </Form.Item>
            <Form.Item name="maxUses" label="最大使用次数">
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="needApproval" label="需要审批" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Button type="primary" icon={<LinkOutlined />} htmlType="submit" disabled={!actionPermission.can('team:member:invite')}>生成链接</Button>
          </Form>
          {latest ? (
            <div className="team-invite-latest">
              <QRCode value={fullInviteUrl(latest)} size={152} />
              <Typography.Text copyable={{ text: fullInviteUrl(latest) }}>{fullInviteUrl(latest)}</Typography.Text>
              <Typography.Text type="secondary">raw token: {latest.rawToken}</Typography.Text>
            </div>
          ) : null}
        </Card>
        <Card title="邀请记录">
          <Table rowKey="id" columns={columns} dataSource={invites} pagination={false} />
        </Card>
      </div>
    </TeamShell>
  );
};

const JoinPage = () => {
  const location = useLocation();
  const token = useMemo(() => new URLSearchParams(location.search).get('token') || '', [location.search]);
  const [invite, setInvite] = useState<TeamInviteRecord>();
  const [code, setCode] = useState('');
  const [result, setResult] = useState<string>();

  useEffect(() => {
    if (token) {
      void previewTeamInvite(token).then(setInvite).catch(() => undefined);
    }
  }, [token]);

  const join = async () => {
    const joined = token ? await joinTeamByToken(token) : await joinTeamByCode(code);
    setResult(joined.status);
    if (joined.team?.id) {
      message.success(joined.status === 'JOINED' ? '已加入团队' : '加入申请已提交');
    }
  };

  return (
    <TeamShell title="加入团队" actions={<Button onClick={() => history.push('/team/management')}>团队管理</Button>}>
      <Card>
        {result ? (
          <Result
            status="success"
            title={result === 'JOINED' ? '已加入团队' : '申请已提交'}
            extra={<Button type="primary" onClick={() => history.push('/team/management')}>查看团队</Button>}
          />
        ) : (
          <Space direction="vertical" size="large" className="team-join-panel">
            {token ? (
              <Descriptions bordered column={1}>
                <Descriptions.Item label="团队名称">{invite?.teamName || '-'}</Descriptions.Item>
                <Descriptions.Item label="团队类型">{invite?.teamType || '-'}</Descriptions.Item>
                <Descriptions.Item label="审批">{invite?.needApproval ? '需要审批' : '直接加入'}</Descriptions.Item>
              </Descriptions>
            ) : (
              <Input value={code} onChange={(event) => setCode(event.target.value)} placeholder="输入邀请码" />
            )}
            <Button type="primary" icon={<CopyOutlined />} onClick={join} disabled={!token && !code}>
              加入团队
            </Button>
          </Space>
        )}
      </Card>
    </TeamShell>
  );
};

const JoinRequestsPage = ({ teamId }: { teamId: number }) => {
  const actionPermission = useActionPermission();
  const [requests, setRequests] = useState<TeamJoinRequestRecord[]>([]);
  const load = async () => setRequests(await listTeamJoinRequests(teamId));
  useEffect(() => {
    void load();
  }, [teamId]);
  return (
    <Table
      rowKey="id"
      dataSource={requests}
      pagination={false}
      columns={[
        { title: '序号', width: 72, align: 'center', render: (_, __, index) => index + 1 },
        { title: '状态', dataIndex: 'status' },
        { title: '申请信息', dataIndex: 'applyMessage', render: (value) => value || '-' },
        {
          title: '操作',
          render: (_, record) => record.status === 'PENDING' ? (
            <Space>
              <Button size="small" disabled={!actionPermission.can('team:member:invite')} onClick={async () => { await approveTeamJoinRequest(teamId, record.id); await load(); }}>通过</Button>
              <Button size="small" danger disabled={!actionPermission.can('team:member:invite')} onClick={async () => { await rejectTeamJoinRequest(teamId, record.id); await load(); }}>拒绝</Button>
            </Space>
          ) : null,
        },
      ]}
    />
  );
};

const TeamRoutePage = () => {
  const location = useLocation();
  const teamId = useTeamId();

  if (location.pathname === '/team/create') {
    return <CreateTeamPage />;
  }
  if (location.pathname === '/team/search') {
    return <TeamSearchPage />;
  }
  if (location.pathname.startsWith('/team/join')) {
    return <JoinPage />;
  }
  if (location.pathname.endsWith('/members')) {
    return <MembersPage />;
  }
  if (location.pathname.endsWith('/invites')) {
    return (
      <Tabs
        className="team-tabs-shell"
        items={[
          { key: 'invites', label: '邀请', children: <InvitesPage /> },
          { key: 'requests', label: '加入申请', children: <TeamShell title="加入申请" actions={<Button onClick={() => history.push(`/team/${teamId}`)}>返回详情</Button>}><Card><JoinRequestsPage teamId={teamId} /></Card></TeamShell> },
        ]}
      />
    );
  }
  if (teamId) {
    return <TeamDetailPage />;
  }
  return <TeamListPage />;
};

export default TeamRoutePage;
