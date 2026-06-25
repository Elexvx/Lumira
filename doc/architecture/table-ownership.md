# Lumira Table Ownership

The machine-readable source of truth is `doc/27-ddd-owner-table-manifest.csv`. This document explains the long-term ownership policy.

## Rules

- Every business table has exactly one long-term owner module.
- Only the owner module may write its tables unless a temporary compatible writer is explicitly listed in the manifest.
- Non-owner modules must read through Internal API, events, or read models.
- Cross-module joins must be implemented as an owner-provided query facade or a read model.
- New migrations must live in the owner module.

## Current Owner Manifest

| Context | Owner module | Owned table patterns | Compatible writers | Notes |
| --- | --- | --- | --- | --- |
| AUTH | `lumira-auth` | `sys_user_passkey_credential`, `sys_user_wechat_binding`, `sys_verification_binding`, `sys_verification_challenge` | `lumira-system` | Authentication credentials and challenges are still bootstrapped by aggregate compatibility migrations. |
| IAM | `lumira-system` | `iam_user*`, `iam_subject*`, `iam_permission`, `iam_delegation_grant`, `sys_user`, `sys_role`, `sys_role_permission`, `sys_menu`, `sys_permission`, `sys_user_role`, `sys_department`, `sys_department_closure`, `sys_user_department`, `sys_role_data_scope` | - | Identity and permission master data. |
| PLATFORM | `lumira-system` | `sys_config`, `sys_dict_*`, `audit_*`, `security_audit_event`, `ddd_read_model_version`, `sys_export_task`, `sys_sensitive_word`, `platform_update_task` | - | Platform configuration, audit, read-model versioning, update orchestration, and governance data. |
| MESSAGE | `lumira-message` | `msg_*`, `platform_event_outbox` | `lumira-system` | Message tables are owned by `lumira-message`; `lumira-system` keeps aggregate runtime compatibility migrations. |
| FILE | `lumira-file` | `file_object`, `file_storage_space`, `file_processing_task`, `file_processing_artifact`, `platform_event_outbox` | `lumira-system` | File tables are owned by `lumira-file`; `lumira-system` keeps aggregate runtime compatibility migrations. |
| PLUGIN | `lumira-plugin` | `sys_plugin_*`, `plugin_event_outbox` | `lumira-system` | Plugin lifecycle data is owned by `lumira-plugin`; `lumira-system` keeps catalog and aggregate compatibility. |
| LOCALIZATION | `lumira-localization` | `sys_localization_*` | `lumira-system` | Localization tables are long-term owned by `lumira-localization`; `lumira-system` currently carries aggregate compatibility migrations. |
| PAYMENT | `lumira-payment` | `payment_*` | `lumira-system` | Payment tables are owned by `lumira-payment`; `lumira-system` keeps aggregate runtime compatibility migrations. |
| AI | `lumira-ai` | `ai_*` | `lumira-system` | AI assistant and knowledge-base data. AI must not directly write Team, Competition, or Registration tables. |
| TEAM | `lumira-team` | `team`, `team_member`, `team_invite`, `team_join_request` | - | Team core business data; teams are generic business subjects and are not tenants. |
| JOB | `lumira-quartz` | - | - | Job is a scheduler/relay adapter and must not own business tables. |

## Reserved Future Owners

| Future context | Reserved tables |
| --- | --- |
| COMPETITION | `competition`, `competition_rule`, `competition_stage`, `competition_group` |
| REGISTRATION | `competition_registration`, `competition_registration_member`, `competition_registration_audit` |
| SCHEDULE | `competition_match`, `competition_match_round`, `competition_match_result` |
| SCORE | `competition_score`, `competition_ranking` |
| CERTIFICATE | `certificate_template`, `certificate_issue` |
| PROJECT | `project`, `project_member`, `project_task` |

## Prohibited Writes

- `lumira-system` must not add new Team, Competition, or Registration writes.
- Future Competition and Registration modules must not directly write `team` or `team_member`.
- AI tools must call the target owner application service or API instead of directly writing business tables.
- `lumira-quartz` may trigger jobs and relays, but it must not own business tables.

## Change Checklist

When adding or moving a table owner, update:

- `doc/27-ddd-owner-table-manifest.csv`
- This document
- The owner module migration
- Architecture tests and explicit allowlists
- API, event, or read-model contracts used by other modules
