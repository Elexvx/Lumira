import { useEffect, useMemo, useState } from 'react';
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
import type { ColumnsType } from 'antd/es/table';
import { CopyOutlined, DeleteOutlined, LinkOutlined, PlusOutlined, TeamOutlined } from '@ant-design/icons';
import { message } from '@/theme/antdFeedbackBridge';
import {
  approveTeamJoinRequest,
  createTeam,
  createTeamInvite,
  deleteTeam,
  disableTeamInvite,
  getTeam,
  joinTeamByCode,
  joinTeamByToken,
  leaveTeam,
  listMyTeams,
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
import type { TeamInviteRecord, TeamJoinRequestRecord, TeamMemberRecord, TeamRecord, TeamRole } from '@/services/team/types';
import './team.css';

const roleOptions: TeamRole[] = ['ADMIN', 'MANAGER', 'MEMBER'];
const typeOptions = ['GENERAL', 'DEV', 'COMPETITION', 'CLUB', 'OTHER'];
const visibilityOptions = ['PRIVATE', 'PUBLIC'];
const joinModeOptions = ['INVITE_ONLY', 'APPLY', 'OPEN'];

const roleColor: Record<string, string> = {
  OWNER: 'gold',
  ADMIN: 'blue',
  MANAGER: 'cyan',
  MEMBER: 'default',
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

const TeamForm = ({ initialValues, onFinish }: { initialValues?: Partial<TeamRecord>; onFinish: (values: any) => Promise<void> }) => {
  const [form] = Form.useForm();
  return (
    <Form
      form={form}
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
        <Select options={typeOptions.map((value) => ({ value, label: value }))} />
      </Form.Item>
      <Form.Item name="visibility" label="可见性">
        <Select options={visibilityOptions.map((value) => ({ value, label: value }))} />
      </Form.Item>
      <Form.Item name="joinMode" label="加入方式">
        <Select options={joinModeOptions.map((value) => ({ value, label: value }))} />
      </Form.Item>
      <Form.Item name="avatarUrl" label="头像 URL">
        <Input maxLength={512} />
      </Form.Item>
      <Form.Item name="description" label="简介">
        <Input.TextArea rows={4} maxLength={1000} />
      </Form.Item>
      <Button type="primary" htmlType="submit">
        保存
      </Button>
    </Form>
  );
};

const TeamListPage = () => {
  const [teams, setTeams] = useState<TeamRecord[]>([]);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      setTeams(await listMyTeams());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <TeamShell
      title="我的团队"
      actions={<Button type="primary" icon={<PlusOutlined />} onClick={() => history.push('/team/create')}>创建团队</Button>}
    >
      <div className="team-grid">
        {teams.map((team) => (
          <Card key={team.id} hoverable onClick={() => history.push(`/team/${team.id}`)}>
            <Space align="start">
              <Avatar size={44} src={team.avatarUrl} icon={<TeamOutlined />} />
              <div>
                <Typography.Title level={4}>{team.teamName}</Typography.Title>
                <Space wrap>
                  <Tag>{team.teamType}</Tag>
                  <Tag color={roleColor[team.myRole || 'MEMBER']}>{team.myRole}</Tag>
                  <Tag>{team.memberCount} 人</Tag>
                </Space>
                <Typography.Paragraph type="secondary" ellipsis={{ rows: 2 }}>
                  {team.description || '暂无简介'}
                </Typography.Paragraph>
              </div>
            </Space>
          </Card>
        ))}
      </div>
      {!loading && teams.length === 0 ? <Empty description="还没有加入任何团队" /> : null}
    </TeamShell>
  );
};

const CreateTeamPage = () => (
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

const useTeamId = () => {
  const params = useParams<{ teamId?: string }>();
  return Number(params.teamId || 0);
};

const TeamDetailPage = () => {
  const teamId = useTeamId();
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
          <Button onClick={() => history.push(`/team/${teamId}/members`)}>成员</Button>
          <Button onClick={() => history.push(`/team/${teamId}/invites`)}>邀请</Button>
          <Button type="primary" onClick={() => setEditOpen(true)}>编辑</Button>
          {team.myRole === 'OWNER' ? (
            <Popconfirm title="确认解散团队？" onConfirm={async () => { await deleteTeam(teamId); message.success('团队已删除'); history.push('/team'); }}>
              <Button danger icon={<DeleteOutlined />}>解散</Button>
            </Popconfirm>
          ) : (
            <Popconfirm title="确认退出团队？" onConfirm={async () => { await leaveTeam(teamId); message.success('已退出团队'); history.push('/team'); }}>
              <Button danger>退出</Button>
            </Popconfirm>
          )}
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
  const [members, setMembers] = useState<TeamMemberRecord[]>([]);
  const load = async () => setMembers(await listTeamMembers(teamId));
  useEffect(() => {
    void load();
  }, [teamId]);

  const columns: ColumnsType<TeamMemberRecord> = [
    { title: '成员 ID', dataIndex: 'userId' },
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
              <Button size="small" danger>移除</Button>
            </Popconfirm>
          ) : null}
          {record.role !== 'OWNER' ? (
            <Popconfirm title="转让 OWNER 给该成员？" onConfirm={async () => { await transferTeamOwner(teamId, record.id); await load(); }}>
              <Button size="small">转让</Button>
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
          <Button size="small">禁用</Button>
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
            <Button type="primary" icon={<LinkOutlined />} htmlType="submit">生成链接</Button>
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
        { title: '用户 ID', dataIndex: 'userId' },
        { title: '状态', dataIndex: 'status' },
        { title: '申请信息', dataIndex: 'applyMessage', render: (value) => value || '-' },
        {
          title: '操作',
          render: (_, record) => record.status === 'PENDING' ? (
            <Space>
              <Button size="small" onClick={async () => { await approveTeamJoinRequest(teamId, record.id); await load(); }}>通过</Button>
              <Button size="small" danger onClick={async () => { await rejectTeamJoinRequest(teamId, record.id); await load(); }}>拒绝</Button>
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
