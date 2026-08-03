import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  DownloadOutlined,
  FileDoneOutlined,
  FileProtectOutlined,
  LeftOutlined,
  PlusOutlined,
  QrcodeOutlined,
  RightOutlined,
  SaveOutlined,
  SendOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons';
import { history, useParams } from '@umijs/max';
import { Button, Card, Descriptions, Drawer, Form, Image, Input, InputNumber, Modal, Segmented, Select, Space, Table, Tag, Tooltip, Typography, Upload } from 'antd';
import type { UploadProps } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import {
  archiveCertificateTemplate,
  createCertificateTemplate,
  downloadCertificate,
  duplicateCertificateTemplate,
  generateCertificates,
  getCertificateTemplateVersion,
  listCertificateTemplateVersions,
  listCertificateTemplates,
  listCertificates,
  publishCertificateTemplate,
  regenerateCertificate,
  revokeCertificate,
  saveCertificateCanvas,
  updateCertificateTemplate,
  uploadCertificateBackground,
  verifyCertificateByNo,
  verifyCertificateByToken,
} from '@/services/certificates/api';
import type {
  CertificateCanvas,
  CertificateCanvasElement,
  CertificateGeneratePayload,
  CertificatePublicVerifyResult,
  CertificateRecord,
  CertificateTemplateRecord,
  CertificateTemplateVersionRecord,
} from '@/services/certificates/types';
import { message } from '@/theme/antdFeedbackBridge';
import { saveBlobAsFile } from '@/utils/download';
import { showErrorMessage } from '@/utils/errorMessage';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import './certificate.css';

const defaultCanvas: CertificateCanvas = {
  page: { width: 3508, height: 2480, dpi: 300, orientation: 'LANDSCAPE' },
  elements: [
    { id: 'el_name', type: 'text', fieldKey: 'recipientName', x: 1200, y: 920, width: 1100, height: 120, fontSize: 72, fontWeight: 'bold', color: '#222222', textAlign: 'center', placeholder: '${recipientName}' },
    { id: 'el_award', type: 'text', fieldKey: 'awardName', x: 1200, y: 1200, width: 1100, height: 100, fontSize: 56, color: '#222222', textAlign: 'center', placeholder: '${awardName}' },
    { id: 'el_qr', type: 'qrcode', fieldKey: 'verificationUrl', x: 2920, y: 1900, width: 220, height: 220 },
  ],
};

const handleCertificateDownload = async (record: CertificateRecord) => {
  try {
    const blob = await downloadCertificate(record.id);
    saveBlobAsFile(blob, `${record.certificateNo}.png`);
  } catch (error) {
    showErrorMessage(error, '证书下载失败');
  }
};

const customPaperKey = 'CUSTOM';

const paperSizePresets = [
  { label: 'A4', value: 'A4', width: 2480, height: 3508, dpi: 300 },
  { label: 'A3', value: 'A3', width: 3508, height: 4961, dpi: 300 },
  { label: 'A5', value: 'A5', width: 1748, height: 2480, dpi: 300 },
  { label: 'Letter', value: 'LETTER', width: 2550, height: 3300, dpi: 300 },
];

const paperSizeOptions = [
  ...paperSizePresets.map((item) => ({ label: item.label, value: item.value })),
  { label: '自定义', value: customPaperKey },
];

const clampZoom = (value: number) => Math.min(2.5, Math.max(0.45, Number(value.toFixed(2))));

const toPositiveInteger = (value: number | null | undefined, fallback: number) => {
  const next = Number(value);
  return Number.isFinite(next) && next > 0 ? Math.round(next) : fallback;
};

const getOrientedPageSize = (width: number, height: number, orientation: CertificateCanvas['page']['orientation']) => {
  const longSide = Math.max(width, height);
  const shortSide = Math.min(width, height);
  return orientation === 'LANDSCAPE'
    ? { width: longSide, height: shortSide }
    : { width: shortSide, height: longSide };
};

const findPaperPresetValue = (page: CertificateCanvas['page']) => {
  const longSide = Math.max(page.width, page.height);
  const shortSide = Math.min(page.width, page.height);
  const preset = paperSizePresets.find((item) => {
    const presetLong = Math.max(item.width, item.height);
    const presetShort = Math.min(item.width, item.height);
    return Math.abs(presetLong - longSide) <= 2 && Math.abs(presetShort - shortSide) <= 2;
  });
  return preset?.value || customPaperKey;
};

const resizeCanvasPage = (canvas: CertificateCanvas, nextPage: CertificateCanvas['page']): CertificateCanvas => {
  const safeWidth = Math.max(1, nextPage.width);
  const safeHeight = Math.max(1, nextPage.height);
  const scaleX = safeWidth / Math.max(1, canvas.page.width);
  const scaleY = safeHeight / Math.max(1, canvas.page.height);
  const fontScale = Math.min(scaleX, scaleY);
  return {
    ...canvas,
    page: {
      ...nextPage,
      width: safeWidth,
      height: safeHeight,
    },
    elements: canvas.elements.map((element) => ({
      ...element,
      x: Math.round(element.x * scaleX),
      y: Math.round(element.y * scaleY),
      width: Math.max(1, Math.round(element.width * scaleX)),
      height: Math.max(1, Math.round(element.height * scaleY)),
      fontSize: element.fontSize ? Math.max(1, Math.round(element.fontSize * fontScale)) : element.fontSize,
    })),
  };
};

type CertificateReferenceField = {
  key: string;
  label: string;
};

const projectReferenceFields: CertificateReferenceField[] = [
  { key: 'title', label: '项目名称' },
  { key: 'code', label: '项目编码' },
  { key: 'category', label: '所属领域' },
  { key: 'description', label: '项目概述' },
  { key: 'ownerName', label: '负责人' },
  { key: 'tags', label: '项目标签' },
  { key: 'status', label: '项目状态' },
  { key: 'imageUrl', label: '项目图片' },
];

const certificateSystemFields: CertificateReferenceField[] = [
  { key: 'recipientName', label: '获奖人/团队' },
  { key: 'competitionTitle', label: '赛事名称' },
  { key: 'projectName', label: '证书项目名称' },
  { key: 'teamName', label: '团队名称' },
  { key: 'awardName', label: '奖项' },
  { key: 'certificateNo', label: '证书编号' },
  { key: 'participantNo', label: '参赛编号' },
  { key: 'issueDate', label: '发证日期' },
  { key: 'organizer', label: '组织单位' },
  { key: 'verificationUrl', label: '查验链接' },
];

const referenceFields = [...projectReferenceFields, ...certificateSystemFields];

const findReferenceField = (fieldKey?: string) => referenceFields.find((item) => item.key === fieldKey);

const statusColor: Record<string, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  ARCHIVED: 'blue',
  ISSUED: 'green',
  GENERATED: 'processing',
  REVOKED: 'red',
  EXPIRED: 'orange',
};

const _templateStatusText: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
};

const _certificateStatusText: Record<string, string> = {
  GENERATED: '已生成',
  ISSUED: '已签发',
  REVOKED: '已撤销',
  EXPIRED: '已过期',
};

const _sceneTypeText: Record<string, string> = {
  COMPETITION_AWARD: '赛事获奖',
  PARTICIPATION: '参与证明',
  CUSTOM: '自定义',
};

const parseCanvas = (json?: string, version?: CertificateTemplateVersionRecord): CertificateCanvas => {
  let parsed = defaultCanvas;
  if (!json) {
    parsed = defaultCanvas;
  } else {
    try {
      parsed = JSON.parse(json);
    } catch {
      parsed = defaultCanvas;
    }
  }
  return {
    page: {
      width: toPositiveInteger(version?.pageWidth || parsed.page?.width, defaultCanvas.page.width),
      height: toPositiveInteger(version?.pageHeight || parsed.page?.height, defaultCanvas.page.height),
      dpi: toPositiveInteger(version?.dpi || parsed.page?.dpi, defaultCanvas.page.dpi),
      orientation: version?.orientation || parsed.page?.orientation || defaultCanvas.page.orientation,
    },
    elements: Array.isArray(parsed.elements) ? parsed.elements : defaultCanvas.elements,
  };
};

const renderText = (element: CertificateCanvasElement, preview: Record<string, string>) => {
  if (element.fieldKey && preview[element.fieldKey]) {
    return preview[element.fieldKey];
  }
  return element.text || element.placeholder || (element.fieldKey ? `\${${element.fieldKey}}` : 'Text');
};

const csvToRows = (text: string) => {
  const [headerLine, ...lines] = text.split(/\r?\n/).filter(Boolean);
  const headers = headerLine.split(',').map((item) => item.trim());
  return lines.map((line) => {
    const values = line.split(',');
    const data: Record<string, string> = {};
    headers.forEach((header, index) => {
      data[header] = values[index]?.trim() || '';
    });
    return {
      recipientName: data.recipientName || data.name || data.姓名 || '',
      competitionTitle: data.competitionTitle || data.赛事 || '',
      projectName: data.projectName || data.项目 || '',
      teamName: data.teamName || data.团队 || '',
      awardName: data.awardName || data.奖项 || '',
      issueDate: data.issueDate || data.日期 || undefined,
      recipientType: 'CUSTOM',
      data,
    };
  }).filter((item) => item.recipientName);
};

export const TemplatesPage = () => {
  const [records, setRecords] = useState<CertificateTemplateRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<CertificateTemplateRecord | null>(null);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listCertificateTemplates({ pageSize: 100 });
      setRecords(response.records || []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async () => {
    const values = await form.validateFields();
    if (editing) {
      await updateCertificateTemplate(editing.id, values);
    } else {
      await createCertificateTemplate(values);
    }
    message.success('已保存证书模板');
    setOpen(false);
    setEditing(null);
    form.resetFields();
    await load();
  };

  return (
    <div className="certificate-page">
      <div className="certificate-page__header">
        <div>
          <Typography.Title level={3}>证书模板</Typography.Title>
          <Typography.Text type="secondary">管理证书背景、变量版式和发布版本。</Typography.Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>新建模板</Button>
      </div>
      <Table
        rowKey="id"
        loading={loading}
        dataSource={records}
        columns={[
          { title: '模板编码', dataIndex: 'templateCode' },
          { title: '模板名称', dataIndex: 'templateName' },
          { title: '场景', dataIndex: 'sceneType' },
          { title: '最新版本', dataIndex: 'latestVersion' },
          { title: '状态', dataIndex: 'status', render: (value) => <Tag color={statusColor[value as string]}>{value}</Tag> },
          {
            title: '操作',
            render: (_, record) => (
              <Space>
                <Button size="small" onClick={() => history.push(`/certificates/templates/${record.id}/designer`)}>设计</Button>
                <Button size="small" icon={<EditOutlined />} onClick={() => { setEditing(record); form.setFieldsValue(record); setOpen(true); }}>编辑</Button>
                <Button size="small" icon={<SendOutlined />} onClick={async () => { await publishCertificateTemplate(record.id); message.success('已发布模板版本'); await load(); }}>发布</Button>
                <Button size="small" icon={<CopyOutlined />} onClick={async () => { await duplicateCertificateTemplate(record.id); await load(); }}>复制</Button>
                <Button size="small" icon={<DeleteOutlined />} onClick={async () => { await archiveCertificateTemplate(record.id); await load(); }}>归档</Button>
              </Space>
            ),
          },
        ]}
      />
      <Modal title={editing ? '编辑模板' : '新建模板'} open={open} onOk={submit} onCancel={() => { setOpen(false); setEditing(null); }}>
        <Form form={form} layout="vertical">
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="templateCode" label="模板编码">
            <Input placeholder="留空自动生成" />
          </Form.Item>
          <Form.Item name="sceneType" label="场景" initialValue="COMPETITION_AWARD">
            <Select options={[
              { label: '赛事获奖', value: 'COMPETITION_AWARD' },
              { label: '参与证明', value: 'PARTICIPATION' },
              { label: '自定义', value: 'CUSTOM' },
            ]} />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export const DesignerPage = () => {
  const params = useParams();
  const templateId = Number(params.id);
  const [versions, setVersions] = useState<CertificateTemplateVersionRecord[]>([]);
  const [version, setVersion] = useState<CertificateTemplateVersionRecord | null>(null);
  const [canvas, setCanvas] = useState<CertificateCanvas>(defaultCanvas);
  const [selectedId, setSelectedId] = useState('el_name');
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [draggingFieldKey, setDraggingFieldKey] = useState<string | null>(null);
  const [elementContextMenu, setElementContextMenu] = useState<{ elementId: string; x: number; y: number } | null>(null);
  const [zoom, setZoom] = useState(1);
  const [leftPanelCollapsed, setLeftPanelCollapsed] = useState(false);
  const [rightPanelCollapsed, setRightPanelCollapsed] = useState(false);
  const stageRef = useRef<HTMLDivElement | null>(null);
  const stageWrapRef = useRef<HTMLDivElement | null>(null);
  const baseScale = canvas.page.orientation === 'PORTRAIT' ? 0.125 : 0.095;
  const scale = baseScale * zoom;
  const paperPresetValue = findPaperPresetValue(canvas.page);
  const zoomPercent = Math.round(zoom * 100);
  const selected = canvas.elements.find((item) => item.id === selectedId);
  const selectedReferenceField = findReferenceField(selected?.fieldKey);
  const preview = {
    title: '智能评审助手',
    code: 'project-2026-001',
    category: '人工智能',
    description: '面向赛事评审流程的智能辅助项目',
    ownerName: '张三',
    tags: 'AI, 评审, 赛事',
    status: '已发布',
    imageUrl: 'https://example.com/project.png',
    recipientName: '张三团队',
    competitionTitle: 'AI 应用创新赛',
    projectName: '智能评审助手',
    awardName: '一等奖',
    certificateNo: 'CERT-2026-000001',
    issueDate: '2026-06-24',
    verificationUrl: '/certificate/verify/example',
  };

  const load = useCallback(async () => {
    const list = await listCertificateTemplateVersions(templateId);
    setVersions(list);
    const draft = list.find((item) => item.status === 'DRAFT') || list[0];
    if (draft) {
      const detail = await getCertificateTemplateVersion(draft.id);
      setVersion(detail);
      setCanvas(parseCanvas(detail.canvasJson, detail));
    }
  }, [templateId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const handleWheel = (event: WheelEvent) => {
      const stageWrap = stageWrapRef.current;
      if (!stageWrap) return;
      if (!event.ctrlKey) return;
      const rect = stageWrap.getBoundingClientRect();
      const isInsideStage =
        event.clientX >= rect.left &&
        event.clientX <= rect.right &&
        event.clientY >= rect.top &&
        event.clientY <= rect.bottom;
      if (!isInsideStage) return;
      event.preventDefault();
      setZoom((prev) => clampZoom(prev + (event.deltaY > 0 ? -0.08 : 0.08)));
    };
    document.addEventListener('wheel', handleWheel, { passive: false, capture: true });
    return () => document.removeEventListener('wheel', handleWheel, true);
  }, []);

  useEffect(() => {
    if (!elementContextMenu) return undefined;
    const closeMenu = () => setElementContextMenu(null);
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        closeMenu();
      }
    };
    document.addEventListener('mousedown', closeMenu);
    document.addEventListener('scroll', closeMenu, true);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeMenu);
      document.removeEventListener('scroll', closeMenu, true);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [elementContextMenu]);

  const updateElement = (patch: Partial<CertificateCanvasElement>) => {
    if (!selected) return;
    setCanvas((prev) => ({
      ...prev,
      elements: prev.elements.map((item) => item.id === selected.id ? { ...item, ...patch } : item),
    }));
  };

  const deleteElement = (elementId = selectedId) => {
    if (!elementId) return;
    const remainingElements = canvas.elements.filter((item) => item.id !== elementId);
    setCanvas((prev) => ({
      ...prev,
      elements: prev.elements.filter((item) => item.id !== elementId),
    }));
    setSelectedId((current) => current === elementId ? (remainingElements[0]?.id || '') : current);
    setDraggingId(null);
    setElementContextMenu(null);
  };

  const updateCanvasPage = (patch: Partial<CertificateCanvas['page']>) => {
    setCanvas((prev) => resizeCanvasPage(prev, { ...prev.page, ...patch }));
  };

  const applyPaperPreset = (presetValue: string) => {
    const preset = paperSizePresets.find((item) => item.value === presetValue);
    if (!preset) return;
    const nextSize = getOrientedPageSize(preset.width, preset.height, canvas.page.orientation);
    updateCanvasPage({ ...nextSize, dpi: preset.dpi });
  };

  const applyOrientation = (orientation: CertificateCanvas['page']['orientation']) => {
    const nextSize = getOrientedPageSize(canvas.page.width, canvas.page.height, orientation);
    updateCanvasPage({ ...nextSize, orientation });
  };

  const getCanvasDropPoint = (clientX: number, clientY: number, fieldWidth = 900, fieldHeight = 110) => {
    if (!stageRef.current) return undefined;
    const rect = stageRef.current.getBoundingClientRect();
    const x = Math.min(
      Math.max(0, Math.round((clientX - rect.left) / scale - fieldWidth / 2)),
      Math.max(0, canvas.page.width - fieldWidth),
    );
    const y = Math.min(
      Math.max(0, Math.round((clientY - rect.top) / scale - fieldHeight / 2)),
      Math.max(0, canvas.page.height - fieldHeight),
    );
    return { x, y };
  };

  const addFieldElement = (field: CertificateReferenceField, point?: { x: number; y: number }) => {
    const x = point?.x ?? 400;
    const y = point?.y ?? 400;
    const element: CertificateCanvasElement = {
      id: `field_${field.key}_${Date.now()}`,
      type: 'text',
      fieldKey: field.key,
      x,
      y,
      width: Math.min(900, Math.max(260, canvas.page.width - x)),
      height: 110,
      fontSize: 48,
      color: '#222222',
      textAlign: 'center',
      placeholder: `\${${field.key}}`,
    };
    setCanvas((prev) => ({ ...prev, elements: [...prev.elements, element] }));
    setSelectedId(element.id);
  };

  const handleFieldPointerDown = (event: React.MouseEvent, field: CertificateReferenceField) => {
    if (event.button !== 0) return;
    event.preventDefault();
    setDraggingFieldKey(field.key);
    const finishDrag = (upEvent: MouseEvent) => {
      document.removeEventListener('mouseup', finishDrag);
      const stageWrap = stageWrapRef.current;
      if (!stageWrap) {
        setDraggingFieldKey(null);
        return;
      }
      const wrapRect = stageWrap.getBoundingClientRect();
      const isInsideStage =
        upEvent.clientX >= wrapRect.left &&
        upEvent.clientX <= wrapRect.right &&
        upEvent.clientY >= wrapRect.top &&
        upEvent.clientY <= wrapRect.bottom;
      if (isInsideStage) {
        const point = getCanvasDropPoint(upEvent.clientX, upEvent.clientY);
        addFieldElement(field, point);
      }
      setDraggingFieldKey(null);
    };
    document.addEventListener('mouseup', finishDrag);
  };

  const handleFieldDragStart = (event: React.DragEvent, field: CertificateReferenceField) => {
    event.dataTransfer.effectAllowed = 'copy';
    event.dataTransfer.setData('application/x-certificate-field', JSON.stringify(field));
    setDraggingFieldKey(field.key);
  };

  const handleFieldDrop = (event: React.DragEvent) => {
    const raw = event.dataTransfer.getData('application/x-certificate-field');
    if (!raw || !stageRef.current) return;
    event.preventDefault();
    try {
      const field = JSON.parse(raw) as CertificateReferenceField;
      addFieldElement(field, getCanvasDropPoint(event.clientX, event.clientY));
    } finally {
      setDraggingFieldKey(null);
    }
  };

  const onMouseMove = (event: React.MouseEvent) => {
    if (!draggingId || !stageRef.current) return;
    const rect = stageRef.current.getBoundingClientRect();
    const x = Math.max(0, Math.round((event.clientX - rect.left) / scale));
    const y = Math.max(0, Math.round((event.clientY - rect.top) / scale));
    setCanvas((prev) => ({
      ...prev,
      elements: prev.elements.map((item) => item.id === draggingId ? { ...item, x, y } : item),
    }));
  };

  const save = async () => {
    if (!version) return;
    const saved = await saveCertificateCanvas(version.id, {
      pageWidth: canvas.page.width,
      pageHeight: canvas.page.height,
      orientation: canvas.page.orientation,
      unit: 'PX',
      dpi: canvas.page.dpi,
      canvasJson: JSON.stringify(canvas),
    });
    setVersion(saved);
    message.success('已保存画布');
  };

  const uploadProps: UploadProps = {
    showUploadList: false,
    beforeUpload: async (file) => {
      if (!version) return false;
      const next = await uploadCertificateBackground(version.id, file);
      setVersion(next);
      message.success('背景已上传');
      return false;
    },
  };

  return (
    <ManagementPage title="证书设计器">
      <ManagementPageBody className="certificate-designer-page">
        <Card className="certificate-designer" styles={{ body: { padding: 0 } }}>
      <div className="certificate-designer__topbar">
        <Space>
          <Button onClick={() => history.push('/certificates/templates')}>返回</Button>
          <Select value={version?.id} style={{ width: 180 }} onChange={async (id) => {
            const detail = await getCertificateTemplateVersion(id);
            setVersion(detail);
            setCanvas(parseCanvas(detail.canvasJson, detail));
          }} options={versions.map((item) => ({ label: `v${item.version} ${item.status}`, value: item.id }))} />
          <Tag color={statusColor[version?.status || 'DRAFT']}>{version?.status}</Tag>
        </Space>
        <Space wrap className="certificate-designer__page-controls">
          <Select
            value={paperPresetValue}
            style={{ width: 104 }}
            options={paperSizeOptions}
            onChange={applyPaperPreset}
          />
          <Segmented
            value={canvas.page.orientation}
            options={[
              { label: '横向', value: 'LANDSCAPE' },
              { label: '纵向', value: 'PORTRAIT' },
            ]}
            onChange={(value) => applyOrientation(value as CertificateCanvas['page']['orientation'])}
          />
          <InputNumber
            addonBefore="宽"
            min={1}
            step={10}
            value={canvas.page.width}
            onChange={(width) => updateCanvasPage({ width: toPositiveInteger(width, canvas.page.width) })}
          />
          <InputNumber
            addonBefore="高"
            min={1}
            step={10}
            value={canvas.page.height}
            onChange={(height) => updateCanvasPage({ height: toPositiveInteger(height, canvas.page.height) })}
          />
          <InputNumber
            addonBefore="DPI"
            min={72}
            max={600}
            step={10}
            value={canvas.page.dpi}
            onChange={(dpi) => updateCanvasPage({ dpi: toPositiveInteger(dpi, canvas.page.dpi) })}
          />
          <Space.Compact className="certificate-designer__zoom-control">
            <Tooltip title="缩小">
              <Button icon={<ZoomOutOutlined />} onClick={() => setZoom((prev) => clampZoom(prev - 0.1))} />
            </Tooltip>
            <Button className="certificate-designer__zoom-value">{zoomPercent}%</Button>
            <Tooltip title="放大">
              <Button icon={<ZoomInOutlined />} onClick={() => setZoom((prev) => clampZoom(prev + 0.1))} />
            </Tooltip>
          </Space.Compact>
        </Space>
        <Space>
          <Upload {...uploadProps}><Button icon={<FileProtectOutlined />}>上传背景</Button></Upload>
          <Button icon={<SaveOutlined />} onClick={save}>保存草稿</Button>
          <Button type="primary" icon={<SendOutlined />} onClick={async () => { await save(); await publishCertificateTemplate(templateId); message.success('已发布版本'); await load(); }}>发布版本</Button>
        </Space>
      </div>
      <div
        className={[
          'certificate-designer__body',
          leftPanelCollapsed ? 'is-left-collapsed' : '',
          rightPanelCollapsed ? 'is-right-collapsed' : '',
        ].filter(Boolean).join(' ')}
      >
        <aside className={`certificate-designer__tools ${leftPanelCollapsed ? 'is-collapsed' : ''}`}>
          <div className="certificate-designer__panel-heading">
            {!leftPanelCollapsed ? <Typography.Text strong>组件</Typography.Text> : null}
            <Tooltip title={leftPanelCollapsed ? '展开左侧' : '折叠左侧'}>
              <Button
                type="text"
                size="small"
                icon={leftPanelCollapsed ? <RightOutlined /> : <LeftOutlined />}
                onClick={() => setLeftPanelCollapsed((prev) => !prev)}
              />
            </Tooltip>
          </div>
          {!leftPanelCollapsed ? (
            <>
              <Button block icon={<PlusOutlined />} onClick={() => addFieldElement(projectReferenceFields[0])}>文本变量</Button>
              <Button block icon={<QrcodeOutlined />} onClick={() => setCanvas((prev) => ({ ...prev, elements: [...prev.elements, { id: `qr_${Date.now()}`, type: 'qrcode', fieldKey: 'verificationUrl', x: 2800, y: 1800, width: 240, height: 240 }] }))}>二维码</Button>
              <Button block disabled={!selected} onClick={() => deleteElement()}>删除元素</Button>
              <div className="certificate-designer__variable-list">
                <Typography.Text strong className="certificate-designer__variable-heading">项目可引用字段</Typography.Text>
                {projectReferenceFields.map((item) => (
                  <Tooltip key={item.key} title="拖到画布生成字段文本">
                    <Tag
                      draggable
                      title={item.key}
                      className={`certificate-designer__variable-tag ${draggingFieldKey === item.key ? 'is-dragging' : ''}`}
                      onMouseDown={(event) => handleFieldPointerDown(event, item)}
                      onDragStart={(event) => handleFieldDragStart(event, item)}
                      onDragEnd={() => setDraggingFieldKey(null)}
                    >
                      <span>{item.label}</span>
                      <span className="certificate-designer__variable-key">{item.key}</span>
                    </Tag>
                  </Tooltip>
                ))}
              </div>
            </>
          ) : null}
        </aside>
        <main
          ref={stageWrapRef}
          className={`certificate-designer__stage-wrap ${draggingFieldKey ? 'is-field-dragging' : ''}`}
          onDragOver={(event) => {
            if (!Array.from(event.dataTransfer.types).includes('application/x-certificate-field')) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = 'copy';
          }}
          onDrop={handleFieldDrop}
        >
          <div
            ref={stageRef}
            className="certificate-canvas"
            style={{ width: canvas.page.width * scale, height: canvas.page.height * scale, backgroundImage: version?.backgroundUrl ? `url(${normalizeUploadUrl(version.backgroundUrl)})` : undefined }}
            onMouseMove={onMouseMove}
            onMouseUp={() => setDraggingId(null)}
            onMouseLeave={() => setDraggingId(null)}
          >
            {canvas.elements.map((element) => (
              <div
                key={element.id}
                className={`certificate-canvas__element ${selectedId === element.id ? 'is-selected' : ''} is-${element.type}`}
                style={{
                  left: element.x * scale,
                  top: element.y * scale,
                  width: element.width * scale,
                  height: element.height * scale,
                  color: element.color,
                  fontSize: (element.fontSize || 48) * scale,
                  fontWeight: element.fontWeight,
                  textAlign: element.textAlign,
                }}
                onMouseDown={(event) => { event.preventDefault(); setSelectedId(element.id); setDraggingId(element.id); }}
                onContextMenu={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  setSelectedId(element.id);
                  setDraggingId(null);
                  setElementContextMenu({ elementId: element.id, x: event.clientX, y: event.clientY });
                }}
              >
                {element.type === 'qrcode' ? <QrcodeOutlined /> : renderText(element, preview)}
              </div>
            ))}
            {elementContextMenu ? (
              <div
                className="certificate-canvas__context-menu"
                style={{ left: elementContextMenu.x, top: elementContextMenu.y }}
                onMouseDown={(event) => event.stopPropagation()}
              >
                <button type="button" onClick={() => deleteElement(elementContextMenu.elementId)}>
                  <DeleteOutlined />
                  <span>删除</span>
                </button>
              </div>
            ) : null}
          </div>
        </main>
        <aside className={`certificate-designer__props ${rightPanelCollapsed ? 'is-collapsed' : ''}`}>
          <div className="certificate-designer__panel-heading">
            {!rightPanelCollapsed ? <Typography.Text strong>元素属性</Typography.Text> : null}
            <Tooltip title={rightPanelCollapsed ? '展开右侧' : '折叠右侧'}>
              <Button
                type="text"
                size="small"
                icon={rightPanelCollapsed ? <LeftOutlined /> : <RightOutlined />}
                onClick={() => setRightPanelCollapsed((prev) => !prev)}
              />
            </Tooltip>
          </div>
          {!rightPanelCollapsed && selected ? (
            <Space direction="vertical" className="certificate-designer__prop-fields">
              <div className="certificate-designer__bound-field">
                <Typography.Text type="secondary">绑定字段</Typography.Text>
                <Tag title={selected.fieldKey || ''} className="certificate-designer__bound-field-tag">
                  {selectedReferenceField?.label || selected.fieldKey || '静态文本'}
                  {selected.fieldKey ? <span className="certificate-designer__variable-key">{selected.fieldKey}</span> : null}
                </Tag>
              </div>
              <InputNumber addonBefore="X" value={selected.x} onChange={(x) => updateElement({ x: Number(x || 0) })} />
              <InputNumber addonBefore="Y" value={selected.y} onChange={(y) => updateElement({ y: Number(y || 0) })} />
              <InputNumber addonBefore="W" value={selected.width} onChange={(width) => updateElement({ width: Number(width || 1) })} />
              <InputNumber addonBefore="H" value={selected.height} onChange={(height) => updateElement({ height: Number(height || 1) })} />
              {selected.type === 'text' ? (
                <>
                  <InputNumber addonBefore="字号" value={selected.fontSize} onChange={(fontSize) => updateElement({ fontSize: Number(fontSize || 12) })} />
                  <Input type="color" value={selected.color} onChange={(event) => updateElement({ color: event.target.value })} />
                  <Select value={selected.textAlign || 'left'} onChange={(textAlign) => updateElement({ textAlign })} options={[
                    { label: '左对齐', value: 'left' },
                    { label: '居中', value: 'center' },
                    { label: '右对齐', value: 'right' },
                  ]} />
                </>
              ) : null}
            </Space>
          ) : null}
          {!rightPanelCollapsed && !selected ? <Typography.Text type="secondary">请选择画布元素</Typography.Text> : null}
        </aside>
      </div>
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export const GeneratePage = () => {
  const [templates, setTemplates] = useState<CertificateTemplateRecord[]>([]);
  const [versions, setVersions] = useState<CertificateTemplateVersionRecord[]>([]);
  const [result, setResult] = useState<CertificateRecord[]>([]);
  const [form] = Form.useForm();

  useEffect(() => {
    void listCertificateTemplates({ status: 'PUBLISHED', pageSize: 100 }).then((res) => setTemplates(res.records || []));
  }, []);

  const submit = async () => {
    const values = await form.validateFields();
    const payload: CertificateGeneratePayload = {
      batchName: values.batchName,
      templateId: values.templateId,
      templateVersionId: values.templateVersionId,
      sourceType: 'MANUAL',
      records: [{
        recipientName: values.recipientName,
        recipientType: 'CUSTOM',
        competitionTitle: values.competitionTitle,
        projectName: values.projectName,
        teamName: values.teamName,
        awardName: values.awardName,
        issueDate: values.issueDate,
      }],
    };
    const response = await generateCertificates(payload);
    setResult(response.records || []);
    message.success('证书已生成');
  };

  const csvUpload: UploadProps = {
    showUploadList: false,
    beforeUpload: async (file) => {
      const values = await form.validateFields(['templateId', 'templateVersionId']);
      const text = await file.text();
      const rows = csvToRows(text);
      const response = await generateCertificates({
        batchName: file.name,
        templateId: values.templateId,
        templateVersionId: values.templateVersionId,
        sourceType: 'IMPORT',
        records: rows,
      });
      setResult(response.records || []);
      message.success(`已导入生成 ${response.records.length} 张证书`);
      return false;
    },
  };

  return (
    <div className="certificate-page">
      <div className="certificate-page__header">
        <div>
          <Typography.Title level={3}>证书生成</Typography.Title>
          <Typography.Text type="secondary">支持手动录入单张证书和 CSV 批量导入。</Typography.Text>
        </div>
        <Upload {...csvUpload}><Button icon={<FileDoneOutlined />}>导入 CSV 生成</Button></Upload>
      </div>
      <Card>
        <Form form={form} layout="vertical" className="certificate-generate-form">
          <Form.Item name="templateId" label="模板" rules={[{ required: true }]}>
            <Select options={templates.map((item) => ({ label: item.templateName, value: item.id }))} onChange={async (templateId) => {
              const list = await listCertificateTemplateVersions(templateId);
              setVersions(list.filter((item) => item.status === 'PUBLISHED'));
            }} />
          </Form.Item>
          <Form.Item name="templateVersionId" label="版本" rules={[{ required: true }]}>
            <Select options={versions.map((item) => ({ label: `v${item.version}`, value: item.id }))} />
          </Form.Item>
          <Form.Item name="batchName" label="批次名称"><Input /></Form.Item>
          <Form.Item name="recipientName" label="获奖人/团队" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="competitionTitle" label="赛事名称"><Input /></Form.Item>
          <Form.Item name="projectName" label="项目名称"><Input /></Form.Item>
          <Form.Item name="teamName" label="团队名称"><Input /></Form.Item>
          <Form.Item name="awardName" label="奖项"><Input /></Form.Item>
          <Form.Item name="issueDate" label="发证日期"><Input placeholder="YYYY-MM-DD" /></Form.Item>
          <Button type="primary" onClick={submit}>生成单张证书</Button>
        </Form>
      </Card>
      <Table rowKey="id" dataSource={result} columns={[
        { title: '证书编号', dataIndex: 'certificateNo' },
        { title: '获奖人/团队', dataIndex: 'recipientName' },
        { title: '状态', dataIndex: 'status', render: (value) => <Tag color={statusColor[value as string]}>{value}</Tag> },
        { title: '校验码', dataIndex: 'verificationCode' },
      ]} />
    </div>
  );
};

export const RecordsPage = () => {
  const [records, setRecords] = useState<CertificateRecord[]>([]);
  const [detail, setDetail] = useState<CertificateRecord | null>(null);
  const [filters, setFilters] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    const response = await listCertificates({ ...filters, pageSize: 100 });
    setRecords(response.records || []);
  }, [filters]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="certificate-page">
      <div className="certificate-page__header">
        <div>
          <Typography.Title level={3}>证书记录</Typography.Title>
          <Typography.Text type="secondary">查询、下载、重新生成和撤销已生成证书。</Typography.Text>
        </div>
        <Space>
          <Input.Search placeholder="证书编号" onSearch={(certificateNo) => setFilters((prev) => ({ ...prev, certificateNo }))} />
          <Input.Search placeholder="获奖人/团队" onSearch={(recipientName) => setFilters((prev) => ({ ...prev, recipientName }))} />
        </Space>
      </div>
      <Table rowKey="id" dataSource={records} columns={[
        { title: '证书编号', dataIndex: 'certificateNo' },
        { title: '获奖人/团队', dataIndex: 'recipientName' },
        { title: '赛事', dataIndex: 'competitionTitle' },
        { title: '奖项', dataIndex: 'awardName' },
        { title: '模板', dataIndex: 'templateName' },
        { title: '发证日期', dataIndex: 'issueDate' },
        { title: '状态', dataIndex: 'status', render: (value) => <Tag color={statusColor[value as string]}>{value}</Tag> },
        {
          title: '操作',
          render: (_, record) => (
            <Space>
              <Button size="small" onClick={() => setDetail(record)}>详情</Button>
              <Button size="small" icon={<DownloadOutlined />} onClick={() => void handleCertificateDownload(record)}>下载</Button>
              <Button size="small" icon={<CopyOutlined />} onClick={() => navigator.clipboard.writeText(`${location.origin}/certificate/verify/${record.publicToken}`)}>复制链接</Button>
              <Button size="small" onClick={async () => { await regenerateCertificate(record.id); await load(); }}>重生成</Button>
              <Button size="small" danger onClick={async () => { await revokeCertificate(record.id, '管理员撤销'); await load(); }}>撤销</Button>
            </Space>
          ),
        },
      ]} />
      <Drawer width={720} open={Boolean(detail)} onClose={() => setDetail(null)} title="证书详情">
        {detail ? (
          <Space direction="vertical" className="certificate-detail">
            <Descriptions column={2} bordered items={[
              { key: 'no', label: '证书编号', children: detail.certificateNo },
              { key: 'code', label: '校验码', children: detail.verificationCode },
              { key: 'name', label: '获奖人/团队', children: detail.recipientName },
              { key: 'status', label: '状态', children: <Tag color={statusColor[detail.status]}>{detail.status}</Tag> },
              { key: 'award', label: '奖项', children: detail.awardName },
              { key: 'date', label: '发证日期', children: detail.issueDate },
            ]} />
            {detail.certificateFileUrl ? <Image src={normalizeUploadUrl(detail.certificateFileUrl)} /> : null}
            <Input.TextArea rows={8} value={detail.dataJson} readOnly />
          </Space>
        ) : null}
      </Drawer>
    </div>
  );
};

export const PublicVerifyPage = () => {
  const params = useParams();
  const token = params.publicToken;
  const [form] = Form.useForm();
  const [result, setResult] = useState<CertificatePublicVerifyResult | null>(null);
  const resultText = useMemo(() => ({
    VALID: '有效证书',
    REVOKED: '证书已撤销',
    EXPIRED: '证书已过期',
    NOT_FOUND: '未查询到证书',
    INVALID_CODE: '校验码错误',
  }[result?.result || 'NOT_FOUND']), [result]);

  useEffect(() => {
    if (token) {
      void verifyCertificateByToken(token).then(setResult);
    }
  }, [token]);

  const submit = async () => {
    const values = await form.validateFields();
    setResult(await verifyCertificateByNo(values.certificateNo, values.verificationCode));
  };

  return (
    <div className="certificate-public">
      <Card className="certificate-public__card">
        <Space direction="vertical" size="large" className="certificate-public__content">
          <div>
            <Typography.Title level={2}>证书真伪查验</Typography.Title>
            <Typography.Text type="secondary">请输入证书编号和校验码，或通过证书二维码直接访问。</Typography.Text>
          </div>
          <Form form={form} layout="vertical">
            <Form.Item name="certificateNo" label="证书编号" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="verificationCode" label="校验码" rules={[{ required: true }]}><Input /></Form.Item>
            <Button type="primary" block onClick={submit}>查询证书</Button>
          </Form>
          {result ? (
            <Card className={`certificate-public__result is-${result.result.toLowerCase()}`}>
              <Typography.Title level={4}>{resultText}</Typography.Title>
              {result.certificateNo ? (
                <Descriptions column={1} items={[
                  { key: 'no', label: '证书编号', children: result.certificateNo },
                  { key: 'name', label: '获奖人/团队', children: result.recipientName },
                  { key: 'competition', label: '赛事', children: result.competitionTitle },
                  { key: 'project', label: '项目', children: result.projectName },
                  { key: 'award', label: '奖项', children: result.awardName },
                  { key: 'date', label: '发证日期', children: result.issueDate },
                ]} />
              ) : null}
            </Card>
          ) : null}
        </Space>
      </Card>
    </div>
  );
};
