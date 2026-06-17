#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { requiredDockerImages } from "./ddd-docker-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

for (const image of requiredDockerImages) {
  const dockerfilePath = path.join(repoRoot, image.dockerfile);
  assert.ok(fs.existsSync(dockerfilePath), `${image.name} Dockerfile must exist at ${image.dockerfile}`);
  const source = fs.readFileSync(dockerfilePath, "utf8");
  assert.match(source, new RegExp(`EXPOSE\\s+${escapeRegExp(image.expectedExposedPort.replace("/tcp", ""))}\\b`), `${image.name} Dockerfile must expose ${image.expectedExposedPort}`);
  assert.ok(
    /\bENTRYPOINT\s+\[/.test(source) || /\bCMD\s+\[/.test(source),
    `${image.name} Dockerfile must define an ENTRYPOINT or CMD`,
  );
  if (image.requireNonRootUser) {
    assert.match(source, /\bUSER\s+(?!root\b|0\b)\S+/, `${image.name} Dockerfile must switch to a non-root USER`);
    assert.match(source, /\badduser\b|\buseradd\b/, `${image.name} Dockerfile must create an application user`);
  }
}

const serviceDockerfile = fs.readFileSync(path.join(repoRoot, "deploy", "docker", "service.Dockerfile"), "utf8");
const productionCompose = fs.readFileSync(path.join(repoRoot, "deploy", "docker-compose.prod.yml"), "utf8");
assert.match(serviceDockerfile, /\bARG\s+MAVEN_IMAGE=/, "service Dockerfile must allow overriding the Maven builder image for registry mirrors");
assert.match(serviceDockerfile, /\bARG\s+JRE_IMAGE=/, "service Dockerfile must allow overriding the JRE runtime image for registry mirrors");
assert.match(serviceDockerfile, /FROM\s+\$\{MAVEN_IMAGE\}\s+AS\s+builder/, "service Dockerfile must use MAVEN_IMAGE for the builder stage");
assert.match(serviceDockerfile, /FROM\s+\$\{JRE_IMAGE\}/, "service Dockerfile must use JRE_IMAGE for the runtime stage");
assert.match(serviceDockerfile, /\bARG\s+SERVICE_MODULE\b/, "service Dockerfile must keep SERVICE_MODULE build arg for owner/deployable modules");
assert.match(serviceDockerfile, /\bARG\s+SERVICE_DIR\b/, "service Dockerfile must keep SERVICE_DIR artifact selection arg");
assert.match(serviceDockerfile, /opentelemetry-javaagent\.jar/, "service Dockerfile must preserve OpenTelemetry javaagent artifact wiring");
assert.match(serviceDockerfile, /\bARG\s+OTEL_JAVAAGENT_URL=\s*(?:\r?\n|$)/, "service Dockerfile must not require external javaagent downloads by default");
assert.match(serviceDockerfile, /if \[ -n "\$OTEL_JAVAAGENT_URL" \]/, "service Dockerfile must download the javaagent only when explicitly configured");
assert.match(serviceDockerfile, /\[\s+!\s+-s\s+\\?"\$OTEL_JAVAAGENT_PATH\\?"/, "service Dockerfile must fail fast if OTEL agent is enabled without a packaged javaagent");
assert.match(serviceDockerfile, /mvn\s+-pl\s+"\$SERVICE_MODULE"\s+-am/, "service Dockerfile must build only the selected Maven module with reactor dependencies");
assert.match(productionCompose, /MAVEN_IMAGE:\s+\$\{MAVEN_IMAGE:-maven:3\.9\.11-eclipse-temurin-21\}/, "production compose must expose MAVEN_IMAGE for trusted registry mirrors");
assert.match(productionCompose, /JRE_IMAGE:\s+\$\{JRE_IMAGE:-eclipse-temurin:21-jre\}/, "production compose must expose JRE_IMAGE for trusted registry mirrors");
assert.match(productionCompose, /OTEL_JAVAAGENT_URL:\s+\$\{OTEL_JAVAAGENT_URL:-\}/, "production compose must expose OTEL_JAVAAGENT_URL as an explicit service build arg");

const frontendDockerfile = fs.readFileSync(path.join(repoRoot, "deploy", "docker", "frontend.Dockerfile"), "utf8");
assert.match(frontendDockerfile, /\bARG\s+NODE_IMAGE=/, "frontend Dockerfile must allow overriding the Node builder image for registry mirrors");
assert.match(frontendDockerfile, /\bARG\s+NGINX_IMAGE=/, "frontend Dockerfile must allow overriding the nginx runtime image for registry mirrors");
assert.match(frontendDockerfile, /FROM\s+\$\{NODE_IMAGE\}\s+AS\s+builder/, "frontend Dockerfile must use NODE_IMAGE for the builder stage");
assert.match(frontendDockerfile, /FROM\s+\$\{NGINX_IMAGE\}/, "frontend Dockerfile must use NGINX_IMAGE for the runtime stage");
assert.match(frontendDockerfile, /pnpm\s+install\s+--frozen-lockfile/, "frontend Dockerfile must use the frozen pnpm lockfile");
assert.match(frontendDockerfile, /pnpm\s+build/, "frontend Dockerfile must run the production build");
assert.match(frontendDockerfile, /COPY\s+deploy\/nginx\/frontend\.conf\s+\/etc\/nginx\/conf\.d\/default\.conf/, "frontend Dockerfile must install the production nginx config");
assert.match(frontendDockerfile, /COPY\s+--from=builder\s+\/workspace\/frontend\/dist\s+\/usr\/share\/nginx\/html/, "frontend Dockerfile must serve the built dist output");
assert.match(productionCompose, /NODE_IMAGE:\s+\$\{NODE_IMAGE:-node:22-bookworm-slim\}/, "production compose must expose NODE_IMAGE for trusted registry mirrors");
assert.match(productionCompose, /NGINX_IMAGE:\s+\$\{NGINX_IMAGE:-nginx:1\.29-alpine\}/, "production compose must expose NGINX_IMAGE for trusted registry mirrors");

console.log("[ddd-dockerfile-contract.test] ok");

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
