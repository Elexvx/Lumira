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
]) {
  assertIncludes(fullReportText, marker);
}

assertIncludes(trackerText, "# Lumira 安全评估整改跟踪表");

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
  "E-DOC-01",
  "E-DOC-02",
  "32 个阻断输入",
  "5 个阻断 gate",
  "3 条并行解阻工作流",
  "5 个阻断审计项",
]) {
  assertIncludes(combinedText, marker);
}

console.log("[security-assessment-report-contract.test] ok");
