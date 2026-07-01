# Login Layout Design QA

final result: passed

Source visual: user-provided Zhihu-style login layout screenshot.

Prototype captures:
- Desktop: `.codex-temp/login-layout-desktop.png`
- Mobile: `.codex-temp/login-layout-mobile.png`

Checks:
- Full-screen illustrated background is retained and fills the viewport.
- Brand lockup is centered above the main panel.
- Main login panel uses a white two-column layout on desktop with QR guidance on the left and the existing login form on the right.
- A thin vertical divider separates QR and form columns.
- The right-side login form now uses text tabs with an active blue underline and a right-aligned organization-account link.
- Forgot-password action, social login area, and agreement notice follow the reference ordering when the matching login capabilities are available.
- The agreement notice is styled as a bottom bordered text panel matching the highlighted reference area.
- Footer links sit at the bottom of the viewport on desktop.
- Mobile collapses to a single-column login form, hides the QR panel, and keeps footer text below the panel without horizontal compression.

Notes:
- The page keeps Lumira's existing cloud-platform background asset instead of using the Zhihu-branded illustration.
- Existing development warnings remain for project-wide `formatMessage` usage and deprecated Modal props; they are unrelated to this layout change.
