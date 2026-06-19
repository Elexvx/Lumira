#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const fullReportFile = path.join(repoRoot, "docs", "security-assessment-full-test-report.md");
const trackerFile = path.join(repoRoot, "docs", "security-assessment-remediation-tracker.md");

const fullReportText = fs.readFileSync(fullReportFile, "utf8");
const trackerText = fs.readFileSync(trackerFile, "utf8");
const combinedText = `${fullReportText}\n${trackerText}`;

function assertIncludes(text, marker, message) {
  assert(text.includes(marker), message ?? `missing marker: ${marker}`);
}

function sectionBetween(text, startMarker, endMarker) {
  const start = text.indexOf(startMarker);
  assert(start >= 0, `missing section start: ${startMarker}`);
  const end = text.indexOf(endMarker, start + startMarker.length);
  assert(end >= 0, `missing section end: ${endMarker}`);
  return text.slice(start, end);
}

function sectionFrom(text, startMarker) {
  const start = text.indexOf(startMarker);
  assert(start >= 0, `missing section start: ${startMarker}`);
  return text.slice(start);
}

for (const [name, text] of [
  ["full security assessment report", fullReportText],
  ["security remediation tracker", trackerText],
]) {
  assert(!/\?{4,}/.test(text), `${name} must not contain repeated question-mark mojibake`);
  assert(!text.includes("\uFFFD"), `${name} must not contain replacement characters`);
  assert(!/[瀹鎶璇涓]{2,}/.test(text), `${name} must not contain common garbled Chinese fragments`);
}

for (const marker of [
  "# Lumira 安全评估与全量测试报告",
  "## 2. 标准逐项对照矩阵",
  "## 3. 详细测评项清单",
  "## 10. 剩余阻断责任矩阵",
  "## 11. 全量排查判定",
]) {
  assertIncludes(fullReportText, marker);
}

assertIncludes(trackerText, "# Lumira 安全评估整改跟踪表");
assertIncludes(trackerText, "## 尚未闭环");

const detailedChecklist = sectionBetween(
  fullReportText,
  "## 3. 详细测评项清单",
  "## 4. 范围说明",
);
const detailedChecklistRows = detailedChecklist.split(/\r?\n/).filter((line) => /^\| \d+ \|/.test(line));
assert.equal(detailedChecklistRows.length, 21, "detailed assessment checklist must keep 21 numbered rows");

const blockerMatrix = sectionBetween(
  fullReportText,
  "## 10. 剩余阻断责任矩阵",
  "## 11. 全量排查判定",
);
for (const gate of [
  "release-env",
  "runtime-business",
  "rollback",
  "migration",
  "explain",
  "first-wave-env-receipt",
  "lane-completion-receipt",
  "owner-evidence",
  "production-audit",
  "final-go-no-go",
]) {
  assertIncludes(blockerMatrix, gate, `remaining blocker matrix must include ${gate}`);
}

for (const marker of [
  "GB/T 22239-2019",
  "GB/T 28449-2018",
  "GB/T 20984-2022",
  "GB/T 30279-2020",
  "OWASP Testing Guide V4",
  "MITRE ATT&CK",
  "NIST SP 800-115",
  "E-REL-05",
  "E-REL-06",
  "E-REL-07",
  "E-REL-08",
  "E-DOC-01",
  "E-DOC-02",
  "E-DOC-03",
  "lane completion receipt 自动填充辅助",
  "node scripts/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>",
  "P0 release env 填写清单",
  "node scripts/ddd-release-env-fill-checklist.test.mjs",
  "55 个 primary blocker key",
  "63 个 config blocker",
  "第一波 env 脱敏回执样例合约",
  "receiptStatus 仍为 BLOCKED",
  "NO_GO_STRICT",
  "32 个阻断输入",
  "5 个阻断 gate",
  "3 条并行解阻工作流",
  "5 个阻断审计项",
]) {
  assertIncludes(combinedText, marker);
}

const fullAssessmentDecision = sectionFrom(
  fullReportText,
  "## 11. 全量排查判定",
);
assertIncludes(fullReportText, "不能判定：生产环境全量安全测评已完成。");
assertIncludes(fullAssessmentDecision, "本机可验证边界");
assertIncludes(trackerText, "发布证据仍处于严格不放行状态");

console.log("[security-assessment-report-contract.test] ok");
