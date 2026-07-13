# Platform Update Actions QA

- Source visual truth: browser annotations Comment 1 and Comment 2 on `https://bm.aiadc.org.cn/settings/monitoring?tab=update` supplied in the current task.
- Implementation screenshot: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/qa-platform-update-actions.png`
- Viewport: 1407 x 991, desktop, light theme.
- State: current release synchronized, installable manifest images present, updater availability probe false, no running update task.

## Full-view comparison evidence

The annotated source and the browser-rendered implementation were compared at the same desktop viewport in the same task. The existing page hierarchy, status card, version cards, check chain, typography, spacing, borders, and light-theme tokens remain unchanged. The requested warning block is absent, so the version cards move upward naturally without leaving an empty gap. The action stack remains aligned to the right edge of the status card.

## Focused region comparison evidence

The status-card action region was checked at readable scale. In the source, `检查` is the only visually active action and `手动更新` is muted. In the implementation, `检查` is a standard secondary button and `更新` is the blue primary button. The DOM snapshot confirms exactly one enabled `cloud-download 更新` button and no `平台更新代理未连接` alert. No additional focused image region is needed because this patch contains no custom imagery or non-standard assets.

## Findings

- No actionable P0, P1, or P2 visual differences remain for the annotated scope.
- Fonts and typography: existing family, sizes, weights, line heights, and hierarchy are preserved; the button label is intentionally shortened from `手动更新` to `更新`.
- Spacing and layout rhythm: removing the warning closes the former vertical gap; card padding, action spacing, radii, and grid alignment remain consistent with the existing screen.
- Colors and visual tokens: the existing Ant Design blue primary token is now applied to `更新`; `检查` uses the standard neutral button treatment.
- Image quality and asset fidelity: not applicable; the screen uses the existing Ant Design icon library and contains no raster assets.
- Copy and content: the unwanted updater warning is removed, and the primary action is named `更新` as requested.

## Comparison history

1. Source findings: [P1] the updater-disconnected alert occupies a full content block despite not helping the requested action; [P1] the update action is visually suppressed while check is primary.
2. Fixes: removed the updater-disconnected alert, promoted `更新` to primary, demoted `检查` to secondary, and allowed update submission whenever an installable server image exists and no task is running.
3. Post-fix evidence: `artifacts/qa-platform-update-actions.png`; DOM checks found the enabled update button and no updater alert. No P0/P1/P2 differences remain.

## Primary interactions tested

- Clicked the enabled `更新` button.
- Confirmed the `确认手动安装平台更新？` modal opens with `开始更新` and `取消` actions.
- Cancelled the modal without submitting a backend task.
- Verified duplicate submissions remain blocked for `PENDING` and `RUNNING` tasks by unit tests.

## Console errors checked

No uncaught runtime errors were observed. Existing Ant Design deprecation warnings for `Space`, `Statistic`, `Steps`, and `Descriptions` remain outside this patch's scope.

## Follow-up polish

- [P3] Migrate the existing deprecated Ant Design props in a separate maintenance pass.

final result: passed
