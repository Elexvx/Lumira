import { ExperimentOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Checkbox, Col, Descriptions, Empty, Form, Input, Row, Segmented, Select, Space, Spin, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { formatMessage, history } from '@umijs/max';
import { useEffect, useMemo, useState } from 'react';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { ManagementDrawer, ManagementPage, ManagementPageBody } from '@/features/management';
import { systemService } from '@/services/system';
import type { PlatformModuleRecord, PlatformModuleType, PlatformModuleValidationPayload, PlatformModuleValidationResult } from '@/types/api';

const MODULE_TYPE_LABELS: Record<PlatformModuleType, string> = {
  FOUNDATION: '底座',
  CAPABILITY: '能力',
  SCENE: '场景',
  ADAPTER: '适配器',
  PLUGIN: '插件',
};

const MODULE_TYPE_COLORS: Record<PlatformModuleType, string> = {
  FOUNDATION: 'blue',
  CAPABILITY: 'geekblue',
  SCENE: 'purple',
  ADAPTER: 'cyan',
  PLUGIN: 'gold',
};

const MODULE_STATUS_COLORS: Record<string, string> = {
  ENABLED: 'green',
  DISABLED: 'default',
  PLANNED: 'orange',
  DEPRECATED: 'red',
};

const MODULE_SOURCE_LABELS: Record<string, string> = {
  BUILTIN: '内置',
  DATABASE: '数据库',
  PLUGIN: '插件',
  MANIFEST: '清单',
};

const MODULE_SOURCE_COLORS: Record<string, string> = {
  BUILTIN: 'blue',
  DATABASE: 'green',
  PLUGIN: 'gold',
  MANIFEST: 'cyan',
};

const FILTER_OPTIONS: Array<{ label: string; value: PlatformModuleType | 'ALL' }> = [
  { label: '全部', value: 'ALL' },
  { label: '底座', value: 'FOUNDATION' },
  { label: '能力', value: 'CAPABILITY' },
  { label: '场景', value: 'SCENE' },
  { label: '适配器', value: 'ADAPTER' },
  { label: '插件', value: 'PLUGIN' },
];

const MODULE_TYPE_OPTIONS = Object.entries(MODULE_TYPE_LABELS).map(([value, label]) => ({ label, value }));
const MODULE_STATUS_OPTIONS = Object.keys(MODULE_STATUS_COLORS).map((value) => ({ label: value, value }));
const MODULE_SOURCE_OPTIONS = Object.entries(MODULE_SOURCE_LABELS).map(([value, label]) => ({ label, value }));

type ModuleValidationFormValues = Omit<PlatformModuleValidationPayload, 'apiPrefixes' | 'permissionKeys' | 'dependencies'> & {
  apiPrefixesText?: string;
  permissionKeysText?: string;
  dependenciesText?: string;
};

const renderListTags = (items: string[], empty = '-') => {
  if (!items?.length) {
    return <Typography.Text type="secondary">{empty}</Typography.Text>;
  }
  return (
    <Space size={[4, 4]} wrap>
      {items.map((item) => (
        <Tag key={item}>{item}</Tag>
      ))}
    </Space>
  );
};

const splitLines = (value?: string) =>
  (value || '')
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);

const ModulesPage = () => {
  const [form] = Form.useForm<ModuleValidationFormValues>();
  const [modules, setModules] = useState<PlatformModuleRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeType, setActiveType] = useState<PlatformModuleType | 'ALL'>('ALL');
  const [activeSource, setActiveSource] = useState<string>('ALL');
  const [selectedModule, setSelectedModule] = useState<PlatformModuleRecord | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [validationOpen, setValidationOpen] = useState(false);
  const [validationLoading, setValidationLoading] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [validationResult, setValidationResult] = useState<PlatformModuleValidationResult | null>(null);
  const detailDescriptionsProps = useDetailDescriptionsProps({ column: 1 });

  const loadModules = async () => {
    setLoading(true);
    try {
      setModules(await systemService.modules({ autoRedirectOnUnauthorized: false }));
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '模块清单加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadModules();
  }, []);

  const openModuleDetail = async (record: PlatformModuleRecord) => {
    setSelectedModule(record);
    setDetailLoading(true);
    try {
      setSelectedModule(await systemService.module(record.moduleCode, { autoRedirectOnUnauthorized: false }));
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '模块详情加载失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const openValidationDrawer = () => {
    setValidationResult(null);
    setValidationOpen(true);
    form.setFieldsValue({
      moduleType: 'SCENE',
      lifecycleStatus: 'PLANNED',
      sourceType: 'DATABASE',
      ownerService: 'system-service',
      overwriteExisting: false,
    });
  };

  const buildValidationPayload = (values: ModuleValidationFormValues): PlatformModuleValidationPayload => ({
    moduleCode: values.moduleCode,
    moduleName: values.moduleName,
    moduleType: values.moduleType,
    lifecycleStatus: values.lifecycleStatus,
    sourceType: values.sourceType,
    description: values.description,
    ownerService: values.ownerService,
    adminRoutePath: values.adminRoutePath,
    apiPrefixes: splitLines(values.apiPrefixesText),
    permissionKeys: splitLines(values.permissionKeysText),
    dependencies: splitLines(values.dependenciesText),
    overwriteExisting: values.overwriteExisting,
  });

  const validateDraft = async () => {
    const values = await form.validateFields();
    setValidationLoading(true);
    try {
      const result = await systemService.validateModule(buildValidationPayload(values), { autoRedirectOnUnauthorized: false });
      setValidationResult(result);
      if (result.valid) {
        message.success('模块草案校验通过');
      } else {
        message.warning('模块草案存在阻塞项');
      }
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '模块草案校验失败');
    } finally {
      setValidationLoading(false);
    }
  };

  const createDraft = async () => {
    const values = await form.validateFields();
    setCreateLoading(true);
    try {
      const payload = buildValidationPayload({
        ...values,
        lifecycleStatus: 'PLANNED',
        sourceType: 'DATABASE',
        overwriteExisting: false,
      });
      await systemService.createModule(payload, { autoRedirectOnUnauthorized: false });
      message.success('数据库模块草案已保存');
      setValidationOpen(false);
      setValidationResult(null);
      form.resetFields();
      await loadModules();
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '数据库模块草案保存失败');
    } finally {
      setCreateLoading(false);
    }
  };

  const summary = useMemo(() => {
    const next = {
      total: modules.length,
      enabled: 0,
      planned: 0,
      database: 0,
      blocked: 0,
      foundation: 0,
      capability: 0,
      scene: 0,
    };
    modules.forEach((item) => {
      if (item.lifecycleStatus === 'ENABLED') {
        next.enabled += 1;
      }
      if (item.lifecycleStatus === 'PLANNED') {
        next.planned += 1;
      }
      if (item.sourceType === 'DATABASE') {
        next.database += 1;
      }
      if (!item.readyToEnable) {
        next.blocked += 1;
      }
      if (item.moduleType === 'FOUNDATION') {
        next.foundation += 1;
      }
      if (item.moduleType === 'CAPABILITY') {
        next.capability += 1;
      }
      if (item.moduleType === 'SCENE') {
        next.scene += 1;
      }
    });
    return next;
  }, [modules]);

  const sourceOptions = useMemo(() => {
    const sources = Array.from(new Set(modules.map((item) => item.sourceType).filter(Boolean)));
    return [
      { label: '全部来源', value: 'ALL' },
      ...sources.map((source) => ({
        label: MODULE_SOURCE_LABELS[source] || source,
        value: source,
      })),
    ];
  }, [modules]);

  const filteredModules = useMemo(
    () =>
      modules.filter((item) => {
        const typeMatched = activeType === 'ALL' || item.moduleType === activeType;
        const sourceMatched = activeSource === 'ALL' || item.sourceType === activeSource;
        return typeMatched && sourceMatched;
      }),
    [activeSource, activeType, modules],
  );

  const columns: ColumnsType<PlatformModuleRecord> = [
    {
      title: '模块',
      dataIndex: 'moduleName',
      width: 220,
      fixed: 'left',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.moduleName}</Typography.Text>
          <Typography.Text type="secondary" copyable={{ text: record.moduleCode }}>
            {record.moduleCode}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'moduleType',
      width: 96,
      render: (value: PlatformModuleType) => <Tag color={MODULE_TYPE_COLORS[value]}>{MODULE_TYPE_LABELS[value] || value}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'lifecycleStatus',
      width: 100,
      render: (value: string) => <Tag color={MODULE_STATUS_COLORS[value] || 'default'}>{value}</Tag>,
    },
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 104,
      render: (value: string) => (
        <Tag color={MODULE_SOURCE_COLORS[value] || 'default'}>{MODULE_SOURCE_LABELS[value] || value || '-'}</Tag>
      ),
    },
    {
      title: '所属服务',
      dataIndex: 'ownerService',
      width: 180,
      render: (value?: string) => value || '-',
    },
    {
      title: '管理入口',
      dataIndex: 'adminRoutePath',
      width: 160,
      render: (value?: string) =>
        value ? (
          <Button type="link" size="small" onClick={() => history.push(value)}>
            {value}
          </Button>
        ) : (
          '-'
        ),
    },
    {
      title: '依赖',
      dataIndex: 'dependencies',
      width: 220,
      render: (items: string[]) => renderListTags(items),
    },
    {
      title: 'API 前缀',
      dataIndex: 'apiPrefixes',
      width: 300,
      render: (items: string[]) => renderListTags(items),
    },
    {
      title: '权限',
      dataIndex: 'permissionKeys',
      width: 120,
      render: (items: string[]) => `${items?.length || 0} 项`,
    },
    {
      title: '依赖健康',
      dataIndex: 'dependencySatisfied',
      width: 112,
      render: (value: boolean, record) => (
        <Tag color={value ? 'green' : 'orange'}>{value ? '正常' : `${record.readinessIssues?.length || 0} 项阻塞`}</Tag>
      ),
    },
    {
      title: '说明',
      dataIndex: 'description',
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 96,
      fixed: 'right',
      render: (_, record) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => void openModuleDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  return (
    <ManagementPage
      title={formatMessage({ id: 'page.modules.title', defaultMessage: '模块中心' })}
      extra={[
        <Button key="validate" icon={<ExperimentOutlined />} onClick={openValidationDrawer}>
          校验草案
        </Button>,
        <Button key="refresh" icon={<ReloadOutlined />} onClick={() => void loadModules()}>
          {formatMessage({ id: 'page.modules.refresh', defaultMessage: '刷新' })}
        </Button>,
      ]}
    >
      <ManagementPageBody>
        <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
          <Col xs={12} md={6}>
            <Card size="small">
              <Typography.Text type="secondary">模块总数</Typography.Text>
              <Typography.Title level={3} style={{ margin: '4px 0 0' }}>{summary.total}</Typography.Title>
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card size="small">
              <Typography.Text type="secondary">已启用</Typography.Text>
              <Typography.Title level={3} style={{ margin: '4px 0 0' }}>{summary.enabled}</Typography.Title>
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card size="small">
              <Typography.Text type="secondary">数据库注册</Typography.Text>
              <Typography.Title level={3} style={{ margin: '4px 0 0' }}>{summary.database}</Typography.Title>
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card size="small">
              <Typography.Text type="secondary">存在阻塞</Typography.Text>
              <Typography.Title level={3} style={{ margin: '4px 0 0' }}>{summary.blocked}</Typography.Title>
            </Card>
          </Col>
        </Row>

        <Card
          size="small"
          title={formatMessage({ id: 'page.modules.inventory', defaultMessage: '模块清单' })}
          extra={
            <Space wrap>
              <Select
                size="small"
                value={activeSource}
                options={sourceOptions}
                style={{ minWidth: 120 }}
                onChange={(value) => setActiveSource(value)}
              />
              <Segmented value={activeType} options={FILTER_OPTIONS} onChange={(value) => setActiveType(value as PlatformModuleType | 'ALL')} />
            </Space>
          }
        >
          <Table<PlatformModuleRecord>
            rowKey="moduleCode"
            columns={columns}
            dataSource={filteredModules}
            loading={loading}
            pagination={false}
            scroll={{ x: 1500 }}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无模块" /> }}
          />
        </Card>

        <ManagementDrawer
          title={selectedModule ? `${selectedModule.moduleName}详情` : '模块详情'}
          open={Boolean(selectedModule)}
          onClose={() => setSelectedModule(null)}
        >
          {selectedModule ? (
            <Spin spinning={detailLoading}>
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              {selectedModule.readinessIssues?.length ? (
                <Alert
                  type={selectedModule.dependencySatisfied ? 'warning' : 'error'}
                  showIcon
                  message="当前模块暂不能直接启用"
                  description={
                    <Space direction="vertical" size={4}>
                      {selectedModule.readinessIssues.map((item) => (
                        <Typography.Text key={item}>{item}</Typography.Text>
                      ))}
                    </Space>
                  }
                />
              ) : (
                <Alert type="success" showIcon message="当前模块依赖完整，可以作为启用候选模块" />
              )}

              <Descriptions {...detailDescriptionsProps}>
                <Descriptions.Item label="模块编码">
                  <Typography.Text copyable={{ text: selectedModule.moduleCode }}>{selectedModule.moduleCode}</Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label="模块名称">{selectedModule.moduleName}</Descriptions.Item>
                <Descriptions.Item label="模块类型">
                  <Tag color={MODULE_TYPE_COLORS[selectedModule.moduleType]}>
                    {MODULE_TYPE_LABELS[selectedModule.moduleType] || selectedModule.moduleType}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="生命周期">
                  <Tag color={MODULE_STATUS_COLORS[selectedModule.lifecycleStatus] || 'default'}>{selectedModule.lifecycleStatus}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="来源">
                  <Tag color={MODULE_SOURCE_COLORS[selectedModule.sourceType] || 'default'}>
                    {MODULE_SOURCE_LABELS[selectedModule.sourceType] || selectedModule.sourceType || '-'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="覆盖内置">
                  <Tag color={selectedModule.overriddenByDatabase ? 'orange' : 'default'}>
                    {selectedModule.overriddenByDatabase ? '是' : '否'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="来源顺序">
                  {renderListTags(selectedModule.registrationSourceOrder, '无')}
                </Descriptions.Item>
                <Descriptions.Item label="注册时间">{selectedModule.registeredAt || '-'}</Descriptions.Item>
                <Descriptions.Item label="内置模块">{selectedModule.builtin ? '是' : '否'}</Descriptions.Item>
                <Descriptions.Item label="依赖满足">
                  <Tag color={selectedModule.dependencySatisfied ? 'green' : 'orange'}>
                    {selectedModule.dependencySatisfied ? '满足' : '未满足'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="启用候选">
                  <Tag color={selectedModule.readyToEnable ? 'green' : 'orange'}>
                    {selectedModule.readyToEnable ? '是' : '否'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="所属服务">{selectedModule.ownerService || '-'}</Descriptions.Item>
                <Descriptions.Item label="管理入口">
                  {selectedModule.adminRoutePath ? (
                    <Button type="link" size="small" onClick={() => history.push(selectedModule.adminRoutePath!)}>
                      {selectedModule.adminRoutePath}
                    </Button>
                  ) : (
                    '-'
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="说明">{selectedModule.description || '-'}</Descriptions.Item>
              </Descriptions>

              <div>
                <Typography.Text strong>依赖模块</Typography.Text>
                <div style={{ marginTop: 8 }}>{renderListTags(selectedModule.dependencies, '无依赖')}</div>
              </div>

              <div>
                <Typography.Text strong>缺失依赖</Typography.Text>
                <div style={{ marginTop: 8 }}>{renderListTags(selectedModule.missingDependencies, '无缺失依赖')}</div>
              </div>

              <div>
                <Typography.Text strong>未启用依赖</Typography.Text>
                <div style={{ marginTop: 8 }}>{renderListTags(selectedModule.inactiveDependencies, '无未启用依赖')}</div>
              </div>

              <div>
                <Typography.Text strong>API 边界</Typography.Text>
                <div style={{ marginTop: 8 }}>{renderListTags(selectedModule.apiPrefixes, '暂无 API 前缀')}</div>
              </div>

              <div>
                <Typography.Text strong>权限声明</Typography.Text>
                <div style={{ marginTop: 8 }}>{renderListTags(selectedModule.permissionKeys, '暂无权限声明')}</div>
              </div>
            </Space>
            </Spin>
          ) : null}
        </ManagementDrawer>

        <ManagementDrawer
          title="模块草案校验"
          open={validationOpen}
          onClose={() => setValidationOpen(false)}
          footerActions={[
            {
              key: 'validate',
              label: '执行校验',
              type: 'primary',
              loading: validationLoading,
              onClick: () => void validateDraft(),
            },
            {
              key: 'create',
              label: '保存草案',
              loading: createLoading,
              onClick: () => void createDraft(),
            },
          ]}
        >
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Alert type="info" showIcon message="当前仅允许保存 DATABASE 来源、PLANNED 生命周期的模块草案，不会启用模块。" />

            <Form<ModuleValidationFormValues> form={form} layout="vertical">
              <Row gutter={12}>
                <Col xs={24} md={12}>
                  <Form.Item name="moduleCode" label="模块编码" rules={[{ required: true, message: '请输入模块编码' }]}>
                    <Input placeholder="journal" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="moduleName" label="模块名称" rules={[{ required: true, message: '请输入模块名称' }]}>
                    <Input placeholder="期刊场景" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="moduleType" label="模块类型" rules={[{ required: true, message: '请选择模块类型' }]}>
                    <Select options={MODULE_TYPE_OPTIONS} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="lifecycleStatus" label="生命周期" rules={[{ required: true, message: '请选择生命周期' }]}>
                    <Select options={MODULE_STATUS_OPTIONS} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="sourceType" label="来源类型">
                    <Select options={MODULE_SOURCE_OPTIONS} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="ownerService" label="所属服务">
                    <Input placeholder="system-service" />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="adminRoutePath" label="管理入口">
                    <Input placeholder="/journal" />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="description" label="说明">
                    <Input.TextArea rows={2} />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="dependenciesText" label="依赖模块">
                    <Input.TextArea rows={3} placeholder={'form\nsubmission\napproval'} />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="apiPrefixesText" label="API 前缀">
                    <Input.TextArea rows={2} placeholder="/api/v1/journal/**" />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="permissionKeysText" label="权限声明">
                    <Input.TextArea rows={3} placeholder={'journal:view\njournal:submission:view'} />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="overwriteExisting" valuePropName="checked">
                    <Checkbox>允许覆盖已有同编码模块</Checkbox>
                  </Form.Item>
                </Col>
              </Row>
            </Form>

            {validationResult ? (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Alert
                  type={validationResult.valid ? 'success' : 'error'}
                  showIcon
                  message={validationResult.valid ? '校验通过' : '校验未通过'}
                  description={validationResult.valid ? '该草案可进入后续写入流程。' : '请先处理阻塞项，再进入真实写入流程。'}
                />
                <div>
                  <Typography.Text strong>阻塞项</Typography.Text>
                  <div style={{ marginTop: 8 }}>{renderListTags(validationResult.issues, '无阻塞项')}</div>
                </div>
                <div>
                  <Typography.Text strong>警告</Typography.Text>
                  <div style={{ marginTop: 8 }}>{renderListTags(validationResult.warnings, '无警告')}</div>
                </div>
                <div>
                  <Typography.Text strong>缺失依赖</Typography.Text>
                  <div style={{ marginTop: 8 }}>{renderListTags(validationResult.missingDependencies, '无缺失依赖')}</div>
                </div>
                <div>
                  <Typography.Text strong>未启用依赖</Typography.Text>
                  <div style={{ marginTop: 8 }}>{renderListTags(validationResult.inactiveDependencies, '无未启用依赖')}</div>
                </div>
                <div>
                  <Typography.Text strong>循环路径</Typography.Text>
                  <div style={{ marginTop: 8 }}>{renderListTags(validationResult.cyclePath, '未检测到循环依赖')}</div>
                </div>
              </Space>
            ) : null}
          </Space>
        </ManagementDrawer>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default ModulesPage;
