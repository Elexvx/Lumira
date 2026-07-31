# Design QA: Alipay key labels

- Source visual truth: the user's annotated payment configuration screenshot in this task.
- Implementation screenshot: [`payment-alipay-labels-production.png`](payment-alipay-labels-production.png)
- Viewport: 1265 × 712, desktop, dark theme.
- State: Settings → Payment → Add → Alipay configuration drawer, scrolled to key fields.
- Primary interactions tested: opened Add menu, selected Alipay, opened the drawer, and scrolled the public-key field into view.
- Console errors checked: 0.

## Full-view comparison evidence

The deployed drawer retains the source layout, spacing, typography, colors, controls, and field order. No surrounding UI was changed.

## Focused region comparison evidence

The annotated source requested Alipay-specific terminology. The deployed focused region now shows `支付宝公钥` for the public key and `应用私钥` for the private key. The public-key placeholder also uses `支付宝公钥`; the private-key preservation hint remains unchanged.

## Findings

- No actionable P0, P1, or P2 differences remain.
- Fonts and typography: unchanged from the source UI; both replacement labels use the existing form-label style.
- Spacing and layout rhythm: unchanged; the longer labels fit without wrapping or clipping.
- Colors and visual tokens: unchanged.
- Image quality and asset fidelity: no image assets are involved in this scoped change.
- Copy and content: matches the requested Alipay terminology.

## Comparison history

- Initial finding: generic labels `公钥` and `私钥` did not identify the exact Alipay key types.
- Fix: changed only the Alipay schema labels to `支付宝公钥` and `应用私钥`, with matching English labels and public-key placeholder.
- Post-fix evidence: production screenshot and DOM snapshot both show the requested labels; no console errors were recorded.

final result: passed
