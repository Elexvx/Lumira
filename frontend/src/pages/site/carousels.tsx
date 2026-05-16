import { Button, Drawer, Form, Image, Input, InputNumber, Popconfirm, Select, Space, Table, Tag, Upload, message } from 'antd';
import { PlusOutlined, UploadOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { fileService } from '@/services/file';
import { siteService, type SiteCarousel } from '@/services/site';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import SiteAdminPage from './SiteAdminPage';
import './site.css';

const MAX_CAROUSEL_IMAGE_SIZE = 10 * 1024 * 1024;

const linkTypeOptions = [
  { value: 'NONE', label: '无跳转' },
  { value: 'INTERNAL', label: '站内路径' },
  { value: 'EXTERNAL', label: '外部链接' },
];

const openTypeOptions = [
  { value: 'SELF', label: '当前窗口' },
  { value: 'BLANK', label: '新窗口' },
];

const statusOptions = [
  { value: 'VISIBLE', label: '显示' },
  { value: 'HIDDEN', label: '隐藏' },
];

const CarouselManagement = () => {
  const [records, setRecords] = useState<SiteCarousel[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [editing, setEditing] = useState<Partial<SiteCarousel> | null>(null);
  const [form] = Form.useForm<Partial<SiteCarousel>>();
  const watchedImageUrl = Form.useWatch('imageUrl', form);
  const actionPermission = useActionPermission();

  const canCreate = actionPermission.can('site:carousel:create');
  const canUpdate = actionPermission.can('site:carousel:update');
  const canDelete = actionPermission.can('site:carousel:delete');
  const canSave = Boolean(editing?.id ? canUpdate : canCreate);
  const previewImageUrl = useMemo(() => normalizeUploadUrl(watchedImageUrl), [watchedImageUrl]);

  const load = async () => {
    setLoading(true);
    try {
      setRecords(await siteService.carousels());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const open = (record?: SiteCarousel) => {
    const next = record || {
      title: '',
      subtitle: '',
      imageUrl: '',
      linkType: 'NONE',
      openType: 'SELF',
      sortOrder: records.length + 1,
      status: 'VISIBLE',
    };
    setEditing(next);
    form.setFieldsValue(next);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (!values.imageFileId && !values.imageUrl) {
      message.warning('请上传轮播图片或填写图片 URL');
      return;
    }

    setSaving(true);
    try {
      if (editing?.id) {
        await siteService.updateCarousel(editing.id, values);
      } else {
        await siteService.createCarousel(values);
      }
      message.success('轮播已保存');
      setEditing(null);
      await load();
    } finally {
      setSaving(false);
    }
  };

  const uploadProps: UploadProps = {
    accept: 'image/*',
    showUploadList: false,
    beforeUpload: async (file) => {
      if (!file.type.startsWith('image/')) {
        message.error('请上传图片文件');
        return Upload.LIST_IGNORE;
      }
      if (file.size > MAX_CAROUSEL_IMAGE_SIZE) {
        message.error('图片过大，请上传不超过 10MB 的文件');
        return Upload.LIST_IGNORE;
      }

      setUploading(true);
      try {
        const uploaded = await fileService.upload(file, {
          category: 'site-carousel',
          tags: 'site,carousel',
          remark: '官网轮播图片',
        });
        form.setFieldsValue({
          imageFileId: uploaded.id,
          imageUrl: normalizeUploadUrl(uploaded.publicUrl),
        });
        message.success('轮播图片已上传');
      } catch (error) {
        message.error(error instanceof Error ? error.message : '图片上传失败，请稍后重试');
      } finally {
        setUploading(false);
      }
      return Upload.LIST_IGNORE;
    },
  };

  return (
    <SiteAdminPage
      title="轮播管理"
      extra={canCreate ? <Button type="primary" icon={<PlusOutlined />} onClick={() => open()}>新增轮播</Button> : null}
    >
      <div className="site-admin-card">
        <Table
          rowKey="id"
          loading={loading}
          dataSource={records}
          pagination={false}
          columns={[
            {
              title: '图片',
              dataIndex: 'imageUrl',
              width: 180,
              render: (value) => {
                const url = normalizeUploadUrl(value);
                return url ? <Image width={120} height={68} preview={false} src={url} className="site-admin-carousel-thumb" /> : '-';
              },
            },
            { title: '标题', dataIndex: 'title' },
            { title: '副标题', dataIndex: 'subtitle', ellipsis: true },
            { title: '排序', dataIndex: 'sortOrder', width: 90 },
            { title: '状态', dataIndex: 'status', width: 110, render: (value) => <Tag color={value === 'VISIBLE' ? 'green' : 'default'}>{value === 'VISIBLE' ? '显示' : '隐藏'}</Tag> },
            { title: '更新时间', dataIndex: 'updatedAt', width: 180 },
            {
              title: '操作',
              width: 180,
              render: (_, record) => (
                <Space>
                  {canUpdate ? <Button type="link" onClick={() => open(record)}>编辑</Button> : null}
                  {canDelete ? (
                    <Popconfirm title="确认删除？" onConfirm={async () => { await siteService.deleteCarousel(record.id); await load(); }}>
                      <Button type="link" danger>删除</Button>
                    </Popconfirm>
                  ) : null}
                </Space>
              ),
            },
          ]}
        />
      </div>
      <Drawer
        title={editing?.id ? '编辑轮播' : '新增轮播'}
        open={Boolean(editing)}
        width={STANDARD_DRAWER_WIDTH}
        onClose={() => setEditing(null)}
        extra={<Button type="primary" loading={saving} disabled={!canSave} onClick={save}>保存</Button>}
      >
        <Form form={form} layout="vertical" disabled={!canSave}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={160} placeholder="例如：连接产业与创新服务" />
          </Form.Item>
          <Form.Item name="subtitle" label="副标题">
            <Input.TextArea rows={3} maxLength={500} placeholder="用于首屏辅助说明，可为空" />
          </Form.Item>
          <Form.Item name="imageFileId" hidden>
            <InputNumber />
          </Form.Item>
          <Form.Item label="轮播图片" required>
            <div className="site-admin-carousel-uploader">
              {previewImageUrl ? (
                <Image width={280} height={158} preview={false} src={previewImageUrl} className="site-admin-carousel-preview" />
              ) : (
                <div className="site-admin-carousel-empty">暂无图片</div>
              )}
              <Space direction="vertical">
                <Upload {...uploadProps}>
                  <Button icon={<UploadOutlined />} loading={uploading}>上传图片</Button>
                </Upload>
                <Button onClick={() => form.setFieldsValue({ imageFileId: undefined, imageUrl: undefined })} disabled={!previewImageUrl}>
                  清除图片
                </Button>
              </Space>
            </div>
          </Form.Item>
          <Form.Item name="imageUrl" label="图片 URL">
            <Input placeholder="可上传生成，也可填写外部图片地址" />
          </Form.Item>
          <Form.Item name="linkType" label="跳转类型">
            <Select options={linkTypeOptions} />
          </Form.Item>
          <Form.Item name="linkTarget" label="跳转目标">
            <Input placeholder="例如：/about 或 https://example.com" />
          </Form.Item>
          <Form.Item name="openType" label="打开方式">
            <Select options={openTypeOptions} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select options={statusOptions} />
          </Form.Item>
        </Form>
      </Drawer>
    </SiteAdminPage>
  );
};

export default CarouselManagement;
