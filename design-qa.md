# Design QA — Monitoring layout fixes

## Update source card

- Source visual truth: the browser annotation attached to this task at `1380 × 994`, focused on the **更新源** card.
- Implementation screenshot: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/design-qa/update-source-layout-1380x994.png`.
- State: dark theme, authenticated administrator, `/settings/monitoring?tab=update`, populated manifest update source.

The revised card preserves the existing monitoring page and renders the eight metadata fields as four rows of two equal columns. At `767px` the layout switches to one column. Long notes, addresses, and image references stay on one line with ellipsis, while tooltips and copy controls preserve the full value.

## Redis trend charts

- Source visual truth: the two browser annotations attached to this task at `1380 × 994`, selecting the memory and OPS trend charts.
- Before screenshot: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/design-qa/redis-trend-width-before.png`.
- Implementation screenshot: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/design-qa/redis-trend-width-after.png`.
- State: dark theme, authenticated administrator, `/settings/monitoring?tab=redis`, live Redis samples.

### Comparison evidence

The original SVG used a fixed `420px` internal coordinate system and `120px` of horizontal chart padding. On the annotated desktop viewport, only about `71.4%` of each SVG was available to the plot, leaving visibly excessive empty space on both sides.

The revised chart observes its rendered card width and updates the SVG view box accordingly. Horizontal safety padding is now `52px` on the label side and `16px` on the trailing side. Final fresh-tab measurements show both charts at the same width with approximately `84.4%` plot coverage; a prior full-width desktop capture measured about `86.0%`, depending on the scrollbar and content width. The first and last time labels use edge-aware anchors and remain inside the SVG.

At widths below the `xl` breakpoint, the two chart cards stack vertically so the plots are not compressed into narrow half-width columns.

## Findings

No actionable P0, P1, or P2 differences remain in the requested regions.

- Typography: existing Ant Design type styles and weights are preserved; long update-source values no longer wrap.
- Layout rhythm: card padding, gutters, and height are preserved; update metadata aligns in a stable two-column grid and both trend plots use their available card width consistently.
- Colors and tokens: existing dark-theme surfaces, borders, status colors, and primary chart color are unchanged.
- Assets: no new image assets or substitute icons were introduced.
- Responsive behavior: update-source metadata becomes one column on narrow screens; Redis charts stack before their plot width becomes cramped.

The final browser check found no runtime errors. The remaining console warnings are existing Ant Design deprecations for `Space` and `Statistic` APIs and are unrelated to these changes.

## Comparison history

1. Update source: fixed an unintended three-column desktop layout by explicitly defining every responsive breakpoint, removing full-row spans, and constraining long values with ellipsis.
2. Redis trends: fixed narrow centered plots by measuring each SVG with `ResizeObserver`, reducing horizontal padding, anchoring edge labels safely, and stacking cards below `xl`.
3. Post-fix verification: both trend charts report identical dimensions and plot coverage; desktop and `1024px` responsive layouts render without overlap or clipped labels.

## Final result

final result: passed
