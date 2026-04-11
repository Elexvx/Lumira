import { Spin, Typography, Progress, Tag } from 'antd';
import { useMemo } from 'react';
import { DEFAULT_BRANDING_SETTINGS } from '@/branding/settings';
import { useBootstrapSnapshot } from '@/bootstrap/bootstrapStore';
import './loading.less';

const phaseLabelMap: Record<string, string> = {
  idle: '准备中',
  health: '后端连接中',
  branding: '品牌加载中',
  security: '安全配置中',
  captcha: '验证码准备中',
  ready: '即将进入',
  error: '启动异常',
};

const Loading = () => {
  const snapshot = useBootstrapSnapshot();
  const brandName = snapshot.brandName || DEFAULT_BRANDING_SETTINGS.websiteName;
  const phaseLabel = phaseLabelMap[snapshot.phase] || '加载中';
  const percent = Math.max(0, Math.min(snapshot.progress, 100));

  const retryLabel = useMemo(() => {
    if (!snapshot.retryCount) {
      return '首次连接';
    }

    return `第 ${snapshot.retryCount} 次重试`;
  }, [snapshot.retryCount]);

  return (
    <Spin
      className="saas-startup-loading__spin"
      fullscreen
      spinning
      percent="auto"
      size="large"
    >
      <div className="saas-startup-loading">
        <div className="saas-startup-loading__ambient saas-startup-loading__ambient--one" />
        <div className="saas-startup-loading__ambient saas-startup-loading__ambient--two" />
        <div className="saas-startup-loading__shell">
          <div className="saas-startup-loading__topline">
            <span className="saas-startup-loading__badge">
              <span className="saas-startup-loading__badge-dot" />
              Auto
            </span>
            <div className="saas-startup-loading__rings" aria-hidden="true">
              <span className="saas-startup-loading__ring saas-startup-loading__ring--sm" />
              <span className="saas-startup-loading__ring saas-startup-loading__ring--md" />
              <span className="saas-startup-loading__ring saas-startup-loading__ring--lg" />
            </div>
          </div>

          <div className="saas-startup-loading__panel">
            <Typography.Text className="saas-startup-loading__eyebrow">{brandName}</Typography.Text>
            <Typography.Title level={2} className="saas-startup-loading__title">
              {snapshot.title}
            </Typography.Title>
            <Typography.Paragraph className="saas-startup-loading__description">
              {snapshot.description}
              {snapshot.errorMessage ? <span className="saas-startup-loading__error"> {snapshot.errorMessage}</span> : null}
            </Typography.Paragraph>

            <div className="saas-startup-loading__progress">
              <Progress percent={percent} showInfo={false} strokeLinecap="round" />
              <div className="saas-startup-loading__progress-meta">
                <Tag color="blue">{phaseLabel}</Tag>
                <span>{retryLabel}</span>
                {snapshot.retryInMs ? <span>约 {Math.max(1, Math.ceil(snapshot.retryInMs / 1000))} 秒后重试</span> : null}
              </div>
            </div>
          </div>
        </div>
      </div>
    </Spin>
  );
};

export default Loading;
