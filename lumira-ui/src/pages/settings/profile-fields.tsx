import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined } from "@ant-design/icons";
import { getLocale } from "@umijs/max";
import { useEffect, useMemo, useState } from "react";
import { ManagementPage } from "@/features/management/ManagementPage";
import { ManagementPageBody } from "@/features/management/ManagementPageBody";
import { useActionPermission } from "@/features/permissions/useActionPermission";
import { useResponsive } from "@/hooks/useResponsive";
import { normalizeLocale } from "@/i18n/locale";
import { request } from "@/services/common/request";
import { message } from "@/theme/antdFeedbackBridge";
import { APP_SPACING, resolveResponsiveValue } from "@/theme/spacing";
import type { ProfileFieldSetting } from "@/types/api";
import { API_OPTS, showErrorMessage } from "@/utils/errorMessage";

const isEnglishLocale = () => normalizeLocale(getLocale()) === "en-US";
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const FIELD_TYPE_OPTIONS = [
  { label: t("单行文本", "Text"), value: "TEXT" },
  { label: t("多行文本", "Textarea"), value: "TEXTAREA" },
  { label: t("数字", "Number"), value: "NUMBER" },
  { label: t("日期", "Date"), value: "DATE" },
  { label: t("下拉选择", "Select"), value: "SELECT" },
];

type CustomFieldFormValues = {
  fieldKey: string;
  fieldLabel: string;
  fieldDescription?: string;
  fieldType: string;
  placeholder?: string;
  groupLabel?: string;
  required?: boolean;
  visible?: boolean;
  weight?: number;
  sortNo?: number;
};

const normalizeCustomFieldKey = (value: string) =>
  value.trim().replace(/[^A-Za-z0-9_]/g, "");

const ProfileFieldManagementPage = () => {
  const actionPermission = useActionPermission();
  const { isMobile } = useResponsive();
  const canUpdate = actionPermission.can("system:config:update");
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);
  const [form] = Form.useForm<CustomFieldFormValues>();
  const [items, setItems] = useState<ProfileFieldSetting[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingFieldKey, setEditingFieldKey] = useState<string | null>(null);
  const editingItem = useMemo(
    () => items.find((item) => item.fieldKey === editingFieldKey) || null,
    [editingFieldKey, items],
  );
  const editingSystemFieldType = Boolean(editingItem && !editingItem.custom);

  const enabledWeight = useMemo(
    () =>
      items
        .filter((item) => item.visible)
        .reduce((total, item) => total + (item.weight || 0), 0),
    [items],
  );

  const loadItems = async () => {
    setLoading(true);
    try {
      const result = await request<ProfileFieldSetting[]>(
        "/v1/system/profile-field-settings",
        {
          method: "GET",
          ...API_OPTS.NO_REDIRECT,
        },
      );
      setItems(result);
    } catch (error) {
      showErrorMessage(
        error,
        t("加载字段配置失败", "Failed to load field settings"),
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadItems();
  }, []);

  const saveItems = async (nextItems: ProfileFieldSetting[]) => {
    setSaving(true);
    try {
      const result = await request<ProfileFieldSetting[]>(
        "/v1/system/profile-field-settings",
        {
          method: "PUT",
          data: {
            items: nextItems.map((item) => ({
              fieldKey: item.fieldKey,
              fieldLabel: item.fieldLabel,
              fieldDescription: item.fieldDescription,
              fieldType: item.fieldType,
              required: Boolean(item.required),
              placeholder: item.placeholder,
              groupKey: item.groupKey,
              groupLabel: item.groupLabel,
              visible: Boolean(item.visible),
              weight: item.weight ?? 1,
              sortNo: item.sortNo ?? 1000,
              custom: Boolean(item.custom),
            })),
          },
          ...API_OPTS.NO_REDIRECT,
        },
      );
      setItems(result);
      message.success(t("字段配置已保存", "Field settings saved"));
      return true;
    } catch (error) {
      showErrorMessage(
        error,
        t("保存字段配置失败", "Failed to save field settings"),
      );
      return false;
    } finally {
      setSaving(false);
    }
  };

  const patchItem = (fieldKey: string, patch: Partial<ProfileFieldSetting>) => {
    const nextItems = items.map((item) =>
      item.fieldKey === fieldKey ? { ...item, ...patch } : item,
    );
    setItems(nextItems);
    void saveItems(nextItems);
  };

  const openAddDrawer = () => {
    setEditingFieldKey(null);
    form.resetFields();
    form.setFieldsValue({
      fieldType: "TEXT",
      groupLabel: t("自定义资料", "Custom profile"),
      required: false,
      visible: true,
      weight: 5,
      sortNo: (items.length + 1) * 10,
    });
    setDrawerOpen(true);
  };

  const openEditDrawer = (item: ProfileFieldSetting) => {
    setEditingFieldKey(item.fieldKey);
    form.resetFields();
    form.setFieldsValue({
      fieldKey: item.fieldKey,
      fieldLabel: item.fieldLabel,
      fieldDescription: item.fieldDescription || undefined,
      fieldType: item.fieldType || "TEXT",
      placeholder: item.placeholder || undefined,
      groupLabel: item.groupLabel || t("自定义资料", "Custom profile"),
      required: Boolean(item.required),
      visible: Boolean(item.visible),
      weight: item.weight ?? 5,
      sortNo: item.sortNo ?? 1000,
    });
    setDrawerOpen(true);
  };

  const closeDrawer = (force = false) => {
    if (!force && form.isFieldsTouched()) {
      Modal.confirm({
        title: t("确认退出编辑？", "Discard changes?"),
        content: t(
          "当前字段修改尚未保存，退出后将丢失本次编辑内容。",
          "Your field changes have not been saved. Closing now will discard them.",
        ),
        okText: t("退出", "Discard"),
        cancelText: t("继续编辑", "Keep editing"),
        okButtonProps: { danger: true },
        onOk: () => closeDrawer(true),
      });
      return;
    }
    setDrawerOpen(false);
    setEditingFieldKey(null);
    form.resetFields();
  };

  const handleSubmitField = async () => {
    const values = await form.validateFields();
    const editing = items.find((item) => item.fieldKey === editingFieldKey);
    const fieldKey = editing ? editing.fieldKey : normalizeCustomFieldKey(values.fieldKey);
    if (
      items.some(
        (item) =>
          item.fieldKey === fieldKey && item.fieldKey !== editingFieldKey,
      )
    ) {
      message.warning(
        t("字段标识已存在，请换一个", "This field key already exists."),
      );
      return;
    }
    const nextItem: ProfileFieldSetting = editing && !editing.custom
      ? {
          ...editing,
          fieldLabel: values.fieldLabel.trim(),
          fieldDescription: values.fieldDescription?.trim() || editing.fieldDescription,
          placeholder: values.placeholder?.trim() || null,
          groupLabel: values.groupLabel?.trim() || editing.groupLabel,
          fieldType: editing.fieldType,
          required: Boolean(values.required),
          visible: values.visible ?? true,
          weight: values.weight ?? editing.weight ?? 1,
          sortNo: values.sortNo ?? editing.sortNo ?? 1000,
          custom: false,
        }
      : {
      fieldKey,
      fieldLabel: values.fieldLabel.trim(),
      fieldDescription:
        values.fieldDescription?.trim() ||
        t("自定义资料字段", "Custom profile field"),
      fieldType: values.fieldType || "TEXT",
      placeholder: values.placeholder?.trim() || null,
      groupKey: "custom",
      groupLabel:
        values.groupLabel?.trim() || t("自定义资料", "Custom profile"),
      required: Boolean(values.required),
      visible: values.visible ?? true,
      weight: values.weight ?? 5,
      sortNo: values.sortNo ?? (items.length + 1) * 10,
      custom: true,
    };
    const nextItems = editingFieldKey
      ? items.map((item) =>
          item.fieldKey === editingFieldKey ? nextItem : item,
        )
      : [...items, nextItem];
    setItems(nextItems);
    if (await saveItems(nextItems)) {
      closeDrawer(true);
    }
  };

  const handleRemoveCustomField = (fieldKey: string) => {
    const nextItems = items.filter((item) => item.fieldKey !== fieldKey);
    setItems(nextItems);
    void saveItems(nextItems);
  };

  const columns: ColumnsType<ProfileFieldSetting> = [
    {
      title: t("字段", "Field"),
      dataIndex: "fieldLabel",
      width: 220,
      render: (_value, item) => (
        <Space wrap size={tagWrapGap} style={{ minWidth: 0 }}>
          <Typography.Text strong>{item.fieldLabel}</Typography.Text>
          <Tag color={item.custom ? "purple" : "blue"}>
            {item.custom ? t("自定义", "Custom") : t("系统", "System")}
          </Tag>
        </Space>
      ),
    },
    {
      title: t("类型", "Type"),
      dataIndex: "fieldType",
      width: 120,
      render: (value) => <Tag>{value || "TEXT"}</Tag>,
    },
    {
      title: t("占位提示", "Placeholder"),
      dataIndex: "placeholder",
      width: 220,
      render: (_value, item) => (
        <Typography.Text type={item.placeholder ? undefined : "secondary"}>
          {item.placeholder || "-"}
        </Typography.Text>
      ),
    },
    {
      title: t("必填", "Required"),
      dataIndex: "required",
      width: 96,
      align: "center",
      render: (_value, item) => (
        <Switch
          checked={Boolean(item.required)}
          disabled={!canUpdate || saving}
          onChange={(checked) =>
            patchItem(item.fieldKey, { required: checked })
          }
        />
      ),
    },
    {
      title: t("权重", "Weight"),
      dataIndex: "weight",
      width: 112,
      render: (_value, item) => (
        <InputNumber
          min={1}
          precision={0}
          controls={false}
          disabled={!canUpdate || saving}
          value={item.weight ?? 1}
          style={{ width: "var(--saas-spacing-100)" }}
          onChange={(value) => {
            if (value != null) {
              patchItem(item.fieldKey, { weight: value });
            }
          }}
        />
      ),
    },
    {
      title: t("排序", "Sort"),
      dataIndex: "sortNo",
      width: 112,
      render: (_value, item) => (
        <InputNumber
          min={1}
          precision={0}
          controls={false}
          disabled={!canUpdate || saving}
          value={item.sortNo ?? 1000}
          style={{ width: "var(--saas-spacing-100)" }}
          onChange={(value) => {
            if (value != null) {
              patchItem(item.fieldKey, { sortNo: value });
            }
          }}
        />
      ),
    },
    {
      title: t("启用", "Enabled"),
      dataIndex: "visible",
      width: 96,
      align: "center",
      render: (_value, item) => (
        <Switch
          checked={Boolean(item.visible)}
          disabled={!canUpdate || saving}
          onChange={(checked) => patchItem(item.fieldKey, { visible: checked })}
        />
      ),
    },
    {
      title: t("操作", "Actions"),
      key: "actions",
      width: 128,
      align: "center",
      render: (_value, item) => (
          <Space size={tagWrapGap} wrap={false}>
            <Button
              type="link"
              size="small"
              disabled={!canUpdate || saving}
              aria-label={t("修改字段", "Edit field")}
              onClick={() => openEditDrawer(item)}
            >
              {t("编辑", "Edit")}
            </Button>
            {item.custom ? (
              <Popconfirm
              title={t("删除自定义字段", "Delete custom field")}
              description={t(
                "删除后将立即保存，确认删除吗？",
                "Deletion is saved immediately. Continue?",
              )}
              okText={t("删除", "Delete")}
              cancelText={t("取消", "Cancel")}
              onConfirm={() => handleRemoveCustomField(item.fieldKey)}
            >
              <Button
                danger
                type="link"
                size="small"
                disabled={!canUpdate || saving}
                aria-label={t("删除字段", "Delete field")}
              >
                {t("删除", "Delete")}
              </Button>
              </Popconfirm>
            ) : null}
          </Space>
        ),
    },
  ];
  const visibleColumns = columns;

  return (
    <ManagementPage
      ghost
      title={t("字段管理", "Field management")}
      content={null}
    >
      <ManagementPageBody>
        <Card
          title={t("个人中心字段定义", "Profile field definitions")}
          extra={
            <Space wrap>
              <Button
                onClick={() => void loadItems()}
                disabled={loading || saving}
              >
                {t("刷新", "Refresh")}
              </Button>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                disabled={!canUpdate || saving}
                onClick={openAddDrawer}
              >
                {t("新增字段", "Add field")}
              </Button>
            </Space>
          }
        >
          <Space
            direction="vertical"
            size={sectionGap}
            style={{ width: "100%" }}
          >
            <Alert
              type={enabledWeight === 100 ? "success" : "info"}
              showIcon
              message={t(
                `当前启用字段权重总和：${enabledWeight}`,
                `Enabled weight total: ${enabledWeight}`,
              )}
            />
            {loading ? (
              <div
                style={{
                  display: "grid",
                  placeItems: "center",
                  minHeight: "var(--saas-spacing-240)",
                }}
              >
                <Spin />
              </div>
            ) : items.length ? (
              <Table
                rowKey="fieldKey"
                columns={visibleColumns}
                dataSource={[...items].sort(
                  (left, right) =>
                    (left.sortNo ?? 1000) - (right.sortNo ?? 1000),
                )}
                pagination={false}
                size="middle"
                scroll={isMobile ? { x: 960 } : undefined}
              />
            ) : (
              <Empty
                description={t("暂无可配置字段", "No configurable fields")}
              />
            )}

          </Space>
        </Card>
      </ManagementPageBody>

      <Drawer
        title={
          editingFieldKey
            ? t("编辑字段", "Edit field")
            : t("新增自定义字段", "Add custom field")
        }
        width={isMobile ? "100%" : 520}
        open={drawerOpen}
        destroyOnHidden
        forceRender
        onClose={() => closeDrawer()}
        footer={
          <Space>
            <Button onClick={() => closeDrawer()}>{t("取消", "Cancel")}</Button>
            <Button
              type="primary"
              loading={saving}
              disabled={loading || !canUpdate || saving}
              onClick={() => void handleSubmitField()}
            >
              {editingFieldKey
                ? t("保存修改", "Save changes")
                : t("保存字段", "Save field")}
            </Button>
          </Space>
        }
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            fieldType: "TEXT",
            groupLabel: t("自定义资料", "Custom profile"),
            required: false,
            visible: true,
            weight: 5,
            sortNo: (items.length + 1) * 10,
          }}
        >
          <Form.Item
            name="fieldLabel"
            label={t("字段名称", "Field label")}
            rules={[
              {
                required: true,
                message: t("请输入字段名称", "Please enter a field label"),
              },
            ]}
          >
            <Input
              placeholder={t("例如：学校", "e.g. School")}
              maxLength={64}
            />
          </Form.Item>
          <Form.Item
            name="fieldKey"
            label={t("字段标识", "Field key")}
            normalize={normalizeCustomFieldKey}
            rules={[
              {
                required: true,
                message: t("请输入字段标识", "Please enter a field key"),
              },
              {
                pattern: /^[A-Za-z][A-Za-z0-9_]{1,63}$/,
                message: t(
                  "以字母开头，仅支持字母、数字、下划线",
                  "Start with a letter; use letters, numbers, and underscores only",
                ),
              },
            ]}
          >
            <Input
              placeholder={t("例如：school", "e.g. school")}
              maxLength={64}
              disabled={Boolean(editingFieldKey)}
            />
          </Form.Item>
          <Form.Item
            name="fieldType"
            label={t("字段类型", "Field type")}
            rules={[
              {
                required: true,
                message: t("请选择字段类型", "Please select a field type"),
              },
            ]}
          >
            <Select options={FIELD_TYPE_OPTIONS} disabled={editingSystemFieldType} />
          </Form.Item>
          <Form.Item name="placeholder" label={t("占位提示", "Placeholder")}>
            <Input
              placeholder={t("例如：请输入学校名称", "e.g. Enter school name")}
              maxLength={120}
            />
          </Form.Item>
          <Form.Item name="fieldDescription" label={t("说明", "Description")}>
            <Input.TextArea
              autoSize={{ minRows: 2, maxRows: 4 }}
              placeholder={t(
                "说明这个字段的用途",
                "Describe how this field is used",
              )}
              maxLength={200}
            />
          </Form.Item>
          <Form.Item name="groupLabel" label={t("分组", "Group")}>
            <Input
              placeholder={t("例如：教育信息", "e.g. Education")}
              maxLength={64}
            />
          </Form.Item>
          <Space size={tagWrapGap} wrap>
            <Form.Item
              name="required"
              label={t("必填", "Required")}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            <Form.Item
              name="visible"
              label={t("启用", "Enabled")}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            <Form.Item name="weight" label={t("权重", "Weight")}>
              <InputNumber min={1} precision={0} controls={false} />
            </Form.Item>
            <Form.Item name="sortNo" label={t("排序", "Sort")}>
              <InputNumber min={1} precision={0} controls={false} />
            </Form.Item>
          </Space>
        </Form>
      </Drawer>
    </ManagementPage>
  );
};

export default ProfileFieldManagementPage;
