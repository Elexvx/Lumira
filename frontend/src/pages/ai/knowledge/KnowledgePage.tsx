import { PlusOutlined } from '@ant-design/icons';
import { Button, Drawer, Empty, Form, Input, Select, Space, Tag, Tabs, Typography, Upload, message, theme } from 'antd';
import { useCallback, useMemo, useRef, useState, type RefObject } from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { adaptPageResult } from '@/features/table/proTableRequest';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { DeleteOutlined, EditOutlined, FileSearchOutlined, ReloadOutlined } from '@ant-design/icons';
import { TableActionBar } from '@/features/table/TableActionBar';
import { request } from '@/services/common/request';
import type { AiKnowledgeBasePayload } from '@/services/ai/types';
import { confirmAction } from '@/utils/confirm';
import { API_OPTS } from '@/utils/errorMessage';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import type { AiKnowledgeBaseRecord, AiKnowledgeDocumentRecord, AiKnowledgeReferenceRecord, PagedResult } from '@/types/api';
import type { FormInstance } from 'antd';
import type { UploadProps } from 'antd';

const SCOPE_TABS = [
  { key: 'ALL', label: '全部可用' },
  { key: 'OWNED', label: '我的知识库' },
  { key: 'SHARED', label: '共享给我' },
  { key: 'TENANT', label: '企业知识库' },
];

const formatNumber = (value?: number | null) => (typeof value === 'number' ? value.toLocaleString() : '0');

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

type UploadRequestOption = Parameters<NonNullable<UploadProps['customRequest']>>[0];

const useKnowledgePageAccess = () => {
  const actionRef = useRef<ActionType>(null);
  const documentActionRef = useRef<ActionType>(null);
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const [selectedKnowledgeBase, setSelectedKnowledgeBase] = useState<AiKnowledgeBaseRecord | null>(null);
  const [searchForm] = Form.useForm<{ query: string }>();
  const [knowledgeBaseForm] = Form.useForm<AiKnowledgeBasePayload>();
  const [activeScope, setActiveScope] = useState('ALL');
  const [knowledgeBaseDrawerOpen, setKnowledgeBaseDrawerOpen] = useState(false);
  const [editingKnowledgeBase, setEditingKnowledgeBase] = useState<AiKnowledgeBaseRecord | null>(null);
  const [knowledgeBaseSaving, setKnowledgeBaseSaving] = useState(false);
  const requestOptions = API_OPTS.NO_REDIRECT;
  const canShareKnowledge = actionPermission.can(['*', 'ai:knowledge:share']);
  const canSaveKnowledgeBase = actionPermission.can(editingKnowledgeBase ? 'ai:knowledge:update' : 'ai:knowledge:create');
  const canEditKnowledge = actionPermission.can('ai:knowledge:update');
  const canDeleteKnowledge = actionPermission.can('ai:knowledge:delete');
  const visibilityOptions = useMemo(
    () => [
      { label: '个人知识库', value: 'PERSONAL' },
      ...(canShareKnowledge ? [{ label: '企业知识库', value: 'TENANT' }] : []),
    ],
    [canShareKnowledge],
  );
  const openCreateDrawer = useCallback(() => {
    setEditingKnowledgeBase(null);
    knowledgeBaseForm.resetFields();
    knowledgeBaseForm.setFieldsValue({ status: 'ENABLED', visibilityScope: 'PERSONAL' });
    setKnowledgeBaseDrawerOpen(true);
  }, [knowledgeBaseForm]);
  const openEditDrawer = useCallback((record: AiKnowledgeBaseRecord) => {
    setEditingKnowledgeBase(record);
    knowledgeBaseForm.setFieldsValue({
      name: record.name,
      description: record.description,
      status: record.status || 'ENABLED',
      visibilityScope: record.visibilityScope || 'PERSONAL',
    });
    setKnowledgeBaseDrawerOpen(true);
  }, [knowledgeBaseForm]);
  const closeDrawer = useCallback(() => {
    setKnowledgeBaseDrawerOpen(false);
    setEditingKnowledgeBase(null);
    setKnowledgeBaseSaving(false);
    knowledgeBaseForm.resetFields();
  }, [knowledgeBaseForm]);
  const saveKnowledgeBase = useCallback(async () => {
    const values = await knowledgeBaseForm.validateFields();
    setKnowledgeBaseSaving(true);
    try {
      if (editingKnowledgeBase) {
        await request(`/ai/knowledge-bases/${editingKnowledgeBase.id}`, {
          method: 'PUT',
          data: values,
          ...requestOptions,
        });
        message.success('知识库已更新');
      } else {
        await request('/ai/knowledge-bases', {
          method: 'POST',
          data: values,
          ...requestOptions,
        });
        message.success('知识库已创建');
      }
      closeDrawer();
      actionRef.current?.reload?.();
    } finally {
      setKnowledgeBaseSaving(false);
    }
  }, [actionRef, closeDrawer, editingKnowledgeBase, knowledgeBaseForm, requestOptions]);
  const deleteKnowledgeBase = useCallback(
    (record: AiKnowledgeBaseRecord, selected: AiKnowledgeBaseRecord | null, setSelected: (next: AiKnowledgeBaseRecord | null) => void) => {
      confirmAction({
        title: '删除知识库',
        content: `确认删除知识库「${record.name}」吗？文档索引也会一并移除。`,
        okText: '确认删除',
        okButtonProps: { danger: true },
        onOk: async () => {
          await request(`/ai/knowledge-bases/${record.id}`, {
            method: 'DELETE',
            ...requestOptions,
          });
          message.success('知识库已删除');
          if (selected?.id === record.id) {
            setSelected(null);
          }
          actionRef.current?.reload?.();
        },
      });
    },
    [actionRef, requestOptions],
  );
  const [uploading, setUploading] = useState(false);
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<AiKnowledgeReferenceRecord[]>([]);
  const uploadDocument = useCallback(async (options: UploadRequestOption) => {
    if (!selectedKnowledgeBase || !(options.file instanceof File)) {
      options.onError?.(new Error('请选择文件'));
      return;
    }
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', options.file);
      await request(`/ai/knowledge-bases/${selectedKnowledgeBase.id}/documents/upload`, {
        method: 'POST',
        headers: {},
        data: formData,
        ...requestOptions,
      });
      options.onSuccess?.({});
      documentActionRef.current?.reload?.();
      actionRef.current?.reload?.();
    } catch (error) {
      options.onError?.(error as Error);
    } finally {
      setUploading(false);
    }
  }, [actionRef, documentActionRef, requestOptions, selectedKnowledgeBase]);
  const reindexDocument = useCallback(async (record: AiKnowledgeDocumentRecord) => {
    if (!selectedKnowledgeBase) {
      return;
    }
    await request(`/ai/knowledge-bases/${selectedKnowledgeBase.id}/documents/${record.id}/reindex`, {
      method: 'POST',
      ...requestOptions,
    });
    documentActionRef.current?.reload?.();
  }, [documentActionRef, requestOptions, selectedKnowledgeBase]);
  const deleteDocument = useCallback((record: AiKnowledgeDocumentRecord) => {
    if (!selectedKnowledgeBase) {
      return;
    }
    confirmAction({
      title: '删除文档',
      content: `确认删除文档「${record.title}」吗？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await request(`/ai/knowledge-bases/${selectedKnowledgeBase.id}/documents/${record.id}`, {
          method: 'DELETE',
          ...requestOptions,
        });
        documentActionRef.current?.reload?.();
        actionRef.current?.reload?.();
      },
    });
  }, [actionRef, documentActionRef, requestOptions, selectedKnowledgeBase]);
  const runSearch = useCallback(async (query: string) => {
    if (!selectedKnowledgeBase) {
      return;
    }
    setSearching(true);
    try {
      const records = await request<AiKnowledgeReferenceRecord[]>('/ai/knowledge-bases/search', {
        method: 'POST',
        data: { query, knowledgeBaseIds: [selectedKnowledgeBase.id], limit: 6 },
        ...requestOptions,
      });
      setSearchResults(records);
    } finally {
      setSearching(false);
    }
  }, [requestOptions, selectedKnowledgeBase]);
  const resetSearchState = useCallback(() => {
    setSearchResults([]);
    searchForm.resetFields();
  }, [searchForm]);
  const handleSelectKnowledgeBase = useCallback(
    (record: AiKnowledgeBaseRecord) => {
      setSelectedKnowledgeBase(record);
      resetSearchState();
    },
    [resetSearchState],
  );
  const handleCloseDocumentDrawer = useCallback(() => {
    setSelectedKnowledgeBase(null);
    resetSearchState();
  }, [resetSearchState]);
  const handleScopeChange = useCallback(
    (key: string) => {
      setActiveScope(key);
      setSelectedKnowledgeBase(null);
      resetSearchState();
    },
    [resetSearchState],
  );
  const requestKnowledgeBase = useCallback(
    async (params: { current?: number; pageSize?: number; scope?: string | number | null; [key: string]: unknown }) => {
      const { current, pageSize, scope, ...rest } = params;
      const requestScope = typeof scope === 'string' ? scope : activeScope;
      const result = await request<PagedResult<AiKnowledgeBaseRecord>>('/ai/knowledge-bases', {
        method: 'GET',
        params: { pageNo: Number(current) || 1, pageSize: Number(pageSize) || 10, scope: requestScope, ...rest },
        ...requestOptions,
      });
      return adaptPageResult(result);
    },
    [activeScope, requestOptions],
  );
  const requestKnowledgeDocuments = useCallback(
    async (params: { current?: number; pageSize?: number }) => {
      if (!selectedKnowledgeBase) {
        return { data: [], success: true, total: 0 };
      }
      const result = await request<PagedResult<AiKnowledgeDocumentRecord>>(
        `/ai/knowledge-bases/${selectedKnowledgeBase.id}/documents`,
        {
          method: 'GET',
          params: { pageNo: Number(params.current) || 1, pageSize: Number(params.pageSize) || 10 },
          ...requestOptions,
        },
      );
      return adaptPageResult(result);
    },
    [requestOptions, selectedKnowledgeBase],
  );

  return {
    actionRef,
    documentActionRef,
    responsive,
    selectedKnowledgeBase,
    setSelectedKnowledgeBase,
    requestOptions,
    canCreateKnowledge: actionPermission.can('ai:knowledge:create'),
    canQueryKnowledge: actionPermission.can('ai:knowledge:query'),
    canReindexDocument: actionPermission.can('ai:knowledge:document:index'),
    canDeleteDocument: actionPermission.can('ai:knowledge:document:delete'),
    canUploadDocument: actionPermission.can('ai:knowledge:document:upload'),
    knowledgeBaseAccess: {
      actionRef,
      form: knowledgeBaseForm,
      drawerOpen: knowledgeBaseDrawerOpen,
      setDrawerOpen: setKnowledgeBaseDrawerOpen,
      editingRecord: editingKnowledgeBase,
      setEditingRecord: setEditingKnowledgeBase,
      saving: knowledgeBaseSaving,
      setSaving: setKnowledgeBaseSaving,
      canSaveKnowledgeBase,
      canEditKnowledge,
      canDeleteKnowledge,
      visibilityOptions,
      openCreateDrawer,
      openEditDrawer,
      closeDrawer,
      saveKnowledgeBase,
      deleteKnowledgeBase,
    },
    documentAccess: {
      uploading,
      setUploading,
      searching,
      setSearching,
      searchResults,
      setSearchResults,
      uploadDocument,
      reindexDocument,
      deleteDocument,
      runSearch,
    },
    selectionAccess: {
      searchForm,
      selectedKnowledgeBase,
      activeScope,
      handleSelectKnowledgeBase,
      handleCloseDocumentDrawer,
      handleScopeChange,
      resetSearchState,
    },
    requestAccess: {
      requestOptions,
      requestKnowledgeBase,
      requestKnowledgeDocuments,
    },
  };
};

const KnowledgeBaseDrawer = ({
  open,
  title,
  form,
  saving,
  canSave,
  visibilityOptions,
  onClose,
  onSave,
}: {
  open: boolean;
  title: string;
  form: FormInstance<AiKnowledgeBasePayload>;
  saving: boolean;
  canSave: boolean;
  visibilityOptions: Array<{ label: string; value: string }>;
  onClose: () => void;
  onSave: () => void;
}) => (
  <ManagementDrawer
    width={STANDARD_DRAWER_WIDTH}
    title={title}
    open={open}
    onClose={onClose}
    destroyOnClose
    footerActions={[
      { key: 'cancel', label: '取消', onClick: onClose },
      { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canSave, onClick: onSave },
    ]}
  >
    <Form form={form} layout="vertical">
      <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入知识库名称' }]}>
        <Input maxLength={128} />
      </Form.Item>
      <Form.Item name="status" label="状态" rules={[{ required: true }]}>
        <Select options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]} />
      </Form.Item>
      <Form.Item name="visibilityScope" label="可见范围" initialValue="PERSONAL">
        <Select options={visibilityOptions} />
      </Form.Item>
      <Form.Item name="description" label="说明">
        <Input.TextArea rows={4} maxLength={1024} />
      </Form.Item>
    </Form>
  </ManagementDrawer>
);

const KnowledgeDocumentDrawer = ({
  isMobile,
  selectedKnowledgeBase,
  uploading,
  searching,
  searchResults,
  searchForm,
  documentActionRef,
  canUploadDocument,
  canQueryKnowledge,
  documentColumns,
  documentRequest,
  onClose,
  onUploadDocument,
  onSearchSubmit,
}: {
  isMobile: boolean;
  selectedKnowledgeBase: AiKnowledgeBaseRecord | null;
  uploading: boolean;
  searching: boolean;
  searchResults: AiKnowledgeReferenceRecord[];
  searchForm: FormInstance<{ query: string }>;
  documentActionRef: RefObject<ActionType | null>;
  canUploadDocument: boolean;
  canQueryKnowledge: boolean;
  documentColumns: ProColumns<AiKnowledgeDocumentRecord>[];
  documentRequest: (params: { current?: number; pageSize?: number }) => Promise<{ data: AiKnowledgeDocumentRecord[]; success: boolean; total: number }>;
  onClose: () => void;
  onUploadDocument: NonNullable<UploadProps['customRequest']>;
  onSearchSubmit: (values: { query: string }) => void;
}) => {
  const { token } = theme.useToken();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);
  return (
    <Drawer
      width={isMobile ? '100%' : STANDARD_DRAWER_WIDTH}
      title={selectedKnowledgeBase ? `知识库文档 / ${selectedKnowledgeBase.name}` : '知识库文档'}
      open={Boolean(selectedKnowledgeBase)}
      onClose={onClose}
      destroyOnHidden
    >
      {selectedKnowledgeBase ? (
        <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
          <Upload.Dragger
            accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md"
            multiple
            showUploadList={false}
            customRequest={onUploadDocument}
            disabled={uploading || !canUploadDocument}
          >
            <p className="ant-upload-text">上传知识库文件</p>
            <p className="ant-upload-hint">上传后会自动解析文本、切片并构建检索索引。</p>
          </Upload.Dragger>

          <ManagementTable<AiKnowledgeDocumentRecord>
            rowKey="id"
            actionRef={documentActionRef}
            columns={documentColumns}
            isMobile={isMobile}
            search={false}
            request={documentRequest}
          />

          <Form form={searchForm} layout="inline" onFinish={onSearchSubmit} style={{ gap: tagWrapGap[0] }}>
              <Form.Item name="query" rules={[{ required: true, message: '请输入检索内容' }]} style={{ flex: 1, minWidth: 'var(--saas-spacing-260)' }}>
                <Input.Search placeholder="测试这个知识库能否检索到答案依据" />
              </Form.Item>
            <Button htmlType="submit" loading={searching} disabled={!canQueryKnowledge}>
              检索测试
            </Button>
          </Form>

          {searchResults.length ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              {searchResults.map((item) => (
                <div
                  key={item.chunkId}
                  style={{ border: `1px solid ${token.colorBorderSecondary}`, borderRadius: 'var(--saas-spacing-6)', padding: sectionGap }}
                >
                  <Space wrap>
                    <Tag color="blue">{item.knowledgeBaseName}</Tag>
                    <Typography.Text strong>{item.documentTitle || item.originalFileName}</Typography.Text>
                    <Typography.Text type="secondary">片段 {Number(item.chunkIndex || 0) + 1}</Typography.Text>
                  </Space>
                  <Typography.Paragraph style={{ marginTop: tagWrapGap[0] }} ellipsis={{ rows: 4, expandable: true }}>
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
  );
};

const buildKnowledgeBaseColumns = ({
  canEditKnowledge,
  canDeleteKnowledge,
  isDesktop,
  isMobile,
  onSelectKnowledgeBase,
  onOpenEditDrawer,
  onDeleteKnowledgeBase,
}: {
  canEditKnowledge: boolean;
  canDeleteKnowledge: boolean;
  isDesktop: boolean;
  isMobile: boolean;
  onSelectKnowledgeBase: (record: AiKnowledgeBaseRecord) => void;
  onOpenEditDrawer: (record: AiKnowledgeBaseRecord) => void;
  onDeleteKnowledgeBase: (record: AiKnowledgeBaseRecord) => void;
}): ProColumns<AiKnowledgeBaseRecord>[] => [
  {
    title: '知识库名称',
    dataIndex: 'name',
    width: 'var(--saas-spacing-220)',
    render: (_, record) => (
      <Button type="link" style={{ padding: 0 }} onClick={() => onSelectKnowledgeBase(record)}>
        {record.name}
      </Button>
    ),
  },
  { title: '状态', dataIndex: 'status', width: 'var(--saas-spacing-100)', valueEnum: { ENABLED: { text: '启用' }, DISABLED: { text: '停用' } }, render: (_, record) => statusTag(record.status) },
  { title: '范围', dataIndex: 'visibilityScope', width: 'var(--saas-spacing-100)', search: false, render: (_, record) => visibilityTag(record.visibilityScope) },
  { title: '文档数', dataIndex: 'documentCount', width: 'var(--saas-spacing-100)', search: false, renderText: formatNumber },
  { title: '切片数', dataIndex: 'chunkCount', width: 'var(--saas-spacing-100)', search: false, renderText: formatNumber },
  { title: '说明', dataIndex: 'description', ellipsis: true, search: false },
  {
    title: '操作',
    valueType: 'option',
    fixed: isDesktop ? 'right' : undefined,
    width: 'var(--saas-spacing-220)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={[
          { key: 'documents', label: '文档', icon: <FileSearchOutlined />, onClick: () => onSelectKnowledgeBase(record) },
          { key: 'edit', label: '编辑', icon: <EditOutlined />, disabled: !canEditKnowledge, onClick: () => onOpenEditDrawer(record) },
          { key: 'delete', label: '删除', danger: true, icon: <DeleteOutlined />, disabled: !canDeleteKnowledge, onClick: () => onDeleteKnowledgeBase(record) },
        ]}
      />
    ),
  },
];

const buildKnowledgeDocumentColumns = ({
  canReindexDocument,
  canDeleteDocument,
  isMobile,
  onReindexDocument,
  onDeleteDocument,
}: {
  canReindexDocument: boolean;
  canDeleteDocument: boolean;
  isMobile: boolean;
  onReindexDocument: (record: AiKnowledgeDocumentRecord) => Promise<void> | void;
  onDeleteDocument: (record: AiKnowledgeDocumentRecord) => void;
}): ProColumns<AiKnowledgeDocumentRecord>[] => [
  { title: '文档', dataIndex: 'title', width: 'var(--saas-spacing-220)', ellipsis: true },
  { title: '格式', dataIndex: 'fileExtension', width: 'var(--saas-spacing-90)', renderText: (value) => String(value || '-').toUpperCase() },
  { title: '状态', dataIndex: 'status', width: 'var(--saas-spacing-100)', render: (_, record) => statusTag(record.status) },
  { title: '字数', dataIndex: 'extractedCharCount', width: 'var(--saas-spacing-100)', renderText: formatNumber },
  { title: '切片', dataIndex: 'chunkCount', width: 'var(--saas-spacing-80)', renderText: formatNumber },
  {
    title: '操作',
    valueType: 'option',
    fixed: 'right',
    width: 'var(--saas-spacing-180)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={[
          { key: 'reindex', label: '重建', icon: <ReloadOutlined />, disabled: !canReindexDocument, onClick: () => onReindexDocument(record) },
          { key: 'delete', label: '删除', danger: true, icon: <DeleteOutlined />, disabled: !canDeleteDocument, onClick: () => onDeleteDocument(record) },
        ]}
      />
    ),
  },
];

const AiKnowledgePage = () => {
  const access = useKnowledgePageAccess();
  const handleDeleteKnowledgeBase = useCallback((record: AiKnowledgeBaseRecord) => {
    access.knowledgeBaseAccess.deleteKnowledgeBase(record, access.selectedKnowledgeBase, access.setSelectedKnowledgeBase);
  }, [access]);

  const handleSearchSubmit = useCallback(({ query }: { query: string }) => {
    void access.documentAccess.runSearch(query);
  }, [access]);

  const knowledgeBaseColumns = buildKnowledgeBaseColumns({
    canEditKnowledge: access.knowledgeBaseAccess.canEditKnowledge,
    canDeleteKnowledge: access.knowledgeBaseAccess.canDeleteKnowledge,
    isDesktop: access.responsive.isDesktop,
    isMobile: access.responsive.isMobile,
    onSelectKnowledgeBase: access.selectionAccess.handleSelectKnowledgeBase,
    onOpenEditDrawer: access.knowledgeBaseAccess.openEditDrawer,
    onDeleteKnowledgeBase: handleDeleteKnowledgeBase,
  });
  const documentColumns = buildKnowledgeDocumentColumns({
    canReindexDocument: access.canReindexDocument,
    canDeleteDocument: access.canDeleteDocument,
    isMobile: access.responsive.isMobile,
    onReindexDocument: access.documentAccess.reindexDocument,
    onDeleteDocument: access.documentAccess.deleteDocument,
  });
  const { actionRef, documentActionRef, responsive, selectedKnowledgeBase, canCreateKnowledge, canQueryKnowledge, canUploadDocument, requestAccess } = access;
  const { activeScope, searchForm, handleScopeChange, handleCloseDocumentDrawer } = access.selectionAccess;
  const { form, drawerOpen, editingRecord, saving, canSaveKnowledgeBase, visibilityOptions, openCreateDrawer, closeDrawer, saveKnowledgeBase } = access.knowledgeBaseAccess;
  const { uploading, searching, searchResults, uploadDocument } = access.documentAccess;
  const { requestKnowledgeBase, requestKnowledgeDocuments } = requestAccess;

  return (
    <ManagementPage title="知识库" content={null}>
      <Tabs
        activeKey={activeScope}
        items={SCOPE_TABS}
        onChange={handleScopeChange}
      />
      <ManagementTable<AiKnowledgeBaseRecord>
        rowKey="id"
        actionRef={actionRef}
        columns={knowledgeBaseColumns}
        isMobile={responsive.isMobile}
        params={{ scope: activeScope }}
        request={requestKnowledgeBase}
        toolbar={{
          actions: [
            <Button key="create" type="primary" icon={<PlusOutlined />} disabled={!canCreateKnowledge} onClick={openCreateDrawer}>
              新建知识库
            </Button>,
          ],
        }}
      />

      <KnowledgeBaseDrawer
        open={drawerOpen}
        title={editingRecord ? '编辑知识库' : '新建知识库'}
        form={form}
        saving={saving}
        canSave={canSaveKnowledgeBase}
        visibilityOptions={visibilityOptions}
        onClose={closeDrawer}
        onSave={() => void saveKnowledgeBase()}
      />

      <KnowledgeDocumentDrawer
        isMobile={responsive.isMobile}
        selectedKnowledgeBase={selectedKnowledgeBase}
        uploading={uploading}
        searching={searching}
        searchResults={searchResults}
        searchForm={searchForm}
        documentActionRef={documentActionRef}
        canUploadDocument={canUploadDocument}
        canQueryKnowledge={canQueryKnowledge}
        documentColumns={documentColumns}
        documentRequest={requestKnowledgeDocuments}
        onClose={handleCloseDocumentDrawer}
        onUploadDocument={(options) => void uploadDocument(options)}
        onSearchSubmit={handleSearchSubmit}
      />
    </ManagementPage>
  );
};

export default AiKnowledgePage;
