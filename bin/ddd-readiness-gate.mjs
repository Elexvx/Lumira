#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

const contexts = [
  {
    name: "IAM",
    route: "/api/v2/iam",
    controller: "services/lumira-system/src/main/java/com/lumira/saas/modules/iam/controller/IamReadinessV2Controller.java",
    test: "services/lumira-system/src/test/java/com/lumira/saas/modules/iam/controller/IamReadinessV2ControllerTest.java",
  },
  {
    name: "Auth",
    route: "/api/v2/auth",
    controller: "services/lumira-auth/src/main/java/com/lumira/auth/controller/AuthReadinessV2Controller.java",
    test: "services/lumira-auth/src/test/java/com/lumira/auth/controller/AuthReadinessV2ControllerTest.java",
  },
  {
    name: "Platform",
    route: "/api/v2/platform",
    controller: "services/lumira-system/src/main/java/com/lumira/saas/modules/platform/controller/PlatformReadinessV2Controller.java",
    test: "services/lumira-system/src/test/java/com/lumira/saas/modules/platform/controller/PlatformReadinessV2ControllerTest.java",
  },
  {
    name: "Message",
    route: "/api/v2/message",
    controller: "services/lumira-message/src/main/java/com/lumira/message/controller/MessageReadinessV2Controller.java",
    test: "services/lumira-message/src/test/java/com/lumira/message/controller/MessageReadinessV2ControllerTest.java",
  },
  {
    name: "File",
    route: "/api/v2/files",
    controller: "services/lumira-file/src/main/java/com/lumira/file/controller/FileReadinessV2Controller.java",
    test: "services/lumira-file/src/test/java/com/lumira/file/controller/FileReadinessV2ControllerTest.java",
  },
  {
    name: "Plugin",
    route: "/api/v2/plugins",
    controller: "services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/controller/PluginReadinessV2Controller.java",
    test: "services/lumira-plugin/src/test/java/com/lumira/saas/modules/plugin/controller/PluginReadinessV2ControllerTest.java",
  },
  {
    name: "Localization",
    route: "/api/v2/localization",
    controller: "services/lumira-localization/src/main/java/com/lumira/saas/modules/localization/controller/LocalizationReadinessV2Controller.java",
    test: "services/lumira-localization/src/test/java/com/lumira/saas/modules/localization/controller/LocalizationReadinessV2ControllerTest.java",
  },
  {
    name: "Payment",
    route: "/api/v2/payment",
    controller: "services/lumira-payment/src/main/java/com/lumira/payment/controller/PaymentReadinessV2Controller.java",
    test: "services/lumira-payment/src/test/java/com/lumira/payment/controller/PaymentReadinessV2ControllerTest.java",
  },
  {
    name: "AI",
    route: "/api/v2/ai",
    controller: "services/lumira-system/src/main/java/com/lumira/saas/modules/ai/controller/AiReadinessV2Controller.java",
    test: "services/lumira-system/src/test/java/com/lumira/saas/modules/ai/controller/AiReadinessV2ControllerTest.java",
  },
  {
    name: "Job",
    route: "/api/v2/job",
    controller: "services/lumira-quartz/src/main/java/com/lumira/job/JobReadinessV2Controller.java",
    test: "services/lumira-quartz/src/test/java/com/lumira/job/JobReadinessV2ControllerTest.java",
  },
];

const docs = [
  "doc/26-ddd-architecture-migration.md",
  "doc/29-ddd-physical-split-readiness.md",
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

const runbook = assertFile("doc/31-ddd-operational-runbook.md", "DDD operational runbook");
for (const context of contexts) {
  if (!runbook.includes(context.name) || !runbook.includes(`${context.route}/readiness`)) {
    fail(`DDD operational runbook missing ${context.name} readiness drill`);
  }
}

if (!process.exitCode) {
  console.log(`[ddd-readiness-gate] validated readiness contracts for ${contexts.length} context(s)`);
}
