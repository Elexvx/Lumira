# Architecture Governance Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Turn the current microservice architecture recommendations into repository-level governance docs and a small Outbox implementation improvement.

**Architecture:** Keep `services/*` as the canonical runtime layout. Add service/data ownership, module-boundary, gateway/auth/permission, event/Outbox, and runbook documents, then make `system-service` Outbox usable by adding its migration and a standard event publisher.

**Tech Stack:** Java 21, Spring Boot, MyBatis Plus, Flyway, Umi Max, Docker Compose.

---

### Task 1: Service And Data Ownership

**Files:**
- Create: `docs/15-service-data-ownership.md`

**Steps:**
- Inventory current service modules and Flyway-owned tables.
- Document owner service, data ownership, migration placement, and cross-service access rules.
- Call out transitional tables that still need owner migration cleanup.

### Task 2: system-service Boundaries

**Files:**
- Create: `docs/16-system-service-module-boundaries.md`

**Steps:**
- Document IAM, AI, configuration, audit, monitor, online-session, and plugin-view responsibilities.
- Define allowed dependency direction and module split criteria.

### Task 3: Gateway, Auth, Permission Boundaries

**Files:**
- Create: `docs/17-gateway-auth-permission-boundaries.md`

**Steps:**
- Document request flow and role matrix for frontend, gateway, auth, system IAM, and business services.
- Record `permission_key` naming rules and current gateway route ownership.

### Task 4: Event And Outbox

**Files:**
- Create: `docs/18-event-outbox-architecture.md`
- Create: `services/system-service/src/main/resources/db/migration/V33__platform_event_outbox.sql`
- Create: `services/system-service/src/main/java/com/legendary/invention/saas/infrastructure/event/PlatformEventTypes.java`
- Create: `services/system-service/src/main/java/com/legendary/invention/saas/infrastructure/event/PlatformEventPublisher.java`
- Modify: `services/system-service/src/main/java/com/legendary/invention/saas/modules/ai/app/AiKnowledgeBaseAppService.java`
- Test: `services/system-service/src/test/java/com/legendary/invention/saas/infrastructure/event/PlatformEventPublisherTest.java`

**Steps:**
- Add the missing system-service outbox migration.
- Add standard source, event, and aggregate constants.
- Add a publisher that builds standard event keys and payloads.
- Publish AI knowledge document indexed/deleted events after transaction commit.
- Add focused unit coverage for the publisher.

### Task 5: Runbook And Index

**Files:**
- Create: `docs/19-architecture-runbook.md`
- Modify: `README.md`

**Steps:**
- Document local, test, and production startup.
- Document build, deploy, health-check, and troubleshooting commands.
- Link the new architecture documents from README.

### Verification

Run:

```bash
./mvnw -q -pl services/system-service -am -DskipTests compile
./mvnw -q -pl services/system-service -Dtest=PlatformEventPublisherTest test
git diff --check
```

### Follow-up Task: file-service Ownership And File Events

**Files:**
- Create: `services/file-service/src/main/resources/db/migration/V3__file_storage_space.sql`
- Create: `services/file-service/src/main/resources/db/migration/V4__platform_event_outbox.sql`
- Create: `services/file-service/src/main/java/com/legendary/invention/file/event/*`
- Modify: `services/file-service/src/main/java/com/legendary/invention/file/app/FileManagementAppService.java`
- Test: `services/file-service/src/test/java/com/legendary/invention/file/event/*Test.java`
- Modify: `docs/15-service-data-ownership.md`
- Modify: `docs/18-event-outbox-architecture.md`

**Steps:**
- Add file-service owner migration for `file_storage_space`.
- Add file-service outbox table and event publisher.
- Publish `FILE_OBJECT_UPLOADED` after upload metadata is written.
- Publish `FILE_OBJECT_DELETED` after file metadata is soft-deleted.
- Verify file-service compile and event tests.
