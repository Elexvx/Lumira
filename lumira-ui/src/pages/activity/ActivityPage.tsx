import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Card, DatePicker, Divider, Empty, Form, Image, Input, InputNumber, Modal, Pagination, Select, Space, Spin, Switch, Tag, Typography, Upload } from 'antd';
import type { FormInstance } from 'antd';
import ImgCrop from 'antd-img-crop';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { useEffect, useMemo, useRef, useState } from 'react';
import { history, useLocation } from '@umijs/max';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useDictOptions } from '@/hooks/useDictOptions';
import { useResponsive } from '@/hooks/useResponsive';
import { createActivity, deleteActivity, listActivities, updateActivity } from '@/services/activity/api';
import type { ActivityBadgeTone, ActivityLocale, ActivityRecord, ActivityStatus, ActivityUpsertPayload } from '@/services/activity/types';
import { request } from '@/services/common/request';
import { message } from '@/theme/antdFeedbackBridge';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import './ActivityPage.css';

const localeOptions: Array<{ label: string; value: ActivityLocale }> = [
  { label: '中文', value: 'zh' },
  { label: 'English', value: 'en' },
];

const statusOptions: Array<{ label: string; value: ActivityStatus }> = [
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' },
];

const badgeToneOptions: Array<{ label: string; value: ActivityBadgeTone }> = [
  { label: '蓝色', value: 'blue' },
  { label: '金色', value: 'gold' },
  { label: '银色', value: 'silver' },
  { label: '铜色', value: 'bronze' },
  { label: '灰色', value: 'slate' },
  { label: '深色', value: 'dark' },
];

const statusLabel: Record<ActivityStatus, string> = {
  draft: '草稿',
  published: '已发布',
};

const statusColor: Record<ActivityStatus, string> = {
  draft: 'default',
  published: 'green',
};

const ALL_ACTIVITY_CATEGORY = '\u5168\u90e8';
const ACTIVITY_CATEGORY_SOURCE_PAGE_SIZE = 200;
const ACTIVITY_CATEGORY_DICT = 'aiadc_activity_category';

const fallbackActivityCategoryOptions: Array<{ label: string; value: string }> = [
  { label: '路演活动', value: '路演活动' },
  { label: '创业沙龙', value: '创业沙龙' },
  { label: '政策宣讲', value: '政策宣讲' },
  { label: '培训活动', value: '培训活动' },
  { label: '其他', value: '其他' },
];

const splitActivityTags = (tags?: string | null) =>
  (tags || '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);

const splitActivityLocales = (value?: string | null) =>
  (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

const joinActivityLocales = (values?: ActivityLocale[]) => Array.from(new Set(values || [])).join(',');

const getActivityCategory = (record: ActivityRecord) => record.subtitle?.trim() || undefined;

const buildActivityCategoryOptions = (records: ActivityRecord[]) => {
  const seenCategories = new Set<string>();
  const options = [ALL_ACTIVITY_CATEGORY];

  records.forEach((record) => {
    const recordCategory = getActivityCategory(record);
    if (recordCategory && !seenCategories.has(recordCategory)) {
      seenCategories.add(recordCategory);
      options.push(recordCategory);
    }
  });

  return options;
};

const activityCoverThemes = ['blue', 'cyan', 'green', 'gold', 'purple', 'slate'] as const;

const getActivitySeed = (record: ActivityRecord) =>
  Array.from(`${record.code || record.id}-${record.title}`).reduce((total, char) => total + char.charCodeAt(0), 0);

const getActivityCoverTheme = (record: ActivityRecord) => activityCoverThemes[getActivitySeed(record) % activityCoverThemes.length];

const isActivitySearchRoute = (pathname: string) => pathname === '/activities/search';
const isActivityManagementRoute = (pathname: string) => pathname === '/activities/management';

type ActivityFormValues = Omit<ActivityUpsertPayload, 'code' | 'activityDate' | 'activityTime' | 'locale'> & {
  code?: string;
  locale: ActivityLocale[];
  activityDateTimeRange?: [Dayjs, Dayjs] | [string, string];
};

const trimOptional = (value?: string) => {
  const trimmed = value?.trim();
  return trimmed || undefined;
};

const parseActivityDate = (value?: string | null) => {
  if (!value) {
    return undefined;
  }
  const parsed = dayjs(value.replace(/\./g, '-'));
  return parsed.isValid() ? parsed : undefined;
};

const parseActivityDateTimeRange = (date?: string | null, time?: string | null): [Dayjs, Dayjs] | undefined => {
  const parsedDate = parseActivityDate(date);
  if (!parsedDate || !time) {
    return undefined;
  }
  const normalizedDate = parsedDate.format('YYYY-MM-DD');
  const [start, end] = time.split('-').map((part) => part.trim());
  if (!start || !end) {
    return undefined;
  }
  const startDateTime = dayjs(`${normalizedDate} ${start}`);
  const endDateTime = dayjs(`${normalizedDate} ${end}`);
  if (!startDateTime.isValid() || !endDateTime.isValid()) {
    return undefined;
  }
  return endDateTime.isAfter(startDateTime) ? [startDateTime, endDateTime] : [startDateTime, startDateTime.hour(23).minute(59).second(0)];
};

const normalizeActivityDateTime = (value?: Dayjs | string) => {
  if (!value) {
    return undefined;
  }
  const parsed = dayjs.isDayjs(value) ? value : dayjs(value);
  return parsed.isValid() ? parsed : undefined;
};

const normalizePayload = (values: ActivityFormValues): ActivityUpsertPayload => {
  const [rangeStartDateTime, rangeEndDateTime] = values.activityDateTimeRange || [];
  const startDateTime = normalizeActivityDateTime(rangeStartDateTime);
  const endDateTime = normalizeActivityDateTime(rangeEndDateTime);
  const activityDate = startDateTime ? startDateTime.format('YYYY.MM.DD') : '';
  const activityTime =
    startDateTime && endDateTime
      ? `${startDateTime.format('HH:mm')}-${endDateTime.format('HH:mm')}`
      : '';
  const { activityDateTimeRange: _activityDateTimeRange, ...payloadValues } = values;
  return {
    ...payloadValues,
    code: trimOptional(values.code),
    locale: joinActivityLocales(values.locale),
    title: values.title.trim(),
    activityDate,
    activityTime,
    location: values.location.trim(),
    subtitle: trimOptional(values.subtitle),
    description: trimOptional(values.description),
    imageUrl: trimOptional(values.imageUrl),
    iconKey: trimOptional(values.iconKey),
    tags: trimOptional(values.tags),
    ctaLabel: trimOptional(values.ctaLabel),
    ctaHref: trimOptional(values.ctaHref),
    badgeText: trimOptional(values.badgeText),
    badgeTone: values.badgeTone || undefined,
    sort: values.sort ?? 100,
    featured: Boolean(values.featured),
  };
};

const ActivityDateTimeRangePicker = ({
  value,
  onChange,
}: {
  value?: ActivityFormValues['activityDateTimeRange'];
  onChange?: (value?: ActivityFormValues['activityDateTimeRange']) => void;
}) => {
  const pickerRef = useRef<HTMLDivElement | null>(null);
  const [draftRange, setDraftRange] = useState<[Dayjs | null, Dayjs | null]>([null, null]);

  useEffect(() => {
    const [startDateTime, endDateTime] = value || [];
    setDraftRange([normalizeActivityDateTime(startDateTime) || null, normalizeActivityDateTime(endDateTime) || null]);
  }, [value]);

  const commitRange = (startDateTime?: Dayjs | null, endDateTime?: Dayjs | null) => {
    const nextRange: [Dayjs | null, Dayjs | null] = [startDateTime || null, endDateTime || null];
    setDraftRange(nextRange);
    onChange?.(startDateTime && endDateTime ? [startDateTime, endDateTime] : undefined);
  };

  const focusEndInput = () => {
    window.setTimeout(() => {
      pickerRef.current?.querySelector<HTMLInputElement>('input[placeholder="结束日期"]')?.focus();
    });
  };

  return (
    <div ref={pickerRef}>
      <DatePicker.RangePicker
        value={draftRange}
        needConfirm={false}
        showTime={{
          format: 'HH:mm',
          defaultValue: [dayjs().hour(0).minute(0).second(0), dayjs().hour(23).minute(59).second(0)],
        }}
        format="YYYY.MM.DD HH:mm"
        minuteStep={15}
        placeholder={['开始日期', '结束日期']}
        placement="topRight"
        getPopupContainer={() => document.body}
        style={{ width: '100%' }}
        onCalendarChange={(dates) => {
          const [nextStartDateTime, nextEndDateTime] = dates || [];
          const normalizedStartDateTime = normalizeActivityDateTime(nextStartDateTime || undefined);
          const normalizedEndDateTime = normalizeActivityDateTime(nextEndDateTime || undefined);
          const [draftStartDateTime, draftEndDateTime] = draftRange;

          if (!normalizedStartDateTime) {
            commitRange();
            return;
          }

          if (!draftStartDateTime || draftEndDateTime) {
            setDraftRange([normalizedStartDateTime, null]);
            onChange?.(undefined);
            focusEndInput();
            return;
          }

          if (normalizedEndDateTime) {
            commitRange(normalizedStartDateTime, normalizedEndDateTime);
            return;
          }

          if (normalizedStartDateTime.isSame(draftStartDateTime)) {
            setDraftRange([draftStartDateTime, null]);
            focusEndInput();
            return;
          }

          const [startDateTime, endDateTime] = normalizedStartDateTime.isBefore(draftStartDateTime)
            ? [normalizedStartDateTime, draftStartDateTime]
            : [draftStartDateTime, normalizedStartDateTime];
          commitRange(startDateTime, endDateTime);
        }}
        onChange={(dates) => {
          if (!dates) {
            commitRange();
          }
        }}
      />
    </div>
  );
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

const uploadActivityImage = async (file: File) => {
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

const ActivityForm = ({ form }: { form: FormInstance<ActivityFormValues> }) => {
  const [uploadingImage, setUploadingImage] = useState(false);
  const imageUrl = Form.useWatch('imageUrl', form);
  const previewUrl = normalizeUploadUrl(imageUrl);
  const { options: activityCategoryOptions } = useDictOptions(ACTIVITY_CATEGORY_DICT, fallbackActivityCategoryOptions);

  const handleImageUpload = async (file: File) => {
    setUploadingImage(true);
    try {
      const uploadedUrl = await uploadActivityImage(file);
      if (uploadedUrl) {
        form.setFieldValue('imageUrl', uploadedUrl);
        message.success('图片已上传');
      }
    } catch (error) {
      showErrorMessage(error, '图片上传失败');
    } finally {
      setUploadingImage(false);
    }
  };

  return (
    <Form<ActivityFormValues>
      form={form}
      layout="vertical"
      initialValues={{
        locale: ['zh'],
        status: 'draft',
        sort: 100,
        featured: false,
        ctaLabel: '查看详情',
        ctaHref: '/login',
      }}
    >
      <Form.Item name="locale" label="语言" rules={[{ required: true }]}>
        <Select mode="multiple" maxTagCount="responsive" options={localeOptions} placeholder="璇█锛堝彲澶氶€夛級" />
      </Form.Item>
      <Form.Item name="title" label="活动标题" rules={[{ required: true, message: '请输入活动标题' }]}>
        <Input maxLength={128} />
      </Form.Item>
      <Form.Item name="subtitle" label="活动分类">
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          options={activityCategoryOptions}
          placeholder="请选择活动分类"
        />
      </Form.Item>
      <Form.Item name="description" label="活动描述">
        <Input.TextArea rows={4} maxLength={1000} />
      </Form.Item>
      <Form.Item
        name="activityDateTimeRange"
        label="活动日期"
        rules={[
          { required: true, message: '请选择活动日期和时间' },
          {
            validator: (_, value: ActivityFormValues['activityDateTimeRange']) => {
              const [startDateTime, endDateTime] = value || [];
              const normalizedStartDateTime = normalizeActivityDateTime(startDateTime);
              const normalizedEndDateTime = normalizeActivityDateTime(endDateTime);
              if (!normalizedStartDateTime || !normalizedEndDateTime) {
                return Promise.reject(new Error('请选择开始和结束日期时间'));
              }
              return normalizedEndDateTime.isAfter(normalizedStartDateTime)
                ? Promise.resolve()
                : Promise.reject(new Error('结束日期时间必须晚于开始日期时间'));
            },
          },
        ]}
      >
        <ActivityDateTimeRangePicker />
      </Form.Item>
      <Form.Item name="location" label="活动地点" rules={[{ required: true, message: '请输入活动地点' }]}>
        <Input maxLength={255} />
      </Form.Item>
      <Space size="middle" style={{ width: '100%' }} align="start">
        <Form.Item name="status" label="发布状态" rules={[{ required: true }]} style={{ flex: 1 }}>
          <Select options={statusOptions} />
        </Form.Item>
        <Form.Item name="sort" label="排序" style={{ flex: 1 }}>
          <InputNumber min={0} max={9999} style={{ width: '100%' }} />
        </Form.Item>
      </Space>
      <Form.Item name="featured" label="当前重点活动" valuePropName="checked">
        <Switch checkedChildren="是" unCheckedChildren="否" />
      </Form.Item>
      <Form.Item label="活动图片">
        <Space direction="vertical" size={8} className="activity-image-upload">
          <ImgCrop
            modalTitle="裁剪活动图片"
            rotationSlider
            aspect={5 / 3}
            beforeCrop={(file) => {
              if (!file.type.startsWith('image/')) {
                message.error('请上传图片文件');
                return false;
              }
              return true;
            }}
          >
            <Upload
              accept="image/*"
              showUploadList={false}
              beforeUpload={async (file) => {
                await handleImageUpload(file);
                return Upload.LIST_IGNORE;
              }}
              disabled={uploadingImage}
            >
              <div
                className={`activity-image-upload__preview${uploadingImage ? ' is-uploading' : ''}${previewUrl ? ' has-image' : ''}`}
                role="button"
                aria-label="上传活动图片"
                tabIndex={uploadingImage ? -1 : 0}
                onKeyDown={(event) => {
                  if (uploadingImage) {
                    return;
                  }
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    event.currentTarget.click();
                  }
                }}
              >
                {previewUrl ? (
                  <Image width="100%" height="100%" src={previewUrl} preview={false} style={{ objectFit: 'cover' }} />
                ) : (
                  <Space direction="vertical" size={6} align="center">
                    <UploadOutlined />
                    <Typography.Text type="secondary">{uploadingImage ? '上传中...' : '点击上传图片'}</Typography.Text>
                  </Space>
                )}
                {previewUrl ? <span className="activity-image-upload__hint">{uploadingImage ? '上传中...' : '点击更换图片'}</span> : null}
              </div>
            </Upload>
          </ImgCrop>
          <Button
            icon={<DeleteOutlined />}
            disabled={!imageUrl || uploadingImage}
            onClick={() => {
              form.setFieldValue('imageUrl', undefined);
            }}
          >
            清空图片
          </Button>
          <Typography.Text type="secondary">图片将按 5:3 比例裁剪后上传。</Typography.Text>
          <Form.Item name="imageUrl" hidden>
            <Input type="hidden" />
          </Form.Item>
        </Space>
      </Form.Item>
      <Form.Item name="iconKey" label="图标标识">
        <Input maxLength={64} />
      </Form.Item>
      <Form.Item name="tags" label="标签">
        <Input maxLength={1000} placeholder="多个标签用英文逗号分隔" />
      </Form.Item>
      <Space size="middle" style={{ width: '100%' }} align="start">
        <Form.Item name="ctaLabel" label="按钮文案" style={{ flex: 1 }}>
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item name="ctaHref" label="按钮链接" style={{ flex: 1 }}>
          <Input maxLength={512} />
        </Form.Item>
      </Space>
      <Space size="middle" style={{ width: '100%' }} align="start">
        <Form.Item name="badgeText" label="徽标文案" style={{ flex: 1 }}>
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item name="badgeTone" label="徽标颜色" style={{ flex: 1 }}>
          <Select allowClear options={badgeToneOptions} />
        </Form.Item>
      </Space>
    </Form>
  );
};

const ActivitySearchView = () => {
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState(ALL_ACTIVITY_CATEGORY);
  const [categoryOptions, setCategoryOptions] = useState([ALL_ACTIVITY_CATEGORY]);
  const [locale, setLocale] = useState<ActivityLocale | undefined>();
  const [status, setStatus] = useState<ActivityStatus | undefined>();
  const [featured, setFeatured] = useState<boolean | undefined>();
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [records, setRecords] = useState<ActivityRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const loadActivities = async (nextKeyword = keyword) => {
    setLoading(true);
    try {
      const queryParams = {
        keyword: trimOptional(nextKeyword),
        locale,
        status,
        featured,
      };
      const [response, categorySourceResponse] = await Promise.all([
        listActivities({
          ...queryParams,
          pageNo,
          pageSize,
        }),
        listActivities({
          ...queryParams,
          pageNo: 1,
          pageSize: ACTIVITY_CATEGORY_SOURCE_PAGE_SIZE,
        }),
      ]);
      const nextCategoryOptions = buildActivityCategoryOptions(categorySourceResponse.records);
      let activeCategory = category;

      if (!nextCategoryOptions.includes(activeCategory)) {
        activeCategory = ALL_ACTIVITY_CATEGORY;
        setCategory(ALL_ACTIVITY_CATEGORY);
        setPageNo(1);
      }

      setCategoryOptions(nextCategoryOptions);

      if (activeCategory === ALL_ACTIVITY_CATEGORY) {
        setRecords(response.records);
        setTotal(response.total);
        return;
      }

      const filteredRecords = categorySourceResponse.records.filter((record) => getActivityCategory(record) === activeCategory);
      const pageStart = (pageNo - 1) * pageSize;
      setRecords(filteredRecords.slice(pageStart, pageStart + pageSize));
      setTotal(filteredRecords.length);
    } catch (error) {
      showErrorMessage(error, '活动列表加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadActivities();
  }, [category, locale, status, featured, pageNo, pageSize]);

  return (
    <ManagementPage title="活动查询">
      <ManagementPageBody className="activity-search-page">
        <div className="activity-search-hero">
            <Input.Search
              className="activity-search-hero__input"
              size="large"
              allowClear
              placeholder="请输入"
              enterButton="搜索"
              prefix={<SearchOutlined />}
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              onSearch={(value) => {
                setKeyword(value);
                setPageNo(1);
                void loadActivities(value);
              }}
            />
        </div>

        <Card className="activity-search-filter-card">
          <div className="activity-search-filter-row">
            <Typography.Text strong>所属类目:</Typography.Text>
            <Space wrap size={[24, 8]}>
                {categoryOptions.map((item) => (
                  <Button
                    key={item}
                    type={category === item ? 'link' : 'text'}
                    onClick={() => {
                      setCategory(item);
                      setPageNo(1);
                    }}
                  >
                    {item}
                  </Button>
                ))}
            </Space>
          </div>
          <Divider />
          <div className="activity-search-filter-row activity-search-filter-row--split">
            <Space wrap size={[12, 8]}>
              <Typography.Text strong>其它选项:</Typography.Text>
              <Typography.Text>语言:</Typography.Text>
                <Select
                  allowClear
                  placeholder="语言：不限"
                  value={locale}
                  className="activity-search-filter-row__small"
                  options={localeOptions}
                  onChange={(value) => {
                    setLocale(value);
                    setPageNo(1);
                  }}
                />
            </Space>
            <Space wrap size={[12, 8]}>
              <Typography.Text>状态:</Typography.Text>
                <Select
                  allowClear
                  placeholder="状态：不限"
                  value={status}
                  className="activity-search-filter-row__small"
                  options={statusOptions}
                  onChange={(value) => {
                    setStatus(value);
                    setPageNo(1);
                  }}
                />
            </Space>
            <Space wrap size={[12, 8]}>
              <Typography.Text>重点:</Typography.Text>
                <Select
                  allowClear
                  placeholder="重点：不限"
                  value={featured}
                  className="activity-search-filter-row__small"
                  options={[
                    { label: '重点活动', value: true },
                    { label: '普通活动', value: false },
                  ]}
                  onChange={(value) => {
                    setFeatured(value);
                    setPageNo(1);
                  }}
                />
            </Space>
          </div>
        </Card>

        <Spin spinning={loading}>
          <div className={`activity-search-results${records.length ? '' : ' activity-search-results--empty'}`}>
          {records.length ? (
            records.map((record, index) => {
                const tags = splitActivityTags(record.tags);
                const coverUrl = normalizeUploadUrl(record.imageUrl || '');
                const coverTheme = getActivityCoverTheme(record);
                return (
                  <div key={record.id}>
                    <article className="activity-search-result">
                      {coverUrl ? (
                        <img className="activity-search-result__cover" src={coverUrl} alt={record.title} />
                      ) : (
                        <div className={`activity-search-result__cover activity-search-result__cover--${coverTheme}`}>
                          <span>{record.subtitle || record.title}</span>
                        </div>
                      )}
                      <div className="activity-search-result__body">
                        <Typography.Title level={4}>{record.title}</Typography.Title>
                      <Space size={6} wrap>
                        {record.subtitle ? <Tag>{record.subtitle}</Tag> : null}
                        <Tag color={statusColor[record.status]}>{statusLabel[record.status]}</Tag>
                        {record.featured ? <Tag color="gold">重点</Tag> : null}
                      </Space>
                        <Typography.Paragraph className="activity-search-result__description" ellipsis={{ rows: 2 }}>
                      {record.description || '该活动暂无简介，编辑后可在活动查询列表中展示完整说明。'}
                    </Typography.Paragraph>
                    {tags.length ? (
                          <Space size={8} wrap>
                        {tags.slice(0, 5).map((tag) => (
                          <Tag key={tag} color="blue">
                            {tag}
                          </Tag>
                        ))}
                      </Space>
                    ) : null}
                        <div className="activity-search-result__footer">
                        <Space direction="vertical" size={4} className="activity-search-result__meta">
                          <Typography.Text type="secondary">时间：{[record.activityDate, record.activityTime].filter(Boolean).join(' ') || '-'}</Typography.Text>
                          <Typography.Text type="secondary">地点：{record.location || '-'}</Typography.Text>
                          <Typography.Text type="secondary">
                            使用语言：
                            {splitActivityLocales(record.locale)
                              .map((locale) => (locale === 'zh' ? '中文' : locale === 'en' ? 'English' : locale))
                              .join(' / ') || '-'}
                          </Typography.Text>
                        </Space>
                        </div>
                      </div>
                  </article>
                    {index < records.length - 1 ? <Divider /> : null}
                  </div>
                );
              })
          ) : (
            <Empty description="暂无活动" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </div>
        </Spin>

        <div className="activity-pagination">
          <Pagination
            current={pageNo}
            pageSize={pageSize}
            total={total}
            showSizeChanger
            onChange={(nextPage, nextPageSize) => {
              setPageNo(nextPage);
              setPageSize(nextPageSize);
            }}
          />
        </div>
      </ManagementPageBody>
    </ManagementPage>
  );
};

const ActivityManagementView = () => {
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [form] = Form.useForm<ActivityFormValues>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<ActivityRecord>();
  const [saving, setSaving] = useState(false);

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingRecord(undefined);
  };

  const openCreateDrawer = () => {
    setEditingRecord(undefined);
    form.resetFields();
    form.setFieldsValue({
      locale: ['zh'],
      status: 'draft',
      sort: 100,
      featured: false,
      ctaLabel: '查看详情',
      ctaHref: '/login',
    });
    setDrawerOpen(true);
  };

  const openEditDrawer = (record: ActivityRecord) => {
    setEditingRecord(record);
    form.resetFields();
    form.setFieldsValue({
      ...record,
      locale: splitActivityLocales(record.locale) as ActivityLocale[],
      subtitle: record.subtitle || undefined,
      description: record.description || undefined,
      imageUrl: record.imageUrl || undefined,
      iconKey: record.iconKey || undefined,
      tags: record.tags || undefined,
      ctaLabel: record.ctaLabel || undefined,
      ctaHref: record.ctaHref || undefined,
      badgeText: record.badgeText || undefined,
      badgeTone: record.badgeTone || undefined,
      activityDateTimeRange: parseActivityDateTimeRange(record.activityDate, record.activityTime),
      featured: Boolean(record.featured),
    });
    setDrawerOpen(true);
  };

  const saveActivity = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editingRecord) {
        await updateActivity(editingRecord.id, normalizePayload(values));
        message.success('活动已更新');
      } else {
        await createActivity(normalizePayload(values));
        message.success('活动已新增');
      }
      closeDrawer();
      actionRef.current?.reload();
    } catch (error) {
      showErrorMessage(error, '活动保存失败');
    } finally {
      setSaving(false);
    }
  };

  const columns = useMemo<ProColumns<ActivityRecord>[]>(
    () => [
      {
        title: '活动',
        dataIndex: 'keyword',
        render: (_, record) => <Typography.Text strong>{record.title}</Typography.Text>,
      },
      {
        title: '分类',
        dataIndex: 'subtitle',
        search: false,
        render: (value) => value ? <Tag color="blue">{String(value)}</Tag> : '-',
      },
      {
        title: '语言',
        dataIndex: 'locale',
        valueType: 'select',
        valueEnum: {
          zh: { text: '中文' },
          en: { text: 'English' },
        },
        width: 96,
        render: (value) => {
          const locales = splitActivityLocales(typeof value === 'string' ? value : undefined);
          if (!locales.length) return '-';
          return <Space size={4} wrap>{locales.map((item) => <Tag key={item}>{item === 'zh' ? '涓枃' : item === 'en' ? 'English' : item}</Tag>)}</Space>;
        },
      },
      {
        title: '日期',
        dataIndex: 'activityDate',
        search: false,
        width: 128,
      },
      {
        title: '时间',
        dataIndex: 'activityTime',
        search: false,
        width: 140,
      },
      {
        title: '地点',
        dataIndex: 'location',
        search: false,
        ellipsis: true,
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
        render: (_, record) => (record.featured ? <Tag color="gold">重点</Tag> : <Tag>普通</Tag>),
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
        render: (_, record) => <Tag color={statusColor[record.status]}>{statusLabel[record.status]}</Tag>,
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
        width: 160,
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
                permission: 'aiadc:activity:update',
                onClick: () => openEditDrawer(record),
              },
              {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined />,
                permission: 'aiadc:activity:delete',
                danger: true,
                onClick: () => {
                  Modal.confirm({
                    title: '确认删除该活动？',
                    content: `删除后活动「${record.title}」不会再展示在活动列表中。`,
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await deleteActivity(record.id);
                      message.success('活动已删除');
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
    [actionPermission, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <ManagementPage title="活动管理">
      <ManagementPageBody>
        <ManagementTable<ActivityRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          scroll={{ x: 1280 }}
          request={async (params) => {
            const response = await listActivities({
              keyword: typeof params.keyword === 'string' ? params.keyword : undefined,
              locale: params.locale as ActivityLocale | undefined,
              status: params.status as ActivityStatus | undefined,
              featured: parseFeaturedFilter(params.featured),
              pageNo: params.current,
              pageSize: params.pageSize,
            });
            return {
              data: response.records,
              total: response.total,
              success: true,
            };
          }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          toolBarRender={() =>
            actionPermission.buildToolbarActions([
              {
                permission: 'aiadc:activity:create',
                value: (
                  <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreateDrawer}>
                    新增活动
                  </Button>
                ),
              },
            ])
          }
        />
      </ManagementPageBody>

      <ManagementDrawer
        title={editingRecord ? '编辑活动' : '新增活动'}
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
            onClick: () => void saveActivity(),
          },
        ]}
      >
        <ActivityForm form={form} />
      </ManagementDrawer>
    </ManagementPage>
  );
};

const ActivityPage = () => {
  const location = useLocation();
  useEffect(() => {
    if (location.pathname === '/activities') {
      history.replace('/activities/management');
    }
  }, [location.pathname]);

  if (isActivitySearchRoute(location.pathname)) {
    return <ActivitySearchView />;
  }

  if (isActivityManagementRoute(location.pathname) || location.pathname === '/activities') {
    return <ActivityManagementView />;
  }

  return <ActivityManagementView />;
};

export default ActivityPage;
