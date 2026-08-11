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

# Design QA — Login split-screen redesign

## Source and implementation evidence

- Desktop source: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/reference-desktop-2048x1030.png` (`2048 × 1030` pixels), supplied with the task and showing the post-consent registration form.
- Desktop implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-desktop-2048x1030.png` (`2033 × 1023` captured pixels after browser scrollbar trimming).
- Desktop CSS viewport: `2048 × 1030` for both source-target inspection and implementation verification; device scale factor `1`. The implementation capture is compared by CSS viewport rather than raw edge pixels because the in-app browser removes the scrollbar gutter from the saved bitmap.
- Mobile source: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/reference-mobile-390x844.png` (`390 × 844` pixels).
- Mobile implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-mobile-390x844.png` (`390 × 843` captured pixels after the one-pixel bottom trim).
- Mobile CSS viewport: `390 × 844`, device scale factor `1`, default password-login state.
- State: unauthenticated `/user/login`, Chinese locale, password mode, empty form, no validation errors, no modal open.

The desktop source and implementation were opened together in one comparison input at the same CSS viewport. The mobile source and implementation were also opened together in one comparison input at the same CSS viewport. Focused inspection covered the split boundary, form-column width, top-right language control, input/button radii, mobile hero height, and the transition from the hero image into the dark form surface.

## Findings

No actionable P0, P1, or P2 visual differences remain for the requested layout.

- Layout: the final desktop split is approximately `66.6% / 33.4%`, closely matching the supplied `68.1% / 31.9%` reference while preserving usable width for Lumira's login modes and captcha states.
- Form column: the implementation uses the reference's narrow centered column, dark surface, compact outlined inputs, rounded primary action, and top-right language switch.
- Mobile: the hero becomes a `42svh` top banner and the authentication content continues as a full-width single column without horizontal overflow (`scrollWidth = innerWidth = 390`).
- Brand and assets: the reference site's photograph and identity were not copied. The existing Lumira-owned cloud-platform asset, configured tenant website name, footer, and primary blue token are preserved.
- Functionality: password login, validation feedback, remember-me, password visibility, forgotten-password modal, agreement links, alternate login methods, captcha rendering, locale switching, tenant background override, and authentication callback behavior remain wired to the existing flow.
- Browser console: the final clean preview has no new runtime errors. Existing Ant Design modal deprecation warnings are unrelated to this layout change.

## Iteration history

1. Replaced the centered white card and dedicated QR column with the reference's full-height hero plus dark authentication panel.
2. Narrowed the desktop authentication panel after the first comparison showed it was too wide, then set the form column to `56%` of the panel so its margins match the source at wide viewports.
3. Added the mobile hero-to-form stack and verified the `390 × 844` layout, vertical scrolling, and zero horizontal overflow.
4. Restarted the local preview after its hot-reload cache became stale, then repeated the clean desktop and mobile captures from a fresh browser tab.
5. Verified form validation, forgotten-password modal, locale switching in both directions, targeted login tests, TypeScript checks, lint, stylelint, and the production build.

## Final result

final result: passed

# Design QA — Browser annotation follow-up

## Source and implementation evidence

- Source visual truth: Browser Comment 1 and Browser Comment 2 attached to this task at a `1380 × 994` viewport. Comment 1 selects the complete hero text overlay and requests that it be removed; Comment 2 selects the authentication stage and identifies it as too narrow.
- Saved before-state support: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-desktop-css-1280x720.png` (`1265 × 712` captured pixels at a `1280 × 720` CSS viewport).
- Desktop implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-annotation-update-1380x994.png` (`1365 × 984` captured pixels at a `1380 × 994` CSS viewport, device scale factor `1`, scrollbar gutter trimmed).
- Same-size implementation comparison: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-annotation-update-1280x720.png` (`1265 × 712` captured pixels at a `1280 × 720` CSS viewport).
- Mobile implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-annotation-update-mobile-390x844.png` (`390 × 843` captured pixels at a `390 × 844` CSS viewport).
- State: unauthenticated password-login mode, empty form, no validation errors or dialogs.

The saved before state and updated `1280 × 720` implementation were opened together in one comparison input. The comparison shows the full hero overlay removed and the form growing from roughly `227px` to `347px`. At the annotated `1380 × 994` viewport, the final authentication stage measures `379.125px` inside a `443.125px` panel, leaving `32px` margins on both sides. Focused browser measurements confirm the hero has no child elements and no text content.

## Findings

No actionable P0, P1, or P2 differences remain for the two browser annotations.

- Typography and copy: all visible hero overlay copy was removed from the DOM; form typography remains unchanged.
- Spacing and layout rhythm: the desktop form now uses `calc(100% - 64px)` up to `440px`, correcting the narrow stage while preserving the panel split and vertical alignment.
- Colors and tokens: dark authentication surface, primary blue action, borders, and muted text tokens are unchanged.
- Image quality: the existing Lumira hero image is now unobstructed and remains sharp with the original responsive crop.
- Responsive behavior: the mobile form remains `350px` wide at a `390px` viewport, with `scrollWidth = innerWidth = 390`; the mobile hero also contains no overlay text.

## Comparison history

1. Browser annotation identified two P2 issues: unwanted hero copy and an overly narrow authentication stage.
2. Removed the hero content node and its unused desktop/mobile typography rules.
3. Replaced the percentage-only form width with a fixed `32px` side gutter and `440px` cap.
4. Post-fix evidence at `1380 × 994` reports `heroChildCount = 0`, empty hero text, and a `379.125px` form stage; the `1280 × 720` before/after comparison confirms both requested changes visually.

## Final result

final result: passed

# Design QA — Join-us button follow-up

## Source and implementation evidence

- Source visual truth: the previously approved login layout at `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-annotation-update-1380x994.png`, plus the scoped request to add a “没有账号？加入我们” button immediately below the login action.
- Desktop implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-join-button-1380x994.png` (`1365 × 984` captured pixels at a `1380 × 994` CSS viewport, device scale factor `1`, scrollbar gutter trimmed).
- Mobile implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-join-button-mobile-focus-390x844.png` (`390 × 843` captured pixels at a `390 × 844` CSS viewport, device scale factor `1`).
- State: unauthenticated password-login mode, empty form, Chinese locale, no validation errors or dialogs.

The approved before-state and final desktop implementation were opened together in one comparison input. The only intentional composition change is the new secondary action between the primary login button and alternate-login divider. The mobile focused capture verifies the same hierarchy at the narrow breakpoint. Browser measurements confirm a `379.125px × 44px` desktop button, a `350.4px × 44px` mobile button, a `12px` gap below the primary action, and no horizontal overflow at `390px`.

## Findings

No actionable P0, P1, or P2 differences remain for the requested addition.

- Fonts and typography: the prompt uses the existing muted small-copy weight; “加入我们” uses the existing primary-link blue and a slightly stronger weight for clear action hierarchy.
- Spacing and layout rhythm: the button matches the primary action width and height, keeps a compact `12px` vertical gap, and preserves the alternate-login divider and footer rhythm.
- Colors and visual tokens: the transparent dark secondary surface, subtle border, hover/focus treatment, and blue action text reuse the login page’s established tokens.
- Image quality and asset fidelity: the Lumira hero image and its responsive crop are unchanged; no new image or icon assets were introduced.
- Copy and content: Chinese renders as “没有账号？加入我们”; English renders as “No account? Join us”.
- Responsiveness: desktop and mobile captures remain free of horizontal overflow; the narrow layout keeps the new action full width and reachable within the existing scroll container.
- Interaction: when SMS registration is enabled, the button switches to the SMS verification flow; when no registration-capable mode is enabled, it returns the localized “注册通道暂未开放，请联系管理员” feedback rather than navigating to a nonexistent route.
- Console: the new interaction introduces no console error. Two existing Ant Design modal deprecation warnings remain unrelated to this change.

## Comparison history

1. Added the secondary action below the primary submit button without changing the approved hero, panel width, form fields, alternate-login methods, or routes.
2. Verified the action in English and Chinese, tested the unavailable-registration fallback, and replaced the initial static feedback call with the page’s Ant Design app context so the new interaction produces no context warning.
3. Re-ran TypeScript, lint, and the targeted login suite (`5` files, `13` tests), then repeated desktop and mobile browser checks.

## Final result

final result: passed

# Design QA — Agreement alignment follow-up

## Source and implementation evidence

- Source visual truth: Browser Comment 1 at the `1380 × 994` viewport, selecting “我已阅读并同意 用户协议 and 隐私协议” and requesting centered alignment. The saved before-state is `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-join-button-1380x994.png`.
- Desktop implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-agreement-centered-1380x994.png` (`1365 × 984` captured pixels at a `1380 × 994` CSS viewport, device scale factor `1`, scrollbar gutter trimmed).
- Mobile implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-agreement-centered-mobile-390x844.png` (`390 × 843` captured pixels at a `390 × 844` CSS viewport, device scale factor `1`).
- State: unauthenticated password-login mode, empty form, Chinese locale, no validation errors or dialogs.

The before-state and final desktop implementation were opened together in one comparison input at the same viewport. Focused browser measurements show the `207.175px` agreement text centered inside its `379.125px` desktop container with a `0px` center delta. The mobile pass shows the same `0px` center delta inside a `350.4px` container and no horizontal overflow.

## Findings

No actionable P0, P1, or P2 differences remain for the annotation.

- Fonts and typography: font family, size, weight, line height, and link emphasis are unchanged; only text alignment changed.
- Spacing and layout rhythm: the agreement now centers across the full form width without changing its margin, order, or surrounding vertical spacing.
- Colors and visual tokens: muted agreement text and link colors are unchanged.
- Image quality and asset fidelity: hero imagery and all existing assets are unchanged.
- Copy and content: agreement wording remains unchanged in both locales.
- Responsiveness: desktop and mobile agreement rows are centered with no clipping or horizontal overflow.
- Interaction: “用户协议” and “隐私协议” remain enabled and retain their existing preview handlers.
- Console: no new runtime error was introduced; existing Ant Design modal deprecation warnings remain unrelated.

## Comparison history

1. The annotation identified one P2 alignment issue: the agreement text was left-aligned while the surrounding secondary content was centered.
2. Added centered text alignment to the existing agreement container without changing DOM structure or link behavior.
3. Post-fix desktop and mobile measurements both report a `0px` center delta; stylelint and browser checks passed.

## Final result

final result: passed

# Design QA — Login localization follow-up

## Source and implementation evidence

- Source visual truth: Browser Comment 1 at `1380 × 994`, where the Chinese login stage visibly mixed Chinese and English copy. The saved before-state is `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-agreement-centered-1380x994.png`.
- Chinese desktop implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-i18n-zh-1380x994.png` (`1365 × 984` captured pixels at a `1380 × 994` CSS viewport, device scale factor `1`, scrollbar gutter trimmed).
- English desktop implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-i18n-en-1380x994.png` (`1365 × 984` captured pixels at the same viewport and density).
- Chinese mobile implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-i18n-zh-mobile-390x844.png` (`390 × 843` captured pixels at a `390 × 844` CSS viewport, device scale factor `1`).
- English mobile implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-i18n-en-mobile-390x844.png` (`390 × 843` captured pixels at the same viewport and density).
- State: unauthenticated password-login mode, empty form, no validation errors or dialogs.

The mixed-language before-state and corrected Chinese desktop state were opened together in one comparison input. The final Chinese and English desktop captures use the same viewport and layout state. Browser copy assertions passed in both directions: Chinese includes the localized title, mode, placeholders, remember/forgot actions, agreement connector, submit action, join action, and alternate-login label; English includes the corresponding English copy. The proper tenant name “宏翔商道” remains unchanged in both locales.

## Findings

No actionable P0, P1, or P2 differences remain for the localization annotation.

- Fonts and typography: existing font sizes, weights, hierarchy, and title treatment are preserved in both locales.
- Spacing and layout rhythm: language-specific copy fits the approved panel width; the agreement line now provides readable link spacing and can wrap while remaining centered.
- Colors and visual tokens: light/dark tokens, primary blue, muted copy, borders, and focus styles are unchanged.
- Image quality and asset fidelity: the existing Lumira hero image and responsive crop remain unchanged; no assets were replaced.
- Copy and content: all `87` login-module message keys have aligned Chinese and English built-in values. Stale runtime values are accepted only when their writing system matches the active locale, so same-language server overrides remain supported while cross-language cached values fall back safely.
- Responsiveness: both `390 × 844` locale passes report `documentWidth = innerWidth = 390` with no horizontal overflow; long English placeholder text truncates inside the input rather than widening the page.
- Interaction: the locale switch was exercised Chinese → English → Chinese with reloads, and both final copy assertions returned `allExpectedPresent = true`.
- Console: no localization runtime error was introduced. Existing Ant Design `destroyOnClose` and `maskClosable` deprecation warnings remain unrelated.

## Comparison history

1. Initial browser verification reproduced the P2 mixed-language state because an old persisted runtime catalog overrode component fallbacks.
2. Added structurally aligned Chinese and English login catalogs and connected them to both Umi locale exports and non-hook message formatting.
3. Added a locale-writing-system guard so correct same-language runtime translations keep priority while stale cross-language values fall back to the bundled catalog.
4. Improved agreement-link spacing/wrapping for English, then repeated desktop/mobile visual checks, bidirectional locale switching, TypeScript, lint, and the targeted login suite (`6` files, `17` tests).

## Final result

final result: passed

# Design QA — Unified WeChat login follow-up

## Source and implementation evidence

- Source visual truth: Browser Comment 1 at `1380 × 994`, selecting the primary login form region and requesting that WeChat login be managed inside it. The saved approved before-state is `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-i18n-zh-1380x994.png` (`1365 × 984` captured pixels).
- Unified password state: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-wechat-unified-password-1380x994.png` (`1365 × 984` captured pixels at a `1380 × 994` CSS viewport, device scale factor `1`, scrollbar gutter trimmed).
- Unified WeChat state: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-wechat-unified-panel-1380x994.png` (`1365 × 984` captured pixels at the same viewport and density).
- Mobile WeChat state: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-wechat-unified-mobile-390x844.png` (`390 × 843` captured pixels at a `390 × 844` CSS viewport, device scale factor `1`).
- State: unauthenticated login, Chinese locale for saved evidence, dark theme, WeChat capability unavailable in the local backend.

The approved before-state, updated password state, and selected WeChat state were opened together in one comparison input. The intentional change is confined to the selected form region: “微信扫码登录” now appears beside “密码登录”, while the duplicate WeChat circle action is removed from “其他方式登录”. The selected WeChat state reuses the existing official QR container and shows a clear icon plus “微信登录暂未启用” when the capability is disabled.

## Findings

No actionable P0, P1, or P2 differences remain for the annotation.

- Fonts and typography: the WeChat tab uses the existing login-mode size, weight, underline, and active-state hierarchy; unavailable copy has corrected dark-theme contrast.
- Spacing and layout rhythm: the new tab fits the existing header without changing form width; the QR panel remains centered and the remaining alternate-login icons rebalance around the center.
- Colors and visual tokens: existing blue tab state, white QR surface, WeChat green icon, and dark-panel tokens are reused.
- Image quality and asset fidelity: the hero image remains unchanged; the official WeChat container and Ant Design WeChat icon are reused rather than approximated.
- Copy and content: Chinese renders “微信扫码登录 / 微信登录暂未启用”; English renders “WeChat QR login / WeChat login is not enabled”.
- Responsiveness: the mobile panel measures `358.4px` inside a `390px` viewport with `documentWidth = innerWidth = 390`; no horizontal overflow or viewport-height growth occurs.
- Interaction: password and WeChat tabs switch inside the same panel; the primary submit and join actions are hidden while WeChat is selected; the duplicate `.saas-login-page__social-button--wechat` is absent.
- Capability behavior: enabled deployments continue to load the existing official WeChat QR authorization; unavailable deployments retain a visible managed tab with an explicit disabled state.
- Console: no new runtime error was introduced. Existing Ant Design `destroyOnClose` and `maskClosable` deprecation warnings remain unrelated.

## Comparison history

1. The annotation identified one P2 information-architecture issue: WeChat login lived outside the primary login-mode manager and duplicated an already implemented WeChat panel.
2. Kept WeChat in the presented mode list across desktop and mobile, including a visible unavailable state when runtime capabilities cannot be loaded, and removed the duplicate social action.
3. The first selected-state capture exposed a P2 dark-theme contrast issue in the WeChat title and hint; added dark-theme text tokens and a visible WeChat icon in the unavailable QR surface.
4. Post-fix desktop and mobile captures show readable copy, centered content, no horizontal overflow, bidirectional locale coverage, and no duplicated WeChat entry.

## Final result

final result: passed

# Design QA — Join link and subtitle removal follow-up

## Source and implementation evidence

- Source visual truth: Browser Comments 1 and 2 at `490 × 938`, requesting that “没有账号？加入我们” become a left-aligned text link and that the duplicate brand subtitle be removed. The saved matching before-state is `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/theme-consistency-audit/01-login-dark.png` (`490 × 938` captured pixels).
- Mobile implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-join-link-no-subtitle-490x938.png` (`490 × 938` captured pixels at a `490 × 938` CSS viewport, device scale factor `1`).
- Desktop implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/login-layout-redesign/implementation-join-link-no-subtitle-1380x994.png` (`1365 × 984` captured pixels at a `1380 × 994` CSS viewport, device scale factor `1`, scrollbar gutter trimmed).
- State: unauthenticated password login, Chinese locale, dark theme, empty form, no validation errors or dialogs.

The same-viewport before and after mobile captures were opened together in one comparison input. The intended differences are visible and isolated: the subtitle below “欢迎登录 宏翔商道” is absent, and the former full-width outlined join button is now a borderless text link aligned to the form’s left edge.

## Findings

No actionable P0, P1, or P2 differences remain for the two annotations.

- Fonts and typography: the join prompt/action keep the existing `13px` muted/blue hierarchy and now read like the adjacent “忘记密码” text action; the duplicate subtitle no longer competes with the login-mode tabs.
- Spacing and layout rhythm: the join link reports a `0px` left delta from the form edge at both viewports; removal of the subtitle tightens the brand-to-mode transition without changing form width or field spacing.
- Colors and visual tokens: transparent background, zero border, muted prompt, blue action, and hover/focus colors reuse the existing login link tokens in light and dark themes.
- Image quality and asset fidelity: the Lumira hero image and crop remain unchanged; no assets were added or replaced.
- Copy and content: the brand header now contains only “欢迎登录 宏翔商道”; Chinese renders “没有账号？加入我们” and English renders “No account? Join us”.
- Responsiveness: `490 × 938` and `1380 × 994` passes have no horizontal overflow; the desktop link is `131px` wide inside a `379.125px` form rather than stretching across it.
- Interaction: the text link retains its existing registration-mode switch and unavailable-registration feedback; the English unavailable path was exercised successfully.
- Console: no new runtime error was introduced. Existing Ant Design `destroyOnClose` and `maskClosable` deprecation warnings remain unrelated.

## Comparison history

1. The annotations identified two P2 hierarchy issues: a redundant mode subtitle and an overly prominent full-width join button.
2. Removed the subtitle node and unused responsive subtitle styles.
3. Converted the join action to Ant Design’s link presentation, removed its width/height/border/background treatment, and aligned it to the form start while preserving its handler and test id.
4. Post-fix browser measurements confirm no subtitle node, transparent background, `0px` border, `0px` left delta, bilingual copy, preserved feedback, and no overflow at mobile or desktop widths.

## Final result

final result: passed

---

# Design QA — Login control sizing (2026-08-05)

## Visual truth and implementation evidence

- Source visual: browser annotation on `http://127.0.0.1:8001/user/login`, captured at a requested 1380 × 994 CSS viewport (device pixel ratio 1.25). Saved evidence: `.codex-tmp/login-controls-source.png` (1365 × 982 px).
- Implementation visual: local preview at `http://127.0.0.1:8898/user/login`, same requested 1380 × 994 CSS viewport and state. Saved evidence: `.codex-tmp/login-controls-implementation.png` (1365 × 984 px).
- Focused responsive evidence: `.codex-tmp/login-controls-mobile.png`, 490 × 938 CSS viewport, dark theme, password-login tab.
- Interaction state checked: password login and WeChat QR login tabs both switch successfully; password-login state was used for the final comparison.

## Acceptance checks

| Check | Result |
| --- | --- |
| Input control height | 52 px |
| Primary login button height | 52 px |
| Input and primary button text | 16 px |
| Login method tab height / text | 40 px / 18 px |
| Secondary join link text | 15 px |
| Agreement text | 14 px |
| Desktop horizontal overflow | None |
| Mobile horizontal overflow at 490 px | None |
| Primary tab interactions | Passed |

## Comparison findings and fix history

1. Initial source inspection showed 44 px inputs/buttons and 14 px primary control text, which appeared undersized inside the 400 px-wide authentication panel.
2. Default control sizing was promoted to the existing large-control values: 52 px fields and CTA, 16 px form text, 40 px tabs, and proportionally increased gaps/radii.
3. Full-page desktop comparison confirmed the auth panel remains aligned and balanced without changing the page structure or background treatment.
4. Focused mobile verification at 490 × 938 confirmed the controls stay within the viewport and preserve readable spacing.
5. Local preview may show fallback English strings when its localization API is unavailable; this is an existing environment behavior and is unrelated to the CSS sizing change.

## Validation

- Stylelint: passed.
- TypeScript typecheck: passed.
- Vitest: 79 files / 402 tests passed.
- Production build: passed.

## Final result

passed

---

# Design QA — Maintenance brand and administrator entry (2026-08-08)

## Visual truth and implementation evidence

- Source visual truth: Browser Comments 1 and 2 attached to this task, showing the public dark-theme maintenance page at a `1334 × 994` viewport. Comment 1 requests a larger website name; Comment 2 identifies the administrator-login control shape as incorrect.
- Desktop implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/design-qa/maintenance-admin-float-match-1334x994.jpg` (`1319 × 983` captured pixels from a `1334 × 994` CSS viewport, device scale factor `1`; the browser omits its scrollbar gutter and bottom edge from the bitmap).
- Mobile implementation: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/design-qa/maintenance-admin-float-match-390x844.jpg` (`390 × 843` captured pixels from a `390 × 844` CSS viewport, device scale factor `1`).
- Reference implementation: the authenticated production `saas-global-float-actions` trigger was measured at the same desktop and mobile viewports before comparing the local maintenance entry.
- State: unauthenticated public maintenance page, dark theme, configured website name/title/message, no countdown.

The two supplied annotation images and the final desktop implementation were reviewed together at the same page state and CSS viewport. The mobile capture was used as supporting responsive evidence.

## Findings

No actionable P0, P1, or P2 differences remain for the two annotations.

- Fonts and typography: the website name now has its own responsive title style and renders at `26.388px / 600` on the annotated desktop viewport, instead of inheriting the smaller Ant Design large-body size. It remains readable at `22px / 600` on mobile without competing with the maintenance title.
- Spacing and layout rhythm: the centered, frameless composition and existing Result spacing are unchanged. The administrator entry now uses the same Ant Design `FloatButton` component and offsets as the global floating action: `32px / 40px` on desktop and `16px / 24px` on mobile.
- Colors and visual tokens: the website name uses the active Ant Design primary text token. The administrator entry now inherits the same primary floating-button surface (`rgb(22, 104, 220)` in the verified dark theme), circular body, icon alignment, and hover behavior as the global action; only the intentional low-visibility `0.28` idle opacity differs.
- Image quality and asset fidelity: no raster or logo asset was added or replaced; a configured website logo still follows the existing image path and sizing rules.
- Copy and content: website name, maintenance title, maintenance message, countdown behavior, and accessible `管理员登录` label are unchanged.
- Responsiveness: at `390 × 844`, document width and scroll width are both `390px`; the local entry and production floating trigger both resolve to the same accessible `44 × 44px`, `50%` radius, `16px` right offset, and `24px` bottom offset, with no horizontal or vertical overflow.

## Focused comparison and interaction evidence

- The desktop screenshot visibly shows the requested stronger website-name hierarchy while preserving the original centered layout.
- Computed browser measurements confirm the local administrator entry and production floating trigger are both `40 × 40px` on desktop and `44 × 44px` on mobile, with a `50%` radius, identical primary background, and identical offsets.
- Activating the administrator entry routes to `/user/login?redirect=%2Fsettings%2Fpersonalization%3Ftab%3Dmaintenance`, preserving the recovery flow.
- No change-related browser error was introduced. The login-page interaction still emits two existing Ant Design Modal deprecation warnings, unrelated to this maintenance-page styling change.

## Comparison history

1. The supplied annotations identified two P2 presentation issues: undersized website identity and an unreliable administrator-button silhouette.
2. Added a dedicated responsive website-name class without changing the logo path or maintenance content.
3. The first pass constrained a regular Ant Design `Button`, but visual review showed that this still did not share the floating-window control's native silhouette.
4. Replaced the recovery control with Ant Design `FloatButton`, matching the existing global floating action's primary type, circle shape, responsive offsets, touch target, and icon geometry while retaining reduced idle opacity.
5. Post-fix desktop and mobile captures plus direct computed-style comparison confirmed exact geometry, preserved navigation, and zero responsive overflow.

## Validation

- Focused Vitest: `2` files / `8` tests passed.
- Stylelint: passed.
- TypeScript typecheck: passed.
- Umi production build: passed (`123` assets).

## Final result

final result: passed

# Maintenance settings annotation QA — 2026-08-08

## Visual truth and implementation evidence

- Source visual truth: Browser Comment 1 on production `/settings/personalization?tab=maintenance`, authenticated dark-theme state at `1280 × 959`.
- Requested region: the maintenance preview block and its red `保存维护模式设置` action.
- Implementation evidence: the actual `MaintenanceTab` source plus a successful Umi production build.
- Browser-rendered implementation screenshot: unavailable in this pass.

## Findings

- The maintenance preview block has been removed from the form.
- The action now uses Ant Design `Button type="primary"`, preserving the product's standard blue primary treatment.
- The action label now resolves through `common.save`, which is `保存` in the Chinese locale.
- No preview-only countdown hook or formatter remains in `MaintenanceTab`.

## Validation

- Focused component test: passed (`1` file / `1` test) for the blue `保存` action and absence of preview-only markup.
- TypeScript typecheck: passed.
- Umi production build: passed (`123` assets).
- Database-i18n strict check: blocked by pre-existing localization regressions outside the two modified files; neither modified file appears in the reported failure list.
- Focused visual comparison: blocked because the isolated Vite QA harness cannot optimize the repository's pinned `esbuild` dependency, and an authenticated production screenshot would still render the currently deployed bundle rather than this local change.

## Final result

blocked — implementation and build validation passed; browser screenshot comparison awaits deployment or a working authenticated local runtime.

---

# Design QA - Payment settings action style (2026-08-06)

## Visual truth and implementation evidence

- Source visual truth: `C:/Users/ADMINI~1/AppData/Local/Temp/codex-clipboard-ef2bb6fc-1491-467b-8f4b-b6747cfd4dcb.png`.
- Source pixels: `2434 x 1028`, dark theme, authenticated payment settings page.
- Intended implementation URL: `http://127.0.0.1:8899/settings/payment`.
- Implementation screenshot: unavailable because the in-app browser has no authenticated local session and redirects to `/user/login`.
- CSS viewport and density normalization: not applicable until the authenticated implementation state can be captured.

## Findings

- Source P2: the `配置` and `测试` actions render as bordered default buttons, unlike the project's established inline table-action treatment.
- Implemented fix: both actions now use the shared link-style table action class and no-wrap spacing while preserving permission checks and the test confirmation.
- Fonts and typography, spacing and layout rhythm, colors and visual tokens, image quality, and copy could not be compared against a rendered authenticated implementation.

## Comparison history

1. The source screenshot identified the inconsistent bordered actions.
2. The code was updated to the existing `saas-table-action-bar` link treatment.
3. ESLint, TypeScript typecheck, production build, and `git diff --check` passed.
4. Browser capture remains blocked by the missing authenticated local session; no password or session data was inspected or reused.

## Final result

blocked

---

# Design QA - Platform update confirmation spacing (2026-08-06)

## Visual truth and implementation evidence

- Source visual truth: Browser Comment 1 on `/settings/monitoring?tab=update`, dark theme, update-confirmation state, supplied at `1280 x 800`.
- Focused implementation screenshot: `artifacts/design-qa/platform-update-modal-spacing-local.png`.
- Implementation viewport: `1280 x 720` at device pixel ratio `1.25`.
- Scope: the confirmation dialog only; fixture release values and the page content behind the mask are not comparison targets.

## Findings

No actionable P0, P1, or P2 differences remain in the requested spacing region.

- The warning icon remains visually paired with the title, while the confirmation paragraph now uses the full body width.
- The alert and bordered descriptions table both measure `24px` from the modal container's left edge and `24px` from its right edge.
- Typography, copy, colors, table structure, warning hierarchy, and footer actions remain unchanged.
- No image asset is involved in this change.

## Validation

- ESLint: passed.
- Stylelint: passed.
- TypeScript typecheck: passed.
- Vitest: 86 files / 429 tests passed.
- Production build: passed.
- Browser visual and DOM measurement: passed.

## Final result

passed

---

# Design QA — Platform update dialogs in dark theme (2026-08-05)

## Visual truth and implementation evidence

- Source visual truth: `artifacts/design-qa/platform-update-modal-before.png`.
- Final implementation: `artifacts/design-qa/platform-update-modal-after.png`.
- Viewport and pixels: both captures are `1365 × 982`; the implementation was captured with a `1365 × 982` CSS viewport and matching output pixels.
- State: authenticated production system monitoring page, Chinese locale, dark theme. The source shows the update confirmation; after the successful deployment the system was already up to date, so the shared rollback confirmation was opened and cancelled to verify the same theme-aware modal runtime without changing production state.

## Findings

No actionable P0, P1, or P2 differences remain in the requested color treatment.

- Earlier P1: the update confirmation used Ant Design's static modal API, so its white surface, dark text, pale table, and information alert ignored the active dark theme.
- Fix: platform update, rollback, updater-installation, and updater-warning dialogs now use the application feedback bridge registered inside the active Ant Design theme context.
- Post-fix evidence: the shared confirmation surface renders with `rgb(27, 27, 27)` background and `rgba(255, 255, 255, 0.85)` title/body text, matching the surrounding dark interface. The semantic warning and destructive-action colors remain distinct.

## Required fidelity surfaces

- Fonts and typography: existing Ant Design family, weights, sizes, wrapping, and hierarchy are unchanged; light foreground text remains legible on the dark surface.
- Spacing and layout rhythm: modal width, content spacing, corner radius, button alignment, overlay, and page composition remain unchanged.
- Colors and visual tokens: the modal now consumes the active dark-theme tokens instead of the static light defaults; warning orange and destructive red retain their semantic roles.
- Image quality and asset fidelity: no raster, logo, illustration, or icon asset was replaced; existing Ant Design icons remain unchanged.
- Copy and content: platform update and rollback text is unchanged.

## Interaction and runtime checks

- Production blue-green update completed with `SUCCEEDED`, `FINALIZING`, and `100%` for commit `5862ea650651`.
- The post-deployment rollback confirmation opened successfully and was cancelled; no rollback request was submitted.
- Browser console reported no warnings or errors after loading the deployed frontend.
- TypeScript typecheck, static-theme smoke check, production frontend build, CI, frontend build workflow, security scan, and production deployment checks passed.

## Comparison history

- Pass 1: identified the light-only modal as a major theme mismatch.
- Implementation: routed all platform-update feedback dialogs through the live theme context.
- Pass 2: same-size production capture confirmed a dark modal surface with matching typography and semantic colors; no P0/P1/P2 visual issue remained.

## Final result

passed
---

# Design QA — Login language consistency and default sizing (2026-08-05)

## Visual truth and implementation evidence

- Source visual truth: browser annotation on `http://127.0.0.1:8898/user/login`; captured pre-fix state at `.codex-tmp/login-language-size-source.png` (1265 × 712 px).
- Implementation: local preview at `http://127.0.0.1:8898/user/login`; captured post-fix state at `.codex-tmp/login-language-size-final.png` (1265 × 712 px).
- CSS viewport: native 1280 × 720 browser viewport, device pixel ratio 1.25; both artifacts use identical capture dimensions.
- State: dark theme, password-login tab, Chinese locale. English locale and WeChat-login state were also exercised.

## Findings and comparison history

1. Earlier sizing work had promoted the normal controls from the repository defaults to 52 px / 16 px, creating a P2 density mismatch. The default values are now restored to 44 px height and 14 px text for account input, password input, and login CTA; related gaps, radii, tabs, secondary copy, and social controls were restored with them.
2. The pre-fix Chinese view mixed Chinese navigation labels with English placeholders, checkbox copy, CTA copy, and agreement conjunctions because the runtime localization bundle was partial. A built-in login fallback catalog now fills missing or wrong-language values while preserving valid database translations.
3. Browser measurements confirm 44 px inputs and CTA at desktop and mobile widths. The 490 × 938 responsive check reports a 490 px document width with no horizontal overflow.
4. Chinese password-login content is fully Chinese apart from product/legal proper names (`Lumira`, `Copyright`). Chinese WeChat content is also fully Chinese. English switching produces fully English password-login content and labels.
5. Full-view comparison finds no regression in layout, typography hierarchy, color tokens, background image quality, icon treatment, or copy placement.

## Validation

- Stylelint: passed.
- TypeScript typecheck: passed.
- Vitest: 80 files / 406 tests passed, including four new bilingual fallback tests.
- Production build: passed.

## Final result

passed

---

# Design QA — Join link motion removal (2026-08-05)

## Visual truth and implementation evidence

- Source visual truth: browser annotation on `http://127.0.0.1:8001/user/login`, saved as `.codex-tmp/login-join-source.png` (1365 × 984 px).
- Implementation: `http://127.0.0.1:8898/user/login`, saved as `.codex-tmp/login-join-no-motion.png` (1379 × 994 px).
- Requested CSS viewport: 1380 × 994, device pixel ratio 1.25. The production capture excludes its native scrollbar gutter; comparison used the same CSS viewport and the matching authentication-panel region rather than treating scrollbar pixels as design drift.
- State: dark theme, password-login tab, join link idle and clicked.

## Findings and comparison history

1. Source behavior used an Ant Design link button, which inherited the library click-wave animation.
2. The implementation replaces only this entry with a semantic native `button`; its copy, alignment, typography, colors, and click handler are unchanged.
3. Focused DOM/style evidence after the fix: element tag `button`, class `saas-login-page__join-button`, `animation-name: none`, `animation-duration: 0s`, `transition-property: none`, `transition-duration: 0s`, and `transform: none`.
4. Full-view comparison found no layout, spacing, typography, color, image, or copy regression attributable to the motion removal. Local preview fallback English strings and temporary service-unavailable notices are existing development-environment behavior.
5. Browser click verification completed without a visual wave, transition, or displacement.

## Validation

- Stylelint: passed.
- TypeScript typecheck: passed.
- Vitest: 79 files / 402 tests passed.
- Production build: passed.

## Final result

passed

---

# Design QA — Login viewport fit and police filing emblem (2026-08-05)

## Visual truth and implementation evidence

- Source visual truth: the supplied screenshot at `944 × 415`, showing the login page scrolled to its footer and the police filing number without its emblem.
- Before evidence: `.codex-tmp/login-before-rendered-944x415.png`.
- Final implementation: `.codex-tmp/login-after-final-944x415.png` at the same `944 × 415` viewport.
- Comparison artifacts: `.codex-tmp/login-design-qa-comparison.png` and `.codex-tmp/login-design-qa-footer-comparison.png`.
- State: unauthenticated password-login mode, dark theme, Chinese locale.

## Findings

No actionable P0, P1, or P2 differences remain in the requested regions.

- Viewport behavior: desktop login now locks both the document root and body to the viewport. At `944 × 415`, `1024 × 600`, and `1366 × 768`, the document, page, and authentication shell report equal client and scroll heights with no vertical scrollbar.
- Short-height adaptation: below `680px` desktop height, nonessential alternate-login content is hidden and vertical gaps are compacted so the primary login workflow, agreement, and compliance footer remain visible.
- Mobile behavior: `390 × 844` and `360 × 640` retain a single-column composition with equal client and scroll heights and no horizontal overflow.
- Police filing identity: the existing official police filing asset is rendered directly before the police record text. It measures `20 × 20px` normally and `16 × 16px` in the short desktop layout.
- Link behavior: the filing entry links to the Ministry of Public Security query page with record code `32010502011484`.
- Existing visual language: hero imagery, panel proportions, typography, colors, control sizes, login modes, and备案 copy remain unchanged.
- Browser console: no change-related error was introduced; only the existing Ant Design modal deprecation warnings remain.

## Interaction checks

- Password and WeChat login modes switch successfully and return to the initial state.
- Account and password fields accept and clear input without submission.
- Theme toggling works and was restored.
- The login submit action remains enabled in the expected state.
- The police filing link and emblem are visible in the footer.

## Validation

- Focused Vitest: `1` file / `2` tests passed.
- TypeScript typecheck: passed.
- Login-flow test suite: passed.
- Stylelint: passed.
- Production build: passed.
- `git diff --check`: passed after preserving unrelated workspace changes.

## Final result

passed

---

# Design QA — Payment result home action removal (2026-08-11)

## Visual truth and implementation evidence

- Source visual truth: Browser Comment 1 on `/competitions/register/payment-result?registrationId=3`, selecting the `返回首页` action for removal.
- Saved before state: `artifacts/payment-result-home-removal/before-1117x751.png` (`1117 × 751` pixels from a matching `1117 × 751` CSS viewport; output and CSS dimensions are normalized 1:1).
- Intended implementation state: authenticated dark-theme payment-success result for registration `3` at the same viewport.
- Browser-rendered implementation screenshot: unavailable because the local authenticated session expired after the source capture and redirected the page to `/user/login`; Chrome was not available as an alternate authenticated browser surface.

## Findings

- The requested `返回首页` action was removed from the success-state action group.
- The existing `返回报名记录` action remains unchanged, and pending/error states retain their `刷新结果` action.
- Fonts and typography, spacing and layout rhythm, colors and visual tokens, image/icon quality, and all payment-result copy are otherwise unchanged by the one-line component edit.
- A same-state full-view and focused-region post-change comparison could not be completed without an authenticated payment-result render.

## Comparison history

1. The authenticated before-state capture confirmed the selected blue `返回首页` action beside `返回报名记录`.
2. Removed only the success-state home action from `PaymentResultPage`; payment status handling, refresh behavior, and registration navigation were preserved.
3. TypeScript typecheck, project lint, `git diff --check`, and the focused registration-checkout test suite passed. Project lint reported two pre-existing warnings outside `PaymentResultPage` and no errors.
4. Post-change browser capture was blocked by session expiry before an authenticated implementation screenshot could be produced.

## Final result

final result: blocked

---

# Design QA — Registration table container responsiveness (2026-08-11)

## Visual truth and implementation evidence

- Source visual truth: Browser Comment 1 on `/competitions/registrations`, captured at a `1117 × 751` viewport with the registration table clipped behind the fixed operation column.
- Final implementation at the matching viewport: `artifacts/registration-table-responsive/after-1117x751.png` (`1117 × 751` CSS pixels and output pixels).
- Additional breakpoint evidence: `artifacts/registration-table-responsive/after-768x800.png`, `after-1366x768.png`, and `after-1920x900.png`.
- State: authenticated Administrator session, Chinese locale, dark theme, competition `全国大学生智能应用开发大赛`, and nine populated registration rows.

## Findings

No actionable P0, P1, or P2 differences remain in the requested table behavior.

- The table now responds to its own container width instead of keeping a fixed `1420px` content canvas. At `1117px`, the required team, project, student-count, status, and action columns fill the available `803px` table container with equal client and scroll widths.
- Lower-priority columns are progressively introduced as space becomes available: material at `1366px`; competition, participant number, and registration time at `1920px`.
- The selected competition remains available in the search form, so repeating the same event name on every narrow-screen row is intentionally deferred until the `xxl` table-container tier.
- Team names and registration numbers use bounded ellipsis and tooltips. The operation column is fixed only on desktop-sized layouts, preventing its sticky layer from obscuring narrow-table content.
- The document and table reported no horizontal overflow at `768`, `1117`, `1366`, or `1920` pixels.

## Required fidelity surfaces

- Fonts and typography: existing Ant Design font family, weights, sizes, status labels, and registration-number hierarchy are unchanged.
- Spacing and layout rhythm: existing search card, toolbar, row heights, selection spacing, and pagination rhythm are preserved; only column allocation changes by container tier.
- Colors and visual tokens: dark-theme surfaces, borders, primary blue actions, status colors, and secondary text continue to use the existing application tokens.
- Image quality and asset fidelity: no image asset was introduced or replaced; existing Ant Design export, material, view, density, and settings icons remain unchanged.
- Copy and content: column labels, filter copy, row values, export labels, and detail-drawer content are preserved.

## Interaction and runtime checks

- Selecting one row immediately changed the primary actions to `导出所选（1）` and `下载所选材料包（1）`; cancelling selection restored both filtered-result labels with a count of nine.
- `查看资料` opened the populated team detail drawer and its close action returned to the table.
- The registration query returned nine rows from the active `lumira_dev` local database after restoring the previously created demo dataset to the active development runtime.
- Visible table headers matched the intended tiers: `报名团队 / 项目 / 状态 / 操作` at `768px`; student count added at `1117px`; material added at `1366px`; and the full column set at `1920px`.

## Validation

- TypeScript typecheck: passed.
- ESLint: passed with two pre-existing unused-import warnings outside this change.
- Stylelint: passed.
- Focused Vitest: `2` files / `13` tests passed.
- `git diff --check`: passed for the edited page files.

## Comparison history

1. The source showed a fixed-width table whose right operation column covered intermediate columns in the `1117 × 751` layout.
2. Replaced the page-level fixed scroll width with container-responsive column tiers, flexible content widths, and bounded team cells.
3. Matching-viewport comparison confirmed the table fits the card without document or table overflow while retaining the useful status column.
4. Additional narrow, medium, and wide viewport checks confirmed progressive disclosure without clipped headers, toolbar actions, or row controls.

## Final result

final result: passed

---

# Design QA — Registration detail single-row fields (2026-08-11)

## Visual truth and implementation evidence

- Source visual truth: Browser Comment 1 on `/competitions/registrations`, requesting one field per row in the registration-detail drawer.
- Browser-captured source state: `artifacts/registration-detail-single-row/before-877x751.png`.
- Browser-rendered implementation: `artifacts/registration-detail-single-row/after-877x751.png`.
- Combined comparison input: `artifacts/registration-detail-single-row/comparison-before-after-877x751.png`.
- Viewport and pixels: both source and implementation are `877 × 751` CSS pixels and output pixels at 1:1 density.
- State: authenticated Administrator session, Chinese locale, dark theme, `智能守护队` registration detail drawer open at its top.

## Findings

No actionable P0, P1, or P2 differences remain for the requested layout.

- The source grouped three overview fields or two snapshot fields on one row. The implementation gives every overview, registration, team, and project field its own bordered row.
- Browser structure confirms four description tables with `9 / 3 / 3 / 3` rows respectively, and every row contains exactly one label cell plus one value cell.
- The document remains `877px` wide with equal client and scroll widths, so the denser vertical layout does not introduce horizontal overflow.

## Required fidelity surfaces

- Fonts and typography: existing typeface, weights, sizes, line heights, label treatment, copy icons, and hierarchy remain unchanged.
- Spacing and layout rhythm: card padding, borders, radii, section gaps, drawer header, and row heights are preserved; only the number of fields per row changes.
- Colors and visual tokens: dark surfaces, borders, foreground hierarchy, and blue copy/export icons continue to use the existing theme tokens.
- Image quality and asset fidelity: no image or icon asset changed; existing Ant Design icons remain intact.
- Copy and content: all labels, registration values, team/project snapshots, and action copy are unchanged.

## Interaction and runtime checks

- Closing and reopening the `智能守护队` detail drawer returned to the top with the single-row layout intact.
- Existing copy controls remained visible beside string values.
- The student-member and stage-material tables intentionally keep their row-based tabular structure.
- No change-related error or overflow state appeared during the browser regression.

## Validation

- TypeScript typecheck: passed.
- ESLint: passed with two pre-existing unused-import warnings outside this change.
- Stylelint: passed.
- `git diff --check`: passed for the edited page.

## Comparison history

1. The captured source showed three-column overview rows and two-column snapshot rows, creating the layout called out by the annotation.
2. Changed both description configurations to one column without altering data, cards, tables, icons, or drawer behavior.
3. The same-state combined comparison confirmed one label/value pair per row across all targeted sections with no P0/P1/P2 visual regression.

## Final result

final result: passed
