import {
  DeleteOutlined,
  EditOutlined,
  FileSearchOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Drawer, Empty, Form, Input, Select, Space, Tabs, Tag, Typography, Upload, message, theme } from 'antd';
import type { UploadProps } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { adaptPageResult } from '@/features/table/proTable';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useResponsive } from '@/hooks/useResponsive';
import { aiService, type AiKnowledgeBasePayload } from '@/services/ai';
import type { AiKnowledgeBaseRecord, AiKnowledgeDocumentRecord, AiKnowledgeReferenceRecord } from '@/types/api';
import { confirmAction } from '@/utils/confirm';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';


const KNOWLEDGE_FILE_ACCEPT = '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.md,.markdown,.txt';
type UploadRequestOption = Parameters<NonNullable<UploadProps['customRequest']>>[0];

const STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
];

const SCOPE_TABS = [
  { key: 'ALL', label: '全部可用' },
  { key: 'OWNED', label: '我的知识库' },
  { key: 'SHARED', label: '共享给我' },
  { key: 'TENANT', label: '企业知识库' },
];

const visibilityTag = (scope?: string | null) => {
  if (scope === 'TENANT') {
    return <Tag color="purple">企业</Tag>;
  }
  if (scope === 'TEAM') {
    return <Tag color="blue">团队</Tag>;
  }
  return <Tag color="green">个人</Tag>;
};

const statusTag = (status?: string | null) => {
  if (status === 'READY') {
    return <Tag color="green">已索引</Tag>;
  }
  if (status === 'INDEXING') {
    return <Tag color="processing">索引中</Tag>;
  }
  if (status === 'FAILED') {
    return <Tag color="red">失败</Tag>;
  }
  if (status === 'DISABLED') {
    return <Tag>停用</Tag>;
  }
  return <Tag color="green">启用</Tag>;
};

const formatNumber = (value?: number | null) => (typeof value === 'number' ? value.toLocaleString() : '0');

const AiKnowledgePage = () => {
  const { token } = theme.useToken();
  const actionRef = useRef<ActionType>(null);
  const documentActionRef = useRef<ActionType>(null);
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const [form] = Form.useForm<AiKnowledgeBasePayload>();
  const [searchForm] = Form.useForm<{ query: string }>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<AiKnowledgeBaseRecord | null>(null);
  const [selectedKnowledgeBase, setSelectedKnowledgeBase] = useState<AiKnowledgeBaseRecord | null>(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<AiKnowledgeReferenceRecord[]>([]);
  const [activeScope, setActiveScope] = useState('ALL');

  const requestOptions = useMemo(() => (API_OPTS.NO_REDIRECT), []);
  const canShareKnowledge = actionPermission.can(['*', 'ai:knowledge:share']);
  const canSaveKnowledgeBase = actionPermission.can(editingRecord ? 'ai:knowledge:update' : 'ai:knowledge:create');
  const canQueryKnowledge = actionPermission.can('ai:knowledge:query');
  const visibilityOptions = useMemo(
    () => [
      { label: '个人知识库', value: 'PERSONAL' },
      ...(canShareKnowledge ? [{ label: '企业知识库', value: 'TENANT' }] : []),
    ],
    [canShareKnowledge],
  );

  const openCreateDrawer = () => {
    setEditingRecord(null);
    form.setFieldsValue({ status: 'ENABLED', visibilityScope: 'PERSONAL' });
    setDrawerOpen(true);
  };

  const openEditDrawer = (record: AiKnowledgeBaseRecord) => {
    setEditingRecord(record);
    form.setFieldsValue({
      name: record.name,
      description: record.description,
      status: record.status || 'ENABLED',
      visibilityScope: record.visibilityScope || 'PERSONAL',
    });
    setDrawerOpen(true);
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingRecord(null);
    setSaving(false);
    form.resetFields();
  };

  const closeDocumentDrawer = () => {
    setSelectedKnowledgeBase(null);
    searchForm.resetFields();
    setSearchResults([]);
  };

  const saveKnowledgeBase = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editingRecord) {
        await aiService.updateKnowledgeBase(editingRecord.id, values, requestOptions);
        message.success('知识库已更新');
      } else {
        await aiService.createKnowledgeBase(values, requestOptions);
        message.success('知识库已创建');
      }
      closeDrawer();
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const deleteKnowledgeBase = (record: AiKnowledgeBaseRecord) => {
    confirmAction({
      title: '删除知识库',
      content: `确认删除知识库「${record.name}」吗？文档索引也会一并移除。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await aiService.deleteKnowledgeBase(record.id, requestOptions);
        message.success('知识库已删除');
        if (selectedKnowledgeBase?.id === record.id) {
          setSelectedKnowledgeBase(null);
        }
        actionRef.current?.reload();
      },
    });
  };

  const uploadDocument = async (options: UploadRequestOption) => {
    if (!selectedKnowledgeBase || !(options.file instanceof File)) {
      options.onError?.(new Error('请选择文件'));
      return;
    }
    setUploading(true);
    try {
      await aiService.uploadKnowledgeDocument(selectedKnowledgeBase.id, options.file, requestOptions);
      message.success('文档已上传并完成索引');
      options.onSuccess?.({});
      documentActionRef.current?.reload();
      actionRef.current?.reload();
    } catch (error) {
      options.onError?.(error as Error);
    } finally {
      setUploading(false);
    }
  };

  const reindexDocument = async (record: AiKnowledgeDocumentRecord) => {
    if (!selectedKnowledgeBase) {
      return;
    }
    await aiService.reindexKnowledgeDocument(selectedKnowledgeBase.id, record.id, requestOptions);
    message.success('索引已重建');
    documentActionRef.current?.reload();
  };

  const deleteDocument = (record: AiKnowledgeDocumentRecord) => {
    if (!selectedKnowledgeBase) {
      return;
    }
    confirmAction({
      title: '删除文档',
      content: `确认删除文档「${record.title}」吗？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await aiService.deleteKnowledgeDocument(selectedKnowledgeBase.id, record.id, requestOptions);
        message.success('文档已删除');
        documentActionRef.current?.reload();
        actionRef.current?.reload();
      },
    });
  };

  const runSearch = async () => {
    if (!selectedKnowledgeBase) {
      return;
    }
    const values = await searchForm.validateFields();
    setSearching(true);
    try {
      const records = await aiService.searchKnowledge(
        { query: values.query, knowledgeBaseIds: [selectedKnowledgeBase.id], limit: 6 },
        requestOptions,
      );
      setSearchResults(records);
      if (!records.length) {
        message.info('未检索到匹配内容');
      }
    } finally {
      setSearching(false);
    }
  };

  const columns: ProColumns<AiKnowledgeBaseRecord>[] = [
    {
      title: '知识库名称',
      dataIndex: 'name',
      width: 220,
      render: (_, record) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => setSelectedKnowledgeBase(record)}>
          {record.name}
        </Button>
      ),
    },
    { title: '状态', dataIndex: 'status', width: 100, valueEnum: { ENABLED: { text: '启用' }, DISABLED: { text: '停用' } }, render: (_, record) => statusTag(record.status) },
    { title: '范围', dataIndex: 'visibilityScope', width: 100, search: false, render: (_, record) => visibilityTag(record.visibilityScope) },
    { title: '文档数', dataIndex: 'documentCount', width: 100, search: false, renderText: formatNumber },
    { title: '切片数', dataIndex: 'chunkCount', width: 100, search: false, renderText: formatNumber },
    { title: '说明', dataIndex: 'description', ellipsis: true, search: false },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 220,
      render: (_, record) => (
        <TableActionBar
          items={[
            { key: 'documents', label: '文档', icon: <FileSearchOutlined />, onClick: () => setSelectedKnowledgeBase(record) },
            { key: 'edit', label: '编辑', icon: <EditOutlined />, disabled: !actionPermission.can('ai:knowledge:update'), onClick: () => openEditDrawer(record) },
            { key: 'delete', label: '删除', danger: true, icon: <DeleteOutlined />, disabled: !actionPermission.can('ai:knowledge:delete'), onClick: () => deleteKnowledgeBase(record) },
          ]}
        />
      ),
    },
  ];

  const documentColumns: ProColumns<AiKnowledgeDocumentRecord>[] = [
    { title: '文档', dataIndex: 'title', width: 220, ellipsis: true },
    { title: '格式', dataIndex: 'fileExtension', width: 90, renderText: (value) => String(value || '-').toUpperCase() },
    { title: '状态', dataIndex: 'status', width: 100, render: (_, record) => statusTag(record.status) },
    { title: '字数', dataIndex: 'extractedCharCount', width: 100, renderText: formatNumber },
    { title: '切片', dataIndex: 'chunkCount', width: 80, renderText: formatNumber },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 180,
      render: (_, record) => (
        <TableActionBar
          items={[
            { key: 'reindex', label: '重建', icon: <ReloadOutlined />, disabled: !actionPermission.can('ai:knowledge:document:index'), onClick: () => void reindexDocument(record) },
            { key: 'delete', label: '删除', danger: true, icon: <DeleteOutlined />, disabled: !actionPermission.can('ai:knowledge:document:delete'), onClick: () => deleteDocument(record) },
          ]}
        />
      ),
    },
  ];

  return (
    <ManagementPage title="知识库" content={null}>
      <Tabs
        activeKey={activeScope}
        items={SCOPE_TABS}
        onChange={(key) => {
          setActiveScope(key);
          setSelectedKnowledgeBase(null);
        }}
      />
      <ManagementTable<AiKnowledgeBaseRecord>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        isMobile={responsive.isMobile}
        params={{ scope: activeScope }}
        request={async (params) => {
          const { current, pageSize, scope, ...rest } = params;
          const requestScope = typeof scope === 'string' ? scope : activeScope;
          const result = await aiService.knowledgeBases(
            { pageNo: Number(current) || 1, pageSize: Number(pageSize) || 10, scope: requestScope, ...rest },
            requestOptions,
          );
          return adaptPageResult(result);
        }}
        toolbar={{
          actions: [
            <Button key="create" type="primary" icon={<PlusOutlined />} disabled={!actionPermission.can('ai:knowledge:create')} onClick={openCreateDrawer}>
              新建知识库
            </Button>,
          ],
        }}
      />

      <ManagementDrawer
        width={STANDARD_DRAWER_WIDTH}
        title={editingRecord ? '编辑知识库' : '新建知识库'}
        open={drawerOpen}
        onClose={closeDrawer}
        destroyOnClose
        footerActions={[
          { key: 'cancel', label: '取消', onClick: closeDrawer },
          { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canSaveKnowledgeBase, onClick: () => void saveKnowledgeBase() },
        ]}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入知识库名称' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select options={STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item name="visibilityScope" label="可见范围" initialValue="PERSONAL">
            <Select options={visibilityOptions} />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={4} maxLength={1024} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <Drawer
        width={responsive.isMobile ? '100%' : STANDARD_DRAWER_WIDTH}
        title={selectedKnowledgeBase ? `知识库文档 / ${selectedKnowledgeBase.name}` : '知识库文档'}
        open={Boolean(selectedKnowledgeBase)}
        onClose={closeDocumentDrawer}
        destroyOnHidden
      >
        {selectedKnowledgeBase ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Upload.Dragger
              accept={KNOWLEDGE_FILE_ACCEPT}
              multiple
              showUploadList={false}
              customRequest={(options) => void uploadDocument(options)}
              disabled={uploading || !actionPermission.can('ai:knowledge:document:upload')}
            >
              <p className="ant-upload-drag-icon"><UploadOutlined /></p>
              <p className="ant-upload-text">上传 Word、Excel、PPT、PDF、Markdown 或 TXT 文件</p>
              <p className="ant-upload-hint">上传后会自动解析文本、切片并构建检索索引。</p>
            </Upload.Dragger>

            <ManagementTable<AiKnowledgeDocumentRecord>
              rowKey="id"
              actionRef={documentActionRef}
              columns={documentColumns}
              isMobile={responsive.isMobile}
              search={false}
              request={async (params) => {
                const result = await aiService.knowledgeDocuments(
                  selectedKnowledgeBase.id,
                  { pageNo: Number(params.current) || 1, pageSize: Number(params.pageSize) || 10 },
                  requestOptions,
                );
                return adaptPageResult(result);
              }}
            />

            <Form form={searchForm} layout="inline" style={{ gap: 8 }}>
              <Form.Item name="query" rules={[{ required: true, message: '请输入检索内容' }]} style={{ flex: 1, minWidth: 260 }}>
                <Input prefix={<SearchOutlined />} placeholder="测试这个知识库能否检索到答案依据" />
              </Form.Item>
              <Button loading={searching} disabled={!canQueryKnowledge} onClick={() => void runSearch()}>
                检索测试
              </Button>
            </Form>

            {searchResults.length ? (
              <Space direction="vertical" style={{ width: '100%' }}>
                {searchResults.map((item) => (
                  <div key={item.chunkId} style={{ border: `1px solid ${token.colorBorderSecondary}`, borderRadius: 6, padding: 12 }}>
                    <Space wrap>
                      <Tag color="blue">{item.knowledgeBaseName}</Tag>
                      <Typography.Text strong>{item.documentTitle || item.originalFileName}</Typography.Text>
                      <Typography.Text type="secondary">片段 {Number(item.chunkIndex || 0) + 1}</Typography.Text>
                    </Space>
                    <Typography.Paragraph style={{ margin: '8px 0 0' }} ellipsis={{ rows: 4, expandable: true }}>
                      {item.content}
                    </Typography.Paragraph>
                  </div>
                ))}
              </Space>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无检索结果" />
            )}
          </Space>
        ) : null}
      </Drawer>
    </ManagementPage>
  );
};

export default AiKnowledgePage;
