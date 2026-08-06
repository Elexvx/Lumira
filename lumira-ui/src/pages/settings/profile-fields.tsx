import {
  Alert,
  Button,
  Card,
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
import { useEffect, useMemo, useState } from "react";
import { ManagementPage } from "@/features/management/ManagementPage";
import { ManagementPageBody } from "@/features/management/ManagementPageBody";
import { StandardDrawer } from "@/features/management/StandardDrawer";
import { useActionPermission } from "@/features/permissions/useActionPermission";
import { useResponsive } from "@/hooks/useResponsive";
import { request } from "@/services/common/request";
import { message } from "@/theme/antdFeedbackBridge";
import { APP_SPACING, resolveResponsiveValue } from "@/theme/spacing";
import type { ProfileFieldSetting } from "@/types/api";
import { API_OPTS, showErrorMessage } from "@/utils/errorMessage";
import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

const FIELD_TYPE_OPTIONS = [
  { label: t('ui.settings.profile-fields.text'), value: "TEXT" },
  { label: t('ui.settings.profile-fields.textarea'), value: "TEXTAREA" },
  { label: t('ui.settings.profile-fields.number'), value: "NUMBER" },
  { label: t('ui.settings.profile-fields.date'), value: "DATE" },
  { label: t('ui.settings.profile-fields.select'), value: "SELECT" },
];

const PROFILE_PAGE_KEY = "PROFILE";

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
          `/v1/system/profile-field-settings?pageKey=${PROFILE_PAGE_KEY}`,
        {
          method: "GET",
          ...API_OPTS.NO_REDIRECT,
        },
      );
      setItems(result);
    } catch (error) {
      showErrorMessage(
        error,
        t('ui.settings.profile-fields.failedToLoadFieldSettings'),
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
        `/v1/system/profile-field-settings?pageKey=${PROFILE_PAGE_KEY}`,
        {
          method: "PUT",
          data: {
            pageKey: PROFILE_PAGE_KEY,
            items: nextItems.map((item) => ({
              fieldKey: item.fieldKey,
              pageKey: PROFILE_PAGE_KEY,
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
      message.success(t('ui.settings.profile-fields.fieldSettingsSaved'));
      return true;
    } catch (error) {
      showErrorMessage(
        error,
        t('ui.settings.profile-fields.failedToSaveFieldSettings'),
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
      groupLabel: t('ui.settings.profile-fields.customProfile'),
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
      groupLabel: item.groupLabel || t('ui.settings.profile-fields.customProfile'),
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
        title: t('ui.settings.profile-fields.discardChanges'),
        content: t('ui.settings.profile-fields.yourFieldChangesHaveNotBeenSavedClosing'),
        okText: t('ui.settings.profile-fields.discard'),
        cancelText: t('ui.settings.profile-fields.keepEditing'),
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
        t('ui.settings.profile-fields.thisFieldKeyAlreadyExists'),
      );
      return;
    }
    const nextItem: ProfileFieldSetting = editing && !editing.custom
      ? {
          ...editing,
          pageKey: PROFILE_PAGE_KEY,
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
      pageKey: PROFILE_PAGE_KEY,
      fieldLabel: values.fieldLabel.trim(),
      fieldDescription:
        values.fieldDescription?.trim() ||
        t('ui.settings.profile-fields.customProfileField'),
      fieldType: values.fieldType || "TEXT",
      placeholder: values.placeholder?.trim() || null,
      groupKey: "custom",
      groupLabel:
        values.groupLabel?.trim() || t('ui.settings.profile-fields.customProfile'),
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
      title: t('ui.settings.profile-fields.field'),
      dataIndex: "fieldLabel",
      width: 220,
      render: (_value, item) => (
        <Space wrap size={tagWrapGap} style={{ minWidth: 0 }}>
          <Typography.Text strong>{item.fieldLabel}</Typography.Text>
          <Tag color={item.custom ? "purple" : "blue"}>
            {item.custom ? t('ui.settings.profile-fields.custom') : t('ui.settings.profile-fields.system')}
          </Tag>
        </Space>
      ),
    },
    {
      title: t('ui.settings.profile-fields.type'),
      dataIndex: "fieldType",
      width: 120,
      render: (value) => <Tag>{value || "TEXT"}</Tag>,
    },
    {
      title: t('ui.settings.profile-fields.placeholder'),
      dataIndex: "placeholder",
      width: 220,
      render: (_value, item) => (
        <Typography.Text type={item.placeholder ? undefined : "secondary"}>
          {item.placeholder || "-"}
        </Typography.Text>
      ),
    },
    {
      title: t('ui.settings.profile-fields.required'),
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
      title: t('ui.settings.profile-fields.weight'),
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
      title: t('ui.settings.profile-fields.sort'),
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
      title: t('ui.settings.profile-fields.enabled'),
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
      title: t('ui.settings.profile-fields.actions'),
      key: "actions",
      width: 128,
      align: "center",
      render: (_value, item) => (
          <Space size={tagWrapGap} wrap={false}>
            <Button
              type="link"
              size="small"
              disabled={!canUpdate || saving}
              aria-label={t('ui.settings.profile-fields.editField')}
              onClick={() => openEditDrawer(item)}
            >
              {t('ui.settings.profile-fields.edit')}
            </Button>
            {item.custom ? (
              <Popconfirm
              title={t('ui.settings.profile-fields.deleteCustomField')}
              description={t('ui.settings.profile-fields.deletionIsSavedImmediatelyContinue')}
              okText={t('ui.settings.profile-fields.delete')}
              cancelText={t('ui.settings.profile-fields.cancel')}
              onConfirm={() => handleRemoveCustomField(item.fieldKey)}
            >
              <Button
                danger
                type="link"
                size="small"
                disabled={!canUpdate || saving}
                aria-label={t('ui.settings.profile-fields.deleteField')}
              >
                {t('ui.settings.profile-fields.delete')}
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
      title={t('ui.settings.profile-fields.fieldManagement')}
      content={null}
    >
      <ManagementPageBody>
        <Card
          title={t('ui.settings.profile-fields.profileFieldDefinitions')}
          extra={
            <Space wrap>
              <Button
                onClick={() => void loadItems()}
                disabled={loading || saving}
              >
                {t('ui.settings.profile-fields.refresh')}
              </Button>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                disabled={!canUpdate || saving}
                onClick={openAddDrawer}
              >
                {t('ui.settings.profile-fields.addField')}
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
              message={t('ui.settings.profile-fields.enabledWeightTotal', { enabledWeight: enabledWeight })}
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
                description={t('ui.settings.profile-fields.noConfigurableFields')}
              />
            )}

          </Space>
        </Card>
      </ManagementPageBody>

      <StandardDrawer
        title={
          editingFieldKey
            ? t('ui.settings.profile-fields.editField.90d0543b')
            : t('ui.settings.profile-fields.addCustomField')
        }
        open={drawerOpen}
        destroyOnHidden
        forceRender
        onClose={() => closeDrawer()}
        footer={
          <Space>
            <Button onClick={() => closeDrawer()}>{t('ui.settings.profile-fields.cancel')}</Button>
            <Button
              type="primary"
              loading={saving}
              disabled={loading || !canUpdate || saving}
              onClick={() => void handleSubmitField()}
            >
              {editingFieldKey
                ? t('ui.settings.profile-fields.saveChanges')
                : t('ui.settings.profile-fields.saveField')}
            </Button>
          </Space>
        }
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            fieldType: "TEXT",
            groupLabel: t('ui.settings.profile-fields.customProfile'),
            required: false,
            visible: true,
            weight: 5,
            sortNo: (items.length + 1) * 10,
          }}
        >
          <Form.Item
            name="fieldLabel"
            label={t('ui.settings.profile-fields.fieldLabel')}
            rules={[
              {
                required: true,
                message: t('ui.settings.profile-fields.pleaseEnterAFieldLabel'),
              },
            ]}
          >
            <Input
              placeholder={t('ui.settings.profile-fields.eGSchool')}
              maxLength={64}
            />
          </Form.Item>
          <Form.Item
            name="fieldKey"
            label={t('ui.settings.profile-fields.fieldKey')}
            normalize={normalizeCustomFieldKey}
            rules={[
              {
                required: true,
                message: t('ui.settings.profile-fields.pleaseEnterAFieldKey'),
              },
              {
                pattern: /^[A-Za-z][A-Za-z0-9_]{1,63}$/,
                message: t('ui.settings.profile-fields.startWithALetterUseLettersNumbersAnd'),
              },
            ]}
          >
            <Input
              placeholder={t('ui.settings.profile-fields.eGSchool.f6fde123')}
              maxLength={64}
              disabled={Boolean(editingFieldKey)}
            />
          </Form.Item>
          <Form.Item
            name="fieldType"
            label={t('ui.settings.profile-fields.fieldType')}
            rules={[
              {
                required: true,
                message: t('ui.settings.profile-fields.pleaseSelectAFieldType'),
              },
            ]}
          >
            <Select options={FIELD_TYPE_OPTIONS} disabled={editingSystemFieldType} />
          </Form.Item>
          <Form.Item name="placeholder" label={t('ui.settings.profile-fields.placeholder')}>
            <Input
              placeholder={t('ui.settings.profile-fields.eGEnterSchoolName')}
              maxLength={120}
            />
          </Form.Item>
          <Form.Item name="fieldDescription" label={t('ui.settings.profile-fields.description')}>
            <Input.TextArea
              autoSize={{ minRows: 2, maxRows: 4 }}
              placeholder={t('ui.settings.profile-fields.describeHowThisFieldIsUsed')}
              maxLength={200}
            />
          </Form.Item>
          <Form.Item name="groupLabel" label={t('ui.settings.profile-fields.group')}>
            <Input
              placeholder={t('ui.settings.profile-fields.eGEducation')}
              maxLength={64}
            />
          </Form.Item>
          <Space size={tagWrapGap} wrap>
            <Form.Item
              name="required"
              label={t('ui.settings.profile-fields.required')}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            <Form.Item
              name="visible"
              label={t('ui.settings.profile-fields.enabled')}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            <Form.Item name="weight" label={t('ui.settings.profile-fields.weight')}>
              <InputNumber min={1} precision={0} controls={false} />
            </Form.Item>
            <Form.Item name="sortNo" label={t('ui.settings.profile-fields.sort')}>
              <InputNumber min={1} precision={0} controls={false} />
            </Form.Item>
          </Space>
        </Form>
      </StandardDrawer>
    </ManagementPage>
  );
};

export default ProfileFieldManagementPage;
