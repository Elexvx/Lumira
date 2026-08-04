# Theme consistency audit

## Overall verdict

The theme state is global, but the theme control is not yet globally unified.

- Global foundation: `ThemePreferenceProvider` wraps the entire application root. Both the login page and authenticated `TopActions` consume the same `useThemePreference()` context and persist through the same `theme_preference` storage setting.
- Interaction mismatch: login exposes a direct two-state light/dark toggle. The authenticated header exposes a dropdown with system, light, dark, and compact preferences.
- Semantic mismatch: the login icon represents the destination mode, while the authenticated header icon represents the current preference.

## Captured steps

1. Login page, dark preference — healthy
   - Evidence: `01-login-dark.png` (`490 × 938`, CSS viewport `490 × 938`, density `1`).
   - The root reports `data-theme="dark"` and `data-theme-preference="dark"`.
   - The control is uniquely exposed as “切换为日间模式”.

2. Login theme toggle and protected-route navigation — theme persistence healthy, authenticated visual verification blocked
   - Evidence: `02-protected-route-redirect-light.png` (`490 × 938`, CSS viewport `490 × 938`, density `1`).
   - After switching, the root reports `data-theme="light"` and `data-theme-preference="light"`.
   - Navigating to `/dashboard` preserves the light preference, then redirects to `/user/login?redirect=%2Fdashboard%2Fhome` because no authenticated local session/backend is available.

## Findings

1. [P2] The global state is shared, but the visible control is inconsistent.
   - Login: one click alternates only light and dark.
   - Authenticated header: a menu selects system, light, dark, or compact.
   - Impact: a user who chose system or compact after login loses that preference if they later use the login-page toggle; the same icon also teaches two different interaction models.
   - Recommended fix: extract one shared theme control and use the authenticated four-option menu in both contexts, adapting only the button styling to each surface.

2. Accessibility check — mostly healthy with a consistency risk.
   - Login has a unique action label that updates with the destination theme.
   - Authenticated code provides a current-theme label and menu selection state.
   - The remaining risk is conceptual rather than a missing accessible name: identical global settings behave differently before and after authentication.

## Evidence limits

The authenticated shell could not be captured in this run because the local backend/session is unavailable and the protected route redirects to login. The authenticated control structure and shared provider were verified from the current application source, but its rendered post-login appearance is not claimed as browser-captured evidence.

## Recommended next decision

Use the four-option dropdown globally, retaining the compact icon button on login while opening the same menu used after authentication.
