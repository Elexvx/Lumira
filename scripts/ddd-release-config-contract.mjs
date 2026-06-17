import { createHash } from "node:crypto";
import { evidenceValueIssue, isHttpsUrl, isLocalUrlLike } from "./ddd-release-evidence-utils.mjs";

const secretNamePattern = /(PASSWORD|SECRET|TOKEN|KEY|CREDENTIAL|PRIVATE|ACCESS_TOKEN)/i;
const urlNamePattern = /(URL|URI|BASE_URL|ADDRESSES)$/i;

export const releaseConfigGroups = [
  {
    name: "runtime",
    owner: "release-infra",
    requirements: [
      { name: "backend base url", keys: ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL"], nonLocal: true, https: true },
      { name: "frontend base url", keys: ["PLAYWRIGHT_BASE_URL", "FRONTEND_BASE_URL"], nonLocal: true, https: true },
      { name: "database url", keys: ["DB_URL", "SPRING_DATASOURCE_URL"], nonLocal: true },
      { name: "database username", keys: ["DB_USERNAME", "SPRING_DATASOURCE_USERNAME", "MYSQL_USER"] },
      { name: "database password", keys: ["DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD", "MYSQL_PASSWORD"], disallowValues: ["root", "password", ""], minLength: 16 },
      { name: "redis host", keys: ["REDIS_HOST", "SPRING_DATA_REDIS_HOST"], nonLocal: true },
      { name: "redis port", keys: ["REDIS_PORT", "SPRING_DATA_REDIS_PORT"], pattern: "^\\d{2,5}$", patternDescription: "a numeric TCP port" },
      { name: "redis password", keys: ["REDIS_PASSWORD", "SPRING_DATA_REDIS_PASSWORD"], required: false, minLength: 16 },
      { name: "jwt secret", keys: ["JWT_SECRET", "SAAS_SECURITY_JWT_SECRET"], minLength: 32 },
      { name: "field secret", keys: ["FIELD_SECRET", "SAAS_SECURITY_FIELD_SECRET"], minLength: 32 },
      { name: "cors origins", keys: ["CORS_ALLOWED_ORIGIN_PATTERNS", "SAAS_WEB_CORS_ALLOWED_ORIGIN_PATTERNS"] },
      { name: "trusted proxy mode", keys: ["TRUST_FORWARDED_HEADERS", "SAAS_WEB_TRUST_FORWARDED_HEADERS"], expectedValues: ["true"] },
    ],
  },
  {
    name: "owner-services",
    owner: "platform-owners",
    requirements: [
      { name: "system service", keys: ["SYSTEM_SERVICE_BASE_URL", "LUMIRA_SYSTEM_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "auth service", keys: ["AUTH_SERVICE_BASE_URL", "LUMIRA_AUTH_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "file service", keys: ["FILE_SERVICE_BASE_URL", "LUMIRA_FILE_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "message service", keys: ["MESSAGE_SERVICE_BASE_URL", "LUMIRA_MESSAGE_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "plugin service", keys: ["PLUGIN_SERVICE_BASE_URL", "LUMIRA_PLUGIN_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "localization service", keys: ["LOCALIZATION_SERVICE_BASE_URL", "LUMIRA_LOCALIZATION_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "payment service", keys: ["PAYMENT_SERVICE_BASE_URL", "LUMIRA_PAYMENT_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "ai service", keys: ["AI_SERVICE_BASE_URL", "LUMIRA_AI_SERVICE_BASE_URL", "LUMIRA_AI_BASE_URL"], nonLocal: true, https: true },
      { name: "job executor", keys: ["JOB_EXECUTOR_BASE_URL", "LUMIRA_JOB_EXECUTOR_BASE_URL"], nonLocal: true, https: true },
    ],
  },
  {
    name: "jobs-and-events",
    owner: "platform-events",
    requirements: [
      { name: "job internal token", keys: ["SAAS_JOB_INTERNAL_TOKEN", "DDD_JOB_INTERNAL_TOKEN", "LUMIRA_JOB_INTERNAL_TOKEN"], minLength: 32 },
      { name: "job backend url", keys: ["SAAS_JOB_BACKEND_BASE_URL", "LUMIRA_JOB_BACKEND_BASE_URL"], nonLocal: true, https: true },
      { name: "job message url", keys: ["SAAS_JOB_MESSAGE_SERVICE_BASE_URL", "LUMIRA_JOB_MESSAGE_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "job file url", keys: ["SAAS_JOB_FILE_SERVICE_BASE_URL", "LUMIRA_JOB_FILE_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "job payment url", keys: ["SAAS_JOB_PAYMENT_SERVICE_BASE_URL", "LUMIRA_JOB_PAYMENT_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "job plugin url", keys: ["SAAS_JOB_PLUGIN_SERVICE_BASE_URL", "LUMIRA_JOB_PLUGIN_SERVICE_BASE_URL"], nonLocal: true, https: true },
      { name: "xxl job admin", keys: ["XXL_JOB_ADMIN_ADDRESSES", "LUMIRA_XXL_JOB_ADMIN_ADDRESSES"], nonLocal: true, https: true },
      { name: "xxl job token", keys: ["XXL_JOB_ACCESS_TOKEN", "XXL_JOB_ADMIN_ACCESS_TOKEN", "LUMIRA_XXL_JOB_ACCESS_TOKEN"], minLength: 32 },
      { name: "outbox dispatcher", keys: ["SAAS_EVENT_OUTBOX_DISPATCHER", "LUMIRA_EVENT_OUTBOX_DISPATCHER"] },
      { name: "event stream key", keys: ["SAAS_EVENT_REDIS_STREAM_KEY", "LUMIRA_EVENT_REDIS_STREAM_KEY"] },
    ],
  },
  {
    name: "file-processing",
    owner: "file-owner",
    requirements: [
      { name: "upload storage root", keys: ["UPLOAD_STORAGE_ROOT", "LUMIRA_UPLOAD_STORAGE_ROOT"] },
      { name: "security scan mode", keys: ["LUMIRA_FILE_SECURITY_SCAN_MODE"] },
      { name: "ocr mode", keys: ["LUMIRA_FILE_OCR_MODE"] },
    ],
  },
  {
    name: "payment",
    owner: "payment-owner",
    requirements: [
      { name: "payment public url", keys: ["PAYMENT_PUBLIC_BASE_URL"], nonLocal: true, https: true },
      { name: "payment webhook secret", keys: ["DDD_PAYMENT_WEBHOOK_SECRET", "PAYMENT_WEBHOOK_SECRET", "LUMIRA_PAYMENT_WEBHOOK_SECRET"], required: false, minLength: 32 },
    ],
  },
  {
    name: "ai",
    owner: "ai-owner",
    requirements: [
      { name: "provider enabled", keys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED", "LUMIRA_AI_PROVIDER_ENABLED"], expectedValues: ["true"] },
      { name: "provider base url", keys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL", "LUMIRA_AI_PROVIDER_BASE_URL"], nonLocal: true, https: true },
      { name: "provider api key", keys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY", "LUMIRA_AI_PROVIDER_API_KEY"], minLength: 32 },
      { name: "chat model", keys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL", "LUMIRA_AI_CHAT_MODEL"] },
      { name: "embedding model", keys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL", "LUMIRA_AI_EMBEDDING_MODEL"] },
      { name: "owner internal token", keys: ["LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN", "LUMIRA_AI_OWNER_INTERNAL_TOKEN", "SAAS_JOB_INTERNAL_TOKEN"], minLength: 32 },
      { name: "iam owner enabled", keys: ["LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED"], expectedValues: ["true"] },
      { name: "iam owner url", keys: ["LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL", "LUMIRA_AI_OWNER_IAM_BASE_URL"], nonLocal: true, https: true },
      { name: "platform owner enabled", keys: ["LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED"], expectedValues: ["true"] },
      { name: "platform owner url", keys: ["LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL", "LUMIRA_AI_OWNER_PLATFORM_BASE_URL"], nonLocal: true, https: true },
      { name: "file owner enabled", keys: ["LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED"], expectedValues: ["true"] },
      { name: "file owner url", keys: ["LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL", "LUMIRA_AI_OWNER_FILE_BASE_URL"], nonLocal: true, https: true },
    ],
  },
];

export function evaluateReleaseConfig(env, { envFile = null } = {}) {
  const blockers = [];
  const warnings = [];
  const groups = releaseConfigGroups.map((group) => checkGroup(group.name, group.owner, group.requirements, env, blockers));

  if (!envFile) {
    warnings.push("DDD_RELEASE_ENV_FILE is not set; evidence was generated from current process environment only");
  }
  if (isPresent(env, ["ELEXVX_REDIS_CLEAR_ON_STARTUP"]) && valueOf(env, "ELEXVX_REDIS_CLEAR_ON_STARTUP") === "true") {
    blockers.push("runtime.redis clear-on-startup must be false for production-equivalent release evidence");
  }
  if (isPresent(env, ["ALLOW_UNSAFE_DEFAULT_ADMIN_LOGIN"]) && valueOf(env, "ALLOW_UNSAFE_DEFAULT_ADMIN_LOGIN") === "true") {
    blockers.push("runtime.unsafe default admin login must be disabled for release evidence");
  }

  return { groups, blockers, warnings };
}

export function validateReleaseConfigArtifact(artifact) {
  const issues = [];
  const groups = Array.isArray(artifact?.groups) ? artifact.groups : [];
  const coverageMatrix = Array.isArray(artifact?.coverageMatrix) ? artifact.coverageMatrix : [];
  const requiredCoverage = coverageMatrix.filter((entry) => entry.required);
  const blockers = Array.isArray(artifact?.blockers) ? artifact.blockers : [];
  const primaryBlockers = Array.isArray(artifact?.primaryBlockers) ? artifact.primaryBlockers : [];
  const placeholderDerivedConfigBlockers = Array.isArray(artifact?.placeholderDerivedConfigBlockers) ? artifact.placeholderDerivedConfigBlockers : [];
  const warnings = Array.isArray(artifact?.warnings) ? artifact.warnings : [];
  const blockerDetails = Array.isArray(artifact?.blockerDetails) ? artifact.blockerDetails : [];
  const expectedStatus = blockers.length === 0 ? "PASS" : "FAIL";

  const actualChecks = groups.reduce((sum, group) => sum + (Array.isArray(group.checks) ? group.checks.length : 0), 0);
  const expectedBlockersByGroup = countBy(blockerDetails, "group");
  const expectedBlockersByOwner = countBy(blockerDetails, "owner");
  const expectedPlaceholderDerivedConfigBlockers = blockerDetails
    .filter((detail) => detail?.blockedByPlaceholderKey === true)
    .map((detail) => detail.blocker);
  const expectedPrimaryBlockers = blockers.filter((blocker) => !expectedPlaceholderDerivedConfigBlockers.includes(blocker));
  const expectedChecks = expectedConfigChecks();
  const expectedCheckKeys = new Set(expectedChecks.map(configCheckKey));
  const coverageKeys = coverageMatrix.map(configCheckKey);
  const coverageKeyCounts = countValues(coverageKeys);
  const allowedInputKinds = new Set([
    "process-environment-only",
    "release-env-file",
    "missing-release-env-file",
    "generated-missing-template",
  ]);

  if (artifact?.status !== expectedStatus) {
    issues.push(`release config status must be ${expectedStatus}, got ${artifact?.status ?? "missing"}`);
  }
  if (!allowedInputKinds.has(artifact?.inputKind)) {
    issues.push(`release config inputKind must be one of ${[...allowedInputKinds].join(", ")}, got ${artifact?.inputKind ?? "missing"}`);
  }
  if ((artifact?.inputKind === "generated-missing-template") !== (artifact?.generatedMissingTemplate === true)) {
    issues.push("release config generatedMissingTemplate must match inputKind=generated-missing-template");
  }
  if (artifact?.generatedMissingTemplate === true && artifact?.envFileExists !== true) {
    issues.push("release config generated missing template input requires an existing envFile");
  }
  if ((artifact?.summary?.groups || 0) !== groups.length) {
    issues.push(`release config summary groups mismatch: declared=${artifact?.summary?.groups || 0}, actual=${groups.length}`);
  }
  if ((artifact?.summary?.checks || 0) !== actualChecks) {
    issues.push(`release config summary checks mismatch: declared=${artifact?.summary?.checks || 0}, actual=${actualChecks}`);
  }
  if ((artifact?.summary?.requiredChecks || 0) !== requiredCoverage.length) {
    issues.push(`release config summary requiredChecks mismatch: declared=${artifact?.summary?.requiredChecks || 0}, actual=${requiredCoverage.length}`);
  }
  if ((artifact?.summary?.runtimePresentRequiredChecks || 0) !== requiredCoverage.filter((entry) => entry.runtimePresent).length) {
    issues.push(`release config summary runtimePresentRequiredChecks mismatch: declared=${artifact?.summary?.runtimePresentRequiredChecks || 0}, actual=${requiredCoverage.filter((entry) => entry.runtimePresent).length}`);
  }
  if ((artifact?.summary?.envFileCoveredRequiredChecks || 0) !== requiredCoverage.filter((entry) => entry.envFileCovered).length) {
    issues.push(`release config summary envFileCoveredRequiredChecks mismatch: declared=${artifact?.summary?.envFileCoveredRequiredChecks || 0}, actual=${requiredCoverage.filter((entry) => entry.envFileCovered).length}`);
  }
  if ((artifact?.summary?.templateCoveredRequiredChecks || 0) !== requiredCoverage.filter((entry) => entry.templateCovered).length) {
    issues.push(`release config summary templateCoveredRequiredChecks mismatch: declared=${artifact?.summary?.templateCoveredRequiredChecks || 0}, actual=${requiredCoverage.filter((entry) => entry.templateCovered).length}`);
  }
  if ((artifact?.summary?.workflowCoveredRequiredChecks || 0) !== requiredCoverage.filter((entry) => entry.workflowCovered).length) {
    issues.push(`release config summary workflowCoveredRequiredChecks mismatch: declared=${artifact?.summary?.workflowCoveredRequiredChecks || 0}, actual=${requiredCoverage.filter((entry) => entry.workflowCovered).length}`);
  }
  if ((artifact?.summary?.blockers || 0) !== blockers.length) {
    issues.push(`release config summary blockers mismatch: declared=${artifact?.summary?.blockers || 0}, actual=${blockers.length}`);
  }
  if ((artifact?.summary?.primaryBlockers || 0) !== primaryBlockers.length) {
    issues.push(`release config summary primaryBlockers mismatch: declared=${artifact?.summary?.primaryBlockers || 0}, actual=${primaryBlockers.length}`);
  }
  if ((artifact?.summary?.releaseConfigBlockersFromPlaceholders || 0) !== placeholderDerivedConfigBlockers.length) {
    issues.push(`release config summary releaseConfigBlockersFromPlaceholders mismatch: declared=${artifact?.summary?.releaseConfigBlockersFromPlaceholders || 0}, actual=${placeholderDerivedConfigBlockers.length}`);
  }
  if ((artifact?.summary?.releaseConfigBlockersAfterPlaceholders || 0) !== Math.max(0, blockers.length - placeholderDerivedConfigBlockers.length)) {
    issues.push(`release config summary releaseConfigBlockersAfterPlaceholders mismatch: declared=${artifact?.summary?.releaseConfigBlockersAfterPlaceholders || 0}, actual=${Math.max(0, blockers.length - placeholderDerivedConfigBlockers.length)}`);
  }
  if ((artifact?.summary?.warnings || 0) !== warnings.length) {
    issues.push(`release config summary warnings mismatch: declared=${artifact?.summary?.warnings || 0}, actual=${warnings.length}`);
  }
  if (!arraysEqual(primaryBlockers, expectedPrimaryBlockers)) {
    issues.push("release config primaryBlockers mismatch");
  }
  if (!arraysEqual(placeholderDerivedConfigBlockers, expectedPlaceholderDerivedConfigBlockers)) {
    issues.push("release config placeholderDerivedConfigBlockers mismatch");
  }
  if (blockerDetails.length !== blockers.length) {
    issues.push(`release config blockerDetails mismatch: details=${blockerDetails.length}, blockers=${blockers.length}`);
  }
  const coverageByKey = new Map(coverageMatrix.map((entry) => [configCheckKey(entry), entry]));
  for (const [index, detail] of blockerDetails.entries()) {
    if (detail?.blocker !== blockers[index]) {
      issues.push(`release config blockerDetails[${index}].blocker mismatch: declared=${detail?.blocker ?? "missing"}, actual=${blockers[index] ?? "missing"}`);
    }
    for (const field of ["group", "owner", "check", "reason"]) {
      if (typeof detail?.[field] !== "string" || detail[field].trim() === "") {
        issues.push(`release config blockerDetails[${index}].${field} must be a non-empty string`);
      }
    }
    if (!Array.isArray(detail?.envKeys)) {
      issues.push(`release config blockerDetails[${index}].envKeys must be an array`);
    }
    if (detail?.required !== null && typeof detail?.required !== "boolean") {
      issues.push(`release config blockerDetails[${index}].required must be boolean or null`);
    }
    if (typeof detail?.blockedByPlaceholderKey !== "boolean") {
      issues.push(`release config blockerDetails[${index}].blockedByPlaceholderKey must be boolean`);
    }
    const coverage = coverageByKey.get(configCheckKey(detail));
    if (coverage) {
      if (detail.owner !== coverage.owner) {
        issues.push(`release config blockerDetails[${index}].owner mismatch for ${configCheckKey(detail)}: declared=${detail.owner || "missing"}, actual=${coverage.owner || "missing"}`);
      }
      if (detail.required !== coverage.required) {
        issues.push(`release config blockerDetails[${index}].required mismatch for ${configCheckKey(detail)}: declared=${detail.required}, actual=${coverage.required}`);
      }
      if (!arraysEqual(detail.envKeys || [], coverage.keys || [])) {
        issues.push(`release config blockerDetails[${index}].envKeys mismatch for ${configCheckKey(detail)}`);
      }
    }
  }
  if (JSON.stringify(artifact?.blockersByGroup || {}) !== JSON.stringify(expectedBlockersByGroup)) {
    issues.push("release config blockersByGroup mismatch");
  }
  if (JSON.stringify(artifact?.blockersByOwner || {}) !== JSON.stringify(expectedBlockersByOwner)) {
    issues.push("release config blockersByOwner mismatch");
  }
  for (const expected of expectedChecks) {
    if (!coverageKeyCounts.has(configCheckKey(expected))) {
      issues.push(`release config coverageMatrix missing ${expected.group}.${expected.check}`);
    }
  }
  for (const [key, count] of coverageKeyCounts.entries()) {
    if (count > 1) {
      issues.push(`release config coverageMatrix duplicate ${key}`);
    }
    if (!expectedCheckKeys.has(key)) {
      issues.push(`release config coverageMatrix unknown ${key}`);
    }
  }
  for (const entry of coverageMatrix) {
    if (!Array.isArray(entry?.keys) || entry.keys.length === 0) {
      issues.push(`release config coverageMatrix ${configCheckKey(entry)} keys must be non-empty`);
    }
  }
  return issues;
}

function expectedConfigChecks() {
  return releaseConfigGroups.flatMap((group) => group.requirements.map((requirement) => ({
    group: group.name,
    owner: group.owner,
    check: requirement.name,
    required: requirement.required !== false,
    keys: Array.isArray(requirement.keys) ? requirement.keys : [requirement.key],
  })));
}

function configCheckKey(entry) {
  return `${entry?.group || "missing"}.${entry?.check || "missing"}`;
}

function countBy(items, key) {
  const counts = {};
  for (const item of items) {
    const value = item[key] || "unknown";
    counts[value] = (counts[value] || 0) + 1;
  }
  return Object.fromEntries(Object.entries(counts).sort(([left], [right]) => left.localeCompare(right)));
}

function countValues(items) {
  const counts = new Map();
  for (const item of items) {
    counts.set(item, (counts.get(item) || 0) + 1);
  }
  return counts;
}

function arraysEqual(left, right) {
  return Array.isArray(left)
    && Array.isArray(right)
    && left.length === right.length
    && left.every((value, index) => value === right[index]);
}

function valueOf(env, key) {
  const value = env[key];
  return typeof value === "string" ? value.trim() : "";
}

function firstValue(env, keys) {
  for (const key of keys) {
    const value = valueOf(env, key);
    if (value) {
      return { key, value };
    }
  }
  return { key: keys[0], value: "" };
}

function isPresent(env, keys) {
  return Boolean(firstValue(env, keys).value);
}

function checkGroup(name, owner, requirements, env, blockers) {
  const checks = [];
  for (const requirement of requirements) {
    const keys = Array.isArray(requirement.keys) ? requirement.keys : [requirement.key];
    const found = firstValue(env, keys);
    const present = Boolean(found.value);
    const local = present && requirement.nonLocal === true && isLocalUrlLike(found.value);
    const unsafeDefault = present && Array.isArray(requirement.disallowValues)
      && requirement.disallowValues.includes(found.value);
    const unexpectedValue = present && Array.isArray(requirement.expectedValues)
      && !requirement.expectedValues.includes(found.value);
    const evidenceIssue = present ? evidenceValueIssue(found.value) : null;
    const placeholderValue = evidenceIssue === "must not contain placeholder text";
    const tooShort = present && Number.isFinite(requirement.minLength) && found.value.length < requirement.minLength;
    const requiresHttps = present && requirement.https === true && !isHttpsUrl(found.value);
    const patternMismatch = present && requirement.pattern && !new RegExp(requirement.pattern).test(found.value);
    const check = {
      name: requirement.name,
      keys,
      matchedKey: present ? found.key : null,
      required: requirement.required !== false,
      nonLocal: requirement.nonLocal === true,
      https: requirement.https === true,
      minLength: requirement.minLength || null,
      expectedValues: requirement.expectedValues || null,
      patternDescription: requirement.patternDescription || null,
      present,
      local,
      tooShort,
      requiresHttps,
      patternMismatch,
      safeValue: present ? redact(found.key, found.value) : null,
    };
    checks.push(check);
    if (requirement.required !== false && !present) {
      blockers.push(`${name}.${requirement.name}: missing ${keys.join(" or ")}`);
    }
    if (local) {
      blockers.push(`${name}.${requirement.name}: must not point at localhost for production-equivalent evidence`);
    }
    if (unsafeDefault) {
      blockers.push(`${name}.${requirement.name}: unsafe default value is not allowed`);
    }
    if (unexpectedValue) {
      blockers.push(`${name}.${requirement.name}: expected ${requirement.expectedValues.join(" or ")}`);
    }
    if (placeholderValue) {
      blockers.push(`${name}.${requirement.name}: placeholder value is not allowed`);
    }
    if (tooShort) {
      blockers.push(`${name}.${requirement.name}: must be at least ${requirement.minLength} characters`);
    }
    if (requiresHttps) {
      blockers.push(`${name}.${requirement.name}: must use HTTPS for production-equivalent evidence`);
    }
    if (patternMismatch) {
      blockers.push(`${name}.${requirement.name}: must be ${requirement.patternDescription || "a valid value"}`);
    }
  }
  return { name, owner, checks };
}

function redact(key, value) {
  if (!value) {
    return null;
  }
  if (secretNamePattern.test(key)) {
    return {
      present: true,
      length: value.length,
      sha256Prefix: createHash("sha256").update(value).digest("hex").slice(0, 12),
    };
  }
  if (urlNamePattern.test(key)) {
    return redactUrl(value);
  }
  return value.length > 80 ? `${value.slice(0, 77)}...` : value;
}

function redactUrl(value) {
  try {
    const url = new URL(value);
    return {
      present: true,
      protocol: url.protocol.replace(":", ""),
      host: url.host,
      pathname: url.pathname,
      local: isLocalUrlLike(value),
    };
  } catch {
    return {
      present: true,
      rawLength: value.length,
      local: isLocalUrlLike(value),
    };
  }
}
