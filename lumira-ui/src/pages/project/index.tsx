import { CheckCircleOutlined, DeleteOutlined, EditOutlined, FileDoneOutlined, PlusOutlined, TeamOutlined, UploadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Alert, Avatar, Button, Card, Checkbox, Descriptions, Form, Input, InputNumber, List, Modal, Radio, Result, Select, Space, Steps, Switch, Table, Tag, Typography, Upload } from 'antd';
import type { FormInstance, SelectProps, UploadProps } from 'antd';
import ImgCrop from 'antd-img-crop';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { history, useLocation } from '@umijs/max';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useDictOptions } from '@/hooks/useDictOptions';
import { useResponsive } from '@/hooks/useResponsive';
import { createProject, deleteProject, listProjects, updateProject } from '@/services/competition/api';
import { request } from '@/services/common/request';
import type { ProjectRating, ProjectRecord, ProjectStatus, ProjectUpsertPayload } from '@/services/competition/types';
import { createTeam, listMyTeams, listTeamMembers } from '@/services/team/api';
import type { TeamMemberRecord, TeamRecord, TeamRole, TeamUpsertPayload } from '@/services/team/types';
import type { FileObjectRecord } from '@/types/api';
import { message } from '@/theme/antdFeedbackBridge';
import { DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB, DOCUMENT_UPLOAD_ACCEPT, formatUploadSize, validateDocumentUploadFile } from '@/utils/uploadValidation';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { normalizeTeamCreatePayload } from '../team/teamPayload';
import './ProjectPage.css';

type ProjectFormValues = Partial<Omit<ProjectUpsertPayload, 'code'>> & {
  code?: string;
  teamMode?: ProjectTeamMode;
  selectedTeamId?: number;
  province?: string;
  city?: string;
  industries?: string[];
  isSchoolTechTransfer?: boolean;
  isFirstCompleterOrOwner?: boolean;
  projectProgress?: 'IDEA_PLAN' | 'COMPANY_REGISTERED' | 'SOCIAL_ORGANIZATION_REGISTERED';
  financingDisclosure?: 'PRIVATE' | 'INVESTOR_VISIBLE';
  projectPlanFileName?: string;
  projectPlanFileUrl?: string;
  patentSummary?: string;
  paperSummary?: string;
  awardSummary?: string;
  softwareCopyrightSummary?: string;
  workCopyrightSummary?: string;
  trademarkSummary?: string;
  teamMembers?: string;
  advisorName?: string;
  teamDraft?: TeamUpsertPayload;
  projectTeamMembers?: ProjectTeamMemberDraft[];
};

type ProjectTableParams = {
  keyword?: unknown;
  category?: unknown;
  ownerName?: unknown;
  rating?: unknown;
  status?: unknown;
  featured?: unknown;
  current?: number;
  pageSize?: number;
};

type ProjectTeamMode = 'NEW' | 'EXISTING';
type ProjectTeamMemberRole = Extract<TeamRole, 'ADMIN' | 'MEMBER'>;

type ProjectTeamMemberDraft = {
  name?: string;
  school?: string;
  education?: string;
  major?: string;
  phone?: string;
  role?: ProjectTeamMemberRole;
};

type MaterialUploadFile = NonNullable<UploadProps['fileList']>[number];
type TeamDictOption = NonNullable<SelectProps['options']>[number];
type DraftSaveStatus = 'idle' | 'saving' | 'saved' | 'error';

const TEAM_TYPE_DICT_CODE = 'team_type';
const PROJECT_TEAM_MEMBER_ROLE_DICT_CODE = 'project_team_member_role';

const fallbackTeamTypeOptions: TeamDictOption[] = [
  { value: 'GENERAL', label: '通用团队' },
  { value: 'DEV', label: '开发团队' },
  { value: 'COMPETITION', label: '竞赛团队' },
  { value: 'CLUB', label: '社团组织' },
  { value: 'OTHER', label: '其他' },
];

const fallbackProjectTeamMemberRoleOptions: TeamDictOption[] = [
  { value: 'ADMIN', label: '负责人' },
  { value: 'MEMBER', label: '组员' },
];

const isProjectTeamMemberRole = (value: unknown): value is ProjectTeamMemberRole => value === 'ADMIN' || value === 'MEMBER';

const normalizeProjectTeamMemberRole = (role?: TeamRole | ProjectTeamMemberRole | null): ProjectTeamMemberRole =>
  role === 'OWNER' || role === 'ADMIN' ? 'ADMIN' : 'MEMBER';

const filterProjectTeamMemberRoleOptions = (options: TeamDictOption[]) => {
  const filtered = options.filter((option) => isProjectTeamMemberRole(option.value));
  return filtered.length ? filtered : fallbackProjectTeamMemberRoleOptions;
};

const useTeamDictOptions = () => {
  const { options: teamTypeOptions } = useDictOptions(TEAM_TYPE_DICT_CODE, fallbackTeamTypeOptions);
  const { options: rawProjectTeamMemberRoleOptions } = useDictOptions(PROJECT_TEAM_MEMBER_ROLE_DICT_CODE, fallbackProjectTeamMemberRoleOptions);
  const projectTeamMemberRoleOptions = useMemo(
    () => filterProjectTeamMemberRoleOptions(rawProjectTeamMemberRoleOptions),
    [rawProjectTeamMemberRoleOptions],
  );

  return { teamTypeOptions, projectTeamMemberRoleOptions };
};

const optionLabel = (options: TeamDictOption[], value?: string | null) => {
  const option = options.find((item) => item.value === value);
  return String(option?.label ?? value ?? '-');
};

const localeOptions = [
  { label: '中文', value: 'zh' },
  { label: 'English', value: 'en' },
];

const projectIndustryOptions = [
  { label: '农、林、牧、渔业', value: '农、林、牧、渔业' },
  { label: '采矿业', value: '采矿业' },
  { label: '制造业', value: '制造业' },
  { label: '水、电、热力、燃气生产及供应', value: '水、电、热力、燃气生产及供应' },
  { label: '建筑业', value: '建筑业' },
  { label: '批发和零售业', value: '批发和零售业' },
  { label: '交通运输、仓储和邮政业', value: '交通运输、仓储和邮政业' },
  { label: '住宿和餐饮业', value: '住宿和餐饮业' },
  { label: '信息技术服务业', value: '信息技术服务业' },
  { label: '金融业', value: '金融业' },
  { label: '房地产业', value: '房地产业' },
  { label: '租赁和商务服务业', value: '租赁和商务服务业' },
  { label: '科学技术服务业', value: '科学技术服务业' },
  { label: '水利、环境和公共设施管理', value: '水利、环境和公共设施管理' },
  { label: '居民服务、修理和其他服务业', value: '居民服务、修理和其他服务业' },
  { label: '教育', value: '教育' },
  { label: '医疗和社会工作', value: '医疗和社会工作' },
  { label: '文化、体育和娱乐业', value: '文化、体育和娱乐业' },
];

const legacyProjectCategoryOptions = [
  { label: '赛事项目', value: 'competition' },
  { label: '创新创业', value: 'innovation' },
  { label: '人工智能', value: 'ai' },
  { label: '产业应用', value: 'industry' },
  { label: '其他', value: 'other' },
];

const categoryOptions = [...projectIndustryOptions, ...legacyProjectCategoryOptions];

const provinceOptions = [
  { label: '江苏省', value: '江苏省' },
  { label: '北京市', value: '北京市' },
  { label: '上海市', value: '上海市' },
  { label: '浙江省', value: '浙江省' },
  { label: '广东省', value: '广东省' },
];

const cityOptionsByProvince: Record<string, Array<{ label: string; value: string }>> = {
  江苏省: [
    { label: '南京市', value: '南京市' },
    { label: '苏州市', value: '苏州市' },
    { label: '无锡市', value: '无锡市' },
    { label: '常州市', value: '常州市' },
  ],
  北京市: [{ label: '北京市', value: '北京市' }],
  上海市: [{ label: '上海市', value: '上海市' }],
  浙江省: [
    { label: '杭州市', value: '杭州市' },
    { label: '宁波市', value: '宁波市' },
  ],
  广东省: [
    { label: '广州市', value: '广州市' },
    { label: '深圳市', value: '深圳市' },
  ],
};

const projectProgressOptions = [
  { label: '创意计划阶段', value: 'IDEA_PLAN' },
  { label: '已注册公司运营', value: 'COMPANY_REGISTERED' },
  { label: '已注册社会组织', value: 'SOCIAL_ORGANIZATION_REGISTERED' },
];

const financingDisclosureOptions = [
  { label: '保密，只展示项目概述视频', value: 'PRIVATE' },
  { label: '向投资人公开，可投资人展示项目的经营成长信息、项目概述、团队成员、融资情况、工商注册信息', value: 'INVESTOR_VISIBLE' },
];

const ratingOptions: Array<{ label: string; value: ProjectRating }> = [
  { label: '热门', value: 'popular' },
  { label: '优秀', value: 'excellent' },
  { label: '最新', value: 'new' },
];

const statusOptions: Array<{ label: string; value: ProjectStatus }> = [
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' },
];

const ratingText: Record<string, string> = {
  popular: '热门',
  excellent: '优秀',
  new: '最新',
  all: '全部',
};

const statusText: Record<string, string> = {
  draft: '草稿',
  published: '已发布',
};

const statusColor: Record<string, string> = {
  draft: 'default',
  published: 'green',
};

const projectCreateSteps = [
  { title: '申报说明' },
  { title: '参赛承诺书' },
  { title: '团队组建' },
  { title: '项目信息' },
  { title: '知识产权' },
  { title: '文件材料' },
  { title: '完整展示' },
  { title: '确认上传' },
];

const projectCreateStepQueryKey = 'step';
const projectCreateDraftKey = 'project.create';
const projectMaterialFileLimit = 10;
const projectLogoMaxSizeMb = 5;

interface ProjectCreateDraft {
  currentStep: number;
  instructionConfirmed: boolean;
  commitmentConfirmed: boolean;
  savedAt: number;
  values: Partial<ProjectFormValues>;
  uploadedMaterials: FileObjectRecord[];
  createdTeam?: TeamRecord;
}

interface StoredProjectCreateDraft {
  payload: ProjectCreateDraft;
  updatedAt: number;
}

const trimOptional = (value?: string) => {
  const trimmed = value?.trim();
  return trimmed || undefined;
};

const buildProjectCode = () => `project-${Date.now()}`;

const buildDefaultTeamDraft = (): TeamUpsertPayload => ({
  teamName: '',
  teamType: 'GENERAL',
  initialMembers: [
    { memberName: '', employeeNo: '', departmentName: '', role: 'MEMBER', remark: '' },
  ],
});

const buildDefaultProjectTeamMembers = (): ProjectTeamMemberDraft[] => [
  { name: '', school: '', education: '', major: '', phone: '', role: 'MEMBER' },
];

const normalizeProjectTeamMemberRows = (members?: ProjectTeamMemberDraft[]) =>
  (members || [])
    .map((member) => ({
      name: trimOptional(member.name),
      school: trimOptional(member.school),
      education: trimOptional(member.education),
      major: trimOptional(member.major),
      phone: trimOptional(member.phone),
      role: normalizeProjectTeamMemberRole(member.role),
    }))
    .filter((member) => member.name || member.school || member.education || member.major || member.phone);

const mapTeamDraftMembersToProjectMembers = (members?: TeamUpsertPayload['initialMembers']): ProjectTeamMemberDraft[] => {
  const mappedMembers = (members || []).map((member) => ({
    name: member.memberName || '',
    school: member.departmentName || '',
    education: '',
    major: '',
    phone: member.employeeNo || '',
    role: normalizeProjectTeamMemberRole(member.role),
  }));
  return mappedMembers.length ? mappedMembers : buildDefaultProjectTeamMembers();
};

const mapExistingTeamMembersToProjectMembers = (members: TeamMemberRecord[]) => {
  const mappedMembers = members.map((member) => ({
    name: member.memberName || member.memberAlias || '',
    school: member.departmentName || '',
    education: '',
    major: '',
    phone: member.employeeNo || '',
    role: normalizeProjectTeamMemberRole(member.role),
  }));
  return mappedMembers.length ? mappedMembers : buildDefaultProjectTeamMembers();
};

const projectTeamMembersToTeamDraftMembers = (members?: ProjectTeamMemberDraft[]): NonNullable<TeamUpsertPayload['initialMembers']> =>
  normalizeProjectTeamMemberRows(members)
    .filter((member) => member.name)
    .map((member) => {
      const remark = [
        member.education ? `学历：${member.education}` : '',
        member.major ? `所学专业：${member.major}` : '',
        member.phone ? `手机号码：${member.phone}` : '',
      ]
        .filter(Boolean)
        .join('；');
      return {
        memberName: member.name || '',
        departmentName: member.school,
        employeeNo: member.phone,
        role: normalizeProjectTeamMemberRole(member.role),
        remark: remark || undefined,
      };
    });

const mapExistingTeamMembersToDraft = (members: TeamMemberRecord[]): NonNullable<TeamUpsertPayload['initialMembers']> =>
  members.map((member) => ({
    memberName: member.memberName || member.memberAlias || '',
    employeeNo: member.employeeNo || undefined,
    departmentName: member.departmentName || undefined,
    role: member.role === 'OWNER' ? 'ADMIN' : member.role || 'MEMBER',
    remark: member.remark || undefined,
  }));

const mapExistingTeamToDraft = (team: TeamRecord, members: TeamMemberRecord[] = []): TeamUpsertPayload => ({
  teamName: team.teamName,
  teamType: team.teamType || 'GENERAL',
  avatarUrl: team.avatarUrl || undefined,
  description: team.description || undefined,
  initialMembers: mapExistingTeamMembersToDraft(members),
});

const normalizeProjectTeamCreatePayload = (teamDraft?: TeamUpsertPayload, projectMembers?: ProjectTeamMemberDraft[]): TeamUpsertPayload => {
  const normalized = normalizeTeamCreatePayload({
    ...(teamDraft || buildDefaultTeamDraft()),
    initialMembers: projectMembers ? projectTeamMembersToTeamDraftMembers(projectMembers) : teamDraft?.initialMembers,
  });
  return {
    teamName: normalized.teamName,
    teamType: normalized.teamType,
    avatarUrl: normalized.avatarUrl,
    description: normalized.description,
    initialMembers: normalized.initialMembers,
  };
};

const parseFeaturedFilter = (value: unknown) => {
  if (typeof value === 'boolean') {
    return value;
  }
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  return undefined;
};

const buildDefaultProjectCreateValues = (): ProjectFormValues => ({
  code: buildProjectCode(),
  locale: 'zh',
  teamMode: 'NEW',
  category: '',
  province: '江苏省',
  city: '南京市',
  industries: [],
  isSchoolTechTransfer: false,
  isFirstCompleterOrOwner: true,
  projectProgress: 'IDEA_PLAN',
  financingDisclosure: 'INVESTOR_VISIBLE',
  teamDraft: buildDefaultTeamDraft(),
  projectTeamMembers: buildDefaultProjectTeamMembers(),
  rating: 'popular',
  status: 'draft',
  sort: 100,
  featured: false,
});

const parseProjectCreateStepFromSearch = (search: string) => {
  const stepValue = Number(new URLSearchParams(search).get(projectCreateStepQueryKey));
  if (!Number.isInteger(stepValue) || stepValue < 1) {
    return 0;
  }
  return Math.min(stepValue - 1, projectCreateSteps.length - 1);
};

const createProjectStepSearch = (stepIndex: number) => `?${projectCreateStepQueryKey}=${Math.min(stepIndex + 1, projectCreateSteps.length)}`;

const readProjectCreateDraft = async (): Promise<ProjectCreateDraft | undefined> => {
  const stored = await request<StoredProjectCreateDraft | null>(`/v2/user-drafts/${projectCreateDraftKey}`, {
    method: 'GET',
    silent: true,
  });
  return stored?.payload;
};

const writeProjectCreateDraft = async (draft: ProjectCreateDraft) => {
  await request<StoredProjectCreateDraft>(`/v2/user-drafts/${projectCreateDraftKey}`, {
    method: 'PUT',
    data: draft,
    silent: true,
  });
};

const clearProjectCreateDraft = async () => {
  await request<void>(`/v2/user-drafts/${projectCreateDraftKey}`, {
    method: 'DELETE',
    silent: true,
  });
};

const getProjectCreateMissingFields = (values: Partial<ProjectFormValues>) => {
  const missingFields: string[] = [];
  if (!trimOptional(values.title)) {
    missingFields.push('项目名称');
  }
  if (!trimOptional(values.imageUrl)) {
    missingFields.push('项目 logo');
  }
  if (!trimOptional(values.province) || !trimOptional(values.city)) {
    missingFields.push('所在地');
  }
  if (!values.industries?.length && !trimOptional(values.category)) {
    missingFields.push('所属领域');
  }
  if (!trimOptional(values.description)) {
    missingFields.push('项目概述');
  }
  if (values.isSchoolTechTransfer === undefined) {
    missingFields.push('学校科技成果转化');
  }
  if (values.isFirstCompleterOrOwner === undefined) {
    missingFields.push('科技成果完成人或所有人确认');
  }
  if (!values.projectProgress) {
    missingFields.push('项目进展');
  }
  if (!values.financingDisclosure) {
    missingFields.push('投融资隐私设置');
  }
  if (!trimOptional(values.locale)) {
    missingFields.push('语言');
  }
  if (!trimOptional(values.status)) {
    missingFields.push('状态');
  }
  return missingFields;
};

const getProjectTeamMissingFields = (values: Partial<ProjectFormValues>) => {
  const missingFields: string[] = [];
  if (values.teamMode === 'EXISTING') {
    if (!values.selectedTeamId) {
      missingFields.push('已有团队');
    }
    return missingFields;
  }
  const teamDraft = normalizeProjectTeamCreatePayload(values.teamDraft);
  if (!trimOptional(teamDraft.teamName)) {
    missingFields.push('团队名称');
  }
  const members = normalizeProjectTeamMemberRows(values.projectTeamMembers);
  if (!members.some((member) => member.name)) {
    missingFields.push('姓名');
  }
  return missingFields;
};

const getAllowedProjectCreateStep = (
  requestedStep: number,
  values: Partial<ProjectFormValues>,
  instructionConfirmed: boolean,
  commitmentConfirmed: boolean,
  hasMaterials: boolean,
  hasCreatedProject: boolean,
) => {
  if (requestedStep >= 1 && !instructionConfirmed) {
    return 0;
  }
  if (requestedStep >= 2 && !commitmentConfirmed) {
    return 1;
  }
  if (requestedStep >= 3 && getProjectTeamMissingFields(values).length) {
    return 2;
  }
  if (requestedStep >= 4 && getProjectCreateMissingFields(values).length) {
    return 3;
  }
  if (requestedStep >= 6 && !hasMaterials) {
    return 5;
  }
  if (requestedStep >= 7 && !hasCreatedProject) {
    return 6;
  }
  return requestedStep;
};

const findOptionLabel = (options: Array<{ label: string; value: string }>, value?: string | null) =>
  options.find((item) => item.value === value)?.label || value || '-';

const renderBooleanValue = (value?: boolean | null) => {
  if (value === undefined || value === null) {
    return '-';
  }
  return value ? '是' : '否';
};

const renderArrayValue = (value?: string[] | null) => (value?.length ? value.join('、') : '-');

const renderTextValue = (value?: string | number | null) => {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  return value;
};

const materialFileSize = (file: MaterialUploadFile | FileObjectRecord) => {
  if ('fileSizeLabel' in file && file.fileSizeLabel) {
    return file.fileSizeLabel;
  }
  const size = 'fileSizeBytes' in file ? file.fileSizeBytes : file.size;
  return typeof size === 'number' ? formatUploadSize(size) : '-';
};

const materialFileName = (file: MaterialUploadFile | FileObjectRecord) =>
  'originalFileName' in file ? file.originalFileName : file.name;

const formatDraftSavedAt = (timestamp?: number) =>
  timestamp
    ? new Date(timestamp).toLocaleTimeString('zh-CN', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    : '';

const uploadProjectFile = async (file: File, category: string, tags: string[], remark: string) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('category', category);
  formData.append('tags', tags.join(','));
  formData.append('remark', remark);
  return request<FileObjectRecord>('/v1/files/upload', {
    method: 'POST',
    headers: {},
    data: formData,
    ...API_OPTS.NO_REDIRECT,
    silent: true,
  });
};

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

const uploadProjectMaterial = async (file: File, payload: ProjectUpsertPayload) =>
  uploadProjectFile(file, 'AIADC 项目参赛材料', ['aiadc', 'project-material', payload.code], `项目申报材料：${payload.title}`);

const resolveFilePublicUrl = (file: FileObjectRecord) => file.publicUrl || file.previewUrl || file.downloadUrl || file.storagePath || '';

const normalizePayload = (values: ProjectFormValues, editingRecord?: ProjectRecord): ProjectUpsertPayload => ({
  code: trimOptional(values.code) || editingRecord?.code || buildProjectCode(),
  locale: values.locale || 'zh',
  title: (values.title || '').trim(),
  category: (values.industries?.[0] || values.category || '').trim(),
  description: trimOptional(values.description),
  imageUrl: trimOptional(values.imageUrl),
  ownerName: trimOptional(values.ownerName) || trimOptional(values.teamDraft?.teamName),
  rating: values.rating || 'popular',
  status: values.status || 'draft',
  sort: values.sort ?? 100,
  tags: trimOptional(values.tags) || values.industries?.join(','),
  ctaLabel: trimOptional(values.ctaLabel),
  ctaHref: trimOptional(values.ctaHref),
  featured: Boolean(values.featured),
});

const ProjectForm = ({
  form,
  showCode = true,
  onValuesChange,
}: {
  form: FormInstance<ProjectFormValues>;
  showCode?: boolean;
  onValuesChange?: () => void;
}) => (
  <Form<ProjectFormValues>
    form={form}
    layout="vertical"
    onValuesChange={onValuesChange}
    initialValues={{
      locale: 'zh',
      category: 'competition',
      rating: 'popular',
      status: 'draft',
      sort: 100,
      featured: false,
    }}
  >
    <Form.Item name="title" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]}>
      <Input maxLength={128} />
    </Form.Item>
    {showCode ? (
      <Form.Item name="code" label="项目编码">
        <Input maxLength={64} placeholder="不填时自动生成" />
      </Form.Item>
    ) : null}
    <Space size="middle" style={{ width: '100%' }} align="start" wrap>
      <Form.Item name="category" label="项目分类" rules={[{ required: true, message: '请选择项目分类' }]} style={{ flex: 1, minWidth: 180 }}>
        <Select showSearch options={categoryOptions} optionFilterProp="label" />
      </Form.Item>
      <Form.Item name="locale" label="语言" rules={[{ required: true }]} style={{ flex: 1, minWidth: 160 }}>
        <Select options={localeOptions} />
      </Form.Item>
    </Space>
    <Form.Item name="description" label="项目简介">
      <Input.TextArea rows={4} maxLength={1000} showCount />
    </Form.Item>
    <Form.Item name="ownerName" label="项目负责人">
      <Input maxLength={128} />
    </Form.Item>
    <Space size="middle" style={{ width: '100%' }} align="start" wrap>
      <Form.Item name="rating" label="推荐等级" style={{ flex: 1, minWidth: 160 }}>
        <Select options={ratingOptions} />
      </Form.Item>
      <Form.Item name="status" label="状态" rules={[{ required: true }]} style={{ flex: 1, minWidth: 160 }}>
        <Select options={statusOptions} />
      </Form.Item>
      <Form.Item name="sort" label="排序" style={{ flex: 1, minWidth: 140 }}>
        <InputNumber min={0} max={9999} style={{ width: '100%' }} />
      </Form.Item>
    </Space>
    <Form.Item name="featured" label="重点项目" valuePropName="checked">
      <Switch checkedChildren="是" unCheckedChildren="否" />
    </Form.Item>
    <Form.Item name="tags" label="标签">
      <Input maxLength={1000} placeholder="多个标签用英文逗号分隔" />
    </Form.Item>
    <Form.Item name="imageUrl" label="封面地址">
      <Input maxLength={512} />
    </Form.Item>
    <Space size="middle" style={{ width: '100%' }} align="start" wrap>
      <Form.Item name="ctaLabel" label="按钮文案" style={{ flex: 1, minWidth: 180 }}>
        <Input maxLength={64} />
      </Form.Item>
      <Form.Item name="ctaHref" label="按钮链接" style={{ flex: 1, minWidth: 180 }}>
        <Input maxLength={512} />
      </Form.Item>
    </Space>
  </Form>
);

const ProjectTeamFormationForm = ({
  form,
  teams,
  teamsLoading,
  syncingTeam,
  onTeamModeChange,
  onExistingTeamSelect,
  onDraftChange,
}: {
  form: FormInstance<ProjectFormValues>;
  teams: TeamRecord[];
  teamsLoading?: boolean;
  syncingTeam?: boolean;
  onTeamModeChange?: (mode: ProjectTeamMode) => void;
  onExistingTeamSelect?: (teamId?: number) => void;
  onDraftChange?: () => void;
}) => {
  const { teamTypeOptions, projectTeamMemberRoleOptions } = useTeamDictOptions();
  const avatarUrlValue = Form.useWatch(['teamDraft', 'avatarUrl'], form);
  const teamMode = Form.useWatch('teamMode', form) || 'NEW';
  const usingExistingTeam = teamMode === 'EXISTING';
  const [avatarUploading, setAvatarUploading] = useState(false);

  const uploadCreateTeamAvatar = async (file: File) => {
    setAvatarUploading(true);
    try {
      const uploadedUrl = await uploadTeamAvatarImage(file);
      if (uploadedUrl) {
        form.setFieldValue(['teamDraft', 'avatarUrl'], uploadedUrl);
        onDraftChange?.();
        message.success('头像已上传');
      }
    } catch (error) {
      showErrorMessage(error, '头像上传失败，请稍后重试');
    } finally {
      setAvatarUploading(false);
    }
  };

  return (
    <Form<ProjectFormValues> form={form} layout="vertical" className="project-application-form" onValuesChange={onDraftChange}>
      <div className="project-application-section">
        <Typography.Title level={5}>团队来源</Typography.Title>
        <div className="project-team-grid">
          <Form.Item name="teamMode" label="组建方式">
            <Radio.Group
              optionType="button"
              buttonStyle="solid"
              options={[
                { label: '新建团队', value: 'NEW' },
                { label: '使用已有团队', value: 'EXISTING' },
              ]}
              onChange={(event) => onTeamModeChange?.(event.target.value as ProjectTeamMode)}
            />
          </Form.Item>
          {usingExistingTeam ? (
            <Form.Item name="selectedTeamId" label="已有团队" rules={[{ required: true, message: '请选择已有团队' }]}>
              <Select
                showSearch
                placeholder="请选择已有团队"
                loading={teamsLoading || syncingTeam}
                optionFilterProp="label"
                options={teams.map((team) => ({
                  value: team.id,
                  label: `${team.teamName}${team.teamCode ? `（${team.teamCode}）` : ''}`,
                }))}
                onChange={(teamId) => onExistingTeamSelect?.(Number(teamId))}
              />
            </Form.Item>
          ) : null}
        </div>
      </div>

      <div className="project-application-section">
        <Typography.Title level={5}>团队管理</Typography.Title>
        <div className="project-team-grid">
          <Form.Item name={['teamDraft', 'avatarUrl']} hidden>
            <Input />
          </Form.Item>
          <Form.Item label="团队头像" className="project-team-grid__wide">
            <div className="project-team-avatar-field">
              <Avatar size={48} src={normalizeUploadUrl(avatarUrlValue) || undefined} icon={<TeamOutlined />} />
              <ImgCrop
                aspect={1}
                cropShape="rect"
                showGrid
                zoomSlider
                rotationSlider
                modalTitle="裁剪团队头像"
                modalOk="确认上传"
                modalCancel="取消"
                modalWidth={520}
              >
                <Upload
                  accept="image/*"
                  showUploadList={false}
                  disabled={avatarUploading || usingExistingTeam}
                  beforeUpload={async (file) => {
                    await uploadCreateTeamAvatar(file);
                    return Upload.LIST_IGNORE;
                  }}
                >
                  <Button icon={<UploadOutlined />} loading={avatarUploading} disabled={usingExistingTeam}>
                    上传图片
                  </Button>
                </Upload>
              </ImgCrop>
              {avatarUrlValue ? (
                <Button
                  type="link"
                  disabled={usingExistingTeam}
                  onClick={() => {
                    form.setFieldValue(['teamDraft', 'avatarUrl'], undefined);
                    onDraftChange?.();
                  }}
                >
                  移除
                </Button>
              ) : null}
            </div>
          </Form.Item>
          <Form.Item name={['teamDraft', 'teamName']} label="团队名称" rules={usingExistingTeam ? [] : [{ required: true, message: '请输入团队名称' }]}>
            <Input placeholder="请输入团队名称" maxLength={128} disabled={usingExistingTeam} />
          </Form.Item>
          <Form.Item name={['teamDraft', 'teamType']} label="团队类型">
            <Select placeholder="请选择团队类型" options={teamTypeOptions} disabled={usingExistingTeam} />
          </Form.Item>
          <Form.Item name={['teamDraft', 'description']} label="团队简介" className="project-team-grid__wide">
            <Input.TextArea placeholder="请输入团队简介" rows={3} maxLength={1000} disabled={usingExistingTeam} />
          </Form.Item>
        </div>
      </div>

      <div className="project-application-section">
        <Typography.Title level={5}>成员管理</Typography.Title>
        <Form.List name="projectTeamMembers">
          {(fields, { add, remove }) => (
            <div className="project-team-member-table">
              <Table
                rowKey="key"
                pagination={false}
                dataSource={fields}
                tableLayout="fixed"
                scroll={{ x: 790 }}
                columns={[
                  {
                    title: '姓名',
                    width: 104,
                    render: (_, field) => (
                      <Form.Item
                        name={[field.name, 'name']}
                        rules={usingExistingTeam ? [] : [{ required: true, message: '请输入姓名' }]}
                        className="project-team-table-form-item"
                      >
                        <Input placeholder="请输入姓名" maxLength={128} disabled={usingExistingTeam} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '所在或毕业院校',
                    width: 172,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'school']} className="project-team-table-form-item">
                        <Input placeholder="请输入所在或毕业院校" maxLength={128} disabled={usingExistingTeam} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '学历',
                    width: 86,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'education']} className="project-team-table-form-item">
                        <Input placeholder="请输入学历" maxLength={64} disabled={usingExistingTeam} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '所学专业',
                    width: 134,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'major']} className="project-team-table-form-item">
                        <Input placeholder="请输入所学专业" maxLength={128} disabled={usingExistingTeam} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '手机号码',
                    width: 134,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'phone']} className="project-team-table-form-item">
                        <Input placeholder="请输入手机号码" maxLength={32} disabled={usingExistingTeam} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '角色',
                    width: 110,
                    render: (_, field) => (
                      <Form.Item
                        name={[field.name, 'role']}
                        initialValue="MEMBER"
                        rules={usingExistingTeam ? [] : [{ required: true, message: '请选择角色' }]}
                        className="project-team-table-form-item"
                      >
                        <Select options={projectTeamMemberRoleOptions} disabled={usingExistingTeam} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '',
                    width: 50,
                    render: (_, field) => (
                      <Button
                        danger
                        type="text"
                        icon={<DeleteOutlined />}
                        aria-label="删除成员"
                        disabled={usingExistingTeam || fields.length <= 1}
                        onClick={() => remove(field.name)}
                      />
                    ),
                  },
                ]}
              />
              <Button
                block
                className="project-team-member-add-row"
                icon={<PlusOutlined />}
                disabled={usingExistingTeam}
                onClick={() => add({ name: '', school: '', education: '', major: '', phone: '', role: 'MEMBER' })}
              >
                添加一行数据
              </Button>
            </div>
          )}
        </Form.List>
      </div>
    </Form>
  );
};

const ProjectApplicationForm = ({
  form,
  onDraftChange,
  onProjectPlanUploaded,
}: {
  form: FormInstance<ProjectFormValues>;
  onDraftChange?: () => void;
  onProjectPlanUploaded?: (file: FileObjectRecord) => void;
}) => {
  const logoUrl = Form.useWatch('imageUrl', form);
  const province = Form.useWatch('province', form);
  const projectPlanFileName = Form.useWatch('projectPlanFileName', form);
  const [uploadingLogo, setUploadingLogo] = useState(false);
  const [uploadingPlan, setUploadingPlan] = useState(false);
  const currentCityOptions = cityOptionsByProvince[province || '江苏省'] || [];

  const handleLogoUpload = async (file: File) => {
    if (!file.type.startsWith('image/')) {
      message.error('请上传 JPG、GIF、PNG 格式的项目 logo');
      return Upload.LIST_IGNORE;
    }
    if (file.size / 1024 / 1024 > projectLogoMaxSizeMb) {
      message.error(`项目 logo 文件不能超过 ${projectLogoMaxSizeMb}MB`);
      return Upload.LIST_IGNORE;
    }
    setUploadingLogo(true);
    try {
      const values = form.getFieldsValue(true) as ProjectFormValues;
      const code = trimOptional(values.code) || buildProjectCode();
      form.setFieldValue('code', code);
      const uploaded = await uploadProjectFile(file, 'AIADC 项目 Logo', ['aiadc', 'project-logo', code], `项目 Logo：${values.title || file.name}`);
      form.setFieldValue('imageUrl', resolveFilePublicUrl(uploaded));
      onDraftChange?.();
      message.success('项目 logo 已上传');
    } catch (error) {
      showErrorMessage(error, '项目 logo 上传失败');
    } finally {
      setUploadingLogo(false);
    }
    return Upload.LIST_IGNORE;
  };

  const handlePlanUpload = async (file: File) => {
    const validationMessage = validateDocumentUploadFile(file, {
      maxSizeMb: DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB,
    });
    if (validationMessage) {
      message.error(validationMessage);
      return Upload.LIST_IGNORE;
    }
    setUploadingPlan(true);
    try {
      const values = form.getFieldsValue(true) as ProjectFormValues;
      const code = trimOptional(values.code) || buildProjectCode();
      form.setFieldValue('code', code);
      const uploaded = await uploadProjectFile(file, 'AIADC 项目计划书', ['aiadc', 'project-plan', code], `项目计划书：${values.title || file.name}`);
      form.setFieldsValue({
        projectPlanFileName: uploaded.originalFileName,
        projectPlanFileUrl: resolveFilePublicUrl(uploaded),
      });
      onProjectPlanUploaded?.(uploaded);
      onDraftChange?.();
      message.success('项目计划书已上传');
    } catch (error) {
      showErrorMessage(error, '项目计划书上传失败');
    } finally {
      setUploadingPlan(false);
    }
    return Upload.LIST_IGNORE;
  };

  return (
    <Form<ProjectFormValues>
      form={form}
      layout="vertical"
      className="project-application-form"
      onValuesChange={(changedValues) => {
        if (Array.isArray(changedValues.industries)) {
          form.setFieldValue('category', changedValues.industries[0] || undefined);
        }
        if (changedValues.province) {
          const nextCities = cityOptionsByProvince[changedValues.province] || [];
          form.setFieldValue('city', nextCities[0]?.value);
        }
        onDraftChange?.();
      }}
      initialValues={buildDefaultProjectCreateValues()}
    >
      <div className="project-application-section">
        <Typography.Title level={5}>项目信息</Typography.Title>
        <Form.Item name="imageUrl" label="项目 logo" rules={[{ required: true, message: '请上传项目 logo' }]}>
          <div className="project-logo-field">
            <div className="project-logo-field__preview">
              {logoUrl ? <img src={logoUrl} alt="项目 logo" /> : <span>Logo</span>}
            </div>
            <Upload accept="image/jpeg,image/gif,image/png" showUploadList={false} beforeUpload={handleLogoUpload}>
              <Button type="primary" loading={uploadingLogo}>
                点击上传
              </Button>
            </Upload>
            <Typography.Text type="secondary">仅支持 JPG、GIF、PNG 格式，文件小于 5MB。</Typography.Text>
          </div>
        </Form.Item>

        <Form.Item name="title" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]}>
          <Input maxLength={128} placeholder="请输入项目名称" />
        </Form.Item>

        <Form.Item label="所在地" required>
          <Space className="project-location-fields" size="middle" wrap>
            <Form.Item name="province" rules={[{ required: true, message: '请选择省份' }]} noStyle>
              <Select options={provinceOptions} placeholder="请选择省份" />
            </Form.Item>
            <Form.Item name="city" rules={[{ required: true, message: '请选择城市' }]} noStyle>
              <Select options={currentCityOptions} placeholder="请选择城市" />
            </Form.Item>
          </Space>
        </Form.Item>

        <Form.Item name="industries" label="所属领域" rules={[{ required: true, message: '请选择所属领域' }]}>
          <Checkbox.Group className="project-choice-grid">
            {projectIndustryOptions.map((option) => (
              <Checkbox key={option.value} className="project-choice-tile" value={option.value}>
                {option.label}
              </Checkbox>
            ))}
          </Checkbox.Group>
        </Form.Item>

        <Form.Item name="description" label="项目概述" rules={[{ required: true, message: '请输入项目概述' }]}>
          <Input.TextArea rows={7} maxLength={1000} showCount placeholder="请输入项目概述" />
        </Form.Item>
        <Alert
          type="warning"
          showIcon
          className="project-application-tip"
          message="填写内容中请勿含有非法字符或与申报无关的信息，请仔细检查并修改后再提交。"
        />

        <Form.Item name="isSchoolTechTransfer" label="学校科技成果转化" rules={[{ required: true, message: '请选择是否为学校科技成果转化' }]}>
          <Radio.Group>
            <Radio value>是</Radio>
            <Radio value={false}>否</Radio>
          </Radio.Group>
        </Form.Item>

        <Form.Item
          name="isFirstCompleterOrOwner"
          label="参赛申报人为科技成果的第一完成人或所有人"
          rules={[{ required: true, message: '请选择申报人与科技成果关系' }]}
        >
          <Radio.Group>
            <Radio value>是</Radio>
            <Radio value={false}>否</Radio>
          </Radio.Group>
        </Form.Item>

        <Form.Item name="projectProgress" label="项目进展" rules={[{ required: true, message: '请选择项目进展' }]}>
          <Radio.Group options={projectProgressOptions} />
        </Form.Item>

        <Form.Item name="financingDisclosure" label="投融资阶段设置" rules={[{ required: true, message: '请选择投融资阶段设置' }]}>
          <Radio.Group className="project-disclosure-options" options={financingDisclosureOptions} />
        </Form.Item>

        <Form.Item label="项目计划书">
          <Space direction="vertical" size={4}>
            <Upload accept={DOCUMENT_UPLOAD_ACCEPT} showUploadList={false} beforeUpload={handlePlanUpload}>
              <Button type="primary" loading={uploadingPlan}>
                {projectPlanFileName ? '重新上传' : '上传项目计划书'}
              </Button>
            </Upload>
            {projectPlanFileName ? <Typography.Text>{projectPlanFileName}</Typography.Text> : null}
            <Typography.Text type="secondary">格式为 PDF、Word、PPT、Excel、Markdown 或 TXT，文件数量限一个，完成后可在下一步继续上传其他材料。</Typography.Text>
          </Space>
        </Form.Item>
      </div>

      <div className="project-application-section">
        <Typography.Title level={5}>指导教师</Typography.Title>
        <Form.Item name="advisorName" label="指导教师">
          <Input maxLength={128} placeholder="请输入指导教师姓名" />
        </Form.Item>
      </div>
    </Form>
  );
};

const ProjectIntellectualPropertyForm = ({
  form,
  onDraftChange,
}: {
  form: FormInstance<ProjectFormValues>;
  onDraftChange?: () => void;
}) => (
  <Form<ProjectFormValues> form={form} layout="vertical" className="project-application-form" onValuesChange={onDraftChange}>
    <Alert
      type="warning"
      showIcon
      className="project-application-warning"
      message="请如实填写项目涉及的专利、论文、项目所获奖项、软件著作权、作品著作权、商标信息，且与项目计划书中涉及的内容保持一致。"
    />

    <div className="project-application-section">
      <Typography.Title level={5}>知识产权与成果信息</Typography.Title>
      <Form.Item name="patentSummary" label="已获专利">
        <Input.TextArea rows={2} maxLength={1000} placeholder="可填写专利名称、专利号、授权状态等" />
      </Form.Item>
      <Form.Item name="paperSummary" label="论文发表">
        <Input.TextArea rows={2} maxLength={1000} placeholder="可填写论文题目、期刊/会议、发表时间等" />
      </Form.Item>
      <Form.Item name="awardSummary" label="项目所获奖项">
        <Input.TextArea rows={2} maxLength={1000} placeholder="可填写奖项名称、获奖级别、获奖时间等" />
      </Form.Item>
      <Form.Item name="softwareCopyrightSummary" label="软件著作权">
        <Input.TextArea rows={2} maxLength={1000} placeholder="可填写软著名称、登记号、权利人等" />
      </Form.Item>
      <Form.Item name="workCopyrightSummary" label="作品著作权">
        <Input.TextArea rows={2} maxLength={1000} placeholder="可填写作品名称、登记号、权利人等" />
      </Form.Item>
      <Form.Item name="trademarkSummary" label="注册商标">
        <Input.TextArea rows={2} maxLength={1000} placeholder="可填写商标名称、注册号、类别等" />
      </Form.Item>
    </div>
  </Form>
);

const CreateProjectPage = () => {
  const location = useLocation();
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const [form] = Form.useForm<ProjectFormValues>();
  const { teamTypeOptions, projectTeamMemberRoleOptions } = useTeamDictOptions();
  const defaultValuesRef = useRef<ProjectFormValues>(buildDefaultProjectCreateValues());
  const [currentStep, setCurrentStep] = useState(0);
  const [instructionConfirmed, setInstructionConfirmed] = useState(false);
  const [commitmentConfirmed, setCommitmentConfirmed] = useState(false);
  const [materialFiles, setMaterialFiles] = useState<MaterialUploadFile[]>([]);
  const [uploadedMaterials, setUploadedMaterials] = useState<FileObjectRecord[]>([]);
  const [createdTeam, setCreatedTeam] = useState<TeamRecord>();
  const [createdProject, setCreatedProject] = useState<ProjectRecord>();
  const [teams, setTeams] = useState<TeamRecord[]>([]);
  const [teamsLoading, setTeamsLoading] = useState(false);
  const [syncingTeam, setSyncingTeam] = useState(false);
  const [saving, setSaving] = useState(false);
  const [draftSavedAt, setDraftSavedAt] = useState<number>();
  const [draftSaveStatus, setDraftSaveStatus] = useState<DraftSaveStatus>('idle');
  const [draftHydrated, setDraftHydrated] = useState(false);
  const draftSaveTimerRef = useRef<number | undefined>(undefined);

  const collectProjectCreateValues = useCallback((): ProjectFormValues => ({
    ...defaultValuesRef.current,
    ...(form.getFieldsValue(true) as Partial<ProjectFormValues>),
  }), [form]);

  const draftSaveText = useMemo(() => {
    if (draftSaveStatus === 'saving') {
      return '正在保存草稿...';
    }
    if (draftSaveStatus === 'error') {
      return '草稿保存失败';
    }
    if (draftSavedAt) {
      return `已保存 ${formatDraftSavedAt(draftSavedAt)}`;
    }
    return '';
  }, [draftSavedAt, draftSaveStatus]);

  const persistProjectCreateDraft = useCallback((
    nextValues: Partial<ProjectFormValues> = collectProjectCreateValues(),
    nextStep = currentStep,
    nextInstructionConfirmed = instructionConfirmed,
    nextCommitmentConfirmed = commitmentConfirmed,
    nextUploadedMaterials = uploadedMaterials,
    nextCreatedTeam = createdTeam,
  ) => {
    const savedAt = Date.now();
    setDraftSaveStatus('saving');
    if (draftSaveTimerRef.current) {
      window.clearTimeout(draftSaveTimerRef.current);
    }
    void writeProjectCreateDraft({
        currentStep: nextStep,
        instructionConfirmed: nextInstructionConfirmed,
        commitmentConfirmed: nextCommitmentConfirmed,
        savedAt,
        values: {
          ...defaultValuesRef.current,
          ...nextValues,
        },
        uploadedMaterials: nextUploadedMaterials,
        createdTeam: nextCreatedTeam,
      })
      .then(() => {
      draftSaveTimerRef.current = window.setTimeout(() => {
        setDraftSavedAt(savedAt);
        setDraftSaveStatus('saved');
        draftSaveTimerRef.current = undefined;
      }, 500);
      })
      .catch(() => setDraftSaveStatus('error'));
  }, [collectProjectCreateValues, commitmentConfirmed, createdTeam, currentStep, instructionConfirmed, uploadedMaterials]);

  useEffect(() => {
    let cancelled = false;
    void readProjectCreateDraft().then((draft) => {
    if (cancelled) return;
    const draftValues = (draft?.values || {}) as Partial<ProjectFormValues>;
    const projectTeamMembers = draftValues.projectTeamMembers?.length
      ? draftValues.projectTeamMembers
      : mapTeamDraftMembersToProjectMembers(draftValues.teamDraft?.initialMembers);
    const nextValues = {
      ...buildDefaultProjectCreateValues(),
      ...draftValues,
      projectTeamMembers,
    };
    defaultValuesRef.current = nextValues;
    form.resetFields();
    form.setFieldsValue(nextValues);
    setInstructionConfirmed(Boolean(draft?.instructionConfirmed));
    setCommitmentConfirmed(Boolean(draft?.commitmentConfirmed));
    setUploadedMaterials(draft?.uploadedMaterials || []);
    setCreatedTeam(draft?.createdTeam);
    setDraftSavedAt(draft?.savedAt);
    setDraftSaveStatus(draft?.savedAt ? 'saved' : 'idle');
    setDraftHydrated(true);
    }).catch(() => {
      if (!cancelled) {
        setDraftSaveStatus('error');
        setDraftHydrated(true);
      }
    });
    return () => { cancelled = true; };
  }, [form]);

  useEffect(
    () => () => {
      if (draftSaveTimerRef.current) {
        window.clearTimeout(draftSaveTimerRef.current);
      }
    },
    [],
  );

  useEffect(() => {
    let mounted = true;
    setTeamsLoading(true);
    listMyTeams()
      .then((nextTeams) => {
        if (mounted) {
          setTeams(nextTeams);
        }
      })
      .catch((error) => {
        showErrorMessage(error, '团队数据加载失败');
      })
      .finally(() => {
        if (mounted) {
          setTeamsLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (!draftHydrated) {
      return;
    }
    const requestedStep = parseProjectCreateStepFromSearch(location.search);
    const values = collectProjectCreateValues();
    const hasMaterials = materialFiles.length > 0 || uploadedMaterials.length > 0;
    const allowedStep = getAllowedProjectCreateStep(
      requestedStep,
      values,
      instructionConfirmed,
      commitmentConfirmed,
      hasMaterials,
      Boolean(createdProject),
    );
    if (allowedStep !== requestedStep) {
      setCurrentStep(allowedStep);
      history.replace({
        pathname: '/projects/create',
        search: createProjectStepSearch(allowedStep),
      });
      message.warning('请先完成当前步骤');
      return;
    }
    setCurrentStep((currentValue) => (currentValue === requestedStep ? currentValue : requestedStep));
  }, [collectProjectCreateValues, draftHydrated, location.search, instructionConfirmed, commitmentConfirmed, materialFiles.length, uploadedMaterials.length, createdProject]);

  const setProjectCreateStep = (nextStep: number, nextUploadedMaterials = uploadedMaterials) => {
    const normalizedStep = Math.max(0, Math.min(nextStep, projectCreateSteps.length - 1));
    persistProjectCreateDraft(collectProjectCreateValues(), normalizedStep, instructionConfirmed, commitmentConfirmed, nextUploadedMaterials);
    setCurrentStep(normalizedStep);
    history.push({
      pathname: '/projects/create',
      search: createProjectStepSearch(normalizedStep),
    });
  };

  const materialUploadProps: UploadProps = {
    multiple: true,
    accept: DOCUMENT_UPLOAD_ACCEPT,
    fileList: materialFiles,
    beforeUpload: (file) => {
      const validationMessage = validateDocumentUploadFile(file, {
        maxSizeMb: DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB,
      });
      if (validationMessage) {
        message.error(validationMessage);
        return Upload.LIST_IGNORE;
      }
      if (materialFiles.length >= projectMaterialFileLimit) {
        message.warning(`最多上传 ${projectMaterialFileLimit} 个材料文件`);
        return Upload.LIST_IGNORE;
      }
      return false;
    },
    onChange: ({ fileList }) => {
      if (fileList.length > projectMaterialFileLimit) {
        message.warning(`最多上传 ${projectMaterialFileLimit} 个材料文件`);
      }
      const nextFileList = fileList.slice(0, projectMaterialFileLimit);
      setMaterialFiles(nextFileList);
      setUploadedMaterials([]);
      persistProjectCreateDraft(collectProjectCreateValues(), currentStep, instructionConfirmed, commitmentConfirmed, []);
    },
    onRemove: () => {
      setUploadedMaterials([]);
      persistProjectCreateDraft(collectProjectCreateValues(), currentStep, instructionConfirmed, commitmentConfirmed, []);
      return true;
    },
  };

  const uploadPendingMaterials = async () => {
    if (uploadedMaterials.length) {
      return uploadedMaterials;
    }
    if (!materialFiles.length) {
      message.warning('请上传参赛文件材料');
      return [];
    }
    const nativeFiles = materialFiles
      .map((file) => file.originFileObj as File | undefined)
      .filter((file): file is File => Boolean(file));
    if (nativeFiles.length !== materialFiles.length) {
      message.error('材料文件状态异常，请重新选择文件');
      return [];
    }

    const payload = normalizePayload(collectProjectCreateValues());
    const materialRecords: FileObjectRecord[] = [];
    for (const file of nativeFiles) {
      materialRecords.push(await uploadProjectMaterial(file, payload));
    }
    setUploadedMaterials(materialRecords);
    persistProjectCreateDraft(collectProjectCreateValues(), currentStep, instructionConfirmed, commitmentConfirmed, materialRecords);
    return materialRecords;
  };

  const handleTeamModeChange = (mode: ProjectTeamMode) => {
    const nextValues: ProjectFormValues = {
      ...collectProjectCreateValues(),
      teamMode: mode,
      selectedTeamId: undefined,
      teamDraft: mode === 'NEW' ? buildDefaultTeamDraft() : collectProjectCreateValues().teamDraft || buildDefaultTeamDraft(),
      projectTeamMembers: buildDefaultProjectTeamMembers(),
    };
    form.setFieldsValue(nextValues);
    setCreatedTeam(undefined);
    persistProjectCreateDraft(nextValues, currentStep, instructionConfirmed, commitmentConfirmed, uploadedMaterials, undefined);
  };

  const handleExistingTeamSelect = async (teamId?: number) => {
    if (!teamId) {
      const nextValues: ProjectFormValues = {
        ...collectProjectCreateValues(),
        selectedTeamId: undefined,
        teamDraft: buildDefaultTeamDraft(),
        projectTeamMembers: buildDefaultProjectTeamMembers(),
      };
      form.setFieldsValue(nextValues);
      setCreatedTeam(undefined);
      persistProjectCreateDraft(nextValues, currentStep, instructionConfirmed, commitmentConfirmed, uploadedMaterials, undefined);
      return;
    }

    const team = teams.find((item) => item.id === teamId);
    if (!team) {
      message.error('未找到所选团队');
      return;
    }

    setSyncingTeam(true);
    try {
      let members: TeamMemberRecord[] = [];
      try {
        members = await listTeamMembers(teamId);
      } catch (error) {
        showErrorMessage(error, '团队成员加载失败');
      }
      const nextValues: ProjectFormValues = {
        ...collectProjectCreateValues(),
        teamMode: 'EXISTING',
        selectedTeamId: team.id,
        teamDraft: mapExistingTeamToDraft(team, members),
        projectTeamMembers: mapExistingTeamMembersToProjectMembers(members),
      };
      form.setFieldsValue(nextValues);
      setCreatedTeam(team);
      persistProjectCreateDraft(nextValues, currentStep, instructionConfirmed, commitmentConfirmed, uploadedMaterials, team);
    } finally {
      setSyncingTeam(false);
    }
  };

  const goNext = async () => {
    if (currentStep === 0) {
      if (!instructionConfirmed) {
        message.warning('请先确认已阅读申报说明');
        return;
      }
      setProjectCreateStep(1);
      return;
    }
    if (currentStep === 1) {
      if (!commitmentConfirmed) {
        message.warning('请先确认参赛承诺书');
        return;
      }
      setProjectCreateStep(2);
      return;
    }
    if (currentStep === 2) {
      await form.validateFields();
      setProjectCreateStep(3);
      return;
    }
    if (currentStep === 3) {
      await form.validateFields();
      setProjectCreateStep(4);
      return;
    }
    if (currentStep === 4) {
      persistProjectCreateDraft();
      setProjectCreateStep(5);
      return;
    }
    if (currentStep === 5) {
      setSaving(true);
      try {
        const materialRecords = await uploadPendingMaterials();
        if (materialRecords.length) {
          setProjectCreateStep(6, materialRecords);
        }
      } catch (error) {
        showErrorMessage(error, '材料上传失败');
      } finally {
        setSaving(false);
      }
    }
  };

  const submit = async () => {
    const values = collectProjectCreateValues();
    const teamMissingFields = getProjectTeamMissingFields(values);
    if (teamMissingFields.length) {
      message.error(`请先补全：${teamMissingFields[0]}`);
      setProjectCreateStep(2);
      return;
    }
    const missingFields = getProjectCreateMissingFields(values);
    if (missingFields.length) {
      message.error(`请先补全：${missingFields[0]}`);
      setProjectCreateStep(3);
      return;
    }
    setSaving(true);
    try {
      const materialRecords = uploadedMaterials.length ? uploadedMaterials : await uploadPendingMaterials();
      if (!materialRecords.length) {
        setProjectCreateStep(5);
        return;
      }
      let team = createdTeam;
      if (values.teamMode === 'EXISTING') {
        team = team || teams.find((item) => item.id === values.selectedTeamId);
        if (!team) {
          message.error('请选择已有团队');
          setProjectCreateStep(2);
          return;
        }
        setCreatedTeam(team);
        persistProjectCreateDraft(values, currentStep, instructionConfirmed, commitmentConfirmed, materialRecords, team);
      } else if (!team) {
        team = await createTeam(normalizeProjectTeamCreatePayload(values.teamDraft, values.projectTeamMembers));
        setCreatedTeam(team);
        persistProjectCreateDraft(values, currentStep, instructionConfirmed, commitmentConfirmed, materialRecords, team);
      }
      const project = await createProject(normalizePayload(values));
      setCreatedProject(project);
      await clearProjectCreateDraft();
      setDraftSavedAt(undefined);
      setDraftSaveStatus('idle');
      setCurrentStep(7);
      history.push({
        pathname: '/projects/create',
        search: createProjectStepSearch(7),
      });
      message.success('项目已提交');
    } catch (error) {
      showErrorMessage(error, '项目提交失败');
    } finally {
      setSaving(false);
    }
  };

  const handleContinueCreate = () => {
    void clearProjectCreateDraft();
    defaultValuesRef.current = buildDefaultProjectCreateValues();
    form.resetFields();
    form.setFieldsValue(defaultValuesRef.current);
    setInstructionConfirmed(false);
    setCommitmentConfirmed(false);
    setMaterialFiles([]);
    setUploadedMaterials([]);
    setCreatedTeam(undefined);
    setCreatedProject(undefined);
    setDraftSavedAt(undefined);
    setDraftSaveStatus('idle');
    setCurrentStep(0);
    history.push({
      pathname: '/projects/create',
      search: createProjectStepSearch(0),
    });
  };

  const handleProjectPlanUploaded = (file: FileObjectRecord) => {
    const nextUploadedMaterials = [file, ...uploadedMaterials.filter((item) => item.id !== file.id && item.category !== 'AIADC 项目计划书')];
    setUploadedMaterials(nextUploadedMaterials);
    persistProjectCreateDraft(collectProjectCreateValues(), currentStep, instructionConfirmed, commitmentConfirmed, nextUploadedMaterials);
  };

  const renderMaterialList = (files: Array<MaterialUploadFile | FileObjectRecord>) => (
    <List
      size="small"
      dataSource={files}
      locale={{ emptyText: '暂无材料文件' }}
      renderItem={(file) => (
        <List.Item>
          <List.Item.Meta avatar={<FileDoneOutlined />} title={materialFileName(file)} description={materialFileSize(file)} />
        </List.Item>
      )}
    />
  );

  const renderProjectPreview = () => {
    const values = collectProjectCreateValues();
    const files = uploadedMaterials.length ? uploadedMaterials : materialFiles;
    const selectedExistingTeam = values.selectedTeamId ? teams.find((team) => team.id === values.selectedTeamId) || createdTeam : undefined;
    const teamDraft = normalizeProjectTeamCreatePayload(values.teamDraft, values.projectTeamMembers);
    const teamMembers = normalizeProjectTeamMemberRows(values.projectTeamMembers);
    const teamMembersText = teamMembers.length
      ? teamMembers
          .map((member, index) => {
            const meta = [
              member.role ? `角色：${optionLabel(projectTeamMemberRoleOptions, member.role)}` : '',
              member.school ? `所在或毕业院校：${member.school}` : '',
              member.education ? `学历：${member.education}` : '',
              member.major ? `所学专业：${member.major}` : '',
              member.phone ? `手机号码：${member.phone}` : '',
            ]
              .filter(Boolean)
              .join(' / ');
            return `${index + 1}. ${member.name || '-'}${meta ? `（${meta}）` : ''}`;
          })
          .join('\n')
      : '-';
    return (
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Descriptions title="团队组建" bordered column={responsive.isMobile ? 1 : 2} size="middle">
          <Descriptions.Item label="团队来源">{values.teamMode === 'EXISTING' ? '已有团队' : '新建团队'}</Descriptions.Item>
          {selectedExistingTeam ? (
            <Descriptions.Item label="团队编码">{renderTextValue(selectedExistingTeam.teamCode)}</Descriptions.Item>
          ) : null}
          <Descriptions.Item label="团队名称">{renderTextValue(teamDraft.teamName)}</Descriptions.Item>
          <Descriptions.Item label="团队类型">{optionLabel(teamTypeOptions, teamDraft.teamType)}</Descriptions.Item>
          <Descriptions.Item label="团队头像" span={responsive.isMobile ? 1 : 2}>
            {renderTextValue(teamDraft.avatarUrl)}
          </Descriptions.Item>
          <Descriptions.Item label="团队简介" span={responsive.isMobile ? 1 : 2}>
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
              {renderTextValue(teamDraft.description)}
            </Typography.Paragraph>
          </Descriptions.Item>
          <Descriptions.Item label="初始成员" span={responsive.isMobile ? 1 : 2}>
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{teamMembersText}</Typography.Paragraph>
          </Descriptions.Item>
        </Descriptions>
        <Descriptions bordered column={responsive.isMobile ? 1 : 2} size="middle">
          <Descriptions.Item label="项目名称">{renderTextValue(values.title)}</Descriptions.Item>
          <Descriptions.Item label="所在地">{[values.province, values.city].filter(Boolean).join(' / ') || '-'}</Descriptions.Item>
          <Descriptions.Item label="所属领域" span={responsive.isMobile ? 1 : 2}>
            {values.industries?.length ? renderArrayValue(values.industries) : findOptionLabel(categoryOptions, values.category)}
          </Descriptions.Item>
          <Descriptions.Item label="项目 logo" span={responsive.isMobile ? 1 : 2}>
            {renderTextValue(values.imageUrl)}
          </Descriptions.Item>
          <Descriptions.Item label="项目概述" span={responsive.isMobile ? 1 : 2}>
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
              {renderTextValue(values.description)}
            </Typography.Paragraph>
          </Descriptions.Item>
          <Descriptions.Item label="学校科技成果转化">{renderBooleanValue(values.isSchoolTechTransfer)}</Descriptions.Item>
          <Descriptions.Item label="第一完成人或所有人">{renderBooleanValue(values.isFirstCompleterOrOwner)}</Descriptions.Item>
          <Descriptions.Item label="项目进展">{findOptionLabel(projectProgressOptions, values.projectProgress)}</Descriptions.Item>
          <Descriptions.Item label="投融资阶段设置">{findOptionLabel(financingDisclosureOptions, values.financingDisclosure)}</Descriptions.Item>
          <Descriptions.Item label="项目计划书" span={responsive.isMobile ? 1 : 2}>
            {renderTextValue(values.projectPlanFileName)}
          </Descriptions.Item>
          <Descriptions.Item label="已获专利" span={responsive.isMobile ? 1 : 2}>
            {renderTextValue(values.patentSummary)}
          </Descriptions.Item>
          <Descriptions.Item label="论文发表" span={responsive.isMobile ? 1 : 2}>
            {renderTextValue(values.paperSummary)}
          </Descriptions.Item>
          <Descriptions.Item label="项目所获奖项" span={responsive.isMobile ? 1 : 2}>
            {renderTextValue(values.awardSummary)}
          </Descriptions.Item>
          <Descriptions.Item label="软件著作权" span={responsive.isMobile ? 1 : 2}>
            {renderTextValue(values.softwareCopyrightSummary)}
          </Descriptions.Item>
          <Descriptions.Item label="作品著作权" span={responsive.isMobile ? 1 : 2}>
            {renderTextValue(values.workCopyrightSummary)}
          </Descriptions.Item>
          <Descriptions.Item label="注册商标" span={responsive.isMobile ? 1 : 2}>
            {renderTextValue(values.trademarkSummary)}
          </Descriptions.Item>
          <Descriptions.Item label="指导教师">{renderTextValue(values.advisorName)}</Descriptions.Item>
        </Descriptions>
        <div className="project-material-preview">
          <Typography.Title level={5}>参赛文件材料</Typography.Title>
          {renderMaterialList(files)}
        </div>
      </Space>
    );
  };

  if (!actionPermission.can('aiadc:project:create')) {
    return (
      <ManagementPage title="新增项目" extra={<Button onClick={() => history.push('/projects/management')}>返回</Button>}>
        <ManagementPageBody>
          <Alert type="error" showIcon message="暂无新增项目权限" />
        </ManagementPageBody>
      </ManagementPage>
    );
  }

  const resultValues = collectProjectCreateValues();
  const resultTeamActionText = resultValues.teamMode === 'EXISTING' ? '已关联团队' : '已创建团队';

  return (
    <ManagementPage title="新增项目" extra={<Button onClick={() => history.push('/projects/management')}>返回项目管理</Button>}>
      <ManagementPageBody className="project-create-page">
        <Card className="project-create-shell">
          <Steps current={currentStep} items={projectCreateSteps} responsive />
          <div className="project-create-step">
            {currentStep === 0 ? (
              <div className="project-create-terms">
                <Alert type="info" showIcon message="请先阅读项目申报说明，确认后继续填写项目内容。" />
                <div className="project-create-terms__content">
                  <Typography.Title level={3}>项目申报说明</Typography.Title>
                  <Typography.Paragraph>请在新增项目之前确认以下内容：</Typography.Paragraph>
                  <ol>
                    <li>项目名称、分类、负责人和项目简介应真实、准确、完整。</li>
                    <li>参赛材料应围绕项目目标、应用场景、创新价值、团队能力和交付计划展开。</li>
                    <li>如项目涉及第三方数据、开源组件、合作单位或知识产权授权，请确保已取得必要授权。</li>
                    <li>提交后，项目可能用于赛事报名、材料审核和展示相关流程，请在确认上传前完成最终核对。</li>
                  </ol>
                  <Typography.Paragraph>继续创建即表示你已阅读项目申报说明，并同意按平台规范提交项目内容。</Typography.Paragraph>
                </div>
                <Checkbox
                  checked={instructionConfirmed}
                  onChange={(event) => {
                    const nextChecked = event.target.checked;
                    setInstructionConfirmed(nextChecked);
                    persistProjectCreateDraft(collectProjectCreateValues(), currentStep, nextChecked, commitmentConfirmed);
                  }}
                >
                  我已阅读并理解项目申报说明
                </Checkbox>
              </div>
            ) : null}
            {currentStep === 1 ? (
              <div className="project-create-terms">
                <Alert type="warning" showIcon message="请确认参赛承诺书后继续组建团队。" />
                <div className="project-create-terms__content">
                  <Typography.Title level={3}>参赛承诺书</Typography.Title>
                  <Typography.Paragraph>
                    申报方承诺提交信息和材料真实、合法、完整，拥有参赛项目相关权利，并遵守赛事组织方对材料审核、展示和联系沟通的管理要求。
                  </Typography.Paragraph>
                  <ol>
                    <li>不存在冒用、抄袭、虚构成果或侵犯第三方权益的情况。</li>
                    <li>项目材料可用于本次赛事评审、沟通、备案和必要展示。</li>
                    <li>如材料内容发生变化，将及时更新并承担相应责任。</li>
                  </ol>
                </div>
                <Checkbox
                  checked={commitmentConfirmed}
                  onChange={(event) => {
                    const nextChecked = event.target.checked;
                    setCommitmentConfirmed(nextChecked);
                    persistProjectCreateDraft(collectProjectCreateValues(), currentStep, instructionConfirmed, nextChecked);
                  }}
                >
                  我已确认并同意参赛承诺书
                </Checkbox>
              </div>
            ) : null}
            {currentStep === 2 ? (
              <ProjectTeamFormationForm
                form={form}
                teams={teams}
                teamsLoading={teamsLoading}
                syncingTeam={syncingTeam}
                onTeamModeChange={handleTeamModeChange}
                onExistingTeamSelect={handleExistingTeamSelect}
                onDraftChange={() => {
                  const nextValues = collectProjectCreateValues();
                  const nextCreatedTeam = nextValues.teamMode === 'EXISTING' ? createdTeam : undefined;
                  if (nextValues.teamMode !== 'EXISTING') {
                    setCreatedTeam(undefined);
                  }
                  persistProjectCreateDraft(nextValues, currentStep, instructionConfirmed, commitmentConfirmed, uploadedMaterials, nextCreatedTeam);
                }}
              />
            ) : null}
            {currentStep === 3 ? (
              <ProjectApplicationForm
                form={form}
                onDraftChange={() => persistProjectCreateDraft()}
                onProjectPlanUploaded={handleProjectPlanUploaded}
              />
            ) : null}
            {currentStep === 4 ? (
              <ProjectIntellectualPropertyForm form={form} onDraftChange={() => persistProjectCreateDraft()} />
            ) : null}
            {currentStep === 5 ? (
              <Space direction="vertical" size="middle" className="project-material-step">
                <Upload.Dragger {...materialUploadProps}>
                  <p className="ant-upload-drag-icon">
                    <UploadOutlined />
                  </p>
                  <p className="ant-upload-text">点击或拖拽上传参赛文件材料</p>
                  <p className="ant-upload-hint">
                    支持 PDF、Word、Excel、PPT、Markdown 或 TXT，单个文件不超过 {DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB}MB，最多 {projectMaterialFileLimit} 个。
                  </p>
                </Upload.Dragger>
                {uploadedMaterials.length ? (
                  <div className="project-material-preview">
                    <Typography.Title level={5}>已上传材料</Typography.Title>
                    {renderMaterialList(uploadedMaterials)}
                  </div>
                ) : null}
              </Space>
            ) : null}
            {currentStep === 6 ? renderProjectPreview() : null}
            {currentStep === 7 ? (
              <Result
                status="success"
                title="提交成功"
                subTitle={`${resultTeamActionText}${createdTeam?.teamName ? `「${createdTeam.teamName}」` : ''}，项目${createdProject?.title ? `「${createdProject.title}」` : ''}已创建，${uploadedMaterials.length} 个材料文件已上传。`}
                extra={[
                  <Button key="continue" icon={<PlusOutlined />} onClick={handleContinueCreate}>
                    继续新增
                  </Button>,
                  <Button key="done" type="primary" icon={<CheckCircleOutlined />} onClick={() => history.push('/projects/management')}>
                    返回项目管理
                  </Button>,
                ]}
              />
            ) : null}
          </div>
          {currentStep < projectCreateSteps.length - 1 ? (
            <div className="project-create-actions">
              {draftSaveText ? (
                <Typography.Text
                  className="project-create-draft-status"
                  type={draftSaveStatus === 'error' ? 'danger' : draftSaveStatus === 'saving' ? 'warning' : 'secondary'}
                >
                  {draftSaveText}
                </Typography.Text>
              ) : null}
              {currentStep > 0 ? <Button onClick={() => setProjectCreateStep(currentStep - 1)}>上一步</Button> : null}
              {currentStep < 6 ? (
                <Button type="primary" loading={saving} onClick={() => void goNext()}>
                  下一步
                </Button>
              ) : (
                <Button type="primary" loading={saving} onClick={() => void submit()}>
                  确认上传
                </Button>
              )}
            </div>
          ) : null}
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

const ProjectPage = () => {
  const location = useLocation();
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [form] = Form.useForm<ProjectFormValues>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<ProjectRecord>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (location.pathname === '/projects') {
      history.replace('/projects/management');
    }
  }, [location.pathname]);

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingRecord(undefined);
  };

  const openEditDrawer = useCallback((record: ProjectRecord) => {
    setEditingRecord(record);
    form.resetFields();
    form.setFieldsValue({
      code: record.code,
      locale: record.locale || 'zh',
      title: record.title,
      category: record.category || 'competition',
      description: record.description || undefined,
      imageUrl: record.imageUrl || undefined,
      ownerName: record.ownerName || undefined,
      rating: (record.rating as ProjectRating) || 'popular',
      status: (record.status as ProjectStatus) || 'draft',
      sort: record.sort ?? 100,
      tags: record.tags || undefined,
      ctaLabel: record.ctaLabel || undefined,
      ctaHref: record.ctaHref || undefined,
      featured: Boolean(record.featured),
    });
    setDrawerOpen(true);
  }, [form]);

  const saveProject = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editingRecord) {
        await updateProject(editingRecord.id, normalizePayload(values, editingRecord));
        message.success('项目已更新');
      } else {
        await createProject(normalizePayload(values));
        message.success('项目已新增');
      }
      closeDrawer();
      actionRef.current?.reload();
    } catch (error) {
      showErrorMessage(error, '项目保存失败');
    } finally {
      setSaving(false);
    }
  };

  const requestProjects = useCallback(async (params: ProjectTableParams) => {
    const response = await listProjects({
      keyword: typeof params.keyword === 'string' ? params.keyword : undefined,
      category: typeof params.category === 'string' ? params.category : undefined,
      ownerName: typeof params.ownerName === 'string' ? params.ownerName : undefined,
      rating: params.rating,
      status: params.status,
      featured: parseFeaturedFilter(params.featured),
      pageNo: params.current,
      pageSize: params.pageSize,
    });
    return {
      data: response.records,
      total: response.total,
      success: true,
    };
  }, []);

  const columns = useMemo<ProColumns<ProjectRecord>[]>(
    () => [
      {
        title: '项目',
        dataIndex: 'keyword',
        fieldProps: {
          placeholder: '输入项目名称/编码/简介/标签',
        },
        render: (_, record) => (
          <Space direction="vertical" size={0}>
            <Typography.Text strong>{record.title}</Typography.Text>
            <Typography.Text type="secondary">{record.code}</Typography.Text>
          </Space>
        ),
      },
      {
        title: '分类',
        dataIndex: 'category',
        valueType: 'select',
        valueEnum: Object.fromEntries(categoryOptions.map((item) => [item.value, { text: item.label }])),
        width: 128,
        render: (_, record) => categoryOptions.find((item) => item.value === record.category)?.label || record.category || '-',
      },
      {
        title: '负责人',
        dataIndex: 'ownerName',
        ellipsis: true,
        width: 160,
        render: (value) => value || '-',
      },
      {
        title: '推荐',
        dataIndex: 'rating',
        valueType: 'select',
        valueEnum: Object.fromEntries(ratingOptions.map((item) => [item.value, { text: item.label }])),
        width: 100,
        render: (_, record) => (
          <Tag color={record.rating === 'excellent' ? 'gold' : record.rating === 'new' ? 'blue' : 'cyan'}>
            {ratingText[record.rating || 'popular'] || record.rating}
          </Tag>
        ),
      },
      {
        title: '重点',
        dataIndex: 'featured',
        valueType: 'select',
        valueEnum: {
          true: { text: '是' },
          false: { text: '否' },
        },
        width: 90,
        render: (_, record) => (record.featured ? <Tag color="gold">是</Tag> : <Tag>否</Tag>),
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueType: 'select',
        valueEnum: {
          draft: { text: '草稿' },
          published: { text: '已发布' },
        },
        width: 110,
        render: (_, record) => <Tag color={statusColor[record.status || 'draft']}>{statusText[record.status || 'draft'] || record.status}</Tag>,
      },
      {
        title: '排序',
        dataIndex: 'sort',
        search: false,
        width: 80,
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        search: false,
        width: 172,
        render: (value) => value || '-',
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 148,
        align: 'right',
        className: 'saas-table-action-column',
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={actionPermission.buildTableActions([
              {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined />,
                permission: 'aiadc:project:update',
                onClick: () => openEditDrawer(record),
              },
              {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined />,
                permission: 'aiadc:project:delete',
                danger: true,
                onClick: () => {
                  Modal.confirm({
                    title: '确认删除该项目？',
                    content: `删除后项目「${record.title}」不会再出现在项目列表中。`,
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await deleteProject(record.id);
                      message.success('项目已删除');
                      actionRef.current?.reload();
                    },
                  });
                },
              },
            ])}
          />
        ),
      },
    ],
    [actionPermission, openEditDrawer, responsive.isDesktop, responsive.isMobile],
  );
  const searchColumns = useMemo(
    () => columns.filter((column) => column.valueType !== 'option'),
    [columns],
  );

  if (location.pathname === '/projects/create') {
    return <CreateProjectPage />;
  }

  if (location.pathname === '/projects/search') {
    return (
      <ManagementPage title="项目查询">
        <ManagementPageBody>
          <ManagementTable<ProjectRecord>
            rowKey="id"
            columns={searchColumns}
            isMobile={responsive.isMobile}
            scroll={{ x: 1032 }}
            request={requestProjects}
            pagination={{ pageSize: 10, showSizeChanger: true }}
          />
        </ManagementPageBody>
      </ManagementPage>
    );
  }

  return (
    <ManagementPage title="项目管理">
      <ManagementPageBody>
        <ManagementTable<ProjectRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          scroll={{ x: 1180 }}
          request={requestProjects}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      </ManagementPageBody>

      <ManagementDrawer
        title="编辑项目"
        open={drawerOpen}
        onClose={closeDrawer}
        destroyOnHidden
        footerActions={[
          { key: 'cancel', label: '取消', onClick: closeDrawer },
          {
            key: 'save',
            label: '保存',
            type: 'primary',
            loading: saving,
            onClick: () => void saveProject(),
          },
        ]}
      >
        <ProjectForm form={form} />
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default ProjectPage;
