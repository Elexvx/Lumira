#!/usr/bin/env node

import {
  buildProductionEquivalenceEvidence,
  evidenceValueIssue,
  isHttpsUrl,
  isLocalUrlLike,
} from "./ddd-release-evidence-utils.mjs";

const args = new Set(process.argv.slice(2));
const help = args.has("--help") || args.has("-h");

function printHelp() {
  console.log(`DDD staging runtime readiness check

Usage:
  node scripts/ddd-staging-runtime-check.mjs [options]

Options:
  --help, -h    Show this help.

Environment:
  LUMIRA_BASE_URL                       Backend HTTPS staging URL.
  PLAYWRIGHT_BASE_URL                   Frontend HTTPS staging URL.
  DDD_DEPLOYMENT_EVIDENCE               Deployment URL, CI run, or release artifact proving this is staged.
  DDD_FRONTEND_EXPECT_DEPLOYED          Set true before frontend staging smoke evidence.
  DDD_AI_EXPECT_PROVIDER_REMOTE         Set true before AI runtime drill evidence.
  DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE    Set true before AI owner gateway drill evidence.

Examples:
  node scripts/ddd-staging-runtime-check.mjs
`);
}

if (help) {
  printHelp();
  process.exit(0);
}

const backendBaseUrl = process.env.LUMIRA_BASE_URL || "";
const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || "";
const deploymentEvidence = process.env.DDD_DEPLOYMENT_EVIDENCE || "";
const frontendDeploymentEvidence = process.env.DDD_FRONTEND_DEPLOYMENT_EVIDENCE || deploymentEvidence;
const aiDeploymentEvidence = process.env.DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE || deploymentEvidence;
const authPerfDeploymentEvidence = process.env.DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE || deploymentEvidence;

function urlCheck(name, value) {
  const issues = [];
  const valueIssue = evidenceValueIssue(value);
  if (valueIssue) issues.push(`${name} ${valueIssue}`);
  if (!isHttpsUrl(value)) issues.push(`${name} must be an HTTPS URL`);
  if (isLocalUrlLike(value)) issues.push(`${name} must not be localhost or loopback`);
  return {
    name,
    valuePresent: typeof value === "string" && value.trim().length > 0,
    https: isHttpsUrl(value),
    localOnly: isLocalUrlLike(value),
    issues,
  };
}

function envFlag(name, expected = "true") {
  const value = process.env[name] || "";
  const pass = value === expected;
  return {
    name,
    expected,
    valuePresent: value.length > 0,
    pass,
    issue: pass ? null : `${name} must be ${expected}`,
  };
}

const backendProductionEquivalence = buildProductionEquivalenceEvidence({
  strict: true,
  baseUrl: backendBaseUrl,
  deploymentEvidence,
  evidenceName: "backend runtime staging check",
});
const frontendProductionEquivalence = buildProductionEquivalenceEvidence({
  strict: true,
  baseUrl: frontendBaseUrl,
  deploymentEvidence: frontendDeploymentEvidence,
  evidenceName: "frontend runtime staging check",
});

const urlChecks = [
  urlCheck("LUMIRA_BASE_URL", backendBaseUrl),
  urlCheck("PLAYWRIGHT_BASE_URL", frontendBaseUrl),
];
const evidenceChecks = [
  { name: "DDD_DEPLOYMENT_EVIDENCE", issue: evidenceValueIssue(deploymentEvidence) },
  { name: "DDD_FRONTEND_DEPLOYMENT_EVIDENCE", issue: evidenceValueIssue(frontendDeploymentEvidence) },
  { name: "DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE", issue: evidenceValueIssue(aiDeploymentEvidence) },
  { name: "DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE", issue: evidenceValueIssue(authPerfDeploymentEvidence) },
];
const expectationChecks = [
  envFlag("DDD_FRONTEND_EXPECT_DEPLOYED"),
  envFlag("DDD_AI_EXPECT_PROVIDER_REMOTE"),
  envFlag("DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE"),
];

const issues = [
  ...urlChecks.flatMap((check) => check.issues),
  ...backendProductionEquivalence.issues,
  ...frontendProductionEquivalence.issues,
  ...evidenceChecks.map((check) => check.issue ? `${check.name} ${check.issue}` : null).filter(Boolean),
  ...expectationChecks.map((check) => check.issue).filter(Boolean),
];

const result = {
  status: issues.length === 0 ? "PASS" : "BLOCKED",
  generatedAt: new Date().toISOString(),
  willWriteFiles: false,
  backendBaseUrl: backendBaseUrl || null,
  frontendBaseUrl: frontendBaseUrl || null,
  productionEquivalence: {
    backend: backendProductionEquivalence,
    frontend: frontendProductionEquivalence,
  },
  urlChecks,
  evidenceChecks,
  expectationChecks,
  nextCommands: [
    "node scripts/ddd-runtime-readiness-smoke.mjs",
    "DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs",
    "node scripts/ddd-ai-runtime-drill.mjs",
    "node scripts/ddd-frontend-playwright-smoke.mjs",
    "node scripts/ddd-frontend-smoke-evidence.mjs",
  ],
  issues,
};

console.log(JSON.stringify(result, null, 2));
process.exit(issues.length === 0 ? 0 : 1);
