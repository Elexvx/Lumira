import { CheckCircleFilled, ExclamationCircleFilled } from '@ant-design/icons';
import { Button, Card, Empty, Progress, Space, Tag, Typography, theme } from 'antd';
import type { ProfileCompletionItem, ProfileCompletionSummary } from '@/types/api';

interface ProfileCompletionCardProps {
  loading: boolean;
  summary?: ProfileCompletionSummary | null;
  onActionItem: (item: ProfileCompletionItem) => void;
  compact?: boolean;
  maxVisibleIncompleteItems?: number;
}

const actionButtonText = (item: ProfileCompletionItem) => item.actionLabel || '去完善';

export const ProfileCompletionCard = ({
  loading,
  summary,
  onActionItem,
  compact = false,
  maxVisibleIncompleteItems = 3,
}: ProfileCompletionCardProps) => {
  const { token } = theme.useToken();
  const groups = summary?.groups || [];
  const incompleteItems = summary?.incompleteItems || [];
  const visibleIncompleteItems = incompleteItems.slice(0, maxVisibleIncompleteItems);
  const firstActionableItem = incompleteItems.find((item) => item.actionAvailable !== false && Boolean(item.actionType));

  if (compact) {
    return (
      <Card
        title="信息完整度"
        loading={loading}
        className="saas-profile-page__completion-card saas-profile-page__completion-card--compact"
        style={{ width: '100%' }}
        extra={summary ? <Tag color={summary.completionRate === 100 ? 'green' : 'blue'}>{summary.completionRate}%</Tag> : null}
      >
        {summary ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Space align="baseline" size={8} wrap>
              <Typography.Title level={3} style={{ margin: 0 }}>
                {summary.completionRate}%
              </Typography.Title>
              <Typography.Text type="secondary">
                {incompleteItems.length ? `${incompleteItems.length} 项待完善` : '资料已完善'}
              </Typography.Text>
              <Typography.Text type="secondary">
                {summary.score}/{summary.maxScore} 分
              </Typography.Text>
            </Space>
            <Progress
              percent={summary.completionRate}
              showInfo={false}
              strokeColor={summary.completionRate === 100 ? token.colorSuccess : token.colorPrimary}
            />
            {visibleIncompleteItems.length ? (
              <Space size={[8, 8]} wrap>
                {visibleIncompleteItems.map((item) => (
                  item.actionAvailable === false ? (
                    <Tag key={item.fieldKey}>待开启 · {item.fieldLabel}</Tag>
                  ) : (
                    <Button key={item.fieldKey} type="link" size="small" icon={<ExclamationCircleFilled />} onClick={() => onActionItem(item)}>
                      {item.fieldLabel}
                    </Button>
                  )
                ))}
                {incompleteItems.length > visibleIncompleteItems.length ? (
                  <Typography.Text type="secondary">还有 {incompleteItems.length - visibleIncompleteItems.length} 项</Typography.Text>
                ) : null}
              </Space>
            ) : null}

            {firstActionableItem ? (
              <Button type="primary" block onClick={() => onActionItem(firstActionableItem)}>
                一键去完善
              </Button>
            ) : null}
          </Space>
        ) : (
          <Empty description="当前暂无可评分字段" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Card>
    );
  }

  return (
    <Card
      title="信息完整度"
      loading={loading}
      style={{ width: '100%' }}
      extra={summary ? <Tag color={summary.completionRate === 100 ? 'green' : 'blue'}>{summary.score}/{summary.maxScore} 分</Tag> : null}
    >
      {summary ? (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Space align="baseline" size={8}>
              <Typography.Title level={3} style={{ margin: 0 }}>
                {summary.score}
              </Typography.Title>
              <Typography.Text type="secondary">/ {summary.maxScore} 分</Typography.Text>
              <Tag color={summary.completionRate === 100 ? 'green' : 'blue'}>{summary.completionRate}%</Tag>
            </Space>
            <Progress
              percent={summary.completionRate}
              showInfo={false}
              strokeColor={{ '0%': token.colorPrimary, '100%': token.colorPrimaryHover }}
            />
            <Typography.Text type="secondary">
              按当前启用字段折算，总权重 {summary.totalWeight || 0}，完成得分 {summary.earnedWeight || 0}
            </Typography.Text>
          </Space>

          {groups.length ? (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              {groups.map((group) => (
                <div
                  key={group.groupKey}
                  style={{
                    border: '1px solid var(--ant-color-border-secondary)',
                    borderRadius: 10,
                    padding: 12,
                    background: 'var(--ant-color-bg-container)',
                  }}
                >
                  <Space align="baseline" size={8} style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Space align="baseline" size={8}>
                      <Typography.Text strong>{group.groupLabel}</Typography.Text>
                      <Tag color={group.completionRate === 100 ? 'green' : 'blue'}>{group.completionRate}%</Tag>
                    </Space>
                    <Typography.Text type="secondary">
                      {group.earnedWeight || 0}/{group.totalWeight || 0}
                    </Typography.Text>
                  </Space>
                  <Space direction="vertical" size={12} style={{ width: '100%', marginTop: 12 }}>
                    {group.items.map((item) => (
                      <div
                        key={item.fieldKey}
                        style={{
                          display: 'flex',
                          flexDirection: 'row',
                          justifyContent: 'space-between',
                          alignItems: 'flex-start',
                          gap: 16,
                        }}
                      >
                        <Space align="start" size={10} style={{ minWidth: 0, flex: 1 }}>
                          {item.completed ? (
                            <CheckCircleFilled style={{ color: token.colorSuccess, fontSize: 18, marginTop: 3 }} />
                          ) : (
                            <ExclamationCircleFilled style={{ color: token.colorWarning, fontSize: 18, marginTop: 3 }} />
                          )}
                          <Space direction="vertical" size={4} style={{ minWidth: 0, flex: 1 }}>
                            <Space wrap size={8}>
                              <Typography.Text strong>{item.fieldLabel}</Typography.Text>
                              <Tag color={item.completed ? 'green' : 'gold'}>{item.completed ? '已完成' : '待完善'}</Tag>
                              <Tag color="blue">+{item.scoreContribution || 0} 分</Tag>
                            </Space>
                            {item.fieldDescription ? <Typography.Text type="secondary">{item.fieldDescription}</Typography.Text> : null}
                            <Typography.Text type="secondary">{item.valueText || '暂无'}</Typography.Text>
                            {!item.completed && item.actionHint ? <Typography.Text type="secondary">{item.actionHint}</Typography.Text> : null}
                          </Space>
                        </Space>
                        {!item.completed ? (
                          item.actionAvailable === false ? (
                            <Tag style={{ marginTop: 2 }}>待开启</Tag>
                          ) : (
                            <Button type="link" onClick={() => onActionItem(item)} style={{ flexShrink: 0, paddingInline: 0 }}>
                              {actionButtonText(item)}
                            </Button>
                          )
                        ) : null}
                      </div>
                    ))}
                  </Space>
                </div>
              ))}
            </Space>
          ) : (
            <Empty description="当前暂无可评分字段" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}

          {firstActionableItem ? (
            <Button type="primary" block onClick={() => onActionItem(firstActionableItem)}>
              一键去完善
            </Button>
          ) : null}
        </Space>
      ) : (
        <Empty description="当前暂无可评分字段" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
    </Card>
  );
};
