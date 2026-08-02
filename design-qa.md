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

## Redis sample-time typography

- Source visual truth: the browser annotation attached to this task at metadata viewport `1380 × 994`. A supporting production capture is saved at `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/design-qa/redis-sample-time-font-before.png`.
- Implementation screenshot: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/design-qa/redis-sample-time-font-after.png`.
- Viewport and density: the implementation uses browser override `1380 × 994`, device scale factor `1`, and captures to `1365 × 984` pixels after scrollbar/capture trimming. The supporting production capture uses the browser's default `1280 × 720` viewport at device scale factor `1.25` and captures to `1265 × 712`; it is used only to corroborate computed typography, while the attached annotation and implementation are compared at the same `1380 × 994` viewport.
- State: dark theme, authenticated administrator, `/settings/monitoring?tab=redis`, live Redis samples.

The annotated production state rendered the first fifteen values at `24px / 700` with a `37.7px` line height, but rendered **采样时间** at `18px / 700` with a `28.3px` line height. The revised state uses the shared `24px / 700` statistic style for all sixteen values. The sample-time column expands from one `xl` grid track (`159.3px`) to two tracks (`334.7px`), keeping the full timestamp on one line without overflow.

At a `1024 × 900` viewport, the sample-time item follows the existing half-width responsive layout, remains `24px`, and reports equal client and scroll widths (`334px`), confirming that the value is not clipped.

## Findings

No actionable P0, P1, or P2 differences remain in the requested regions.

- Typography: all sixteen Redis overview values now share the same `24px / 700` hierarchy and line height; long update-source values no longer wrap.
- Layout rhythm: card padding, gutters, and height are preserved; update metadata aligns in a stable two-column grid and both trend plots use their available card width consistently.
- Colors and tokens: existing dark-theme surfaces, borders, status colors, and primary chart color are unchanged.
- Assets: no new image assets or substitute icons were introduced.
- Responsive behavior: update-source metadata becomes one column on narrow screens; Redis charts stack before their plot width becomes cramped.

The final browser check found no runtime errors. The remaining console warnings are existing Ant Design deprecations for `Space` and `Statistic` APIs and are unrelated to these changes.

## Comparison history

1. Update source: fixed an unintended three-column desktop layout by explicitly defining every responsive breakpoint, removing full-row spans, and constraining long values with ellipsis.
2. Redis trends: fixed narrow centered plots by measuring each SVG with `ResizeObserver`, reducing horizontal padding, anchoring edge labels safely, and stacking cards below `xl`.
3. Redis sample time: fixed the P2 typography mismatch by removing the isolated `18px` override and widening the item from `xl=4` to `xl=8`; the post-fix capture shows the same font size, weight, and line height as adjacent metrics with no wrapping.
4. Post-fix verification: both trend charts report identical dimensions and plot coverage; desktop and `1024px` responsive layouts render without overlap or clipped labels.

## Final result

final result: passed
