# Rollback Deferral Owner Handoff

This handoff is a coordination aid only. It does not make rollback drills pass. The release gate accepts `DEFERRED` only after the deferral JSON contains real reason, approver, evidence reference, and a future expiration for each context.

| Owner | Contexts | Handoff file |
|---|---|---|
| ai-owner | AI | ai-owner.md |
| auth-owner | Auth | auth-owner.md |
| file-owner | File | file-owner.md |
| iam-owner | IAM | iam-owner.md |
| job-owner | Job | job-owner.md |
| localization-owner | Localization | localization-owner.md |
| message-owner | Message | message-owner.md |
| payment-owner | Payment | payment-owner.md |
| platform-owner | Platform | platform-owner.md |
| plugin-owner | Plugin | plugin-owner.md |

Strict validation command:

```sh
DDD_ROLLBACK_DRILL_STRICT=true DDD_ROLLBACK_DRILL_DEFERRAL_FILE=<approved-deferrals.json> node scripts/ddd-rollback-drill-evidence.mjs
```

