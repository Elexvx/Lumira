import {
  ApartmentOutlined,
  AppstoreOutlined,
  ApiOutlined,
  CrownOutlined,
  FolderOpenOutlined,
  RocketOutlined,
  SafetyOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons';
import type { ComponentType, CSSProperties } from 'react';

export type MockIconComponent = ComponentType<{
  className?: string;
  style?: CSSProperties;
}>;

export interface WelcomeProfile {
  greeting: string;
  name: string;
  role: string;
  department: string;
  note: string;
  avatarText: string;
  avatarBackground: string;
}

export interface WelcomeMetric {
  label: string;
  value: string;
  trend: string;
}

export interface WorkbenchProject {
  icon: MockIconComponent;
  name: string;
  description: string;
  team: string;
  updatedAt: string;
}

export interface WorkbenchActivity {
  avatarText: string;
  avatarColor: string;
  userName: string;
  action: string;
  target: string;
  time: string;
}

export interface WorkbenchRadarPoint {
  indicator: string;
  value: number;
}

export interface WorkbenchTeam {
  icon: MockIconComponent;
  name: string;
  description: string;
  members: string;
}

export const welcomeProfile: WelcomeProfile = {
  greeting: '下午好',
  name: '陈宇',
  role: '前端研发',
  department: '平台部',
  note: '今天先处理进行中的项目、团队协作和待接入的快捷入口。',
  avatarText: '陈',
  avatarBackground: 'linear-gradient(135deg, #4f7cff 0%, #33c3f0 100%)',
};

export const welcomeMetrics: WelcomeMetric[] = [
  { label: '项目数', value: '12', trend: '较上周 +2' },
  { label: '团队内排名', value: '3 / 24', trend: '稳定在前 15%' },
  { label: '项目访问', value: '1,284', trend: '近 7 天访问量' },
];

export const workbenchProjects: WorkbenchProject[] = [
  {
    icon: RocketOutlined,
    name: '供应链协同平台',
    description: '用于对接订单、库存和结算的协同工作台。',
    team: '平台部 / 业务中台组',
    updatedAt: '2 小时前',
  },
  {
    icon: FolderOpenOutlined,
    name: '数据资产中心',
    description: '沉淀指标口径、标签资产和数据服务能力。',
    team: '数据平台组',
    updatedAt: '今天 09:40',
  },
  {
    icon: AppstoreOutlined,
    name: '审批流引擎',
    description: '承接跨部门审批、流转配置和节点编排。',
    team: '流程中心',
    updatedAt: '昨天 18:20',
  },
  {
    icon: ThunderboltOutlined,
    name: '增长运营看板',
    description: '活动效果、用户转化和渠道质量的统一视图。',
    team: '增长团队',
    updatedAt: '昨天 16:05',
  },
  {
    icon: SafetyOutlined,
    name: '权限中台改造',
    description: '角色、菜单、数据权限和审计入口的统一治理。',
    team: '安全与权限组',
    updatedAt: '3 天前',
  },
  {
    icon: ApartmentOutlined,
    name: '客户成功 CRM',
    description: '跟进重点客户、服务工单和续约里程碑。',
    team: '客户成功组',
    updatedAt: '本周一',
  },
];

export const workbenchActivities: WorkbenchActivity[] = [
  {
    avatarText: '周',
    avatarColor: '#1677ff',
    userName: '周倩',
    action: '更新了',
    target: '供应链协同平台的版本说明',
    time: '10 分钟前',
  },
  {
    avatarText: '林',
    avatarColor: '#52c41a',
    userName: '林菲',
    action: '补充了',
    target: '数据资产中心的指标口径',
    time: '45 分钟前',
  },
  {
    avatarText: '王',
    avatarColor: '#faad14',
    userName: '王浩',
    action: '确认了',
    target: '审批流引擎的下一步排期',
    time: '2 小时前',
  },
  {
    avatarText: '赵',
    avatarColor: '#722ed1',
    userName: '赵婷',
    action: '完成了',
    target: '权限中台改造的菜单梳理',
    time: '昨天 19:12',
  },
  {
    avatarText: '孙',
    avatarColor: '#eb2f96',
    userName: '孙宁',
    action: '发布了',
    target: '增长运营看板的周报摘要',
    time: '昨天 15:30',
  },
];

export const workbenchRadarData: WorkbenchRadarPoint[] = [
  { indicator: '效率', value: 84 },
  { indicator: '稳定', value: 79 },
  { indicator: '协同', value: 88 },
  { indicator: '交付', value: 74 },
  { indicator: '响应', value: 91 },
  { indicator: '活跃', value: 83 },
];

export const workbenchShortcutSlots = ['预留位 1', '预留位 2', '预留位 3', '预留位 4'];

export const workbenchTeams: WorkbenchTeam[] = [
  {
    icon: TeamOutlined,
    name: '平台研发组',
    description: '负责页面、流程和基础能力的落地。',
    members: '12 人',
  },
  {
    icon: UserSwitchOutlined,
    name: '运营支持组',
    description: '维护常用入口和活动执行节奏。',
    members: '8 人',
  },
  {
    icon: CrownOutlined,
    name: '业务负责人',
    description: '推动关键需求的排期和验收。',
    members: '4 人',
  },
  {
    icon: ApiOutlined,
    name: '接口协作组',
    description: '对接后续真实接口与字段契约。',
    members: '6 人',
  },
];
