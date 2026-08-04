# Route Navigation Repair — Design QA

- Source visual truth path: `C:/Users/ADMINI~1/AppData/Local/Temp/codex-clipboard-47cb2be3-b6ff-480e-9444-564c7d1d02b2.png`
- Implementation screenshot path: unavailable; the production preview is logged out and the authenticated menu state cannot be captured without a running backend session.
- Viewport: browser default, 1265 × 712 CSS px for the available logged-out preview.
- Source pixels: 642 × 468.
- Implementation pixels: unavailable for the authenticated state.
- Density normalization: not applicable because the source and implementation could not be captured in the same state.
- State: source is an authenticated dark-theme sidebar; implementation preview is a logged-out login screen.

**Findings**

- [Blocked] The source and implementation do not represent the same authentication and route state.
  Location: authenticated main sidebar.
  Evidence: the source shows the certificate/expert menu region; the local production preview can only render `/user/login` because its backend session is unavailable.
  Impact: typography, spacing, colors, icons, copy, expansion state, and selected-state fidelity cannot be judged honestly from browser evidence.
  Fix: run the migrated platform with an authenticated account, open the certificate, expert, registration, and workflow routes, then capture the sidebar at the source viewport.

**Open Questions**

- None about the intended hierarchy. The code and database contracts now agree on the parent/child placement; only authenticated visual evidence is unavailable.

**Implementation Checklist**

- Completed: keep redirect catalogs on their own menu identity instead of borrowing the default leaf key.
- Completed: give certificate and expert catalogs stable keys.
- Completed: remove the retired competition catalog injection path.
- Completed: assign unique persisted IDs to review results and personal certificates.
- Completed: move expert application under expert review and keep personal certificates under registration.
- Completed: add an idempotent online migration and bump the menu-tree read-model version.
- Remaining: capture and compare the authenticated sidebar after the backend migration is running.

**Full-view comparison evidence**

- Blocked: the available implementation full view is the login screen, not the authenticated sidebar.

**Focused region comparison evidence**

- Blocked: the certificate/expert sidebar region is not present in the logged-out preview.

**Comparison history**

- Initial source review found the certificate catalog displaced by `/certificates/mine`, with its unique children hoisted into the top level.
- The implementation was corrected at the menu-identity and persisted-hierarchy layers.
- Post-fix code evidence passed across certificate, expert, activity, competition, payment, and workflow catalogs; post-fix authenticated visual evidence remains unavailable.

final result: blocked
