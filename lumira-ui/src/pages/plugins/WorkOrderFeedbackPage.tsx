import { BoldOutlined, ItalicOutlined, OrderedListOutlined, PlusOutlined, UnorderedListOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { Button, Empty, Form, Input, Segmented, Select, Space, Tag, Typography } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { DragEvent, ClipboardEvent as ReactClipboardEvent } from 'react';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import { buildTableRequest, type PagedResponse } from '@/features/table/proTableRequest';
import { request } from '@/services/common/request';
import { message } from '@/theme/antdFeedbackBridge';
import type {
  FileObjectRecord,
  WorkOrderFeedbackPayload,
  WorkOrderFeedbackPriority,
  WorkOrderFeedbackRecord,
  WorkOrderFeedbackStatus,
  WorkOrderFeedbackStatusPayload,
} from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { sanitizeRichText } from '@/security/richTextSanitizer';
import { databaseMessage } from '@/i18n/databaseMessage';
import './WorkOrderFeedbackPage.css';

const t = databaseMessage;

type WorkOrderFormValues = {
  title: string;
  priority: WorkOrderFeedbackPriority;
};

type WorkOrderStatusFormValues = {
  status: WorkOrderFeedbackStatus;
  adminReply?: string;
};

type RichTextEditorProps = {
  value?: string;
  onChange?: (value: string) => void;
  disabled?: boolean;
  placeholder?: string;
};

const PLUGIN_CODE = 'work-order-feedback';

const STATUS_OPTIONS = [
  { label: t('plugin.workOrders.status.pending'), value: 'OPEN' },
  { label: t('plugin.workOrders.status.processing'), value: 'PROCESSING' },
  { label: t('plugin.workOrders.status.resolved'), value: 'RESOLVED' },
  { label: t('plugin.workOrders.status.closed'), value: 'CLOSED' },
] satisfies Array<{ label: string; value: WorkOrderFeedbackStatus }>;

const PRIORITY_OPTIONS = [
  { label: t('plugin.workOrders.priority.low'), value: 'LOW' },
  { label: t('plugin.workOrders.priority.normal'), value: 'NORMAL' },
  { label: t('plugin.workOrders.priority.high'), value: 'HIGH' },
  { label: t('plugin.workOrders.priority.urgent'), value: 'URGENT' },
] satisfies Array<{ label: string; value: WorkOrderFeedbackPriority }>;

const statusColor: Record<WorkOrderFeedbackStatus, string> = {
  OPEN: 'blue',
  PROCESSING: 'gold',
  RESOLVED: 'green',
  CLOSED: 'default',
};

const priorityColor: Record<WorkOrderFeedbackPriority, string> = {
  LOW: 'default',
  NORMAL: 'blue',
  HIGH: 'orange',
  URGENT: 'red',
};

const labelOf = <T extends string>(options: Array<{ label: string; value: T }>, value?: T | null) =>
  options.find((item) => item.value === value)?.label || value || '-';

const uploadRichTextImage = async (file: File) => {
  if (!file.type.startsWith('image/')) {
    message.warning(t('plugin.workOrders.validation.imagesOnly'));
    return null;
  }
  const formData = new FormData();
  formData.append('file', file);
  const record = await request<FileObjectRecord>('/v1/work-order-feedback/images', {
    method: 'POST',
    data: formData,
    ...API_OPTS.NO_REDIRECT,
  });
  return normalizeUploadUrl(record.previewUrl || record.publicUrl);
};

const RichTextEditor = ({ value, onChange, disabled, placeholder }: RichTextEditorProps) => {
  const editorRef = useRef<HTMLDivElement | null>(null);
  const selectionRef = useRef<Range | null>(null);
  const lastValueRef = useRef<string>('');
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    const editor = editorRef.current;
    const nextValue = sanitizeRichText(value);
    if (editor && nextValue !== lastValueRef.current && nextValue !== editor.innerHTML) {
      editor.innerHTML = nextValue;
      lastValueRef.current = nextValue;
    }
  }, [value]);

  const emitChange = useCallback(() => {
    const html = sanitizeRichText(editorRef.current?.innerHTML || '');
    lastValueRef.current = html;
    onChange?.(html);
  }, [onChange]);

  const rememberSelection = useCallback(() => {
    const selection = window.getSelection();
    if (selection && selection.rangeCount > 0) {
      selectionRef.current = selection.getRangeAt(0).cloneRange();
    }
  }, []);

  const restoreSelection = useCallback(() => {
    const editor = editorRef.current;
    const selection = window.getSelection();
    if (!editor || !selection) {
      return;
    }
    editor.focus();
    selection.removeAllRanges();
    if (selectionRef.current) {
      selection.addRange(selectionRef.current);
    }
  }, []);

  const exec = useCallback((command: string) => {
    if (disabled) {
      return;
    }
    restoreSelection();
    document.execCommand(command);
    emitChange();
  }, [disabled, emitChange, restoreSelection]);

  const insertImage = useCallback(async (file: File) => {
    if (disabled) {
      return;
    }
    rememberSelection();
    setUploading(true);
    try {
      const url = await uploadRichTextImage(file);
      if (!url) {
        return;
      }
      restoreSelection();
      const img = document.createElement('img');
      img.src = url;
      img.alt = file.name || 'image';
      img.loading = 'lazy';
      img.style.maxWidth = '100%';
      const selection = window.getSelection();
      const range = selection?.rangeCount ? selection.getRangeAt(0) : null;
      if (range) {
        range.deleteContents();
        range.insertNode(img);
        range.setStartAfter(img);
        range.collapse(true);
        selection?.removeAllRanges();
        selection?.addRange(range);
      } else {
        editorRef.current?.appendChild(img);
      }
      emitChange();
      message.success(t('plugin.workOrders.message.imageUploaded'));
    } finally {
      setUploading(false);
    }
  }, [disabled, emitChange, rememberSelection, restoreSelection]);

  const handlePaste = useCallback((event: ReactClipboardEvent<HTMLDivElement>) => {
    const files = Array.from(event.clipboardData.files).filter((file) => file.type.startsWith('image/'));
    if (!files.length) {
      return;
    }
    event.preventDefault();
    void insertImage(files[0]);
  }, [insertImage]);

  const handleDrop = useCallback((event: DragEvent<HTMLDivElement>) => {
    const file = Array.from(event.dataTransfer.files).find((item) => item.type.startsWith('image/'));
    if (!file) {
      return;
    }
    event.preventDefault();
    void insertImage(file);
  }, [insertImage]);

  return (
    <div className="work-order-rich-editor">
      <div className="work-order-rich-editor-toolbar">
        <Button size="small" icon={<BoldOutlined />} disabled={disabled} onMouseDown={(event) => event.preventDefault()} onClick={() => exec('bold')} />
        <Button size="small" icon={<ItalicOutlined />} disabled={disabled} onMouseDown={(event) => event.preventDefault()} onClick={() => exec('italic')} />
        <Button size="small" icon={<UnorderedListOutlined />} disabled={disabled} onMouseDown={(event) => event.preventDefault()} onClick={() => exec('insertUnorderedList')} />
        <Button size="small" icon={<OrderedListOutlined />} disabled={disabled} onMouseDown={(event) => event.preventDefault()} onClick={() => exec('insertOrderedList')} />
        {uploading ? <Typography.Text type="secondary">{t('plugin.workOrders.message.uploading')}</Typography.Text> : null}
      </div>
      <div
        ref={editorRef}
        className="work-order-rich-editor-body"
        contentEditable={!disabled}
        suppressContentEditableWarning
        data-placeholder={placeholder || t('plugin.workOrders.validation.detailRequired')}
        onInput={emitChange}
        onBlur={emitChange}
        onKeyUp={rememberSelection}
        onMouseUp={rememberSelection}
        onPaste={handlePaste}
        onDrop={handleDrop}
      />
    </div>
  );
};

const RichTextPreview = ({ html }: { html?: string | null }) => {
  const sanitizedHtml = sanitizeRichText(html || '');
  // The DOMPurify boundary is covered by richTextSanitizer.test.ts, including script and event-handler payloads.
  // nosemgrep: typescript.react.security.audit.react-dangerouslysetinnerhtml.react-dangerouslysetinnerhtml
  return <div className="work-order-detail-html" dangerouslySetInnerHTML={{ __html: sanitizedHtml }} />;
};

const WorkOrderFeedbackPage = () => {
  const { initialState } = useInitialStateModel();
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const crud = useCrudPageState<WorkOrderFeedbackRecord>();
  const [form] = Form.useForm<WorkOrderFormValues>();
  const [statusForm] = Form.useForm<WorkOrderStatusFormValues>();
  const [detailHtml, setDetailHtml] = useState('');
  const [saving, setSaving] = useState(false);
  const [statusSaving, setStatusSaving] = useState(false);
  const [scope, setScope] = useState<'mine' | 'admin'>('admin');
  const [detailRecord, setDetailRecord] = useState<WorkOrderFeedbackRecord | null>(null);
  const [statusDrawerOpen, setStatusDrawerOpen] = useState(false);

  const pluginEnabled = Boolean(initialState?.availablePlugins?.some((item) => item.pluginCode === PLUGIN_CODE));
  const canCreate = actionPermission.can('plugin:work-order-feedback:create');
  const canManage = actionPermission.can('plugin:work-order-feedback:manage');
  const tableScope = canManage ? scope : 'mine';

  const openCreate = useCallback(() => {
    crud.drawer.openCreate();
    form.resetFields();
    form.setFieldsValue({ priority: 'NORMAL' });
    setDetailHtml('');
  }, [crud.drawer, form]);

  const openDetail = useCallback(async (record: WorkOrderFeedbackRecord) => {
    const result = await request<WorkOrderFeedbackRecord>(`/v1/work-order-feedback/${record.id}`, {
      method: 'GET',
      params: { scope: tableScope },
      ...API_OPTS.NO_REDIRECT,
    });
    setDetailRecord(result);
  }, [tableScope]);

  const openStatus = useCallback((record: WorkOrderFeedbackRecord) => {
    setDetailRecord(record);
    statusForm.setFieldsValue({ status: record.status, adminReply: record.adminReply || '' });
    setStatusDrawerOpen(true);
  }, [statusForm]);

  const saveWorkOrder = async () => {
    const sanitizedDetail = sanitizeRichText(detailHtml);
    if (!sanitizedDetail.replace(/<[^>]*>/g, '').trim() && !sanitizedDetail.includes('<img')) {
      message.warning(t('plugin.workOrders.validation.descriptionRequired'));
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: WorkOrderFeedbackPayload = {
        title: values.title,
        priority: values.priority,
        detailHtml: sanitizedDetail,
      };
      await request<WorkOrderFeedbackRecord>('/v1/work-order-feedback', {
        method: 'POST',
        data: payload,
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(t('plugin.workOrders.message.submitted'));
      crud.reloadAndCloseEditor();
      setDetailHtml('');
    } finally {
      setSaving(false);
    }
  };

  const saveStatus = async () => {
    if (!detailRecord) {
      return;
    }
    setStatusSaving(true);
    try {
      const values = await statusForm.validateFields();
      const payload: WorkOrderFeedbackStatusPayload = {
        status: values.status,
        adminReply: values.adminReply || null,
      };
      const result = await request<WorkOrderFeedbackRecord>(`/v1/work-order-feedback/${detailRecord.id}/status`, {
        method: 'PATCH',
        data: payload,
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(t('plugin.workOrders.message.statusUpdated'));
      setDetailRecord(result);
      setStatusDrawerOpen(false);
      crud.reloadTable();
    } finally {
      setStatusSaving(false);
    }
  };

  const columns = useMemo<ProColumns<WorkOrderFeedbackRecord>[]>(() => {
    const actionItems = (record: WorkOrderFeedbackRecord): TableActionItem[] => [
      { key: 'detail', label: t('plugin.workOrders.common.details'), onClick: () => void openDetail(record) },
      {
        key: 'status',
        label: t('plugin.workOrders.common.process'),
        disabled: !canManage,
        onClick: () => openStatus(record),
      },
    ];
    return [
      { title: t('plugin.workOrders.field.title'), dataIndex: 'title' },
      {
        title: t('plugin.workOrders.field.status'),
        dataIndex: 'status',
        valueType: 'select',
        valueEnum: Object.fromEntries(STATUS_OPTIONS.map((item) => [item.value, { text: item.label }])),
        render: (_, record) => <Tag color={statusColor[record.status]}>{labelOf(STATUS_OPTIONS, record.status)}</Tag>,
      },
      {
        title: t('plugin.workOrders.field.priority'),
        dataIndex: 'priority',
        valueType: 'select',
        valueEnum: Object.fromEntries(PRIORITY_OPTIONS.map((item) => [item.value, { text: item.label }])),
        render: (_, record) => <Tag color={priorityColor[record.priority]}>{labelOf(PRIORITY_OPTIONS, record.priority)}</Tag>,
      },
      { title: t('plugin.workOrders.field.submitter'), dataIndex: 'submitterName', search: false, hideInTable: tableScope !== 'admin' },
      { title: t('plugin.workOrders.field.updatedAt'), dataIndex: 'updatedAt', search: false, width: 180 },
      {
        title: t('plugin.workOrders.field.actions'),
        valueType: 'option',
        fixed: responsive.isMobile ? undefined : 'right',
        render: (_, record) => <TableActionBar isMobile={responsive.isMobile} items={actionItems(record)} />,
      },
    ];
  }, [canManage, openDetail, openStatus, responsive.isMobile, tableScope]);

  const content = (
    <>
      <ManagementPageBody>
        {!pluginEnabled ? (
          <Empty description={t('plugin.workOrders.disabled')} />
        ) : (
          <ManagementTable<WorkOrderFeedbackRecord, { keyword?: string; status?: string; priority?: string; scope?: string }>
            rowKey="id"
            isMobile={responsive.isMobile}
            actionRef={crud.actionRef}
            columns={columns}
            search={searchConfig}
            options={{ reload: false }}
            params={{ scope: tableScope }}
            toolBarRender={() => [
              ...(canManage
                ? [
                    <Segmented
                      key="scope"
                      size="small"
                      value={scope}
                      options={[
                        { label: t('plugin.workOrders.scope.mine'), value: 'mine' },
                        { label: t('plugin.workOrders.scope.all'), value: 'admin' },
                      ]}
                      onChange={(value) => setScope(value as 'mine' | 'admin')}
                    />,
                  ]
                : []),
              ...buildToolbarButtons([
                { key: 'create', label: <><PlusOutlined /> {t('plugin.workOrders.create.title')}</>, onClick: openCreate, permission: 'plugin:work-order-feedback:create', type: 'primary' },
              ]),
            ]}
            request={buildTableRequest(async (params) => {
              const result = await request<PagedResponse<WorkOrderFeedbackRecord>>('/v1/work-order-feedback', {
                method: 'GET',
                params: { ...params, scope: tableScope },
                ...API_OPTS.NO_REDIRECT,
              });
              return result;
            })}
          />
        )}
      </ManagementPageBody>

      <ManagementDrawer
        title={t('plugin.workOrders.create.title')}
        open={crud.drawer.open}
        onClose={crud.drawer.close}
        footerActions={[
          { key: 'cancel', label: t('plugin.workOrders.common.cancel'), onClick: crud.drawer.close },
          { key: 'save', label: t('plugin.workOrders.common.submit'), type: 'primary', loading: saving, disabled: !canCreate, onClick: () => void saveWorkOrder() },
        ]}
      >
        <Form form={form} layout="vertical" initialValues={{ priority: 'NORMAL' }}>
          <Form.Item name="title" label={t('plugin.workOrders.field.title')} rules={[{ required: true, message: t('plugin.workOrders.validation.titleRequired') }, { max: 160, message: t('plugin.workOrders.validation.titleMaxLength') }]}>
            <Input maxLength={160} showCount />
          </Form.Item>
          <Form.Item name="priority" label={t('plugin.workOrders.field.priority')}>
            <Select options={PRIORITY_OPTIONS} />
          </Form.Item>
          <Form.Item label={t('plugin.workOrders.common.details')} required>
            <RichTextEditor value={detailHtml} onChange={setDetailHtml} placeholder={t('plugin.workOrders.editor.placeholder')} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer
        title={t('plugin.workOrders.detail.title')}
        open={Boolean(detailRecord)}
        onClose={() => setDetailRecord(null)}
        footerActions={[
          { key: 'close', label: t('plugin.workOrders.common.close'), onClick: () => setDetailRecord(null) },
          { key: 'handle', label: t('plugin.workOrders.common.process'), type: 'primary', disabled: !canManage || !detailRecord, onClick: () => detailRecord && openStatus(detailRecord) },
        ]}
      >
        {detailRecord ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Typography.Title level={4}>{detailRecord.title}</Typography.Title>
            <Space wrap>
              <Tag color={statusColor[detailRecord.status]}>{labelOf(STATUS_OPTIONS, detailRecord.status)}</Tag>
              <Tag color={priorityColor[detailRecord.priority]}>{labelOf(PRIORITY_OPTIONS, detailRecord.priority)}</Tag>
              <Typography.Text type="secondary">{detailRecord.submitterName}</Typography.Text>
              <Typography.Text type="secondary">{detailRecord.createdAt}</Typography.Text>
            </Space>
            <RichTextPreview html={detailRecord.detailHtml} />
            {detailRecord.adminReply ? (
              <div>
                <Typography.Text strong>{t('plugin.workOrders.field.reply')}</Typography.Text>
                <Typography.Paragraph style={{ marginTop: 8 }}>{detailRecord.adminReply}</Typography.Paragraph>
              </div>
            ) : null}
          </Space>
        ) : null}
      </ManagementDrawer>

      <ManagementDrawer
        title={t('plugin.workOrders.process.title')}
        open={statusDrawerOpen}
        onClose={() => setStatusDrawerOpen(false)}
        footerActions={[
          { key: 'cancel', label: t('plugin.workOrders.common.cancel'), onClick: () => setStatusDrawerOpen(false) },
          { key: 'save', label: t('plugin.workOrders.common.save'), type: 'primary', loading: statusSaving, disabled: !canManage, onClick: () => void saveStatus() },
        ]}
      >
        <Form form={statusForm} layout="vertical">
          <Form.Item name="status" label={t('plugin.workOrders.field.status')} rules={[{ required: true, message: t('plugin.workOrders.validation.statusRequired') }]}>
            <Select options={STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item name="adminReply" label={t('plugin.workOrders.field.reply')}>
            <Input.TextArea rows={5} maxLength={4000} showCount />
          </Form.Item>
        </Form>
      </ManagementDrawer>
    </>
  );

  return (
    <ManagementPage title={t('plugin.workOrders.page.title')}>
      {content}
    </ManagementPage>
  );
};

export default WorkOrderFeedbackPage;
