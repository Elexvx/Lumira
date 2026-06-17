#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

const contexts = [
  {
    name: "IAM",
    route: "/api/v2/iam",
    controller: "services/system-service/src/main/java/com/lumira/saas/modules/iam/controller/IamReadinessV2Controller.java",
    test: "services/system-service/src/test/java/com/lumira/saas/modules/iam/controller/IamReadinessV2ControllerTest.java",
  },
  {
    name: "Auth",
    route: "/api/v2/auth",
    controller: "services/auth-service/src/main/java/com/lumira/auth/controller/AuthReadinessV2Controller.java",
    test: "services/auth-service/src/test/java/com/lumira/auth/controller/AuthReadinessV2ControllerTest.java",
  },
  {
    name: "Platform",
    route: "/api/v2/platform",
    controller: "services/system-service/src/main/java/com/lumira/saas/modules/platform/controller/PlatformReadinessV2Controller.java",
    test: "services/system-service/src/test/java/com/lumira/saas/modules/platform/controller/PlatformReadinessV2ControllerTest.java",
  },
  {
    name: "Message",
    route: "/api/v2/message",
    controller: "services/message-service/src/main/java/com/lumira/message/controller/MessageReadinessV2Controller.java",
    test: "services/message-service/src/test/java/com/lumira/message/controller/MessageReadinessV2ControllerTest.java",
  },
  {
    name: "File",
    route: "/api/v2/files",
    controller: "services/file-service/src/main/java/com/lumira/file/controller/FileReadinessV2Controller.java",
    test: "services/file-service/src/test/java/com/lumira/file/controller/FileReadinessV2ControllerTest.java",
  },
  {
    name: "Plugin",
    route: "/api/v2/plugins",
    controller: "services/plugin-service/src/main/java/com/lumira/saas/modules/plugin/controller/PluginReadinessV2Controller.java",
    test: "services/plugin-service/src/test/java/com/lumira/saas/modules/plugin/controller/PluginReadinessV2ControllerTest.java",
  },
  {
    name: "Localization",
    route: "/api/v2/localization",
    controller: "services/localization-service/src/main/java/com/lumira/saas/modules/localization/controller/LocalizationReadinessV2Controller.java",
    test: "services/localization-service/src/test/java/com/lumira/saas/modules/localization/controller/LocalizationReadinessV2ControllerTest.java",
  },
  {
    name: "Payment",
    route: "/api/v2/payment",
    controller: "services/payment-service/src/main/java/com/lumira/payment/controller/PaymentReadinessV2Controller.java",
    test: "services/payment-service/src/test/java/com/lumira/payment/controller/PaymentReadinessV2ControllerTest.java",
  },
  {
    name: "AI",
    route: "/api/v2/ai",
    controller: "services/system-service/src/main/java/com/lumira/saas/modules/ai/controller/AiReadinessV2Controller.java",
    test: "services/system-service/src/test/java/com/lumira/saas/modules/ai/controller/AiReadinessV2ControllerTest.java",
  },
  {
    name: "Job",
    route: "/api/v2/job",
    controller: "services/job-executor/src/main/java/com/lumira/job/JobReadinessV2Controller.java",
    test: "services/job-executor/src/test/java/com/lumira/job/JobReadinessV2ControllerTest.java",
  },
];

const docs = [
  "docs/26-ddd-architecture-migration.md",
  "docs/29-ddd-physical-split-readiness.md",
];

function fail(message) {
  console.error(`[ddd-readiness-gate] ${message}`);
  process.exitCode = 1;
}

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function assertFile(relativePath, label) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!fs.existsSync(absolutePath)) {
    fail(`missing ${label}: ${relativePath}`);
    return "";
  }
  return fs.readFileSync(absolutePath, "utf8");
}

const docText = docs.map((doc) => assertFile(doc, "DDD document")).join("\n");

for (const context of contexts) {
  const controller = assertFile(context.controller, `${context.name} readiness controller`);
  assertFile(context.test, `${context.name} readiness test`);

  if (!controller.includes(`@RequestMapping("${context.route}")`)) {
    fail(`${context.name} readiness controller does not declare route ${context.route}`);
  }
  for (const suffix of ["readiness", "health", "metrics"]) {
    if (!controller.includes(`@GetMapping("/${suffix}")`)) {
      fail(`${context.name} readiness controller missing /${suffix} endpoint`);
    }
    if (!docText.includes(`${context.route}/${suffix}`)) {
      fail(`${context.name} readiness docs missing ${context.route}/${suffix}`);
    }
  }
  if (!controller.includes(`"${context.name}"`)) {
    fail(`${context.name} readiness controller does not expose context name`);
  }
}

const runbook = assertFile("docs/31-ddd-operational-runbook.md", "DDD operational runbook");
for (const context of contexts) {
  if (!runbook.includes(context.name) || !runbook.includes(`${context.route}/readiness`)) {
    fail(`DDD operational runbook missing ${context.name} readiness drill`);
  }
}

if (!process.exitCode) {
  console.log(`[ddd-readiness-gate] validated readiness contracts for ${contexts.length} context(s)`);
}
