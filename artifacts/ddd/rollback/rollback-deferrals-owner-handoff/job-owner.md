# Rollback Deferral Handoff: job-owner

Fill the JSON deferral template with real approval evidence before running the strict rollback drill validator.

Required fields for every deferred context:
- `notExercisableReason`: concrete release-window reason, no placeholders.
- `riskAcceptedBy`: named approver or approval group.
- `deferralEvidence`: approval ticket, change record, meeting note, artifact/log path, HTTPS link, or object URI.
- `expiresAt`: future ISO timestamp.

| Context | Intended rollback action | Deferral evidence placeholder | Expires at |
|---|---|---|---|
| Job | Exercise XXL-JOB handler disablement and manual owner internal endpoint fallback; attach dashboard, token, and endpoint evidence. | CHANGE-12345 | replace-with-future-iso-timestamp |

After filling the approved deferral file, run:

```sh
DDD_ROLLBACK_DRILL_STRICT=true DDD_ROLLBACK_DRILL_DEFERRAL_FILE=<approved-deferrals.json> node scripts/ddd-rollback-drill-evidence.mjs
```

