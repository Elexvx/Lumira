import { Button, DatePicker, Descriptions, Drawer, Form, Input, Select, Space, Tag, Typography, message } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useRef, useState } from 'react';
import { PageContainer, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { buildMobilePagination, buildTableRequest, buildTableScroll } from '@/features/table/proTable';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { useResponsive } from '@/hooks/useResponsive';
import { useStandardFormProps } from '@/features/form/config';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { iamService } from '@/services/iam';
import { messageService } from '@/services/message';
import { userService } from '@/services/user';
import type { MessageNoticeRecord, RoleRecord, UserRecord } from '@/types/api';
import { TableActionBar } from '@/features/table/TableActionBar';
import { confirmAction } from '@/utils/confirm';
import { notifyMessageCenterRefresh } from '@/components/message-center/messageCenterEvents';

const TARGET_SCOPE_LABELS: Record<string, string> = {
  TENANT: '租户全员',
  USER: '指定用户',
  ROLE: '角色分组',
};

const PUBLISH_STATUS_LABELS: Record<string, { color: string; text: string }> = {
  PUBLISHED: { color: 'green', text: '已发布' },
  RETRACTED: { color: 'default', text: '已撤回' },
};

const READ_STATUS_LABELS: Record<string, { color: string; text: string }> = {
  true: { color: 'blue', text: '已读' },
  false: { color: 'red', text: '未读' },
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }

  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : value;
};

const resolveSortParams = (sorter?: Record<string, unknown>) => {
  if (!sorter) {
    return {};
  }

  const [entry] = Object.entries(sorter).filter(([, order]) => order === 'ascend' || order === 'descend');
  if (!entry) {
    return {};
  }

  const [sortField, sortOrder] = entry;
  return {
    sortField,
    sortOrder: sortOrder === 'ascend' ? 'ASC' : 'DESC',
  };
};

const renderTag = (label?: string | null, color = 'default') => {
  if (!label) {
    return '-';
  }

  return <Tag color={color}>{label}</Tag>;
};

const renderEnumTag = (value: string | boolean | undefined | null, labels: Record<string, { color: string; text: string }>) => {
  if (value === undefined || value === null) {
    return '-';
  }

  const item = labels[String(value)];
  return item ? <Tag color={item.color}>{item.text}</Tag> : String(value);
};

const NotificationsPage = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const [detailRecord, setDetailRecord] = useState<MessageNoticeRecord | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [userSearch, setUserSearch] = useState('');
  const [userOptions, setUserOptions] = useState<UserRecord[]>([]);
  const [userLoading, setUserLoading] = useState(false);
  const [roleOptions, setRoleOptions] = useState<RoleRecord[]>([]);
  const [roleLoading, setRoleLoading] = useState(false);
  const [form] = Form.useForm<{
    title: string;
    content: string;
    targetScope: 'TENANT' | 'USER' | 'ROLE';
    targetUserId?: number;
    targetRoleId?: number;
  }>();
  const targetScope = Form.useWatch('targetScope', form);
  const detailDescriptionsProps = useDetailDescriptionsProps({
    column: responsive.isMobile ? 1 : 2,
  });
  const publishFormProps = useStandardFormProps({
    form,
    initialValues: {
      title: '',
      content: '',
      targetScope: 'TENANT',
      targetUserId: undefined,
      targetRoleId: undefined,
    },
  });

  const canManualPublish =
    actionPermission.can('message:message:write') ||
    actionPermission.can('system:notification:write');

  const requestOptions = useMemo(
    () => ({
      autoRedirectOnUnauthorized: false,
      silent: true,
    }),
    [],
  );

  useEffect(() => {
    if (!publishOpen || !canManualPublish || roleOptions.length > 0) {
      return;
    }

    let active = true;
    setRoleLoading(true);
    void iamService
      .roles(
        {
          pageNo: 1,
          pageSize: 200,
        },
        requestOptions,
      )
      .then((result) => {
        if (!active) {
          return;
        }
        setRoleOptions(result.records || []);
      })
      .catch(() => {
        if (active) {
          message.error('角色列表加载失败，请稍后重试');
        }
      })
      .finally(() => {
        if (active) {
          setRoleLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [canManualPublish, publishOpen, requestOptions, roleOptions.length]);

  useEffect(() => {
    if (!publishOpen || targetScope !== 'USER') {
      return;
    }

    let active = true;
    const timer = window.setTimeout(() => {
      setUserLoading(true);
      void userService
        .list(
          {
            username: userSearch || undefined,
            pageNo: 1,
            pageSize: 20,
          },
          requestOptions,
        )
        .then((result) => {
          if (!active) {
            return;
          }
          setUserOptions(result.records || []);
        })
        .catch(() => {
          if (active) {
            message.error('用户名列表加载失败，请稍后重试');
          }
        })
        .finally(() => {
          if (active) {
            setUserLoading(false);
          }
        });
    }, 250);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [publishOpen, requestOptions, targetScope, userSearch]);

  const closePublishDrawer = () => {
    setPublishOpen(false);
    setUserSearch('');
    setUserOptions([]);
    setUserLoading(false);
    setRoleLoading(false);
    form.resetFields();
  };

  const openPublishDrawer = () => {
    form.resetFields();
    setUserSearch('');
    setUserOptions([]);
    setUserLoading(false);
    setRoleLoading(false);
    form.setFieldsValue({ title: '', content: '', targetScope: 'TENANT', targetUserId: undefined, targetRoleId: undefined });
    setPublishOpen(true);
  };

  const handlePublish = async () => {
    if (!canManualPublish) {
      return;
    }

    setPublishing(true);
    try {
      const values = await form.validateFields();
      await messageService.createMessage(
        {
          title: values.title,
          content: values.content,
          targetScope: values.targetScope,
          targetUserId: values.targetScope === 'USER' ? values.targetUserId : undefined,
          targetRoleId: values.targetScope === 'ROLE' ? values.targetRoleId : undefined,
        },
        requestOptions,
      );
      message.success('站内信已发布');
      closePublishDrawer();
      notifyMessageCenterRefresh();
      actionRef.current?.reload();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      message.error(error instanceof Error ? error.message : '站内信发布失败，请稍后重试');
    } finally {
      setPublishing(false);
    }
  };

  const handleOpenDetail = (record: MessageNoticeRecord) => {
    setDetailRecord(record);
    setDetailOpen(true);
  };

  const handleRetract = (record: MessageNoticeRecord) => {
    confirmAction({
      title: '撤回站内信',
      content: `确认撤回「${record.title}」吗？撤回后该记录将保留在归档中，但不再继续投递。`,
      okText: '确认撤回',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await messageService.retractMessage(record.id, requestOptions);
          message.success('站内信已撤回');
          setDetailRecord((current) => (current && current.id === record.id ? { ...current, publishStatus: 'RETRACTED' } : current));
          notifyMessageCenterRefresh();
          actionRef.current?.reload();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '站内信撤回失败，请稍后重试');
        }
      },
    });
  };

  const columns = useMemo<ProColumns<MessageNoticeRecord>[]>(
    () => [
      {
        title: '关键字',
        dataIndex: 'keyword',
        hideInTable: true,
        renderFormItem: () => <Input allowClear placeholder="按标题或内容搜索" />,
      },
      {
        title: '标题',
        dataIndex: 'title',
        ellipsis: true,
        copyable: true,
        search: false,
        render: (_, record) => <Typography.Text strong>{record.title}</Typography.Text>,
      },
      {
        title: '目标范围',
        dataIndex: 'targetScope',
        width: 120,
        valueEnum: {
          TENANT: { text: '租户全员' },
          USER: { text: '指定用户' },
          ROLE: { text: '角色分组' },
        },
        renderFormItem: () => (
          <Select
            allowClear
            options={[
              { label: '租户全员', value: 'TENANT' },
              { label: '指定用户', value: 'USER' },
              { label: '角色分组', value: 'ROLE' },
            ]}
            placeholder="全部"
          />
        ),
        render: (_, record) => renderTag(TARGET_SCOPE_LABELS[record.targetScope] || record.targetScope, 'geekblue'),
      },
      {
        title: '目标用户',
        dataIndex: 'targetUserName',
        width: 160,
        search: false,
        responsive: ['lg', 'xl', 'xxl'],
        render: (_, record) =>
          record.targetScope === 'USER'
            ? record.targetUserName || (record.targetUserId ? String(record.targetUserId) : '-')
            : '-',
      },
      {
        title: '目标角色',
        dataIndex: 'targetRoleName',
        width: 160,
        search: false,
        responsive: ['lg', 'xl', 'xxl'],
        render: (_, record) =>
          record.targetScope === 'ROLE'
            ? record.targetRoleName || (record.targetRoleId ? String(record.targetRoleId) : '-')
            : '-',
      },
      {
        title: '状态',
        dataIndex: 'publishStatus',
        width: 110,
        valueEnum: {
          PUBLISHED: { text: '已发布' },
          RETRACTED: { text: '已撤回' },
        },
        renderFormItem: () => (
          <Select
            allowClear
            options={[
              { label: '已发布', value: 'PUBLISHED' },
              { label: '已撤回', value: 'RETRACTED' },
            ]}
            placeholder="全部"
          />
        ),
        render: (_, record) => renderEnumTag(record.publishStatus, PUBLISH_STATUS_LABELS),
      },
      {
        title: '阅读状态',
        dataIndex: 'readFlag',
        width: 110,
        search: false,
        responsive: ['lg', 'xl', 'xxl'],
        render: (_, record) => renderEnumTag(Boolean(record.readFlag), READ_STATUS_LABELS),
      },
      {
        title: '发布时间',
        dataIndex: 'publishedAt',
        width: 180,
        search: false,
        sorter: true,
        render: (_, record) => formatDateTime(record.publishedAt || record.createdAt),
      },
      {
        title: '发布时间范围',
        dataIndex: 'publishedAtRange',
        hideInTable: true,
        renderFormItem: () => <DatePicker.RangePicker showTime style={{ width: '100%' }} />,
        search: {
          transform: (value) => {
            if (!Array.isArray(value) || value.length !== 2) {
              return {};
            }

            const [start, end] = value as unknown as [
              { format: (pattern: string) => string },
              { format: (pattern: string) => string },
            ];

            return {
              publishedAtStart: start?.format?.('YYYY-MM-DDTHH:mm:ss'),
              publishedAtEnd: end?.format?.('YYYY-MM-DDTHH:mm:ss'),
            };
          },
        },
      },
      {
        title: '操作',
        valueType: 'option',
        width: 160,
        fixed: responsive.isDesktop ? 'right' : undefined,
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={[
              {
                key: 'detail',
                label: '详情',
                onClick: () => handleOpenDetail(record),
              },
              {
                key: 'retract',
                label: '撤回',
                danger: true,
                hidden: record.publishStatus === 'RETRACTED',
                onClick: () => handleRetract(record),
              },
            ]}
          />
        ),
      },
    ],
    [responsive.isDesktop, responsive.isMobile, detailRecord],
  );

  const toolbar = useMemo(() => {
    const items = [
      <Button key="refresh" onClick={() => actionRef.current?.reload()}>
        刷新
      </Button>,
    ];

    if (canManualPublish) {
      items.push(
        <Button key="manual-send" type="primary" onClick={openPublishDrawer}>
          手动发布
        </Button>,
      );
    }

    return items;
  }, [canManualPublish, openPublishDrawer]);

  return (
    <PageContainer
      title="站内信归档"
      content="这里展示租户内的站内信归档记录，支持手动发布与撤回。"
      className="saas-management-page"
      ghost
    >
      <div className="saas-table-wrap">
        <ProTable<MessageNoticeRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          options={false}
          pagination={buildMobilePagination({ showSizeChanger: true, pageSize: 10 }, responsive.isMobile)}
          scroll={buildTableScroll(columns, responsive.isMobile, { wide: true })}
          search={{
            labelWidth: 'auto',
            span: responsive.isMobile ? 24 : 8,
          }}
          request={buildTableRequest((params, sorter) => messageService.archiveMessages({ ...params, ...resolveSortParams(sorter) }, requestOptions))}
          toolBarRender={() => toolbar}
        />
      </div>

      <Drawer
        title={detailRecord ? `站内信详情 · ${detailRecord.title}` : '站内信详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setDetailRecord(null);
        }}
        width={responsive.isMobile ? '100vw' : 760}
        destroyOnClose
        extra={
          detailRecord && detailRecord.publishStatus === 'PUBLISHED' ? (
            <Button danger onClick={() => handleRetract(detailRecord)}>
              撤回
            </Button>
          ) : null
        }
      >
        {detailRecord ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions {...detailDescriptionsProps} bordered>
              <Descriptions.Item label="标题">{detailRecord.title}</Descriptions.Item>
              <Descriptions.Item label="目标范围">{TARGET_SCOPE_LABELS[detailRecord.targetScope] || detailRecord.targetScope}</Descriptions.Item>
              <Descriptions.Item label="目标用户">
                {detailRecord.targetScope === 'USER'
                  ? detailRecord.targetUserName || (detailRecord.targetUserId ? String(detailRecord.targetUserId) : '-')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="目标角色">
                {detailRecord.targetScope === 'ROLE'
                  ? detailRecord.targetRoleName || (detailRecord.targetRoleId ? String(detailRecord.targetRoleId) : '-')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="状态">{PUBLISH_STATUS_LABELS[detailRecord.publishStatus]?.text || detailRecord.publishStatus}</Descriptions.Item>
              <Descriptions.Item label="发布时间">{formatDateTime(detailRecord.publishedAt || detailRecord.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="当前阅读状态">{detailRecord.readFlag ? '已读' : '未读'}</Descriptions.Item>
              <Descriptions.Item label="创建人 ID">{detailRecord.createdBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="更新人 ID">{detailRecord.updatedBy ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="已读时间">{formatDateTime(detailRecord.readAt)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatDateTime(detailRecord.updatedAt)}</Descriptions.Item>
            </Descriptions>

            <div>
              <Typography.Title level={5} style={{ marginTop: 0 }}>
                内容
              </Typography.Title>
              <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                {detailRecord.content}
              </Typography.Paragraph>
            </div>
          </Space>
        ) : null}
      </Drawer>

      <Drawer
        title="手动发布站内信"
        open={publishOpen}
        onClose={closePublishDrawer}
        width={responsive.isMobile ? '100vw' : 720}
        destroyOnClose
        footer={
          <Space style={{ justifyContent: 'flex-end', width: '100%' }}>
            <Button onClick={closePublishDrawer}>取消</Button>
            <Button type="primary" loading={publishing} onClick={() => void handlePublish()}>
              发布
            </Button>
          </Space>
        }
      >
        <Form
          {...publishFormProps}
          onValuesChange={(changedValues) => {
            if (Object.prototype.hasOwnProperty.call(changedValues, 'targetScope')) {
              if (changedValues.targetScope === 'TENANT') {
                form.setFieldsValue({ targetUserId: undefined, targetRoleId: undefined });
                setUserOptions([]);
                setUserLoading(false);
              } else if (changedValues.targetScope === 'USER') {
                form.setFieldsValue({ targetRoleId: undefined });
                setRoleLoading(false);
              } else if (changedValues.targetScope === 'ROLE') {
                form.setFieldsValue({ targetUserId: undefined });
                setUserOptions([]);
                setUserLoading(false);
              }
            }
          }}
        >
          <Form.Item
            name="title"
            label="站内信标题"
            rules={[
              { required: true, message: '请输入站内信标题' },
              { max: 128, message: '标题长度不能超过 128 个字符' },
            ]}
          >
            <Input placeholder="例如：系统维护提醒" />
          </Form.Item>
          <Form.Item
            name="content"
            label="站内信内容"
            rules={[
              { required: true, message: '请输入站内信内容' },
              { max: 2000, message: '内容长度不能超过 2000 个字符' },
            ]}
          >
            <Input.TextArea rows={8} placeholder="请输入要发送给租户用户的站内信内容" />
          </Form.Item>
          <Form.Item name="targetScope" label="目标范围" rules={[{ required: true, message: '请选择目标范围' }]}>
            <Select
              options={[
                { label: '租户全员', value: 'TENANT' },
                { label: '指定用户', value: 'USER' },
                { label: '角色分组', value: 'ROLE' },
              ]}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, current) => prev.targetScope !== current.targetScope}>
            {({ getFieldValue }) => {
              const currentTargetScope = getFieldValue('targetScope');

              if (currentTargetScope === 'USER') {
                return (
                  <Form.Item
                    name="targetUserId"
                    label="目标用户名"
                    rules={[
                      {
                        validator: async (_, value) => {
                          if (currentTargetScope === 'USER' && !value) {
                            throw new Error('请选择目标用户名');
                          }
                        },
                      },
                    ]}
                  >
                    <Select
                      allowClear
                      showSearch
                      filterOption={false}
                      loading={userLoading}
                      placeholder="输入用户名搜索"
                      onSearch={(value) => setUserSearch(value.trim())}
                      options={userOptions.map((item) => {
                        const displayName = item.realName || item.nickname;
                        return {
                          label: displayName ? `${item.username} · ${displayName}` : item.username,
                          value: item.id,
                        };
                      })}
                      notFoundContent={userLoading ? '加载中...' : '暂无匹配用户'}
                    />
                  </Form.Item>
                );
              }

              if (currentTargetScope === 'ROLE') {
                return (
                  <Form.Item
                    name="targetRoleId"
                    label="角色分组"
                    rules={[
                      {
                        validator: async (_, value) => {
                          if (currentTargetScope === 'ROLE' && !value) {
                            throw new Error('请选择角色分组');
                          }
                        },
                      },
                    ]}
                  >
                    <Select
                      allowClear
                      showSearch
                      loading={roleLoading}
                      placeholder="请选择角色分组"
                      filterOption={(input, option) => String(option?.label || '').toLowerCase().includes(input.toLowerCase())}
                      options={roleOptions.map((item) => ({
                        label: item.roleName,
                        value: item.id,
                      }))}
                      notFoundContent={roleLoading ? '加载中...' : '暂无角色'}
                    />
                  </Form.Item>
                );
              }

              return null;
            }}
          </Form.Item>
        </Form>
      </Drawer>
    </PageContainer>
  );
};

export default NotificationsPage;
