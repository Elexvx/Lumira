# DDD Release Owner Input Receipt Items: release-infra

Generated at: 2026-06-17T08:00:49.110Z
Status: PENDING_OWNER_INPUT
Cutover ready: false
Owner input items: 9

## Items

- [ ] 26. `LUMIRA_BASE_URL` status=PLACEHOLDER; class=url; reason=production-endpoint; secret=false; https=true; nonLocal=true; aliases=LUMIRA_BASE_URL|DEPLOY_CHECK_BASE_URL; packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json; handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
  - Collection: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- [ ] 27. `CORS_ALLOWED_ORIGIN_PATTERNS` status=PLACEHOLDER; class=identifier; reason=owner-production-value; secret=false; https=false; nonLocal=false; aliases=CORS_ALLOWED_ORIGIN_PATTERNS|SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS; packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json; handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
  - Collection: Collect the production value from the owning release context.
- [ ] 28. `DB_PASSWORD` status=PLACEHOLDER; class=secret; reason=secret-manager; secret=true; https=false; nonLocal=false; aliases=DB_PASSWORD|SPRING_DATASOURCE_PASSWORD|MYSQL_PASSWORD; packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json; handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
  - Collection: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- [ ] 29. `DB_URL` status=PLACEHOLDER; class=url; reason=production-endpoint; secret=false; https=false; nonLocal=true; aliases=DB_URL|SPRING_DATASOURCE_URL; packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json; handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
  - Collection: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- [ ] 30. `DB_USERNAME` status=PLACEHOLDER; class=identifier; reason=owner-production-value; secret=false; https=false; nonLocal=false; aliases=DB_USERNAME|SPRING_DATASOURCE_USERNAME|MYSQL_USER; packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json; handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
  - Collection: Collect the production value from the owning release context.
- [ ] 31. `FIELD_SECRET` status=PLACEHOLDER; class=secret; reason=secret-manager; secret=true; https=false; nonLocal=false; aliases=FIELD_SECRET|SAAS_SECURITY_FIELD_SECRET; packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json; handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
  - Collection: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- [ ] 32. `PLAYWRIGHT_BASE_URL` status=PLACEHOLDER; class=url; reason=production-endpoint; secret=false; https=true; nonLocal=true; aliases=PLAYWRIGHT_BASE_URL|FRONTEND_BASE_URL; packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json; handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
  - Collection: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.
- [ ] 33. `JWT_SECRET` status=PLACEHOLDER; class=secret; reason=secret-manager; secret=true; https=false; nonLocal=false; aliases=JWT_SECRET|SAAS_SECURITY_JWT_SECRET; packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json; handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
  - Collection: Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts.
- [ ] 34. `REDIS_HOST` status=PLACEHOLDER; class=identifier; reason=production-endpoint; secret=false; https=false; nonLocal=true; aliases=REDIS_HOST|SPRING_DATA_REDIS_HOST; packet=artifacts/ddd/release/release-env-owner-input-packet/03-release-infra.json; handoff=artifacts/ddd/release/release-env-owner-handoff-redacted/03-release-infra.md
  - Collection: Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform.

Concrete values are intentionally omitted from this artifact.
