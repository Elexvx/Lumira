import { useEffect, useMemo, useState } from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import { history, useLocation, useParams } from '@umijs/max';
import {
  Avatar,
  Button,
  Card,
  Descriptions,
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
} from 'antd';
import type { FormInstance, SelectProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CopyOutlined, DeleteOutlined, LinkOutlined, PlusOutlined, TeamOutlined } from '@ant-design/icons';
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
import {
  adminDeleteTeam,
  adminUpdateTeam,
  approveTeamJoinRequest,
  createTeam,
  createTeamInvite,
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
import type { TeamInviteRecord, TeamJoinRequestRecord, TeamMemberRecord, TeamRecord, TeamRole, TeamUpsertPayload } from '@/services/team/types';
import './team.css';

const roleOptions: TeamRole[] = ['ADMIN', 'MANAGER', 'MEMBER'];
type TeamDictOption = NonNullable<SelectProps['options']>[number];

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

const useTeamDictOptions = () => {
  const { options: teamTypeOptions } = useDictOptions(TEAM_TYPE_DICT_CODE, fallbackTeamTypeOptions);
  const { options: visibilityOptions } = useDictOptions(TEAM_VISIBILITY_DICT_CODE, fallbackVisibilityOptions);
  const { options: joinModeOptions } = useDictOptions(TEAM_JOIN_MODE_DICT_CODE, fallbackJoinModeOptions);

  return { teamTypeOptions, visibilityOptions, joinModeOptions };
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

const TeamShell = ({ title, actions, children }: { title: string; actions?: React.ReactNode; children: React.ReactNode }) => (
  <div className="team-page">
    <div className="team-page__header">
      <div>
        <Typography.Title level={2}>{title}</Typography.Title>
      </div>
      <Space wrap>{actions}</Space>
    </div>
    {children}
  </div>
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

  const openCreateDrawer = () => {
    setEditingTeam(undefined);
    teamForm.resetFields();
    teamForm.setFieldsValue({
      teamType: 'GENERAL',
      visibility: 'PRIVATE',
      joinMode: 'INVITE_ONLY',
    });
    setDrawerOpen(true);
  };

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
    const values = await teamForm.validateFields();
    setSaving(true);
    try {
      if (editingTeam) {
        await adminUpdateTeam(editingTeam.id, values);
        message.success('团队已更新');
      } else {
        await createTeam(values);
        message.success('团队已创建');
      }
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
      render: (_, record) => (
        <Space>
          <Avatar size={32} src={normalizeUploadUrl(record.avatarUrl) || undefined} icon={<TeamOutlined />} />
          <Space direction="vertical" size={0}>
            <Typography.Text strong>{record.teamName}</Typography.Text>
            <Typography.Text type="secondary">{record.teamCode}</Typography.Text>
          </Space>
        </Space>
      ),
    },
    { title: '类型', dataIndex: 'teamType', render: (value) => <Tag>{value}</Tag> },
    { title: '可见性', dataIndex: 'visibility' },
    { title: '加入方式', dataIndex: 'joinMode' },
    { title: 'Owner ID', dataIndex: 'ownerUserId' },
    { title: '成员数', dataIndex: 'memberCount' },
    { title: '状态', dataIndex: 'status', render: (value) => <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>{value}</Tag> },
    { title: '更新时间', dataIndex: 'updatedAt', render: (value) => value || '-' },
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
          search={false}
          scroll={{ x: 1180 }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          locale={{ emptyText: <Empty description="暂无团队" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
          onRefresh={() => void load()}
          toolBarRender={() =>
            actionPermission.buildToolbarActions([
              {
                value: (
                  <Button
                    key="create"
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={openCreateDrawer}
                  >
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
        title={editingTeam ? '编辑团队' : '新增团队'}
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

const CreateTeamPage = () => {
  const actionPermission = useActionPermission();

  if (!actionPermission.can('team:create')) {
    return (
      <TeamShell title="创建团队" actions={<Button onClick={() => history.push('/team')}>返回</Button>}>
        <Result status="403" title="403" />
      </TeamShell>
    );
  }

  return (
    <TeamShell title="创建团队" actions={<Button onClick={() => history.push('/team')}>返回</Button>}>
      <Card>
        <TeamForm
          onFinish={async (values) => {
            const team = await createTeam(values);
            message.success('团队已创建');
            history.push(`/team/${team.id}`);
          }}
        />
      </Card>
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
  const [team, setTeam] = useState<TeamRecord>();
  const [editOpen, setEditOpen] = useState(false);

  const load = async () => setTeam(await getTeam(teamId));
  useEffect(() => {
    void load();
  }, [teamId]);

  if (!team) {
    return <TeamShell title="团队详情"><Card loading /></TeamShell>;
  }

  return (
    <TeamShell
      title={team.teamName}
      actions={
        <>
          <Button onClick={() => history.push('/team')}>我的团队</Button>
          {actionPermission.can('team:member:view') ? (
            <Button onClick={() => history.push(`/team/${teamId}/members`)}>成员</Button>
          ) : null}
          {actionPermission.can('team:member:invite') ? (
            <Button onClick={() => history.push(`/team/${teamId}/invites`)}>邀请</Button>
          ) : null}
          {actionPermission.can('team:update') ? (
            <Button type="primary" onClick={() => setEditOpen(true)}>编辑</Button>
          ) : null}
          {team.myRole === 'OWNER' && actionPermission.can('team:delete') ? (
            <Popconfirm title="确认解散团队？" onConfirm={async () => { await deleteTeam(teamId); message.success('团队已删除'); history.push('/team'); }}>
              <Button danger icon={<DeleteOutlined />}>解散</Button>
            </Popconfirm>
          ) : null}
          {team.myRole !== 'OWNER' ? (
            <Popconfirm title="确认退出团队？" onConfirm={async () => { await leaveTeam(teamId); message.success('已退出团队'); history.push('/team'); }}>
              <Button danger>退出</Button>
            </Popconfirm>
          ) : null}
        </>
      }
    >
      <Card>
        <Descriptions bordered column={2}>
          <Descriptions.Item label="团队编码">{team.teamCode}</Descriptions.Item>
          <Descriptions.Item label="我的角色"><Tag color={roleColor[team.myRole || '']}>{team.myRole || '-'}</Tag></Descriptions.Item>
          <Descriptions.Item label="团队类型">{team.teamType}</Descriptions.Item>
          <Descriptions.Item label="成员数">{team.memberCount}</Descriptions.Item>
          <Descriptions.Item label="可见性">{team.visibility}</Descriptions.Item>
          <Descriptions.Item label="加入方式">{team.joinMode}</Descriptions.Item>
          <Descriptions.Item label="简介" span={2}>{team.description || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>
      <Modal title="编辑团队" open={editOpen} footer={null} onCancel={() => setEditOpen(false)} destroyOnHidden>
        <TeamForm
          initialValues={team}
          onFinish={async (values) => {
            const updated = await updateTeam(teamId, values);
            setTeam(updated);
            setEditOpen(false);
            message.success('团队已更新');
          }}
        />
      </Modal>
    </TeamShell>
  );
};

const MembersPage = () => {
  const teamId = useTeamId();
  const actionPermission = useActionPermission();
  const [members, setMembers] = useState<TeamMemberRecord[]>([]);
  const load = async () => setMembers(await listTeamMembers(teamId));
  useEffect(() => {
    void load();
  }, [teamId]);

  const columns: ColumnsType<TeamMemberRecord> = [
    { title: '序号', width: 72, align: 'center', render: (_, __, index) => index + 1 },
    { title: '角色', dataIndex: 'role', render: (role) => <Tag color={roleColor[role]}>{role}</Tag> },
    { title: '状态', dataIndex: 'status' },
    {
      title: '操作',
      render: (_, record) => (
        <Space wrap>
          {record.role !== 'OWNER' ? (
            <Select
              size="small"
              value={record.role}
              disabled={!actionPermission.can('team:member:role-update')}
              style={{ width: 120 }}
              options={roleOptions.map((role) => ({ value: role, label: role }))}
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
          {record.role !== 'OWNER' ? (
            <Popconfirm title="转让 OWNER 给该成员？" onConfirm={async () => { await transferTeamOwner(teamId, record.id); await load(); }}>
              <Button size="small" disabled={!actionPermission.can('team:member:role-update')}>转让</Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <TeamShell title="成员管理" actions={<Button onClick={() => history.push(`/team/${teamId}`)}>返回详情</Button>}>
      <Card>
        <Table rowKey="id" columns={columns} dataSource={members} pagination={false} />
      </Card>
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
    { title: '加入角色', dataIndex: 'roleOnJoin' },
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
              <Select options={roleOptions.map((role) => ({ value: role, label: role }))} />
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
    <TeamShell title="加入团队" actions={<Button onClick={() => history.push('/team')}>我的团队</Button>}>
      <Card>
        {result ? (
          <Result
            status="success"
            title={result === 'JOINED' ? '已加入团队' : '申请已提交'}
            extra={<Button type="primary" onClick={() => history.push('/team')}>查看我的团队</Button>}
          />
        ) : (
          <Space direction="vertical" size="large" className="team-join-panel">
            {token ? (
              <Descriptions bordered column={1}>
                <Descriptions.Item label="团队 ID">{invite?.teamId || '-'}</Descriptions.Item>
                <Descriptions.Item label="加入角色">{invite?.roleOnJoin || '-'}</Descriptions.Item>
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
