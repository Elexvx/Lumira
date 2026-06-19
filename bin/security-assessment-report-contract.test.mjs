#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const fullReportFile = path.join(repoRoot, "doc", "security-assessment-full-test-report.md");
const trackerFile = path.join(repoRoot, "doc", "security-assessment-remediation-tracker.md");

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
  const mojibakeFragments = ["\u7039\u5927", "\u93c1", "\u6daf", "\u95c1", "\u95bb", "\u940e", "\u951f"];
  for (const fragment of mojibakeFragments) {
    assert(!text.includes(fragment), `${name} must not contain garbled Chinese fragment: ${fragment}`);
  }
}

for (const marker of [
  "# Lumira",
  "## 2.",
  "## 3.",
  "## 10.",
  "## 11.",
]) {
  assertIncludes(fullReportText, marker);
}

assertIncludes(trackerText, "# Lumira");

const detailedChecklist = sectionBetween(fullReportText, "## 3.", "## 4.");
const detailedChecklistRows = detailedChecklist.split(/\r?\n/).filter((line) => /^\| \d+ \|/.test(line));
assert.equal(detailedChecklistRows.length, 21, "detailed assessment checklist must keep 21 numbered rows");

const blockerMatrix = sectionBetween(fullReportText, "## 10.", "## 11.");
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
  "node bin/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>",
  "node bin/ddd-release-env-fill-checklist.test.mjs",
  "NO_GO_STRICT",
  "E-REL-09",
  "release-env-lint.attempt.json",
]) {
  assertIncludes(combinedText, marker);
}

const fullAssessmentDecision = sectionFrom(fullReportText, "## 11.");
assertIncludes(fullAssessmentDecision, "NO_GO_STRICT");

console.log("[security-assessment-report-contract.test] ok");
