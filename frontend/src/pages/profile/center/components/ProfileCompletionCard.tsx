import { CheckCircleFilled, ExclamationCircleFilled } from '@ant-design/icons';
import { Button, Card, Empty, Progress, Space, Tag, Typography } from 'antd';
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
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <div className="saas-profile-page__completion-summary">
              <Progress
                type="circle"
                percent={summary.completionRate}
                size={72}
                strokeColor={summary.completionRate === 100 ? '#52c41a' : '#1677ff'}
              />
              <Space direction="vertical" size={4} style={{ minWidth: 0 }}>
                <Typography.Title level={4} style={{ margin: 0 }}>
                  {summary.score}/{summary.maxScore} 分
                </Typography.Title>
                <Typography.Text type="secondary">
                  已完成 {summary.earnedWeight || 0} / {summary.totalWeight || 0} 权重
                </Typography.Text>
                <Typography.Text type="secondary">
                  {incompleteItems.length ? `${incompleteItems.length} 项资料待完善` : '资料已全部完善'}
                </Typography.Text>
              </Space>
            </div>

            {groups.length ? (
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                {groups.map((group) => (
                  <div key={group.groupKey} className="saas-profile-page__completion-group-row">
                    <Typography.Text strong ellipsis style={{ minWidth: 0 }}>
                      {group.groupLabel}
                    </Typography.Text>
                    <Space size={8} align="center">
                      <Progress percent={group.completionRate} showInfo={false} size="small" className="saas-profile-page__completion-group-progress" />
                      <Typography.Text type="secondary">{group.earnedWeight || 0}/{group.totalWeight || 0}</Typography.Text>
                    </Space>
                  </div>
                ))}
              </Space>
            ) : null}

            {visibleIncompleteItems.length ? (
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                {visibleIncompleteItems.map((item) => (
                  <div key={item.fieldKey} className="saas-profile-page__completion-action-row">
                    <Space size={8} style={{ minWidth: 0 }}>
                      <ExclamationCircleFilled style={{ color: '#fa8c16' }} />
                      <Typography.Text strong ellipsis style={{ minWidth: 0 }}>
                        {item.fieldLabel}
                      </Typography.Text>
                    </Space>
                    {item.actionAvailable === false ? (
                      <Tag style={{ marginInlineEnd: 0 }}>待开启</Tag>
                    ) : (
                      <Button type="link" size="small" onClick={() => onActionItem(item)}>
                        {actionButtonText(item)}
                      </Button>
                    )}
                  </div>
                ))}
                {incompleteItems.length > visibleIncompleteItems.length ? (
                  <Typography.Text type="secondary">还有 {incompleteItems.length - visibleIncompleteItems.length} 项可在编辑资料中完善</Typography.Text>
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
              strokeColor={{ '0%': '#1677ff', '100%': '#69c0ff' }}
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
                            <CheckCircleFilled style={{ color: '#52c41a', fontSize: 18, marginTop: 3 }} />
                          ) : (
                            <ExclamationCircleFilled style={{ color: '#fa8c16', fontSize: 18, marginTop: 3 }} />
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
