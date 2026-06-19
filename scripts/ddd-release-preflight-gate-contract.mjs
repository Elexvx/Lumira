#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const releaseDir = path.join(repoRoot, "artifacts", "ddd", "release");
const gatePath = path.join(releaseDir, "release-preflight-gate.sh");
const failures = [];

function bashPath(file) {
  const resolved = path.resolve(file);
  if (process.platform !== "win32") return resolved;
  return `/mnt/${resolved[0].toLowerCase()}${resolved.slice(2).replaceAll("\\", "/")}`;
}

function bashPathEnv() {
  if (process.platform !== "win32") return process.env.PATH;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-wsl-node-"));
  fs.writeFileSync(path.join(dir, "node"), `#!/usr/bin/env bash
args=()
for arg in "$@"; do
  if [[ "$arg" =~ ^/mnt/([a-zA-Z])/(.*)$ ]]; then
    drive="\${BASH_REMATCH[1]^^}:"
    rest="\${BASH_REMATCH[2]//\\//\\\\}"
    args+=("\${drive}\\\\\${rest}")
  else
    args+=("$arg")
  fi
done
exec "${bashPath(process.execPath)}" "\${args[@]}"
`);
  fs.chmodSync(path.join(dir, "node"), 0o755);
  return `${bashPath(dir)}:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin`;
}

const bashEnvPath = bashPathEnv();

function shellQuote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
}

function addFailure(message) {
  failures.push(message);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function readOptionalJson(file) {
  if (!fs.existsSync(file)) return null;
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return null;
  }
}

function runGate(reportFile, env = {}) {
  if (process.platform === "win32") {
    const command = [
      `export PATH=${shellQuote(bashEnvPath)}`,
      `export DDD_NODE_BIN=node`,
      `export LUMIRA_REPO_ROOT=${shellQuote(bashPath(repoRoot))}`,
      `export DDD_RELEASE_PREFLIGHT_REPORT=${shellQuote(bashPath(reportFile))}`,
      ...Object.entries(env).map(([key, value]) => `export ${key}=${shellQuote(value)}`),
      `bash ${shellQuote(bashPath(gatePath))}`,
    ].join("; ");
    return spawnSync("bash", ["-lc", command], {
      cwd: repoRoot,
      encoding: "utf8",
    });
  }
  return spawnSync("bash", [bashPath(gatePath)], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      PATH: bashEnvPath,
      LUMIRA_REPO_ROOT: bashPath(repoRoot),
      DDD_RELEASE_PREFLIGHT_REPORT: bashPath(reportFile),
      ...env,
    },
  });
}

if (!fs.existsSync(gatePath)) {
  addFailure(`release preflight gate script must exist: ${gatePath}`);
} else {
  const mode = fs.statSync(gatePath).mode & 0o777;
  if (process.platform !== "win32" && (mode & 0o111) === 0) addFailure("release preflight gate script must be executable");
  const source = fs.readFileSync(gatePath, "utf8");
  for (const snippet of [
    "set -euo pipefail",
    "DDD_RELEASE_PREFLIGHT_ENFORCE",
    "DDD_RELEASE_PREFLIGHT_REPORT",
    "safe_load_release_env_file",
    "DDD_RELEASE_ENV_FILE",
    "template-refused",
    "permission-refused",
    "release-artifact-integrity-gate.sh",
    "DDD_RELEASE_MANIFEST_CHECK_ENV=true",
    "ddd-release-evidence-manifest.mjs",
    "ddd-release-artifact-path-leak-contract.mjs",
    "ddd-release-unblock-brief.mjs",
    "ddd-release-unblock-brief-contract.mjs",
    "ddd-release-env-owner-handoff-redacted-contract.mjs",
    "ddd-release-env-owner-input-packet-contract.mjs",
    "ddd-release-config-owner-input-reconciliation.mjs",
    "ddd-release-owner-input-receipt.mjs",
    "ddd-release-owner-input-receipt-contract.mjs",
    "release-env-readiness-gate.sh",
    "release-final-go-no-go-gate.sh",
    "DDD_RELEASE_ENV_READINESS_ENFORCE=1",
    "DDD_FINAL_GO_NO_GO_ENFORCE=1",
    "advisoryOnly",
    "advisoryFailureCount",
    "advisoryFailures",
    "cutoverAllowed",
    "releaseEnvFileCutoverSafe",
    "finalRecommendation",
    "cutoverDecisionSource",
    "Default preflight PASS means checks completed; it is not cutover approval.",
    "write_preflight_report NO_GO",
    "write_preflight_report PASS",
    "failed_step=\"manifest-provenance-preflight\"",
    "failed_step=\"artifact-path-leak\"",
    "failed_step=\"unblock-brief\"",
    "failed_step=\"env-owner-handoff-redacted\"",
    "failed_step=\"env-owner-input-packet\"",
    "failed_step=\"config-owner-input-reconciliation\"",
    "failed_step=\"owner-input-receipt\"",
    "failed_step=\"env-readiness\"",
    "failed_step=\"final-go-no-go\"",
  ]) {
    if (!source.includes(snippet)) addFailure(`release preflight gate script must include ${snippet}`);
  }
}

const syntax = spawnSync("bash", ["-n", bashPath(gatePath)], { cwd: repoRoot, encoding: "utf8" });
if (syntax.status !== 0) addFailure(`release preflight gate bash syntax must pass: ${syntax.stderr}`);

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-preflight-gate-contract-"));
try {
  const finalGoPacketPath = path.join(releaseDir, "release-final-go-no-go.json");
  const envReadinessPath = path.join(releaseDir, "release-env-readiness-redacted.json");
  const finalGoPacket = readOptionalJson(finalGoPacketPath);
  const envReadinessPacket = readOptionalJson(envReadinessPath);
  const expectedReleaseEnvFileCutoverSafe = finalGoPacket?.releaseEnvFileCutoverSafe === true;
  const unresolvedReadiness = Number(envReadinessPacket?.summary?.blockers || 0)
    + Number(envReadinessPacket?.summary?.placeholders || 0)
    + Number(envReadinessPacket?.summary?.missing || 0);
  const initialRefreshRun = spawnSync("node", ["scripts/ddd-release-readiness-summary.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  if (initialRefreshRun.status !== 0) {
    addFailure(`release artifact integrity refresh before default preflight must pass: ${initialRefreshRun.stderr || initialRefreshRun.stdout}`);
  }

  const defaultReportPath = path.join(tmpDir, "preflight-default.json");
  const defaultRun = runGate(defaultReportPath);
  if (defaultRun.status !== 0) addFailure(`default preflight must exit 0 for reporting mode: ${defaultRun.stderr || defaultRun.stdout}`);
  if (!defaultRun.stdout.includes("step=artifact-integrity")) addFailure("default preflight must run artifact-integrity step");
  if (!defaultRun.stdout.includes("step=manifest-provenance-preflight")) addFailure("default preflight must run manifest-provenance-preflight step");
  if (!defaultRun.stdout.includes("step=artifact-path-leak")) addFailure("default preflight must run artifact-path-leak step");
  if (!defaultRun.stdout.includes("step=unblock-brief")) addFailure("default preflight must run unblock-brief step");
  if (!defaultRun.stdout.includes("step=unblock-brief-contract")) addFailure("default preflight must run unblock-brief-contract step");
  if (!defaultRun.stdout.includes("step=env-owner-handoff-redacted")) addFailure("default preflight must run env-owner-handoff-redacted step");
  if (!defaultRun.stdout.includes("step=env-owner-input-packet")) addFailure("default preflight must run env-owner-input-packet step");
  if (!defaultRun.stdout.includes("step=config-owner-input-reconciliation")) addFailure("default preflight must run config-owner-input-reconciliation step");
  if (!defaultRun.stdout.includes("step=owner-input-receipt")) addFailure("default preflight must run owner-input-receipt step");
  if (!defaultRun.stdout.includes("step=env-readiness")) addFailure("default preflight must run env-readiness step");
  if (!defaultRun.stdout.includes("step=final-go-no-go")) addFailure("default preflight must run final-go-no-go step");
  if (!defaultRun.stdout.includes("complete enforce=false")) addFailure("default preflight must print enforce=false completion");
  const defaultReport = readJson(defaultReportPath);
  if (defaultReport.status !== "PASS") addFailure("default preflight report must be PASS in non-enforcing report mode");
  if (defaultReport.enforce !== false) addFailure("default preflight report must set enforce=false");
  if (defaultReport.advisoryOnly !== true) addFailure("default preflight report must mark advisoryOnly=true");
  const defaultAdvisoryFailures = defaultReport.steps.filter((step) => step.exitCode > 0);
  if (defaultReport.advisoryFailureCount !== defaultAdvisoryFailures.length) {
    addFailure("default preflight report advisoryFailureCount must match failed advisory steps");
  }
  if (!Array.isArray(defaultReport.advisoryFailures)) {
    addFailure("default preflight report must include advisoryFailures array");
  } else if (defaultReport.advisoryFailures.length !== defaultAdvisoryFailures.length) {
    addFailure("default preflight report advisoryFailures length must match failed advisory steps");
  } else {
    for (const [index, failure] of defaultReport.advisoryFailures.entries()) {
      const expected = defaultAdvisoryFailures[index];
      if (failure.name !== expected.name || failure.exitCode !== expected.exitCode || failure.command !== expected.command) {
        addFailure(`default preflight advisory failure ${index} must match failed step ${expected.name}`);
      }
    }
  }
  if (defaultReport.cutoverAllowed !== false) addFailure("default preflight report must not allow cutover while final gate is NO-GO");
  if (defaultReport.releaseEnvFileCutoverSafe !== expectedReleaseEnvFileCutoverSafe) addFailure(`default preflight report must carry releaseEnvFileCutoverSafe=${expectedReleaseEnvFileCutoverSafe}`);
  if (defaultReport.finalRecommendation !== "NO_GO_STRICT") addFailure("default preflight report must carry finalRecommendation=NO_GO_STRICT");
  if (typeof defaultReport.gateBlockers !== "number" || defaultReport.gateBlockers < 0) addFailure("default preflight report must carry non-negative gateBlockers");
  if (typeof defaultReport.stopReasonCount !== "number" || defaultReport.stopReasonCount <= 0) addFailure("default preflight report must carry stopReasonCount");
  if (defaultReport.cutoverDecisionSource !== "artifacts/ddd/release/release-final-go-no-go.json") {
    addFailure("default preflight report must point at final go/no-go packet as cutoverDecisionSource");
  }
  if (!String(defaultReport.advisoryNotice || "").includes("not cutover approval")) {
    addFailure("default preflight report must include advisory notice");
  }
  if (!String(defaultReport.advisoryNotice || "").includes(`advisoryFailureCount=${defaultAdvisoryFailures.length}`)) {
    addFailure("default preflight report advisory notice must include advisory failure count");
  }
  if (defaultReport.failedStep !== null) addFailure("default preflight report failedStep must be null");
  const expectedStepOrder = ["artifact-integrity", "manifest-provenance-preflight", "artifact-path-leak", "unblock-brief", "env-owner-handoff-redacted", "env-owner-input-packet", "config-owner-input-reconciliation", "owner-input-receipt", "env-readiness", "final-go-no-go"];
  if (JSON.stringify(defaultReport.steps.map((step) => step.name)) !== JSON.stringify(expectedStepOrder)) {
    addFailure("default preflight report must preserve expected step order");
  }
  if (defaultReport.steps.find((step) => step.name === "artifact-integrity")?.exitCode !== 0
    || defaultReport.steps.find((step) => step.name === "artifact-path-leak")?.exitCode !== 0
    || defaultReport.steps.find((step) => step.name === "unblock-brief")?.exitCode !== 0
    || defaultReport.steps.find((step) => step.name === "env-owner-handoff-redacted")?.exitCode !== 0
    || defaultReport.steps.find((step) => step.name === "env-owner-input-packet")?.exitCode !== 0
    || defaultReport.steps.find((step) => step.name === "config-owner-input-reconciliation")?.exitCode !== 0
    || defaultReport.steps.find((step) => step.name === "owner-input-receipt")?.exitCode !== 0
    || defaultReport.steps.find((step) => step.name === "env-readiness")?.exitCode !== 0
    || defaultReport.steps.find((step) => step.name === "final-go-no-go")?.exitCode !== 0) {
    addFailure("default preflight report must record zero exit codes for advisory gate wrappers except manifest provenance preflight");
  }

  const refreshRun = spawnSync("node", ["scripts/ddd-release-readiness-summary.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  if (refreshRun.status !== 0) {
    addFailure(`release artifact integrity refresh before enforced preflight must pass: ${refreshRun.stderr || refreshRun.stdout}`);
  }

  const enforcedReportPath = path.join(tmpDir, "preflight-enforced.json");
  const enforcedRun = runGate(enforcedReportPath, { DDD_RELEASE_PREFLIGHT_ENFORCE: "1" });
  const manifestPreflightExitCode = defaultReport.steps.find((step) => step.name === "manifest-provenance-preflight")?.exitCode ?? 0;
  if (manifestPreflightExitCode !== 0 && enforcedRun.status !== manifestPreflightExitCode) {
    addFailure(`enforced preflight must stop on manifest provenance preflight with exit ${manifestPreflightExitCode}, got ${enforcedRun.status}`);
  } else if (manifestPreflightExitCode === 0 && unresolvedReadiness > 0 && enforcedRun.status !== 21) {
    addFailure(`enforced preflight must stop on env readiness with exit 21, got ${enforcedRun.status}`);
  } else if (manifestPreflightExitCode === 0 && unresolvedReadiness === 0 && enforcedRun.status !== 10) {
    addFailure(`enforced preflight must fail at final-go-no-go with exit 10 when env is ready, got ${enforcedRun.status}`);
  }
  const enforcedReport = readJson(enforcedReportPath);
  if (enforcedReport.status !== "NO_GO") addFailure("enforced preflight report must be NO_GO");
  if (enforcedReport.enforce !== true) addFailure("enforced preflight report must set enforce=true");
  if (enforcedReport.advisoryOnly !== false) addFailure("enforced preflight report must set advisoryOnly=false");
  if (enforcedReport.advisoryFailureCount !== 0) addFailure("enforced preflight report must not count advisory failures");
  if (!Array.isArray(enforcedReport.advisoryFailures) || enforcedReport.advisoryFailures.length !== 0) {
    addFailure("enforced preflight report advisoryFailures must be empty");
  }
  if (enforcedReport.cutoverAllowed !== false) addFailure("enforced preflight report must not allow cutover while final gate is NO-GO");
  if (enforcedReport.releaseEnvFileCutoverSafe !== expectedReleaseEnvFileCutoverSafe) addFailure(`enforced preflight report must carry releaseEnvFileCutoverSafe=${expectedReleaseEnvFileCutoverSafe}`);
  if (enforcedReport.finalRecommendation !== "NO_GO_STRICT") addFailure("enforced preflight report must carry finalRecommendation=NO_GO_STRICT");
  if (manifestPreflightExitCode !== 0) {
    if (enforcedReport.failedStep !== "manifest-provenance-preflight") addFailure("enforced preflight must fail at manifest-provenance-preflight while manifest provenance is missing");
    if (enforcedReport.steps.find((step) => step.name === "artifact-integrity")?.exitCode !== 0) {
      addFailure("enforced preflight artifact-integrity step must pass before manifest provenance preflight");
    }
    if (enforcedReport.steps.find((step) => step.name === "manifest-provenance-preflight")?.exitCode !== manifestPreflightExitCode) {
      addFailure("enforced preflight manifest-provenance-preflight exit code must match default advisory diagnosis");
    }
    if (enforcedReport.steps.find((step) => step.name === "artifact-path-leak")?.exitCode !== 0) {
      addFailure("enforced preflight artifact-path-leak step must pass before manifest provenance failure is enforced");
    }
    if (enforcedReport.steps.find((step) => step.name === "env-readiness")?.exitCode !== -1) {
      addFailure("enforced preflight must not run env-readiness after manifest provenance failure");
    }
    if (enforcedReport.steps.find((step) => step.name === "env-owner-input-packet")?.exitCode !== -1) {
      addFailure("enforced preflight must not run env-owner-input-packet after manifest provenance failure");
    }
    if (enforcedReport.steps.find((step) => step.name === "owner-input-receipt")?.exitCode !== -1) {
      addFailure("enforced preflight must not run owner-input-receipt after manifest provenance failure");
    }
    if (enforcedReport.steps.find((step) => step.name === "final-go-no-go")?.exitCode !== -1) {
      addFailure("enforced preflight must not run final-go-no-go after manifest provenance failure");
    }
  } else if (unresolvedReadiness > 0) {
    if (enforcedReport.failedStep !== "env-readiness") addFailure("enforced preflight must fail at env-readiness while env placeholders remain");
    if (enforcedReport.steps.find((step) => step.name === "artifact-integrity")?.exitCode !== 0) {
      addFailure("enforced preflight artifact-integrity step must pass before env readiness");
    }
    if (enforcedReport.steps.find((step) => step.name === "artifact-path-leak")?.exitCode !== 0) {
      addFailure("enforced preflight artifact-path-leak step must pass before env readiness");
    }
    if (enforcedReport.steps.find((step) => step.name === "unblock-brief")?.exitCode !== 0) {
      addFailure("enforced preflight unblock-brief step must pass before env readiness");
    }
    if (enforcedReport.steps.find((step) => step.name === "env-owner-handoff-redacted")?.exitCode !== 0) {
      addFailure("enforced preflight env-owner-handoff-redacted step must pass before env readiness");
    }
    if (enforcedReport.steps.find((step) => step.name === "env-owner-input-packet")?.exitCode !== 0) {
      addFailure("enforced preflight env-owner-input-packet step must pass before env readiness");
    }
    if (enforcedReport.steps.find((step) => step.name === "owner-input-receipt")?.exitCode !== 0) {
      addFailure("enforced preflight owner-input-receipt step must pass before env readiness");
    }
    if (enforcedReport.steps.find((step) => step.name === "env-readiness")?.exitCode !== 21) {
      addFailure("enforced preflight env-readiness exit code must be 21");
    }
    if (enforcedReport.steps.find((step) => step.name === "final-go-no-go")?.exitCode !== -1) {
      addFailure("enforced preflight must not run final-go-no-go after env readiness failure");
    }
  } else {
    if (enforcedReport.failedStep !== "final-go-no-go") addFailure("enforced preflight must fail at final-go-no-go while env readiness is clean");
    if (enforcedReport.steps.find((step) => step.name === "final-go-no-go")?.exitCode !== 10) {
      addFailure("enforced preflight final-go-no-go exit code must be 10 when it blocks");
    }
  }
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}

if (failures.length > 0) {
  throw new Error(`release preflight gate contract failed: ${failures.join("; ")}`);
}

console.log("[ddd-release-preflight-gate-contract] ok");
