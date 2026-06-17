import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const homeDir = process.env.HOME || "";

export const requiredBackendTestClasses = [
  "com.lumira.saas.architecture.DddArchitectureBoundaryTest",
  "com.lumira.saas.modules.architecture.interfaces.rest.DddArchitectureCatalogControllerTest",
  "com.lumira.saas.modules.iam.domain.model.IamDomainModelsTest",
  "com.lumira.saas.modules.iam.controller.IamV2ControllerTest",
  "com.lumira.saas.modules.iam.controller.IamReadinessV2ControllerTest",
  "com.lumira.saas.modules.platform.domain.model.PlatformDomainModelsTest",
  "com.lumira.saas.modules.platform.controller.PlatformV2ControllerTest",
  "com.lumira.saas.modules.platform.controller.PlatformReadinessV2ControllerTest",
  "com.lumira.auth.domain.model.AuthDomainModelsTest",
  "com.lumira.auth.controller.AuthV2ControllerTest",
  "com.lumira.auth.controller.AuthReadinessV2ControllerTest",
  "com.lumira.auth.service.AuthSessionStoreTest",
  "com.lumira.message.domain.model.MessageDomainModelsTest",
  "com.lumira.message.controller.MessageV2ControllerTest",
  "com.lumira.message.controller.MessageReadinessV2ControllerTest",
  "com.lumira.file.domain.model.FileDomainModelsTest",
  "com.lumira.file.controller.FileV2ControllerTest",
  "com.lumira.file.controller.FileReadinessV2ControllerTest",
  "com.lumira.file.processing.FileProcessingTaskServiceTest",
  "com.lumira.saas.modules.plugin.domain.model.PluginDomainModelsTest",
  "com.lumira.saas.modules.plugin.controller.PluginV2ControllerTest",
  "com.lumira.saas.modules.plugin.controller.PluginReadinessV2ControllerTest",
  "com.lumira.saas.modules.plugin.PluginArchitectureContractTest",
  "com.lumira.saas.modules.localization.domain.model.LocalizationDomainModelsTest",
  "com.lumira.saas.modules.localization.controller.LocalizationV2ControllerTest",
  "com.lumira.saas.modules.localization.controller.LocalizationReadinessV2ControllerTest",
  "com.lumira.payment.domain.model.PaymentDomainModelsTest",
  "com.lumira.payment.controller.PaymentV2ControllerTest",
  "com.lumira.payment.controller.PaymentReadinessV2ControllerTest",
  "com.lumira.payment.service.PaymentWebhookServiceTest",
  "com.lumira.saas.modules.ai.domain.model.AiAssistantDomainModelsTest",
  "com.lumira.ai.controller.AiV2ControllerTest",
  "com.lumira.ai.controller.AiReadinessV2ControllerTest",
  "com.lumira.ai.integration.RemoteAiOwnerToolGatewayTest",
  "com.lumira.ai.provider.DefaultAiProviderRuntimeTest",
  "com.lumira.job.domain.model.JobDomainModelsTest",
  "com.lumira.job.JobReadinessV2ControllerTest",
  "com.lumira.job.XxlJobExecutorConfigTest",
];

export const requiredBackendModules = [
  {
    module: "services/lumira-server",
    entrypoint: "src/main/java/com/lumira/server/LumiraServerApplication.java",
    deployable: true,
  },
  {
    module: "services/auth-service",
    entrypoint: "src/main/java/com/lumira/auth/AuthServiceApplication.java",
    deployable: true,
  },
  {
    module: "services/message-service",
    entrypoint: "src/main/java/com/lumira/message/MessageServiceApplication.java",
    deployable: true,
  },
  {
    module: "services/file-service",
    entrypoint: "src/main/java/com/lumira/file/FileServiceApplication.java",
    deployable: true,
  },
  {
    module: "services/plugin-service",
    entrypoint: "src/main/java/com/lumira/plugin/PluginServiceApplication.java",
    deployable: true,
  },
  {
    module: "services/localization-service",
    entrypoint: "src/main/java/com/lumira/localization/LocalizationServiceApplication.java",
    deployable: true,
  },
  {
    module: "services/payment-service",
    entrypoint: "src/main/java/com/lumira/payment/PaymentServiceApplication.java",
    deployable: true,
  },
  {
    module: "services/ai-service",
    entrypoint: "src/main/java/com/lumira/ai/AiServiceApplication.java",
    deployable: true,
  },
  {
    module: "services/job-executor",
    entrypoint: "src/main/java/com/lumira/job/JobExecutorApplication.java",
    deployable: true,
  },
  {
    module: "services/system-service",
    entrypoint: null,
    deployable: false,
  },
];

export function validateBackendTestArtifact(artifact) {
  const issues = [];
  if (artifact?.status !== "PASS") {
    issues.push(`status=${artifact?.status ?? "missing"}`);
  }
  validateNoLocalPath("backend test reportRoot", artifact?.reportRoot, issues);
  const suites = Array.isArray(artifact?.suites) ? artifact.suites : [];
  const actualTests = sum(suites, "tests");
  const actualFailures = sum(suites, "failures");
  const actualErrors = sum(suites, "errors");
  const actualSkipped = sum(suites, "skipped");
  if (suites.length === 0) {
    issues.push("no surefire XML suites recorded");
  }
  const byName = new Map(suites.map((suite) => [suite.name, suite]));
  const missingRequired = requiredBackendTestClasses.filter((name) => !byName.has(name));
  const requiredPresent = requiredBackendTestClasses.length - missingRequired.length;
  if ((artifact?.summary?.suites || 0) !== suites.length) {
    issues.push(`backend test summary suites mismatch: declared=${artifact?.summary?.suites || 0}, actual=${suites.length}`);
  }
  if ((artifact?.summary?.tests || 0) !== actualTests) {
    issues.push(`backend test summary tests mismatch: declared=${artifact?.summary?.tests || 0}, actual=${actualTests}`);
  }
  if ((artifact?.summary?.failures || 0) !== actualFailures) {
    issues.push(`backend test summary failures mismatch: declared=${artifact?.summary?.failures || 0}, actual=${actualFailures}`);
  }
  if ((artifact?.summary?.errors || 0) !== actualErrors) {
    issues.push(`backend test summary errors mismatch: declared=${artifact?.summary?.errors || 0}, actual=${actualErrors}`);
  }
  if ((artifact?.summary?.skipped || 0) !== actualSkipped) {
    issues.push(`backend test summary skipped mismatch: declared=${artifact?.summary?.skipped || 0}, actual=${actualSkipped}`);
  }
  if ((artifact?.summary?.required || 0) !== requiredBackendTestClasses.length) {
    issues.push(`backend test summary required mismatch: declared=${artifact?.summary?.required || 0}, actual=${requiredBackendTestClasses.length}`);
  }
  if ((artifact?.summary?.requiredPresent || 0) !== requiredPresent) {
    issues.push(`backend test summary requiredPresent mismatch: declared=${artifact?.summary?.requiredPresent || 0}, actual=${requiredPresent}`);
  }
  if ((artifact?.summary?.requiredMissing || 0) !== missingRequired.length) {
    issues.push(`backend test summary requiredMissing mismatch: declared=${artifact?.summary?.requiredMissing || 0}, actual=${missingRequired.length}`);
  }
  if (missingRequired.length > 0) {
    issues.push(`missing required test classes=${missingRequired.length}`);
  }
  const emptyRequiredSuites = requiredBackendTestClasses
    .map((name) => byName.get(name))
    .filter((suite) => suite && (suite.tests || 0) <= 0);
  if (emptyRequiredSuites.length > 0) {
    issues.push(`required test classes with zero tests=${emptyRequiredSuites.map((suite) => suite.name).join(", ")}`);
  }
  const skippedRequiredSuites = requiredBackendTestClasses
    .map((name) => byName.get(name))
    .filter((suite) => suite && (suite.tests || 0) > 0 && (suite.tests || 0) <= (suite.skipped || 0));
  if (skippedRequiredSuites.length > 0) {
    issues.push(`required test classes fully skipped=${skippedRequiredSuites.map((suite) => suite.name).join(", ")}`);
  }
  const failedSuites = suites.filter((suite) => (suite.failures || 0) > 0 || (suite.errors || 0) > 0);
  if (failedSuites.length > 0) {
    issues.push(`failures=${sum(failedSuites, "failures")}, errors=${sum(failedSuites, "errors")}`);
  }
  for (const suite of suites) {
    validateNoLocalPath(`${suite.name || "unknown"} file`, suite.file, issues);
  }
  return issues;
}

export function validateBackendBuildArtifact(artifact) {
  const issues = [];
  if (artifact?.status !== "PASS") {
    issues.push(`status=${artifact?.status ?? "missing"}`);
  }
  if (artifact?.build?.skipped) {
    issues.push("backend package run was skipped");
  }
  if (artifact?.build && artifact.build.status !== "PASS") {
    issues.push(`backend package status=${artifact.build.status}`);
  }
  if (artifact?.build) {
    validateNoLocalPath("backend package stdoutTail", artifact.build.stdoutTail, issues);
    validateNoLocalPath("backend package stderrTail", artifact.build.stderrTail, issues);
  }
  const modules = Array.isArray(artifact?.modules) ? artifact.modules : [];
  const deployableModules = modules.filter((module) => module.deployable !== false).length;
  const jars = modules.filter((module) => module.jarPresent).length;
  const missingEntrypoints = modules.filter((module) => !module.entrypointPresent).length;
  const missingClasses = modules.filter((module) => !module.classesPresent).length;
  const missingJars = modules.filter((module) => !module.jarPresent).length;
  const requiredByModule = new Map(requiredBackendModules.map((module) => [module.module, module]));
  const moduleCounts = countValues(modules.map((module) => module.module).filter(Boolean));
  if ((artifact?.summary?.modules || 0) !== modules.length) {
    issues.push(`backend build summary modules mismatch: declared=${artifact?.summary?.modules || 0}, actual=${modules.length}`);
  }
  if ((artifact?.summary?.deployableModules || 0) !== deployableModules) {
    issues.push(`backend build summary deployableModules mismatch: declared=${artifact?.summary?.deployableModules || 0}, actual=${deployableModules}`);
  }
  if ((artifact?.summary?.jars || 0) !== jars) {
    issues.push(`backend build summary jars mismatch: declared=${artifact?.summary?.jars || 0}, actual=${jars}`);
  }
  if ((artifact?.summary?.missingEntrypoints || 0) !== missingEntrypoints) {
    issues.push(`backend build summary missingEntrypoints mismatch: declared=${artifact?.summary?.missingEntrypoints || 0}, actual=${missingEntrypoints}`);
  }
  if ((artifact?.summary?.missingClasses || 0) !== missingClasses) {
    issues.push(`backend build summary missingClasses mismatch: declared=${artifact?.summary?.missingClasses || 0}, actual=${missingClasses}`);
  }
  if ((artifact?.summary?.missingJars || 0) !== missingJars) {
    issues.push(`backend build summary missingJars mismatch: declared=${artifact?.summary?.missingJars || 0}, actual=${missingJars}`);
  }
  const moduleNames = new Set(modules.map((item) => item.module));
  for (const required of requiredBackendModules) {
    if (!moduleNames.has(required.module)) {
      issues.push(`missing module report ${required.module}`);
    }
  }
  for (const [moduleName, count] of moduleCounts.entries()) {
    if (count > 1) {
      issues.push(`duplicate module report ${moduleName}`);
    }
    if (!requiredByModule.has(moduleName)) {
      issues.push(`unknown module report ${moduleName}`);
    }
  }
  for (const module of modules) {
    const required = requiredByModule.get(module.module);
    if (required) {
      if (module.deployable !== required.deployable) {
        issues.push(`${module.module} deployable must be ${required.deployable}`);
      }
      if ((module.entrypoint || null) !== (required.entrypoint || null)) {
        issues.push(`${module.module} entrypoint must be ${required.entrypoint || "null"}`);
      }
    }
    if (!module.entrypointPresent) {
      issues.push(`${module.module} missing Spring Boot entrypoint ${module.entrypoint}`);
    }
    if (!module.classesPresent) {
      issues.push(`${module.module} missing target/classes`);
    }
    if (!module.jarPresent) {
      issues.push(`${module.module} missing target jar`);
    }
    if (module.jarPresent && (!Array.isArray(module.jars) || module.jars.length === 0)) {
      issues.push(`${module.module} jarPresent requires jars[] metadata`);
    }
    for (const jar of Array.isArray(module.jars) ? module.jars : []) {
      if (!jar?.file) {
        issues.push(`${module.module} jar file path is required`);
      }
      validateNoLocalPath(`${module.module} jar file`, jar?.file, issues);
      if (!Number.isFinite(Number(jar?.bytes)) || Number(jar.bytes) <= 0) {
        issues.push(`${module.module} jar ${jar?.file || "<unknown>"} bytes must be positive`);
      }
      if (!/^[a-f0-9]{64}$/i.test(String(jar?.sha256 || ""))) {
        issues.push(`${module.module} jar ${jar?.file || "<unknown>"} sha256 must be 64 hex characters`);
      }
    }
  }
  return issues;
}

function sum(items, field) {
  return items.reduce((total, item) => total + (Number(item[field]) || 0), 0);
}

function countValues(items) {
  const counts = new Map();
  for (const item of items) {
    counts.set(item, (counts.get(item) || 0) + 1);
  }
  return counts;
}

function validateNoLocalPath(label, value, issues) {
  if (typeof value !== "string" || value.length === 0) return;
  if (repoRoot && value.includes(repoRoot)) {
    issues.push(`${label} must not include local repo path`);
  }
  if (homeDir && homeDir !== "/" && value.includes(homeDir)) {
    issues.push(`${label} must not include local home path`);
  }
}
