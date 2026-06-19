#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD final go/no-go gate.
# Generated at: 2026-06-19T18:09:18.921Z
# Default mode prints the decision. Set DDD_FINAL_GO_NO_GO_ENFORCE=1 to fail on NO-GO.
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
if [[ -z "${LUMIRA_REPO_ROOT:-}" ]]; then
  if [[ -f "bin/ddd-release-readiness-summary.mjs" ]]; then
    LUMIRA_REPO_ROOT=$(pwd)
  else
    LUMIRA_REPO_ROOT=$(cd "${SCRIPT_DIR}/../../.." && pwd)
  fi
fi
export LUMIRA_REPO_ROOT
cd "${LUMIRA_REPO_ROOT}"

DDD_FINAL_GO_NO_GO_PACKET="${DDD_FINAL_GO_NO_GO_PACKET:-artifacts/ddd/release/release-final-go-no-go.json}"
DDD_FINAL_GO_NO_GO_ENFORCE="${DDD_FINAL_GO_NO_GO_ENFORCE:-}"
DDD_STAGING_FINAL_REVIEW_ENFORCE="${DDD_STAGING_FINAL_REVIEW_ENFORCE:-${DDD_FINAL_GO_NO_GO_ENFORCE}}"
DDD_NODE_BIN="${DDD_NODE_BIN:-node}"
if [[ ! -f "${DDD_FINAL_GO_NO_GO_PACKET}" ]]; then
  echo "Final go/no-go packet does not exist: ${DDD_FINAL_GO_NO_GO_PACKET}" >&2
  echo "Run: node bin/ddd-release-readiness-summary.mjs" >&2
  exit 2
fi
set +e
"${DDD_NODE_BIN}" --input-type=module - "${DDD_FINAL_GO_NO_GO_PACKET}" <<'NODE'
import fs from 'node:fs';
const packetPath = process.argv[2];
const packet = JSON.parse(fs.readFileSync(packetPath, 'utf8'));
const stopReasons = Array.isArray(packet.currentStopReasons) ? packet.currentStopReasons : [];
const nextCommands = Array.isArray(packet.nextCommands) ? packet.nextCommands : [];
const stopOwners = Array.isArray(packet.ciSummary?.stopOwners) ? packet.ciSummary.stopOwners : [];
const blockedArtifacts = Array.isArray(packet.ciSummary?.blockedArtifactPaths) ? packet.ciSummary.blockedArtifactPaths : [];
const blockedContentHints = Array.isArray(packet.ciSummary?.blockedContentHints) ? packet.ciSummary.blockedContentHints : [];
const exitCodeMap = packet.ciSummary?.exitCodeMap || {};
const finalNoGoExitCode = Number(exitCodeMap.finalNoGo ?? packet.ciSummary?.nonGoExitCode ?? 10);
const finalPacketInvalidExitCode = Number(exitCodeMap.finalPacketInvalid ?? 11);
const releaseEnvUnresolvedExitCode = Number(exitCodeMap.releaseEnvUnresolved ?? 21);
const releaseEnvInvalidPacketExitCode = Number(exitCodeMap.releaseEnvInvalidPacket ?? 22);
const invalidPacketReasons = [];
if (!['GO_STRICT', 'NO_GO_STRICT'].includes(packet.finalRecommendation || packet.recommendation || '')) invalidPacketReasons.push('finalRecommendation');
if (typeof packet.cutoverAllowed !== 'boolean') invalidPacketReasons.push('cutoverAllowed');
if (packet.noAutoWaivers !== true) invalidPacketReasons.push('noAutoWaivers');
if (!packet.gate || typeof packet.gate.blockers !== 'number') invalidPacketReasons.push('gate.blockers');
if (!Array.isArray(packet.currentStopReasons)) invalidPacketReasons.push('currentStopReasons');
if (!Array.isArray(packet.nextCommands)) invalidPacketReasons.push('nextCommands');
if (!packet.ciSummary || typeof packet.ciSummary !== 'object') invalidPacketReasons.push('ciSummary');
if (!packet.ciSummary?.releaseEnvReadiness || typeof packet.ciSummary.releaseEnvReadiness !== 'object') invalidPacketReasons.push('ciSummary.releaseEnvReadiness');
if (!packet.ciSummary?.configOwnerInputReconciliation || typeof packet.ciSummary.configOwnerInputReconciliation !== 'object') invalidPacketReasons.push('ciSummary.configOwnerInputReconciliation');
if (!packet.ciSummary?.ownerInputReceipt || typeof packet.ciSummary.ownerInputReceipt !== 'object') invalidPacketReasons.push('ciSummary.ownerInputReceipt');
if (invalidPacketReasons.length > 0) {
  console.error(`[ddd-final-go-no-go][invalid-packet] ${invalidPacketReasons.join(',')}`);
  process.exit(finalPacketInvalidExitCode);
}
const releaseEnvReadiness = packet.ciSummary?.releaseEnvReadiness || {};
const configOwnerInputReconciliation = packet.ciSummary?.configOwnerInputReconciliation || {};
const ownerInputReceipt = packet.ciSummary?.ownerInputReceipt || {};
const orchestratorPreflight = packet.ciSummary?.orchestratorPreflight || {};
const releaseEnvFile = packet.safetySignals?.releaseEnvFile || {};
const releaseEnvFileCutoverSafe = releaseEnvFile.ready === true
  && releaseEnvFile.status === 'PASS'
  && releaseEnvFile.inputKind === 'release-env-file'
  && releaseEnvFile.envFilePresent === true
  && releaseEnvFile.generatedMissingTemplate !== true
  && releaseEnvFile.securityChecked === true
  && releaseEnvFile.permissionSafe === true
  && releaseEnvFile.permissionCheckSkipped !== true
  && releaseEnvFile.modeOctal === (releaseEnvFile.requiredMode || '600')
  && (releaseEnvFile.requiredMode || '600') === '600';
if (typeof packet.releaseEnvFileCutoverSafe !== 'boolean') invalidPacketReasons.push('releaseEnvFileCutoverSafe');
if (typeof packet.releaseEnvFileCutoverSafe === 'boolean' && packet.releaseEnvFileCutoverSafe !== releaseEnvFileCutoverSafe) invalidPacketReasons.push('releaseEnvFileCutoverSafeMismatch');
if (configOwnerInputReconciliation.status !== 'PASS') invalidPacketReasons.push('configOwnerInputReconciliation.status');
if (Number(configOwnerInputReconciliation.unmappedConfigPlaceholderKeys ?? 0) !== 0) invalidPacketReasons.push('configOwnerInputUnmapped');
if (Number(configOwnerInputReconciliation.mappedConfigPlaceholderKeys ?? -1) !== Number(configOwnerInputReconciliation.uniqueConfigPlaceholderKeys ?? -2)) invalidPacketReasons.push('configOwnerInputMappedCount');
if (!['PASS', 'PENDING_OWNER_INPUT'].includes(ownerInputReceipt.status || '')) invalidPacketReasons.push('ownerInputReceipt.status');
if (ownerInputReceipt.status === 'PASS' && ownerInputReceipt.cutoverReady !== true) invalidPacketReasons.push('ownerInputReceipt.cutoverReady');
if (ownerInputReceipt.status === 'PENDING_OWNER_INPUT' && ownerInputReceipt.cutoverReady !== false) invalidPacketReasons.push('ownerInputReceipt.pendingCutoverReady');
if (invalidPacketReasons.length > 0) {
  console.error(`[ddd-final-go-no-go][invalid-packet] ${invalidPacketReasons.join(',')}`);
  process.exit(finalPacketInvalidExitCode);
}
const finalRecommendation = packet.finalRecommendation || packet.recommendation || 'UNKNOWN';
if (finalRecommendation === 'GO_STRICT' && stopReasons.length > 0) invalidPacketReasons.push('goWithStopReasons');
if (packet.cutoverAllowed === true && stopReasons.length > 0) invalidPacketReasons.push('cutoverAllowedWithStopReasons');
if (packet.cutoverAllowed === true && Number(packet.gate?.blockers ?? 0) > 0) invalidPacketReasons.push('cutoverAllowedWithGateBlockers');
if (invalidPacketReasons.length > 0) {
  console.error(`[ddd-final-go-no-go][invalid-packet] ${invalidPacketReasons.join(',')}`);
  process.exit(finalPacketInvalidExitCode);
}
console.log(`[ddd-final-go-no-go] recommendation=${packet.recommendation} finalRecommendation=${finalRecommendation} cutoverAllowed=${packet.cutoverAllowed} gateBlockers=${packet.gate?.blockers ?? 'unknown'} stopReasons=${stopReasons.length}`);
console.log(`[ddd-final-go-no-go] ci stopOwners=${stopOwners.join(',') || 'none'} blockedArtifacts=${blockedArtifacts.length} blockedContentHints=${blockedContentHints.length} nonGoExitCode=${finalNoGoExitCode}`);
console.log(`[ddd-final-go-no-go] exitCodes finalNoGo=${finalNoGoExitCode} finalPacketInvalid=${finalPacketInvalidExitCode} envUnresolved=${releaseEnvUnresolvedExitCode} envInvalidPacket=${releaseEnvInvalidPacketExitCode}`);
console.log(`[ddd-final-go-no-go] configOwnerInputReconciliation status=${configOwnerInputReconciliation.status || 'missing'} placeholders=${configOwnerInputReconciliation.configPlaceholderBlockers ?? 'unknown'} uniqueKeys=${configOwnerInputReconciliation.uniqueConfigPlaceholderKeys ?? 'unknown'} mapped=${configOwnerInputReconciliation.mappedConfigPlaceholderKeys ?? 'unknown'} unmapped=${configOwnerInputReconciliation.unmappedConfigPlaceholderKeys ?? 'unknown'} ownerInputs=${configOwnerInputReconciliation.ownerInputKeys ?? 'unknown'}`);
console.log(`[ddd-final-go-no-go] ownerInputReceipt status=${ownerInputReceipt.status || 'missing'} cutoverReady=${ownerInputReceipt.cutoverReady === true} inputs=${ownerInputReceipt.requiredOwnerInputs ?? 'unknown'} owners=${ownerInputReceipt.ownerCount ?? 'unknown'} pendingOwners=${ownerInputReceipt.pendingOwnerCount ?? 'unknown'} missingCriteria=${Array.isArray(ownerInputReceipt.missingCriteria) ? ownerInputReceipt.missingCriteria.join(',') : 'unknown'} artifact=${ownerInputReceipt.artifact || 'missing'}`);
console.log(`[ddd-final-go-no-go] releaseEnvReadiness blockers=${releaseEnvReadiness.blockers ?? 'unknown'} placeholders=${releaseEnvReadiness.placeholders ?? 'unknown'} missing=${releaseEnvReadiness.missing ?? 'unknown'} filledRedacted=${releaseEnvReadiness.filledRedacted ?? 'unknown'} owners=${releaseEnvReadiness.ownerCount ?? 'unknown'} handoff=${releaseEnvReadiness.ownerHandoffDir || 'missing'} handoffCsv=${releaseEnvReadiness.ownerHandoffCsv || 'missing'}`);
const releaseEnvOwnerBlockers = Array.isArray(releaseEnvReadiness.ownerBlockerSummary) ? releaseEnvReadiness.ownerBlockerSummary : [];
if (releaseEnvOwnerBlockers.length > 0) console.log(`[ddd-final-go-no-go] releaseEnvOwnerBlockers ${releaseEnvOwnerBlockers.map((owner) => `${owner.owner}:${owner.blockers}`).join(',')}`);
const orchestratorOwnerActions = Array.isArray(orchestratorPreflight.ownerActionSummary) ? orchestratorPreflight.ownerActionSummary : [];
console.log(`[ddd-final-go-no-go] orchestratorPreflight mode=${orchestratorPreflight.mode || 'missing'} status=${orchestratorPreflight.status || 'missing'} blockers=${orchestratorPreflight.blockers ?? 'unknown'} warnings=${orchestratorPreflight.warnings ?? 'unknown'} selectedSteps=${orchestratorPreflight.selectedStepCount ?? 'unknown'} executedResults=${orchestratorPreflight.executedResultCount ?? 'unknown'} artifact=${orchestratorPreflight.artifact || 'missing'}`);
if (orchestratorOwnerActions.length > 0) console.log(`[ddd-final-go-no-go] orchestratorPreflightOwners ${orchestratorOwnerActions.map((owner) => `${owner.owner}:${Array.isArray(owner.actions) ? owner.actions.length : 0}`).join(',')}`);
console.log(`[ddd-final-go-no-go] safety releaseEnvFile ready=${releaseEnvFile.ready === true} status=${releaseEnvFile.status || 'missing'} inputKind=${releaseEnvFile.inputKind || 'missing'} envFilePresent=${releaseEnvFile.envFilePresent === true} securityChecked=${releaseEnvFile.securityChecked === true} permissionSafe=${releaseEnvFile.permissionSafe === true} mode=${releaseEnvFile.modeOctal || 'missing'} requiredMode=${releaseEnvFile.requiredMode || '600'}`);
if (packet.ciSummary?.firstEnvOwnerAction) console.log(`[ddd-final-go-no-go] first env owner action: owner=${packet.ciSummary.firstEnvOwnerAction.owner} blockers=${packet.ciSummary.firstEnvOwnerAction.blockers ?? 0} placeholders=${packet.ciSummary.firstEnvOwnerAction.placeholders ?? 0} missing=${packet.ciSummary.firstEnvOwnerAction.missing ?? 0} handoff=${packet.ciSummary.firstEnvOwnerAction.handoffPath || 'missing'} next=${packet.ciSummary.firstEnvOwnerAction.nextCommand || 'none'}`);
if (packet.ciSummary?.firstOrchestratorPreflightAction) console.log(`[ddd-final-go-no-go] first orchestrator preflight action: owner=${packet.ciSummary.firstOrchestratorPreflightAction.owner} check=${packet.ciSummary.firstOrchestratorPreflightAction.checkId || packet.ciSummary.firstOrchestratorPreflightAction.id || 'missing'} reason=${packet.ciSummary.firstOrchestratorPreflightAction.reason || 'missing'} envKeys=${(packet.ciSummary.firstOrchestratorPreflightAction.envKeys || []).join(',') || 'none'} command=${packet.ciSummary.firstOrchestratorPreflightAction.command || 'none'}`);
if (packet.ciSummary?.firstNextCommand) console.log(`[ddd-final-go-no-go] first next command: ${packet.ciSummary.firstNextCommand}`);
if (packet.ciSummary?.firstOwnerAction) console.log(`[ddd-final-go-no-go] first owner action: owner=${packet.ciSummary.firstOwnerAction.owner} command=${packet.ciSummary.firstOwnerAction.displayCommand || packet.ciSummary.firstOwnerAction.command || 'none'} reason=${packet.ciSummary.firstOwnerAction.displayReason || packet.ciSummary.firstOwnerAction.reason || 'none'}`);
if (packet.ciSummary?.firstOwnerAction?.nextAction) console.log(`[ddd-final-go-no-go] first owner next action: ${packet.ciSummary.firstOwnerAction.displayNextAction || packet.ciSummary.firstOwnerAction.nextAction}`);
if (stopReasons.length > 0) {
  console.log('[ddd-final-go-no-go] stop reasons:');
  for (const reason of stopReasons) console.log(`- ${reason}`);
}
if (nextCommands.length > 0) {
  console.log('[ddd-final-go-no-go] next commands:');
  for (const command of nextCommands) console.log(`- ${command}`);
}
if (packet.cutoverAllowed === true && releaseEnvFile.ready !== true) {
  console.error('[ddd-final-go-no-go] releaseEnvFile.ready must be true before cutoverAllowed can be true');
  process.exit(4);
}
if (packet.cutoverAllowed === true && releaseEnvFileCutoverSafe !== true) {
  console.error('[ddd-final-go-no-go] releaseEnvFile must be PASS release-env-file with checked chmod 600 permissions before cutoverAllowed can be true');
  process.exit(4);
}
if (packet.cutoverAllowed !== true) {
  process.exitCode = finalNoGoExitCode;
}
NODE
DDD_FINAL_GO_NO_GO_STATUS=$?
set -e
if [[ "${DDD_FINAL_GO_NO_GO_STATUS}" == "0" ]]; then
  if [[ "${DDD_STAGING_FINAL_REVIEW_ENFORCE}" == "1" || "${DDD_STAGING_FINAL_REVIEW_ENFORCE}" == "true" ]]; then
    set +e
    "${DDD_NODE_BIN}" bin/ddd-staging-execution-checklist.mjs --final-review-enforce
    DDD_STAGING_FINAL_REVIEW_STATUS=$?
    set -e
    if [[ "${DDD_STAGING_FINAL_REVIEW_STATUS}" != "0" ]]; then
      echo "[ddd-final-go-no-go][staging-final-review-blocked] cutover blocked; run node bin/ddd-staging-execution-checklist.mjs --final-review" >&2
      if [[ "${DDD_FINAL_GO_NO_GO_ENFORCE}" == "1" || "${DDD_FINAL_GO_NO_GO_ENFORCE}" == "true" ]]; then
        exit 10
      fi
      exit 0
    fi
  fi
  echo "[ddd-final-go-no-go][go] cutover allowed"
  exit 0
fi
if [[ "${DDD_FINAL_GO_NO_GO_STATUS}" == "10" ]]; then
  echo "[ddd-final-go-no-go][no-go] cutover blocked; see ${DDD_FINAL_GO_NO_GO_PACKET} and artifacts/ddd/release/release-final-go-no-go.md" >&2
  if [[ "${DDD_FINAL_GO_NO_GO_ENFORCE}" == "1" || "${DDD_FINAL_GO_NO_GO_ENFORCE}" == "true" ]]; then
    exit 10
  fi
  exit 0
fi
exit "${DDD_FINAL_GO_NO_GO_STATUS}"

# Generated packet is currently NO-GO with 22 stop reasons.
