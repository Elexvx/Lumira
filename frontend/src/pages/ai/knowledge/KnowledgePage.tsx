import { PlusOutlined } from '@ant-design/icons';
import { Button, Drawer, Empty, Form, Input, Select, Space, Tag, Tabs, Typography, Upload, theme } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { useCallback, useMemo, useRef, useState, type RefObject } from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { useConfirmableDrawerClose } from '@/features/management/drawerCloseConfirm';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
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
import { API_OPTS, extractErrorMessage } from '@/utils/errorMessage';
import { DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB, DOCUMENT_UPLOAD_ACCEPT, validateDocumentUploadFile } from '@/utils/uploadValidation';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import type { AiKnowledgeBaseRecord, AiKnowledgeDocumentRecord, AiKnowledgeReferenceRecord, PagedResult } from '@/types/api';
import type { FormInstance } from 'antd';
import type { UploadProps } from 'antd';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const SCOPE_TABS = [
  { key: 'ALL', label: t('全部可用', 'All available') },
  { key: 'OWNED', label: t('我的知识库', 'My knowledge bases') },
  { key: 'SHARED', label: t('共享给我', 'Shared with me') },
  { key: 'TENANT', label: t('企业知识库', 'Tenant knowledge bases') },
];

const formatNumber = (value?: number | null) => (typeof value === 'number' ? value.toLocaleString() : '0');

const visibilityTag = (scope?: string | null) => {
  if (scope === 'TENANT') {
    return <Tag color="purple">{t('企业', 'Tenant')}</Tag>;
  }
  if (scope === 'TEAM') {
    return <Tag color="blue">{t('团队', 'Team')}</Tag>;
  }
  return <Tag color="green">{t('个人', 'Personal')}</Tag>;
};

const statusTag = (status?: string | null) => {
  if (status === 'READY') {
    return <Tag color="green">{t('已索引', 'Indexed')}</Tag>;
  }
  if (status === 'INDEXING') {
    return <Tag color="processing">{t('索引中', 'Indexing')}</Tag>;
  }
  if (status === 'FAILED') {
    return <Tag color="red">{t('失败', 'Failed')}</Tag>;
  }
  if (status === 'DISABLED') {
    return <Tag>{t('停用', 'Disabled')}</Tag>;
  }
  return <Tag color="green">{t('启用', 'Enabled')}</Tag>;
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
      { label: t('个人知识库', 'Personal knowledge base'), value: 'PERSONAL' },
      ...(canShareKnowledge ? [{ label: t('企业知识库', 'Tenant knowledge base'), value: 'TENANT' }] : []),
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
        message.success(t('知识库已更新', 'Knowledge base updated'));
      } else {
        await request('/ai/knowledge-bases', {
          method: 'POST',
          data: values,
          ...requestOptions,
        });
        message.success(t('知识库已创建', 'Knowledge base created'));
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
        title: t('删除知识库', 'Delete knowledge base'),
        content: t(`确认删除知识库「${record.name}」吗？文档索引也会一并移除。`, `Delete knowledge base "${record.name}"? Document indexes will also be removed.`),
        okText: t('确认删除', 'Delete'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request(`/ai/knowledge-bases/${record.id}`, {
            method: 'DELETE',
            ...requestOptions,
          });
          message.success(t('知识库已删除', 'Knowledge base deleted'));
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
      options.onError?.(new Error(t('请选择文件', 'Please select a file')));
      return;
    }
    const validationMessage = validateDocumentUploadFile(options.file, {
      maxSizeMb: DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB,
    });
    if (validationMessage) {
      message.warning(validationMessage);
      options.onError?.(new Error(validationMessage));
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
        silent: true,
      });
      options.onSuccess?.({});
      documentActionRef.current?.reload?.();
      actionRef.current?.reload?.();
    } catch (error) {
      message.warning(extractErrorMessage(error, t('知识库文件上传失败，请稍后重试', 'Knowledge base document upload failed. Please try again later.')));
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
      title: t('删除文档', 'Delete document'),
      content: t(`确认删除文档「${record.title}」吗？`, `Delete document "${record.title}"?`),
      okText: t('确认删除', 'Delete'),
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
      { key: 'cancel', label: t('取消', 'Cancel'), onClick: onClose },
      { key: 'save', label: t('保存', 'Save'), type: 'primary', loading: saving, disabled: !canSave, onClick: onSave },
    ]}
  >
    <Form form={form} layout="vertical">
      <Form.Item name="name" label={t('名称', 'Name')} rules={[{ required: true, message: t('请输入知识库名称', 'Please enter the knowledge base name') }]}>
        <Input maxLength={128} />
      </Form.Item>
      <Form.Item name="status" label={t('状态', 'Status')} rules={[{ required: true }]}>
        <Select options={[{ value: 'ENABLED', label: t('启用', 'Enabled') }, { value: 'DISABLED', label: t('停用', 'Disabled') }]} />
      </Form.Item>
      <Form.Item name="visibilityScope" label={t('可见范围', 'Visibility scope')} initialValue="PERSONAL">
        <Select options={visibilityOptions} />
      </Form.Item>
      <Form.Item name="description" label={t('说明', 'Description')}>
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
  const handleDrawerClose = useConfirmableDrawerClose(onClose);
  return (
    <Drawer
      width={isMobile ? '100%' : STANDARD_DRAWER_WIDTH}
      title={selectedKnowledgeBase ? `${t('知识库文档', 'Knowledge base documents')} / ${selectedKnowledgeBase.name}` : t('知识库文档', 'Knowledge base documents')}
      open={Boolean(selectedKnowledgeBase)}
      onClose={handleDrawerClose}
      destroyOnHidden
    >
      {selectedKnowledgeBase ? (
        <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
          <Upload.Dragger
            accept={DOCUMENT_UPLOAD_ACCEPT}
            multiple
            showUploadList={false}
            customRequest={onUploadDocument}
            disabled={uploading || !canUploadDocument}
          >
            <p className="ant-upload-text">{t('上传知识库文件', 'Upload knowledge base files')}</p>
            <p className="ant-upload-hint">{t('上传后会自动解析文本、切片并构建检索索引。', 'Uploaded files will be parsed, chunked, and indexed automatically.')}</p>
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
              <Form.Item name="query" rules={[{ required: true, message: t('请输入检索内容', 'Please enter a search query') }]} style={{ flex: 1, minWidth: 'var(--saas-spacing-260)' }}>
                <Input.Search placeholder={t('测试这个知识库能否检索到答案依据', 'Test whether this knowledge base can retrieve supporting evidence')} />
              </Form.Item>
            <Button htmlType="submit" loading={searching} disabled={!canQueryKnowledge}>
              {t('检索测试', 'Search test')}
            </Button>
          </Form>

          {searchResults.length ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              {searchResults.map((item) => (
                <div
                  key={item.chunkId}
                  style={{
                    border: `1px solid ${token.colorBorderSecondary}`,
                    borderRadius: 'var(--saas-border-radius-base)',
                    padding: sectionGap,
                  }}
                >
                  <Space wrap>
                    <Tag color="blue">{item.knowledgeBaseName}</Tag>
                    <Typography.Text strong>{item.documentTitle || item.originalFileName}</Typography.Text>
                    <Typography.Text type="secondary">{t('片段', 'Chunk')} {Number(item.chunkIndex || 0) + 1}</Typography.Text>
                  </Space>
                  <Typography.Paragraph style={{ marginTop: tagWrapGap[0] }} ellipsis={{ rows: 4, expandable: true }}>
                    {item.content}
                  </Typography.Paragraph>
                </div>
              ))}
            </Space>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('暂无检索结果', 'No search results')} />
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
    title: t('知识库名称', 'Knowledge base name'),
    dataIndex: 'name',
    width: 'var(--saas-spacing-220)',
    render: (_, record) => (
      <Button type="link" style={{ padding: 0 }} onClick={() => onSelectKnowledgeBase(record)}>
        {record.name}
      </Button>
    ),
  },
  { title: t('状态', 'Status'), dataIndex: 'status', width: 'var(--saas-spacing-100)', valueEnum: { ENABLED: { text: t('启用', 'Enabled') }, DISABLED: { text: t('停用', 'Disabled') } }, render: (_, record) => statusTag(record.status) },
  { title: t('范围', 'Scope'), dataIndex: 'visibilityScope', width: 'var(--saas-spacing-100)', search: false, render: (_, record) => visibilityTag(record.visibilityScope) },
  { title: t('文档数', 'Document count'), dataIndex: 'documentCount', width: 'var(--saas-spacing-100)', search: false, renderText: formatNumber },
  { title: t('切片数', 'Chunk count'), dataIndex: 'chunkCount', width: 'var(--saas-spacing-100)', search: false, renderText: formatNumber },
  { title: t('说明', 'Description'), dataIndex: 'description', ellipsis: true, search: false },
  {
    title: t('操作', 'Actions'),
    valueType: 'option',
    fixed: isDesktop ? 'right' : undefined,
    width: 'var(--saas-spacing-220)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={[
          { key: 'documents', label: t('文档', 'Documents'), icon: <FileSearchOutlined />, onClick: () => onSelectKnowledgeBase(record) },
          { key: 'edit', label: t('编辑', 'Edit'), icon: <EditOutlined />, disabled: !canEditKnowledge, onClick: () => onOpenEditDrawer(record) },
          { key: 'delete', label: t('删除', 'Delete'), danger: true, icon: <DeleteOutlined />, disabled: !canDeleteKnowledge, onClick: () => onDeleteKnowledgeBase(record) },
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
  { title: t('文档', 'Document'), dataIndex: 'title', width: 'var(--saas-spacing-220)', ellipsis: true },
  { title: t('格式', 'Format'), dataIndex: 'fileExtension', width: 'var(--saas-spacing-90)', renderText: (value) => String(value || '-').toUpperCase() },
  { title: t('状态', 'Status'), dataIndex: 'status', width: 'var(--saas-spacing-100)', render: (_, record) => statusTag(record.status) },
  { title: t('字数', 'Characters'), dataIndex: 'extractedCharCount', width: 'var(--saas-spacing-100)', renderText: formatNumber },
  { title: t('切片', 'Chunks'), dataIndex: 'chunkCount', width: 'var(--saas-spacing-80)', renderText: formatNumber },
  {
    title: t('操作', 'Actions'),
    valueType: 'option',
    fixed: 'right',
    width: 'var(--saas-spacing-180)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={[
          { key: 'reindex', label: t('重建', 'Reindex'), icon: <ReloadOutlined />, disabled: !canReindexDocument, onClick: () => onReindexDocument(record) },
          { key: 'delete', label: t('删除', 'Delete'), danger: true, icon: <DeleteOutlined />, disabled: !canDeleteDocument, onClick: () => onDeleteDocument(record) },
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
    <ManagementPage title={t('知识库', 'Knowledge base')} content={null}>
      <ManagementPageBody>
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
                {t('新建知识库', 'Create knowledge base')}
              </Button>,
            ],
          }}
        />
      </ManagementPageBody>

      <KnowledgeBaseDrawer
        open={drawerOpen}
        title={editingRecord ? t('编辑知识库', 'Edit knowledge base') : t('新建知识库', 'Create knowledge base')}
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
