#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  return {
    matrix: {
      owners: [
        {
          owner: "release-infra",
          templateEnvKeys: ["DB_PASSWORD", "MYSQL_PASSWORD", "SPRING_DATASOURCE_PASSWORD", "TRUST_FORWARDED_HEADERS"],
          unresolvedTemplateKeys: ["DB_PASSWORD", "MYSQL_PASSWORD"],
          aliasMappings: [
            { alias: "MYSQL_PASSWORD", canonical: "DB_PASSWORD" },
            { alias: "SPRING_DATASOURCE_PASSWORD", canonical: "DB_PASSWORD" },
          ],
        },
      ],
    },
    envLint: {
      envFile: ".env.release.local",
      keys: ["DB_PASSWORD", "MYSQL_PASSWORD", "SPRING_DATASOURCE_PASSWORD", "TRUST_FORWARDED_HEADERS"],
      canonicalKeys: ["DB_PASSWORD", "TRUST_FORWARDED_HEADERS"],
      unresolvedTemplateKeys: ["DB_PASSWORD", "MYSQL_PASSWORD"],
      canonicalUnresolvedTemplateKeys: ["DB_PASSWORD"],
      missingEnv: {
        templateAliasMappings: [
          { alias: "MYSQL_PASSWORD", canonical: "DB_PASSWORD" },
          { alias: "SPRING_DATASOURCE_PASSWORD", canonical: "DB_PASSWORD" },
        ],
      },
    },
    canonicalFill: {
      envFile: ".env.release.local",
      canonicalFillItemCount: 2,
      unresolvedAliasCount: 2,
      ownerCount: 1,
      items: [
        {
          fillOrder: 1,
          owner: "release-infra",
          owners: ["release-infra"],
          group: "database",
          requirement: "password",
          canonicalKey: "DB_PASSWORD",
          valueClass: "secret",
          fillGuidance: "Provide via approved secret manager or secure release channel; never commit.",
          secret: true,
          safeToPreFill: false,
          aliasCount: 3,
          aliases: ["DB_PASSWORD", "MYSQL_PASSWORD", "SPRING_DATASOURCE_PASSWORD"],
          unresolvedAliasCount: 2,
          unresolvedAliases: ["DB_PASSWORD", "MYSQL_PASSWORD"],
          required: true,
          validation: {
            https: false,
            nonLocal: false,
            minLength: 16,
            expectedValues: [],
            pattern: null,
            disallowValues: [],
          },
          aliasSyncCommand: "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs",
        },
        {
          fillOrder: 2,
          owner: "release-infra",
          owners: ["release-infra"],
          group: "web",
          requirement: "trust forwarded headers",
          canonicalKey: "TRUST_FORWARDED_HEADERS",
          valueClass: "toggle",
          fillGuidance: "Use one of: true, false.",
          secret: false,
          safeToPreFill: true,
          aliasCount: 1,
          aliases: ["TRUST_FORWARDED_HEADERS"],
          unresolvedAliasCount: 0,
          unresolvedAliases: [],
          required: true,
          validation: {
            https: false,
            nonLocal: false,
            minLength: null,
            expectedValues: ["true", "false"],
            pattern: null,
            disallowValues: [],
          },
          aliasSyncCommand: "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs",
        },
      ],
    },
    template: [
      "# Lumira DDD canonical release environment fill template.",
      "# DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env .env.release.local",
      "# DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs",
      "DB_PASSWORD=__REQUIRED__",
      "TRUST_FORWARDED_HEADERS=true",
      "",
    ].join("\n"),
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-env-owner-matrix.json"), `${JSON.stringify(artifacts.matrix, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-lint.json"), `${JSON.stringify(artifacts.envLint, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-canonical-fill.json"), `${JSON.stringify(artifacts.canonicalFill, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-canonical-fill.template.env"), artifacts.template);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-canonical-fill-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-env-canonical-fill-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /items=2/);

const duplicateTemplateKeyResult = runContract((artifacts) => {
  artifacts.template += "DB_PASSWORD=again\n";
});
assert.notEqual(duplicateTemplateKeyResult.status, 0);
assert.match(duplicateTemplateKeyResult.stderr, /template canonical keys must be unique/);

const aliasInTemplateResult = runContract((artifacts) => {
  artifacts.template += "MYSQL_PASSWORD=__REQUIRED__\n";
});
assert.notEqual(aliasInTemplateResult.status, 0);
assert.match(aliasInTemplateResult.stderr, /aliases must not be emitted/);

const unsafePrefillResult = runContract((artifacts) => {
  artifacts.canonicalFill.items[0].safeToPreFill = true;
});
assert.notEqual(unsafePrefillResult.status, 0);
assert.match(unsafePrefillResult.stderr, /secret items must not be safeToPreFill/);

const unresolvedAliasMismatchResult = runContract((artifacts) => {
  artifacts.canonicalFill.items[0].unresolvedAliases.push("UNKNOWN_ALIAS");
  artifacts.canonicalFill.items[0].unresolvedAliasCount += 1;
  artifacts.canonicalFill.unresolvedAliasCount += 1;
});
assert.notEqual(unresolvedAliasMismatchResult.status, 0);
assert.match(unresolvedAliasMismatchResult.stderr, /subset of aliases|matrix unresolved/);

const badSortResult = runContract((artifacts) => {
  artifacts.canonicalFill.items.reverse();
  artifacts.canonicalFill.items[0].fillOrder = 1;
  artifacts.canonicalFill.items[1].fillOrder = 2;
});
assert.notEqual(badSortResult.status, 0);
assert.match(badSortResult.stderr, /sorted by owner/);

console.log("[ddd-release-env-canonical-fill-contract.test] ok");
